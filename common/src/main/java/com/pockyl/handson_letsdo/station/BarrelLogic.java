package com.pockyl.handson_letsdo.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.state.TankContents;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;

import java.util.ArrayList;
import java.util.List;

/**
 * The barrel by hand: juice is poured in from its bottles, which come back empty; what a wine is made of is thrown in at the
 * opening; everything in it ferments together ({@link BarrelBrew}) and the wine waits in the barrel until it is drawn at the bung
 * into empty wine bottles. Juice can be taken back into bottles at the opening, what lies in it picked out, and empty bottles a
 * hopper left are given back at the bung: everything that goes in can come out. It also runs on the clients around a barrel
 * nobody works at; there it only reads.
 */
public final class BarrelLogic implements StationLogic {
    /** The most wines one pick in the book lays in. */
    private static final int FILL_MOST = 16;
    /**
     * How far ahead of the clock the barrel is told it stands open, ticks, renewed every tick while the hatch is off: a barrel
     * whose station went without closing is shut again by itself.
     */
    private static final int OPEN_AHEAD = 20;

    private final Barrel barrel;
    private final BlockEntity entity;
    private final SlotStorage waiting;
    private final SlotStorage ingredients;
    private final SlotStorage bottles;
    private final SlotStorage served;
    private List<Wine> wines;
    /** A cook changed what lies in the barrel: it is laid out on the next tick. */
    private boolean unsorted;
    /** The wine the cook last picked in the book: the barrel's choice while its ingredients make it, until it is empty. */
    private ResourceLocation wanted;
    /** The juice the ingredients were last laid out for: thrown into an empty barrel, they are laid out again once juice is in. */
    private String arrangedFor = "";
    /** How many bottles of wine were ready when last looked, to tell when more are done; -1 before the first look. */
    private int readyBefore = -1;

    public BarrelLogic(StationContext station) {
        entity = station.blockEntity();
        barrel = Barrel.orEmpty(entity);
        StationState state = station.state();
        waiting = SlotStorage.of(barrel.container(), Barrel.JUICE_SLOT, 1);
        ingredients = SlotStorage.of(barrel.container(), Barrel.FIRST_INGREDIENT, Barrel.INGREDIENTS);
        bottles = SlotStorage.of(barrel.container(), Barrel.BOTTLE_SLOT, 1);
        served = SlotStorage.of(barrel.container(), Barrel.WINE_SLOT, 1);
        state.bindSlots(BarrelStation.WAITING, waiting);
        state.bindSlots(BarrelStation.INGREDIENTS, ingredients);
        state.bindSlots(BarrelStation.BOTTLES, bottles);
        state.bindSlots(BarrelStation.SERVED, served);
        wines = barrel.isReal() ? VineryWines.all(station.level()) : List.of();
        refresh(station);
    }

    /** The wines are read again for every cook: a reload may have changed them. */
    @Override
    public void onOpen(StationContext station, Player player) {
        if (barrel.isReal()) {
            wines = VineryWines.all(station.level());
        }
        refresh(station);
    }

    /** The last cook to go puts the hatch back on: a barrel is never left open. */
    @Override
    public void onClose(StationContext station, Player player) {
        if (!station.level().isClientSide() && station.users().stream().allMatch(user -> user == player)) {
            setLid(station, false);
        }
    }

    @Override
    public void tick(StationContext station) {
        if (!station.level().isClientSide() && station.users().isEmpty() && BarrelStation.isOpen(station.state())) {
            setLid(station, false);
        }
        if (!station.level().isClientSide()) {
            tellBarrel(station);
        }
        if (!station.level().isClientSide() && barrel.isReal()) {
            if (wanted != null && StationRecipes.kinds(ingredients).isEmpty()) {
                wanted = null;
            }
            String juice = barrel.juice();
            if (unsorted || !juice.equals(arrangedFor)) {
                unsorted = false;
                arrangedFor = juice;
                arrange();
            }
            int ready = ready();
            if (readyBefore >= 0 && ready > readyBefore) {
                CompoundTag data = new CompoundTag();
                data.putInt(BarrelStation.COUNT, ready - readyBefore);
                station.effect(BarrelStation.EFFECT_BOTTLED, BarrelStation.TAP, data);
            }
            readyBefore = ready;
        }
        refresh(station);
    }

