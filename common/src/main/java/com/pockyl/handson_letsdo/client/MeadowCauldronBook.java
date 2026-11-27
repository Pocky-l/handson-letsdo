package com.pockyl.handson_letsdo.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_letsdo.station.MeadowCauldron;
import com.pockyl.handson_letsdo.station.MeadowCauldronStation;

import java.util.ArrayList;
import java.util.List;

/**
 * The cooking cauldron's book: Meadow's dishes, each with what goes in, the water it takes and its time. A dish picked lays its food
 * in from the inventory (with shift as many sets as the slots hold) and pours water in when the cauldron has too little.
 */
final class MeadowCauldronBook implements RecipeBook.Source {
    @Override
    public String memory() {
        return "meadow_cooking_cauldron";
    }

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        List<RecipeBook.Page> pages = new ArrayList<>();
        for (MeadowCauldron.Dish dish : MeadowCauldron.dishes(view.level())) {
            List<ItemStack> foot = new ArrayList<>();
            if (dish.water() > 0) {
                foot.add(new ItemStack(Items.WATER_BUCKET));
            }
            foot.add(new ItemStack(Items.CLOCK));
            Component seconds = Component.translatable("handson_letsdo.book.seconds", Math.max(1, Math.round(dish.ticks() / 20.0F)));
            pages.add(new RecipeBook.Page(dish.id(), dish.result(), dish.ingredients(), foot, seconds, false));
        }
        return pages;
    }

    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(MeadowCauldronStation.DISH, page.key().toString());
        fill.putBoolean(MeadowCauldronStation.ALL, Screen.hasShiftDown());
        view.sendAction(MeadowCauldronStation.ACTION_FILL, fill);
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        return List.of(view.state().getItems(MeadowCauldronStation.INGREDIENTS));
    }

    /** The dish the food in the cauldron makes. */
    @Override
    public Object current(StationView view) {
        CompoundTag data = view.state().data();
        return data.contains(MeadowCauldronStation.MAKING) ? ResourceLocation.tryParse(data.getString(MeadowCauldronStation.MAKING)) : null;
    }
}
