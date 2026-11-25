package com.pockyl.handson_letsdo.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_letsdo.Cookable;
import com.pockyl.handson_letsdo.station.BowlRecipes;
import com.pockyl.handson_letsdo.station.BowlStation;
import com.pockyl.handson_letsdo.station.Dish;

import java.util.ArrayList;
import java.util.List;

/**
 * The crafting bowl's book: everything the bowl mixes, with its ingredients. A page picked lays one set of them into the bowl from
 * the inventory, with Shift as many as there are.
 */
final class BowlBook implements RecipeBook.Source {
    @Override
    public String memory() {
        return "crafting_bowl";
    }

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        List<RecipeBook.Page> pages = new ArrayList<>();
        for (Dish recipe : BowlRecipes.all(view.level())) {
            if (Cookable.all(recipe.ingredients())) {
                pages.add(new RecipeBook.Page(recipe.id(), recipe.result(), recipe.ingredients(), List.of(), null, false));
            }
        }
        return pages;
    }

    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(BowlStation.RECIPE, page.key().toString());
        fill.putBoolean(BowlStation.ALL, Screen.hasShiftDown());
        view.sendAction(BowlStation.ACTION_FILL, fill);
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        return BowlBehavior.ingredientsOf(view).stream().filter(stack -> !stack.isEmpty()).toList();
    }

    /** The page of what the bowl's ingredients make. */
    @Override
    public Object current(StationView view) {
        Dish recipe = BowlRecipes.reads(BowlRecipes.all(view.level()), BowlBehavior.ingredientsOf(view));
        return recipe == null ? null : recipe.id();
    }
}
