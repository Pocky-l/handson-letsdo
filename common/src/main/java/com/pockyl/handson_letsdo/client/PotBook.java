package com.pockyl.handson_letsdo.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_letsdo.station.CookingPotStation;
import com.pockyl.handson_letsdo.station.Dish;
import com.pockyl.handson_letsdo.station.Pot;
import com.pockyl.handson_letsdo.station.PotStars;

import java.util.ArrayList;
import java.util.List;

/**
 * A pot's book: the dishes it cooks, what each is served in, how long one serving takes under the spoon, and its stars. A dish
 * picked goes into the pot from the inventory.
 */
final class PotBook implements RecipeBook.Source {
    private final Pot.Kind kind;

    PotBook(Pot.Kind kind) {
        this.kind = kind;
    }

    @Override
    public String memory() {
        return kind.name();
    }

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        List<RecipeBook.Page> pages = new ArrayList<>();
        // As long as the server's settings make it, which the station tells.
        int serving = Math.max(1, view.state().data().getInt(CookingPotStation.SERVING_TIME));
        Component seconds = Component.translatable("handson_letsdo.book.seconds", Math.max(1, Math.round(serving / 20.0F / PotStars.speed(1))));
        for (Dish dish : kind.dishes(view.level())) {
            List<Ingredient> ingredients = dish.ingredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
            if (ingredients.size() > Pot.INGREDIENTS) {
                continue;
            }
            List<ItemStack> foot = new ArrayList<>();
            if (!dish.vessel().isEmpty()) {
                foot.add(dish.vessel());
            }
            foot.add(new ItemStack(Items.CLOCK));
            pages.add(new RecipeBook.Page(dish.id(), dish.result(), ingredients, foot, seconds, true));
        }
        return pages;
    }

    /** A dish picked in the book goes into the pot from the inventory: one set, or as many as a batch cooks with shift. */
    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(CookingPotStation.DISH, page.key().toString());
        fill.putBoolean(CookingPotStation.SCOOP_ALL, Screen.hasShiftDown());
        view.sendAction(CookingPotStation.ACTION_FILL, fill);
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        return List.of(view.state().getItems(CookingPotStation.INGREDIENTS));
    }

    /** The dish the ingredients make, or the soup that waits in the pot. */
    @Override
    public Object current(StationView view) {
        ResourceLocation soup = ResourceLocation.tryParse(view.state().data().getString(CookingPotStation.SOUP));
        if (soup != null && view.state().data().contains(CookingPotStation.SOUP)) {
            return soup;
        }
        ItemStack[] items = view.state().getItems(CookingPotStation.INGREDIENTS);
        SimpleContainer food = new SimpleContainer(Pot.SLOTS);
        for (int i = 0; i < items.length && i < Pot.INGREDIENTS; i++) {
            food.setItem(i, items[i].copy());
        }
        Dish making = Pot.making(view.level(), kind, food);
        return making == null ? null : making.id();
    }
}
