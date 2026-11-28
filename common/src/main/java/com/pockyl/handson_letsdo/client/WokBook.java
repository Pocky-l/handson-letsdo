package com.pockyl.handson_letsdo.client;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.satisfy.wok_and_bowl.core.recipe.WokRecipe;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_letsdo.station.WokAndBowlWok;
import com.pockyl.handson_letsdo.station.WokStation;

import java.util.ArrayList;
import java.util.List;

/** The wok's book: its recipes, their ingredients in the order they go in; a page picked throws in what the wok still lacks. */
final class WokBook implements RecipeBook.Source {
    @Override
    public String memory() {
        return WokStation.ID.toString();
    }

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        List<RecipeBook.Page> pages = new ArrayList<>();
        for (RecipeHolder<WokRecipe> recipe : WokAndBowlWok.recipes(view.level())) {
            pages.add(new RecipeBook.Page(recipe.id(), WokAndBowlWok.result(recipe.value()).copy(), List.copyOf(WokAndBowlWok.ingredients(recipe.value())),
                    List.of(), null, false));
        }
        return pages;
    }

    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(WokStation.RECIPE, page.key().toString());
        view.sendAction(WokStation.ACTION_FILL, fill);
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        WokAndBowlWok wok = WokAndBowlWok.of(view.level().getBlockEntity(view.pos()));
        return wok == null ? List.of() : List.copyOf(wok.ingredients());
    }

    /** The page of the recipe what lies in the wok is making. */
    @Override
    public Object current(StationView view) {
        WokAndBowlWok wok = WokAndBowlWok.of(view.level().getBlockEntity(view.pos()));
        if (wok == null || wok.ingredients().isEmpty()) {
            return null;
        }
        for (RecipeHolder<WokRecipe> recipe : WokAndBowlWok.recipes(view.level())) {
            if (recipe.value().startsWith(wok.ingredients())) {
                return recipe.id();
            }
        }
        return null;
    }
}
