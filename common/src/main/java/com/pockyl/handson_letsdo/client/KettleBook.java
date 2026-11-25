package com.pockyl.handson_letsdo.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_letsdo.station.HerbalTeas;
import com.pockyl.handson_letsdo.station.KettleStation;
import com.pockyl.handson_letsdo.station.Tea;

import java.util.ArrayList;
import java.util.List;

/**
 * The kettle's book: every drink with its water first among what goes in, then what is dropped into it; at the foot its bottle,
 * what feeds the heat when the drink wants the kettle hotter than a fire alone makes it, and how long a bottle takes. A drink
 * picked goes in from the inventory, bottles and water too.
 */
final class KettleBook implements RecipeBook.Source {
    @Override
    public String memory() {
        return "tea_kettle";
    }

    /** What water is poured from: the buckets the kettle takes, and a bottle of water. */
    static List<ItemStack> waterVessels() {
        List<ItemStack> vessels = new ArrayList<>();
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(HerbalTeas.LARGE_WATER)) {
            vessels.add(new ItemStack(holder.value()));
        }
        vessels.add(PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER));
        return vessels;
    }

    private static ItemStack firstOf(net.minecraft.tags.TagKey<Item> tag) {
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            return new ItemStack(holder.value());
        }
        return ItemStack.EMPTY;
    }

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        List<RecipeBook.Page> pages = new ArrayList<>();
        List<ItemStack> water = waterVessels();
        ItemStack ember = firstOf(HerbalTeas.HEAT_ITEMS);
        for (Tea tea : HerbalTeas.all(view.level())) {
            if (tea.result().isEmpty()) {
                continue;
            }
            List<Ingredient> ingredients = new ArrayList<>();
            if (tea.water() > 0) {
                ingredients.add(Ingredient.of(water.stream()));
            }
            ingredients.addAll(tea.ingredients());
            List<ItemStack> foot = new ArrayList<>();
            for (Ingredient bottle : tea.bottles()) {
                if (bottle.getItems().length > 0) {
                    foot.add(bottle.getItems()[0].copyWithCount(1));
                }
            }
            if (tea.heat() > HerbalTeas.HEAT_OF_A_FIRE && !ember.isEmpty()) {
                foot.add(ember.copy());
            }
            foot.add(new ItemStack(Items.CLOCK));
            Component time = Component.translatable("handson_letsdo.book.seconds", Math.max(1, Math.round(tea.time() / 20.0F)));
            pages.add(new RecipeBook.Page(tea.id(), tea.result(), ingredients, foot, time, false));
        }
        return pages;
    }

    /** A drink picked in the book goes in from the inventory: for one bottle, or as many as fit with shift. */
    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(KettleStation.TEA, page.key().toString());
        fill.putBoolean(KettleStation.ALL, Screen.hasShiftDown());
        view.sendAction(KettleStation.ACTION_FILL, fill);
    }

    /** What lies in the kettle, and a bucket of water while water stands in it: a drink lacks none then. */
    @Override
    public List<ItemStack> inStation(StationView view) {
        List<ItemStack> in = new ArrayList<>(List.of(view.state().getItems(KettleStation.INGREDIENTS)));
        if (KettleStation.water(view.state()) > 0) {
            in.add(new ItemStack(Items.WATER_BUCKET));
        }
        return in;
    }

    @Override
    public Object current(StationView view) {
        String making = view.state().data().getString(KettleStation.MAKING);
        return making.isEmpty() ? null : ResourceLocation.tryParse(making);
    }
}
