package com.pockyl.handson_letsdo.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.state.TankContents;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The kettle by hand. Nothing here brews: the station lays things into the kettle's own slots and the kettle does the rest
 * in its own tick, as it does for its menu and for hoppers. The lid is the cook's: things go in at the top only with it off,
 * and the last cook to go puts it back on. Water is poured by laying its bucket or bottle into the kettle's water slot, which
 * the kettle empties on its next tick; what that leaves (an empty bucket) goes back to whoever poured. Leaves and sugar go into
 * its four ingredient slots, laid out the way the drink they make is read; what feeds the heat into its heat slot. The kettle
 * brews into itself ({@link KettleDrinks}) and the cook pours the drinks into glass bottles at the spout; drinks it filled into
 * bottles a hopper laid in are taken from its drink slot.
 */
public final class KettleLogic implements StationLogic {
    /** The most bottles one pick in the book lays in. */
    private static final int FILL_MOST = 16;
    /** Where what pouring left goes back to: the cursor, or an inventory slot (0 and up); else just the inventory. */
    private static final int CURSOR = -1;
    private static final int ANYWHERE = -2;

    private final Kettle kettle;
    private final SlotStorage ingredients;
    private final SlotStorage bottles;
    private final SlotStorage served;
    private final SlotStorage embers;
    private final SlotStorage waiting;
    private List<Tea> teas;
    /** A cook changed what lies in the kettle: it is laid out on the next tick. */
    private boolean unsorted;
    /** The drink the cook last picked in the book: the kettle's choice while its ingredients make it, until it is empty. */
    private ResourceLocation wanted;
    /** How many drinks stood ready and how hot the kettle was when last looked; -1 before the first look. */
    private int servedBefore = -1;
    private int waitingBefore = -1;
    private int heatBefore = -1;
    /** Who poured the water that waits in the kettle's water slot, and where it came from: what it leaves goes back there. */
    private UUID pourer;
    private int pouredFrom = ANYWHERE;
    /** Water a drink picked in the book still lacks, up to this level, poured from that cook's inventory a vessel a tick. */
    private int waterWanted;
    private UUID waterFor;

    public KettleLogic(StationContext station) {
        kettle = Kettle.orEmpty(station.blockEntity());
        StationState state = station.state();
        ingredients = SlotStorage.of(kettle.container(), Kettle.FIRST_INGREDIENT, Kettle.INGREDIENTS);
        bottles = SlotStorage.of(kettle.container(), Kettle.BOTTLE_SLOT, 1);
        served = SlotStorage.of(kettle.container(), Kettle.DRINK_SLOT, 1);
        embers = SlotStorage.of(kettle.container(), Kettle.HEAT_SLOT, 1);
        waiting = SlotStorage.of(kettle.container(), Kettle.WATER_SLOT, 1);
        state.bindSlots(KettleStation.INGREDIENTS, ingredients);
        state.bindSlots(KettleStation.BOTTLES, bottles);
        state.bindSlots(KettleStation.SERVED, served);
        state.bindSlots(KettleStation.EMBERS, embers);
        state.bindSlots(KettleStation.WAITING, waiting);
        teas = kettle.isReal() ? HerbalTeas.all(station.level()) : List.of();
        refresh(station);
    }

    /** The drinks are read again for every cook: a reload may have changed them. */
    @Override
    public void onOpen(StationContext station, Player player) {
        if (kettle.isReal()) {
            teas = HerbalTeas.all(station.level());
        }
        refresh(station);
    }

    /** The last cook to go puts the lid back on: a kettle is never left open. */
    @Override
    public void onClose(StationContext station, Player player) {
        if (!station.level().isClientSide() && station.users().stream().allMatch(user -> user == player)) {
            setLid(station, false);
        }
    }

    private static void setLid(StationContext station, boolean off) {
        station.state().setControl(KettleStation.LID, off ? 1.0F : 0.0F);
    }

    @Override
    public void tick(StationContext station) {
        if (!station.level().isClientSide() && station.users().isEmpty() && KettleStation.isOpen(station.state())) {
            setLid(station, false);
        }
        if (!station.level().isClientSide() && kettle.isReal()) {
            settleWater(station);
            if (wanted != null && StationRecipes.kinds(ingredients).isEmpty()) {
                wanted = null;
            }
            if (unsorted) {
                unsorted = false;
                arrange();
            }
            tellWhatHappened(station);
        }
        refresh(station);
    }

