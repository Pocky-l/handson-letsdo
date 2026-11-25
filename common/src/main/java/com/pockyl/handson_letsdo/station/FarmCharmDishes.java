package com.pockyl.handson_letsdo.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.satisfy.farm_and_charm.core.recipe.CookingPotRecipe;
import net.satisfy.farm_and_charm.core.recipe.MincerRecipe;
import net.satisfy.foundation.recipe.RecipeUnlockManager;
import net.satisfy.farm_and_charm.core.recipe.RoasterRecipe;
import net.satisfy.farm_and_charm.core.recipe.StoveRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What only Farm &amp; Charm's own classes tell: the dishes of its pot, its roaster, its stove, its mincer and its crafting bowl as
 * their recipes say them, what the mincer grinds, and which recipes a player has learned. The one place that names them, used
 * only where Farm &amp; Charm is installed.
 */
public final class FarmCharmDishes {
    public static final String MOD_ID = "farm_and_charm";

    /**
     * The dishes read from each recipe manager (the server's and a client's), by recipe type, with the recipes they were read
     * from: a server makes a new manager on a reload, a client's manager is given new recipes, and either way they are read again.
     */
    private static final Map<RecipeManager, Map<ResourceLocation, Read>> READ = new WeakHashMap<>();

    private record Read(List<RecipeHolder<?>> from, List<Dish> dishes) {
        boolean isOf(List<RecipeHolder<?>> recipes) {
            if (recipes.size() != from.size()) {
                return false;
            }
            for (int i = 0; i < recipes.size(); i++) {
                if (recipes.get(i) != from.get(i)) {
                    return false;
                }
            }
            return true;
        }
    }

    private FarmCharmDishes() {
    }

    /** Every dish of the recipe type {@code type} now, read from the game's recipes as they are: datapacks and scripts change them. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static synchronized List<Dish> all(Level level, ResourceLocation type) {
        RecipeType<?> recipeType = BuiltInRegistries.RECIPE_TYPE.get(type);
        if (recipeType == null) {
            return List.of();
        }
        List<RecipeHolder<?>> recipes = level.getRecipeManager().getAllRecipesFor((RecipeType) recipeType);
        Map<ResourceLocation, Read> read = READ.computeIfAbsent(level.getRecipeManager(), manager -> new HashMap<>());
        Read known = read.get(type);
        if (known == null || !known.isOf(recipes)) {
            known = new Read(List.copyOf(recipes), List.copyOf(read(level, recipes)));
            read.put(type, known);
        }
        return known.dishes();
    }

    private static List<Dish> read(Level level, List<RecipeHolder<?>> recipes) {
        List<Dish> dishes = new ArrayList<>();
        for (RecipeHolder<?> holder : recipes) {
            Recipe<?> recipe = holder.value();
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (result.isEmpty()) {
                continue;
            }
            if (recipe instanceof CookingPotRecipe pot) {
                dishes.add(new Dish(holder.id(), List.copyOf(recipe.getIngredients()),
                        pot.isContainerRequired() ? pot.getContainerItem().copyWithCount(1) : ItemStack.EMPTY, result, pot.requiresLearning()));
            } else if (recipe instanceof RoasterRecipe roast) {
                dishes.add(new Dish(holder.id(), List.copyOf(recipe.getIngredients()), roast.getContainer().copyWithCount(1), result,
                        roast.requiresLearning()));
            } else if (recipe instanceof StoveRecipe baked) {
                dishes.add(new Dish(holder.id(), List.copyOf(recipe.getIngredients()), ItemStack.EMPTY, result, baked.requiresLearning()));
            } else {
                // The mincer and the crafting bowl: their ingredients and result as every recipe gives them, nothing to learn.
                dishes.add(new Dish(holder.id(), List.copyOf(recipe.getIngredients()), ItemStack.EMPTY, result, false));
            }
        }
        return dishes;
    }

    /**
     * What the mincer's recipe {@code id} grinds, as its recipe says it: {@code MEAT}, {@code WOOD}, {@code STONE}, {@code METAL} or
     * anything else; empty for a recipe that is none of the mincer's.
     */
    public static String mincedKind(Level level, ResourceLocation id) {
        return level.getRecipeManager().byKey(id).map(RecipeHolder::value).filter(MincerRecipe.class::isInstance).map(recipe -> ((MincerRecipe) recipe).getRecipeType())
                .orElse("");
    }

    /**
     * Whether {@code player} may not make a recipe of the type {@code type} that wants learning: Farm &amp; Charm 1.1.x asks its lock
     * by the recipe's type, not by the recipe. Nobody who is not there has learned it.
     */
    public static boolean isLocked(Player player, ResourceLocation type) {
        return !(player instanceof ServerPlayer server) || RecipeUnlockManager.isRecipeLocked(server, type);
    }
}
