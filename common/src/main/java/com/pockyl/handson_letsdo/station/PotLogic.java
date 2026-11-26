package com.pockyl.handson_letsdo.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
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
import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.KitchenTags;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A pot of Farm &amp; Charm by hand, with the rules of Hands-On Delight's pot: food is thrown in and laid out the way the pot
 * reads the dish it makes, nothing cooks unless it is stirred ({@link PotCooking}), and the soup that is done is filled into bowls
 * dipped into it. What a serving's ingredients leave (a bucket) goes to the cook.
 */
public final class PotLogic implements StationLogic {
    /** How full the pot looks: there is always water, and a little more for every stack in it. */
    private static final int BROTH_BASE = 58;
    private static final int BROTH_PER_STACK = 6;
    /** A finished soup stands high in the pot and sinks serving by serving. */
    private static final int SOUP_BASE = 74;
    private static final int SOUP_PER_SERVING = 5;
    /** How long a stir of the spoon keeps the pot cooking, in ticks: the soup goes on turning for that long. */
    private static final int STIR_TICKS = 40;
    /** Ticks the spoon stays in one cook's hand after it last moved: one spoon, one hand at a time. */
    private static final int SPOON_FREE = 10;

    private final Pot pot;
    /** The pot's stirring and its soup; null for a pot this mod's mixin is not in, which only holds what is laid into it. */
    private final PotCooking cooking;
    private final SlotStorage food;
    private final SlotStorage vessels;
    private List<Dish> dishes;
    private boolean unsorted;
    /** Who stirs the pot, or last opened it while nobody stirred: their stars are the ones that can fill. */
    private UUID cook;
    private long stirredAt = Long.MIN_VALUE / 2;
    /** The dish the cook last picked in the book: the pot's choice while its food makes it, until the pot is empty. */
    private ResourceLocation wanted;

