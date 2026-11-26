package com.pockyl.handson_letsdo.client;

import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.NodeView;

/**
 * Where a pot's or pan's lid goes while somebody cooks, and its way there.
 *
 * <p>The pot's and the roaster's lid is put down behind the vessel, away from the cook: lifted a little and squared with the
 * vessel, swung up about its back edge as a hinged lid is and carried over the back rim, and set down on its edge on what the
 * vessel stands on (the campfire's grate, the stove's top, a magma block), leaning on the vessel's back wall, its underside to
 * it. One swing about one level axis: no flips, no turn round its knob of more than an eighth. Where nothing behind the vessel
 * is near enough to stand it on (a hanging pan over a campfire, a pan standing over one), it is hung on the back rim, against
 * the back wall. A hanging pan has its frame's top bar and chains over the middle: the lid's back edge is carried off the rim
 * early, so that the lid passes behind them as it swings up.
 *
 * <p>All sizes in pixels of the vessel's own frame (origin the middle of its floor, x along its handles), as its models have
 * them. The scene camera looks along +z from the front (-z), so the back is +z of the scene and the cook's right is -x.
 *
 * @param halfX    half the lid's width (its model's x)
 * @param halfZ    half the lid's depth (its model's z)
 * @param wallX    half the vessel's width over its walls
 * @param wallZ    half its depth over its walls
 * @param rim      the height of its rim
 * @param barHigh  a hanging frame's top bar, from the floor of the block (the frame does not hang higher with the pan); 0 for a
 *                 vessel with no frame
 * @param center   the middle of the lid's underside from its node's origin, in the lid's own axes (a model not centred on its
 *                 block)
 */
public record LidRest(float halfX, float halfZ, float wallX, float wallZ, float rim, float barHigh, Vector3f center) {
    /** Farm &amp; Charm's cooking pot and its 9 by 9 lid, whose model lies half a pixel off its block's middle. */
    public static final LidRest COOKING_POT = new LidRest(4.5F, 4.5F, 5.0F, 5.0F, 8.0F, 0.0F, new Vector3f(-0.5F, 0.0F, -0.5F));
    /** Farm &amp; Charm's roaster and its 10 by 8 lid; its frame's bar when it hangs. */
    public static final LidRest ROASTER = new LidRest(5.0F, 4.0F, 6.0F, 5.0F, 6.0F, 14.0F, new Vector3f());
    /** Candlelight's cooking pot: Farm &amp; Charm's pot and lid, the pot 4 px taller. */
    public static final LidRest CANDLELIGHT_POT = new LidRest(4.5F, 4.5F, 5.0F, 5.0F, 12.0F, 0.0F, new Vector3f(-0.5F, 0.0F, -0.5F));

    /** How far the lid set down leans in onto the vessel's wall from upright, radians; and one hung on the rim. */
    private static final float LEAN = 12.0F * Mth.DEG_TO_RAD;
    private static final float HUNG_LEAN = 5.0F * Mth.DEG_TO_RAD;
    /** How far over the rim the upper edge of a lid hung on it stands. */
    private static final float HOOK = 0.6F;
    /** Where its hinged edge lies on the rim before it goes: this far in from the rim's outer side (half the wall). */
    private static final float ON_RIM = 0.5F;
    /**
     * How far out from the wall its lower edge is carried before it goes down (more under a hanging pan's bar), how high the lid is
     * lifted on its way at the most, and where behind the wall the ground is looked for.
     */
    private static final float CLEAR = 1.0F;
    private static final float CLEAR_OF_BAR = 2.0F;
    private static final float RAISE = 0.8F;
    private static final float PROBE = 1.0F;
    /** Its gap to what it touches, so that no face of it lies in another. */
    private static final float GAP = 0.03F;
    private static final Vector3f UP = new Vector3f(0.0F, 1.0F, 0.0F);

    /** A lid's place (the middle of its underside) and turn in the scene's axes, from the vessel's middle. */
    public record Pose(Vector3f at, Quaternionf turn) {
    }

    /** What stands beside the vessel: the height of its top at a point (scene axes, pixels from the vessel's middle), NaN for none. */
    @FunctionalInterface
    public interface Ground {
        Ground NONE = (x, z) -> Float.NaN;

