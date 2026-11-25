package com.pockyl.handson_letsdo.client;

import net.minecraft.util.Mth;

import com.pockyl.handson_letsdo.station.MincerStation;

/**
 * A crank with weight: the hand holds its grip as a spring that can pull only so hard, the crank has its own inertia, the load in the
 * mincer resists it (more the heavier the load), and the blade catches on what it cuts four times a turn. So the grip lags the hand,
 * picks up speed slowly, stalls a moment at each catch and lurches on, runs down by itself when the hand stops, and never turns faster
 * than the load lets it. Angles in radians, never wrapped; the hand's own angle may wrap.
 */
final class CrankFeel {
    /** How heavy the crank and its blade are to set going (pulls below are divided by it). */
    private static final float INERTIA = 1.8F;
    /** The hand on the grip: how hard it pulls for each radian it is ahead, how much it holds back, how hard at most. */
    private static final float HAND_PULL = 140.0F;
    private static final float HAND_HOLD = 14.0F;
    private static final float HAND_MOST = 60.0F;
    /** The hand gets no further ahead of the grip than this, radians: beyond, it slips along with it. */
    private static final float SLIP = 1.2F;
    /** What the crank resists with: nothing to grind, and the heaviest load on top of that; how much more at a catch. */
    private static final float IDLE = 2.0F;
    private static final float HEAVY = 22.0F;
    private static final float CATCH = 0.5F;
    private static final int BLADES = 4;
    /** Where the blade catches, radians from the crank's rest: just past it, so that a crank at rest is not stuck on a catch. */
    private static final float CATCH_AT = Mth.PI / 8.0F;
    /** It drags the more the faster it goes, the more under a heavy load. */
    private static final float DRAG = 1.2F;
    private static final float DRAG_HEAVY = 2.0F;
    /** Slower than this it stands until the hand pulls harder than the load holds it. */
    private static final float STICK = 0.08F;
    /** It goes up to this share of the fastest the server counts for the load. */
    private static final float TOP_SHARE = 0.94F;
    private static final int STEPS = 4;

    private float angle;
    private float speed;
    private float hand = Float.NaN;
    private float lastWant = Float.NaN;
    private float strain;
    private float pull;

    float angle() {
        return angle;
    }

    float speed() {
        return speed;
    }

    /** How hard the hand works, 0 (not at all) to 1 (as hard as it can), eased. */
    float strain() {
        return strain;
    }

    /** Puts the crank at {@code to} at rest: another hand turns it, or it settled. */
    void set(float to) {
        angle = to;
        speed = 0.0F;
        hand = Float.NaN;
        lastWant = Float.NaN;
    }

    /** Lets go of the grip: the crank runs on by itself and runs down. */
    void letGo() {
        hand = Float.NaN;
        lastWant = Float.NaN;
    }

    /**
     * One frame: the hand wants the grip at {@code want} (radians round the axle, any wrap) or does not hold it ({@code NaN}); the load
     * is as heavy as {@code heaviness} ({@link MincerStation#heaviness}, 0 for nothing). Returns how many catches the blade went
     * through.
     */
    int step(float dt, float want, float heaviness) {
        if (!Float.isNaN(want)) {
            // The hand follows the pointer's own way round, never the short way to where it is: a crank left far behind is pulled on
            // forwards, not back.
            if (Float.isNaN(hand) || Float.isNaN(lastWant)) {
                hand = angle + Mth.wrapDegrees((want - angle) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
            } else {
                hand += Mth.wrapDegrees((want - lastWant) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
            }
            lastWant = want;
            hand = Mth.clamp(hand, angle - SLIP, angle + SLIP);
        } else {
            lastWant = Float.NaN;
        }
        float load = IDLE + HEAVY * heaviness;
        float drag = DRAG + DRAG_HEAVY * heaviness;
        float top = Mth.TWO_PI / (MincerStation.turnTicks(heaviness) / 20.0F) * TOP_SHARE;
        float h = dt / STEPS;
        int catches = 0;
        float pulled = 0.0F;
        for (int i = 0; i < STEPS; i++) {
            float handPull = Float.isNaN(hand) ? 0.0F : Mth.clamp(HAND_PULL * (hand - angle) - HAND_HOLD * speed, -HAND_MOST, HAND_MOST);
            pulled = Math.max(pulled, Math.abs(handPull));
            float bump = Math.max(0.0F, Mth.cos(BLADES * (angle - CATCH_AT)));
            float holds = load * (1.0F + (heaviness > 0.0F ? CATCH * (float) Math.pow(bump, 6.0) : 0.0F));
            if (Math.abs(speed) < STICK && Math.abs(handPull) <= holds) {
                speed = 0.0F;
                continue;
            }
            float going = Math.abs(speed) < STICK ? Math.signum(handPull) : Math.signum(speed);
            float before = speed;
            speed += (handPull - going * holds - drag * speed) / INERTIA * h;
            if (Float.isNaN(hand) && before != 0.0F && Math.signum(speed) != Math.signum(before)) {
                // What holds it back stops it; it does not turn it the other way.
                speed = 0.0F;
            }
            speed = Mth.clamp(speed, -top, top);
            float from = angle;
            angle += speed * h;
            if (heaviness > 0.0F) {
                catches += Math.abs(Mth.floor((angle - CATCH_AT) * BLADES / Mth.TWO_PI) - Mth.floor((from - CATCH_AT) * BLADES / Mth.TWO_PI));
            }
        }
        pull = Mth.lerp(Math.min(1.0F, dt * 10.0F), pull, pulled / HAND_MOST);
        strain = Float.isNaN(hand) ? Math.max(0.0F, strain - dt * 3.0F) : pull;
        return catches;
    }
}
