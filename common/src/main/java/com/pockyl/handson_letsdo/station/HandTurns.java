package com.pockyl.handson_letsdo.station;

import net.minecraft.util.Mth;

/**
 * How far a thing turned round by the cook's hand (the mincer's crank, the bowl's whisk) went, from the angles the hand reports:
 * the short way between two reports, either way, at most as fast as a hand turns it. A report long after the last one starts
 * afresh: the hand let go meanwhile.
 */
public final class HandTurns {
    /** Reports further apart than this, in ticks, start afresh. */
    private static final int GAP = 20;

    private final float mostPerTick;
    private float last = Float.NaN;
    private long lastAt;

    /** A whole turn takes the hand at least {@code fastestTurnTicks} ticks. */
    public HandTurns(float fastestTurnTicks) {
        mostPerTick = Mth.TWO_PI / fastestTurnTicks;
    }

    /** The hand reports {@code angle} (radians) at game time {@code now}: how far it turned since the last report, radians either way. */
    public float report(long now, float angle) {
        return count(now, angle, mostPerTick);
    }

    /**
     * The same, for a thing that turns no faster than a whole turn in {@code fastestTurnTicks} ticks now: a crank under a heavy load.
     */
    public float report(long now, float angle, float fastestTurnTicks) {
        return count(now, angle, Mth.TWO_PI / Math.max(1.0F, fastestTurnTicks));
    }

    private float count(long now, float angle, float perTick) {
        if (!Float.isFinite(angle)) {
            return 0.0F;
        }
        if (Float.isNaN(last) || now - lastAt > GAP || now < lastAt) {
            last = angle;
            lastAt = now;
            return 0.0F;
        }
        float most = perTick * (now - lastAt + 1);
        float way = Mth.clamp(Mth.wrapDegrees((angle - last) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD, -most, most);
        last = angle;
        lastAt = now;
        return way;
    }

    /** The hand let go: the next report starts afresh. */
    public void reset() {
        last = Float.NaN;
    }
}
