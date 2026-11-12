package dev.ancientfoundry.multiblock;

import java.util.ArrayList;
import java.util.List;

import dev.ancientfoundry.block.FoundryControllerBlock;
import dev.ancientfoundry.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Shape of the Ancient Foundry: 5 wide, 5 deep, 4 tall, with the controller in the middle of the
 * front wall, one block above the base.
 *
 * <p>Local coordinates are relative to the controller: {@code dx} runs sideways (-2..2), {@code dy}
 * upwards (-1..2) and {@code dz} into the structure, away from the controller's face (0..4).
 *
 * <pre>
 * dy = -1 (base)    dy = 0            dy = 1            dy = 2 (roof)
 * F F F F F         F W W W F         F W W W F         F F F F F
 * F M M M F         W L L L W         W L L L W         F W W W F
 * F M M M F         W L K L W         W L L L W         F W V W F
 * F M M M F         W L L L W         W L L L W         F W W W F
 * F F F F F         F W C W F         F W W W F         F F F F F
 *      front             front             front             front
 * </pre>
 * F = frame (casing only), W = wall (casing, glass or a hatch), M = magma block, L = lava source,
 * K = ancient core, V = vent (needs air above), C = controller.
 */
public final class FoundryStructure {
	public static final int HALF_WIDTH = 2;
	public static final int DEPTH = 5;
	public static final int MIN_Y = -1;
	public static final int MAX_Y = 2;

	/** What a position of the structure must hold. */
	public enum Part {
		FRAME, WALL, MAGMA, LAVA, CORE, VENT, CONTROLLER, AIR
	}

	/**
	 * Outcome of a structure check.
	 *
	 * @param formed        true when every block is in place
	 * @param inputHatches  input hatches found in the walls (only when formed)
	 * @param outputHatches output hatches found in the walls (only when formed)
	 * @param vent          position of the vent (only when formed)
	 * @param errorPos      first wrong position, scanning from the base up (only when not formed)
	 * @param errorPart     what that position should hold
	 * @param errorLayer    layer of the first wrong position, 1 = base .. 4 = roof, 5 = above the vent
	 */
	public record Result(boolean formed, List<BlockPos> inputHatches, List<BlockPos> outputHatches,
			@Nullable BlockPos vent, @Nullable BlockPos errorPos, @Nullable Part errorPart, int errorLayer) {
	}

	private FoundryStructure() {
	}

	/** What belongs at a local position; only valid inside the 5x4x5 box. */
	public static Part partAt(int dx, int dy, int dz) {
		boolean sideEdge = Math.abs(dx) == HALF_WIDTH;
		boolean depthEdge = dz == 0 || dz == DEPTH - 1;
		boolean inner = !sideEdge && !depthEdge;

		if (dy == MIN_Y) {
			return inner ? Part.MAGMA : Part.FRAME;
		}

		if (dy == MAX_Y) {
			if (!inner) {
				return Part.FRAME;
			}

			return dx == 0 && dz == 2 ? Part.VENT : Part.WALL;
		}

		// The two chamber layers.
		if (inner) {
			return dy == 0 && dx == 0 && dz == 2 ? Part.CORE : Part.LAVA;
		}

		if (sideEdge && depthEdge) {
			return Part.FRAME;
		}

		return dy == 0 && dx == 0 && dz == 0 ? Part.CONTROLLER : Part.WALL;
	}

	/** Converts a local position to a world position for a controller facing {@code facing}. */
	public static BlockPos toWorld(BlockPos controller, Direction facing, int dx, int dy, int dz) {
		Direction back = facing.getOpposite();
		Direction side = facing.getCounterClockWise();
		return controller.relative(back, dz).relative(side, dx).above(dy);
	}

	/** True when every chunk the structure touches is loaded, so checking it will not load chunks. */
	public static boolean isAreaLoaded(Level level, BlockPos controller, Direction facing) {
		return level.isLoaded(toWorld(controller, facing, -HALF_WIDTH, 0, 0))
				&& level.isLoaded(toWorld(controller, facing, HALF_WIDTH, 0, 0))
				&& level.isLoaded(toWorld(controller, facing, -HALF_WIDTH, 0, DEPTH - 1))
				&& level.isLoaded(toWorld(controller, facing, HALF_WIDTH, 0, DEPTH - 1));
	}

