package dev.ancientfoundry.blockentity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.ancientfoundry.AncientFoundry;
import dev.ancientfoundry.block.FoundryControllerBlock;
import dev.ancientfoundry.config.FoundryConfig;
import dev.ancientfoundry.logic.FoundryCore;
import dev.ancientfoundry.menu.FoundryMenu;
import dev.ancientfoundry.multiblock.FoundryStructure;
import dev.ancientfoundry.registry.ModBlockEntities;
import dev.ancientfoundry.registry.ModBlocks;
import dev.ancientfoundry.registry.ModTags;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Runs the Ancient Foundry: keeps the structure checked, feeds {@link FoundryCore} from its
 * inventory and pushes results out through the output hatches.
 */
public class FoundryControllerBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
	public static final int SLOT_ROCK = 0;
	public static final int SLOT_SOUL = 1;
	public static final int SLOT_FUEL = 2;
	public static final int SLOT_OUTPUT = 3;
	public static final int SLOT_BYPRODUCT = 4;
	public static final int SLOT_COUNT = 5;

	/** Synced values, see {@link #dataAccess}. */
	public static final int DATA_HEAT_PERMILLE = 0;
	public static final int DATA_WORK_HEAT_PERMILLE = 1;
	public static final int DATA_TEMPERATURE = 2;
	public static final int DATA_BURN_PERMILLE = 3;
	public static final int DATA_PROGRESS_PERMILLE = 4;
	public static final int DATA_WINDOW_CYCLE = 5;
	public static final int DATA_WINDOW_SIZE = 6;
	public static final int DATA_STATUS = 7;
	public static final int DATA_WINDOW_SCRAP_FOUND = 8;
	public static final int DATA_TOTAL_SCRAPS_LOW = 9;
	public static final int DATA_TOTAL_SCRAPS_HIGH = 10;
	public static final int DATA_ACTIVE_MINUTES_LOW = 11;
	public static final int DATA_ACTIVE_MINUTES_HIGH = 12;
	public static final int DATA_COUNT = 13;

	/** Temperature shown to the player when the foundry is exactly at working heat. */
	public static final int WORK_TEMPERATURE_CELSIUS = 1200;

	private static final int VALIDATE_INTERVAL = 20;
	private static final int EJECT_INTERVAL = 10;
	private static final int[] ALL_SLOTS = {SLOT_ROCK, SLOT_SOUL, SLOT_FUEL, SLOT_OUTPUT, SLOT_BYPRODUCT};
	private static final int[] RESULT_SLOTS = {SLOT_OUTPUT, SLOT_BYPRODUCT};

	private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
	private final FoundryCore core = new FoundryCore(FoundryConfig.get().toSettings());
	private final CoreHost host = new CoreHost();
	private final WorldlyContainer inputProxy = new InputHatchContainer(this);

	private boolean formed;
	private final Set<BlockPos> inputHatches = new HashSet<>();
	private final List<BlockPos> outputHatches = new ArrayList<>();
	@Nullable
	private BlockPos ventPos;
	@Nullable
	private FoundryStructure.Result lastResult;
	private int tickCounter;

	/**
	 * Values the open screen needs. The vanilla packet carries each one as a 16-bit number, so
	 * everything is either scaled to 0..1000 or split into 15-bit halves.
	 */
	private final ContainerData dataAccess = new ContainerData() {
		@Override
		public int get(int index) {
			FoundryCore.Settings settings = core.settings();
			long activeMinutes = core.activeTicks() / 1200L;

			return switch (index) {
				case DATA_HEAT_PERMILLE -> permille(core.heat(), settings.maxHeat());
				case DATA_WORK_HEAT_PERMILLE -> permille(settings.workHeat(), settings.maxHeat());
				case DATA_TEMPERATURE -> (int) Math.min(30_000L, (long) core.heat() * WORK_TEMPERATURE_CELSIUS / settings.workHeat());
				case DATA_BURN_PERMILLE -> permille(core.burnTime(), core.burnTotal());
				case DATA_PROGRESS_PERMILLE -> permille(core.progress(), settings.cycleTicks());
				case DATA_WINDOW_CYCLE -> Math.min(30_000, core.windowCycle());
				case DATA_WINDOW_SIZE -> Math.min(30_000, settings.cyclesPerScrap());
				case DATA_STATUS -> formed ? core.status().ordinal() : FoundryCore.Status.UNFORMED.ordinal();
				case DATA_WINDOW_SCRAP_FOUND -> core.windowScrapFound() ? 1 : 0;
				case DATA_TOTAL_SCRAPS_LOW -> (int) (core.totalScraps() & 0x7FFF);
				case DATA_TOTAL_SCRAPS_HIGH -> (int) ((core.totalScraps() >> 15) & 0x7FFF);
				case DATA_ACTIVE_MINUTES_LOW -> (int) (activeMinutes & 0x7FFF);
				case DATA_ACTIVE_MINUTES_HIGH -> (int) ((activeMinutes >> 15) & 0x7FFF);
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			// Server-side values are computed, never set from outside.
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	public FoundryControllerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.FOUNDRY_CONTROLLER, pos, state);
	}

	private static int permille(long value, long max) {
		if (max <= 0L) {
			return 0;
		}

		return (int) Math.max(0L, Math.min(1000L, value * 1000L / max));
	}

	// ------------------------------------------------------------------ ticking

	public static void serverTick(Level level, BlockPos pos, BlockState state, FoundryControllerBlockEntity controller) {
		if (level instanceof ServerLevel serverLevel) {
			controller.tickServer(serverLevel, pos);
		}
	}

	private void tickServer(ServerLevel level, BlockPos pos) {
		if (tickCounter % VALIDATE_INTERVAL == 0 || lastResult == null) {
			revalidate(level);
		}

		tickCounter++;

		boolean paused = level.hasNeighborSignal(pos);
		FoundryCore.Status status = core.tick(host, formed, paused);

		boolean lit = formed && (status == FoundryCore.Status.WORKING || status == FoundryCore.Status.HEATING);
		BlockState state = this.getBlockState();

		if (state.getValue(FoundryControllerBlock.LIT) != lit || state.getValue(FoundryControllerBlock.FORMED) != formed) {
			level.setBlock(pos, state.setValue(FoundryControllerBlock.LIT, lit).setValue(FoundryControllerBlock.FORMED, formed), 3);
		}

		if (formed && tickCounter % EJECT_INTERVAL == 0) {
			ejectResults(level);
		}

		if (status == FoundryCore.Status.WORKING && ventPos != null && tickCounter % 8 == 0) {
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
					ventPos.getX() + 0.5, ventPos.getY() + 1.1, ventPos.getZ() + 0.5, 1, 0.12, 0.0, 0.12, 0.01);
		}

		if (core.isActive()) {
			// Heat and progress change every tick; keep the chunk marked for saving without
			// triggering comparator updates the way setChanged() would.
			level.blockEntityChanged(pos);
		}
	}

	/** Re-checks the structure right away. Skipped while part of it sits in an unloaded chunk. */
	public void revalidate(Level level) {
		BlockPos pos = this.getBlockPos();
		Direction facing = this.getBlockState().getValue(FoundryControllerBlock.FACING);

		if (!FoundryStructure.isAreaLoaded(level, pos, facing)) {
			return;
		}

		FoundryStructure.Result result = FoundryStructure.validate(level, pos, facing);
		boolean wasFormed = formed;

		lastResult = result;
		formed = result.formed();
		inputHatches.clear();
		outputHatches.clear();
		ventPos = result.vent();

		if (formed) {
			inputHatches.addAll(result.inputHatches());
			outputHatches.addAll(result.outputHatches());
		}

		if (wasFormed != formed) {
			this.setChanged();
		}
	}

	private void ejectResults(ServerLevel level) {
		if (outputHatches.isEmpty() || (items.get(SLOT_OUTPUT).isEmpty() && items.get(SLOT_BYPRODUCT).isEmpty())) {
			return;
		}

		for (BlockPos hatch : outputHatches) {
			for (Direction direction : Direction.values()) {
				BlockPos target = hatch.relative(direction);

				if (isFoundryBlock(level.getBlockState(target))) {
					continue;
				}

				Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, target, direction.getOpposite());

				if (storage == null || !storage.supportsInsertion()) {
					continue;
				}

				for (int slot : RESULT_SLOTS) {
					ItemStack stack = items.get(slot);

					if (stack.isEmpty()) {
						continue;
					}

					try (Transaction transaction = Transaction.openOuter()) {
						long moved = storage.insert(ItemVariant.of(stack), stack.getCount(), transaction);

						if (moved > 0) {
							transaction.commit();
							stack.shrink((int) moved);
							this.setChanged();
						}
					}
				}
			}
		}
	}

	private static boolean isFoundryBlock(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(AncientFoundry.MOD_ID);
	}

	// ------------------------------------------------------------------ interaction

	/** Right-click: opens the screen, or tells the player which block is still missing. */
	public void onUse(Player player) {
		Level level = this.getLevel();

		if (level == null) {
			return;
		}

		revalidate(level);

		if (formed) {
			player.openMenu(this);
			return;
		}

		FoundryStructure.Result result = lastResult;

		if (result == null || result.errorPos() == null || result.errorPart() == null) {
			player.displayClientMessage(Component.translatable("message.ancient_foundry.not_loaded").withStyle(ChatFormatting.RED), false);
			return;
		}

		BlockPos error = result.errorPos();
		Component part = Component.translatable("message.ancient_foundry.part." + result.errorPart().name().toLowerCase(Locale.ROOT))
				.withStyle(ChatFormatting.GOLD);
		Component layer = Component.translatable("message.ancient_foundry.layer." + result.errorLayer());
		player.displayClientMessage(Component.translatable("message.ancient_foundry.incomplete",
				part, error.getX(), error.getY(), error.getZ(), layer).withStyle(ChatFormatting.RED), false);

		if (level instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ParticleTypes.END_ROD,
					error.getX() + 0.5, error.getY() + 0.5, error.getZ() + 0.5, 24, 0.35, 0.35, 0.35, 0.0);
		}
	}

	// ------------------------------------------------------------------ queries

	public boolean isFormed() {
		return formed;
	}

	public FoundryCore core() {
		return core;
	}

	@Nullable
	public FoundryStructure.Result lastResult() {
		return lastResult;
	}

	public boolean ownsInputHatch(BlockPos pos) {
		return formed && inputHatches.contains(pos);
	}

	public List<BlockPos> outputHatches() {
		return List.copyOf(outputHatches);
	}

	/** The container hoppers see through an input hatch. Always the same instance. */
	public WorldlyContainer getInputProxy() {
		return inputProxy;
	}

	/**
	 * Finds the formed foundry an input hatch belongs to, or null.
	 *
	 * <p>A hatch sits in a wall or in the roof, so its controller is at most 4 blocks away
	 * sideways and up to 2 blocks below.
	 */
	@Nullable
	public static FoundryControllerBlockEntity findForInputHatch(LevelAccessor level, BlockPos hatch) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				if (!level.hasChunk((hatch.getX() + dx) >> 4, (hatch.getZ() + dz) >> 4)) {
					continue;
				}

				for (int dy = 0; dy >= -2; dy--) {
					cursor.set(hatch.getX() + dx, hatch.getY() + dy, hatch.getZ() + dz);

					if (level.getBlockState(cursor).is(ModBlocks.FOUNDRY_CONTROLLER)
							&& level.getBlockEntity(cursor) instanceof FoundryControllerBlockEntity controller
							&& controller.ownsInputHatch(hatch)) {
						return controller;
					}
				}
			}
		}

		return null;
	}

	// ------------------------------------------------------------------ core host

	private final class CoreHost implements FoundryCore.Host {
		@Override
		public boolean hasIngredients() {
			return !items.get(SLOT_ROCK).isEmpty() && !items.get(SLOT_SOUL).isEmpty();
		}

		@Override
		public boolean hasOutputSpace() {
			ItemStack output = items.get(SLOT_OUTPUT);
			return output.isEmpty() || (output.is(Items.NETHERITE_SCRAP) && output.getCount() < output.getMaxStackSize());
		}

		@Override
		public int takeFuel() {
			ItemStack fuel = items.get(SLOT_FUEL);

			if (fuel.isEmpty()) {
				return 0;
			}

			Item item = fuel.getItem();
			int burnTime = AbstractFurnaceBlockEntity.getFuel().getOrDefault(item, 0);

			if (burnTime <= 0) {
				return 0;
			}

			fuel.shrink(1);

			if (item.hasCraftingRemainingItem()) {
				Item remainder = item.getCraftingRemainingItem();

				if (remainder != null) {
					addByproduct(new ItemStack(remainder));
				}
			}

			setChanged();
			return (int) Math.max(1L, (long) burnTime * FoundryConfig.get().fuelEfficiencyPercent / 100L);
		}

		@Override
		public void consumeIngredients() {
			items.get(SLOT_ROCK).shrink(1);
			items.get(SLOT_SOUL).shrink(1);
			setChanged();
		}

		@Override
		public void produceScrap() {
			ItemStack output = items.get(SLOT_OUTPUT);

			if (output.isEmpty()) {
				items.set(SLOT_OUTPUT, new ItemStack(Items.NETHERITE_SCRAP));
			} else {
				output.grow(1);
			}

			setChanged();

			if (getLevel() instanceof ServerLevel serverLevel) {
				BlockPos pos = getBlockPos();
				serverLevel.playSound(null, pos, SoundEvents.ANCIENT_DEBRIS_BREAK, SoundSource.BLOCKS, 1.0F, 0.7F);
				serverLevel.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.6F, 0.6F);

				BlockPos burst = ventPos != null ? ventPos.above() : pos.above();
				serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
						burst.getX() + 0.5, burst.getY() + 0.2, burst.getZ() + 0.5, 30, 0.2, 0.3, 0.2, 0.03);
			}
		}

		@Override
		public int nextInt(int bound) {
			Level level = getLevel();
			return level != null ? level.getRandom().nextInt(bound) : (int) (Math.random() * bound);
		}
	}

	/** Puts a leftover (an empty bucket, say) in the by-product slot, or drops it in front of the controller. */
	private void addByproduct(ItemStack stack) {
		ItemStack current = items.get(SLOT_BYPRODUCT);

		if (current.isEmpty()) {
			items.set(SLOT_BYPRODUCT, stack);
			return;
		}

		if (ItemStack.isSameItemSameComponents(current, stack) && current.getCount() + stack.getCount() <= current.getMaxStackSize()) {
			current.grow(stack.getCount());
			return;
		}

		Level level = this.getLevel();

		if (level != null) {
			BlockPos front = this.getBlockPos().relative(this.getBlockState().getValue(FoundryControllerBlock.FACING));
			Containers.dropItemStack(level, front.getX() + 0.5, front.getY() + 0.5, front.getZ() + 0.5, stack);
		}
	}

	// ------------------------------------------------------------------ container

	/** Which items a slot accepts. Result slots accept nothing from outside. */
	public static boolean isValidForSlot(int slot, ItemStack stack) {
		return switch (slot) {
			case SLOT_ROCK -> stack.is(ModTags.FOUNDRY_ROCK);
			case SLOT_SOUL -> stack.is(ModTags.FOUNDRY_SOUL);
			case SLOT_FUEL -> AbstractFurnaceBlockEntity.isFuel(stack);
			default -> false;
		};
	}

	@Override
	public int getContainerSize() {
		return SLOT_COUNT;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				return false;
			}
		}

		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack removed = ContainerHelper.removeItem(items, slot, amount);

		if (!removed.isEmpty()) {
			this.setChanged();
		}

		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		items.set(slot, stack);
		stack.limitSize(this.getMaxStackSize(stack));
		this.setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return isValidForSlot(slot, stack);
	}

	@Override
	public void clearContent() {
		items.clear();
		this.setChanged();
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		return ALL_SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
		return isValidForSlot(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
		return slot == SLOT_OUTPUT || slot == SLOT_BYPRODUCT;
	}

	// ------------------------------------------------------------------ menu

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.ancient_foundry.foundry");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new FoundryMenu(containerId, playerInventory, this, dataAccess);
	}

	public ContainerData dataAccess() {
		return dataAccess;
	}

	// ------------------------------------------------------------------ saving

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ContainerHelper.saveAllItems(tag, items, registries);
		tag.putBoolean("Formed", formed);
		tag.putInt("Heat", core.heat());
		tag.putInt("BurnTime", core.burnTime());
		tag.putInt("BurnTotal", core.burnTotal());
		tag.putInt("Progress", core.progress());
		tag.putInt("WindowCycle", core.windowCycle());
		tag.putInt("WinningCycle", core.winningCycle());
		tag.putLong("TotalScraps", core.totalScraps());
		tag.putLong("ActiveTicks", core.activeTicks());
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		items.clear();
		ContainerHelper.loadAllItems(tag, items, registries);
		formed = tag.getBoolean("Formed");
		core.restore(tag.getInt("Heat"), tag.getInt("BurnTime"), tag.getInt("BurnTotal"), tag.getInt("Progress"),
				tag.getInt("WindowCycle"), tag.contains("WinningCycle") ? tag.getInt("WinningCycle") : -1,
				tag.getLong("TotalScraps"), tag.getLong("ActiveTicks"));
	}
}
