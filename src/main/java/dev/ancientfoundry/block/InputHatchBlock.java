package dev.ancientfoundry.block;

import dev.ancientfoundry.blockentity.FoundryControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A wall block that lets hoppers, droppers and pipes feed the foundry.
 *
 * <p>The hatch stores nothing itself: while it is part of a formed foundry it exposes the
 * controller's rock, soul and fuel slots. Outside a formed foundry it is not a container at all.
 */
public class InputHatchBlock extends Block implements WorldlyContainerHolder {
	public InputHatchBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	@Nullable
	public WorldlyContainer getContainer(BlockState state, LevelAccessor level, BlockPos pos) {
		FoundryControllerBlockEntity controller = FoundryControllerBlockEntity.findForInputHatch(level, pos);
		return controller == null ? null : controller.getInputProxy();
	}
}
