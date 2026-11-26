package com.pockyl.handson_letsdo.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_letsdo.station.Cauldron;
import com.pockyl.handson_letsdo.station.CauldronStation;

import java.util.ArrayList;
import java.util.List;

/**
 * The cauldron's book: its one brew, as HerbalBrews shows it in recipe viewers, three potions of any kind and the herbal infusion
 * into a flask with every effect of the three. Picked, the potions and the infusion the cauldron still lacks go in from the
 * inventory.
 */
final class CauldronBook implements RecipeBook.Source {
    private static final String KEY = "flask";

    @Override
    public String memory() {
        return "brewing_cauldron";
    }

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        Item flask = BuiltInRegistries.ITEM.get(Cauldron.FLASK);
        Item infusion = BuiltInRegistries.ITEM.get(Cauldron.INFUSION);
        if (flask == Items.AIR || infusion == Items.AIR) {
            return List.of();
        }
        Ingredient potion = Ingredient.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION);
        return List.of(new RecipeBook.Page(KEY, new ItemStack(flask), List.of(potion, potion, potion, Ingredient.of(infusion)), List.of(),
                null, false));
    }

    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putBoolean(CauldronStation.ALL, Screen.hasShiftDown());
        view.sendAction(CauldronStation.ACTION_FILL, fill);
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        List<ItemStack> lying = new ArrayList<>();
        for (int slot = 0; slot < Cauldron.POTIONS; slot++) {
            ItemStack potion = view.state().getItem(SlotRef.of(CauldronStation.POTIONS, slot));
            if (!potion.isEmpty()) {
                lying.add(potion);
            }
        }
        ItemStack infusion = view.state().getItem(CauldronStation.CATALYST_SLOT);
        if (!infusion.isEmpty()) {
            lying.add(infusion);
        }
        return lying;
    }

    @Override
    public Object current(StationView view) {
        return KEY;
    }
}
