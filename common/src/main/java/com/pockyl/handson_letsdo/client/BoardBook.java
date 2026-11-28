package com.pockyl.handson_letsdo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.satisfy.farm_and_charm.core.recipe.CuttingBoardAssemblyRecipe;
import net.satisfy.farm_and_charm.core.recipe.CuttingBoardRecipe;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_letsdo.station.BoardLogic;
import com.pockyl.handson_letsdo.station.CuttingBoardStation;
import com.pockyl.handson_letsdo.station.FarmCharmBoard;
import com.pockyl.handson_letsdo.station.FarmCharmDishes;

import java.util.ArrayList;
import java.util.List;

/**
 * The board's book: every cut by what it makes (the knife's tab) and every dish put together on it (the dishes' tab), read from the
 * recipe manager the client has. A dish picked is laid from the inventory and made; a cut picked lays its food from the inventory.
 */
final class BoardBook implements RecipeBook.Source {
    private static final String DISHES = "dishes";
    private static final String KNIFE = "knife";

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        List<RecipeBook.Page> pages = new ArrayList<>();
        for (RecipeHolder<CuttingBoardAssemblyRecipe> dish : FarmCharmBoard.assemblies(view.level())) {
            pages.add(new RecipeBook.Page(dish.id(), FarmCharmBoard.result(dish.value()).copy(), List.copyOf(FarmCharmBoard.laid(dish.value())),
                    List.of(), null, false, DISHES));
        }
        for (RecipeHolder<CuttingBoardRecipe> cut : FarmCharmBoard.cuts(view.level())) {
            CuttingBoardRecipe recipe = cut.value();
            pages.add(new RecipeBook.Page(cut.id(), recipe.getResult().copy(), List.of(recipe.getIngredient()),
                    recipe.getByproducts().stream().map(ItemStack::copy).toList(), null, false, KNIFE));
        }
        return pages;
    }

    @Override
    public List<RecipeBook.Tab> tabs(StationView view) {
        List<RecipeBook.Tab> tabs = new ArrayList<>();
        tabs.add(new RecipeBook.Tab(null, new ItemStack(Items.COMPASS), Component.translatable("handson_letsdo.book.tab.all")));
        tabs.add(new RecipeBook.Tab(DISHES, icon("dog_food", Items.PAPER), Component.translatable("handson_letsdo.book.tab.dishes")));
        tabs.add(new RecipeBook.Tab(KNIFE, icon("iron_cleaver", Items.IRON_AXE), Component.translatable("handson_letsdo.book.tab.knife")));
        return tabs;
    }

    private static ItemStack icon(String path, net.minecraft.world.item.Item fallback) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(FarmCharmDishes.MOD_ID, path));
        return new ItemStack(item == Items.AIR ? fallback : item);
    }

    @Override
    public String memory() {
        return CuttingBoardStation.ID.toString();
    }

    /**
     * A dish picked is the one the board makes as soon as the layers make it; what it takes is laid from the inventory, one set or,
     * with shift, a batch. A cut picked lays its food from the inventory, the first stack of it.
     */
    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        if (DISHES.equals(page.kind())) {
            CompoundTag pick = new CompoundTag();
            pick.putString(CuttingBoardStation.DISH, page.key().toString());
            pick.putBoolean(CuttingBoardStation.ALL, Screen.hasShiftDown());
            view.sendAction(CuttingBoardStation.ACTION_PICK, pick);
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || page.ingredients().isEmpty()) {
            return;
        }
        Ingredient food = page.ingredients().get(0);
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (food.test(inventory.getItem(slot))) {
                CompoundTag how = new CompoundTag();
                how.putInt(CuttingBoardStation.FROM, slot);
                view.sendAction(CuttingBoardStation.ACTION_PLACE, how);
                return;
            }
        }
    }

    /** The dish the layers make, else what lies on the board to be cut. */
    @Override
    public Object current(StationView view) {
        String dish = view.state().data().getString(CuttingBoardStation.DISH);
        if (!dish.isEmpty()) {
            for (RecipeHolder<CuttingBoardAssemblyRecipe> recipe : FarmCharmBoard.assemblies(view.level())) {
                if (BuiltInRegistries.ITEM.getKey(FarmCharmBoard.result(recipe.value()).getItem()).toString().equals(dish)) {
                    return recipe.id();
                }
            }
        }
        ItemStack onBoard = view.state().getItem(CuttingBoardStation.BOARD_SLOT);
        if (onBoard.isEmpty()) {
            return null;
        }
        for (RecipeHolder<CuttingBoardRecipe> cut : FarmCharmBoard.cuts(view.level())) {
            if (cut.value().getIngredient().test(onBoard)) {
                return cut.id();
            }
        }
        return null;
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        List<ItemStack> there = new ArrayList<>(BoardLogic.layers(view.state()));
        there.add(view.state().getItem(CuttingBoardStation.BOARD_SLOT));
        return there;
    }

    /** Whether {@code stack} goes into one of the dishes put together on the board. */
    static boolean goesIntoADish(StationView view, ItemStack stack) {
        return FarmCharmBoard.assemblies(view.level()).stream()
                .anyMatch(dish -> FarmCharmBoard.laid(dish.value()).stream().anyMatch(ingredient -> ingredient.test(stack)));
    }
}
