package com.pockyl.handson_letsdo.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;

import java.util.List;

/**
 * The mincer by hand. The cook lays what is to be minced on the hopper and turns the crank; the server counts the crank's way round
 * from the angles the cook's hand reports, either way, no faster than the load lets it turn ({@link MincerStation#turnTicks}: metal is
 * heavier than meat, a full load than one item), and every {@link MincerStation#LOAD_TURN} of it minces a load: up to
 * {@link MincerStation#LOAD} of what lies there, each by the mincer's own tick. What comes out lies on the foot under the spout until
 * taken; what does not fit there goes to the cook. On a client near a mincer nobody works at, the same logic runs on the client's
 * copy and only tells the scene what the mincer holds.
 */
public final class MincerLogic implements StationLogic {
    /** The most a pick in the book lays on the hopper with Shift. */
    private static final int FILL_MOST = 64;

    private final Mincer mincer;
    private final SlotStorage input;
    private final SlotStorage minced;
    private final HandTurns hand = new HandTurns(MincerStation.FASTEST_TURN_TICKS);
    private List<Dish> recipes = List.of();

    public MincerLogic(StationContext station) {
        mincer = Mincer.orEmpty(station.blockEntity());
        StationState state = station.state();
        input = SlotStorage.of(mincer.container(), Mincer.INPUT_SLOT, 1);
        minced = SlotStorage.of(mincer.container(), Mincer.OUTPUT_SLOT, 1);
        state.bindSlots(MincerStation.INPUT, input);
        state.bindSlots(MincerStation.MINCED, minced);
        read(station.level());
        refresh(station);
    }

    private void read(Level level) {
        recipes = MincerRecipes.all(level);
    }

    /** The recipes are read again for every cook: a reload may have changed them. */
    @Override
    public void onOpen(StationContext station, Player player) {
        read(station.level());
        hand.reset();
        refresh(station);
    }

    /** The clients are told where the crank was left, so the mincer near them shows the load as it is. */
    @Override
    public void onClose(StationContext station, Player player) {
        hand.reset();
        if (!station.level().isClientSide() && mincer.hand() != null) {
            mincer.hand().handson$tell();
        }
    }

    @Override
    public void tick(StationContext station) {
        refresh(station);
    }

    @Override
    public void onSlotChanged(StationContext station, SlotRef slot) {
        refresh(station);
    }

    /** Only what the mincer minces goes onto its hopper, alike to what lies there; nothing is laid under the spout. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (!slot.group().equals(MincerStation.INPUT)) {
            return false;
        }
        ItemStack there = input.get(0);
        return MincerRecipes.taking(recipes, stack) != null && (there.isEmpty() || ItemStack.isSameItemSameTags(there, stack));
    }

    /** What the mincer's own output does for a player who takes it. */
    @Override
    public void onTaken(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (slot.equals(MincerStation.MINCED_SLOT)) {
            stack.onCraftedBy(station.level(), player, stack.getCount());
        }
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (!mincer.isReal() || station.level().isClientSide()) {
            return;
        }
        if (MincerStation.ACTION_CRANK.equals(action)) {
            crank(station, player, data.getFloat(MincerStation.ANGLE));
        } else if (MincerStation.ACTION_FILL.equals(action)) {
            ResourceLocation id = ResourceLocation.tryParse(data.getString(MincerStation.RECIPE));
            recipes.stream().filter(recipe -> recipe.id().equals(id)).findFirst().ifPresent(recipe ->
                    fill(player.getInventory(), recipe, data.getBoolean(MincerStation.ALL) ? FILL_MOST : MincerStation.LOAD));
            player.getInventory().setChanged();
            refresh(station);
        }
    }

    /**
     * The cook's hand turned the crank to {@code angle}: the way it went since the last report counts, the short way round, at most
     * as fast as a hand turns it.
     */
    private void crank(StationContext station, Player player, float angle) {
        if (!Float.isFinite(angle)) {
            return;
        }
        CompoundTag own = station.state().data();
        own.putFloat(MincerStation.CRANK, angle);
        station.state().setData(own);
        float way = hand.report(station.level().getGameTime(), angle, MincerStation.turnTicks(heaviness(station)));
        if (way != 0.0F) {
            turn(station, player, way);
        }
    }

    /** How heavy the crank turns now, with what lies on the hopper. */
    private float heaviness(StationContext station) {
        ItemStack lying = input.get(0);
        Dish recipe = MincerRecipes.taking(recipes, lying);
        return recipe == null ? 0.0F
                : MincerStation.heaviness(FarmCharmDishes.mincedKind(station.level(), recipe.id()), Math.min(MincerStation.LOAD, lying.getCount()));
    }

