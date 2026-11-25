package com.pockyl.handson_letsdo.client;

import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.scene.Visual;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Wine drawn at the barrel's bung, one bottle at a time, as Hands-On Delight's keg draws its drinks at the tap: the bottle comes from
 * the player and is held under the bung leaning in, the wine runs into its neck while it is eased back, the last drops fall, and the
 * full bottle goes back to the player. It is shown as the wine it becomes, never as an empty bottle that changes. The cook's own
 * bottle comes at the click, before the barrel answers; the wine other players draw comes with the barrel's word. Several drawn at
 * once come one after the other, quicker.
 */
final class BarrelDraughts {
    private static final String NODE = "draught";
    private static final String STREAM = "barrel_bung";
    /** How high the bottle is drawn, pixels (its icon is 16, the neck at the top in the middle). */
    static final float SIZE = 4.5F;
    /** Where it is held: its neck this far out from the head, its mouth this far under the bung, pixels. */
    private static final float OUT = 1.4F;
    private static final float UNDER = 1.1F;
    /**
     * How far it leans its neck in to the bung while it is held there, degrees: as a bottle is held under a tap, and so that the
     * camera, which looks down on the head, sees it whole and not squat. It leans further as the wine starts and is eased back as
     * it fills.
     */
    static final float TILT = 24.0F;
    private static final float LEAN = 16.0F;
    /** How small it is where it comes from and where it goes, how high it swings on the way, pixels. */
    private static final float FAR_SCALE = 0.55F;
    private static final float ARC = 3.0F;
    /** A bottle the cook brought waits this long for the barrel's word; without it, it goes back empty-handed. */
    private static final float ANSWER = 0.8F;
    /** The bottle faces whoever stands at the head: its icon is turned to the camera. */
    private static final float FACING = 180.0F;

    /** Seconds: coming, running, the last drops, going. */
    private record Pace(float come, float run, float drip, float go) {
        static final Pace ONE = new Pace(0.22F, 0.95F, 0.3F, 0.32F);
        static final Pace QUICK = new Pace(0.16F, 0.55F, 0.12F, 0.24F);
    }

    /** One bottle on its way: the wine it becomes, whose it is and where it goes. */
    private static final class Bottle {
        private final Item wine;
        /** ARGB, the colour of the wine that runs into it. */
        private final int color;
        private final boolean own;
        private final boolean fromHand;
        /** How many bottles the cook held: when every one is filled, the last wine stays in the hand. */
        private final int held;
        private boolean toHand;
        private Pace pace;
        private float age;
        /** Seconds since the click, counted on while it is held for the barrel's answer. */
        private float waited;
        private boolean answered;
        private boolean dry;
        private boolean poured;
        private Vector3f from;

        private Bottle(Item wine, int color, boolean own, boolean fromHand, int held, boolean toHand, Pace pace) {
            this.wine = wine;
            this.color = color;
            this.own = own;
            this.fromHand = fromHand;
            this.held = held;
            this.toHand = toHand;
            this.pace = pace;
        }

        private Bottle next() {
            Bottle bottle = new Bottle(wine, color, own, fromHand, held, false, Pace.QUICK);
            bottle.answered = true;
            return bottle;
        }
    }

    private final Deque<Bottle> waiting = new ArrayDeque<>();
    private Bottle current;
    private Item shownItem;
    private boolean handHidden;

    /** The scene is new: nothing is at the bung. */
    void ready(StationView view) {
        view.node(NODE).ifPresent(node -> node.setVisible(false));
        shownItem = null;
    }

    /** The middle of a bottle held under the bung, leaning in, scene pixels: where the wine offered to an empty hand hangs. */
    static Vector3f heldMiddle(Vector3fc bung, Vector3fc outward) {
        float half = SIZE / 2.0F;
        float radians = TILT * Mth.DEG_TO_RAD;
        float out = OUT + half * Mth.sin(radians);
        return new Vector3f(bung).add(outward.x() * out, -UNDER - half * Mth.cos(radians), outward.z() * out);
    }

    /** Whether a bottle is at the bung or on its way. */
    boolean busy() {
        return current != null || !waiting.isEmpty();
    }

    /**
     * The cook draws {@code wine} (with {@code all}, every bottle that waits): the bottle comes at once, the barrel answers after.
     * It comes from the hand ({@code held} empty bottles on the cursor, {@code fromHand}) or from the inventory; the wine goes back
     * to the hand when the hand held only that bottle, else into the inventory.
     */
    void draw(StationView view, Item wine, int color, boolean fromHand, int held, boolean all) {
        Pace pace = all ? Pace.QUICK : Pace.ONE;
        waiting.add(new Bottle(wine, color, true, fromHand, held, fromHand && held == 1, pace));
        if (fromHand && view.isFocused() && !handHidden) {
            // At the click, before the bottle sets off: the cursor's stack would cover it.
            view.setCarriedHidden(true);
            handHidden = true;
        }
    }

