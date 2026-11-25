package com.pockyl.handson_letsdo.station;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * A drink the tea kettle brews: what goes into the water, the bottles it is brewed into (its recipe's ingredients that are
 * bottles of the kettle's bottle place; none for a drink made of another drink), how much water a bottle of it takes and how
 * hot the kettle must be, both of a hundred, and the ticks one bottle takes.
 */
public record Tea(ResourceLocation id, List<Ingredient> ingredients, List<Ingredient> bottles, ItemStack result, int water, int heat, int time) {
}
