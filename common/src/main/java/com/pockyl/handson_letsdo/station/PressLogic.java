package com.pockyl.handson_letsdo.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.state.TankContents;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;

import java.util.List;
import java.util.function.Predicate;

/**
 * The press by hand. While a cook works at it, the press presses only as the cook presses the screw's cap: the last of
 * {@link Press#PRESSES_PER_LOAD} presses presses a load of up to {@link Press#LOAD} fruits at once, by the press's own step, and
 * the juice runs out quickly ({@link PressBasket}). Left alone, the press does it in its own tick and its own time, as it does for
 * its menu and for hoppers, while the basket's front is shut. The cook opens and shuts it; things go into and out of the basket
 * only while it is open. What it bottles runs into the tub under its
 * lip ({@link PressBasket}), where the cook fills empty wine bottles; the mash can be taken as it is. On a client near a press
 * nobody works at, the same logic runs on the client's copy of the press and only tells the scene what the press does.
 */
public final class PressLogic implements StationLogic {
    /** The most one pick in the book lays in. */
    private static final int FILL_MOST = 16;

    private final Press press;
    private final SlotStorage fruit;
    private final SlotStorage mash;
    private final SlotStorage bottles;
    private final SlotStorage served;
    private List<VineryPressing.Step> mashing = List.of();
    private List<VineryPressing.Step> bottling = List.of();
    /** A cook at the press holds its own pressing this long after the last tick they were seen there. */
    private static final int ATTENDED = 20;
    /** How much fruit, mash and bottled drink there was when last looked, to tell what the press did; -1 before the first look. */
    private int fruitBefore = -1;
    private int mashBefore = -1;
    private int servedBefore = -1;
    private int tubBefore = -1;

    public PressLogic(StationContext station) {
        press = Press.orEmpty(station.blockEntity());
        StationState state = station.state();
        fruit = SlotStorage.of(press.container(), Press.FRUIT_SLOT, 1);
        mash = SlotStorage.of(press.container(), Press.MASH_SLOT, 1);
        bottles = SlotStorage.of(press.container(), Press.BOTTLE_SLOT, 1);
        served = SlotStorage.of(press.container(), Press.RESULT_SLOT, 1);
        state.bindSlots(PressStation.FRUIT, fruit);
        state.bindSlots(PressStation.MASH, mash);
        state.bindSlots(PressStation.BOTTLES, bottles);
        state.bindSlots(PressStation.SERVED, served);
        read(station.level());
        refresh(station);
    }

    private void read(Level level) {
        if (press.isReal()) {
            mashing = VineryPressing.mashing(level);
            bottling = VineryPressing.bottling(level);
        }
    }

    /** The recipes are read again for every cook: a reload may have changed them. */
    @Override
    public void onOpen(StationContext station, Player player) {
        read(station.level());
        refresh(station);
    }

    @Override
    public void tick(StationContext station) {
        if (!station.level().isClientSide() && press.isReal()) {
            if (press.basket() != null && !station.users().isEmpty()) {
                press.basket().handson$attend(station.level().getGameTime() + ATTENDED);
            }
            tellWhatHappened(station);
        }
        refresh(station);
    }

    /** A fruit the press mashed, a bottle it filled: told to everybody who sees it. Mash laid in by hand is no pressing. */
    private void tellWhatHappened(StationContext station) {
        int fruitNow = fruit.get(0).getCount();
        int mashNow = mash.get(0).getCount();
        ItemStack drinks = served.get(0);
        if (mashBefore >= 0 && mashNow > mashBefore && fruitNow < fruitBefore) {
            station.effect(PressStation.EFFECT_PRESSED, PressStation.BASKET);
        }
        if (servedBefore >= 0 && drinks.getCount() > servedBefore) {
            bottled(station, drinks, PressStation.SERVED);
        }
        ItemStack tub = press.tub();
        if (tubBefore >= 0 && tub.getCount() > tubBefore) {
            bottled(station, tub, PressStation.TUB);
        }
        fruitBefore = fruitNow;
        mashBefore = mashNow;
        servedBefore = drinks.getCount();
        tubBefore = tub.getCount();
    }

    private static void bottled(StationContext station, ItemStack drink, String where) {
        CompoundTag data = new CompoundTag();
        data.putString(PressStation.VESSEL, BuiltInRegistries.ITEM.getKey(drink.getItem()).toString());
        station.effect(PressStation.EFFECT_BOTTLED, where, data);
    }

    /** Whether the press presses {@code stack}. */
    public boolean isFruit(ItemStack stack) {
        return VineryPressing.taking(mashing, stack) != null;
    }

    /** Whether the press bottles {@code stack} as it is. */
    public boolean isMash(ItemStack stack) {
        return VineryPressing.taking(bottling, stack) != null;
    }

