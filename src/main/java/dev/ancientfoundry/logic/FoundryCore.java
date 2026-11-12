package dev.ancientfoundry.logic;

/**
 * The foundry's brain: heat, fuel, cycle progress and the scrap schedule.
 *
 * <p>This class has no Minecraft imports on purpose, so the balance (how many scraps come out of
 * N ticks) can be unit-tested and simulated without the game.
 *
 * <h2>How the scrap drop works</h2>
 * Cycles are grouped in windows of {@code cyclesPerScrap} cycles. At the start of every window one
 * cycle is drawn at random; that cycle, and only that one, yields a scrap. So the drop is rare
 * (about {@code 1 / cyclesPerScrap} per cycle) and its timing is unpredictable, but every window
 * yields exactly one scrap: a long run can never fall short of the target because of bad luck.
 */
public final class FoundryCore {
	/** Everything the core needs from the outside world. */
	public interface Host {
		/** True when one unit of every ingredient is available. */
		boolean hasIngredients();

		/** True when the output can take one more scrap. */
		boolean hasOutputSpace();

		/** Consumes one fuel item and returns how many ticks it burns; 0 when there is no fuel. */
		int takeFuel();

		/** Consumes one unit of every ingredient. */
		void consumeIngredients();

		/** Adds one scrap to the output. */
		void produceScrap();

		/** Uniform random integer in {@code [0, bound)}. */
		int nextInt(int bound);
	}

	public enum Status {
		UNFORMED, PAUSED, NO_INPUT, OUTPUT_FULL, NO_FUEL, HEATING, WORKING;

		private static final Status[] VALUES = values();

		public static Status byOrdinal(int ordinal) {
			return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : UNFORMED;
		}
	}

	/**
	 * Tunables.
	 *
	 * @param cycleTicks     length of one cycle, in ticks
	 * @param cyclesPerScrap cycles per window; each window yields exactly one scrap
	 * @param workHeat       heat needed before cycles advance
	 * @param maxHeat        heat cap
	 * @param heatUp         heat gained per tick while fuel burns
	 * @param coolDown       heat lost per tick while nothing burns
	 */
	public record Settings(int cycleTicks, int cyclesPerScrap, int workHeat, int maxHeat, int heatUp, int coolDown) {
		public Settings {
			cycleTicks = Math.max(1, cycleTicks);
			cyclesPerScrap = Math.max(1, cyclesPerScrap);
			workHeat = Math.max(1, workHeat);
			maxHeat = Math.max(workHeat, maxHeat);
			heatUp = Math.max(1, heatUp);
			coolDown = Math.max(1, coolDown);
		}

		/** Ticks from cold to working temperature with fuel burning the whole time. */
		public int warmupTicks() {
			return (workHeat + heatUp - 1) / heatUp;
		}

		/** Average ticks of work per scrap. */
		public long ticksPerScrap() {
			return (long) cycleTicks * cyclesPerScrap;
		}
	}

	private final Settings settings;

	private int heat;
	private int burnTime;
	private int burnTotal;
	private int progress;
	private int windowCycle;
	private int winningCycle = -1;
	private long totalScraps;
	private long activeTicks;
	private Status status = Status.UNFORMED;

	public FoundryCore(Settings settings) {
		this.settings = settings;
	}