    /**
     * The kettle has poured in the water that waited: what it left of it, an empty bucket, goes back to where the water came
     * from. Then the water a drink picked in the book still lacks: the next bucket or bottle from the picker's inventory.
     */
    private void settleWater(StationContext station) {
        ItemStack left = waiting.get(0);
        if (pourer != null && HerbalTeas.waterOf(left) == 0) {
            Player cook = cookAt(station, pourer);
            if (!left.isEmpty() && cook != null) {
                waiting.set(0, ItemStack.EMPTY);
                give(cook, left.copy(), pouredFrom);
                cook.getInventory().setChanged();
            }
            pourer = null;
        }
        if (waterWanted <= 0) {
            return;
        }
        Player cook = cookAt(station, waterFor);
        if (cook == null || kettle.water() >= Math.min(Kettle.FULL, waterWanted)) {
            waterWanted = 0;
            waterFor = null;
        } else if (waiting.get(0).isEmpty()) {
            int slot = waterSlot(cook.getInventory());
            if (slot < 0 || !pour(station, cook, slot)) {
                waterWanted = 0;
                waterFor = null;
            } else {
                cook.getInventory().setChanged();
            }
        }
    }

    private static Player cookAt(StationContext station, UUID id) {
        for (Player user : station.users()) {
            if (user.getUUID().equals(id)) {
                return user;
            }
        }
        return null;
    }

