package com.pockyl.handson_letsdo.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The crafting bowl by hand. The cook lays the ingredients in and whisks them with the bowl's whisk; the server counts the way the
 * whisk's loops go through the bowl from the places the cook's hand reports, in any figure, and once the bowlful has had its
 * circles' worth ({@link BowlStation#circles} times {@link BowlStation#CIRCLE_WAY}) every set of it is made by the bowl's own tick at
 * once. The result waits in the bowl until the cook takes it from the badge; what an ingredient leaves (a bucket) goes to the cook,
 * and a liquid poured in is drawn back with an empty container of its kind. On a client near a bowl nobody works at, the same logic
 * runs on the client's copy and only tells the scene what the bowl holds.
 */
public final class BowlLogic implements StationLogic {
    /** The most sets one pick in the book lays in with Shift. */
    private static final int FILL_MOST = 16;

    private final Bowl bowl;
    private final SlotStorage ingredients;
    private final SlotStorage result;
    private final HandPath hand = new HandPath(BowlStation.WHISK_REACH);
    private List<Dish> recipes = List.of();

    public BowlLogic(StationContext station) {
        bowl = Bowl.orEmpty(station.blockEntity());
        StationState state = station.state();
        ingredients = SlotStorage.of(bowl.container(), 0, Bowl.INGREDIENTS);
        result = SlotStorage.of(bowl.container(), Bowl.RESULT_SLOT, 1);
        state.bindSlots(BowlStation.INGREDIENTS, ingredients);
        state.bindSlots(BowlStation.RESULT, result);
        read(station.level());
        refresh(station);
    }

    private void read(Level level) {
        recipes = BowlRecipes.all(level);
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        read(station.level());
        hand.reset();
        refresh(station);
    }

    /** The clients are told where the stirring was left, so the bowl near them shows it as it is. */
    @Override
    public void onClose(StationContext station, Player player) {
        hand.reset();
        if (!station.level().isClientSide() && bowl.hand() != null) {
            bowl.hand().handson$tell();
        }
    }

    @Override
    public void tick(StationContext station) {
        if (!station.level().isClientSide() && bowl.isReal()) {
            bowl.resetWhenTaken();
        }
        refresh(station);
    }

    @Override
    public void onSlotChanged(StationContext station, SlotRef slot) {
        refresh(station);
    }

    /** Only what some recipe of the bowl uses goes in; nothing is laid where the result waits. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return slot.group().equals(BowlStation.INGREDIENTS) && BowlRecipes.uses(recipes, stack);
    }

    @Override
    public void onTaken(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (slot.equals(BowlStation.RESULT_SLOT)) {
            stack.onCraftedBy(station.level(), player, stack.getCount());
            if (!station.level().isClientSide()) {
                bowl.resetWhenTaken();
            }
        }
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (!bowl.isReal() || station.level().isClientSide()) {
            return;
        }
        if (BowlStation.ACTION_STIR.equals(action)) {
            float x = data.getFloat(BowlStation.X);
            float z = data.getFloat(BowlStation.Z);
            if (!Float.isFinite(x) || !Float.isFinite(z)) {
                return;
            }
            CompoundTag own = station.state().data();
            own.putFloat(BowlStation.WHISK_X, Mth.clamp(x, -BowlStation.WHISK_REACH, BowlStation.WHISK_REACH));
            own.putFloat(BowlStation.WHISK_Z, Mth.clamp(z, -BowlStation.WHISK_REACH, BowlStation.WHISK_REACH));
            station.state().setData(own);
            // The hand's way counts alike however thick the bowlful has become: the batter holds the whisk back only in how it looks
            // and feels on the client, never in how long the bowlful takes.
            float way = hand.report(station.level().getGameTime(), x, z, BowlStation.COUNTED_PACE);
            if (way > 0.0F) {
                stir(station, player, way);
            }
        } else if (BowlStation.ACTION_TAKE.equals(action)) {
            take(station, player);
        } else if (BowlStation.ACTION_DRAW.equals(action)) {
            draw(station, player, data.contains(BowlStation.FROM) ? data.getInt(BowlStation.FROM) : -1, data.getBoolean(BowlStation.ALL));
        } else if (BowlStation.ACTION_FILL.equals(action)) {
            ResourceLocation id = ResourceLocation.tryParse(data.getString(BowlStation.RECIPE));
            recipes.stream().filter(recipe -> recipe.id().equals(id)).findFirst()
                    .ifPresent(recipe -> fill(player, recipe, data.getBoolean(BowlStation.ALL) ? FILL_MOST : 1));
            player.getInventory().setChanged();
            refresh(station);
        }
    }

    /** The recipe what lies in the bowl makes now; null for none, and while a result waits in the bowl (it blocks the bowl's own). */
    private Dish making() {
        if (!result.get(0).isEmpty()) {
            return null;
        }
        return BowlRecipes.reads(recipes, bowl.ingredients());
    }

