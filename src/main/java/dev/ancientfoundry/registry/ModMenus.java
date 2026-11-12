package dev.ancientfoundry.registry;

import dev.ancientfoundry.AncientFoundry;
import dev.ancientfoundry.menu.FoundryMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
	public static final MenuType<FoundryMenu> FOUNDRY = Registry.register(
			BuiltInRegistries.MENU,
			AncientFoundry.id("foundry"),
			new MenuType<FoundryMenu>(FoundryMenu::new, FeatureFlags.VANILLA_SET));

	private ModMenus() {
	}

	public static void init() {
	}
}
