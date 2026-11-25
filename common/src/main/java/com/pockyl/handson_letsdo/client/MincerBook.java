package com.pockyl.handson_letsdo.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_letsdo.station.Dish;
import com.pockyl.handson_letsdo.station.MincerRecipes;
import com.pockyl.handson_letsdo.station.MincerStation;

import java.util.ArrayList;
import java.util.List;

/**
 * The mincer's book: everything it minces, with what it is minced of. A page picked lays a load of it onto the hopper from the
 * inventory, with Shift a stack.
 */
final class MincerBook implements RecipeBook.Source {
    @Override
    public String memory() {
        return "mincer";
    }

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        List<RecipeBook.Page> pages = new ArrayList<>();
        for (Dish recipe : MincerRecipes.all(view.level())) {
            pages.add(new RecipeBook.Page(recipe.id(), recipe.result(), recipe.ingredients(), List.of(), null, false));
        }
        return pages;
    }

    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(MincerStation.RECIPE, page.key().toString());
        fill.putBoolean(MincerStation.ALL, Screen.hasShiftDown());
        view.sendAction(MincerStation.ACTION_FILL, fill);
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        ItemStack lying = view.state().getItem(MincerStation.INPUT_SLOT);
        return lying.isEmpty() ? List.of() : List.of(lying);
    }

    /** The page of what lies on the hopper. */
    @Override
    public Object current(StationView view) {
        Dish recipe = MincerRecipes.taking(MincerRecipes.all(view.level()), view.state().getItem(MincerStation.INPUT_SLOT));
        return recipe == null ? null : recipe.id();
    }
}
