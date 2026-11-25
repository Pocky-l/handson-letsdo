package com.pockyl.handson_letsdo.station;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/** The crafting bowl's recipes as the live recipe manager has them: up to four ingredients, one a slot, and their result. */
public final class BowlRecipes {
    @SuppressWarnings("removal")
    public static final ResourceLocation TYPE = new ResourceLocation(FarmCharmDishes.MOD_ID, "crafting_bowl");

    private BowlRecipes() {
    }

    public static List<Dish> all(Level level) {
        return FarmCharmDishes.all(level, TYPE);
    }

    /** The first recipe the four stacks make, one ingredient a slot, as the bowl reads them; null for none. */
    public static Dish reads(List<Dish> recipes, List<ItemStack> stacks) {
        for (Dish recipe : recipes) {
            if (!recipe.ingredients().isEmpty() && StationRecipes.matches(recipe.ingredients(), stacks)) {
                return recipe;
            }
        }
        return null;
    }

    /** Whether some recipe of the bowl uses {@code stack}. */
    public static boolean uses(List<Dish> recipes, ItemStack stack) {
        return !stack.isEmpty() && recipes.stream().anyMatch(recipe -> recipe.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack)));
    }
}
