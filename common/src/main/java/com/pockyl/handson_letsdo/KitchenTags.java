package com.pockyl.handson_letsdo;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Item tags that say how food behaves in the kitchen, so that what happens at a station is what a cook would see. Each has
 * defaults for the Let's Do mods; packs and addons add their own items.
 */
public final class KitchenTags {
    /** A liquid in its container: poured out, the container never lies in the food. */
    public static final TagKey<Item> POURED = tag("poured");
    /** Cracked open before it goes in: the shell never goes into the food. */
    public static final TagKey<Item> CRACKED = tag("cracked");
    /** Dissolves in a soup: no pieces of it float there. */
    public static final TagKey<Item> DISSOLVES = tag("dissolves");
    /** Cooks in a soup whole and is taken out again: no pieces of it float there. */
    public static final TagKey<Item> STRAINED = tag("strained");
    /** Grains: many small pieces in a soup. */
    public static final TagKey<Item> GRAINS = tag("grains");

    private KitchenTags() {
    }

    public static boolean is(ItemStack stack, TagKey<Item> tag) {
        return !stack.isEmpty() && stack.is(tag);
    }

    /**
     * Whether {@code stack} is grain, shaken or poured in as many small bits: in {@link #GRAINS} and no food by itself. The seed tags
     * it reads also hold crops that plant themselves (Farm &amp; Charm's onion, a potato): those are whole vegetables, thrown in.
     */
    public static boolean grains(ItemStack stack) {
        return is(stack, GRAINS) && stack.getItem().getFoodProperties() == null;
    }

    private static TagKey<Item> tag(String name) {
        return TagKey.create(Registries.ITEM, HandsOnLetsDo.id(name));
    }
}
