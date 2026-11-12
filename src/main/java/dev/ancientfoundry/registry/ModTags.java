package dev.ancientfoundry.registry;

import dev.ancientfoundry.AncientFoundry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModTags {
	/** Nether rock the foundry crushes: one per cycle. */
	public static final TagKey<Item> FOUNDRY_ROCK = TagKey.create(Registries.ITEM, AncientFoundry.id("foundry_rock"));

	/** Soul matter the foundry infuses into the rock: one per cycle. */
	public static final TagKey<Item> FOUNDRY_SOUL = TagKey.create(Registries.ITEM, AncientFoundry.id("foundry_soul"));

	private ModTags() {
	}

	public static void init() {
	}
}
