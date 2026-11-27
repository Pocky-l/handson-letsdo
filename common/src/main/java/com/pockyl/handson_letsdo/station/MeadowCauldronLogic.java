package com.pockyl.handson_letsdo.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

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
 * The cooking cauldron by hand. Nothing here cooks: the station lays water and food into the cauldron's own slots and the cauldron
 * cooks in its own tick, as it does for its menu and for hoppers. Water is poured from a bucket or a bottle into its water slot, which
 * its tick empties into its water; what held it goes back to the cook. Food dropped in lies together, alike things in one stack,
 * and the cauldron reads a dish one ingredient a slot: what lies in it is laid out the way the dish it makes wants it. The cook's
 * ladle hurries the dish: every turn of it round the cauldron while a dish cooks brings the cauldron's own count on by
 * {@code 1 / TURNS} of the dish's time; left alone it cooks in Meadow's time, never slower. The dish waits in the cauldron's dish
 * slot, floating in the water, and is taken from there.
 */
public final class MeadowCauldronLogic implements StationLogic {
    /** Where what pouring left goes back to: the cursor, or an inventory slot (0 and up); else just the inventory. */
    private static final int CURSOR = -1;
    private static final int ANYWHERE = -2;
    /** The most sets one pick in the book lays in. */
    private static final int FILL_MOST = 64;

    private final MeadowCauldron cauldron;
    private final SlotStorage served;
    private final SlotStorage ingredients;
    private final SlotStorage waiting;
    private final HandTurns turns = new HandTurns(MeadowCauldronStation.FASTEST_TURN_TICKS);
    /** Ticks of the dish the ladle has earned and the cauldron has not been told of yet: less than one. */
    private float hurried;
    /** When the ladle was last reported, and where its end is; whether the clients were told since it last hurried the dish. */
    private long stirredAt = Long.MIN_VALUE;
    private float ladleX;
    private float ladleZ;
    private boolean untold;
    /** A cook changed what lies in the cauldron: it is laid out on the next tick. */
    private boolean unsorted;
    /** The dish the cook last picked in the book: the cauldron's choice while its food makes it, until it is empty. */
    private ResourceLocation wanted;
    /** Who poured the water that waits in the water slot, and where it came from: what it leaves goes back there. */
    private UUID pourer;
    private int pouredFrom = ANYWHERE;
    /** The food the dish {@link #read} was read from; null before the first look. */
    private List<ItemStack> readFrom;
    private MeadowCauldron.Dish read;
    /** What the dish slot held when last looked, to tell everybody when a dish is done; null before the first look. */
    private ItemStack servedBefore;

