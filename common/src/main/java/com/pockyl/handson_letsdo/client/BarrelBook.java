package com.pockyl.handson_letsdo.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_letsdo.station.BarrelStation;
import com.pockyl.handson_letsdo.station.VineryWines;
import com.pockyl.handson_letsdo.station.Wine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The barrel's book: every wine with the juice it is made of first among what goes in, an empty bottle at the foot where the wine
 * is filled into one, and how long it takes. A wine picked goes in from the inventory, juice and bottles too.
 */
final class BarrelBook implements RecipeBook.Source {
    /** Shorter than this, ticks, the time is told in seconds, else in minutes. */
    private static final int IN_SECONDS = 2400;

    /** The bottles of each juice, by the word wines name it with: read once, the items do not change while the game runs. */
    private Map<String, List<ItemStack>> bottles;

    @Override
    public String memory() {
        return "fermentation_barrel";
    }

    private List<ItemStack> bottlesOf(String juice) {
        if (bottles == null) {
            bottles = new HashMap<>();
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack stack = new ItemStack(item);
                String holds = VineryWines.juiceOf(stack);
                if (!holds.isEmpty()) {
                    bottles.computeIfAbsent(holds, key -> new ArrayList<>()).add(stack);
                }
            }
        }
        return bottles.getOrDefault(juice, List.of());
    }

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        List<RecipeBook.Page> pages = new ArrayList<>();
        // As long as the server's Vinery takes, which the station tells.
        int total = Math.max(1, view.state().data().getInt(BarrelStation.TOTAL));
        for (Wine wine : VineryWines.all(view.level())) {
            List<ItemStack> juice = bottlesOf(wine.juice());
            if (wine.result().isEmpty() || juice.isEmpty()) {
                continue;
            }
            List<Ingredient> ingredients = new ArrayList<>();
            ingredients.add(Ingredient.of(juice.stream()));
            wine.ingredients().stream().filter(ingredient -> !ingredient.isEmpty()).forEach(ingredients::add);
            List<ItemStack> foot = new ArrayList<>();
            if (wine.bottle()) {
                foot.add(new ItemStack(BarrelStation.wineBottle()));
            }
            foot.add(new ItemStack(Items.CLOCK));
            Component time = total < IN_SECONDS ? Component.translatable("handson_letsdo.book.seconds", Math.max(1, Math.round(total / 20.0F)))
                    : Component.translatable("handson_letsdo.book.minutes", Math.max(1, Math.round(total / 1200.0F)));
            pages.add(new RecipeBook.Page(wine.id(), wine.result(), ingredients, foot, time, false));
        }
        return pages;
    }

    /** A wine picked in the book goes in from the inventory: for one wine, or as many as fit with shift. */
    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(BarrelStation.WINE, page.key().toString());
        fill.putBoolean(BarrelStation.ALL, Screen.hasShiftDown());
        view.sendAction(BarrelStation.ACTION_FILL, fill);
    }

    /** What lies in the barrel, and a bottle of the juice that stands in it: a wine of that juice lacks none. */
    @Override
    public List<ItemStack> inStation(StationView view) {
        List<ItemStack> in = new ArrayList<>(List.of(view.state().getItems(BarrelStation.INGREDIENTS)));
        List<ItemStack> juice = bottlesOf(BarrelStation.juiceOf(view.state()));
        if (!juice.isEmpty()) {
            in.add(juice.get(0));
        }
        return in;
    }

    @Override
    public Object current(StationView view) {
        String making = view.state().data().getString(BarrelStation.MAKING);
        return making.isEmpty() ? null : ResourceLocation.tryParse(making);
    }
}