    /**
     * The barrel gave {@code count} bottles of {@code wine}: the cook's own bottle that waits for it goes on, with more after it
     * when several were drawn; wine somebody else drew comes now.
     */
    void drawn(Item wine, int color, int count) {
        int bottles = Math.max(1, count);
        Bottle own = unanswered();
        if (own != null) {
            own.answered = true;
            if (bottles > 1 && own.pace == Pace.ONE && own.age <= own.pace.come()) {
                own.pace = Pace.QUICK;
            }
            // The others follow the one they belong to, before anything the cook clicked after it.
            List<Bottle> order = new ArrayList<>(waiting);
            int at = own == current ? 0 : order.indexOf(own) + 1;
            Bottle last = own;
            for (int i = 1; i < bottles; i++) {
                last = own.next();
                order.add(at++, last);
            }
            if (own.fromHand && own.held <= bottles) {
                own.toHand = false;
                last.toHand = true;
            }
            waiting.clear();
            waiting.addAll(order);
            return;
        }
        for (int i = 0; i < bottles; i++) {
            Bottle bottle = new Bottle(wine, color, false, false, 1, false, bottles > 1 ? Pace.QUICK : Pace.ONE);
            bottle.answered = true;
            waiting.add(bottle);
        }
    }

    private Bottle unanswered() {
        if (current != null && current.own && !current.answered && !current.dry) {
            return current;
        }
        for (Bottle bottle : waiting) {
            if (bottle.own && !bottle.answered) {
                return bottle;
            }
        }
        return null;
    }

    /** The cook left: what is on its way is put away at once. */
    void clear(StationView view) {
        waiting.clear();
        current = null;
        hide(view);
    }

    /**
     * Moves the bottle at the bung.
     *
     * @param bung    where the wine leaves the bung, scene pixels
     * @param outward the way the head faces, level, unit
     */
    void frame(StationView view, float dt, Vector3fc bung, Vector3fc outward) {
        NodeView node = view.node(NODE).orElse(null);
        if (node == null) {
            waiting.clear();
            current = null;
            return;
        }
        if (current == null) {
            current = waiting.poll();
            if (current == null) {
                hide(view);
                return;
            }
            start(view, node, current, bung, outward);
        }
        Bottle bottle = current;
        bottle.age += dt;
        bottle.waited += dt;
        Pace pace = bottle.pace;
        if (bottle.own && !bottle.answered && !bottle.dry && bottle.waited > ANSWER) {
            bottle.dry = true;
        }
        if (!bottle.answered && !bottle.dry && bottle.age > pace.come()) {
            // Held under the bung until the barrel answers.
            bottle.age = pace.come();
        }
        float run = bottle.dry ? 0.0F : pace.run();
        float drip = bottle.dry ? 0.0F : pace.drip();
        float t = bottle.age;
        Vector3f mouth = new Vector3f(bung).add(outward.x() * OUT, -UNDER, outward.z() * OUT);
        Vector3f held = new Vector3f(mouth).sub(0.0F, SIZE, 0.0F);
        float lean;
        float size = 1.0F;
        Vector3f at;
        if (t < pace.come()) {
            float k = KitchenMotions.out(t / pace.come());
            at = new Vector3f(bottle.from).lerp(held, k);
            at.y += ARC * 0.5F * Mth.sin(k * Mth.PI);
            size = Mth.lerp(k, FAR_SCALE, 1.0F);
            lean = (TILT + LEAN) * k;
        } else if (t < pace.come() + run) {
            float k = (t - pace.come()) / run;
            if (!bottle.poured) {
                bottle.poured = true;
                view.playSound(pace == Pace.QUICK ? BarrelSounds.POUR_QUICK : BarrelSounds.POUR, NODE, 0.7F,
                        0.96F + view.level().random.nextFloat() * 0.08F);
            }
            // The bottle is set upright as it fills.
            lean = TILT + LEAN * (1.0F - KitchenMotions.smooth(k * 1.35F));
            at = held;
            stream(view, bung, mouth, lean, bottle.color, k);
        } else if (t < pace.come() + run + drip) {
            float k = (t - pace.come() - run) / drip;
            lean = TILT;
            at = held;
            drops(view, bung, outward, bottle, k, dt / drip);
        } else if (t < pace.come() + run + drip + pace.go()) {
            float k = KitchenMotions.smooth((t - pace.come() - run - drip) / pace.go());
            Vector3f to = destination(view, bottle, held);
            at = new Vector3f(held).lerp(to, k);
            at.y += ARC * Mth.sin(k * Mth.PI);
            size = Mth.lerp(k, 1.0F, FAR_SCALE);
            lean = TILT * (1.0F - k) - 6.0F * Mth.sin(k * Mth.PI);
        } else {
            finish(view, node);
            return;
        }
        place(node, at, lean, outward, size);
    }

