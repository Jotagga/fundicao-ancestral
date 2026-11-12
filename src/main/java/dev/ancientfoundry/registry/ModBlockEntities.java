package dev.ancientfoundry.registry;

import dev.ancientfoundry.AncientFoundry;
import dev.ancientfoundry.blockentity.FoundryControllerBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<FoundryControllerBlockEntity> FOUNDRY_CONTROLLER = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			AncientFoundry.id("foundry_controller"),
			BlockEntityType.Builder.of(FoundryControllerBlockEntity::new, ModBlocks.FOUNDRY_CONTROLLER).build(null));

	private ModBlockEntities() {
	}

	public static void init() {
	}
}