	public static boolean matches(Part part, BlockState state) {
		return switch (part) {
			case FRAME -> state.is(ModBlocks.FOUNDRY_CASING);
			case WALL -> state.is(ModBlocks.FOUNDRY_CASING) || state.is(ModBlocks.FOUNDRY_GLASS)
					|| state.is(ModBlocks.FOUNDRY_INPUT_HATCH) || state.is(ModBlocks.FOUNDRY_OUTPUT_HATCH);
			case MAGMA -> state.is(Blocks.MAGMA_BLOCK);
			case LAVA -> state.is(Blocks.LAVA) && state.getFluidState().isSource();
			case CORE -> state.is(ModBlocks.ANCIENT_CORE);
			case VENT -> state.is(ModBlocks.FOUNDRY_VENT);
			case CONTROLLER -> state.is(ModBlocks.FOUNDRY_CONTROLLER);
			case AIR -> state.isAir();
		};
	}

	/** Checks the whole structure, base first, and reports the first wrong block if there is one. */
	public static Result validate(BlockGetter level, BlockPos controller, Direction facing) {
		List<BlockPos> inputs = new ArrayList<>();
		List<BlockPos> outputs = new ArrayList<>();
		BlockPos vent = null;

		for (int dy = MIN_Y; dy <= MAX_Y; dy++) {
			for (int dz = 0; dz < DEPTH; dz++) {
				for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH; dx++) {
					Part part = partAt(dx, dy, dz);

					if (part == Part.CONTROLLER) {
						continue;
					}

					BlockPos pos = toWorld(controller, facing, dx, dy, dz);
					BlockState state = level.getBlockState(pos);

					if (!matches(part, state)) {
						return new Result(false, List.of(), List.of(), null, pos, part, dy - MIN_Y + 1);
					}

					if (part == Part.VENT) {
						vent = pos;
					} else if (state.is(ModBlocks.FOUNDRY_INPUT_HATCH)) {
						inputs.add(pos);
					} else if (state.is(ModBlocks.FOUNDRY_OUTPUT_HATCH)) {
						outputs.add(pos);
					}
				}
			}
		}

		BlockPos aboveVent = toWorld(controller, facing, 0, MAX_Y + 1, 2);

		if (!matches(Part.AIR, level.getBlockState(aboveVent))) {
			return new Result(false, List.of(), List.of(), null, aboveVent, Part.AIR, MAX_Y - MIN_Y + 2);
		}

		return new Result(true, List.copyOf(inputs), List.copyOf(outputs), vent, null, null, 0);
	}

	/**
	 * Builds a complete foundry, controller included, with plain casing in every wall position.
	 * Used by the game tests; handy for creative showcases too.
	 */
	public static void place(Level level, BlockPos controller, Direction facing) {
		for (int dy = MIN_Y; dy <= MAX_Y; dy++) {
			for (int dz = 0; dz < DEPTH; dz++) {
				for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH; dx++) {
					BlockPos pos = toWorld(controller, facing, dx, dy, dz);
					BlockState state = switch (partAt(dx, dy, dz)) {
						case FRAME, WALL -> ModBlocks.FOUNDRY_CASING.defaultBlockState();
						case MAGMA -> Blocks.MAGMA_BLOCK.defaultBlockState();
						case LAVA -> Blocks.LAVA.defaultBlockState();
						case CORE -> ModBlocks.ANCIENT_CORE.defaultBlockState();
						case VENT -> ModBlocks.FOUNDRY_VENT.defaultBlockState();
						case CONTROLLER -> ModBlocks.FOUNDRY_CONTROLLER.defaultBlockState()
								.setValue(FoundryControllerBlock.FACING, facing);
						case AIR -> Blocks.AIR.defaultBlockState();
					};
					level.setBlock(pos, state, 3);
				}
			}
		}

		level.setBlock(toWorld(controller, facing, 0, MAX_Y + 1, 2), Blocks.AIR.defaultBlockState(), 3);
	}
}
