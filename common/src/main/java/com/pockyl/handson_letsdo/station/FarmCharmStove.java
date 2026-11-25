package com.pockyl.handson_letsdo.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.pockyl.diegesis.api.station.StationContext;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Farm &amp; Charm's stove, a brick oven, as the oven station sees it: its own container and the numbers its menu shows
 * ({@link BlockNumbers}). Nothing of Farm &amp; Charm is named here but its recipes ({@link FarmCharmDishes}); the stove bakes by
 * itself, in its own time, with its own fuel, and the station only lays things into it.
 *
 * <p>Slots, as the stove has them: the baked dish, three ingredients, the fuel. Numbers: ticks its fuel still burns and burnt for
 * in all, ticks baked and ticks a dish takes.
 */
public final class FarmCharmStove implements KitchenOven {
    public static final String KIND_FOOD = "food";

    public static final int OUTPUT_SLOT = 0;
    public static final int FIRST_PLACE = 1;
    public static final int PLACES = 3;
    public static final int FUEL_SLOT = 4;
    public static final int SLOTS = 5;

    public static final int BURN = 0;
    public static final int BURN_TOTAL = 1;
    public static final int COOK = 2;
    public static final int COOK_TOTAL = 3;
    public static final int NUMBERS = 4;

    /** Ticks the stove bakes one dish for. */
    public static final int BAKE_TIME = 240;

    public static final ResourceLocation BLOCK_ID = ResourceLocation.fromNamespaceAndPath(FarmCharmDishes.MOD_ID, "stove");
    private static final ResourceLocation RECIPES = ResourceLocation.fromNamespaceAndPath(FarmCharmDishes.MOD_ID, "stove");
    public static final Supplier<Block> BLOCK = () -> BuiltInRegistries.BLOCK.get(BLOCK_ID);

    /** What burns and for how long, as furnaces know it; read again every few seconds, as tags may change. */
    private static Map<Item, Integer> fuels = Map.of();
    private static long fuelsReadAt;
    private static Block stoveBlock;
    /** The bakings made of each list of dishes read. */
    private static final Map<List<Dish>, List<Baking>> READ = new IdentityHashMap<>();
    /** Of each stove whose door stands open: the bake and the fire held, ticks (-1: no fire to hold yet). Server only. */
    private static final Map<BlockEntity, int[]> HELD = new WeakHashMap<>();

    private final BlockEntity entity;
    private final Container container;
    private final ContainerData numbers;

    private FarmCharmStove(BlockEntity entity, Container container, ContainerData numbers) {
        this.entity = entity;
        this.container = container;
        this.numbers = numbers;
    }

    /**
     * The stove of a block entity, or null for anything that is not the stove this was made for: another build of Farm &amp;
     * Charm whose stove has other slots or numbers keeps its own menu.
     */
    public static FarmCharmStove of(BlockEntity entity) {
        if (!(entity instanceof WorldlyContainer container) || container.getContainerSize() != SLOTS
                || !BuiltInRegistries.BLOCK.getKey(entity.getBlockState().getBlock()).equals(BLOCK_ID)) {
            return null;
        }
        ContainerData numbers = BlockNumbers.of(entity);
        return numbers == null || numbers.getCount() != NUMBERS ? null : new FarmCharmStove(entity, container, numbers);
    }

    /** Whether {@code entity} is Farm &amp; Charm's stove: asked for every block entity drawn, so by the block itself. */
    public static boolean isStove(BlockEntity entity) {
        if (stoveBlock == null) {
            stoveBlock = BLOCK.get();
        }
        return entity.getBlockState().getBlock() == stoveBlock;
    }

    @Override
    public BlockEntity blockEntity() {
        return entity;
    }

    public Container container() {
        return container;
    }

    @Override
    public int places() {
        return PLACES;
    }

    @Override
    public ItemStack place(int index) {
        return container.getItem(FIRST_PLACE + index);
    }

    /** The stove starts its baking anew when another kind of thing comes onto a place: its own rule. */
    @Override
    public void setPlace(int index, ItemStack stack) {
        container.setItem(FIRST_PLACE + index, stack);
        container.setChanged();
    }

    @Override
    public int outputs() {
        return 1;
    }

    @Override
    public ItemStack output(int index) {
        return container.getItem(OUTPUT_SLOT);
    }

    @Override
    public void setOutput(int index, ItemStack stack) {
        setKeepingTheBake(OUTPUT_SLOT, stack);
    }

    @Override
    public boolean burnsFuel() {
        return true;
    }

    @Override
    public ItemStack fuel() {
        return container.getItem(FUEL_SLOT);
    }

