package dev.ancientfoundry.blockentity;

import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What a hopper sees when it points at an input hatch: the controller's rock, soul and fuel
 * slots, insert-only. Each item type goes to its own slot, so one hatch can take all three.
 */
final class InputHatchContainer implements WorldlyContainer {
	private static final int[] SLOTS = {
			FoundryControllerBlockEntity.SLOT_ROCK,
			FoundryControllerBlockEntity.SLOT_SOUL,
			FoundryControllerBlockEntity.SLOT_FUEL
	};

	private final FoundryControllerBlockEntity controller;

	InputHatchContainer(FoundryControllerBlockEntity controller) {
		this.controller = controller;
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		return SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
		return controller.isFormed() && FoundryControllerBlockEntity.isValidForSlot(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
		return false;
	}

	@Override
	public int getContainerSize() {
		return SLOTS.length;
	}

	@Override
	public boolean isEmpty() {
		for (int slot : SLOTS) {
			if (!controller.getItem(slot).isEmpty()) {
				return false;
			}
		}

		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return controller.getItem(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		return controller.removeItem(slot, amount);
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return controller.removeItemNoUpdate(slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		controller.setItem(slot, stack);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return FoundryControllerBlockEntity.isValidForSlot(slot, stack);
	}

	@Override
	public void setChanged() {
		controller.setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return false;
	}

	@Override
	public void clearContent() {
		// The hatch owns nothing; clearing it must not wipe the controller.
	}
}