        float top(float x, float z);
    }

    /** The lid as it lies on the vessel: its node at {@code nodeAt} turned by {@code turn} (scene axes, from the vessel's middle). */
    public Pose lying(Vector3f nodeAt, Quaternionf turn) {
        return new Pose(new Vector3f(nodeAt).add(turn.transform(new Vector3f(center))), new Quaternionf(turn));
    }

    /**
     * Moves {@code node}, the lid, {@code k} (0 to 1) of the way from where it lies on the vessel ({@code on}, from {@link #lying})
     * to set down behind it on {@code ground}. The vessel is turned {@code turn} degrees about y in the scene, a hanging one hangs
     * {@code lift} higher; the lid's parent frame is turned {@code parentTurn} degrees about y and sits at {@code parentAt} from
     * the vessel's middle (scene axes), and the node's own place and turn in the scene file are {@code ownAt}, {@code ownTurn}.
     */
    public void open(NodeView node, Pose on, float k, float turn, boolean hanging, float lift, Ground ground, float parentTurn,
            Vector3f parentAt, Vector3f ownAt, Quaternionf ownTurn) {
        Pose pose = openPose(on, k, turn, hanging, lift, ground);
        put(node, pose.at(), pose.turn(), parentTurn, parentAt, ownAt, ownTurn, center);
    }

    /** {@link #open}'s pose of the lid's underside, scene axes from the vessel's middle. */
    Pose openPose(Pose on, float k, float turn, boolean hanging, float lift, Ground ground) {
        float vesselYaw = turn * Mth.DEG_TO_RAD;
        Quaternionf vessel = new Quaternionf().rotationY(vesselYaw);
        boolean onBar = hanging && barHigh > 0.0F;
        // The wall it goes behind: the back one; a hanging pan whose bar runs from front to back has it on the cook's right.
        Vector3f side = new Vector3f(0.0F, 0.0F, 1.0F);
        if (onBar && Math.abs(vessel.transform(new Vector3f(1.0F, 0.0F, 0.0F)).z) > 0.5F) {
            side.set(-1.0F, 0.0F, 0.0F);
        }
        Vector3f local = new Quaternionf(vessel).conjugate().transform(new Vector3f(side));
        float depth = Math.abs(local.x) * halfX + Math.abs(local.z) * halfZ;
        float width = Math.abs(local.x) * halfZ + Math.abs(local.z) * halfX;
        float wall = Math.abs(local.x) * wallX + Math.abs(local.z) * wallZ;
        float hingeOut = wall - ON_RIM;
        float rimY = rim + lift + GAP;
        float length = 2.0F * depth;
        Vector3f along = new Vector3f(UP).cross(side).normalize();
        Rest rest = rest(ground, side, along, width, wall, rimY, lift, length);
        float most = Mth.HALF_PI - rest.lean();
        // Squared with the vessel first, the short way to the nearest of its alike turns (a square lid every quarter turn).
        float alike = halfX == halfZ ? Mth.HALF_PI : Mth.PI;
        float onYaw = yawOf(on.turn());
        float round = vesselYaw - onYaw;
        round -= alike * Math.round(round / alike);
        float square = KitchenMotions.smooth(k / 0.35F);
        Quaternionf carried = new Quaternionf().rotationY(-onYaw).mul(on.turn());
        Quaternionf flat = new Quaternionf().rotationY(onYaw + round * square).mul(carried.slerp(new Quaternionf(), square));
        // Lying, it is carried from where it lay to the middle, its back edge on the rim. That edge goes out over the rim as the lid
        // swings up about it (early, so that a hanging pan's lid passes behind the bar), and down behind the wall once the lid
        // stands, to where it is set down; the lid rises a little on the way.
        Vector3f lying = new Vector3f(side).mul(hingeOut - depth).add(0.0F, rimY, 0.0F);
        Vector3f middle = new Vector3f(on.at()).lerp(lying, square);
        float out = Math.max(rest.out(), wall + (onBar ? CLEAR_OF_BAR : CLEAR));
        float away = KitchenMotions.smooth((k - 0.1F) / 0.4F);
        float settle = KitchenMotions.smooth((k - 0.55F) / 0.45F);
        Vector3f edge = new Vector3f(middle).add(new Vector3f(side).mul(depth))
                .add(new Vector3f(side).mul((out - hingeOut) * away + (rest.out() - out) * settle))
                .add(0.0F, RAISE * Mth.sin(Mth.PI * k) - (rimY - rest.foot()) * settle, 0.0F);
        float swing = most * KitchenMotions.smooth((k - 0.1F) / 0.65F);
        Quaternionf hinge = new Quaternionf().rotationAxis(swing, along);
        Vector3f at = edge.add(hinge.transform(new Vector3f(side).mul(-depth)));
        return new Pose(at, hinge.mul(flat));
    }

