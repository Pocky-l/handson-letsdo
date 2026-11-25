package com.pockyl.handson_letsdo.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_letsdo.station.BarrelStation;
import com.pockyl.handson_letsdo.station.PressStation;
import com.pockyl.handson_letsdo.station.VineryPressing;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The press's book: everything it bottles, with the fruit it is pressed of (or, where nothing is pressed to it, what is bottled
 * itself) and an empty wine bottle at the foot where it takes one; then what it only presses. At the station it is pressed by hand,
 * so no time is shown. A thing picked goes in from the inventory.
 */
final class PressBook implements RecipeBook.Source {
    @Override
    public String memory() {
        return "apple_press";
    }

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        List<VineryPressing.Step> mashing = VineryPressing.mashing(view.level());
        List<VineryPressing.Step> bottling = VineryPressing.bottling(view.level());
        List<RecipeBook.Page> pages = new ArrayList<>();
        for (VineryPressing.Step bottled : bottling) {
            List<ItemStack> fruit = new ArrayList<>();
            for (VineryPressing.Step pressed : mashing) {
                if (bottled.input().test(pressed.result())) {
                    fruit.addAll(List.of(pressed.input().getItems()));
                }
            }
            Ingredient from = fruit.isEmpty() ? bottled.input() : Ingredient.of(fruit.stream());
            List<ItemStack> foot = new ArrayList<>();
            if (bottled.bottle()) {
                foot.add(new ItemStack(BarrelStation.wineBottle()));
            }
            pages.add(new RecipeBook.Page(bottled.id(), bottled.result(), List.of(from), foot, null, false));
        }
        for (VineryPressing.Step pressed : mashing) {
            if (bottling.stream().noneMatch(bottled -> bottled.input().test(pressed.result()))) {
                pages.add(new RecipeBook.Page(pressed.id(), pressed.result(), List.of(pressed.input()), List.of(), null, false));
            }
        }
        return pages;
    }

    /** A thing picked in the book goes in from the inventory: for one, or as many as fit with shift. */
    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(PressStation.STEP, page.key().toString());
        fill.putBoolean(PressStation.ALL, Screen.hasShiftDown());
        view.sendAction(PressStation.ACTION_FILL, fill);
    }

    /** The fruit in the basket and the mash before it. */
    @Override
    public List<ItemStack> inStation(StationView view) {
        return Stream.of(view.state().getItem(PressStation.FRUIT_SLOT), view.state().getItem(PressStation.MASH_SLOT))
                .filter(stack -> !stack.isEmpty()).toList();
    }

    /** The page of what the press makes of what is in it. */
    @Override
    public Object current(StationView view) {
        Item making = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(view.state().data().getString(PressStation.MAKING)));
        if (making == null || making == Items.AIR) {
            return null;
        }
        return Stream.concat(VineryPressing.bottling(view.level()).stream(), VineryPressing.mashing(view.level()).stream())
                .filter(step -> step.result().is(making)).map(VineryPressing.Step::id).findFirst().orElse(null);
    }
}
