package com.pockyl.handson_letsdo.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.satisfy.herbalbrews.core.recipe.TeaKettleRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What only HerbalBrews' own classes tell: the drinks of its tea kettle as their recipes say them. The one place that names
 * them, used only where HerbalBrews is installed. Its item tags and the measures its kettle is built with are here too.
 */
public final class HerbalTeas {
    public static final String MOD_ID = "herbalbrews";
    @SuppressWarnings("removal")
    private static final ResourceLocation RECIPES = new ResourceLocation(MOD_ID, "kettle_brewing");

    /** What the kettle's bottle place takes, what feeds its heat, and the two measures of water: a bottle's and a bucket's. */
    public static final TagKey<Item> BOTTLES = tag("container_items");
    public static final TagKey<Item> HEAT_ITEMS = tag("heat_items");
    public static final TagKey<Item> SMALL_WATER = tag("small_water_fill");
    public static final TagKey<Item> LARGE_WATER = tag("large_water_fill");
    /** The kettle's own numbers: how much water each measure is, of {@link Kettle#FULL}; how much heat one heat item gives. */
    public static final int SMALL_AMOUNT = 25;
    public static final int LARGE_AMOUNT = 50;
    public static final int HEAT_PER_ITEM = 35;
    /** The kettle burns a heat item only while it is cooler than this; standing on something hot it is never cooler than that. */
    public static final int HEAT_FED_BELOW = 70;
    public static final int HEAT_OF_A_FIRE = 30;

    /**
     * The drinks read from each recipe manager (the server's and a client's), with the recipes they were read from: a server
     * makes a new manager on a reload, a client's manager is given new recipes, and either way they are read again.
     */
    private static final Map<RecipeManager, Read> READ = new WeakHashMap<>();

    private record Read(List<Recipe<Container>> from, List<Tea> teas) {
        boolean isOf(List<Recipe<Container>> recipes) {
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

    private HerbalTeas() {
    }

    @SuppressWarnings("removal")
    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, new ResourceLocation(MOD_ID, path));
    }

    /** How much water pouring {@code stack} into the kettle gives; 0 for anything that is no water to it. */
    public static int waterOf(ItemStack stack) {
        return stack.isEmpty() ? 0 : stack.is(SMALL_WATER) ? SMALL_AMOUNT : stack.is(LARGE_WATER) ? LARGE_AMOUNT : 0;
    }

    /** Every drink of the kettle now, read from the game's recipes as they are: datapacks and scripts change them. */
    @SuppressWarnings("unchecked")
    public static synchronized List<Tea> all(Level level) {
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(RECIPES);
        if (type == null) {
            return List.of();
        }
        List<Recipe<Container>> recipes = level.getRecipeManager().getAllRecipesFor((RecipeType<Recipe<Container>>) type);
        Read known = READ.get(level.getRecipeManager());
        if (known == null || !known.isOf(recipes)) {
            known = new Read(List.copyOf(recipes), List.copyOf(read(level, recipes)));
            READ.put(level.getRecipeManager(), known);
        }
        return known.teas();
    }

    private static List<Tea> read(Level level, List<Recipe<Container>> recipes) {
        List<Tea> teas = new ArrayList<>();
        for (Recipe<?> recipe : recipes) {
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (result.isEmpty() || !(recipe instanceof TeaKettleRecipe brewing)) {
                continue;
            }
            List<Ingredient> ingredients = new ArrayList<>();
            List<Ingredient> bottles = new ArrayList<>();
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (!ingredient.isEmpty()) {
                    (isBottle(ingredient) ? bottles : ingredients).add(ingredient);
                }
            }
            teas.add(new Tea(recipe.getId(), List.copyOf(ingredients), List.copyOf(bottles), result, Math.max(0, brewing.getRequiredWater()),
                    Math.max(0, brewing.getRequiredHeat()), Math.max(1, brewing.getRequiredDuration())));
        }
        return teas;
    }

    /** An ingredient that only bottles of the kettle's bottle place are: it goes there, not into the water. */
    private static boolean isBottle(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        return items.length > 0 && Arrays.stream(items).allMatch(stack -> stack.is(BOTTLES));
    }
}
