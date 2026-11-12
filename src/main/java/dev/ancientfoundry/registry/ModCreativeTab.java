package dev.ancientfoundry.registry;

import dev.ancientfoundry.AncientFoundry;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModCreativeTab {
	public static final ResourceKey<CreativeModeTab> KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, AncientFoundry.id("main"));

	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY,
			FabricItemGroup.builder()
					.title(Component.translatable("itemGroup.ancient_foundry"))
					.icon(() -> new ItemStack(ModBlocks.FOUNDRY_CONTROLLER))
					.displayItems((parameters, output) -> {
						output.accept(ModBlocks.FOUNDRY_CONTROLLER);
						output.accept(ModBlocks.FOUNDRY_CASING);
						output.accept(ModBlocks.FOUNDRY_GLASS);
						output.accept(ModBlocks.ANCIENT_CORE);
						output.accept(ModBlocks.FOUNDRY_VENT);
						output.accept(ModBlocks.FOUNDRY_INPUT_HATCH);
						output.accept(ModBlocks.FOUNDRY_OUTPUT_HATCH);
					})
					.build());

	private ModCreativeTab() {
	}

	public static void init() {
	}
}