    /**
     * Only what the press presses goes into the basket, and only what it bottles lies before it, both only while the basket is
     * open. Bottles are filled at the tub, never laid in.
     */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (slot.group().equals(PressStation.FRUIT)) {
            return press.isOpen() && isFruit(stack);
        }
        if (slot.group().equals(PressStation.BOTTLES)) {
            return false;
        }
        return !slot.group().equals(PressStation.MASH) || press.isOpen() && isMash(stack);
    }

    /** What the press's own result slot does for a player who takes what was bottled. */
    @Override
    public void onTaken(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (slot.equals(PressStation.SERVED_SLOT)) {
            stack.onCraftedBy(station.level(), player, stack.getCount());
        }
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (!press.isReal() || station.level().isClientSide()) {
            return;
        }
        if (PressStation.ACTION_DOORS.equals(action)) {
            if (press.basket() != null) {
                press.basket().handson$setOpen(data.getBoolean(PressStation.OPEN));
            }
            refresh(station);
            return;
        }
        if (PressStation.ACTION_PRESS.equals(action)) {
            press(station);
            return;
        }
        if (PressStation.ACTION_DRAW.equals(action)) {
            draw(station, player, data.getBoolean(PressStation.ALL), data.contains(PressStation.FROM) ? data.getInt(PressStation.FROM) : -1);
            return;
        }
        if (!PressStation.ACTION_FILL.equals(action)) {
            return;
        }
        // A pick in the book opens the basket to lay the fruit in, as a cook would.
        if (press.basket() != null) {
            press.basket().handson$setOpen(true);
        }
        ResourceLocation id = ResourceLocation.tryParse(data.getString(PressStation.STEP));
        fill(player, id, data.getBoolean(PressStation.ALL) ? FILL_MOST : 1);
        player.getInventory().setChanged();
        refresh(station);
    }

    /**
     * The cook presses the screw's cap: an open basket is shut first, and the press counts the press; with nothing to press under the
     * plate everybody sees the cap give nothing. The press's own effects tell the rest (a load pressed, a bottle's worth in the tub).
     */
    private void press(StationContext station) {
        PressBasket basket = press.basket();
        if (basket == null) {
            return;
        }
        basket.handson$attend(station.level().getGameTime() + ATTENDED);
        if (basket.handson$isOpen()) {
            basket.handson$setOpen(false);
        }
        if (basket.handson$press() < 0) {
            station.effect(PressStation.EFFECT_NOTHING, "screw");
        }
        tellWhatHappened(station);
        refresh(station);
    }

    /**
     * At the tub: what it holds runs into empty wine bottles, one or with {@code all} as many as it holds and there are bottles:
     * those on the cursor (or in inventory slot {@code from}), or with an empty hand the inventory's, from as many of its stacks
     * as it takes. Each bottle filled pays back the one the press was lent.
     */
    private void draw(StationContext station, Player player, boolean all, int from) {
        PressBasket basket = press.basket();
        ItemStack tub = press.tub();
        Inventory inventory = player.getInventory();
        ItemStack held = from >= 0 ? inventory.getItem(from) : player.inventoryMenu.getCarried();
        if (basket == null || tub.isEmpty() || !held.isEmpty() && !BarrelStation.isWineBottle(held)) {
            return;
        }
        boolean filledHeld = !held.isEmpty();
        int want = all ? tub.getCount() : 1;
        int count = 0;
        while (count < want) {
            ItemStack source = filledHeld ? held : BarrelLogic.emptyBottle(inventory);
            int taken = Math.min(want - count, source.getCount());
            if (taken <= 0) {
                break;
            }
            source.shrink(taken);
            count += taken;
        }
        if (count <= 0) {
            return;
        }
        ItemStack drawn = tub.copyWithCount(count);
        basket.handson$setTub(tub.getCount() > count ? tub.copyWithCount(tub.getCount() - count) : ItemStack.EMPTY);
        tubBefore = press.tub().getCount();
        drawn.onCraftedBy(station.level(), player, count);
        CompoundTag data = new CompoundTag();
        data.putString(PressStation.VESSEL, BuiltInRegistries.ITEM.getKey(drawn.getItem()).toString());
        data.putInt(PressStation.COUNT, count);
        BarrelLogic.give(player, drawn, filledHeld && held.isEmpty(), from);
        player.getInventory().setChanged();
        station.effect(PressStation.EFFECT_DRAWN, PressStation.TUB, data);
        refresh(station);
    }

    /**
     * Something picked in the book. A thing the press bottles: the fruit it is pressed of goes into the basket, up to
     * {@code sets} of it, or with none in the inventory what is bottled itself goes before the plate. A thing the press only
     * presses: its fruit. A press that holds other fruit takes nothing. Bottles are not laid in: they are filled at the tub.
     */
    private void fill(Player player, ResourceLocation id, int sets) {
        Inventory inventory = player.getInventory();
        VineryPressing.Step bottled = bottling.stream().filter(step -> step.id().equals(id)).findFirst().orElse(null);
        if (bottled == null) {
            mashing.stream().filter(step -> step.id().equals(id)).findFirst()
                    .ifPresent(pressed -> move(inventory, pressed.input()::test, fruit, sets));
            return;
        }
        int laid = 0;
        for (VineryPressing.Step pressed : mashing) {
            if (bottled.input().test(pressed.result())) {
                laid += move(inventory, pressed.input()::test, fruit, sets - laid);
            }
        }
        if (laid == 0) {
            move(inventory, bottled.input()::test, mash, sets);
        }
    }

    /**
     * Moves up to {@code most} of what {@code wanted} takes from the inventory into the one slot of {@code slot}, alike to what
     * lies there; returns how many went.
     */
    private static int move(Inventory inventory, Predicate<ItemStack> wanted, SlotStorage slot, int most) {
        int moved = 0;
        for (int index = 0; index < Inventory.INVENTORY_SIZE && moved < most; index++) {
            ItemStack stack = inventory.getItem(index);
            ItemStack there = slot.get(0);
            if (stack.isEmpty() || !wanted.test(stack) || !there.isEmpty() && !ItemStack.isSameItemSameComponents(there, stack)) {
                continue;
            }
            int count = Math.min(most - moved, Math.min(stack.getCount(), stack.getMaxStackSize() - there.getCount()));
            if (count <= 0) {
                continue;
            }
            slot.set(0, stack.copyWithCount(there.getCount() + count));
            stack.shrink(count);
            moved += count;
        }
        return moved;
    }

    /** What the press makes in the end of what is in it: what it bottles of the mash, or of what the fruit is pressed to. */
    private ItemStack making() {
        ItemStack lying = mash.get(0);
        VineryPressing.Step pressed = VineryPressing.taking(mashing, fruit.get(0));
        ItemStack toBottle = !lying.isEmpty() ? lying : pressed == null ? ItemStack.EMPTY : pressed.result();
        VineryPressing.Step bottled = VineryPressing.taking(bottling, toBottle);
        return bottled != null ? bottled.result() : pressed != null ? pressed.result() : ItemStack.EMPTY;
    }

    private void refresh(StationContext station) {
        StationState state = station.state();
        Level level = station.level();
        CompoundTag own = state.data();
        PressBasket basket = press.basket();
        own.putInt(PressStation.MASH_TOTAL, press.mashTime());
        // A bottle's worth runs at the hand's pace while the press is worked by hand.
        own.putInt(PressStation.BOTTLE_TOTAL, basket != null && basket.handson$quick() ? Press.JUICE_TICKS : press.bottleTime());
        own.putInt(PressStation.PRESSES, basket == null ? 0 : basket.handson$presses());
        own.putBoolean(PressStation.BY_HAND, !station.users().isEmpty());
        // The start and the length, not the count: the count changes every tick, and every change is sent to every client.
        // The press keeps both starts, as the server told them, on a client's copy too.
        if (basket != null) {
            // Nothing runs while the basket stands open: the press waits.
            boolean open = basket.handson$isOpen();
            since(own, PressStation.MASH_STARTED, open ? -1L : basket.handson$mashSince());
            since(own, PressStation.BOTTLE_STARTED, open ? -1L : basket.handson$bottleSince());
            own.putBoolean(PressStation.OPEN, open);
        } else {
            started(own, PressStation.MASH_STARTED, level, press.mashed());
            started(own, PressStation.BOTTLE_STARTED, level, press.bottled());
            own.putBoolean(PressStation.OPEN, true);
        }
        ItemStack tub = press.tub();
        state.setTank(PressStation.TUB, tub.isEmpty() ? TankContents.EMPTY
                : TankContents.of(BuiltInRegistries.ITEM.getKey(tub.getItem()), tub.getCount()));
        ItemStack making = press.isReal() ? making() : ItemStack.EMPTY;
        if (making.isEmpty()) {
            own.remove(PressStation.MAKING);
        } else {
            own.putString(PressStation.MAKING, BuiltInRegistries.ITEM.getKey(making.getItem()).toString());
        }
        state.setData(own);
    }

    private static void since(CompoundTag own, String key, long since) {
        if (since >= 0) {
            own.putLong(key, since);
        } else {
            own.remove(key);
        }
    }

    private static void started(CompoundTag own, String key, Level level, int progress) {
        if (progress > 0 && !level.isClientSide()) {
            own.putLong(key, level.getGameTime() - progress);
        } else {
            own.remove(key);
        }
    }
}
