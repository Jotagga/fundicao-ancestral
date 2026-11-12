package dev.ancientfoundry.registry;

import dev.ancientfoundry.AncientFoundry;
import dev.ancientfoundry.block.FoundryControllerBlock;
import dev.ancientfoundry.block.FoundryGlassBlock;
import dev.ancientfoundry.block.InputHatchBlock;
import dev.ancientfoundry.item.TooltipBlockItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	public static final Block FOUNDRY_CASING = register("foundry_casing", new Block(machine()));

	public static final Block FOUNDRY_GLASS = register("foundry_glass", new FoundryGlassBlock(
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BLACK)
					.instrument(NoteBlockInstrument.HAT)
					.requiresCorrectToolForDrops()
					.strength(2.0F, 9.0F)
					.sound(SoundType.GLASS)
					.noOcclusion()
					.isValidSpawn((state, level, pos, entityType) -> false)
					.isRedstoneConductor((state, level, pos) -> false)
					.isSuffocating((state, level, pos) -> false)
					.isViewBlocking((state, level, pos) -> false)));

	public static final Block FOUNDRY_CONTROLLER = register("foundry_controller", new FoundryControllerBlock(
			machine().lightLevel(ModBlocks::controllerLight)));

	public static final Block FOUNDRY_INPUT_HATCH = register("foundry_input_hatch", new InputHatchBlock(machine()));

	public static final Block FOUNDRY_OUTPUT_HATCH = register("foundry_output_hatch", new Block(machine()));

	public static final Block FOUNDRY_VENT = register("foundry_vent", new Block(machine()));

	public static final Block ANCIENT_CORE = register("ancient_core", new Block(
			machine().mapColor(MapColor.COLOR_CYAN).strength(5.0F, 1200.0F).lightLevel(state -> 10)));

	private ModBlocks() {
	}

	private static BlockBehaviour.Properties machine() {
		return BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_BLACK)
				.instrument(NoteBlockInstrument.BASEDRUM)
				.requiresCorrectToolForDrops()
				.strength(3.5F, 9.0F)
				.sound(SoundType.NETHERITE_BLOCK);
	}

	private static int controllerLight(BlockState state) {
		return state.getValue(FoundryControllerBlock.LIT) ? 13 : 0;
	}

	private static <T extends Block> T register(String name, T block) {
		ResourceLocation id = AncientFoundry.id(name);
		Registry.register(BuiltInRegistries.BLOCK, id, block);
		Registry.register(BuiltInRegistries.ITEM, id, new TooltipBlockItem(block, new Item.Properties()));
		return block;
	}

	/** Forces class loading, which runs the registrations above. */
	public static void init() {
	}
}