    /** How many sets the bowlful makes: the smallest stack of its ingredients, no more than one result stack holds. */
    private int sets(Dish recipe) {
        int sets = Integer.MAX_VALUE;
        for (ItemStack stack : bowl.ingredients()) {
            if (!stack.isEmpty()) {
                sets = Math.min(sets, stack.getCount());
            }
        }
        int perSet = Math.max(1, recipe.result().getCount());
        return Math.max(1, Math.min(sets, recipe.result().getMaxStackSize() / perSet));
    }

    /**
     * The whisk went {@code way} pixels through what lies in the bowl, in whatever figure: once the bowlful's circles' worth is done it
     * is mixed. Stirring an empty bowl, or one whose result waits, counts nothing.
     */
    public void stir(StationContext station, Player player, float way) {
        BowlHand kept = bowl.hand();
        if (kept == null) {
            return;
        }
        Dish recipe = making();
        if (recipe == null) {
            kept.handson$setStirred(0.0F);
            refresh(station);
            return;
        }
        int sets = sets(recipe);
        float needed = BowlStation.circles(sets) * BowlStation.CIRCLE_WAY;
        float stirred = Math.abs(kept.handson$stirred()) + Math.abs(way);
        if (stirred >= needed) {
            stirred = 0.0F;
            mix(station, player, recipe, sets);
        }
        kept.handson$setStirred(stirred);
        refresh(station);
    }

    /**
     * The bowlful: {@code sets} sets, each made by the bowl's own tick, their results together in the result slot. What an
     * ingredient leaves, which the bowl throws out beside it, goes to the cook.
     */
    private void mix(StationContext station, Player player, Dish recipe, int sets) {
        Container container = bowl.container();
        Level level = station.level();
        AABB around = new AABB(station.pos());
        Set<ItemEntity> before = new HashSet<>(level.getEntitiesOfClass(ItemEntity.class, around));
        ItemStack made = ItemStack.EMPTY;
        for (int i = 0; i < sets; i++) {
            bowl.stirOnce();
            ItemStack out = container.getItem(Bowl.RESULT_SLOT);
            if (out.isEmpty()) {
                break;
            }
            container.setItem(Bowl.RESULT_SLOT, ItemStack.EMPTY);
            if (made.isEmpty()) {
                made = out.copy();
            } else if (ItemStack.isSameItemSameTags(made, out) && made.getCount() + out.getCount() <= made.getMaxStackSize()) {
                made.grow(out.getCount());
            } else {
                player.getInventory().placeItemBackInInventory(out.copy());
            }
        }
        container.setItem(Bowl.RESULT_SLOT, made);
        for (ItemEntity left : level.getEntitiesOfClass(ItemEntity.class, around)) {
            if (!before.contains(left) && left.isAlive()) {
                player.getInventory().placeItemBackInInventory(left.getItem().copy());
                left.discard();
            }
        }
        bowl.hand().handson$tell();
        if (!made.isEmpty()) {
            CompoundTag data = new CompoundTag();
            data.putString(BowlStation.ITEM, BuiltInRegistries.ITEM.getKey(made.getItem()).toString());
            station.effect(BowlStation.EFFECT_MIXED, BowlStation.RESULT, data);
        }
    }