    public MeadowCauldronLogic(StationContext station) {
        cauldron = MeadowCauldron.orEmpty(station.blockEntity());
        StationState state = station.state();
        served = SlotStorage.of(cauldron.container(), MeadowCauldron.OUTPUT_SLOT, 1);
        ingredients = SlotStorage.of(cauldron.container(), MeadowCauldron.FIRST_INGREDIENT, MeadowCauldron.INGREDIENTS);
        waiting = SlotStorage.of(cauldron.container(), MeadowCauldron.WATER_SLOT, 1);
        state.bindSlots(MeadowCauldronStation.SERVED, served);
        state.bindSlots(MeadowCauldronStation.INGREDIENTS, ingredients);
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
            if (stirredAt != Long.MIN_VALUE && station.level().getGameTime() - stirredAt > MeadowCauldronStation.STIR_GAP) {
                letGo(station);
            }
            settleWater(station);
            if (wanted != null && StationRecipes.kinds(ingredients).isEmpty()) {
                wanted = null;
            }
            if (unsorted) {
                unsorted = false;
                arrange(station.level());
            }
            tellWhatHappened(station);
        }
        refresh(station);
    }

    /** The ladle was let go: it rests again, and the clients near are told how far the dish is now. */
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

    /** The cauldron has poured in the water that waited: what held it, an empty bucket, goes back to where the water came from. */
    private void settleWater(StationContext station) {
        ItemStack left = waiting.get(0);
        if (pourer == null || MeadowCauldron.takesAsWater(left)) {
            return;
        }
        Player cook = cookAt(station, pourer);
        if (!left.isEmpty() && cook != null) {
            waiting.set(0, ItemStack.EMPTY);
            give(cook, left.copy(), pouredFrom);
            cook.getInventory().setChanged();
        }
        pourer = null;
    }

    private static Player cookAt(StationContext station, UUID id) {
        for (Player user : station.users()) {
            if (user.getUUID().equals(id)) {
                return user;
            }
        }
        return null;
    }

    /** The cauldron cooked a dish in its own tick: everybody sees it done. */
    private void tellWhatHappened(StationContext station) {
        ItemStack dish = served.get(0);
        int before = servedBefore != null && ItemStack.isSameItemSameTags(servedBefore, dish) ? servedBefore.getCount() : 0;
        if (servedBefore != null && !dish.isEmpty() && dish.getCount() > before) {
            CompoundTag data = new CompoundTag();
            data.putString(MeadowCauldronStation.ITEM, BuiltInRegistries.ITEM.getKey(dish.getItem()).toString());
            station.effect(MeadowCauldronStation.EFFECT_COOKED, MeadowCauldronStation.MOUTH, data);
        }
        servedBefore = dish.copy();
    }

    @Override
    public void onSlotChanged(StationContext station, SlotRef slot) {
        if (slot.group().equals(MeadowCauldronStation.INGREDIENTS) && !station.level().isClientSide()) {
            unsorted = true;
        }
    }

    /**
     * Things dropped in lie together, two of a kind in one stack, and the cauldron reads a dish one ingredient a slot: what lies in it
     * is laid out the way the dish it makes wants it ({@link StationRecipes#choose}). What makes nothing stays as it lies.
     */
    private void arrange(Level level) {
        List<ItemStack> kinds = StationRecipes.kinds(ingredients);
        if (kinds.isEmpty()) {
            return;
        }
        List<StationRecipes.Candidate<MeadowCauldron.Dish>> candidates = candidates(level);
        StationRecipes.Candidate<MeadowCauldron.Dish> found = StationRecipes.reads(candidates, inCauldron());
        StationRecipes.Choice<MeadowCauldron.Dish> choice = StationRecipes.choose(candidates, kinds, MeadowCauldron.INGREDIENTS, wanted,
                found == null ? null : found.id(), false);
        if (choice != null) {
            StationRecipes.lay(ingredients, kinds, choice.parts());
        }
    }

    /** How {@code kinds} lie for the dish they make ({@link #arrange}), or null. */
    private int[] partsWith(Level level, List<ItemStack> kinds) {
        List<StationRecipes.Candidate<MeadowCauldron.Dish>> candidates = candidates(level);
        StationRecipes.Candidate<MeadowCauldron.Dish> found = StationRecipes.reads(candidates, inCauldron());
        StationRecipes.Choice<MeadowCauldron.Dish> choice = StationRecipes.choose(candidates, kinds, MeadowCauldron.INGREDIENTS, wanted,
                found == null ? null : found.id(), true);
        return choice == null ? null : choice.parts();
    }

    private List<ItemStack> inCauldron() {
        List<ItemStack> in = new ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            in.add(ingredients.get(i));
        }
        return in;
    }

    private static List<StationRecipes.Candidate<MeadowCauldron.Dish>> candidates(Level level) {
        return StationRecipes.candidates(MeadowCauldron.dishes(level), MeadowCauldron.Dish::id, MeadowCauldron.Dish::ingredients);
    }

    /** Only what some dish is made of goes into the water (Meadow's menu takes anything). */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return !slot.group().equals(MeadowCauldronStation.INGREDIENTS) || MeadowCauldron.isIngredient(station.level(), stack);
    }

    /** What taking a dish does elsewhere: the cook made it. */
    @Override
    public void onTaken(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (slot.equals(MeadowCauldronStation.SERVED_SLOT)) {
            stack.onCraftedBy(station.level(), player, stack.getCount());
            servedBefore = served.get(0).copy();
        }
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (!cauldron.isReal() || station.level().isClientSide()) {
            return;
        }
        int from = data.contains(MeadowCauldronStation.FROM) ? data.getInt(MeadowCauldronStation.FROM) : CURSOR;
        if (from >= Inventory.INVENTORY_SIZE || from < CURSOR) {
            return;
        }
        if (MeadowCauldronStation.ACTION_POUR.equals(action)) {
            pour(station, player, from);
        } else if (MeadowCauldronStation.ACTION_STIR.equals(action)) {
            stir(station, data.getFloat(MeadowCauldronStation.X), data.getFloat(MeadowCauldronStation.Z));
        } else if (MeadowCauldronStation.ACTION_FILL.equals(action)) {
            ResourceLocation id = ResourceLocation.tryParse(data.getString(MeadowCauldronStation.DISH));
            MeadowCauldron.dishes(station.level()).stream().filter(dish -> dish.id().equals(id)).findFirst()
                    .ifPresent(dish -> fill(station, player, dish, data.getBoolean(MeadowCauldronStation.ALL) ? FILL_MOST : 1));
        } else if (MeadowCauldronStation.ACTION_TAKE.equals(action)) {
            take(station, player);
        } else if (StationRecipes.ACTION_OFFER.equals(action)) {
            // A stack clicked onto a cauldron whose slots hold other food: in, when they still make a dish with it.
            Level level = station.level();
            if (StationRecipes.offerCarried(station, this, player, MeadowCauldronStation.INGREDIENTS, ingredients,
                    data.getBoolean(StationRecipes.ONE), kinds -> partsWith(level, kinds))) {
                unsorted = true;
            }
        }
        player.getInventory().setChanged();
        refresh(station);
    }

    /** The dish that waits goes to the cook: into the inventory, or at their feet when it is full. */
    private void take(StationContext station, Player player) {
        ItemStack dish = served.get(0);
        if (dish.isEmpty()) {
            return;
        }
        served.set(0, ItemStack.EMPTY);
        ItemStack taken = dish.copy();
        taken.onCraftedBy(station.level(), player, taken.getCount());
        servedBefore = ItemStack.EMPTY;
        give(player, taken, ANYWHERE);
    }

    /** Whether water from {@code stack} can be poured in now: water, room for all of it, and no water still waiting to go in. */
    public boolean pourable(ItemStack stack) {
        int water = MeadowCauldron.waterOf(stack);
        return water > 0 && cauldron.water() + water <= MeadowCauldron.MOST_WATER && !MeadowCauldron.takesAsWater(waiting.get(0));
    }

    /**
     * One bucket or bottle of water of the cursor's stack (or of inventory slot {@code from}'s) is laid into the cauldron's water
     * slot: the cauldron pours it in on its next tick, and what held it comes back in {@link #settleWater}. Water that would not fit
     * all of it is not poured: Meadow's cauldron would spill the rest. Something else left in that slot by a hopper goes to the cook
     * first.
     */
    private boolean pour(StationContext station, Player player, int from) {
        Inventory inventory = player.getInventory();
        ItemStack held = from >= 0 ? inventory.getItem(from) : player.inventoryMenu.getCarried();
        if (!pourable(held)) {
            return false;
        }
        ItemStack left = waiting.get(0);
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
        data.putString(MeadowCauldronStation.ITEM, BuiltInRegistries.ITEM.getKey(vessel.getItem()).toString());
        station.effect(MeadowCauldronStation.EFFECT_POURED, MeadowCauldronStation.MOUTH, data);
        return true;
    }

    /**
     * A dish picked in the book: up to {@code sets} sets of what it is made of go in from the inventory, laid out the way the dish is
     * read, and water from the inventory when the cauldron has less than the dish takes. A cauldron that holds food the dish does not
     * use takes nothing. The picked dish stays the cauldron's choice while its food makes it.
     */
    private void fill(StationContext station, Player player, MeadowCauldron.Dish dish, int sets) {
        wanted = dish.id();
        List<ItemStack> kinds = StationRecipes.kinds(ingredients);
        StationRecipes.Filled filled = StationRecipes.fill(player.getInventory(), dish.ingredients(), kinds, MeadowCauldron.INGREDIENTS,
                sets);
        if (filled != null) {
            StationRecipes.lay(ingredients, filled.kinds(), filled.parts());
        }
        if (cauldron.water() < dish.water() && !MeadowCauldron.takesAsWater(waiting.get(0))) {
            int slot = waterSlot(player.getInventory());
            if (slot >= 0) {
                pour(station, player, slot);
            }
        }
    }

    /** The inventory slot of the first water that fits in the cauldron, the selected one first; -1 for none. */
    private int waterSlot(Inventory inventory) {
        if (pourable(inventory.getSelected())) {
            return inventory.selected;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (pourable(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * The ladle's end is at {@code x, z} now, pixels from the middle in the cauldron's own axes: the way it went round the middle since
     * the last report hurries the dish that cooks, at most as fast as a hand turns it.
     */
    private void stir(StationContext station, float x, float z) {
        float reach = MeadowCauldronStation.LADLE_REACH;
        float length = Mth.sqrt(x * x + z * z);
        if (!Float.isFinite(length)) {
            return;
        }
        if (length > reach) {
            x *= reach / length;
            z *= reach / length;
        }
        long now = station.level().getGameTime();
        // Round the middle only: an end that stays near it turns the water round nothing.
        float way = length < 0.5F ? 0.0F : Math.abs(turns.report(now, (float) Mth.atan2(z, x)));
        stirredAt = now;
        ladleX = x;
        ladleZ = z;
        int duration = cauldron.duration();
        if (!cauldron.cooks() || duration <= 0) {
            hurried = 0.0F;
            return;
        }
        hurried += way / Mth.TWO_PI * duration / MeadowCauldronStation.TURNS;
        int ticks = (int) hurried;
        if (ticks > 0) {
            hurried -= ticks;
            // Its own tick counts the last one and cooks.
            cauldron.setTime(Math.min(cauldron.time() + ticks, duration - 1));
            untold = true;
        }
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

    /** The dish the food in the cauldron makes, read again only when the food changed: the logic runs every tick, also in the world. */
    private MeadowCauldron.Dish making(Level level) {
        if (!cauldron.isReal()) {
            return null;
        }
        List<ItemStack> now = cauldron.ingredients();
        boolean same = readFrom != null;
        for (int i = 0; same && i < now.size(); i++) {
            same = ItemStack.matches(now.get(i), readFrom.get(i));
        }
        if (!same) {
            readFrom = new ArrayList<>();
            now.forEach(stack -> readFrom.add(stack.copy()));
            read = MeadowCauldron.making(level, now);
        }
        return read;
    }

    private void refresh(StationContext station) {
        StationState state = station.state();
        Level level = station.level();
        CompoundTag own = state.data();
        boolean cooking = cauldron.cooks();
        own.putBoolean(MeadowCauldronStation.COOKING, cooking);
        own.putInt(MeadowCauldronStation.DURATION, cauldron.duration());
        // The start and the length, not the count: the count changes every tick, and every change is sent to every client.
        if (cooking) {
            long now = level.getGameTime();
            long told = !level.isClientSide() ? now
                    : station.blockEntity() instanceof CauldronTold copy ? copy.handson$toldAt(now) : now;
            own.putLong(MeadowCauldronStation.STARTED, told - cauldron.time());
        } else {
            own.remove(MeadowCauldronStation.STARTED);
        }
        int water = cauldron.water();
        own.putInt(MeadowCauldronStation.WATER_LEVEL, water);
        state.setTank(MeadowCauldronStation.WATER,
                water <= 0 ? TankContents.EMPTY : TankContents.of(MeadowCauldronStation.WATER_LIQUID, water));
        BlockEntity entity = station.blockEntity();
        own.putBoolean(MeadowCauldronStation.HEATED,
                entity != null && MeadowCauldron.isHeated(level, station.pos(), entity.getBlockState()));
        MeadowCauldron.Dish making = making(level);
        if (making != null) {
            own.putString(MeadowCauldronStation.MAKING, making.id().toString());
            own.putInt(MeadowCauldronStation.MAKING_WATER, making.water());
        } else {
            own.remove(MeadowCauldronStation.MAKING);
            own.remove(MeadowCauldronStation.MAKING_WATER);
        }
        boolean stirring = stirredAt != Long.MIN_VALUE;
        own.putBoolean(MeadowCauldronStation.STIRRING, stirring);
        if (stirring) {
            own.putFloat(MeadowCauldronStation.LADLE_X, ladleX);
            own.putFloat(MeadowCauldronStation.LADLE_Z, ladleZ);
        } else {
            own.remove(MeadowCauldronStation.LADLE_X);
            own.remove(MeadowCauldronStation.LADLE_Z);
        }
        state.setData(own);
    }
}
