package com.pockyl.handson_letsdo.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;

import java.util.function.Predicate;

/**
 * The cauldron by hand. Nothing here brews: the station lays potions and the infusion into the cauldron's own slots and the cauldron
 * brews in its own tick, as it does for its menu and for hoppers. A potion is poured in from its bottle into the first empty potion
 * slot; a glass bottle draws the last one poured back out while nothing is brewed of it. The cook's stick hurries the brew: every
 * turn of it round the cauldron while a brew is on brings the cauldron's own count on by {@code 1 / TURNS} of the brew's time, so
 * that it brews in a few seconds of stirring; left alone it brews in HerbalBrews' time, never slower. The flask it brews waits in
 * its flask slot and is filled at the cauldron for the cook who takes it.
 */
public final class CauldronLogic implements StationLogic {
    /** Where what is taken goes back to: the cursor, or an inventory slot (0 and up); else just the inventory. */
    private static final int CURSOR = -1;
    private static final int ANYWHERE = -2;

    private final Cauldron cauldron;
    private final HandTurns turns = new HandTurns(CauldronStation.FASTEST_TURN_TICKS);
    /** Ticks of the brew the stick has earned and the cauldron has not been told of yet: less than one. */
    private float hurried;
    /** When the stick was last reported, and where its end is; whether the clients were told since it last hurried the brew. */
    private long stirredAt = Long.MIN_VALUE;
    private float stickX;
    private float stickZ;
    private boolean untold;
    /** What the flask slot held when last looked, to tell everybody when the brew is done; null before the first look. */
    private ItemStack flaskBefore;

    public CauldronLogic(StationContext station) {
        cauldron = Cauldron.orEmpty(station.blockEntity());
        StationState state = station.state();
        state.bindSlots(CauldronStation.POTIONS, SlotStorage.of(cauldron.container(), 0, Cauldron.POTIONS));
        state.bindSlots(CauldronStation.FLASK, SlotStorage.of(cauldron.container(), Cauldron.FLASK_SLOT, 1));
        state.bindSlots(CauldronStation.CATALYST, SlotStorage.of(cauldron.container(), Cauldron.CATALYST_SLOT, 1));
        refresh(station);
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        refresh(station);
    }

    @Override
    public void onClose(StationContext station, Player player) {
        if (!station.level().isClientSide() && station.users().stream().allMatch(user -> user == player)) {
            letGo(station);
        }
    }

    @Override
    public void tick(StationContext station) {
        if (!station.level().isClientSide() && cauldron.isReal()) {
            if (stirredAt != Long.MIN_VALUE && station.level().getGameTime() - stirredAt > CauldronStation.STIR_GAP) {
                letGo(station);
            }
            tellWhatHappened(station);
        }
        refresh(station);
    }

