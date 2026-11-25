package com.pockyl.handson_letsdo.client;

import net.minecraft.sounds.SoundEvents;
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
 * Bottles filled at the press's tub, one at a time, the barrel's draughts ({@link BarrelDraughts}) told the tub's way: the bottle comes from the player over the tub, is tipped on
 * its side and dipped neck first into the juice, which bubbles into it, is lifted out upright with a drop or two falling back,
 * and goes back to the player full. It is shown as the drink it becomes. The cook's own bottle comes at the click, before the
 * press answers; bottles other players fill come with the press's word. Several at once come one after the other, quicker.
 */
final class TubBottles {
    private static final String NODE = "draught";
    /** How high the bottle is drawn, pixels (its icon is 16, the neck at the top in the middle). */
    static final float SIZE = 4.5F;
    /** How far it is tipped to be dipped, degrees from upright, in the camera's plane: its neck down into the tub. */
    private static final float DIP = 115.0F;
    /** How far under the juice its mouth goes, and the lowest it goes over the tub's floor, pixels. */
    private static final float UNDER = 0.6F;
    private static final float LOWEST = 1.3F;
    /** Where it waits upright over the tub before it is dipped and after, its bottom this high over the tub's rim. */
    private static final float OVER_RIM = 2.0F;
    /** How small it is where it comes from and where it goes, how high it swings on the way, pixels. */
    private static final float FAR_SCALE = 0.55F;
    private static final float ARC = 3.0F;
    /** A bottle the cook brought waits this long for the press's word; without it, it goes back empty. */
    private static final float ANSWER = 0.8F;
    /** Glugs a second while it is under, and how long the drop that falls back off it takes to land, seconds. */
    private static final float GLUGS = 3.0F;
    private static final float DROP_FALL = 0.15F;
    /** At most this many bottles are dipped for one filling: more would keep the cook watching what is already theirs. */
    private static final int SHOWN_MOST = 3;
    /** The bottle faces whoever stands before the press: its icon is turned to the camera. */
    private static final float FACING = 180.0F;

    /** Seconds: coming, dipped, the last drops, going. */
    private record Pace(float come, float dip, float drip, float go) {
        static final Pace ONE = new Pace(0.24F, 0.95F, 0.25F, 0.3F);
        static final Pace QUICK = new Pace(0.16F, 0.55F, 0.12F, 0.22F);
    }

    /** One bottle on its way: the drink it becomes, whose it is and where it goes. */
    private static final class Bottle {
        private final Item drink;
        /** ARGB, the colour of the juice that runs into it. */
        private final int color;
        private final boolean own;
        private final boolean fromHand;
        /** How many bottles the cook held: when every one is filled, the last drink stays in the hand. */
        private final int held;
        private boolean toHand;
        private Pace pace;
        private float age;
        /** Seconds since the click, counted on while it is held for the press's answer. */
        private float waited;
        private boolean answered;
        private boolean dry;
        private boolean dipped;
        /** When the drop that falls back off it lands in the tub; below 0 for none. */
        private float dropLands = -1.0F;
        private Vector3f from;

        private Bottle(Item drink, int color, boolean own, boolean fromHand, int held, boolean toHand, Pace pace) {
            this.drink = drink;
            this.color = color;
            this.own = own;
            this.fromHand = fromHand;
            this.held = held;
            this.toHand = toHand;
            this.pace = pace;
        }

        private Bottle next() {
            Bottle bottle = new Bottle(drink, color, own, fromHand, held, false, Pace.QUICK);
            bottle.answered = true;
            return bottle;
        }
    }

    private final Deque<Bottle> waiting = new ArrayDeque<>();
    private Bottle current;
    private Item shownItem;
    private boolean handHidden;

    /** The scene is new: nothing is at the tub. */
    void ready(StationView view) {
        view.node(NODE).ifPresent(node -> node.setVisible(false));
        shownItem = null;
    }

    /** Whether a bottle is at the tub or on its way. */
    boolean busy() {
        return current != null || !waiting.isEmpty();
    }