    private void start(StationView view, NodeView node, Bottle bottle, Vector3fc bung, Vector3fc outward) {
        Vector3f mouth = new Vector3f(bung).add(outward.x() * OUT, -UNDER, outward.z() * OUT);
        float height = mouth.y + 3.0F;
        if (bottle.own && view.isFocused()) {
            bottle.from = (bottle.fromHand ? view.cursorOn(height) : FromPlayer.point(view, height, 0, 1)).orElse(null);
        }
        if (bottle.from == null) {
            // Somebody standing before the head.
            bottle.from = new Vector3f(mouth).add(outward.x() * 7.0F, 4.0F, outward.z() * 7.0F);
        }
        if (bottle.own && bottle.fromHand && view.isFocused() && !handHidden) {
            view.setCarriedHidden(true);
            handHidden = true;
        }
        if (shownItem != bottle.wine) {
            shownItem = bottle.wine;
            node.setVisual(Visual.ofItem(bottle.wine).withAnchor(new Vector3f(8.0F, 0.0F, 8.0F)));
        }
        node.setOnTop(true);
        node.setVisible(true);
    }

    /** The bottle's place and pose: {@code at} is its bottom; leaning, it turns round its mouth, which stays under the bung. */
    private static void place(NodeView node, Vector3f at, float lean, Vector3fc outward, float size) {
        float radians = lean * Mth.DEG_TO_RAD;
        float tall = SIZE * size;
        Vector3f bottom = new Vector3f(at).add(0.0F, tall * (1.0F - Mth.cos(radians)), 0.0F)
                .add(outward.x() * tall * Mth.sin(radians), 0.0F, outward.z() * tall * Mth.sin(radians));
        node.moveTo(bottom.x - 8.0F, bottom.y, bottom.z - 8.0F, 0.0F);
        // Turned to face the head's way, the lean is about the bottle's own x: its neck goes towards the barrel.
        node.rotateTo(-lean, FACING, 0.0F, 0.0F);
        node.scaleTo(size * SIZE / 16.0F, 0.0F);
    }

    /** The wine running from the bung into the neck: it starts at once, runs full, and thins just before the bottle is full. */
    private static void stream(StationView view, Vector3fc bung, Vector3f mouth, float lean, int color, float k) {
        float flow = Math.min(1.0F, k / 0.06F) * (k > 0.85F ? Math.max(0.2F, (1.0F - k) / 0.15F) : 1.0F);
        view.pourStream(STREAM, new Vector3f(bung), new Vector3f(mouth).sub(0.0F, 0.3F, 0.0F), 0xFF000000 | color,
                flow * 0.35F * (0.6F + 0.4F * (1.0F - (lean - TILT) / LEAN)), null);
    }

    /** The last two drops fall from the bung into the full bottle. */
    private static void drops(StationView view, Vector3fc bung, Vector3fc outward, Bottle bottle, float k, float step) {
        if (bottle.dry) {
            return;
        }
        for (float when : new float[] {0.12F, 0.55F}) {
            if (k >= when && k - step < when) {
                view.spawnParticles(DiegesisParticles.DROPLET, new Vector3f(bung).add(outward.x() * 0.2F, -0.2F, outward.z() * 0.2F),
                        new Vector3f(0.0F, -12.0F, 0.0F), 0.0F, 1, 0xFF000000 | bottle.color);
            }
        }
    }

    /** Where the full bottle goes: to the cursor it came from, to the inventory at the bottom of the screen, or back to whoever drew it. */
    private static Vector3f destination(StationView view, Bottle bottle, Vector3f held) {
        float height = held.y + SIZE + 3.0F;
        if (bottle.own && view.isFocused()) {
            Vector3f to = (bottle.toHand ? view.cursorOn(height) : FromPlayer.point(view, height, 0, 1)).orElse(null);
            if (to != null) {
                return to;
            }
        }
        return new Vector3f(bottle.from);
    }

    private void finish(StationView view, NodeView node) {
        current = null;
        node.setVisible(false);
        if (handHidden && waiting.stream().noneMatch(next -> next.own && next.fromHand)) {
            view.setCarriedHidden(false);
            handHidden = false;
        }
    }

    private void hide(StationView view) {
        view.node(NODE).ifPresent(node -> node.setVisible(false));
        if (handHidden) {
            view.setCarriedHidden(false);
            handHidden = false;
        }
    }
}
