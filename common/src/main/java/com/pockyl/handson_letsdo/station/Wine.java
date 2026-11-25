package com.pockyl.handson_letsdo.station;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * A wine the fermentation barrel makes: up to three ingredients, one a slot, in so much of a juice, with or without an empty
 * wine bottle to fill.
 */
public record Wine(ResourceLocation id, List<Ingredient> ingredients, String juice, int juiceAmount, boolean bottle, ItemStack result) {
}