    /** The result that waits goes to the cook, as Farm &amp; Charm's own empty hand takes it, and the bowl's count goes back. */
    private void take(StationContext station, Player player) {
        ItemStack waiting = result.get(0);
        if (waiting.isEmpty()) {
            return;
        }
        ItemStack taken = waiting.copy();
        result.set(0, ItemStack.EMPTY);
        onTaken(station, player, BowlStation.RESULT_SLOT, taken);
        CompoundTag data = new CompoundTag();
        data.putString(BowlStation.ITEM, BuiltInRegistries.ITEM.getKey(taken.getItem()).toString());
        if (!player.getInventory().add(taken) && !taken.isEmpty()) {
            player.drop(taken, false);
        }
        player.getInventory().setChanged();
        if (bowl.hand() != null) {
            bowl.hand().handson$tell();
        }
        station.effect(BowlStation.EFFECT_TAKEN, BowlStation.RESULT, data);
        refresh(station);
    }

    /**
     * An empty container over the bowl draws back what was poured in such a container, the last poured first: one, or with {@code all}
     * as many as there are containers and portions. It comes from the cursor, or from inventory slot {@code from}; what is drawn goes
     * where the container was, or to the inventory.
     */
    private void draw(StationContext station, Player player, int from, boolean all) {
        Inventory inventory = player.getInventory();
        ItemStack held = from >= 0 ? inventory.getItem(from) : player.inventoryMenu.getCarried();
        if (held.isEmpty() || !result.get(0).isEmpty()) {
            return;
        }
        for (int i = Bowl.INGREDIENTS - 1; i >= 0; i--) {
            ItemStack in = ingredients.get(i);
            ItemStack container = in.isEmpty() ? ItemStack.EMPTY : BowlStation.containerOf(in);
            if (container.isEmpty() || !ItemStack.isSameItemSameTags(container, held)) {
                continue;
            }
            int count = Math.min(all ? in.getCount() : 1, held.getCount());
            ItemStack drawn = in.copyWithCount(count);
            ingredients.set(i, in.getCount() > count ? in.copyWithCount(in.getCount() - count) : ItemStack.EMPTY);
            held.shrink(count);
            BarrelLogic.give(player, drawn, held.isEmpty(), from);
            inventory.setChanged();
            CompoundTag data = new CompoundTag();
            data.putString(BowlStation.ITEM, BuiltInRegistries.ITEM.getKey(drawn.getItem()).toString());
            station.effect(BowlStation.EFFECT_DRAWN, BowlStation.MIXTURE, data);
            if (bowl.hand() != null) {
                bowl.hand().handson$tell();
            }
            refresh(station);
            return;
        }
    }

    /**
     * A pick in the book: the recipe's ingredients go in from the inventory, one set or up to {@code sets}, alike to what lies there;
     * a bowl that holds what the recipe does not use takes nothing.
     */
    private void fill(Player player, Dish recipe, int sets) {
        StationRecipes.Filled filled = StationRecipes.fill(player.getInventory(), recipe.ingredients(), StationRecipes.kinds(ingredients),
                Bowl.INGREDIENTS, sets);
        if (filled != null) {
            StationRecipes.lay(ingredients, filled.kinds(), filled.parts());
        }
    }

    private void refresh(StationContext station) {
        StationState state = station.state();
        CompoundTag own = state.data();
        Dish recipe = bowl.isReal() ? making() : null;
        BowlHand kept = bowl.hand();
        int sets = recipe == null ? 0 : sets(recipe);
        int circles = recipe == null ? 0 : BowlStation.circles(sets);
        float stirred = kept == null || recipe == null ? 0.0F
                : Math.min(1.0F, Math.abs(kept.handson$stirred()) / (circles * BowlStation.CIRCLE_WAY));
        own.putFloat(BowlStation.STIRRED, stirred);
        own.putInt(BowlStation.CIRCLES, circles);
        own.putInt(BowlStation.SETS, sets);
        if (recipe == null) {
            own.remove(BowlStation.MAKING);
        } else {
            own.putString(BowlStation.MAKING, BuiltInRegistries.ITEM.getKey(recipe.result().getItem()).toString());
        }
        state.setData(own);
    }
}