    /**
     * The crank went {@code way} round (radians, either way) with the load on the hopper: every {@link MincerStation#LOAD_TURN} of it
     * minces a load. Turning it with nothing to mince counts nothing.
     */
    public void turn(StationContext station, Player player, float way) {
        MincerHand kept = mincer.hand();
        if (kept == null) {
            return;
        }
        Dish recipe = MincerRecipes.taking(recipes, input.get(0));
        if (recipe == null) {
            kept.handson$setTurned(0.0F);
            refresh(station);
            return;
        }
        float turned = kept.handson$turned() + way;
        while (Math.abs(turned) >= MincerStation.LOAD_TURN && recipe != null) {
            turned -= Math.signum(turned) * MincerStation.LOAD_TURN;
            mince(station, player, recipe);
            recipe = MincerRecipes.taking(recipes, input.get(0));
        }
        kept.handson$setTurned(recipe == null ? 0.0F : turned);
        refresh(station);
    }

    /**
     * One load: up to {@link MincerStation#LOAD} of what lies on the hopper, each made by the mincer's own tick, together onto the foot
     * under the spout. What lay there of another kind, and what does not fit there, goes to the cook.
     */
    private void mince(StationContext station, Player player, Dish recipe) {
        Container container = mincer.container();
        int load = Math.min(MincerStation.LOAD, input.get(0).getCount());
        ItemStack lying = container.getItem(Mincer.OUTPUT_SLOT).copy();
        container.setItem(Mincer.OUTPUT_SLOT, ItemStack.EMPTY);
        ItemStack made = ItemStack.EMPTY;
        int count = 0;
        for (int i = 0; i < load; i++) {
            mincer.mince();
            ItemStack out = container.getItem(Mincer.OUTPUT_SLOT);
            if (out.isEmpty()) {
                break;
            }
            container.setItem(Mincer.OUTPUT_SLOT, ItemStack.EMPTY);
            if (made.isEmpty()) {
                made = out.copy();
            } else if (ItemStack.isSameItemSameTags(made, out)) {
                made.grow(out.getCount());
            } else {
                give(player, out);
            }
            count++;
        }
        if (!lying.isEmpty() && !made.isEmpty() && !ItemStack.isSameItemSameTags(lying, made)) {
            give(player, lying);
            lying = ItemStack.EMPTY;
        }
        ItemStack plate = lying.isEmpty() ? made : lying;
        if (!lying.isEmpty() && !made.isEmpty()) {
            int room = Math.max(0, lying.getMaxStackSize() - lying.getCount());
            int moved = Math.min(room, made.getCount());
            plate = lying.copyWithCount(lying.getCount() + moved);
            made.shrink(moved);
            give(player, made);
        } else if (plate.getCount() > plate.getMaxStackSize()) {
            give(player, plate.split(plate.getCount() - plate.getMaxStackSize()));
        }
        container.setItem(Mincer.OUTPUT_SLOT, plate);
        MincerHand kept = mincer.hand();
        kept.handson$setKept(!plate.isEmpty());
        kept.handson$tell();
        if (count > 0) {
            CompoundTag data = new CompoundTag();
            data.putString(MincerStation.ITEM, BuiltInRegistries.ITEM.getKey(recipe.result().getItem()).toString());
            data.putString(MincerStation.KIND, FarmCharmDishes.mincedKind(station.level(), recipe.id()));
            station.effect(MincerStation.EFFECT_MINCED, MincerStation.DIE, data);
        }
    }

    private static void give(Player player, ItemStack stack) {
        if (!stack.isEmpty()) {
            player.getInventory().placeItemBackInInventory(stack.copy());
            stack.setCount(0);
        }
    }

    /** A pick in the book: what the recipe minces goes from the inventory onto the hopper, alike to what lies there, up to {@code most}. */
    private void fill(Inventory inventory, Dish recipe, int most) {
        int moved = 0;
        for (int index = 0; index < Inventory.INVENTORY_SIZE && moved < most; index++) {
            ItemStack stack = inventory.getItem(index);
            ItemStack there = input.get(0);
            if (stack.isEmpty() || !recipe.ingredients().get(0).test(stack) || !there.isEmpty() && !ItemStack.isSameItemSameTags(there, stack)) {
                continue;
            }
            int count = Math.min(most - moved, Math.min(stack.getCount(), stack.getMaxStackSize() - there.getCount()));
            if (count <= 0) {
                continue;
            }
            input.set(0, stack.copyWithCount(there.getCount() + count));
            stack.shrink(count);
            moved += count;
        }
    }

    private void refresh(StationContext station) {
        StationState state = station.state();
        CompoundTag own = state.data();
        ItemStack lying = input.get(0);
        Dish recipe = mincer.isReal() ? MincerRecipes.taking(recipes, lying) : null;
        MincerHand kept = mincer.hand();
        float turned = kept == null || recipe == null ? 0.0F : Math.min(1.0F, Math.abs(kept.handson$turned()) / MincerStation.LOAD_TURN);
        own.putFloat(MincerStation.TURNED, turned);
        own.putInt(MincerStation.LOAD_SIZE, recipe == null ? 0 : Math.min(MincerStation.LOAD, lying.getCount()));
        if (recipe == null) {
            own.remove(MincerStation.MAKING);
            own.remove(MincerStation.GRINDS);
        } else {
            own.putString(MincerStation.MAKING, BuiltInRegistries.ITEM.getKey(recipe.result().getItem()).toString());
            own.putString(MincerStation.GRINDS, FarmCharmDishes.mincedKind(station.level(), recipe.id()));
        }
        state.setData(own);
    }
}