	/**
	 * Advances the machine by one tick.
	 *
	 * @param formed whether the multiblock is complete
	 * @param paused whether the machine is paused (redstone signal)
	 */
	public Status tick(Host host, boolean formed, boolean paused) {
		final int window = settings.cyclesPerScrap();

		if (winningCycle < 0 || winningCycle >= window || windowCycle >= window) {
			// First tick ever, or the window size changed in the config: start a fresh window.
			windowCycle = 0;
			winningCycle = host.nextInt(window);
		}

		final boolean hasInput = formed && host.hasIngredients();
		final boolean hasSpace = formed && host.hasOutputSpace();
		final boolean wantsWork = formed && !paused && hasInput && hasSpace;

		// Fire: the current fuel item always burns out; a new one is lit only when there is work.
		if (burnTime > 0) {
			burnTime--;
		}

		if (burnTime <= 0 && wantsWork) {
			int fuel = host.takeFuel();

			if (fuel > 0) {
				burnTime = fuel;
				burnTotal = fuel;
			}
		}

		final boolean burning = burnTime > 0;

		// Heat.
		if (burning && formed) {
			heat = Math.min(settings.maxHeat(), heat + settings.heatUp());
		} else {
			heat = Math.max(0, heat - settings.coolDown());
		}

		final boolean hot = heat >= settings.workHeat();

		// Work.
		if (wantsWork && hot) {
			progress++;
			activeTicks++;

			if (progress >= settings.cycleTicks()) {
				progress = 0;
				host.consumeIngredients();

				if (advanceWindow(host)) {
					totalScraps++;
					host.produceScrap();
				}
			}
		} else if (!formed || !hasInput) {
			progress = 0;
		} else if (!paused && hasSpace) {
			// Too cold: the charge slowly settles back, like a furnace that ran out of fuel.
			progress = Math.max(0, progress - 2);
		}
		// Paused or output full: progress is held.

		if (!formed) {
			status = Status.UNFORMED;
		} else if (paused) {
			status = Status.PAUSED;
		} else if (!hasInput) {
			status = Status.NO_INPUT;
		} else if (!hasSpace) {
			status = Status.OUTPUT_FULL;
		} else if (hot) {
			status = Status.WORKING;
		} else if (burning) {
			status = Status.HEATING;
		} else {
			status = Status.NO_FUEL;
		}

		return status;
	}

	/** Moves to the next cycle of the window; returns true when the finished cycle was the winner. */
	private boolean advanceWindow(Host host) {
		final int window = settings.cyclesPerScrap();
		final boolean hit = windowCycle == winningCycle;
		windowCycle++;

		if (windowCycle >= window) {
			windowCycle = 0;
			winningCycle = host.nextInt(window);
		}

		return hit;
	}

	public Settings settings() {
		return settings;
	}

	public Status status() {
		return status;
	}

	public int heat() {
		return heat;
	}

	public int burnTime() {
		return burnTime;
	}

	public int burnTotal() {
		return burnTotal;
	}

	public int progress() {
		return progress;
	}

	/** Cycles already finished in the current window. */
	public int windowCycle() {
		return windowCycle;
	}

	public int winningCycle() {
		return winningCycle;
	}

	/** True once the current window has already paid out its scrap. */
	public boolean windowScrapFound() {
		return winningCycle >= 0 && windowCycle > winningCycle;
	}

	public long totalScraps() {
		return totalScraps;
	}

	/** Ticks spent actually working (hot, fed and unpaused). */
	public long activeTicks() {
		return activeTicks;
	}

	public boolean isBurning() {
		return burnTime > 0;
	}

	public boolean isHot() {
		return heat >= settings.workHeat();
	}

	/** True while there is still state worth saving every tick. */
	public boolean isActive() {
		return burnTime > 0 || heat > 0 || progress > 0;
	}

	/** Restores saved state; out-of-range values are clamped. */
	public void restore(int heat, int burnTime, int burnTotal, int progress, int windowCycle, int winningCycle,
			long totalScraps, long activeTicks) {
		this.heat = clamp(heat, 0, settings.maxHeat());
		this.burnTime = Math.max(0, burnTime);
		this.burnTotal = Math.max(this.burnTime, burnTotal);
		this.progress = clamp(progress, 0, settings.cycleTicks() - 1);
		this.windowCycle = Math.max(0, windowCycle);
		this.winningCycle = winningCycle;
		this.totalScraps = Math.max(0L, totalScraps);
		this.activeTicks = Math.max(0L, activeTicks);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
