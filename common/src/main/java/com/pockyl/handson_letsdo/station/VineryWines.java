package com.pockyl.handson_letsdo.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.satisfy.vinery.core.block.entity.FermentationBarrelBlockEntity;
import net.satisfy.vinery.core.recipe.FermentationBarrelRecipe;
import net.satisfy.vinery.core.util.JuiceUtil;
import net.satisfy.vinery.core.util.WineYears;
import net.satisfy.vinery.platform.PlatformHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What only Vinery's own classes tell: its wines, which juice a bottle holds, and how much a barrel takes. The one place that
 * names them, used only where Vinery is installed.
 */
public final class VineryWines {
    @SuppressWarnings("removal")
    private static final ResourceLocation TYPE = new ResourceLocation(BarrelStation.MOD_ID, "wine_fermentation");

    /**
     * The wines read from each recipe manager (the server's and a client's), with the recipes they were read from: a server makes
     * a new manager on a reload, a client's manager is given new recipes, and either way they are read again.
     */
    private static final Map<RecipeManager, Read> READ = new WeakHashMap<>();

    private record Read(List<Recipe<Container>> from, List<Wine> wines) {
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
    /** The bottle of each juice, by the word wines name it with: read once, the items do not change while the game runs. */
    private static Map<String, ItemStack> bottles;

    private VineryWines() {
    }

    /** Every wine the barrel makes now, read from the game's recipes as they are: datapacks and scripts change them. */
    @SuppressWarnings("unchecked")
    public static synchronized List<Wine> all(Level level) {
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(TYPE);
        if (type == null) {
            return List.of();
        }
        List<Recipe<Container>> recipes = level.getRecipeManager().getAllRecipesFor((RecipeType<Recipe<Container>>) type);
        Read known = READ.get(level.getRecipeManager());
        if (known == null || !known.isOf(recipes)) {
            known = new Read(List.copyOf(recipes), List.copyOf(readAll(level, recipes)));
            READ.put(level.getRecipeManager(), known);
        }
        return known.wines();
    }

    /** The wine of the recipe {@code id}, or null for one there is not (any more). */
    public static Wine byId(Level level, String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return null;
        }
        for (Wine wine : all(level)) {
            if (wine.id().equals(key)) {
                return wine;
            }
        }
        return null;
    }

    private static List<Wine> readAll(Level level, List<Recipe<Container>> recipes) {
        List<Wine> wines = new ArrayList<>();
        for (Recipe<?> recipe : recipes) {
            if (recipe instanceof FermentationBarrelRecipe wine) {
                wines.add(new Wine(recipe.getId(), List.copyOf(recipe.getIngredients()), wine.getJuiceType(), wine.getJuiceAmount(),
                        wine.isWineBottleRequired(), recipe.getResultItem(level.registryAccess())));
            }
        }
        return wines;
    }

    /** The juice a bottle holds, as wines name it; empty for anything that is no juice. */
    public static String juiceOf(ItemStack stack) {
        return stack.isEmpty() || !JuiceUtil.isJuice(stack) ? "" : JuiceUtil.getJuiceType(stack);
    }

    /** How much juice a barrel holds, in Vinery's measure: its own setting. */
    public static int capacity() {
        return Math.max(1, PlatformHelper.getMaxFluidLevel());
    }

    /** How much juice one bottle is. */
    public static int perBottle() {
        return Math.max(1, PlatformHelper.getMaxFluidIncrease());
    }

    /** Ticks a wine ferments: Vinery's own setting. */
    public static int fermentationTime() {
        return Math.max(1, PlatformHelper.getTotalFermentationTime());
    }

    /** A bottle of {@code wine}, of this year, as Vinery's own barrel gives it. */
    public static ItemStack bottled(Wine wine, Level level) {
        ItemStack stack = wine.result().copyWithCount(1);
        WineYears.setWineYear(stack, level);
        return stack;
    }

    /** A bottle of the juice {@code juice}, as wines name it; empty for one no item holds. */
    public static synchronized ItemStack bottleOf(String juice) {
        if (bottles == null) {
            bottles = new HashMap<>();
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack stack = new ItemStack(item);
                String holds = juiceOf(stack);
                if (!holds.isEmpty()) {
                    bottles.putIfAbsent(holds, stack);
                }
            }
        }
        ItemStack bottle = bottles.get(juice);
        return bottle == null ? ItemStack.EMPTY : bottle.copy();
    }

    /** Sets the juice that stands in the barrel and how much: Vinery's own setters, which tell the clients around. */
    public static void setJuice(BlockEntity entity, String juice, int level) {
        if (entity instanceof FermentationBarrelBlockEntity barrel) {
            barrel.setJuiceType(level > 0 ? juice : "");
            barrel.setFluidLevel(Math.max(0, level));
        }
    }
}