    /** Where the lid's lower edge ends, out from the vessel's middle and high, and how far it leans in from upright. */
    private record Rest(float out, float foot, float lean) {
    }

    /**
     * On what stands behind the wall (the lowest of it under the lid's edge) when the lid stood there reaches the wall, leaning on
     * the rim's outer edge, or on the wall with its upper edge where it stays lower than the rim; else hung on the rim.
     */
    private static Rest rest(Ground ground, Vector3f side, Vector3f along, float width, float wall, float rimY, float lift,
            float length) {
        float floor = Float.POSITIVE_INFINITY;
        for (int i = -1; i <= 1; i++) {
            Vector3f probe = new Vector3f(side).mul(wall + PROBE).add(new Vector3f(along).mul(i * (width - 0.5F)));
            floor = Math.min(floor, ground.top(probe.x, probe.z));
        }
        if (!Float.isNaN(floor) && floor + length * Mth.cos(LEAN) >= lift + 1.0F) {
            float foot = floor + GAP;
            float high = rimY - foot;
            float out = length * Mth.cos(LEAN) > high ? wall + GAP + high * (float) Math.tan(LEAN) : wall + GAP + length * Mth.sin(LEAN);
            return new Rest(out, foot, LEAN);
        }
        return new Rest(wall + GAP + length * Mth.sin(HUNG_LEAN), rimY + HOOK - length * Mth.cos(HUNG_LEAN), HUNG_LEAN);
    }

    /**
     * The lid on its edge with the inner side of its lower edge at {@code foot} from the middle along {@code side} (level, scene
     * axes) and {@code bottom} high, leaning in by {@code lean} radians, its knob outwards; {@code half} is half its height
     * stood so.
     */
    public static Pose stood(Vector3f side, float foot, float lean, float bottom, float half) {
        // Up its face from its lower edge, leaning in.
        Vector3f top = new Vector3f(UP).mul((float) Math.cos(lean)).sub(new Vector3f(side).mul((float) Math.sin(lean)));
        Vector3f at = new Vector3f(side).mul(foot).add(0.0F, bottom + GAP, 0.0F).add(top.mul(half));
        return new Pose(at, standing(side, lean));
    }

    /**
     * The turn of a lid stood on its edge on {@code side} (level, scene axes) and leaning in by {@code lean} radians, its knob
     * outwards: a lid lying level, its width along the edge it stands on, tipped about that edge's line, nothing else.
     */
    public static Quaternionf standing(Vector3f side, float lean) {
        Vector3f axis = new Vector3f(UP).cross(side).normalize();
        // Its own x along the edge it stands on, so that its depth goes up its face.
        Quaternionf level = new Quaternionf().rotationY((float) Math.atan2(-axis.z, axis.x));
        return new Quaternionf().rotationAxis(Mth.HALF_PI - lean, axis).mul(level);
    }

