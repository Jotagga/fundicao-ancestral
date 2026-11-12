package dev.ancientfoundry.gametest;

import java.util.List;

import dev.ancientfoundry.AncientFoundry;
import dev.ancientfoundry.block.FoundryControllerBlock;
import dev.ancientfoundry.blockentity.FoundryControllerBlockEntity;
import dev.ancientfoundry.logic.FoundryCore;
import dev.ancientfoundry.multiblock.FoundryStructure;
import dev.ancientfoundry.registry.ModBlocks;
import dev.ancientfoundry.registry.ModTags;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * In-game tests, run on a real (headless) server by {@code ./gradlew runGameTest}.
 *
 * <p>Every test builds its foundry inside the empty 8x8x8 test structure. For a controller facing
 * north at (3, 1, 1) the foundry occupies x 1..5, y 0..3 and z 1..5.
 */
public class FoundryGameTests implements FabricGameTest {
	private static final BlockPos CONTROLLER = new BlockPos(3, 1, 1);
	private static final long TWELVE_HOURS = 12L * 60L * 60L * 20L;

	// ------------------------------------------------------------------ helpers

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestAssertException(message);
		}
	}

	private static FoundryControllerBlockEntity build(GameTestHelper helper, BlockPos relative, Direction facing) {
		ServerLevel level = helper.getLevel();
		BlockPos absolute = helper.absolutePos(relative);
		FoundryStructure.place(level, absolute, facing);
		BlockEntity blockEntity = level.getBlockEntity(absolute);

		if (blockEntity instanceof FoundryControllerBlockEntity controller) {
			return controller;
		}

		throw new GameTestAssertException("No controller block entity at " + absolute);
	}

	private static String describe(FoundryControllerBlockEntity controller) {
		FoundryStructure.Result result = controller.lastResult();

		if (result == null) {
			return "structure never checked";
		}

		return result.formed() ? "formed" : "missing " + result.errorPart() + " at " + result.errorPos() + " (layer " + result.errorLayer() + ")";
	}

	private static void refill(Container controller) {
		controller.setItem(FoundryControllerBlockEntity.SLOT_ROCK, new ItemStack(Items.BASALT, 64));
		controller.setItem(FoundryControllerBlockEntity.SLOT_SOUL, new ItemStack(Items.SOUL_SAND, 64));
		controller.setItem(FoundryControllerBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL_BLOCK, 64));
	}

	private static void formsFacing(GameTestHelper helper, BlockPos relative, Direction facing) {
		FoundryControllerBlockEntity controller = build(helper, relative, facing);

		helper.runAfterDelay(5, () -> {
			check(controller.isFormed(), "Foundry facing " + facing + " did not form: " + describe(controller));
			BlockState state = helper.getBlockState(relative);
			check(state.getValue(FoundryControllerBlock.FORMED), "FORMED block state not set for " + facing);
			check(!state.getValue(FoundryControllerBlock.LIT), "An empty foundry must not be lit");
			helper.succeed();
		});
	}

	/** Swaps one structure block for something wrong and expects the controller to name it. */
	private static void expectError(GameTestHelper helper, int dx, int dy, int dz, BlockState wrong,
			FoundryStructure.Part expectedPart, int expectedLayer) {
		FoundryControllerBlockEntity controller = build(helper, CONTROLLER, Direction.NORTH);
		BlockPos absolute = helper.absolutePos(CONTROLLER);
		BlockPos broken = FoundryStructure.toWorld(absolute, Direction.NORTH, dx, dy, dz);
		helper.getLevel().setBlock(broken, wrong, 3);

		helper.runAfterDelay(5, () -> {
			check(!controller.isFormed(), "Foundry formed with " + wrong.getBlock() + " where " + expectedPart + " belongs");
			FoundryStructure.Result result = controller.lastResult();
			check(result != null && broken.equals(result.errorPos()), "Wrong error position: " + describe(controller) + ", expected " + broken);
			check(result.errorPart() == expectedPart, "Wrong error part: " + describe(controller) + ", expected " + expectedPart);
			check(result.errorLayer() == expectedLayer, "Wrong error layer: " + describe(controller) + ", expected " + expectedLayer);
			check(!helper.getBlockState(CONTROLLER).getValue(FoundryControllerBlock.FORMED), "FORMED block state still set");
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------ structure

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void formsFacingNorth(GameTestHelper helper) {
		formsFacing(helper, new BlockPos(3, 1, 1), Direction.NORTH);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void formsFacingSouth(GameTestHelper helper) {
		formsFacing(helper, new BlockPos(3, 1, 6), Direction.SOUTH);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void formsFacingEast(GameTestHelper helper) {
		formsFacing(helper, new BlockPos(6, 1, 3), Direction.EAST);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void formsFacingWest(GameTestHelper helper) {
		formsFacing(helper, new BlockPos(1, 1, 3), Direction.WEST);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void frameRejectsGlass(GameTestHelper helper) {
		// A vertical edge of the chamber: casing only.
		expectError(helper, 2, 0, 0, ModBlocks.FOUNDRY_GLASS.defaultBlockState(), FoundryStructure.Part.FRAME, 2);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void baseNeedsMagma(GameTestHelper helper) {
		expectError(helper, 0, -1, 2, Blocks.STONE.defaultBlockState(), FoundryStructure.Part.MAGMA, 1);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void chamberNeedsCore(GameTestHelper helper) {
		expectError(helper, 0, 0, 2, Blocks.LAVA.defaultBlockState(), FoundryStructure.Part.CORE, 2);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void chamberNeedsLava(GameTestHelper helper) {
		expectError(helper, 1, 1, 1, Blocks.STONE.defaultBlockState(), FoundryStructure.Part.LAVA, 3);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void roofNeedsVent(GameTestHelper helper) {
		expectError(helper, 0, 2, 2, ModBlocks.FOUNDRY_CASING.defaultBlockState(), FoundryStructure.Part.VENT, 4);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void ventNeedsOpenAir(GameTestHelper helper) {
		expectError(helper, 0, 3, 2, Blocks.STONE.defaultBlockState(), FoundryStructure.Part.AIR, 5);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void wallsAcceptGlassAndHatchesAndRecover(GameTestHelper helper) {
		FoundryControllerBlockEntity controller = build(helper, CONTROLLER, Direction.NORTH);
		ServerLevel level = helper.getLevel();
		BlockPos absolute = helper.absolutePos(CONTROLLER);
		BlockPos roofSpot = FoundryStructure.toWorld(absolute, Direction.NORTH, 1, 2, 3);
		BlockPos wallSpot = FoundryStructure.toWorld(absolute, Direction.NORTH, -2, 1, 2);
		BlockPos backSpot = FoundryStructure.toWorld(absolute, Direction.NORTH, 0, 0, 4);

		level.setBlock(roofSpot, Blocks.STONE.defaultBlockState(), 3);

		helper.runAfterDelay(5, () -> {
			check(!controller.isFormed(), "Stone in the roof must break the structure");
			level.setBlock(roofSpot, ModBlocks.FOUNDRY_GLASS.defaultBlockState(), 3);
			level.setBlock(wallSpot, ModBlocks.FOUNDRY_INPUT_HATCH.defaultBlockState(), 3);
			level.setBlock(backSpot, ModBlocks.FOUNDRY_OUTPUT_HATCH.defaultBlockState(), 3);

			// The controller re-checks once a second.
			helper.runAfterDelay(30, () -> {
				check(controller.isFormed(), "Glass and hatches must be accepted in walls: " + describe(controller));
				check(controller.ownsInputHatch(wallSpot), "Input hatch not registered");
				check(controller.outputHatches().contains(backSpot), "Output hatch not registered");
				check(helper.getBlockState(CONTROLLER).getValue(FoundryControllerBlock.FORMED), "FORMED block state not restored");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void loneControllerExplainsWhatIsMissing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos absolute = helper.absolutePos(CONTROLLER);
		level.setBlock(absolute, ModBlocks.FOUNDRY_CONTROLLER.defaultBlockState(), 3);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);

		helper.runAfterDelay(5, () -> {
			FoundryControllerBlockEntity controller = (FoundryControllerBlockEntity) level.getBlockEntity(absolute);
			check(controller != null, "Controller block entity missing");
			controller.onUse(player);
			FoundryStructure.Result result = controller.lastResult();
			check(result != null && !result.formed(), "A lone controller cannot be a foundry");
			check(result.errorPart() == FoundryStructure.Part.FRAME && result.errorLayer() == 1,
					"The first missing block should be base casing: " + describe(controller));
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------ the rate

	/**
	 * The promise of the mod: 12 hours of a fed foundry give at least 40 scraps, i.e. 10 ingots.
	 * The controller is ticked 864,000 times in a row, starting cold.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200)
	public void twelveHoursGiveTenIngots(GameTestHelper helper) {
		FoundryControllerBlockEntity controller = build(helper, CONTROLLER, Direction.NORTH);
		ServerLevel level = helper.getLevel();
		BlockPos absolute = helper.absolutePos(CONTROLLER);

		helper.runAfterDelay(5, () -> {
			check(controller.isFormed(), "Foundry did not form: " + describe(controller));
			long fortiethAt = -1L;

			for (long tick = 1; tick <= TWELVE_HOURS; tick++) {
				if (tick % 600L == 1L) {
					refill(controller);
				}

				FoundryControllerBlockEntity.serverTick(level, absolute, level.getBlockState(absolute), controller);

				if (fortiethAt < 0L && controller.core().totalScraps() >= 40L) {
					fortiethAt = tick;
				}
			}

			long scraps = controller.core().totalScraps();
			int inSlot = controller.getItem(FoundryControllerBlockEntity.SLOT_OUTPUT).getCount();
			AncientFoundry.LOGGER.info("[gametest] 12 h cold start: {} scraps = {} ingots; 40th scrap after {} h; {} h spent working",
					scraps, scraps / 4L, String.format("%.2f", fortiethAt / 72000.0), String.format("%.2f", controller.core().activeTicks() / 72000.0));

			check(controller.isFormed(), "Foundry fell apart during the run: " + describe(controller));
			check(scraps >= 40L, "Only " + scraps + " scraps in 12 h; 10 ingots need 40");
			check(scraps <= 41L, scraps + " scraps in 12 h is more than the design allows (41)");
			check(inSlot == scraps, "Output slot holds " + inSlot + " scraps but " + scraps + " were produced");
			check(controller.getItem(FoundryControllerBlockEntity.SLOT_OUTPUT).is(Items.NETHERITE_SCRAP), "Output is not netherite scrap");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200)
	public void everyWindowPaysExactlyOneScrap(GameTestHelper helper) {
		FoundryControllerBlockEntity controller = build(helper, CONTROLLER, Direction.NORTH);
		ServerLevel level = helper.getLevel();
		BlockPos absolute = helper.absolutePos(CONTROLLER);

		helper.runAfterDelay(5, () -> {
			FoundryCore.Settings settings = controller.core().settings();
			long window = settings.ticksPerScrap();
			refill(controller);

			// Heat up first, then watch 12 whole windows.
			for (int tick = 0; tick < settings.warmupTicks() - 1; tick++) {
				FoundryControllerBlockEntity.serverTick(level, absolute, level.getBlockState(absolute), controller);
			}

			check(controller.core().status() == FoundryCore.Status.HEATING, "Expected HEATING just before working heat, got " + controller.core().status());

			for (int round = 1; round <= 12; round++) {
				for (long tick = 0; tick < window; tick++) {
					if (tick % 600L == 0L) {
						refill(controller);
						// Keep the output slot from filling up.
						controller.setItem(FoundryControllerBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
					}

					FoundryControllerBlockEntity.serverTick(level, absolute, level.getBlockState(absolute), controller);
				}

				check(controller.core().totalScraps() == round, "After window " + round + " the total is " + controller.core().totalScraps());
			}

			check(helper.getBlockState(CONTROLLER).getValue(FoundryControllerBlock.LIT), "A working foundry must be lit");
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------ fuel, pause, automation

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void lavaBucketLeavesAnEmptyBucket(GameTestHelper helper) {
		FoundryControllerBlockEntity controller = build(helper, CONTROLLER, Direction.NORTH);

		helper.runAfterDelay(5, () -> {
			controller.setItem(FoundryControllerBlockEntity.SLOT_ROCK, new ItemStack(Items.BLACKSTONE, 8));
			controller.setItem(FoundryControllerBlockEntity.SLOT_SOUL, new ItemStack(Items.SOUL_SOIL, 8));
			controller.setItem(FoundryControllerBlockEntity.SLOT_FUEL, new ItemStack(Items.LAVA_BUCKET));

			helper.runAfterDelay(5, () -> {
				check(controller.getItem(FoundryControllerBlockEntity.SLOT_FUEL).isEmpty(), "Lava bucket was not consumed");
				check(controller.getItem(FoundryControllerBlockEntity.SLOT_BYPRODUCT).is(Items.BUCKET), "Empty bucket missing from the by-product slot");
				check(controller.core().burnTotal() == 10_000, "A lava bucket should burn 10000 ticks (50% of 20000), got " + controller.core().burnTotal());
				check(controller.core().status() == FoundryCore.Status.HEATING, "Expected HEATING, got " + controller.core().status());
				check(helper.getBlockState(CONTROLLER).getValue(FoundryControllerBlock.LIT), "A heating foundry must be lit");
				check(controller.getItem(FoundryControllerBlockEntity.SLOT_ROCK).getCount() == 8, "Ingredients must not be consumed before a cycle ends");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void redstonePausesWithoutBurningFuel(GameTestHelper helper) {
		FoundryControllerBlockEntity controller = build(helper, CONTROLLER, Direction.NORTH);
		BlockPos front = CONTROLLER.north();
		helper.setBlock(front, Blocks.REDSTONE_BLOCK);

		helper.runAfterDelay(5, () -> {
			refill(controller);

			helper.runAfterDelay(10, () -> {
				check(controller.core().status() == FoundryCore.Status.PAUSED, "Expected PAUSED, got " + controller.core().status());
				check(controller.getItem(FoundryControllerBlockEntity.SLOT_FUEL).getCount() == 64, "Fuel was burned while paused");
				helper.setBlock(front, Blocks.AIR);

				helper.runAfterDelay(10, () -> {
					check(controller.core().status() == FoundryCore.Status.HEATING, "Expected HEATING after the signal went away, got " + controller.core().status());
					check(controller.getItem(FoundryControllerBlockEntity.SLOT_FUEL).getCount() == 63, "Exactly one fuel item should be burning");
					helper.succeed();
				});
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void hopperFeedsThroughInputHatch(GameTestHelper helper) {
		FoundryControllerBlockEntity controller = build(helper, CONTROLLER, Direction.NORTH);
		ServerLevel level = helper.getLevel();
		BlockPos absolute = helper.absolutePos(CONTROLLER);
		BlockPos hatch = FoundryStructure.toWorld(absolute, Direction.NORTH, 1, 2, 1);
		BlockPos hopperPos = hatch.above();
		level.setBlock(hatch, ModBlocks.FOUNDRY_INPUT_HATCH.defaultBlockState(), 3);
		level.setBlock(hopperPos, Blocks.HOPPER.defaultBlockState(), 3);

		if (!(level.getBlockEntity(hopperPos) instanceof Container hopper)) {
			throw new GameTestAssertException("Hopper block entity missing");
		}

		hopper.setItem(0, new ItemStack(Items.BASALT, 3));
		hopper.setItem(1, new ItemStack(Items.SOUL_SAND, 2));
		hopper.setItem(2, new ItemStack(Items.COAL, 1));
		hopper.setItem(3, new ItemStack(Items.DIRT, 4));

		helper.runAfterDelay(150, () -> {
			check(controller.isFormed(), "Foundry with a roof hatch did not form: " + describe(controller));
			ItemStack rock = controller.getItem(FoundryControllerBlockEntity.SLOT_ROCK);
			ItemStack soul = controller.getItem(FoundryControllerBlockEntity.SLOT_SOUL);
			check(rock.is(Items.BASALT) && rock.getCount() == 3, "Rock slot holds " + rock + ", expected 3 basalt");
			check(soul.is(Items.SOUL_SAND) && soul.getCount() == 2, "Soul slot holds " + soul + ", expected 2 soul sand");
			check(controller.core().isBurning(), "The coal should have been accepted as fuel and lit");
			check(hopper.getItem(2).isEmpty(), "Coal is still in the hopper");
			check(hopper.getItem(3).getCount() == 4, "Dirt must stay in the hopper, found " + hopper.getItem(3));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void outputHatchEjectsIntoChest(GameTestHelper helper) {
		FoundryControllerBlockEntity controller = build(helper, CONTROLLER, Direction.NORTH);
		ServerLevel level = helper.getLevel();
		BlockPos absolute = helper.absolutePos(CONTROLLER);
		BlockPos hatch = FoundryStructure.toWorld(absolute, Direction.NORTH, 2, 0, 2);
		BlockPos chestPos = FoundryStructure.toWorld(absolute, Direction.NORTH, 3, 0, 2);
		level.setBlock(hatch, ModBlocks.FOUNDRY_OUTPUT_HATCH.defaultBlockState(), 3);
		level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);

		helper.runAfterDelay(5, () -> {
			check(controller.isFormed(), "Foundry with an output hatch did not form: " + describe(controller));
			controller.setItem(FoundryControllerBlockEntity.SLOT_OUTPUT, new ItemStack(Items.NETHERITE_SCRAP, 5));
			controller.setItem(FoundryControllerBlockEntity.SLOT_BYPRODUCT, new ItemStack(Items.BUCKET, 1));

			helper.runAfterDelay(40, () -> {
				if (!(level.getBlockEntity(chestPos) instanceof Container chest)) {
					throw new GameTestAssertException("Chest block entity missing");
				}

				check(chest.countItem(Items.NETHERITE_SCRAP) == 5, "Chest holds " + chest.countItem(Items.NETHERITE_SCRAP) + " scraps, expected 5");
				check(chest.countItem(Items.BUCKET) == 1, "Chest holds " + chest.countItem(Items.BUCKET) + " buckets, expected 1");
				check(controller.getItem(FoundryControllerBlockEntity.SLOT_OUTPUT).isEmpty(), "Output slot was not emptied");
				check(controller.getItem(FoundryControllerBlockEntity.SLOT_BYPRODUCT).isEmpty(), "By-product slot was not emptied");
				helper.succeed();
			});
		});
	}

	// ------------------------------------------------------------------ menu and data

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void menuShiftClickSortsItems(GameTestHelper helper) {
		FoundryControllerBlockEntity controller = build(helper, CONTROLLER, Direction.NORTH);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);

		helper.runAfterDelay(5, () -> {
			AbstractContainerMenu menu = controller.createMenu(1, player.getInventory(), player);
			check(menu.slots.size() == FoundryControllerBlockEntity.SLOT_COUNT + 36, "Menu has " + menu.slots.size() + " slots, expected 41");

			// Player main inventory slots 9..11 are menu slots 5..7.
			player.getInventory().setItem(9, new ItemStack(Items.NETHERRACK, 10));
			player.getInventory().setItem(10, new ItemStack(Items.SOUL_SOIL, 7));
			player.getInventory().setItem(11, new ItemStack(Items.BLAZE_ROD, 3));
			player.getInventory().setItem(12, new ItemStack(Items.DIRT, 5));
			menu.quickMoveStack(player, 5);
			menu.quickMoveStack(player, 6);
			menu.quickMoveStack(player, 7);
			menu.quickMoveStack(player, 8);

			check(controller.getItem(FoundryControllerBlockEntity.SLOT_ROCK).getCount() == 10, "Netherrack did not go to the rock slot");
			check(controller.getItem(FoundryControllerBlockEntity.SLOT_SOUL).getCount() == 7, "Soul soil did not go to the soul slot");
			check(controller.getItem(FoundryControllerBlockEntity.SLOT_FUEL).is(Items.BLAZE_ROD), "Blaze rods did not go to the fuel slot");
			check(player.getInventory().countItem(Items.DIRT) == 5, "Dirt left the player inventory");
			check(controller.countItem(Items.DIRT) == 0, "Dirt entered the foundry");

			ItemStack scrap = new ItemStack(Items.NETHERITE_SCRAP);
			check(!menu.getSlot(FoundryControllerBlockEntity.SLOT_OUTPUT).mayPlace(scrap), "Players must not be able to put items in the output slot");
			check(!menu.getSlot(FoundryControllerBlockEntity.SLOT_ROCK).mayPlace(new ItemStack(Items.DIRT)), "Dirt must not fit the rock slot");

			controller.setItem(FoundryControllerBlockEntity.SLOT_OUTPUT, new ItemStack(Items.NETHERITE_SCRAP, 4));
			menu.quickMoveStack(player, FoundryControllerBlockEntity.SLOT_OUTPUT);
			check(player.getInventory().countItem(Items.NETHERITE_SCRAP) == 4, "Shift-click did not take the scraps out");

			menu.removed(player);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void recipesLootAndTagsAreLoaded(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<String> names = List.of("foundry_controller", "foundry_casing", "foundry_glass", "foundry_input_hatch",
				"foundry_output_hatch", "foundry_vent", "ancient_core");
		List<Block> blocks = List.of(ModBlocks.FOUNDRY_CONTROLLER, ModBlocks.FOUNDRY_CASING, ModBlocks.FOUNDRY_GLASS,
				ModBlocks.FOUNDRY_INPUT_HATCH, ModBlocks.FOUNDRY_OUTPUT_HATCH, ModBlocks.FOUNDRY_VENT, ModBlocks.ANCIENT_CORE);

		for (String name : names) {
			check(server.getRecipeManager().byKey(AncientFoundry.id(name)).isPresent(), "Recipe missing: " + name);
			check(server.getAdvancements().get(AncientFoundry.id("recipes/misc/" + name)) != null, "Recipe unlock missing: " + name);
		}

		for (Block block : blocks) {
			LootTable table = server.reloadableRegistries().getLootTable(block.getLootTable());
			check(table != LootTable.EMPTY, "Loot table missing for " + block);
			check(block.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE), block + " is not mineable with a pickaxe");
		}

		RecipeHolder<?> casing = server.getRecipeManager().byKey(AncientFoundry.id("foundry_casing")).orElseThrow();
		ItemStack result = casing.value().getResultItem(server.registryAccess());
		check(result.is(ModBlocks.FOUNDRY_CASING.asItem()) && result.getCount() == 4, "Casing recipe gives " + result + ", expected 4 casing");

		for (var rock : List.of(Items.BASALT, Items.SMOOTH_BASALT, Items.POLISHED_BASALT, Items.BLACKSTONE, Items.NETHERRACK)) {
			check(new ItemStack(rock).is(ModTags.FOUNDRY_ROCK), rock + " is missing from the foundry_rock tag");
		}

		check(new ItemStack(Items.SOUL_SAND).is(ModTags.FOUNDRY_SOUL) && new ItemStack(Items.SOUL_SOIL).is(ModTags.FOUNDRY_SOUL), "Soul sand or soul soil missing from the foundry_soul tag");
		check(!new ItemStack(Items.COBBLESTONE).is(ModTags.FOUNDRY_ROCK), "Cobblestone must not count as foundry rock");
		helper.succeed();
	}
}
