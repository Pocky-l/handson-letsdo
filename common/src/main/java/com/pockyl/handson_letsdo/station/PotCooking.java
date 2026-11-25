package com.pockyl.handson_letsdo.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;

import com.pockyl.handson_letsdo.HandsOnConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * How a pot of Farm &amp; Charm cooks while a cook works at its station, as at Hands-On Delight's pot: food a cook put in waits
 * for the spoon, everything in the pot cooks as one batch while it is stirred, a big batch taking longer by the square root, and
 * how well it was stirred adds servings. The soup then waits in the pot and is filled into its bowls afterwards, as soup is.
 *
 * <p>Farm &amp; Charm's pot is left as it is. Its own tick is only held back ({@link #holds}) while a cook's batch waits for the
 * spoon or a finished soup waits for its bowls, when it would make nothing or the wrong thing; every serving is still made by
 * that tick ({@link #craft}), so whatever hooks into the pot's cooking sees it. Food that came in by itself (a hopper, the pot's
 * own menu on a client without this mod) cooks Farm &amp; Charm's way, a serving at a time in its own time, and so does a cook's
 * food once they walked away.
 */
public final class PotCooking {
    private static final String TAG = "HandsOnPot";
    /** Ticks after the cook left the station from which their food cooks without them, as the pot cooks it. */
    private static final int AWAY = 40;
    /** A cook attended within this many ticks is at the station now: it ticks every tick, with one tick to spare. */
    private static final int HERE = 2;
    /** The most servings of soup that wait in a pot. */
    public static final int MOST_SERVINGS = 64;

    private final BlockEntity entity;
    private final StirSkill skill = new StirSkill();

    /** The food in the pot was put in by a cook at the station: it waits for the spoon. Saved with the pot. */
    private boolean byHand;
    /** How long the batch has cooked under the spoon, in ticks of one serving's time. Saved with the pot. */
    private float work;
    /** The part of a bonus serving the batches before earned, carried on to the next. Saved with the pot. */
    private float bonusCarried;
    /** The recipe of the soup that waits in the pot, how many servings of it, and how many of those the stars added. Saved. */
    private String soup = "";
    private int servings;
    private int spare;

    private long stirredUntil;
    /** The stars the last batch was done with, and the servings they added. */
    private int grade;
    private int bonus;
    /** Who cooks at the station: a recipe that wants learning cooks only for them. */
    private UUID cook;
    /** The last tick a cook was at the station, and how many ingredients the pot held at its last tick (-1: not looked yet). */
    private long attendedAt = Long.MIN_VALUE / 2;
    private int fed = -1;
    /** A cook put food in at the station since the pot's last tick. */
    private boolean handFed;
    /** On while a serving is made through the pot's own tick. */
    private boolean crafting;
    /** The game time the pot's tick was last decided for, and what was decided: on some loaders the tick asks twice. */
    private long decidedAt = Long.MIN_VALUE;
    private boolean held;
    /** How fast the batch cooked on its last tick, to keep the time cooked when the batch changes; 0 before the first. */
    private float pace;
    /** What the pot's own count stood at on its last tick of its own. */
    private int ownTime;
    /** What the ingredients left (their bottles) when servings were made with a cook at the station, for the station to hand over. */
    private final List<ItemStack> leftovers = new ArrayList<>();
    /** The food the dish was last looked up for, and the dish. */
    private final ItemStack[] madeFor = new ItemStack[Pot.INGREDIENTS];
    private RecipeManager madeWith;
    private Dish made;

    /** One serving made, and what its ingredients left behind. */
    public record Served(ItemStack dish, List<ItemStack> left) {
    }

    public PotCooking(BlockEntity entity) {
        this.entity = entity;
    }

    /** The pot counts as stirred until the game time {@code until}. */
    public void stir(long until) {
        stirredUntil = until;
    }

    public boolean isStirred(long gameTime) {
        return gameTime < stirredUntil;
    }

    public StirSkill skill() {
        return skill;
    }

    /** The stars the last batch was done with. */
    public int grade() {
        return grade;
    }

    /** The servings the stars of the last batch added. */
    public int bonus() {
        return bonus;
    }

    /** A cook works at the pot's station in this tick. */
    public void attend(long gameTime, Player player) {
        attendedAt = gameTime;
        if (cook == null) {
            cook = player.getUUID();
        }
    }

    /** {@code player} holds the spoon: the pot cooks for them. */
    public void cookedBy(Player player) {
        cook = player.getUUID();
    }

    /** A cook put food into the pot at the station: what comes in before the pot's next tick is put in by hand. */
    public void feedByHand() {
        handFed = true;
    }

    /** How far the batch under the spoon is, 0 to 1. */
    public float progress(Pot pot) {
        return Mth.clamp(work / pot.kind().handTime(), 0.0F, 1.0F);
    }

    /** Ticks of stirring until the batch of {@code batch} servings is done. */
    public int ticksLeft(Pot pot, int batch) {
        return Mth.ceil(Math.max(0.0F, pot.kind().handTime() - work) / PotStars.speed(batch));
    }

    /** How many servings of soup wait in the pot. */
    public int servings() {
        return servings;
    }

    /** How many sets of ingredients in the pot are the soup that waits: they are no longer food to take out. */
    public int reserved() {
        return Math.max(0, servings - spare);
    }

    /** The dish of the soup that waits in the pot; null for none. */
    public Dish soup(Level level, Pot pot) {
        if (servings <= 0) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(soup);
        for (Dish dish : pot.kind().dishes(level)) {
            if (dish.id().equals(id)) {
                return dish;
            }
        }
        return null;
    }

    private Level level() {
        return entity.getLevel();
    }

    private boolean attended() {
        return level() != null && level().getGameTime() - attendedAt <= AWAY;
    }

    private boolean here() {
        return level() != null && level().getGameTime() - attendedAt <= HERE;
    }

    /**
     * What the clients near the pot see of the station's part in one number: whether its food waits for the spoon and the soup
     * that waits. Not how far the batch is: that changes every tick and is shown only to whoever is at the station. Never 0 but
     * while a serving is being made, when there is nothing to tell.
     */
    public int shown() {
        // Halfway through a serving the slots hold what that one tick of the pot's own needs: nothing to tell anybody.
        if (crafting) {
            return 0;
        }
        int shown = ((byHand ? 1 : 0) * 31 + servings) * 31 * 31 + spare * 31 + soup.hashCode();
        return shown == 0 ? 1 : shown;
    }

    public void save(CompoundTag tag) {
        if (!byHand && work <= 0.0F && bonusCarried <= 0.0F && servings <= 0) {
            return;
        }
        CompoundTag own = new CompoundTag();
        own.putBoolean("ByHand", byHand);
        own.putFloat("Work", work);
        own.putFloat("Bonus", bonusCarried);
        if (servings > 0) {
            own.putString("Soup", soup);
            own.putInt("Servings", servings);
            own.putInt("Spare", spare);
        }
        tag.put(TAG, own);
    }

    public void load(CompoundTag tag) {
        CompoundTag own = tag.getCompound(TAG);
        byHand = own.getBoolean("ByHand");
        work = Math.max(0.0F, own.getFloat("Work"));
        bonusCarried = Mth.clamp(own.getFloat("Bonus"), 0.0F, 0.999F);
        servings = Mth.clamp(own.getInt("Servings"), 0, MOST_SERVINGS * 2);
        soup = servings > 0 ? own.getString("Soup") : "";
        spare = Mth.clamp(own.getInt("Spare"), 0, servings);
        // What a loaded pot holds was there before: only what comes in from now on says who feeds it.
        fed = -1;
    }

    /**
     * Asked at the head of the pot's own tick on the server: true when that tick must not run now. It is held while a cook's batch
     * waits for the spoon or cooks under it (the pot's own tick would cook a serving at a time with bowls in it, and nothing
     * without), and while a finished soup waits for its bowls. Everything else is the pot's own.
     */
    public boolean holds() {
        Level level = level();
        if (crafting || level == null || level.isClientSide()) {
            return false;
        }
        long now = level.getGameTime();
        if (decidedAt == now) {
            return held;
        }
        decidedAt = now;
        Pot pot = Pot.of(entity);
        boolean before = held;
        held = pot != null && pot.kind().active() && decide(level, pot, now);
        if (pot != null && before && !held) {
            // The pot's own count starts afresh: it counted nothing while it was held.
            pot.setTime(0);
            ownTime = 0;
        }
        return held;
    }

    /**
     * Whether a cook's batch waits for the spoon now: nothing waits served, the cook feeds it by hand and is there, stirring is
     * required, and nobody stirs. The badge asks for the spoon then.
     */
    public boolean waitsForSpoon(Level level, long now) {
        return servings <= 0 && byHand && attended() && HandsOnConfig.stirringRequired() && !isInMenu(level) && !isStirred(now);
    }

    private boolean decide(Level level, Pot pot, long now) {
        watchFood(pot);
        if (servings > 0) {
            keepSoup(level, pot);
        }
        boolean heated = pot.isHeated();
        if (servings > 0) {
            pot.setHeated(heated);
            pot.showState(heated, false);
            if (heated) {
                fillVessels(level, pot);
            }
            return true;
        }
        boolean stirred = isStirred(now);
        boolean ownWay = !stirred && (!byHand || !attended() || !HandsOnConfig.stirringRequired() || isInMenu(level));
        Dish dish = ownWay ? null : making(level, pot);
        if (dish != null && dish.learned() && FarmCharmDishes.isLocked(cook == null ? null : level.getPlayerByUUID(cook), pot.kind().recipes())) {
            dish = null;
        }
        int batch = dish == null ? 0 : batch(pot, dish);
        if (batch <= 0) {
            if (!ownWay) {
                work = 0.0F;
                pace = 0.0F;
            }
            followOwnCooking(pot);
            return false;
        }
        float speed = PotStars.speed(batch);
        keepTimeAt(speed);
        if (stirred && heated) {
            work += speed;
            if (work >= pot.kind().handTime()) {
                finish(level, pot, dish, batch);
            }
        }
        pot.setHeated(heated);
        pot.setTime(servings > 0 ? 0 : Math.round(progress(pot) * (Pot.UPSTREAM_TIME - 1)));
        pot.showState(heated, stirred && heated && servings <= 0);
        return true;
    }

    /**
     * While the pot cooks its own way, the batch is as far as the pot says: a cook who comes back takes it on from there. A count
     * that fell back to nothing made a serving or lost its food, and the batch starts anew.
     */
    private void followOwnCooking(Pot pot) {
        int time = pot.time();
        if (time > 0) {
            work = Math.max(work, time * (float) pot.kind().handTime() / Pot.UPSTREAM_TIME);
        } else if (ownTime > 0) {
            work = 0.0F;
        }
        ownTime = time;
    }

    /** Sees whether food came in since the pot's last tick, and whether by hand or by itself (a hopper, a pipe, a menu). */
    private void watchFood(Pot pot) {
        int count = 0;
        for (int slot = 0; slot < Pot.INGREDIENTS; slot++) {
            count += pot.ingredient(slot).getCount();
        }
        if (count > fed && (fed >= 0 || handFed)) {
            // Food came in: from a cook at the station, or by itself, also while a cook stands there. Before the pot's first
            // look only a cook's hand tells: what lay in it then keeps what was saved of it.
            byHand = handFed;
        }
        handFed = false;
        fed = count;
        if (count == 0) {
            work = 0.0F;
        }
        leaveForgotten(pot);
    }

    /** Whether a player works at this pot through its own menu, which has no spoon. */
    private boolean isInMenu(Level level) {
        for (Player player : level.players()) {
            if (player.containerMenu != player.inventoryMenu
                    && player.containerMenu.slots.stream().anyMatch(slot -> slot.container == entity)) {
                return true;
            }
        }
        return false;
    }

    /**
     * More food thrown in takes longer, less takes shorter: the time already cooked stays what it was. Without this a serving's
     * worth of time cooked alone counted as the same share of a batch.
     */
    private void keepTimeAt(float now) {
        if (pace > 0.0F && now != pace) {
            work = work * now / pace;
        }
        pace = now;
    }

    /** What the food in the pot makes, as Farm &amp; Charm's own lookup reads it; null for nothing. */
    public Dish making(Level level, Pot pot) {
        boolean same = madeWith == level.getRecipeManager();
        for (int slot = 0; slot < Pot.INGREDIENTS && same; slot++) {
            same = madeFor[slot] != null && ItemStack.isSameItemSameComponents(madeFor[slot], pot.ingredient(slot));
        }
        if (same) {
            return made;
        }
        madeWith = level.getRecipeManager();
        for (int slot = 0; slot < Pot.INGREDIENTS; slot++) {
            madeFor[slot] = pot.ingredient(slot).copyWithCount(1);
        }
        made = Pot.making(level, pot.kind(), pot.container());
        return made;
    }

    /** How many sets of ingredients lie in the pot: every slot gives one item to a serving. */
    private static int sets(Pot pot) {
        int sets = Integer.MAX_VALUE;
        for (int slot = 0; slot < Pot.INGREDIENTS; slot++) {
            ItemStack stack = pot.ingredient(slot);
            if (!stack.isEmpty()) {
                sets = Math.min(sets, stack.getCount());
            }
        }
        return sets == Integer.MAX_VALUE ? 0 : sets;
    }

    /**
     * How many servings of {@code dish} cook together: all that the ingredients make. A dish that needs no vessel comes out into
     * the pot's own dish slot at once, so only as many as that has room for.
     */
    public static int batch(Pot pot, Dish dish) {
        int batch = Math.min(sets(pot), MOST_SERVINGS);
        if (dish.vessel().isEmpty()) {
            batch = Math.min(batch, room(pot, dish));
        }
        return Math.max(0, batch);
    }

    /** How many servings of {@code dish} the pot's dish slot still takes. */
    private static int room(Pot pot, Dish dish) {
        ItemStack out = pot.output();
        ItemStack result = dish.result();
        if (!out.isEmpty() && !ItemStack.isSameItem(out, result)) {
            return 0;
        }
        return (result.getMaxStackSize() - out.getCount()) / Math.max(1, result.getCount());
    }

    /** The batch under the spoon is done: its soup waits in the pot for its bowls, or a dish that needs none is made at once. */
    private void finish(Level level, Pot pot, Dish dish, int batch) {
        grade = skill.grade(level.getGameTime());
        int extra = PotStars.bonus(batch, grade, bonusCarried);
        work = 0.0F;
        pace = 0.0F;
        int free;
        if (dish.vessel().isEmpty()) {
            // The most servings the dish slot takes, out of as few ingredients as that needs: what is saved stays in the pot.
            int total = Math.min(room(pot, dish), batch + extra);
            free = Math.max(0, Math.min(extra, total - 1));
            int cooked = 0;
            for (int serving = 0; serving < total; serving++) {
                boolean spared = serving < free;
                Served served = craft(level, pot, dish, spared, ItemStack.EMPTY);
                if (served == null || !intoOutput(pot, served.dish())) {
                    break;
                }
                cooked += spared ? 0 : 1;
                keepOrLeave(level, pot, served.left());
            }
            batch = cooked;
        } else {
            free = extra;
            soup = dish.id().toString();
            servings = batch + extra;
            spare = extra;
        }
        bonus = free;
        bonusCarried = Mth.clamp(bonusCarried + batch * PotStars.bonusRate(grade) - free, 0.0F, 0.999F);
        entity.setChanged();
    }

    /** The soup that waits is only as much as its food still in the pot: taken out through the pot's own menu, it is no soup. */
    private void keepSoup(Level level, Pot pot) {
        Dish dish = soup(level, pot);
        Dish lying = making(level, pot);
        int paid = Math.min(servings - spare, sets(pot));
        if (dish == null || lying == null || !lying.id().equals(dish.id()) || paid <= 0) {
            servings = 0;
            spare = 0;
            soup = "";
            entity.setChanged();
        } else if (paid + spare != servings) {
            servings = paid + spare;
            entity.setChanged();
        }
    }

    /**
     * For hoppers and the pot's own menu: a vessel laid into the pot's vessel slot is filled with the soup that waits, into its
     * dish slot, one a tick.
     */
    private void fillVessels(Level level, Pot pot) {
        Dish dish = soup(level, pot);
        if (dish == null || !dish.servedIn(pot.vessels()) || room(pot, dish) <= 0) {
            return;
        }
        boolean spared = spare > 0;
        Served served = craft(level, pot, dish, spared, ItemStack.EMPTY);
        if (served == null) {
            return;
        }
        if (!intoOutput(pot, served.dish())) {
            Block.popResource(level, entity.getBlockPos(), served.dish());
        }
        keepOrLeave(level, pot, served.left());
        took(spared);
    }

    private void took(boolean spared) {
        servings--;
        if (spared) {
            spare--;
        }
        if (servings <= 0) {
            servings = 0;
            spare = 0;
            soup = "";
        }
        entity.setChanged();
    }

    /** Adds {@code dish} to what lies in the pot's dish slot; false when it does not go there. */
    private static boolean intoOutput(Pot pot, ItemStack dish) {
        ItemStack out = pot.output();
        if (out.isEmpty()) {
            pot.container().setItem(Pot.OUTPUT_SLOT, dish);
            return true;
        }
        if (!ItemStack.isSameItemSameComponents(out, dish) || out.getCount() + dish.getCount() > out.getMaxStackSize()) {
            return false;
        }
        pot.container().setItem(Pot.OUTPUT_SLOT, out.copyWithCount(out.getCount() + dish.getCount()));
        return true;
    }

    /**
     * The soup that waits is filled into {@code vessel}: one serving, with what its ingredients left behind. Null when none could be
     * made: no soup waits, the dish is served in something else, or the pot stands on nothing hot (Farm &amp; Charm cooks only
     * over heat).
     */
    public Served serve(ItemStack vessel) {
        Level level = level();
        Pot pot = level == null ? null : Pot.of(entity);
        if (pot == null || servings <= 0) {
            return null;
        }
        keepSoup(level, pot);
        Dish dish = soup(level, pot);
        if (dish == null || !dish.servedIn(vessel) || !pot.isHeated()) {
            return null;
        }
        boolean spared = spare > 0;
        Served served = craft(level, pot, dish, spared, vessel);
        if (served != null) {
            took(spared);
        }
        return served;
    }

    /**
     * One serving of {@code dish} through Farm &amp; Charm's own cooking: the pot's own tick, a tick before the dish is done. For
     * that one tick the pot holds one of each ingredient, so that the serving takes one from every slot and what an ingredient
     * leaves (a bottle) lies where it lay; {@code vessel}, when given, lies in its vessel slot; its dish slot is empty. Afterwards
     * everything lies as before, less the serving's food; with {@code spared} less nothing: a serving the stars added uses none.
     * Null when the pot made nothing (no heat, a recipe not learned).
     */
    private Served craft(Level level, Pot pot, Dish dish, boolean spared, ItemStack vessel) {
        Container container = pot.container();
        ItemStack[] before = new ItemStack[Pot.INGREDIENTS];
        for (int slot = 0; slot < Pot.INGREDIENTS; slot++) {
            before[slot] = container.getItem(slot).copy();
            if (!before[slot].isEmpty()) {
                container.setItem(slot, before[slot].copyWithCount(1));
            }
        }
        ItemStack output = container.getItem(Pot.OUTPUT_SLOT).copy();
        ItemStack vessels = container.getItem(Pot.VESSEL_SLOT).copy();
        container.setItem(Pot.OUTPUT_SLOT, ItemStack.EMPTY);
        if (!vessel.isEmpty()) {
            container.setItem(Pot.VESSEL_SLOT, vessel.copyWithCount(1));
        }
        pot.setTime(Pot.UPSTREAM_TIME - 1);
        crafting = true;
        try {
            tickOwn(level);
        } finally {
            crafting = false;
        }
        ItemStack dishMade = container.getItem(Pot.OUTPUT_SLOT).copy();
        boolean done = !dishMade.isEmpty();
        List<ItemStack> left = new ArrayList<>();
        for (int slot = 0; slot < Pot.INGREDIENTS; slot++) {
            ItemStack after = container.getItem(slot);
            ItemStack was = before[slot];
            boolean used = done && !spared && !was.isEmpty() && (after.isEmpty() || !ItemStack.isSameItemSameComponents(after, was));
            if (done && !spared && !after.isEmpty() && (was.isEmpty() || !ItemStack.isSameItemSameComponents(after, was))) {
                left.add(after.copy());
            }
            container.setItem(slot, !used ? was : was.getCount() > 1 ? was.copyWithCount(was.getCount() - 1) : ItemStack.EMPTY);
        }
        container.setItem(Pot.OUTPUT_SLOT, output);
        if (!vessel.isEmpty() || !done) {
            container.setItem(Pot.VESSEL_SLOT, vessels);
        }
        pot.setTime(0);
        return done ? new Served(dishMade, left) : null;
    }

    /** The pot's own tick, through the game's own way to tick a block entity. */
    @SuppressWarnings("unchecked")
    private void tickOwn(Level level) {
        if (entity instanceof BlockEntityTicker<?> ticker) {
            ((BlockEntityTicker<BlockEntity>) ticker).tick(level, entity.getBlockPos(), entity.getBlockState(), entity);
        }
    }

    /**
     * What a serving's ingredients left: kept for the cook while one is at the station, else laid back into the pot as Farm &amp;
     * Charm lays it (a free slot, or out beside the pot).
     */
    private void keepOrLeave(Level level, Pot pot, List<ItemStack> left) {
        for (ItemStack stack : left) {
            if (here()) {
                leftovers.add(stack);
            } else {
                leave(level, pot, stack);
            }
        }
    }

    private void leave(Level level, Pot pot, ItemStack stack) {
        Container container = pot.container();
        for (int slot = 0; slot < Pot.INGREDIENTS; slot++) {
            ItemStack there = container.getItem(slot);
            if (there.isEmpty()) {
                container.setItem(slot, stack);
                return;
            }
            if (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() + stack.getCount() <= there.getMaxStackSize()) {
                container.setItem(slot, there.copyWithCount(there.getCount() + stack.getCount()));
                return;
            }
        }
        Block.popResource(level, entity.getBlockPos(), stack);
    }

    /** Leftovers nobody took (the cook left that very tick) are laid back as the pot would lay them. */
    private void leaveForgotten(Pot pot) {
        if (leftovers.isEmpty() || here()) {
            return;
        }
        List<ItemStack> forgotten = List.copyOf(leftovers);
        leftovers.clear();
        forgotten.forEach(stack -> leave(level(), pot, stack));
    }

    /** What the ingredients left (a water bottle's bottle) while a cook was at the station, taken once: the cook gets it. */
    public List<ItemStack> takeLeftovers() {
        List<ItemStack> taken = List.copyOf(leftovers);
        leftovers.clear();
        return taken;
    }

    /** Sets how far the batch under the spoon is, 0 to 1: only a test skips the stirring so. */
    public void setProgress(Pot pot, float progress) {
        work = progress * pot.kind().handTime();
    }

    /** Whether the food in the pot was put in by a cook at the station. */
    public boolean isByHand() {
        return byHand;
    }
}
