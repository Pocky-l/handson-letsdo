package com.pockyl.handson_letsdo.station;

import net.minecraft.util.Mth;

/**
 * How far a thing moved by the cook's hand through a bowl (the crafting bowl's whisk) went, from the places the hand reports: the
 * way between two reports, whichever way and in any figure, never outside the bowl, and never more than the mixture lets it go in
 * the time that passed. The way allowed comes back with every tick at the pace given, and a little of it may be saved up for a
 * hand that reports late; however often it reports and however it jerks or shakes, it goes no further than a steady hand at that
 * pace. A report long after the last one starts afresh: the hand let go meanwhile.
 */
public final class HandPath {
    /** Reports further apart than this, in ticks, start afresh. */
    private static final int GAP = 20;
    /** Ticks of the pace that may be saved up: reports come about once a tick, now and then two in one and none in the next. */
    public static final int SAVED_TICKS = 3;

    private final float reach;
    private float lastX = Float.NaN;
    private float lastZ;
    private long lastAt;
    private float allowed;

    /** The hand moves it no further than {@code reach} from the middle either way. */
    public HandPath(float reach) {
        this.reach = reach;
    }

    /**
     * The hand reports {@code x, z} (pixels from the middle) at game time {@code now}, the mixture letting it go {@code pace} pixels a
     * tick: how far it went since the last report, pixels.
     */
    public float report(long now, float x, float z, float pace) {
        if (!Float.isFinite(x) || !Float.isFinite(z)) {
            return 0.0F;
        }
        float atX = Mth.clamp(x, -reach, reach);
        float atZ = Mth.clamp(z, -reach, reach);
        float most = Math.max(0.0F, pace);
        if (Float.isNaN(lastX) || now - lastAt > GAP || now < lastAt) {
            lastX = atX;
            lastZ = atZ;
            lastAt = now;
            allowed = 0.0F;
            return 0.0F;
        }
        allowed = Math.min(most * SAVED_TICKS, allowed + most * (now - lastAt));
        float way = Math.min(allowed, (float) Math.hypot(atX - lastX, atZ - lastZ));
        allowed -= way;
        lastX = atX;
        lastZ = atZ;
        lastAt = now;
        return way;
    }

    /** The hand let go: the next report starts afresh. */
    public void reset() {
        lastX = Float.NaN;
    }
}