    /** Bottles of wine the barrel holds ready: those that wait in it and those in its wine slot. */
    private int ready() {
        return barrel.portions() + served.get(0).getCount();
    }

    @Override
    public void onSlotChanged(StationContext station, SlotRef slot) {
        if (slot.group().equals(BarrelStation.INGREDIENTS) && !station.level().isClientSide()) {
            unsorted = true;
        }
    }

    /**
     * Ingredients thrown in lie together, two sugar in one stack, and Vinery reads a wine one ingredient a slot: what lies in the
     * barrel is laid out the way the wine it makes wants it ({@link StationRecipes#choose}). What makes nothing stays as it lies.
     */
    private void arrange() {
        List<ItemStack> kinds = StationRecipes.kinds(ingredients);
        if (kinds.isEmpty()) {
            return;
        }
        StationRecipes.Candidate<Wine> found = StationRecipes.reads(candidates(true), inBarrel());
        StationRecipes.Choice<Wine> choice = StationRecipes.choose(candidates(false), kinds, Barrel.INGREDIENTS, wanted,
                found == null ? null : found.id(), false);
        if (choice != null) {
            StationRecipes.lay(ingredients, kinds, choice.parts());
        }
    }

    /** How {@code kinds} lie for the wine they make in this barrel ({@link #arrange}), or null. */
    private int[] partsWith(List<ItemStack> kinds) {
        StationRecipes.Candidate<Wine> found = StationRecipes.reads(candidates(true), inBarrel());
        StationRecipes.Choice<Wine> choice = StationRecipes.choose(candidates(false), kinds, Barrel.INGREDIENTS, wanted,
                found == null ? null : found.id(), true);
        return choice == null ? null : choice.parts();
    }

