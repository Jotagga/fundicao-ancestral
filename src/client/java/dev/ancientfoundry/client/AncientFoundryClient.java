package dev.ancientfoundry.client;

import dev.ancientfoundry.registry.ModBlocks;
import dev.ancientfoundry.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;

public final class AncientFoundryClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.FOUNDRY, FoundryScreen::new);
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.FOUNDRY_GLASS, RenderType.translucent());
	}
}