    @Override
    public void setFuel(ItemStack stack) {
        setKeepingTheBake(FUEL_SLOT, stack);
    }

    /** Its firebox is an opening of its own under the door: fuel is laid on and taken off the fire with the door shut too. */
    @Override
    public boolean fuelBehindDoor() {
        return false;
    }

    /**
     * The stove takes any change of a slot's kind of thing for a change of what bakes and starts over, also when the dish is
     * taken out or fuel is laid in. Nothing on the tray changed then: what was baked so far stays baked.
     */
    private void setKeepingTheBake(int slot, ItemStack stack) {
        int baked = numbers.get(COOK);
        int total = numbers.get(COOK_TOTAL);
        container.setItem(slot, stack);
        numbers.set(COOK, baked);
        numbers.set(COOK_TOTAL, total);
        container.setChanged();
    }

    /** Whatever a furnace burns, for as long: the stove reads the furnaces' own list. */
    @Override
    public boolean isFuel(ItemStack stack) {
        return !stack.isEmpty() && fuels().getOrDefault(stack.getItem(), 0) > 0;
    }

    private static synchronized Map<Item, Integer> fuels() {
        long now = System.currentTimeMillis();
        if (fuels.isEmpty() || now - fuelsReadAt > 5000L) {
            fuels = Map.copyOf(AbstractFurnaceBlockEntity.getFuel());
            fuelsReadAt = now;
        }
        return fuels;
    }

    @Override
    public boolean bakesEachPlace() {
        return false;
    }

    @Override
    public int perPlace() {
        return 64;
    }