    /**
     * Moves {@code node}, a lid, between where it lies ({@code on}) and its rest {@code rest}, {@code k} 0 to 1, along one curve
     * with no stop on it: it leaves its place along {@code slide} (scene axes: out from under what spans the vessel's top, low),
     * rises on its way, comes over its rest {@code carry} over the higher of its two ends and goes down onto it. It looks the same
     * every {@code alike} degrees round its knob, and turns once it is out; the middle of its underside is {@code center} from its
     * node's origin, in its own axes. Its parent's frame is turned {@code parentTurn} degrees about y and sits at
     * {@code parentAt} (scene axes), and the node's own place and turn in the scene file are {@code ownAt}, {@code ownTurn}.
     */
    public static void place(NodeView node, Pose on, Vector3f slide, Pose rest, float k, float parentTurn, Vector3f parentAt,
            Vector3f ownAt, Quaternionf ownTurn, float carry, float alike, Vector3f center) {
        float u = 1.0F - k;
        float over = Math.max(on.at().y, rest.at().y) + carry;
        Vector3f off = new Vector3f(on.at()).add(slide);
        Vector3f out = new Vector3f(rest.at().x, over, rest.at().z);
        Vector3f at = new Vector3f(on.at()).mul(u * u * u).add(off.mul(3.0F * u * u * k)).add(out.mul(3.0F * u * k * k))
                .add(new Vector3f(rest.at()).mul(k * k * k));
        // It turns on its way, once it is out from under the bail.
        Quaternionf turn = turnOnTheWay(on.turn(), rest.turn(), KitchenMotions.smooth((k - 0.3F) / 0.6F), alike);
        put(node, at, turn, parentTurn, parentAt, ownAt, ownTurn, center);
    }

    /** Puts the lid's node so that the middle of its underside is {@code at}, turned {@code turn} (scene axes). */
    private static void put(NodeView node, Vector3f at, Quaternionf turn, float parentTurn, Vector3f parentAt, Vector3f ownAt,
            Quaternionf ownTurn, Vector3f center) {
        // From the middle of its underside to its node's origin.
        Vector3f origin = new Vector3f(at).sub(turn.transform(new Vector3f(center)));
        // Into the parent's frame, then from the node's own place and turn in the scene file.
        Quaternionf parent = new Quaternionf().rotationY(parentTurn * Mth.DEG_TO_RAD);
        Quaternionf back = new Quaternionf(parent).conjugate();
        Vector3f local = back.transform(origin).sub(back.transform(new Vector3f(parentAt))).sub(ownAt);
        Quaternionf extra = new Quaternionf(ownTurn).conjugate().mul(back).mul(turn);
        Vector3f euler = extra.getEulerAnglesYXZ(new Vector3f());
        node.moveTo(local.x, local.y, local.z, 0.0F);
        node.rotateTo(euler.x * Mth.RAD_TO_DEG, euler.y * Mth.RAD_TO_DEG, euler.z * Mth.RAD_TO_DEG, 0.0F);
    }

    /**
     * The lid's turn {@code t} of the way from lying ({@code on}: turned round its knob, and tipped with a vessel in the hand) to
     * stood ({@code rest}: lying level and tipped up about a level axis): the tip grows about that one axis, the turn round its
     * knob goes the short way to the nearest of its alike turns, and the vessel's tip wears off. Never more than a quarter turn
     * of tip and an eighth round the knob, whatever either end is.
     */
    static Quaternionf turnOnTheWay(Quaternionf on, Quaternionf rest, float t, float alike) {
        float onYaw = yawOf(on);
        Quaternionf carried = new Quaternionf().rotationY(-onYaw).mul(on);
        Vector3f knob = rest.transform(new Vector3f(UP));
        Vector3f axis = new Vector3f(UP).cross(knob);
        float tip = (float) Math.acos(Mth.clamp(knob.y, -1.0F, 1.0F));
        Quaternionf restLevel;
        if (axis.lengthSquared() < 1.0E-8F) {
            axis.set(1.0F, 0.0F, 0.0F);
            restLevel = new Quaternionf(rest);
        } else {
            axis.normalize();
            restLevel = new Quaternionf().rotationAxis(-tip, axis).mul(rest);
        }
        float step = alike * Mth.DEG_TO_RAD;
        float round = yawOf(restLevel) - onYaw;
        round -= step * Math.round(round / step);
        return new Quaternionf().rotationAxis(tip * t, axis).mul(new Quaternionf().rotationY(onYaw + round * t))
                .mul(carried.slerp(new Quaternionf(), t));
    }

    /** How far {@code turn} is turned round the scene's y, radians: its part about y alone. */
    private static float yawOf(Quaternionf turn) {
        return 2.0F * (float) Math.atan2(turn.y, turn.w);
    }
}