    /**
     * The cook fills a bottle with {@code drink} (with {@code all}, every portion the tub holds): the bottle comes at once, the
     * press answers after. It comes from the hand ({@code held} empty bottles on the cursor, {@code fromHand}) or from the
     * inventory; the drink goes back to the hand when the hand held only that bottle, else into the inventory.
     */
    void fill(StationView view, Item drink, int color, boolean fromHand, int held, boolean all) {
        Pace pace = all ? Pace.QUICK : Pace.ONE;
        waiting.add(new Bottle(drink, color, true, fromHand, held, fromHand && held == 1, pace));
        if (fromHand && view.isFocused() && !handHidden) {
            // At the click, before the bottle sets off: the cursor's stack would cover it.
            view.setCarriedHidden(true);
            handHidden = true;
        }
    }

    /**
     * The press filled {@code count} bottles with {@code drink}: the cook's own bottle that waits for it goes on, with more after
     * it when several were filled; bottles somebody else filled come now.
     */
    void filled(Item drink, int color, int count) {
        int filled = Math.max(1, count);
        int bottles = Math.min(filled, SHOWN_MOST);
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
            if (own.fromHand && own.held <= filled) {
                own.toHand = false;
                last.toHand = true;
            }
            waiting.clear();
            waiting.addAll(order);
            return;
        }
        for (int i = 0; i < bottles; i++) {
            Bottle bottle = new Bottle(drink, color, false, false, 1, false, bottles > 1 ? Pace.QUICK : Pace.ONE);
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
     * Moves the bottle at the tub.
     *
     * @param middle  the middle of the tub's inside at its rim, scene pixels
     * @param surface how high the juice stands in it now
     * @param liquid  the tub's liquid node, for the bubbles and the ripples
     * @param origin  where that node's pixels count from, scene pixels
     */
    void frame(StationView view, float dt, Vector3fc middle, float surface, String liquid, Vector3fc origin) {
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
            start(view, node, current, middle);
        }
        Bottle bottle = current;
        bottle.age += dt;
        bottle.waited += dt;
        Pace pace = bottle.pace;
        if (bottle.own && !bottle.answered && !bottle.dry && bottle.waited > ANSWER) {
            bottle.dry = true;
        }
        if (!bottle.answered && !bottle.dry && bottle.age > pace.come()) {
            // Held over the tub until the press answers.
            bottle.age = pace.come();
        }
        float dip = bottle.dry ? 0.0F : pace.dip();
        float drip = bottle.dry ? 0.0F : pace.drip();
        float t = bottle.age;
        float tall = SIZE;
        // Upright over the tub, its bottom over the rim.
        Vector3f over = new Vector3f(middle).add(0.0F, OVER_RIM, 0.0F);
        float tilt = 0.0F;
        float size = 1.0F;
        Vector3f at;
        if (t < pace.come()) {
            float k = KitchenMotions.out(t / pace.come());
            at = new Vector3f(bottle.from).lerp(over, k);
            at.y += ARC * 0.5F * Mth.sin(k * Mth.PI);
            size = Mth.lerp(k, FAR_SCALE, 1.0F);
        } else if (t < pace.come() + dip) {
            float k = (t - pace.come()) / dip;
            // Tipped and lowered in the first quarter, held in, lifted out and righted in the last.
            float in = KitchenMotions.smooth(Math.min(k / 0.25F, (1.0F - k) / 0.2F));
            tilt = DIP * in;
            Vector3f mouth = new Vector3f(middle.x() + 1.0F, Math.max(LOWEST, surface - UNDER), middle.z());
            Vector3f dipped = new Vector3f(mouth).sub(mouthOffset(DIP, tall));
            at = new Vector3f(over).lerp(dipped, in);
            if (!bottle.dipped && in > 0.8F) {
                bottle.dipped = true;
                view.playSound(SoundEvents.BOTTLE_FILL, NODE, 0.6F, 0.95F + view.level().random.nextFloat() * 0.1F);
                view.node(liquid).ifPresent(tub -> tub.ripple(mouth.x - origin.x(), mouth.z - origin.z(), 0.5F));
                view.spawnParticles(DiegesisParticles.SPLASH, mouth, new Vector3f(0.0F, 2.0F, 0.0F), 0.4F, 3, 0xFF000000 | bottle.color);
            }
            if (in > 0.9F && view.level().random.nextFloat() < dt * 10.0F) {
                view.spawnInLiquid(DiegesisParticles.BUBBLE, liquid, 1, 0xE0FFFFFF, new Vector3f(0.0F, 3.0F, 0.0F));
            }
            // The air in it glugs out as the juice runs in.
            if (in > 0.9F && view.level().random.nextFloat() < dt * GLUGS) {
                view.playSound(PressSounds.GLUG, NODE, 0.25F, 0.95F + view.level().random.nextFloat() * 0.2F);
            }
        } else if (t < pace.come() + dip + drip) {
            float k = (t - pace.come() - dip) / drip;
            at = over;
            if (!bottle.dry && k >= 0.2F && k - dt / drip < 0.2F) {
                view.spawnParticles(DiegesisParticles.DROPLET, new Vector3f(over).add(0.0F, -0.2F, 0.0F), new Vector3f(0.0F, -8.0F, 0.0F),
                        0.0F, 1, 0xFF000000 | bottle.color);
                bottle.dropLands = t + DROP_FALL;
            }
        } else if (t < pace.come() + dip + drip + pace.go()) {
            float k = KitchenMotions.smooth((t - pace.come() - dip - drip) / pace.go());
            Vector3f to = destination(view, bottle, over);
            at = new Vector3f(over).lerp(to, k);
            at.y += ARC * Mth.sin(k * Mth.PI);
            size = Mth.lerp(k, 1.0F, FAR_SCALE);
        } else {
            finish(view, node);
            return;
        }
        if (bottle.dropLands >= 0.0F && t >= bottle.dropLands) {
            // The drop that fell back rings the juice under the bottle.
            bottle.dropLands = -1.0F;
            view.node(liquid).ifPresent(tub -> tub.ripple(over.x - origin.x(), over.z - origin.z(), 0.2F));
            view.playSound(PressSounds.DROP, NODE, 0.25F, 1.1F + view.level().random.nextFloat() * 0.2F);
        }
        place(node, at, tilt, size);
    }