    /** The stick was let go: it rests again, and the clients near are told how far the brew is now. */
    private void letGo(StationContext station) {
        stirredAt = Long.MIN_VALUE;
        turns.reset();
        if (untold) {
            untold = false;
            BlockEntity entity = station.blockEntity();
            if (entity != null) {
                station.level().sendBlockUpdated(station.pos(), entity.getBlockState(), entity.getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** The cauldron brewed a flask in its own tick: everybody sees it done. */
    private void tellWhatHappened(StationContext station) {
        ItemStack flask = cauldron.flask();
        if (flaskBefore != null && flaskBefore.isEmpty() && Cauldron.isFlask(flask)) {
            CompoundTag data = new CompoundTag();
            data.putInt(CauldronStation.COLOR, CauldronBrews.color(flask));
            station.effect(CauldronStation.EFFECT_BREWED, CauldronStation.MOUTH, data);
        }
        flaskBefore = flask.copy();
    }

    /** Potions go in only while no flask waits in the cauldron: it brews no more until the flask is taken. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return !slot.group().equals(CauldronStation.POTIONS) || cauldron.flask().isEmpty();
    }

    /** What taking a brewed flask does elsewhere: the cook made it. */
    @Override
    public void onTaken(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (slot.equals(CauldronStation.FLASK_SLOT)) {
            stack.onCraftedBy(station.level(), player, stack.getCount());
        }
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (!cauldron.isReal() || station.level().isClientSide()) {
            return;
        }
        int from = data.contains(CauldronStation.FROM) ? data.getInt(CauldronStation.FROM) : CURSOR;
        if (from >= Inventory.INVENTORY_SIZE || from < CURSOR) {
            return;
        }
        if (CauldronStation.ACTION_POUR.equals(action)) {
            pour(station, player, from);
        } else if (CauldronStation.ACTION_DRAW.equals(action)) {
            draw(station, player, from);
        } else if (CauldronStation.ACTION_STIR.equals(action)) {
            stir(station, data.getFloat(CauldronStation.X), data.getFloat(CauldronStation.Z));
        } else if (CauldronStation.ACTION_TAKE.equals(action)) {
            take(station, player);
        } else if (CauldronStation.ACTION_THROW.equals(action)) {
            throwIn(player, from, data.getBoolean(CauldronStation.ALL));
        } else if (CauldronStation.ACTION_FILL.equals(action)) {
            fill(station, player, data.getBoolean(CauldronStation.ALL));
        }
        player.getInventory().setChanged();
        refresh(station);
    }

    /** Whether {@code stack} can be poured in now: a potion, into a cauldron with a free potion slot and no flask waiting in it. */
    public boolean pourable(ItemStack stack) {
        return Cauldron.isPotion(stack) && cauldron.flask().isEmpty() && freeSlot() >= 0;
    }

    private int freeSlot() {
        for (int slot = 0; slot < Cauldron.POTIONS; slot++) {
            if (cauldron.container().getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /** One potion of the cursor's stack (or of inventory slot {@code from}'s) is poured into the first empty potion slot. */
    private void pour(StationContext station, Player player, int from) {
        ItemStack held = from >= 0 ? player.getInventory().getItem(from) : player.inventoryMenu.getCarried();
        if (!pourable(held)) {
            return;
        }
        ItemStack potion = held.split(1);
        if (held.isEmpty()) {
            if (from >= 0) {
                player.getInventory().setItem(from, ItemStack.EMPTY);
            } else {
                player.inventoryMenu.setCarried(ItemStack.EMPTY);
            }
        }
        cauldron.container().setItem(freeSlot(), potion);
        cauldron.container().setChanged();
        station.effect(CauldronStation.EFFECT_POURED, CauldronStation.MOUTH, about(potion));
    }

    /**
     * The infusion on the cursor (or in inventory slot {@code from}) goes into the cauldron's infusion slot, one or with {@code all} the
     * stack, as much as the slot takes.
     */
    private void throwIn(Player player, int from, boolean all) {
        ItemStack held = from >= 0 ? player.getInventory().getItem(from) : player.inventoryMenu.getCarried();
        ItemStack lying = cauldron.catalyst();
        if (!Cauldron.isCatalyst(held) || !lying.isEmpty() && !ItemStack.isSameItemSameTags(lying, held)) {
            return;
        }
        int room = Math.min(held.getMaxStackSize(), cauldron.container().getMaxStackSize()) - lying.getCount();
        int count = Math.min(room, all ? held.getCount() : 1);
        if (count <= 0) {
            return;
        }
        ItemStack thrown = held.split(count);
        if (held.isEmpty()) {
            if (from >= 0) {
                player.getInventory().setItem(from, ItemStack.EMPTY);
            } else {
                player.inventoryMenu.setCarried(ItemStack.EMPTY);
            }
        }
        if (!lying.isEmpty()) {
            thrown.grow(lying.getCount());
        }
        cauldron.container().setItem(Cauldron.CATALYST_SLOT, thrown);
        cauldron.container().setChanged();
    }

    /**
     * The brew picked in the book: the potions the cauldron lacks are poured in from the inventory, the selected stack first, and an
     * infusion thrown in when it has none (with {@code all} the stack). Nothing goes in while a flask waits.
     */
    private void fill(StationContext station, Player player, boolean all) {
        if (!cauldron.flask().isEmpty()) {
            return;
        }
        Inventory inventory = player.getInventory();
        while (freeSlot() >= 0) {
            int slot = slotOf(inventory, Cauldron::isPotion);
            if (slot < 0) {
                break;
            }
            pour(station, player, slot);
        }
        if (cauldron.catalyst().isEmpty()) {
            int slot = slotOf(inventory, Cauldron::isCatalyst);
            if (slot >= 0) {
                throwIn(player, slot, all);
            }
        }
    }

    /** The inventory slot of the first stack {@code wanted} takes, the selected one first; -1 for none. */
    private static int slotOf(Inventory inventory, Predicate<ItemStack> wanted) {
        if (wanted.test(inventory.getSelected())) {
            return inventory.selected;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (wanted.test(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * The glass bottle on the cursor (or in inventory slot {@code from}) draws the last potion poured back out, while no flask waits:
     * the potion goes back where the bottle came from while that is free, else into the inventory.
     */
    private void draw(StationContext station, Player player, int from) {
        if (!cauldron.flask().isEmpty()) {
            return;
        }
        int last = -1;
        for (int slot = Cauldron.POTIONS - 1; slot >= 0 && last < 0; slot--) {
            if (!cauldron.container().getItem(slot).isEmpty()) {
                last = slot;
            }
        }
        ItemStack bottles = from >= 0 ? player.getInventory().getItem(from) : player.inventoryMenu.getCarried();
        if (last < 0 || !bottles.is(Items.GLASS_BOTTLE)) {
            return;
        }
        bottles.shrink(1);
        boolean emptied = bottles.isEmpty();
        if (emptied) {
            if (from >= 0) {
                player.getInventory().setItem(from, ItemStack.EMPTY);
            } else {
                player.inventoryMenu.setCarried(ItemStack.EMPTY);
            }
        }
        ItemStack potion = cauldron.container().removeItemNoUpdate(last);
        cauldron.container().setItem(last, ItemStack.EMPTY);
        cauldron.container().setChanged();
        station.effect(CauldronStation.EFFECT_DRAWN, CauldronStation.MOUTH, about(potion));
        give(player, potion, emptied ? from : ANYWHERE);
    }

    /**
     * The stick's end is at {@code x, z} now, pixels from the middle in the cauldron's own axes: the way it went round the middle since
     * the last report hurries the brew that is on, at most as fast as a hand turns it.
     */
    private void stir(StationContext station, float x, float z) {
        float reach = CauldronStation.STICK_REACH;
        float length = Mth.sqrt(x * x + z * z);
        if (!Float.isFinite(length)) {
            return;
        }
        if (length > reach) {
            x *= reach / length;
            z *= reach / length;
        }
        Level level = station.level();
        long now = level.getGameTime();
        // Round the middle only: an end that stays near it turns the brew round nothing.
        float way = length < 0.5F ? 0.0F : Math.abs(turns.report(now, (float) Mth.atan2(z, x)));
        stirredAt = now;
        stickX = x;
        stickZ = z;
        if (!cauldron.brews() || cauldron.total() <= 0) {
            hurried = 0.0F;
            return;
        }
        hurried += way / Mth.TWO_PI * cauldron.total() / CauldronStation.TURNS;
        int ticks = (int) hurried;
        if (ticks > 0) {
            hurried -= ticks;
            // Its own tick counts the last one and brews.
            cauldron.setTime(Math.min(cauldron.time() + ticks, cauldron.total() - 1));
            untold = true;
        }
    }

    /** The flask that waits is filled at the cauldron for the cook: into the inventory, or at their feet when it is full. */
    private void take(StationContext station, Player player) {
        ItemStack flask = cauldron.flask();
        if (flask.isEmpty()) {
            return;
        }
        ItemStack taken = cauldron.container().removeItemNoUpdate(Cauldron.FLASK_SLOT);
        cauldron.container().setItem(Cauldron.FLASK_SLOT, ItemStack.EMPTY);
        cauldron.container().setChanged();
        taken.onCraftedBy(station.level(), player, taken.getCount());
        CompoundTag data = about(taken);
        flaskBefore = ItemStack.EMPTY;
        station.effect(CauldronStation.EFFECT_TAKEN, CauldronStation.MOUTH, data);
        give(player, taken, ANYWHERE);
    }

    private static CompoundTag about(ItemStack stack) {
        CompoundTag data = new CompoundTag();
        data.putString(CauldronStation.ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        data.putInt(CauldronStation.COLOR, CauldronBrews.color(stack));
        return data;
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

    private void refresh(StationContext station) {
        StationState state = station.state();
        Level level = station.level();
        CompoundTag own = state.data();
        int time = cauldron.time();
        boolean brewing = time > 0 && cauldron.brews();
        own.putBoolean(CauldronStation.BREWING, brewing);
        own.putInt(CauldronStation.TOTAL, cauldron.total());
        // The start and the length, not the count: the count changes every tick, and every change is sent to every client.
        if (brewing) {
            long now = level.getGameTime();
            long told = !level.isClientSide() ? now
                    : station.blockEntity() instanceof CauldronTold copy ? copy.handson$toldAt(now) : now;
            own.putLong(CauldronStation.STARTED, told - time);
        } else {
            own.remove(CauldronStation.STARTED);
        }
        ItemStack flask = cauldron.flask();
        own.putBoolean(CauldronStation.MAKES, cauldron.potions().size() < Cauldron.POTIONS
                || CauldronBrews.makesSomething(cauldron.potions()));
        own.putInt(CauldronStation.COLOR, flask.isEmpty() ? CauldronBrews.brewColor(cauldron.potions()) : CauldronBrews.color(flask));
        boolean stirring = stirredAt != Long.MIN_VALUE;
        own.putBoolean(CauldronStation.STIRRING, stirring);
        if (stirring) {
            own.putFloat(CauldronStation.STICK_X, stickX);
            own.putFloat(CauldronStation.STICK_Z, stickZ);
        } else {
            own.remove(CauldronStation.STICK_X);
            own.remove(CauldronStation.STICK_Z);
        }
        state.setData(own);
    }
}
