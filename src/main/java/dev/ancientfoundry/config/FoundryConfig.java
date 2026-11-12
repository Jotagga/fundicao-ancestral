package dev.ancientfoundry.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.ancientfoundry.AncientFoundry;
import dev.ancientfoundry.logic.FoundryCore;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Server-side balance settings, stored in {@code config/ancient_foundry.json}.
 *
 * <p>Defaults: a 30 s cycle and one scrap every 35 cycles, i.e. one scrap per 17.5 min of work.
 * A cold machine needs 2 min to heat up, so 12 h of real time give 41 scraps (10 ingots + 1).
 */
public final class FoundryConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = AncientFoundry.MOD_ID + ".json";

	private static FoundryConfig instance = new FoundryConfig();

	/** Length of one cycle, in ticks (20 ticks = 1 s). */
	public int cycleTicks = 600;

	/** Cycles per guaranteed scrap. Each cycle has about a 1-in-this chance of being the lucky one. */
	public int cyclesPerScrap = 35;

	/** Ticks of burning fuel needed to bring a cold foundry to working temperature. */
	public int warmupTicks = 2400;

	/** Burn time of a fuel item in the foundry, as a percentage of its furnace burn time. */
	public int fuelEfficiencyPercent = 50;

	public static FoundryConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		FoundryConfig loaded = null;

		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, FoundryConfig.class);
			} catch (IOException | JsonParseException e) {
				AncientFoundry.LOGGER.warn("Could not read {}, using defaults", path, e);
			}
		}

		if (loaded == null) {
			loaded = new FoundryConfig();
		}

		loaded.sanitize();
		instance = loaded;

		try {
			Files.createDirectories(path.getParent());

			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(loaded, writer);
			}
		} catch (IOException e) {
			AncientFoundry.LOGGER.warn("Could not write {}", path, e);
		}
	}

	private void sanitize() {
		cycleTicks = clamp(cycleTicks, 20, 72_000);
		cyclesPerScrap = clamp(cyclesPerScrap, 1, 10_000);
		warmupTicks = clamp(warmupTicks, 20, 24_000);
		fuelEfficiencyPercent = clamp(fuelEfficiencyPercent, 1, 1_000);
	}

	/** Heat rises 1 per tick while burning and falls 2 per tick otherwise; the cap sits 25% above working heat. */
	public FoundryCore.Settings toSettings() {
		int workHeat = warmupTicks;
		return new FoundryCore.Settings(cycleTicks, cyclesPerScrap, workHeat, workHeat + workHeat / 4, 1, 2);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
