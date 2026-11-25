package com.pockyl.handson_letsdo.gametest;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.satisfy.farm_and_charm.core.recipe.CookingPotRecipe;
import net.satisfy.farm_and_charm.core.recipe.RecipeUnlockManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Recipes of Farm &amp; Charm as a data pack would add them, put among the server's recipes for a test. Apart from the tests, so
 * that those load without Farm &amp; Charm.
 */
final class FarmCharmTestRecipes {
    private FarmCharmTestRecipes() {
    }

    /** A pot recipe served in a bowl that only a player who learned it can cook. */
    static void addLearnedPotRecipe(ServerLevel level, ResourceLocation id, ItemStack result, Item... inputs) {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (Item input : inputs) {
            ingredients.add(Ingredient.of(input));
        }
        List<Recipe<?>> all = without(level, id);
        all.add(new CookingPotRecipe(id, ingredients, true, new ItemStack(Items.BOWL), result, true));
        level.getRecipeManager().replaceRecipes(all);
    }

    /** {@code player} learns the recipe {@code id}, as Farm &amp; Charm's recipe book teaches it. */
    static void teach(ServerPlayer player, ServerLevel level, ResourceLocation id) {
        level.getRecipeManager().byKey(id).ifPresent(recipe -> RecipeUnlockManager.unlockRecipes(player, List.of(recipe)));
    }

    static void remove(ServerLevel level, ResourceLocation id) {
        level.getRecipeManager().replaceRecipes(without(level, id));
    }

    private static List<Recipe<?>> without(ServerLevel level, ResourceLocation id) {
        RecipeManager recipes = level.getRecipeManager();
        List<Recipe<?>> all = new ArrayList<>(recipes.getRecipes());
        all.removeIf(recipe -> recipe.getId().equals(id));
        return all;
    }
}
