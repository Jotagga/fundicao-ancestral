package dev.ancientfoundry.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/** Balance tests for the foundry logic. They need no Minecraft, only the pure core. */
class FoundryCoreTest {
	/** The shipped defaults: 30 s cycles, 35 cycles per scrap, 2 min warm-up. */
	private static final FoundryCore.Settings DEFAULTS = new FoundryCore.Settings(600, 35, 2400, 3000, 1, 2);
	private static final long TWELVE_HOURS = 12L * 60L * 60L * 20L;

	/** A foundry with endless supplies unless told otherwise. */
	private static final class TestHost implements FoundryCore.Host {
		private final Random random;
		private boolean ingredients = true;
		private boolean fuel = true;
		private int burnPerItem = 8000;
		private int outputCapacity = Integer.MAX_VALUE;
		private int scraps;
		private int cycles;
		private int fuelItems;
		private long now;
		private final List<Long> scrapTicks = new ArrayList<>();

		private TestHost(long seed) {
			this.random = new Random(seed);
		}

		@Override
		public boolean hasIngredients() {
			return ingredients;
		}

		@Override
		public boolean hasOutputSpace() {
			return scraps < outputCapacity;
		}

		@Override
		public int takeFuel() {
			if (!fuel) {
				return 0;
			}

			fuelItems++;
			return burnPerItem;
		}

		@Override
		public void consumeIngredients() {
			cycles++;
		}

		@Override
		public void produceScrap() {
			scraps++;
			scrapTicks.add(now);
		}

		@Override
		public int nextInt(int bound) {
			return random.nextInt(bound);
		}
	}

	private static void run(FoundryCore core, TestHost host, long ticks) {
		for (long i = 0; i < ticks; i++) {
			host.now++;
			core.tick(host, true, false);
		}
	}

	@Test
	void twelveHoursFromColdAlwaysGiveTenIngots() {
		for (long seed = 0; seed < 300; seed++) {
			TestHost host = new TestHost(seed);
			FoundryCore core = new FoundryCore(DEFAULTS);
			run(core, host, TWELVE_HOURS);

			assertTrue(host.scraps >= 40, "seed " + seed + ": only " + host.scraps + " scraps in 12 h");
			assertEquals(41, host.scraps, "seed " + seed);
			assertTrue(host.scrapTicks.get(39) <= TWELVE_HOURS - 20L * 60L * 15L,
					"seed " + seed + ": the 40th scrap should leave at least 15 minutes of slack");
			assertEquals(host.scraps, core.totalScraps());
		}
	}

	@Test
	void everyWindowPaysExactlyOnce() {
		TestHost host = new TestHost(1234);
		FoundryCore core = new FoundryCore(DEFAULTS);
		run(core, host, DEFAULTS.warmupTicks() - 1);
		assertEquals(FoundryCore.Status.HEATING, core.status());
		assertEquals(0, host.cycles);

		for (int window = 1; window <= 200; window++) {
			run(core, host, DEFAULTS.ticksPerScrap());
			assertEquals(window, host.scraps, "after window " + window);
			assertEquals(window * 35, host.cycles);
		}

		assertEquals(FoundryCore.Status.WORKING, core.status());
	}

	@Test
	void dropTimingIsSpreadOut() {
		TestHost host = new TestHost(99);
		FoundryCore core = new FoundryCore(DEFAULTS);
		run(core, host, DEFAULTS.warmupTicks() + DEFAULTS.ticksPerScrap() * 2000L);

		long shortest = Long.MAX_VALUE;
		long longest = 0;

		for (int i = 1; i < host.scrapTicks.size(); i++) {
			long gap = host.scrapTicks.get(i) - host.scrapTicks.get(i - 1);
			shortest = Math.min(shortest, gap);
			longest = Math.max(longest, gap);
		}

		// Two neighbouring windows can pay at their far or near ends: gaps run from 1 to 69 cycles.
		assertTrue(shortest >= 600, "shortest gap " + shortest);
		assertTrue(longest <= 69L * 600L, "longest gap " + longest);
		assertTrue(shortest <= 5L * 600L, "drops never came close together: " + shortest);
		assertTrue(longest >= 60L * 600L, "drops never came far apart: " + longest);
	}

