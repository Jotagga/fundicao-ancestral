package dev.ancientfoundry.block;

import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Heat-proof window for the foundry walls. Behaves like vanilla glass but drops itself. */
public class FoundryGlassBlock extends TransparentBlock {
	public FoundryGlassBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}
}