    /** What some dish of the stove is made of. */
    @Override
    public boolean takes(ItemStack stack) {
        Level level = entity.getLevel();
        if (level == null || stack.isEmpty()) {
            return false;
        }
        for (Baking recipe : recipes(level)) {
            if (recipe.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack))) {
                return true;
            }
        }
        return false;
    }

    /** The tray bakes as one set: every place that holds something is as far as the dish. */
    @Override
    public float progress(int place) {
        int total = numbers.get(COOK_TOTAL);
        return place(place).isEmpty() || total <= 0 ? 0.0F : Math.min(1.0F, (float) numbers.get(COOK) / total);
    }

    @Override
    public int ticksLeft(int place) {
        int baked = numbers.get(COOK);
        return place(place).isEmpty() || baked <= 0 ? 0 : Math.max(1, numbers.get(COOK_TOTAL) - baked);
    }

    @Override
    public boolean isHot() {
        return numbers.get(BURN) > 0;
    }

    /** What lies on the tray makes a dish, and nothing burns nor is there fuel to light. */
    @Override
    public boolean waitsForHeat() {
        Level level = entity.getLevel();
        return level != null && !isHot() && fuel().isEmpty() && KitchenOven.matching(recipes(level), tray()).isPresent();
    }

    private ItemStack[] tray() {
        ItemStack[] tray = new ItemStack[PLACES];
        for (int place = 0; place < PLACES; place++) {
            tray[place] = place(place);
        }
        return tray;
    }

    @Override
    public List<Baking> recipes(Level level) {
        return recipesOf(level);
    }

    /** The stove's dishes as bakings; kept for as long as the game's recipes are the same (a server's and a client's). */
    public static synchronized List<Baking> recipesOf(Level level) {
        List<Dish> dishes = FarmCharmDishes.all(level, RECIPES);
        List<Baking> known = READ.get(dishes);
        if (known == null) {
            List<Baking> recipes = new ArrayList<>();
            for (Dish dish : dishes) {
                List<Ingredient> needed = dish.ingredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
                if (!needed.isEmpty() && needed.size() <= PLACES) {
                    recipes.add(new Baking(dish.id(), needed, 0, 0, dish.result(), BAKE_TIME, KIND_FOOD));
                }
            }
            if (READ.size() > 4) {
                READ.clear();
            }
            known = List.copyOf(recipes);
            READ.put(dishes, known);
        }
        return known;
    }

    /** The stove cooks for whoever works at it, as for whoever opened its menu: a recipe that wants learning asks who. */
    @Override
    public void cookCame(Player player) {
        BlockOwner.set(entity, player);
    }

    /**
     * The stove bakes only with its door shut: while it stands open, what bakes and the fire are held where
     * they were, and both go on as it is shut. Nothing is lost by opening it: the fire does not burn down meanwhile. The stove's
     * own tick still runs (no mixin): every tick of the station puts its numbers back, the bake a tick short of done at the most,
     * since the stove takes out the dish the tick its count reaches the total. Then what lies on the tray alike is laid the way the
     * stove takes it ({@link #layAlike}).
     */
    @Override
    public void whileDoor(StationContext station, boolean open) {
        hold(open);
        layAlike();
    }

    /** Holds the bake and the fire while the door is open; lets them go on once it is shut. */
    private void hold(boolean open) {
        if (!open) {
            HELD.remove(entity);
            return;
        }
        int total = numbers.get(COOK_TOTAL) > 0 ? numbers.get(COOK_TOTAL) : BAKE_TIME;
        int cook = numbers.get(COOK);
        int[] held = HELD.computeIfAbsent(entity, it -> new int[] {Math.min(cook, total - 2), -1});
        // Its count went back: the stove started anew on other things laid on the tray. Held from there.
        if (cook < held[0]) {
            held[0] = cook;
        }
        numbers.set(COOK, held[0]);
        // A fire lit while the door stood open is held from then on; one about to go out is held a tick before, so that the
        // stove never lights more fuel behind an open door.
        int burn = numbers.get(BURN);
        if (held[1] < 0 && burn > 0) {
            held[1] = Math.max(burn, 2);
        }
        if (held[1] > 0) {
            numbers.set(BURN, held[1]);
        }
    }

    /**
     * Farm &amp; Charm 1.1.x bakes a dish only with as many places taken as the dish has ingredients, and takes each ingredient from
     * the smallest pile that fits: of two alike one place runs empty first, and the stove would stop with the other still full. So
     * while nothing on the tray bakes and a place stands empty, one of a pile is moved onto it where that makes the tray a dish. It
     * then bakes to the last set, also with nobody there ({@code StoveMixin}). Only counts move, between places of one kind.
     */
    private void layAlike() {
        Level level = entity.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        ItemStack[] tray = new ItemStack[PLACES];
        boolean empty = false;
        boolean pile = false;
        for (int i = 0; i < PLACES; i++) {
            tray[i] = place(i);
            empty |= tray[i].isEmpty();
            pile |= tray[i].getCount() > 1;
        }
        if (!empty || !pile) {
            return;
        }
        List<Baking> recipes = recipesOf(level);
        if (bakesAny(recipes, tray)) {
            return;
        }
        for (int to = 0; to < PLACES; to++) {
            if (!tray[to].isEmpty()) {
                continue;
            }
            for (int from = 0; from < PLACES; from++) {
                if (tray[from].getCount() < 2) {
                    continue;
                }
                ItemStack[] tried = tray.clone();
                tried[from] = tray[from].copyWithCount(tray[from].getCount() - 1);
                tried[to] = tray[from].copyWithCount(1);
                if (bakesAny(recipes, tried)) {
                    container.setItem(FIRST_PLACE + from, tried[from]);
                    container.setItem(FIRST_PLACE + to, tried[to]);
                    container.setChanged();
                    return;
                }
            }
        }
    }

    /** {@link #layAlike} for the stove of {@code entity}, before its own tick; nothing for any other block entity. Server only. */
    public static void layAlike(BlockEntity entity) {
        FarmCharmStove stove = of(entity);
        if (stove != null) {
            stove.layAlike();
        }
    }

    private static boolean bakesAny(List<Baking> recipes, ItemStack[] tray) {
        for (Baking recipe : recipes) {
            if (bakes(recipe.needed(), tray)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the stove bakes a dish of {@code needed} from {@code tray}, as Farm &amp; Charm 1.1.x reads it: one place taken for each
     * ingredient, and each ingredient (those that fit fewer places first) found on the smallest pile left that fits it.
     */
    static boolean bakes(List<Ingredient> needed, ItemStack[] tray) {
        int taken = 0;
        int[] left = new int[tray.length];
        for (int i = 0; i < tray.length; i++) {
            left[i] = tray[i].getCount();
            taken += tray[i].isEmpty() ? 0 : 1;
        }
        if (taken != needed.size()) {
            return false;
        }
        List<Ingredient> order = new ArrayList<>(needed);
        order.sort(java.util.Comparator.comparingInt(ingredient -> fits(ingredient, tray, left)));
        for (Ingredient ingredient : order) {
            int best = -1;
            for (int i = 0; i < tray.length; i++) {
                if (left[i] > 0 && ingredient.test(tray[i]) && (best < 0 || left[i] < left[best])) {
                    best = i;
                }
            }
            if (best < 0) {
                return false;
            }
            left[best]--;
        }
        return true;
    }

    private static int fits(Ingredient ingredient, ItemStack[] tray, int[] left) {
        int count = 0;
        for (int i = 0; i < tray.length; i++) {
            count += left[i] > 0 && ingredient.test(tray[i]) ? 1 : 0;
        }
        return count;
    }
}