	@Test
	void noFuelIsBurnedWithoutWork() {
		TestHost host = new TestHost(5);
		host.ingredients = false;
		FoundryCore core = new FoundryCore(DEFAULTS);
		run(core, host, 50_000);

		assertEquals(0, host.fuelItems);
		assertEquals(0, core.heat());
		assertEquals(FoundryCore.Status.NO_INPUT, core.status());
	}

	@Test
	void fullOutputStopsTheMachineWithoutLosingScraps() {
		TestHost host = new TestHost(6);
		host.outputCapacity = 3;
		FoundryCore core = new FoundryCore(DEFAULTS);
		run(core, host, TWELVE_HOURS);

		assertEquals(3, host.scraps);
		assertEquals(3, core.totalScraps());
		assertEquals(FoundryCore.Status.OUTPUT_FULL, core.status());

		// Emptying the output lets it go on: after reheating, two windows' worth of work always
		// covers the rest of the interrupted window plus the next window's winning cycle.
		host.outputCapacity = Integer.MAX_VALUE;
		run(core, host, DEFAULTS.warmupTicks() + DEFAULTS.ticksPerScrap() * 2);
		assertTrue(host.scraps >= 4, "scraps after emptying: " + host.scraps);
	}

	@Test
	void runningOutOfFuelCoolsDownAndStops() {
		TestHost host = new TestHost(7);
		FoundryCore core = new FoundryCore(DEFAULTS);
		run(core, host, DEFAULTS.warmupTicks() + 10_000);
		assertEquals(FoundryCore.Status.WORKING, core.status());

		host.fuel = false;
		run(core, host, 20_000);
		assertEquals(FoundryCore.Status.NO_FUEL, core.status());
		assertEquals(0, core.heat());
		int cyclesWhenCold = host.cycles;
		run(core, host, 20_000);
		assertEquals(cyclesWhenCold, host.cycles, "a cold foundry must not finish cycles");

		host.fuel = true;
		run(core, host, DEFAULTS.warmupTicks() + 600);
		assertEquals(FoundryCore.Status.WORKING, core.status());
		assertTrue(host.cycles > cyclesWhenCold);
	}

	@Test
	void pausedAndUnformedMachinesDoNothing() {
		TestHost host = new TestHost(8);
		FoundryCore core = new FoundryCore(DEFAULTS);

		for (int i = 0; i < 50_000; i++) {
			core.tick(host, true, true);
		}

		assertEquals(FoundryCore.Status.PAUSED, core.status());
		assertEquals(0, host.fuelItems);

		for (int i = 0; i < 50_000; i++) {
			core.tick(host, false, false);
		}

		assertEquals(FoundryCore.Status.UNFORMED, core.status());
		assertEquals(0, host.fuelItems);
		assertEquals(0, host.scraps);
	}

	@Test
	void savedStateSurvivesARestart() {
		TestHost host = new TestHost(9);
		FoundryCore core = new FoundryCore(DEFAULTS);
		run(core, host, DEFAULTS.warmupTicks() + DEFAULTS.ticksPerScrap() * 3 + 7_777);

		FoundryCore reloaded = new FoundryCore(DEFAULTS);
		reloaded.restore(core.heat(), core.burnTime(), core.burnTotal(), core.progress(), core.windowCycle(),
				core.winningCycle(), core.totalScraps(), core.activeTicks());

		// Both must behave identically from here on, given the same random numbers.
		TestHost a = new TestHost(77);
		TestHost b = new TestHost(77);
		run(core, a, 300_000);
		run(reloaded, b, 300_000);
		assertEquals(a.scraps, b.scraps);
		assertEquals(a.scrapTicks, b.scrapTicks);
		assertEquals(core.totalScraps(), reloaded.totalScraps());
	}

	@Test
	void changingTheWindowSizeStartsAFreshWindow() {
		FoundryCore core = new FoundryCore(new FoundryCore.Settings(600, 10, 2400, 3000, 1, 2));
		// Saved with a 35-cycle window: cycle 30, winner 33. Both are out of range for 10.
		core.restore(3000, 100, 8000, 0, 30, 33, 0, 0);
		TestHost host = new TestHost(10);
		run(core, host, 10L * 600L);

		assertEquals(1, host.scraps, "a 10-cycle window must pay once in its first 10 cycles");
		assertTrue(core.winningCycle() < 10);
	}
}
