package dev.ancientfoundry.menu;

import dev.ancientfoundry.blockentity.FoundryControllerBlockEntity;
import dev.ancientfoundry.logic.FoundryCore;
import dev.ancientfoundry.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The controller's inventory screen: three inputs, two results and the synced gauges. */
public class FoundryMenu extends AbstractContainerMenu {
	public static final int ROCK_X = 44;
	public static final int ROCK_Y = 20;
	public static final int SOUL_X = 66;
	public static final int SOUL_Y = 20;
	public static final int FUEL_X = 55;
	public static final int FUEL_Y = 57;
	public static final int OUTPUT_X = 128;
	public static final int OUTPUT_Y = 38;
	public static final int BYPRODUCT_X = 152;
	public static final int BYPRODUCT_Y = 38;
	public static final int INVENTORY_X = 8;
	public static final int INVENTORY_Y = 118;
	public static final int HOTBAR_Y = 176;

	private static final int MACHINE_SLOTS = FoundryControllerBlockEntity.SLOT_COUNT;
	private static final int PLAYER_START = MACHINE_SLOTS;
	private static final int HOTBAR_START = PLAYER_START + 27;
	private static final int PLAYER_END = HOTBAR_START + 9;

	private final Container container;
	private final ContainerData data;

	/** Client side: the real contents arrive through the menu sync. */
	public FoundryMenu(int containerId, Inventory playerInventory) {
		this(containerId, playerInventory, new SimpleContainer(MACHINE_SLOTS), new SimpleContainerData(FoundryControllerBlockEntity.DATA_COUNT));
	}

	public FoundryMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
		super(ModMenus.FOUNDRY, containerId);
		checkContainerSize(container, MACHINE_SLOTS);
		checkContainerDataCount(data, FoundryControllerBlockEntity.DATA_COUNT);
		this.container = container;
		this.data = data;
		container.startOpen(playerInventory.player);

		this.addSlot(new InputSlot(container, FoundryControllerBlockEntity.SLOT_ROCK, ROCK_X, ROCK_Y));
		this.addSlot(new InputSlot(container, FoundryControllerBlockEntity.SLOT_SOUL, SOUL_X, SOUL_Y));
		this.addSlot(new InputSlot(container, FoundryControllerBlockEntity.SLOT_FUEL, FUEL_X, FUEL_Y));
		this.addSlot(new ResultSlot(container, FoundryControllerBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y));
		this.addSlot(new ResultSlot(container, FoundryControllerBlockEntity.SLOT_BYPRODUCT, BYPRODUCT_X, BYPRODUCT_Y));

		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				this.addSlot(new Slot(playerInventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
			}
		}

		for (int column = 0; column < 9; column++) {
			this.addSlot(new Slot(playerInventory, column, INVENTORY_X + column * 18, HOTBAR_Y));
		}

		this.addDataSlots(data);
	}

	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		container.stopOpen(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);

		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();

		if (index < MACHINE_SLOTS) {
			// Machine -> player, filling the hotbar last.
			if (!this.moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
				return ItemStack.EMPTY;
			}

			slot.onQuickCraft(stack, original);
		} else {
			int target = targetSlotFor(stack);

			if (target >= 0) {
				if (!this.moveItemStackTo(stack, target, target + 1, false)) {
					return ItemStack.EMPTY;
				}
			} else if (index < HOTBAR_START) {
				if (!this.moveItemStackTo(stack, HOTBAR_START, PLAYER_END, false)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.moveItemStackTo(stack, PLAYER_START, HOTBAR_START, false)) {
				return ItemStack.EMPTY;
			}
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}

		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}

		slot.onTake(player, stack);
		return original;
	}

	private static int targetSlotFor(ItemStack stack) {
		for (int slot = FoundryControllerBlockEntity.SLOT_ROCK; slot <= FoundryControllerBlockEntity.SLOT_FUEL; slot++) {
			if (FoundryControllerBlockEntity.isValidForSlot(slot, stack)) {
				return slot;
			}
		}

		return -1;
	}

	// ------------------------------------------------------------------ synced gauges

	/** Heat as a fraction of the maximum, 0..1000. */
	public int heatPermille() {
		return data.get(FoundryControllerBlockEntity.DATA_HEAT_PERMILLE);
	}

	/** Working heat as a fraction of the maximum, 0..1000. */
	public int workHeatPermille() {
		return data.get(FoundryControllerBlockEntity.DATA_WORK_HEAT_PERMILLE);
	}

	public int temperature() {
		return data.get(FoundryControllerBlockEntity.DATA_TEMPERATURE);
	}

	public int burnPermille() {
		return data.get(FoundryControllerBlockEntity.DATA_BURN_PERMILLE);
	}

	public int progressPermille() {
		return data.get(FoundryControllerBlockEntity.DATA_PROGRESS_PERMILLE);
	}

	public int windowCycle() {
		return data.get(FoundryControllerBlockEntity.DATA_WINDOW_CYCLE);
	}

	public int windowSize() {
		return data.get(FoundryControllerBlockEntity.DATA_WINDOW_SIZE);
	}

	public FoundryCore.Status status() {
		return FoundryCore.Status.byOrdinal(data.get(FoundryControllerBlockEntity.DATA_STATUS));
	}

	public boolean windowScrapFound() {
		return data.get(FoundryControllerBlockEntity.DATA_WINDOW_SCRAP_FOUND) != 0;
	}

	public int totalScraps() {
		return (data.get(FoundryControllerBlockEntity.DATA_TOTAL_SCRAPS_HIGH) << 15) | data.get(FoundryControllerBlockEntity.DATA_TOTAL_SCRAPS_LOW);
	}

	public int activeMinutes() {
		return (data.get(FoundryControllerBlockEntity.DATA_ACTIVE_MINUTES_HIGH) << 15) | data.get(FoundryControllerBlockEntity.DATA_ACTIVE_MINUTES_LOW);
	}

	// ------------------------------------------------------------------ slots

	/** Accepts only what the machine slot behind it accepts. */
	private static final class InputSlot extends Slot {
		private InputSlot(Container container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return FoundryControllerBlockEntity.isValidForSlot(this.getContainerSlot(), stack);
		}
	}

	/** Take-only. */
	private static final class ResultSlot extends Slot {
		private ResultSlot(Container container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}
	}
}