    /** From the bottle's bottom to its mouth, tipped {@code tilt} degrees in the camera's plane, scene pixels. */
    private static Vector3f mouthOffset(float tilt, float tall) {
        float radians = tilt * Mth.DEG_TO_RAD;
        // Turned to the camera (half round about y), a tip about its own z swings its neck towards the scene's +x.
        return new Vector3f(tall * Mth.sin(radians), tall * Mth.cos(radians), 0.0F);
    }

    private void start(StationView view, NodeView node, Bottle bottle, Vector3fc middle) {
        float height = middle.y() + OVER_RIM + 3.0F;
        if (bottle.own && view.isFocused()) {
            bottle.from = (bottle.fromHand ? view.cursorOn(height) : FromPlayer.point(view, height, 0, 1)).orElse(null);
        }
        if (bottle.from == null) {
            // Somebody standing before the press.
            bottle.from = new Vector3f(middle).add(0.0F, 5.0F, -8.0F);
        }
        if (bottle.own && bottle.fromHand && view.isFocused() && !handHidden) {
            view.setCarriedHidden(true);
            handHidden = true;
        }
        if (shownItem != bottle.drink) {
            shownItem = bottle.drink;
            node.setVisual(Visual.ofItem(bottle.drink).withAnchor(new Vector3f(8.0F, 0.0F, 8.0F)));
        }
        node.setOnTop(true);
        node.setVisible(true);
    }

    /** The bottle's place and pose: {@code at} is its bottom, {@code tilt} its tip in the camera's plane. */
    private static void place(NodeView node, Vector3f at, float tilt, float size) {
        node.moveTo(at.x - 8.0F, at.y, at.z - 8.0F, 0.0F);
        node.rotateTo(0.0F, FACING, tilt, 0.0F);
        node.scaleTo(size * SIZE / 16.0F, 0.0F);
    }

    /** Where the full bottle goes: to the cursor it came from, to the inventory at the bottom of the screen, or back to whoever filled it. */
    private static Vector3f destination(StationView view, Bottle bottle, Vector3f over) {
        float height = over.y + SIZE + 3.0F;
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