    public PotLogic(StationContext station, Pot.Kind kind) {
        pot = Pot.orEmpty(station.blockEntity(), kind);
        cooking = pot.cooking();
        Level level = station.level();
        Container container = pot.container();
        food = new SlotStorage() {
            @Override
            public int size() {
                return Pot.INGREDIENTS;
            }

            @Override
            public ItemStack get(int index) {
                ItemStack real = container.getItem(index);
                int soup = reserved();
                if (soup <= 0 || real.isEmpty()) {
                    return real;
                }
                return real.getCount() > soup ? real.copyWithCount(real.getCount() - soup) : ItemStack.EMPTY;
            }

            @Override
            public void set(int index, ItemStack stack) {
                ItemStack real = container.getItem(index);
                int soup = Math.min(reserved(), real.getCount());
                if (soup <= 0) {
                    container.setItem(index, stack);
                } else if (stack.isEmpty() || ItemStack.isSameItemSameTags(stack, real)) {
                    // What is soup already stays under whatever of its kind still floats.
                    container.setItem(index, real.copyWithCount(soup + stack.getCount()));
                }
                container.setChanged();
            }
        };
        vessels = SlotStorage.of(container, Pot.VESSEL_SLOT, 1);
        StationState state = station.state();
        // On a client's copy the food and the soup are worked out from what the pot last told it; what the server says of them
        // at the station must show at once all the same (a soup ladled out stayed on show until the cook left).
        boolean client = level.isClientSide();
        state.bindSlots(CookingPotStation.INGREDIENTS, ToldSlots.of(client, food));
        state.bindSlots(CookingPotStation.MEAL, ToldSlots.of(client, new SlotStorage() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public ItemStack get(int index) {
                Dish soup = cooking == null ? null : cooking.soup(level, pot);
                return soup == null ? ItemStack.EMPTY : soup.result().copyWithCount(Math.min(cooking.servings(), soup.result().getMaxStackSize()));
            }

            @Override
            public void set(int index, ItemStack stack) {
                // The soup is in the pot, not in a slot: it only leaves in a vessel.
            }
        }));
        state.bindSlots(CookingPotStation.SERVED, SlotStorage.of(container, Pot.OUTPUT_SLOT, 1));
        state.bindSlots(CookingPotStation.VESSELS, vessels);
        dishes = pot.isReal() ? kind.dishes(level) : List.of();
        refresh(station);
    }

    /** Sets of ingredients that are the soup that waits: not shown as food any more, and not to be taken out. */
    private int reserved() {
        return cooking == null ? 0 : cooking.reserved();
    }

    /** The dishes are read again for every cook: a reload may have changed them. */
    @Override
    public void onOpen(StationContext station, Player player) {
        if (pot.isReal()) {
            dishes = pot.kind().dishes(station.level());
            // The pot cooks for whoever works at it, as for whoever opened its menu: a recipe that wants learning asks who.
            BlockOwner.set(pot.entity(), player);
        }
        if (cooking != null) {
            cooking.attend(station.level().getGameTime(), player);
        }
        if (!isStirring(station)) {
            cook = player.getUUID();
            if (cooking != null) {
                cooking.cookedBy(player);
            }
        }
        refresh(station);
    }

    private boolean isStirring(StationContext station) {
        return station.level().getGameTime() - stirredAt < SPOON_FREE;
    }

    /** Who holds the spoon: one of those at the station, as long as they are there. */
    private Player cook(StationContext station) {
        if (cook == null) {
            return null;
        }
        for (Player user : station.users()) {
            if (user.getUUID().equals(cook)) {
                return user;
            }
        }
        return station.level().getPlayerByUUID(cook);
    }

    @Override
    public void onSlotChanged(StationContext station, SlotRef slot) {
        if (slot.group().equals(CookingPotStation.INGREDIENTS) && !station.level().isClientSide()) {
            unsorted = true;
            if (cooking != null) {
                cooking.feedByHand();
            }
        }
    }

    @Override
    public void tick(StationContext station) {
        if (station.level().isClientSide()) {
            // A client's copy of the logic, while nobody works at the pot: it only shows what the pot told this client it holds.
            refresh(station);
            return;
        }
        if (cooking != null) {
            station.users().stream().findFirst().ifPresent(user -> cooking.attend(station.level().getGameTime(), user));
        }
        if (wanted != null && StationRecipes.kinds(food).isEmpty()) {
            wanted = null;
        }
        if (unsorted) {
            unsorted = false;
            shareOut(station);
        }
        giveBackLeftovers(station);
        refresh(station);
    }

    /** What the ingredients left when a dish was made (a water bucket's bucket) goes back to the cook, or to anyone at the pot. */
    private void giveBackLeftovers(StationContext station) {
        List<ItemStack> leftovers = cooking == null ? List.of() : cooking.takeLeftovers();
        if (leftovers.isEmpty()) {
            return;
        }
        Player to = recipient(station);
        for (ItemStack leftover : leftovers) {
            if (to == null) {
                Containers.dropItemStack(station.level(), station.pos().getX() + 0.5, station.pos().getY() + 1.0, station.pos().getZ() + 0.5,
                        leftover);
            } else {
                give(to, leftover);
            }
        }
    }

    /** The cook, or anyone at the pot; null with nobody there. */
    private Player recipient(StationContext station) {
        Player to = null;
        for (Player user : station.users()) {
            if (to == null || user.getUUID().equals(cook)) {
                to = user;
            }
        }
        return to;
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    /**
     * After a cook's change, the food is laid out the way the pot reads the dish it makes ({@link StationRecipes#choose}): two
     * tomatoes thrown in lie in one slot, and the soup of tomato, tomato and sugar wants them in two. Food that makes nothing
     * stays as it lies. More than a dish's slots hold goes back to the cook; with nobody there it stays. Not while a soup waits:
     * its food lies as it was cooked.
     */
    private void shareOut(StationContext station) {
        if (reserved() > 0) {
            return;
        }
        List<ItemStack> kinds = StationRecipes.kinds(food);
        if (kinds.isEmpty()) {
            return;
        }
        StationRecipes.Choice<Dish> choice = choose(station, kinds, !station.users().isEmpty());
        if (choice != null) {
            List<ItemStack> rest = StationRecipes.lay(food, kinds, choice.parts());
            Player to = recipient(station);
            for (ItemStack kind : rest) {
                // One kind may be more than a stack: the inventory takes any count, a dropped item only one stack.
                for (int count = kind.getCount(); count > 0 && to != null; count -= kind.getMaxStackSize()) {
                    give(to, kind.copyWithCount(Math.min(count, kind.getMaxStackSize())));
                }
            }
        }
    }

    private List<StationRecipes.Candidate<Dish>> candidates() {
        return StationRecipes.candidates(dishes, Dish::id, Dish::ingredients);
    }

    /** The dish {@code kinds} make in this pot ({@link StationRecipes#choose}); null for none. */
    private StationRecipes.Choice<Dish> choose(StationContext station, List<ItemStack> kinds, boolean overflow) {
        Dish lying = cooking == null ? null : cooking.making(station.level(), pot);
        return StationRecipes.choose(candidates(), kinds, Pot.INGREDIENTS, wanted, lying == null ? null : lying.id(), overflow);
    }

    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return !slot.equals(CookingPotStation.MEAL_SLOT) && !slot.equals(CookingPotStation.VESSEL_SLOT);
    }

    /**
     * Only what some dish is made of goes into the water; what a soup is served in does not, unless some dish cooks with it. A pot
     * full of finished soup takes nothing more until it is served.
     */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (!slot.group().equals(CookingPotStation.INGREDIENTS)) {
            return false;
        }
        return reserved() <= 0 && isIngredient(stack);
    }

    /** Whether some dish of this pot takes {@code stack} as an ingredient. */
    public boolean isIngredient(ItemStack stack) {
        for (Dish dish : dishes) {
            if (dish.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack))) {
                return true;
            }
        }
        return false;
    }

    /** Whether some dish of this pot is served in {@code stack}. */
    public boolean isVessel(ItemStack stack) {
        return dishes.stream().anyMatch(dish -> dish.servedIn(stack));
    }

    /** What a result slot does for a player who takes the dish: it counts as made by them. */
    @Override
    public void onTaken(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (slot.equals(CookingPotStation.SERVED_SLOT)) {
            stack.onCraftedBy(station.level(), player, stack.getCount());
        }
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (!pot.isReal() || station.level().isClientSide()) {
            return;
        }
        if (StationRecipes.ACTION_OFFER.equals(action)) {
            if (reserved() <= 0 && StationRecipes.offerCarried(station, this, player, CookingPotStation.INGREDIENTS, food,
                    data.getBoolean(StationRecipes.ONE), kinds -> {
                        StationRecipes.Choice<Dish> choice = choose(station, kinds, true);
                        return choice == null ? null : choice.parts();
                    })) {
                if (cooking != null) {
                    cooking.feedByHand();
                }
                unsorted = true;
            }
        } else if (CookingPotStation.ACTION_SCOOP.equals(action)) {
            scoop(station, player, data.getBoolean(CookingPotStation.SCOOP_ALL), data.getBoolean(CookingPotStation.FROM_INVENTORY));
        } else if (CookingPotStation.ACTION_FILL.equals(action)) {
            ResourceLocation id = ResourceLocation.tryParse(data.getString(CookingPotStation.DISH));
            dishes.stream().filter(dish -> dish.id().equals(id)).findFirst()
                    .ifPresent(dish -> fill(player, dish, data.getBoolean(CookingPotStation.SCOOP_ALL) ? CookingPotStation.FILL_MOST : 1));
        } else if (CookingPotStation.ACTION_TAKE_BACK.equals(action)) {
            takeBack(player);
        } else if (CookingPotStation.ACTION_STIR.equals(action) && cooking != null) {
            stir(station, player, data);
        }
        player.getInventory().setChanged();
    }

    private void stir(StationContext station, Player player, CompoundTag data) {
        long now = station.level().getGameTime();
        if (isStirring(station) && !player.getUUID().equals(cook)) {
            return;
        }
        stirredAt = now;
        cooking.stir(now + STIR_TICKS);
        cooking.cookedBy(player);
        cook = player.getUUID();
        BlockOwner.set(pot.entity(), player);
        float x = spoon(data.getFloat(CookingPotStation.STIR_X));
        float z = spoon(data.getFloat(CookingPotStation.STIR_Z));
        Dish dish = cooking.making(station.level(), pot);
        float at = data.contains(CookingPotStation.STIR_AT) && Float.isFinite(data.getFloat(CookingPotStation.STIR_AT))
                ? data.getFloat(CookingPotStation.STIR_AT) : Float.NaN;
        boolean cooks = dish != null && cooking.servings() <= 0 && pot.isHeated() && PotCooking.batch(pot, dish) > 0;
        cooking.skill().move(now, at, x, z, cooks, PotStars.open(player, dish == null ? ItemStack.EMPTY : dish.result()));
        station.state().setControl(CookingPotStation.SPOON_X, x);
        station.state().setControl(CookingPotStation.SPOON_Z, z);
    }

    /** Where the client says the spoon is, -1 to 1 across the pot; a broken value is the middle, never a NaN kept in the stars. */
    private static float spoon(float at) {
        return Float.isFinite(at) ? Mth.clamp(at, -1.0F, 1.0F) : 0.0F;
    }

    /**
     * What went in without floating, so that no click on it can take it (water poured, an egg cracked, sugar dissolved), comes
     * back out whole to the inventory, the last first: nothing put in by mistake stays in the pot for good, and water comes back
     * in its bucket.
     */
    private void takeBack(Player player) {
        for (int i = Pot.INGREDIENTS - 1; i >= 0; i--) {
            ItemStack stack = food.get(i);
            if (!stack.isEmpty() && sinks(stack)) {
                ItemStack out = stack.copy();
                food.set(i, ItemStack.EMPTY);
                for (int count = out.getCount(); count > 0; count -= out.getMaxStackSize()) {
                    give(player, out.copyWithCount(Math.min(count, out.getMaxStackSize())));
                }
                unsorted = true;
                return;
            }
        }
    }

    /** Whether {@code stack} goes into the soup without floating in it: a liquid poured, an egg cracked, what dissolves. */
    public static boolean sinks(ItemStack stack) {
        return KitchenTags.is(stack, KitchenTags.POURED) || KitchenTags.is(stack, KitchenTags.CRACKED)
                || KitchenTags.is(stack, KitchenTags.DISSOLVES);
    }

    /**
     * Puts up to {@code sets} whole sets of the dish's ingredients from the player's inventory into the pot and lays them out the
     * way the pot reads the dish; the picked dish stays the pot's choice while the food makes it. An empty pot takes any dish, one
     * that holds food only more of what it makes; one that holds a finished soup none.
     */
    private void fill(Player player, Dish dish, int sets) {
        if (reserved() > 0) {
            return;
        }
        StationRecipes.Filled filled = StationRecipes.fill(player.getInventory(), dish.ingredients().stream()
                .filter(ingredient -> !ingredient.isEmpty()).toList(), StationRecipes.kinds(food), Pot.INGREDIENTS, sets);
        if (filled == null) {
            return;
        }
        wanted = dish.id();
        StationRecipes.lay(food, filled.kinds(), filled.parts());
        if (cooking != null) {
            cooking.feedByHand();
        }
    }

    /**
     * Vessels take servings of the soup that waits: those on the player's cursor, or with {@code fromInventory} and an empty hand
     * those in the inventory; one, or with {@code all} as many as there are and the soup fills. The filled ones go to the
     * inventory; the last one of a stack held stays in the hand.
     */
    private void scoop(StationContext station, Player player, boolean all, boolean fromInventory) {
        Inventory inventory = player.getInventory();
        if (fromInventory && !player.inventoryMenu.getCarried().isEmpty()) {
            return;
        }
        if (cooking == null) {
            // A pot that is not stirred holds bowls for its own cooking: they are laid into it.
            layVessels(player);
            return;
        }
        int scooped = 0;
        do {
            int slot = fromInventory ? vesselSlot(station.level(), inventory) : -1;
            ItemStack source = fromInventory ? slot < 0 ? ItemStack.EMPTY : inventory.getItem(slot) : player.inventoryMenu.getCarried();
            PotCooking.Served served = source.isEmpty() ? null : cooking.serve(source);
            if (served == null) {
                break;
            }
            scooped++;
            ItemStack rest = source.getCount() > 1 ? source.copyWithCount(source.getCount() - 1) : ItemStack.EMPTY;
            ItemStack dish = served.dish();
            dish.onCraftedBy(station.level(), player, dish.getCount());
            if (fromInventory) {
                inventory.setItem(slot, rest);
                give(player, dish);
            } else if (rest.isEmpty()) {
                player.inventoryMenu.setCarried(dish);
            } else {
                player.inventoryMenu.setCarried(rest);
                give(player, dish);
            }
            served.left().forEach(left -> give(player, left));
        } while (all);
        if (scooped > 0) {
            CompoundTag data = new CompoundTag();
            data.putInt("count", scooped);
            station.effect(CookingPotStation.EFFECT_SCOOPED, "broth", data);
            refresh(station);
        }
    }

    /** The bowls on the cursor go into the pot's own vessel slot, as many as it takes. */
    private void layVessels(Player player) {
        ItemStack carried = player.inventoryMenu.getCarried();
        ItemStack there = vessels.get(0);
        if (carried.isEmpty() || !isVessel(carried) || !there.isEmpty() && !ItemStack.isSameItemSameTags(there, carried)) {
            return;
        }
        int moved = Math.min(carried.getCount(), carried.getMaxStackSize() - there.getCount());
        if (moved > 0) {
            vessels.set(0, carried.copyWithCount(there.getCount() + moved));
            player.inventoryMenu.setCarried(moved == carried.getCount() ? ItemStack.EMPTY : carried.copyWithCount(carried.getCount() - moved));
        }
    }

    /** The inventory slot whose stack the soup that waits is served in: the one in hand first, then the rest; -1 without one. */
    private int vesselSlot(Level level, Inventory inventory) {
        Dish soup = cooking == null ? null : cooking.soup(level, pot);
        return soup == null ? -1 : vesselSlot(soup, inventory);
    }

    /** The inventory slot whose stack {@code soup} is served in: the one in hand first, then the rest; -1 without one. */
    public static int vesselSlot(Dish soup, Inventory inventory) {
        if (soup.servedIn(inventory.getSelected())) {
            return inventory.selected;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (soup.servedIn(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private void refresh(StationContext station) {
        StationState state = station.state();
        Level level = station.level();
        long now = level.getGameTime();
        Dish soup = cooking == null ? null : cooking.soup(level, pot);
        Dish making = soup != null || cooking == null ? null : cooking.making(level, pot);
        int batch = making == null ? 0 : PotCooking.batch(pot, making);
        float progress = cooking != null && batch > 0 ? cooking.progress(pot) : !pot.isReal() || cooking != null ? 0.0F
                : Math.min(1.0F, (float) pot.time() / pot.kind().upstreamTime());
        state.setControl(CookingPotStation.PROGRESS, progress);
        state.setControl(CookingPotStation.HEATED, pot.isHeated() ? 1.0F : 0.0F);
        boolean stirred = cooking != null && cooking.isStirred(now);
        state.setControl(CookingPotStation.STIRRING, stirred ? 1.0F : 0.0F);
        state.setControl(CookingPotStation.STARS, cooking == null ? 0.0F : cooking.skill().stars(now));
        if (!stirred) {
            state.setControl(CookingPotStation.SPOON_X, 0.0F);
            state.setControl(CookingPotStation.SPOON_Z, 0.0F);
        }
        CompoundTag own = state.data();
        // In whole seconds, as the badge tells it: the clients hear of it once a second, not every tick.
        own.putInt(CookingPotStation.COOK_LEFT, cooking == null || batch <= 0 ? 0 : Mth.ceil(cooking.ticksLeft(pot, batch) / 20.0F) * 20);
        own.putInt(CookingPotStation.GRADE, cooking == null ? 0 : cooking.grade());
        own.putInt(CookingPotStation.BONUS, cooking == null ? 0 : cooking.bonus());
        own.putBoolean(CookingPotStation.STIRRED_POT, cooking != null);
        own.putInt(CookingPotStation.SERVING_TIME, pot.kind().handTime());
        own.putInt(CookingPotStation.SERVINGS, cooking == null ? 0 : cooking.servings());
        own.putInt(CookingPotStation.BATCH, batch);
        own.putBoolean(CookingPotStation.WAITS_FOR_SPOON, cooking != null && batch > 0 && cooking.waitsForSpoon(level, now));
        Dish shown = soup != null ? soup : making;
        if (soup != null) {
            own.putString(CookingPotStation.SOUP, soup.id().toString());
        } else {
            own.remove(CookingPotStation.SOUP);
        }
        if (shown != null) {
            // A finished soup keeps the stars of the dish it was.
            own.putInt(CookingPotStation.OPEN_STARS, PotStars.open(cook(station), shown.result()));
        }
        state.setData(own);
        int stacks = 0;
        for (int i = 0; i < Pot.INGREDIENTS; i++) {
            stacks += pot.ingredient(i).isEmpty() ? 0 : 1;
        }
        int broth = BROTH_BASE + BROTH_PER_STACK * stacks;
        if (soup != null) {
            broth = Math.max(broth, Math.min(100, SOUP_BASE + SOUP_PER_SERVING * cooking.servings()));
        }
        state.setTank(CookingPotStation.BROTH, TankContents.of(HandsOnLetsDo.id("broth"), broth));
    }
}