    private List<ItemStack> inBarrel() {
        List<ItemStack> inBarrel = new ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            inBarrel.add(ingredients.get(i));
        }
        return inBarrel;
    }

    /**
     * The wines of the juice that stands in the barrel. Without {@code exact}, an empty barrel takes any: its juice may be poured
     * in after the ingredients.
     */
    private List<StationRecipes.Candidate<Wine>> candidates(boolean exact) {
        String juice = barrel.juice();
        List<Wine> fitting = wines.stream().filter(wine -> juice.isEmpty() ? !exact : wine.juice().equals(juice)).toList();
        return StationRecipes.candidates(fitting, Wine::id, Wine::ingredients);
    }

    /** Whether some wine takes {@code stack} as an ingredient: the barrel takes nothing else, as its menu. */
    public boolean isIngredient(ItemStack stack) {
        return wines.stream().anyMatch(wine -> wine.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack)));
    }

    /** Only what some wine is made of goes in at the opening, and only with the hatch off; juice is poured. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return !slot.group().equals(BarrelStation.INGREDIENTS) || isIngredient(stack) && BarrelStation.isOpen(station.state());
    }

    private void setLid(StationContext station, boolean off) {
        station.state().setControl(BarrelStation.LID, off ? 1.0F : 0.0F);
        if (!station.level().isClientSide()) {
            tellBarrel(station);
        }
    }

    /** The barrel ferments only shut: it is told whether its hatch is off, so that its count waits meanwhile. */
    private void tellBarrel(StationContext station) {
        BrewingBarrel brew = barrel.brew();
        if (brew != null) {
            brew.handson$setOpenUntil(BarrelStation.isOpen(station.state()) ? station.level().getGameTime() + OPEN_AHEAD : Long.MIN_VALUE);
        }
    }

    /** What Vinery's result slot does for a player who takes the wine. */
    @Override
    public void onTaken(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (slot.equals(BarrelStation.SERVED_SLOT)) {
            stack.onCraftedBy(station.level(), player, stack.getCount());
        }
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (!barrel.isReal() || station.level().isClientSide()) {
            return;
        }
        int from = data.contains(BarrelStation.FROM) ? data.getInt(BarrelStation.FROM) : -1;
        if (from >= Inventory.INVENTORY_SIZE) {
            return;
        }
        refresh(station);
        if (BarrelStation.ACTION_LID.equals(action)) {
            setLid(station, data.getBoolean(BarrelStation.OPEN));
        } else if (BarrelStation.ACTION_DRAW.equals(action)) {
            draw(station, player, data.getBoolean(BarrelStation.ALL), from);
        } else if (BarrelStation.ACTION_DRAW_BACK.equals(action) && BarrelStation.isOpen(station.state())) {
            drawBack(station, player, data.getBoolean(BarrelStation.ALL));
        } else if (StationRecipes.ACTION_OFFER.equals(action) && BarrelStation.isOpen(station.state())) {
            // A stack clicked onto a barrel whose slots hold other things: in, when they still make a wine with it.
            if (StationRecipes.offerCarried(station, this, player, BarrelStation.INGREDIENTS, ingredients, data.getBoolean(StationRecipes.ONE),
                    this::partsWith)) {
                unsorted = true;
            }
        } else if (BarrelStation.ACTION_POUR.equals(action)) {
            ItemStack held = from >= 0 ? player.getInventory().getItem(from) : player.inventoryMenu.getCarried();
            // Juice brought to a shut barrel is meant for it: the hatch comes off for it.
            if (BarrelStation.pourable(station.state(), held) > 0) {
                setLid(station, true);
            }
            pour(station, player, held, data.getBoolean(BarrelStation.ALL) ? Integer.MAX_VALUE : 1, from);
        } else if (BarrelStation.ACTION_FILL.equals(action)) {
            ResourceLocation id = ResourceLocation.tryParse(data.getString(BarrelStation.WINE));
            // A wine picked in the book takes the hatch off for what it is made of to go in.
            setLid(station, true);
            wines.stream().filter(wine -> wine.id().equals(id)).findFirst()
                    .ifPresent(wine -> fill(station, player, wine, data.getBoolean(BarrelStation.ALL) ? FILL_MOST : 1));
        }
        player.getInventory().setChanged();
    }

    /**
     * Up to {@code most} bottles of the juice {@code held} (the cursor's stack, or inventory slot {@code from}'s) are poured in, as
     * many as the barrel takes now; {@code held} shrinks by them and they come back empty, where they were when it is used up.
     * Returns how many.
     */
    private int pour(StationContext station, Player player, ItemStack held, int most, int from) {
        int count = Math.min(Math.min(most, held.getCount()), BarrelStation.pourable(station.state(), held));
        if (count <= 0) {
            return 0;
        }
        ItemStack vessel = held.copyWithCount(1);
        String juice = VineryWines.juiceOf(vessel);
        VineryWines.setJuice(entity, juice, barrel.level() + count * VineryWines.perBottle());
        held.shrink(count);
        give(player, new ItemStack(BarrelStation.wineBottle(), count), held.isEmpty(), from);
        CompoundTag data = new CompoundTag();
        data.putString(BarrelStation.VESSEL, BuiltInRegistries.ITEM.getKey(vessel.getItem()).toString());
        data.putString(BarrelStation.JUICE, juice);
        data.putInt(BarrelStation.COUNT, count);
        station.effect(BarrelStation.EFFECT_POURED, BarrelStation.MOUTH, data);
        refresh(station);
        return count;
    }

    /**
     * At the bung. The wine that waits runs into empty wine bottles, one or with {@code all} as many as wait: the bottles on the
     * cursor (or in inventory slot {@code from}), or with an empty hand one from the inventory; a wine whose recipe needs no bottle
     * comes as it is. Bottled wine in the barrel's wine slot (filled for hoppers) comes to an empty hand. With nothing to draw, an
     * empty hand gets back the empty bottles a hopper or the menu left.
     */
    private void draw(StationContext station, Player player, boolean all, int from) {
        Level level = station.level();
        Inventory inventory = player.getInventory();
        ItemStack held = from >= 0 ? inventory.getItem(from) : player.inventoryMenu.getCarried();
        if (!held.isEmpty() && !BarrelStation.isWineBottle(held)) {
            return;
        }
        BrewingBarrel brew = barrel.brew();
        Wine wine = brew == null ? null : BarrelBrew.stored(level, brew);
        ItemStack drawn = ItemStack.EMPTY;
        boolean filledHeld = false;
        if (wine != null) {
            int count = all ? brew.handson$portions() : 1;
            if (wine.bottle()) {
                filledHeld = !held.isEmpty();
                ItemStack source = filledHeld ? held : emptyBottle(inventory);
                count = Math.min(count, source.getCount());
                source.shrink(count);
            }
            if (count > 0) {
                drawn = VineryWines.bottled(wine, level).copyWithCount(count);
                BarrelBrew.take(entity, brew, count);
            }
        }
        if (drawn.isEmpty() && held.isEmpty() && !served.get(0).isEmpty()) {
            ItemStack out = served.get(0).copy();
            drawn = out.split(all ? out.getCount() : 1);
            served.set(0, out);
        }
        if (drawn.isEmpty()) {
            if (held.isEmpty() && !bottles.get(0).isEmpty()) {
                give(player, bottles.get(0).copy(), false, -1);
                bottles.set(0, ItemStack.EMPTY);
            }
            return;
        }
        readyBefore = ready();
        drawn.onCraftedBy(level, player, drawn.getCount());
        CompoundTag data = new CompoundTag();
        data.putString(BarrelStation.VESSEL, BuiltInRegistries.ITEM.getKey(drawn.getItem()).toString());
        data.putInt(BarrelStation.COUNT, drawn.getCount());
        give(player, drawn, filledHeld && held.isEmpty(), from);
        station.effect(BarrelStation.EFFECT_DRAWN, BarrelStation.TAP, data);
        refresh(station);
    }

    /** The first empty wine bottles in the inventory, the stack itself; empty for none. */
    static ItemStack emptyBottle(Inventory inventory) {
        if (BarrelStation.isWineBottle(inventory.getSelected())) {
            return inventory.getSelected();
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (BarrelStation.isWineBottle(inventory.getItem(slot))) {
                return inventory.getItem(slot);
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * At the opening, empty wine bottles on the cursor take the juice back, a bottle's worth each, one or with {@code all} as many
     * as there are and the barrel holds: what was poured in can always come out. What is less than a bottle stays.
     */
    private void drawBack(StationContext station, Player player, boolean all) {
        ItemStack held = player.inventoryMenu.getCarried();
        String juice = barrel.juice();
        int per = VineryWines.perBottle();
        ItemStack bottle = VineryWines.bottleOf(juice);
        if (!BarrelStation.isWineBottle(held) || bottle.isEmpty() || barrel.level() < per) {
            return;
        }
        int count = Math.min(all ? held.getCount() : 1, barrel.level() / per);
        VineryWines.setJuice(entity, juice, barrel.level() - count * per);
        held.shrink(count);
        give(player, bottle.copyWithCount(count), held.isEmpty(), -1);
        CompoundTag data = new CompoundTag();
        data.putString(BarrelStation.VESSEL, BuiltInRegistries.ITEM.getKey(bottle.getItem()).toString());
        data.putString(BarrelStation.JUICE, juice);
        data.putInt(BarrelStation.COUNT, count);
        station.effect(BarrelStation.EFFECT_DRAWN_BACK, BarrelStation.MOUTH, data);
        refresh(station);
    }

    /**
     * {@code stack} goes to the player: onto the cursor (or into inventory slot {@code from}) when {@code inPlace}, what was there
     * being used up; else into the inventory, and what does not fit is dropped at their feet.
     */
    static void give(Player player, ItemStack stack, boolean inPlace, int from) {
        Inventory inventory = player.getInventory();
        if (inPlace && stack.getCount() <= stack.getMaxStackSize()) {
            if (from >= 0) {
                inventory.setItem(from, stack);
            } else {
                player.inventoryMenu.setCarried(stack);
            }
            return;
        }
        if (from < 0 && player.inventoryMenu.getCarried().isEmpty()) {
            player.inventoryMenu.setCarried(ItemStack.EMPTY);
        }
        if (!inventory.add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    /**
     * A wine picked in the book: up to {@code sets} sets of its ingredients go in from the inventory, laid out the way the wine is
     * read, then as much of its juice as they take and the barrel still lacks. A barrel that holds another juice, or things the
     * wine does not use, takes nothing. The picked wine stays the barrel's choice while its ingredients make it.
     */
    private void fill(StationContext station, Player player, Wine wine, int sets) {
        String juice = barrel.juice();
        if (!juice.isEmpty() && !juice.equals(wine.juice())) {
            return;
        }
        Inventory inventory = player.getInventory();
        List<ItemStack> kinds = StationRecipes.kinds(ingredients);
        StationRecipes.Filled filled = StationRecipes.fill(inventory, wine.ingredients().stream().filter(ingredient -> !ingredient.isEmpty())
                .toList(), kinds, Barrel.INGREDIENTS, sets);
        int[] parts;
        if (filled != null) {
            kinds = filled.kinds();
            parts = filled.parts();
            StationRecipes.lay(ingredients, kinds, parts);
        } else {
            // Nothing more to add: what already lies in the barrel may still lack its juice.
            parts = kinds.isEmpty() ? null : StationRecipes.parts(wine.ingredients(), kinds);
        }
        if (parts == null) {
            return;
        }
        wanted = wine.id();
        int wanting = Math.min(sets, StationRecipes.servings(kinds, parts));
        int perBottle = VineryWines.perBottle();
        int lacking = wanting * wine.juiceAmount() - barrel.level();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && lacking > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (wine.juice().equals(VineryWines.juiceOf(stack))) {
                lacking -= pour(station, player, stack, (lacking + perBottle - 1) / perBottle, slot) * perBottle;
            }
        }
        unsorted = true;
    }

    private void refresh(StationContext station) {
        StationState state = station.state();
        Level level = station.level();
        int juiceLevel = barrel.level();
        String juice = barrel.juice();
        int capacity = barrel.isReal() ? VineryWines.capacity() : 1;
        BrewingBarrel brew = barrel.brew();
        Wine stored = brew == null || !barrel.isReal() ? null : BarrelBrew.stored(level, brew);
        int volume = stored == null ? 0 : brew.handson$portions() * stored.juiceAmount();
        int liquid = juiceLevel + volume;
        String shownJuice = !juice.isEmpty() ? juice : stored != null ? stored.juice() : "";
        state.setTank(BarrelStation.JUICE, liquid <= 0 ? TankContents.EMPTY : TankContents.of(BarrelStation.liquid(shownJuice),
                Math.max(1, Math.min(BarrelStation.FULL, Math.round((float) BarrelStation.FULL * liquid / capacity)))));
        CompoundTag own = state.data();
        own.putInt(BarrelStation.LEVEL, juiceLevel);
        own.putInt(BarrelStation.CAPACITY, capacity);
        own.putInt(BarrelStation.PER_BOTTLE, barrel.isReal() ? VineryWines.perBottle() : 1);
        if (stored == null) {
            own.remove(BarrelStation.STORED);
            own.remove(BarrelStation.STORED_WINE);
            own.remove(BarrelStation.STORED_JUICE);
            own.remove(BarrelStation.STORED_VOLUME);
            own.remove(BarrelStation.STORED_BOTTLE);
        } else {
            own.putInt(BarrelStation.STORED, brew.handson$portions());
            own.putString(BarrelStation.STORED_WINE, BuiltInRegistries.ITEM.getKey(stored.result().getItem()).toString());
            own.putString(BarrelStation.STORED_JUICE, stored.juice());
            own.putInt(BarrelStation.STORED_VOLUME, volume);
            own.putBoolean(BarrelStation.STORED_BOTTLE, stored.bottle());
        }
        Wine making = brew == null || !barrel.isReal() ? null : BarrelBrew.brewing(level, barrel, brew);
        if (making == null) {
            own.remove(BarrelStation.MAKING);
            own.remove(BarrelStation.BATCH);
        } else {
            own.putString(BarrelStation.MAKING, making.id().toString());
            own.putInt(BarrelStation.BATCH, BarrelBrew.batch(barrel, brew, making));
        }
        int time = barrel.time();
        // The start and the length, not the count: the count changes every tick, and every change is sent to every client.
        // A client's own copy of the barrel does not know how far it is: there only the wine is told.
        own.putInt(BarrelStation.TOTAL, barrel.isReal() ? VineryWines.fermentationTime() : 1);
        // With the hatch off the count waits: how far it is, which stays put, instead of the start, which would move every tick.
        boolean held = brew != null && brew.handson$isOpen(level.getGameTime());
        if (time > 0 && !level.isClientSide() && !held) {
            own.putLong(BarrelStation.STARTED, level.getGameTime() - time);
        } else {
            own.remove(BarrelStation.STARTED);
        }
        if (time > 0 && !level.isClientSide() && held) {
            own.putInt(BarrelStation.HELD, time);
        } else {
            own.remove(BarrelStation.HELD);
        }
        state.setData(own);
    }
}
