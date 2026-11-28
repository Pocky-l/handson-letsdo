package com.pockyl.handson_letsdo.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.satisfy.wok_and_bowl.core.recipe.WokRecipe;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;

import java.util.List;
import java.util.Optional;

/**
 * The wok's rules at the station: nothing of its own. What the cook throws in, pours or strikes is handed to the wok's own
 * {@code addIngredient}; the flick and a click with an empty hand are the wok's empty hand. The wok decides, plays its sounds and
 * tells the clients; this only says who threw what, so it flies from the right hand.
 */
public final class WokLogic implements StationLogic {
    private final WokAndBowlWok wok;

    public WokLogic(StationContext station) {
        wok = WokAndBowlWok.of(station.blockEntity());
    }

    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return false;
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (wok == null || station.level().isClientSide()) {
            return;
        }
        switch (action) {
            case WokStation.ACTION_PLACE -> place(station, player, data.contains(WokStation.FROM) ? data.getInt(WokStation.FROM) : -1,
                    data.getBoolean(WokStation.BY_USE));
            case WokStation.ACTION_TOSS -> {
                if (wok.isCooking()) {
                    wok.emptyHand(player);
                }
            }
            case WokStation.ACTION_TAKE -> {
                if (!wok.isCooking()) {
                    wok.emptyHand(player);
                }
            }
            case WokStation.ACTION_FILL -> fill(station, player, ResourceLocation.tryParse(data.getString(WokStation.RECIPE)));
            default -> {
            }
        }
    }

    /** One of the stack on the cursor, or of inventory slot {@code from}, goes in as the wok takes it. */
    private void place(StationContext station, Player player, int from, boolean byUse) {
        if (from >= Inventory.INVENTORY_SIZE) {
            return;
        }
        ItemStack stack = from >= 0 ? player.getInventory().getItem(from) : player.inventoryMenu.getCarried();
        if (stack.isEmpty()) {
            return;
        }
        ItemStack one = stack.copyWithCount(1);
        if (wok.add(stack, player)) {
            if (from >= 0) {
                player.getInventory().setItem(from, stack.isEmpty() ? ItemStack.EMPTY : stack);
            } else {
                player.inventoryMenu.setCarried(stack.isEmpty() ? ItemStack.EMPTY : stack);
            }
            CompoundTag what = new CompoundTag();
            what.put(WokStation.STACK, one.saveOptional(station.level().registryAccess()));
            station.effect(WokStation.EFFECT_ADDED, "wok", what);
        } else if (!byUse) {
            CompoundTag what = new CompoundTag();
            what.put(WokStation.STACK, one.saveOptional(station.level().registryAccess()));
            station.effect(WokStation.EFFECT_REFUSED, "wok", what);
        }
    }

    /** What the picked recipe still lacks goes in, in its order, from the inventory, as far as the inventory has it. */
    private void fill(StationContext station, Player player, ResourceLocation id) {
        Optional<RecipeHolder<WokRecipe>> recipe = WokAndBowlWok.recipes(station.level()).stream().filter(it -> it.id().equals(id)).findFirst();
        if (recipe.isEmpty()) {
            return;
        }
        List<Ingredient> wanted = WokAndBowlWok.ingredients(recipe.get().value());
        if (!recipe.get().value().startsWith(wok.ingredients())) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int next = wok.ingredients().size(); next < wanted.size(); next++) {
            int slot = -1;
            for (int i = 0; i < Inventory.INVENTORY_SIZE && slot < 0; i++) {
                if (wanted.get(next).test(inventory.getItem(i))) {
                    slot = i;
                }
            }
            if (slot < 0) {
                return;
            }
            ItemStack stack = inventory.getItem(slot);
            ItemStack one = stack.copyWithCount(1);
            if (!wok.add(stack, player)) {
                return;
            }
            inventory.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
            CompoundTag what = new CompoundTag();
            what.put(WokStation.STACK, one.saveOptional(station.level().registryAccess()));
            station.effect(WokStation.EFFECT_ADDED, "wok", what);
        }
    }
}
