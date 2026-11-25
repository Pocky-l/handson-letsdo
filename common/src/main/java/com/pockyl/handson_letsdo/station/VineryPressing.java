package com.pockyl.handson_letsdo.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.satisfy.vinery.core.recipe.ApplePressFermentingRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What Vinery's apple press does, as its recipes say it: fruit is pressed to mash, and mash is bottled. The one place that names
 * the press's recipe classes, used only where Vinery is installed.
 */
public final class VineryPressing {
    private static final ResourceLocation MASHING = ResourceLocation.fromNamespaceAndPath(BarrelStation.MOD_ID, "apple_mashing");
    private static final ResourceLocation BOTTLING = ResourceLocation.fromNamespaceAndPath(BarrelStation.MOD_ID, "apple_fermenting");

    /** One step of the press: what goes in, what comes out, and whether an empty wine bottle is used up for it. */
    public record Step(ResourceLocation id, Ingredient input, ItemStack result, boolean bottle) {
    }

    /** Both steps as read from a recipe manager, with the recipes they were read from: read again when those change. */
    private record Read(List<RecipeHolder<?>> mashed, List<RecipeHolder<?>> bottled, List<Step> mashing, List<Step> bottling) {
        boolean isOf(List<RecipeHolder<?>> mashedNow, List<RecipeHolder<?>> bottledNow) {
            return same(mashed, mashedNow) && same(bottled, bottledNow);
        }

        private static boolean same(List<RecipeHolder<?>> before, List<RecipeHolder<?>> now) {
            if (before.size() != now.size()) {
                return false;
            }
            for (int i = 0; i < now.size(); i++) {
                if (before.get(i) != now.get(i)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final Map<RecipeManager, Read> READ = new WeakHashMap<>();

    private VineryPressing() {
    }

    /** What the press presses, read from the game's recipes as they are: datapacks and scripts change them. */
    public static List<Step> mashing(Level level) {
        return read(level).mashing();
    }

    /** What the press bottles of what it pressed. */
    public static List<Step> bottling(Level level) {
        return read(level).bottling();
    }

    private static synchronized Read read(Level level) {
        List<RecipeHolder<?>> mashed = recipes(level, MASHING);
        List<RecipeHolder<?>> bottled = recipes(level, BOTTLING);
        Read known = READ.get(level.getRecipeManager());
        if (known == null || !known.isOf(mashed, bottled)) {
            known = new Read(List.copyOf(mashed), List.copyOf(bottled), steps(level, mashed), steps(level, bottled));
            READ.put(level.getRecipeManager(), known);
        }
        return known;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<RecipeHolder<?>> recipes(Level level, ResourceLocation id) {
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(id);
        return type == null ? List.of() : level.getRecipeManager().getAllRecipesFor((RecipeType) type);
    }

    private static List<Step> steps(Level level, List<RecipeHolder<?>> recipes) {
        List<Step> steps = new ArrayList<>();
        for (RecipeHolder<?> holder : recipes) {
            Recipe<?> recipe = holder.value();
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (result.isEmpty() || recipe.getIngredients().isEmpty() || recipe.getIngredients().get(0).isEmpty()) {
                continue;
            }
            steps.add(new Step(holder.id(), recipe.getIngredients().get(0), result,
                    recipe instanceof ApplePressFermentingRecipe bottled && bottled.requiresBottle()));
        }
        return List.copyOf(steps);
    }

    /** The step of {@code steps} that takes {@code stack}, the first as the press finds it; null for none. */
    public static Step taking(List<Step> steps, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (Step step : steps) {
            if (step.input().test(stack)) {
                return step;
            }
        }
        return null;
    }
}
