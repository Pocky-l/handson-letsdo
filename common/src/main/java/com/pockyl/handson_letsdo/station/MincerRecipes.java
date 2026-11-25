package com.pockyl.handson_letsdo.station;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/** The mincer's recipes as the live recipe manager has them: one ingredient each, the result of one item. */
public final class MincerRecipes {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(FarmCharmDishes.MOD_ID, "mincer");

    private MincerRecipes() {
    }

    public static List<Dish> all(Level level) {
        return FarmCharmDishes.all(level, TYPE);
    }

    /** The recipe that minces {@code stack}, the first as the mincer looks them up; null for none. */
    public static Dish taking(List<Dish> recipes, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (Dish recipe : recipes) {
            if (!recipe.ingredients().isEmpty() && recipe.ingredients().get(0).test(stack)) {
                return recipe;
            }
        }
        return null;
    }
}
