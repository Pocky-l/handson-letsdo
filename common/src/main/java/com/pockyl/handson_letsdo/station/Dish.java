package com.pockyl.handson_letsdo.station;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * A dish one of Farm &amp; Charm's stations makes: its ingredients, one a slot; what a serving is filled into (a bowl, a bottle;
 * empty for a dish that needs no vessel); what one serving is, which may be several items; and whether only a player who learned
 * the recipe can make it.
 */
public record Dish(ResourceLocation id, List<Ingredient> ingredients, ItemStack vessel, ItemStack result, boolean learned) {
    /** Whether a serving is filled into {@code stack}. */
    public boolean servedIn(ItemStack stack) {
        return !vessel.isEmpty() && stack.is(vessel.getItem());
    }
}
