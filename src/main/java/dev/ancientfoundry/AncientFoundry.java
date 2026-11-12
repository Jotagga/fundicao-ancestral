package dev.ancientfoundry;

import dev.ancientfoundry.config.FoundryConfig;
import dev.ancientfoundry.registry.ModBlockEntities;
import dev.ancientfoundry.registry.ModBlocks;
import dev.ancientfoundry.registry.ModCreativeTab;
import dev.ancientfoundry.registry.ModMenus;
import dev.ancientfoundry.registry.ModTags;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AncientFoundry implements ModInitializer {
	public static final String MOD_ID = "ancient_foundry";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		FoundryConfig.load();

		// Order matters: block entities and the creative tab refer to the blocks.
		ModTags.init();
		ModBlocks.init();
		ModBlockEntities.init();
		ModMenus.init();
		ModCreativeTab.init();

		FoundryConfig config = FoundryConfig.get();
		LOGGER.info("Ancient Foundry loaded: one netherite scrap every {} cycles of {} ticks ({} scraps per 12 h of work)",
				config.cyclesPerScrap, config.cycleTicks, 864_000L / config.toSettings().ticksPerScrap());
	}
}