    /** The inventory slot of the first water the kettle takes; -1 for none. */
    private static int waterSlot(Inventory inventory) {
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (HerbalTeas.waterOf(inventory.getItem(slot)) > 0) {
                return slot;
            }
        }
        return -1;
    }

    /** A bottle the kettle filled, a thing it burned for heat: told to everybody who sees it. */
    private void tellWhatHappened(StationContext station) {
        ItemStack drinks = served.get(0);
        int ready = drinks.getCount();
        if (servedBefore >= 0 && ready > servedBefore) {
            CompoundTag data = new CompoundTag();
            data.putString(KettleStation.VESSEL, BuiltInRegistries.ITEM.getKey(drinks.getItem()).toString());
            data.putInt(KettleStation.COUNT, ready - servedBefore);
            station.effect(KettleStation.EFFECT_BREWED, KettleStation.SPOUT, data);
        }
        servedBefore = ready;
        // A drink brewed into the kettle: it waits there.
        ItemStack waiting = kettle.waiting();
        if (waitingBefore >= 0 && waiting.getCount() > waitingBefore) {
            CompoundTag data = new CompoundTag();
            data.putString(KettleStation.VESSEL, BuiltInRegistries.ITEM.getKey(waiting.getItem()).toString());
            data.putInt(KettleStation.COUNT, waiting.getCount() - waitingBefore);
            station.effect(KettleStation.EFFECT_BREWED, KettleStation.MOUTH, data);
        }
        waitingBefore = waiting.getCount();
        // The kettle burns what feeds its heat in the tick it is laid in, before anybody saw it lie there: the heat tells. Only a
        // burned thing lifts it by more than a fire under the kettle does.
        int heat = kettle.heat();
        if (heatBefore >= 0 && heat - heatBefore > HerbalTeas.HEAT_OF_A_FIRE) {
            station.effect(KettleStation.EFFECT_FLARED, KettleStation.FIRE);
        }
        heatBefore = heat;
    }

    @Override
    public void onSlotChanged(StationContext station, SlotRef slot) {
        if (slot.group().equals(KettleStation.INGREDIENTS) && !station.level().isClientSide()) {
            unsorted = true;
        }
    }

    /**
     * Things dropped in lie together, two of a kind in one stack, and the kettle reads a drink one ingredient a slot: what lies
     * in it is laid out the way the drink it makes wants it ({@link StationRecipes#choose}). What makes nothing stays as it lies.
     */
    private void arrange() {
        List<ItemStack> kinds = StationRecipes.kinds(ingredients);
        if (kinds.isEmpty()) {
            return;
        }
        StationRecipes.Candidate<Tea> found = StationRecipes.reads(candidates(), inKettle());
        StationRecipes.Choice<Tea> choice = StationRecipes.choose(candidates(), kinds, Kettle.INGREDIENTS, wanted,
                found == null ? null : found.id(), false);
        if (choice != null) {
            StationRecipes.lay(ingredients, kinds, choice.parts());
        }
    }

    /** How {@code kinds} lie for the drink they make ({@link #arrange}), or null. */
    private int[] partsWith(List<ItemStack> kinds) {
        StationRecipes.Candidate<Tea> found = StationRecipes.reads(candidates(), inKettle());
        StationRecipes.Choice<Tea> choice = StationRecipes.choose(candidates(), kinds, Kettle.INGREDIENTS, wanted,
                found == null ? null : found.id(), true);
        return choice == null ? null : choice.parts();
    }

    private List<ItemStack> inKettle() {
        List<ItemStack> in = new ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            in.add(ingredients.get(i));
        }
        return in;
    }

    private List<StationRecipes.Candidate<Tea>> candidates() {
        return StationRecipes.candidates(teas, Tea::id, Tea::ingredients);
    }

    /**
     * The drink what lies in the kettle makes, whatever water, heat or bottles it still lacks; null for none. The drink whose
     * ingredients are exactly what lies there, else the first, in the recipes' order, that finds all of its own among them: the
     * kettle itself brews with other things lying beside.
     */
    private Tea making() {
        List<ItemStack> in = inKettle();
        StationRecipes.Candidate<Tea> exact = StationRecipes.reads(candidates(), in);
        if (exact != null) {
            return exact.recipe();
        }
        for (Tea tea : teas) {
            if (!tea.ingredients().isEmpty() && finds(tea.ingredients(), in)) {
                return tea;
            }
        }
        return null;
    }

    /** Whether every one of {@code wanted} finds a stack of its own in {@code stacks}, the first that fits, as the kettle looks. */
    private static boolean finds(List<Ingredient> wanted, List<ItemStack> stacks) {
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                left.add(stack);
            }
        }
        for (Ingredient ingredient : wanted) {
            int hit = -1;
            for (int i = 0; i < left.size() && hit < 0; i++) {
                if (ingredient.test(left.get(i))) {
                    hit = i;
                }
            }
            if (hit < 0) {
                return false;
            }
            left.remove(hit);
        }
        return true;
    }

    /** Whether some drink takes {@code stack} into the water: the kettle takes nothing else there. */
    public boolean isIngredient(ItemStack stack) {
        return teas.stream().anyMatch(tea -> tea.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack)));
    }

    /** Only what some drink is made of goes into the water, and only with the lid off; heat and water have their own places. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return !slot.group().equals(KettleStation.INGREDIENTS) || isIngredient(stack) && KettleStation.isOpen(station.state());
    }

    /** What the kettle's own drink slot does for a player who takes the drink. */
    @Override
    public void onTaken(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (slot.equals(KettleStation.SERVED_SLOT)) {
            stack.onCraftedBy(station.level(), player, stack.getCount());
        }
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (!kettle.isReal() || station.level().isClientSide()) {
            return;
        }
        int from = data.contains(KettleStation.FROM) ? data.getInt(KettleStation.FROM) : CURSOR;
        if (from >= Inventory.INVENTORY_SIZE || from < CURSOR) {
            return;
        }
        if (KettleStation.ACTION_LID.equals(action)) {
            setLid(station, data.getBoolean(KettleStation.OPEN));
        } else if (KettleStation.ACTION_DRAW.equals(action)) {
            draw(station, player, data.getBoolean(KettleStation.ALL), data.getBoolean(KettleStation.ANY) ? ANYWHERE : from);
        } else if (KettleStation.ACTION_POUR.equals(action)) {
            // Water brought to a kettle with its lid on is meant for it: the lid comes off for it.
            ItemStack held = from >= 0 ? player.getInventory().getItem(from) : player.inventoryMenu.getCarried();
            if (HerbalTeas.waterOf(held) > 0) {
                setLid(station, true);
            }
            pour(station, player, from);
        } else if (KettleStation.ACTION_FILL.equals(action)) {
            // A drink picked in the book takes the lid off for what it is made of to go in.
            setLid(station, true);
            ResourceLocation id = ResourceLocation.tryParse(data.getString(KettleStation.TEA));
            teas.stream().filter(tea -> tea.id().equals(id)).findFirst()
                    .ifPresent(tea -> fill(player, tea, data.getBoolean(KettleStation.ALL) ? FILL_MOST : 1));
        } else if (StationRecipes.ACTION_OFFER.equals(action) && KettleStation.isOpen(station.state())) {
            // A stack clicked onto a kettle whose slots hold other things: in, when they still make a drink with it.
            if (StationRecipes.offerCarried(station, this, player, KettleStation.INGREDIENTS, ingredients, data.getBoolean(StationRecipes.ONE),
                    this::partsWith)) {
                unsorted = true;
            }
        }
        player.getInventory().setChanged();
        refresh(station);
    }

    /**
     * One bucket or bottle of water of the cursor's stack (or of inventory slot {@code from}'s) is laid into the kettle's water
     * slot: the kettle pours it in on its next tick, and what it leaves comes back in {@link #settleWater}. A full kettle takes
     * none, nor one that still has water waiting. Something else left in that slot by a hopper goes to the cook first.
     */
    private boolean pour(StationContext station, Player player, int from) {
        Inventory inventory = player.getInventory();
        ItemStack held = from >= 0 ? inventory.getItem(from) : player.inventoryMenu.getCarried();
        if (HerbalTeas.waterOf(held) <= 0 || kettle.water() >= Kettle.FULL) {
            return false;
        }
        ItemStack left = waiting.get(0);
        if (HerbalTeas.waterOf(left) > 0) {
            return false;
        }
        if (!left.isEmpty()) {
            waiting.set(0, ItemStack.EMPTY);
            give(player, left.copy(), ANYWHERE);
        }
        ItemStack vessel = held.split(1);
        if (held.isEmpty()) {
            if (from >= 0) {
                inventory.setItem(from, ItemStack.EMPTY);
            } else {
                player.inventoryMenu.setCarried(ItemStack.EMPTY);
            }
        }
        waiting.set(0, vessel);
        pourer = player.getUUID();
        pouredFrom = held.isEmpty() ? from : ANYWHERE;
        CompoundTag data = new CompoundTag();
        data.putString(KettleStation.VESSEL, BuiltInRegistries.ITEM.getKey(vessel.getItem()).toString());
        station.effect(KettleStation.EFFECT_POURED, KettleStation.MOUTH, data);
        return true;
    }

    /**
     * Drinks poured at the spout: into the glass bottle on the cursor, or in inventory slot {@code from}, or ({@link #ANYWHERE}) the
     * first in the inventory; one, or with {@code all} as many as wait and there are bottles there. Each drink goes back where its
     * bottle came from while that is free, else into the inventory. Everybody sees them poured.
     */
    private void draw(StationContext station, Player player, boolean all, int from) {
        KettleDrinks drinks = kettle.drinks();
        if (drinks == null || drinks.handson$waiting().isEmpty()) {
            return;
        }
        Inventory inventory = player.getInventory();
        int poured = 0;
        ItemStack kind = drinks.handson$waiting().copyWithCount(1);
        while (!drinks.handson$waiting().isEmpty() && (all || poured == 0)) {
            int slot = from;
            if (from == ANYWHERE) {
                slot = bottleSlot(inventory);
                if (slot < 0) {
                    break;
                }
            }
            ItemStack bottles = slot >= 0 ? inventory.getItem(slot) : player.inventoryMenu.getCarried();
            if (!bottles.is(Items.GLASS_BOTTLE)) {
                break;
            }
            bottles.shrink(1);
            boolean emptied = bottles.isEmpty();
            if (emptied) {
                if (slot >= 0) {
                    inventory.setItem(slot, ItemStack.EMPTY);
                } else {
                    player.inventoryMenu.setCarried(ItemStack.EMPTY);
                }
            }
            ItemStack drink = drinks.handson$pour();
            drink.onCraftedBy(station.level(), player, 1);
            give(player, drink, emptied ? slot : ANYWHERE);
            poured++;
        }
        if (poured > 0) {
            CompoundTag data = new CompoundTag();
            data.putString(KettleStation.VESSEL, BuiltInRegistries.ITEM.getKey(kind.getItem()).toString());
            data.putInt(KettleStation.COUNT, poured);
            station.effect(KettleStation.EFFECT_DRAWN, KettleStation.SPOUT, data);
            waitingBefore = kettle.waiting().getCount();
        }
    }

    /** The inventory slot of the first glass bottle, the selected one first; -1 for none. */
    private static int bottleSlot(Inventory inventory) {
        if (inventory.getSelected().is(Items.GLASS_BOTTLE)) {
            return inventory.selected;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (inventory.getItem(slot).is(Items.GLASS_BOTTLE)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * {@code stack} goes to the player: back onto the cursor or into the inventory slot it came from ({@code from}) while that is
     * free, else into the inventory, and what does not fit is dropped at their feet.
     */
    private static void give(Player player, ItemStack stack, int from) {
        Inventory inventory = player.getInventory();
        if (from == CURSOR && player.inventoryMenu.getCarried().isEmpty()) {
            player.inventoryMenu.setCarried(stack);
        } else if (from >= 0 && inventory.getItem(from).isEmpty()) {
            inventory.setItem(from, stack);
        } else if (!inventory.add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    /**
     * A drink picked in the book: up to {@code sets} sets of what it is made of go in from the inventory, laid out the way the
     * drink is read, then the water those lack: the water is poured a vessel a tick ({@link #settleWater}). No bottles: the
     * drinks wait in the kettle and are poured at the spout. A kettle that holds things the drink does not use takes nothing. The
     * picked drink stays the kettle's choice while its ingredients make it. What feeds the heat is the cook's to lay.
     */
    private void fill(Player player, Tea tea, int sets) {
        Inventory inventory = player.getInventory();
        List<ItemStack> kinds = StationRecipes.kinds(ingredients);
        StationRecipes.Filled filled = tea.ingredients().isEmpty() ? null
                : StationRecipes.fill(inventory, tea.ingredients(), kinds, Kettle.INGREDIENTS, sets);
        int[] parts;
        if (filled != null) {
            kinds = filled.kinds();
            parts = filled.parts();
            StationRecipes.lay(ingredients, kinds, parts);
        } else {
            // Nothing more to add: what already lies in the kettle may still lack its bottles or its water.
            parts = kinds.isEmpty() ? null : StationRecipes.parts(tea.ingredients(), kinds);
        }
        if (parts == null) {
            return;
        }
        wanted = tea.id();
        int drinks = Math.min(sets, StationRecipes.servings(kinds, parts));
        int water = Math.min(Kettle.FULL, drinks * tea.water());
        if (water > kettle.water()) {
            waterWanted = water;
            waterFor = player.getUUID();
        }
        unsorted = true;
    }

    private void refresh(StationContext station) {
        StationState state = station.state();
        Level level = station.level();
        int water = kettle.water();
        state.setTank(KettleStation.WATER, water <= 0 ? TankContents.EMPTY : TankContents.of(KettleStation.WATER_LIQUID, water));
        CompoundTag own = state.data();
        own.putInt(KettleStation.HEAT, kettle.heat());
        own.putBoolean(KettleStation.HEATED, kettle.isHeated());
        Tea making = kettle.isReal() ? making() : null;
        if (making == null) {
            own.remove(KettleStation.MAKING);
            own.remove(KettleStation.NEEDS_WATER);
            own.remove(KettleStation.NEEDS_HEAT);
        } else {
            own.putString(KettleStation.MAKING, making.id().toString());
            own.putInt(KettleStation.NEEDS_WATER, making.water());
            own.putInt(KettleStation.NEEDS_HEAT, making.heat());
        }
        ItemStack waiting = kettle.waiting();
        if (waiting.isEmpty()) {
            own.remove(KettleStation.DRINK);
            own.remove(KettleStation.DRINKS);
        } else {
            own.putString(KettleStation.DRINK, BuiltInRegistries.ITEM.getKey(waiting.getItem()).toString());
            own.putInt(KettleStation.DRINKS, waiting.getCount());
        }
        int time = kettle.time();
        own.putBoolean(KettleStation.BREWING, time > 0);
        // The start and the length, not the count: the count changes every tick, and every change is sent to every client.
        if (time > 0 && !level.isClientSide()) {
            own.putLong(KettleStation.STARTED, level.getGameTime() - time);
            own.putInt(KettleStation.TOTAL, Math.max(1, kettle.needed()));
        } else {
            own.remove(KettleStation.STARTED);
            own.putInt(KettleStation.TOTAL, making == null ? 0 : making.time());
        }
        state.setData(own);
    }
}
