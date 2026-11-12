package dev.ancientfoundry.clienttest;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import dev.ancientfoundry.AncientFoundry;
import dev.ancientfoundry.block.FoundryControllerBlock;
import dev.ancientfoundry.blockentity.FoundryControllerBlockEntity;
import dev.ancientfoundry.client.FoundryScreen;
import dev.ancientfoundry.logic.FoundryCore;
import dev.ancientfoundry.menu.FoundryMenu;
import dev.ancientfoundry.multiblock.FoundryStructure;
import dev.ancientfoundry.registry.ModBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A scripted run of the real client, started with {@code ./gradlew runClientTest}.
 *
 * <p>It checks what a server can never check: that every model, texture and translation of the
 * mod really loads in the client, that the controller screen opens with the synced values, and
 * it saves screenshots of the foundry and of its screen to {@code screenshots/}.
 *
 * <p>The script runs on its own thread and hands small jobs to the client and server threads.
 */
public final class ClientSmokeTest implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("ancient_foundry_clienttest");
	private static final List<Block> BLOCKS = List.of(ModBlocks.FOUNDRY_CONTROLLER, ModBlocks.FOUNDRY_CASING,
			ModBlocks.FOUNDRY_GLASS, ModBlocks.FOUNDRY_INPUT_HATCH, ModBlocks.FOUNDRY_OUTPUT_HATCH,
			ModBlocks.FOUNDRY_VENT, ModBlocks.ANCIENT_CORE);
	private static final List<String> TEXTURES = List.of("foundry_casing", "foundry_glass",
			"foundry_controller_front_off", "foundry_controller_front_idle", "foundry_controller_front_on",
			"foundry_input_hatch", "foundry_output_hatch", "foundry_vent_top", "foundry_vent_side", "ancient_core");

	private final List<String> failures = Collections.synchronizedList(new ArrayList<>());
	private final AtomicLong clientTicks = new AtomicLong();

	@Override
	public void onInitializeClient() {
		if (!Boolean.getBoolean("ancientfoundry.clienttest")) {
			return;
		}

		ClientTickEvents.END_CLIENT_TICK.register(client -> clientTicks.incrementAndGet());

		Thread thread = new Thread(() -> {
			boolean passed = false;

			try {
				runTest();
				passed = failures.isEmpty();
			} catch (Throwable t) {
				LOGGER.error("[clienttest] crashed", t);
			}

			for (String failure : failures) {
				LOGGER.error("[clienttest] FAILED: {}", failure);
			}

			LOGGER.info("[clienttest] RESULT: {}", passed ? "PASS" : "FAIL");

			if (passed) {
				Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
			} else {
				System.exit(1);
			}
		}, "Ancient Foundry client test");
		thread.setDaemon(true);
		thread.start();

		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(15L * 60L * 1000L);
			} catch (InterruptedException e) {
				return;
			}

			LOGGER.error("[clienttest] RESULT: FAIL (timed out after 15 minutes)");
			System.exit(2);
		}, "Ancient Foundry client test watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
	}

	// ------------------------------------------------------------------ the script

	private void runTest() throws Exception {
		waitFor("the resources to load", 600, () -> onClient(client -> client.getOverlay() == null && client.screen != null));
		String language = onClient(client -> client.getLanguageManager().getSelected());
		LOGGER.info("[clienttest] client loaded, language {}", language);

		onClient(client -> {
			client.options.pauseOnLostFocus = false;
			client.options.hideGui = true;
			client.options.renderDistance().set(5);
			return null;
		});

		onClient(client -> {
			checkResources(client);
			return null;
		});
		LOGGER.info("[clienttest] resource checks done, {} problem(s) so far", failures.size());

		Minecraft.getInstance().execute(() -> Minecraft.getInstance().createWorldOpenFlows().createFreshLevel(
				"FoundryTest",
				new LevelSettings("FoundryTest", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
				new WorldOptions(20261111L, false, false),
				registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
				null));

		waitFor("the world to load", 600, () -> onClient(client -> client.level != null && client.player != null
				&& client.getSingleplayerServer() != null
				&& !client.getSingleplayerServer().getPlayerList().getPlayers().isEmpty()
				&& !(client.screen instanceof LevelLoadingScreen) && !(client.screen instanceof ReceivingLevelScreen)));
		waitTicks(40);
		LOGGER.info("[clienttest] in the world");

		IntegratedServer server = onClient(Minecraft::getSingleplayerServer);
		BlockPos controllerPos = onServer(server, this::buildScene);
		waitTicks(60);

		onServer(server, s -> {
			ServerLevel level = s.overworld();

			if (level.getBlockEntity(controllerPos) instanceof FoundryControllerBlockEntity controller) {
				check(controller.isFormed(), "the foundry did not form on the integrated server");
				check(controller.core().status() == FoundryCore.Status.WORKING, "expected WORKING, got " + controller.core().status());
			} else {
				fail("no controller block entity at " + controllerPos);
			}

			return null;
		});

		onClient(client -> {
			BlockState state = client.level.getBlockState(controllerPos);
			check(state.is(ModBlocks.FOUNDRY_CONTROLLER), "the client does not see the controller, it sees " + state);

			if (state.is(ModBlocks.FOUNDRY_CONTROLLER)) {
				check(state.getValue(FoundryControllerBlock.FORMED), "client block state: formed=false");
				check(state.getValue(FoundryControllerBlock.LIT), "client block state: lit=false");
			}

			return null;
		});

		// Give the chunks time to be drawn, and the "new recipes" toasts time to go away.
		Thread.sleep(12_000L);
		screenshot("foundry_world.png");

		onServer(server, s -> {
			ServerPlayer player = s.getPlayerList().getPlayers().get(0);

			if (s.overworld().getBlockEntity(controllerPos) instanceof FoundryControllerBlockEntity controller) {
				player.openMenu(controller);
			}

			return null;
		});

		waitFor("the foundry screen to open", 60, () -> onClient(client -> client.screen instanceof FoundryScreen));
		waitTicks(20);
		Thread.sleep(2_000L);

		onClient(client -> {
			if (client.screen instanceof FoundryScreen screen) {
				FoundryMenu menu = screen.getMenu();
				check(menu.status() == FoundryCore.Status.WORKING, "screen status is " + menu.status() + ", expected WORKING");
				check(menu.temperature() >= 1200, "screen temperature is " + menu.temperature() + ", expected at least 1200");
				check(menu.windowSize() == 35, "screen window size is " + menu.windowSize() + ", expected 35");
				check(menu.totalScraps() == 17, "screen total is " + menu.totalScraps() + ", expected 17");
				check(menu.getSlot(FoundryControllerBlockEntity.SLOT_ROCK).getItem().is(Items.BASALT), "the rock slot did not sync");
				check(menu.getSlot(FoundryControllerBlockEntity.SLOT_OUTPUT).getItem().is(Items.NETHERITE_SCRAP), "the output slot did not sync");
				check(menu.progressPermille() > 0 && menu.burnPermille() > 0 && menu.heatPermille() > 0, "the gauges did not sync");
			} else {
				fail("the foundry screen is not open");
			}

			return null;
		});

		screenshot("foundry_gui.png");
		onClient(client -> {
			client.player.closeContainer();
			return null;
		});
		waitTicks(10);
	}

	/** Runs on the client thread: every model, texture and translation of the mod must be real. */
	private void checkResources(Minecraft client) {
		BakedModel missing = client.getModelManager().getMissingModel();

		for (Block block : BLOCKS) {
			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
				check(client.getBlockRenderer().getBlockModel(state) != missing, "missing block model for " + state);
			}

			check(client.getItemRenderer().getItemModelShaper().getItemModel(new ItemStack(block)) != missing, "missing item model for " + block);
			check(I18n.exists(block.getDescriptionId()), "missing translation " + block.getDescriptionId());
			check(I18n.exists(block.getDescriptionId() + ".tooltip"), "missing translation " + block.getDescriptionId() + ".tooltip");
		}

		TextureAtlas atlas = client.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);

		for (String name : TEXTURES) {
			ResourceLocation id = AncientFoundry.id("block/" + name);
			check(atlas.getSprite(id).contents().name().equals(id), "texture not in the block atlas: " + id);
		}

		check(client.getResourceManager().getResource(AncientFoundry.id("textures/gui/foundry.png")).isPresent(), "GUI texture missing");
		check(ItemBlockRenderTypes.getChunkRenderType(ModBlocks.FOUNDRY_GLASS.defaultBlockState()) == RenderType.translucent(),
				"foundry glass is not on the translucent layer");

		for (String key : List.of("container.ancient_foundry.foundry", "itemGroup.ancient_foundry", "gui.ancient_foundry.status.working",
				"gui.ancient_foundry.window", "message.ancient_foundry.incomplete", "gui.ancient_foundry.slot.rock")) {
			check(I18n.exists(key), "missing translation " + key);
		}
	}

	/** Runs on the server thread: builds a working foundry in front of the player and frames the shot. */
	private BlockPos buildScene(IntegratedServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		BlockPos feet = player.blockPosition();
		BlockPos controllerPos = feet.offset(0, 1, 6);
		Direction facing = Direction.NORTH;

		FoundryStructure.place(level, controllerPos, facing);

		// Windows in the front and in the side wall that faces the camera, hatches, a hopper.
		for (int dx = -1; dx <= 1; dx++) {
			level.setBlock(FoundryStructure.toWorld(controllerPos, facing, dx, 1, 0), ModBlocks.FOUNDRY_GLASS.defaultBlockState(), 3);
		}

		for (int dz = 1; dz <= 3; dz++) {
			for (int dy = 0; dy <= 1; dy++) {
				level.setBlock(FoundryStructure.toWorld(controllerPos, facing, 2, dy, dz), ModBlocks.FOUNDRY_GLASS.defaultBlockState(), 3);
			}
		}

		level.setBlock(FoundryStructure.toWorld(controllerPos, facing, 1, 0, 0), ModBlocks.FOUNDRY_OUTPUT_HATCH.defaultBlockState(), 3);
		BlockPos inputHatch = FoundryStructure.toWorld(controllerPos, facing, 1, 2, 1);
		level.setBlock(inputHatch, ModBlocks.FOUNDRY_INPUT_HATCH.defaultBlockState(), 3);
		level.setBlock(inputHatch.above(), Blocks.HOPPER.defaultBlockState(), 3);

		if (level.getBlockEntity(controllerPos) instanceof FoundryControllerBlockEntity controller) {
			controller.setItem(FoundryControllerBlockEntity.SLOT_ROCK, new ItemStack(Items.BASALT, 48));
			controller.setItem(FoundryControllerBlockEntity.SLOT_SOUL, new ItemStack(Items.SOUL_SAND, 52));
			controller.setItem(FoundryControllerBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL_BLOCK, 20));
			controller.setItem(FoundryControllerBlockEntity.SLOT_OUTPUT, new ItemStack(Items.NETHERITE_SCRAP, 3));
			// A machine that has been running for a while: hot, mid-cycle, 17 scraps so far.
			FoundryCore.Settings settings = controller.core().settings();
			controller.core().restore(settings.workHeat() + settings.workHeat() / 8, 5200, 8000, 390, 12, 20, 17L,
					17L * settings.ticksPerScrap() + 12L * settings.cycleTicks() + 390L);
		} else {
			fail("no controller block entity after building the scene");
		}

		for (int i = 0; i < BLOCKS.size(); i++) {
			player.getInventory().setItem(i, new ItemStack(BLOCKS.get(i), i == 1 ? 64 : 1));
		}

		player.getInventory().setItem(9, new ItemStack(Items.NETHERITE_SCRAP, 4));
		player.getInventory().setItem(10, new ItemStack(Items.GOLD_INGOT, 4));
		player.getInventory().setItem(11, new ItemStack(Items.NETHERITE_INGOT, 1));

		level.setDayTime(6000L);
		level.setWeatherParameters(100_000, 0, false, false);

		// Hover north-west of the foundry, looking at its front and its windowed side.
		player.getAbilities().flying = true;
		player.onUpdateAbilities();
		double x = controllerPos.getX() + 0.5 - 4.5;
		double y = controllerPos.getY() + 1.4;
		double z = controllerPos.getZ() + 0.5 - 4.5;
		player.teleportTo(level, x, y, z, -35.0F, 18.0F);
		return controllerPos;
	}

	// ------------------------------------------------------------------ plumbing

	private void check(boolean condition, String message) {
		if (!condition) {
			fail(message);
		}
	}

	private void fail(String message) {
		failures.add(message);
	}

	private static <T> T onClient(Function<Minecraft, T> job) {
		Minecraft client = Minecraft.getInstance();
		return client.submit(() -> job.apply(client)).join();
	}

	private static <T> T onServer(IntegratedServer server, Function<IntegratedServer, T> job) {
		return server.submit(() -> job.apply(server)).join();
	}

	private static void waitFor(String what, int seconds, Supplier<Boolean> condition) throws InterruptedException {
		long deadline = System.nanoTime() + seconds * 1_000_000_000L;

		while (!condition.get()) {
			if (System.nanoTime() > deadline) {
				throw new IllegalStateException("Timed out after " + seconds + " s waiting for " + what);
			}

			Thread.sleep(250L);
		}
	}

	private void waitTicks(int ticks) throws InterruptedException {
		long target = clientTicks.get() + ticks;
		waitFor(ticks + " client ticks", 120, () -> clientTicks.get() >= target);
	}

	private void screenshot(String name) throws InterruptedException {
		Consumer<Component> ignore = message -> { };
		onClient(client -> {
			File gameDirectory = client.gameDirectory;
			Screenshot.grab(gameDirectory, name, client.getMainRenderTarget(), ignore);
			return null;
		});
		// The file is written by an I/O worker.
		Thread.sleep(2_000L);
		LOGGER.info("[clienttest] screenshot saved: {}", name);
	}
}
