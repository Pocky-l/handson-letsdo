package com.pockyl.handson_letsdo.station;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What stirring a pot well is worth: how many stars a cook can fill for a dish, by how much of it they have made, what a batch
 * done at so many stars adds, and how much longer a big batch takes.
 */
public final class PotStars {
    /**
     * How many times as fast as its usual time a serving cooks while it is stirred; a batch takes the square root of its servings
     * times as long, up to this many: sixteen take four times as long as one, a whole stack no longer.
     */
    private static final float PACE = 1.5F;
    private static final int SLOWEST_BATCH = 16;
    /** Servings of a dish a player has made, by any means, from which the second and the third star open for it. */
    private static final int SECOND_STAR = 8;
    private static final int THIRD_STAR = 32;

    private PotStars() {
    }

    /** How many times as fast as one serving a batch cooks: sixteen servings take four times as long as one, so a full pot is still work. */
    public static float speed(int batch) {
        return PACE / (float) Math.sqrt(Mth.clamp(batch, 1, SLOWEST_BATCH));
    }

    /** How many stars a player can fill for a dish: one the first time, more the more of it they have made. */
    public static int open(Player player, ItemStack dish) {
        if (!(player instanceof ServerPlayer server) || dish.isEmpty()) {
            return 1;
        }
        return open(server.getStats().getValue(Stats.ITEM_CRAFTED.get(dish.getItem())));
    }

    /** How many stars {@code made} servings of a dish open. */
    public static int open(int made) {
        return made >= THIRD_STAR ? 3 : made >= SECOND_STAR ? 2 : 1;
    }

    /** How far the next star is, 0 to 1, after {@code made} servings; 1 once all are open. */
    public static float next(int made) {
        return made >= THIRD_STAR ? 1.0F : made >= SECOND_STAR
                ? (float) (made - SECOND_STAR) / (THIRD_STAR - SECOND_STAR) : (float) made / SECOND_STAR;
    }

    /** The share of a batch the most stars a cook has opened can add, in whole percent. */
    public static int bonusPercent(int stars) {
        return stars >= 3 ? 50 : stars == 2 ? 25 : 0;
    }

    /** The share of a serving that each well stirred one brings: two stars one more for every four, three stars one more for every two. */
    public static float bonusRate(int stars) {
        return bonusPercent(stars) / 100.0F;
    }

    /**
     * The whole servings that well stirred {@code cooked} ones bring, with {@code carried}, the part of a serving earned by the
     * batches before. Rounded down and carried on: a single serving at three stars brings half of one, and the next one makes it
     * whole, so no size of batch earns more than another.
     */
    public static int bonus(int cooked, int stars, float carried) {
        return Mth.floor(cooked * bonusRate(stars) + carried + 1.0e-4F);
    }
}
