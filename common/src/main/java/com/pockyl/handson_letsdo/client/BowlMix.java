package com.pockyl.handson_letsdo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.Floater;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.scene.MassLook;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.handson_letsdo.KitchenTags;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleSupplier;

/**
 * What is in the crafting bowl and how it behaves. Things thrown in come over from the hand in an arc, turning, and land with a bounce:
 * on the dry floor with a little dust of their colour, in a liquid with a splash. What is thrown in lies as itself, the item as the
 * pot shows its food (several of a stack, a few of them), so the cook sees what is in the bowl, and the whisk knocks and pushes them
 * about: they slide, bump into each other and the walls, turn over at a hard knock and come to rest; in a liquid they float and are
 * carried along by the whisk. Liquids are poured from their containers, eggs cracked on the rim, flour and
 * the like shaken out with a puff of dust into the mixture. As the bowlful is whisked the mixture
 * rises and thickens: from the liquid's own colour to the colour of what it becomes, from thin and quick to forget a stir to a thick
 * batter that keeps the whisk's furrows, lumpy at first and smooth at the end; what lies in it is coated, shrinks and goes under. A whisk hitting the wall fast flicks drops of a liquid up. Done, the pieces swell
 * and are gone with a puff, and the finished mixture lies in the bowl until the result is taken; then it is scooped out. Scene pixels
 * from the bowl's middle; the bowl is square, so its inside is the same whichever way it is turned.
 */
final class BowlMix {
    /** The inside of the bowl: half its width, the floor's top, the rim's top; the finished mixture's height over the floor. */
    static final float HALF = 5.0F;
    static final float FLOOR = 0.1F;
    static final float RIM = 8.05F;
    static final float DONE_LEVEL = 6.8F;
    /** The mixture's tank (crafting_bowl.json, node mixture): how high its box is over the floor. */
    private static final float TANK_HEIGHT = 7.4F;
    private static final float MIDDLE = 8.0F;

    /**
     * A piece: the item a little bigger than the pot's food (3.2), since it lies deeper under the camera, pixels across as a slot of
     * that size draws it; how far round its middle it keeps others and the walls off, about what an icon's pixels reach turned any
     * way (less, and a piece by a wall showed through it). An item visual at scale 1 is as big as a slot of {@link #ITEM_PIXELS},
     * about its middle.
     */
    private static final float SIZE = 4.2F;
    private static final float RADIUS = 1.9F;
    private static final float ITEM_PIXELS = 16.0F;
    /** What lies over another lies an item's depth and a hair higher, reaching it this fast (a second). */
    private static final float LAYER = SIZE / 16.0F + 0.06F;
    private static final float LAYER_SETTLE = 12.0F;
    private static final Vector3f ITEM_MIDDLE = new Vector3f(8.0F, 8.0F, 8.0F);
    /** Several of a stack show as more pieces, at most this many more of each, and no more pieces than this in all. */
    private static final int COPIES = 2;
    private static final int MOST_PIECES = 10;

    /** Coming in: seconds in the air from the hand, dropped from the cursor; the stagger between several; how high and how far over. */
    private static final float FLY_TIME = 0.42F;
    private static final float DROP_TIME = 0.24F;
    private static final float FLY_FROM = 10.0F;
    private static final float DROP_FROM = 6.0F;
    private static final float FLY_ARC = 4.0F;
    private static final float STAGGER = 0.13F;
    /** What is left of the speed it came with as it lands, and the bounce after it. */
    private static final float SKID = 0.3F;
    private static final float BOUNCE_HOP = 0.7F;
    private static final float BOUNCE_TIME = 0.2F;

    /**
     * What floats is carried back from the walls by the liquid running in again: how fast at the wall (pixels a second), from how far
     * out (a part of the room it has).
     */
    private static final float RUN_BACK = 4.0F;
    private static final float RUN_BACK_FROM = 0.55F;
    /** Dry, it drifts back only this share as fast, and only while the whisk works. */
    private static final float DRY_BACK = 0.5F;
    /**
     * Sliding on the dry floor: how fast it stops (pixels a second squared), what a wall gives back dry and wet, the fastest a piece
     * goes. In a liquid: how quickly a piece takes the liquid's way, thin and thick (a second), and how much of the whisk's way the
     * liquid next to it takes, and how far round it.
     */
    private static final float FRICTION = 70.0F;
    private static final float BOUNCE_DRY = 0.35F;
    private static final float BOUNCE_WET = 0.12F;
    private static final float FASTEST = 40.0F;
    private static final float DRAG_THIN = 3.0F;
    private static final float DRAG_THICK = 8.0F;
    private static final float DRAWN_ALONG = 0.75F;
    private static final float DRAWN_REACH = 2.8F;
    /** The whisk's loops as something that pushes, pixels round their middle; how hard it hits, 1 = it gives back all. */
    private static final float WHISK_RADIUS = 2.0F;
    private static final float WHISK_KICK = 0.45F;
    /** A knock: from what speed it is heard, from what speed it may turn a piece over, and how likely, and how long it takes. */
    private static final float KNOCK_FROM = 7.0F;
    private static final float TURN_FROM = 16.0F;
    private static final float TURN_CHANCE = 0.35F;
    private static final float TURN_TIME = 0.38F;
    private static final float TURN_HOP = 1.3F;
    private static final float KNOCK_GAP = 0.18F;
    /**
     * Degrees a second of spin for each pixel a second a piece rubs along the whisk, a wall or another piece, the most it turns, and how
     * fast that dies away: a knocked piece slides and turns a little, it does not twirl.
     */
    private static final float SPIN_FROM_RUB = 3.0F;
    private static final float MOST_SPIN = 70.0F;
    private static final float SPIN_HALF_LIFE = 0.15F;
    /** How fast a thing turns over in the air as it is thrown in, at most, degrees a second about the upright. */
    private static final float THROWN_SPIN = 60.0F;

    /** Stirred: pieces shrink by this much, go this much deeper and take this much of the mixture's colour by the time it is done. */
    private static final float DISSOLVE = 0.55F;
    private static final float COATED = 0.5F;
    /** Done: each piece swells for so long, then shrinks away; they go one after the other. */
    private static final float SWELL_TIME = 0.16F;
    private static final float GONE_TIME = 0.4F;
    private static final float SWELL = 1.25F;
    private static final float MELT_STAGGER = 0.07F;

    /** Flour or grain shaken out: how wide its dust puffs, how much bigger more of it lies, the most. */
    private static final float HEAP_WIDE = 3.4F;
    private static final float HEAP_MORE = 0.06F;
    private static final float HEAP_LARGEST = 1.3F;
    /** How fast the whisk works a heap in while it goes through it, of the heap a second at a whisk's full pace. */
    private static final float HEAP_WORKED = 0.35F;

    /** What a portion of a liquid fills, pixels: a bucket, a bottle, an egg; the most all of them fill before it is whisked. */
    private static final float BUCKETFUL = 2.0F;
    private static final float BOTTLEFUL = 1.0F;
    private static final float EGGFUL = 0.45F;
    private static final float MOST_LIQUID = 4.6F;
    /** How hard a liquid (water, milk) is to whisk through, a full bowl of it, 0 to 1. */
    private static final float LIQUID_HEAVINESS = 0.08F;
    /** Water in a bowl: clear and pale, not the blue of a water bucket's icon, which would turn a dough grey on its way. */
    private static final int WATER = 0xFFC4D4DE;
    /** How fast what is poured rises, pixels a second; how fast the mixture is scooped out once its result is taken. */
    private static final float POUR_RISE = 3.2F;
    private static final float SCOOPED = 14.0F;

    /**
     * What was poured turns into the mass of what the bowlful becomes ({@link BowlMass}) from this far through the stirring to that far;
     * with nothing poured, what goes in is the mass from the first turn. The finished bowlful's top (crafting_bowl.json, node dough: a
     * sheet at y 7) is not drawn: it is what the cook clicks, outlined, to take the result.
     */
    private static final float THICK_FROM = 0.12F;
    private static final float THICK_FULL = 0.55F;
    private static final float DOUGH_SHEET = 7.0F;
    private static final int UNDRAWN = 0x00FFFFFF;
    /**
     * What a porridge or a salad holds as pieces (of each thing and in all: {@link BowlMass#heldEach}, {@link BowlMass#heldMost}), kept
     * apart by half their size, all of them by this far through the stirring.
     */
    private static final float PIECE_CLEARANCE = 0.5F;
    private static final float HELD_FROM = 0.08F;
    private static final float HELD_ALL = 0.6F;
    /** The mixture's look: how long the whisk's furrows stay, thin and thick, how clearly. */
    private static final float WAKE_THIN = 3.0F;
    private static final float WAKE_THICK = 3.0F;
    private static final float WAKE_CONTRAST_THIN = 0.9F;
    private static final float WAKE_CONTRAST_THICK = 0.9F;
    /** The whisk's furrow: how wide, how deep at most, and at what pace it is deepest. */
    private static final float WAKE_WIDTH = 1.6F;
    private static final float WAKE_DEPTH = 0.75F;
    private static final float WAKE_FULL = 40.0F;
    /** How see-through it is: water, milk, a thick mixture (alpha). */
    private static final int ALPHA_WATER = 0x70;
    private static final int ALPHA_MILK = 0xE8;
    private static final int ALPHA_THICK = 0xF6;

    /** The whisk at speed: from what pace it splashes, pixels a second; splashes a second at full pace. */
    private static final float SPLASH_FROM = 18.0F;
    private static final float SPLASH_RATE = 9.0F;
    private static final float FULL_PACE = 40.0F;
    /** Waves round the whisk in a thin liquid: up to how thick, from what speed, how often. */
    private static final float WAVES_UNTIL = 0.5F;
    private static final float WAVES_FROM = 6.0F;
    private static final float WAVE_GAP = 0.12F;
    /** It hits the wall: from what pace it flicks drops of the mixture up. */
    private static final float FLICK_FROM = 14.0F;
    /** A thick mixture squelches under a whisk going fast enough: how often at most. */
    private static final float SQUELCH_GAP = 0.32F;

    private static final int GLINT_COLOR = 0xFFFFF0C8;
    /**
     * What was shaken out, poured or cracked in is not drawn; while an empty hand is over the bowl it shows see-through where it lies,
     * so that the cook can find it, and brighter under the hand, to be taken back.
     */
    private static final int UNSEEN = 0x00FFFFFF;
    private static final int SHOWN = 0x96FFFFFF;
    private static final int FOUND = 0xD2FFFFFF;
    /** How far round the bowl's inside the hand counts as over it, pixels beyond its walls. */
    private static final float OVER_BOWL = 1.5F;
    /**
     * The bowl's front wall hides a strip of what lies in it from the camera (63 degrees down): this much of it for each pixel the wall
     * stands over a thing (1 / tan 63). What lies there is kept out of the strip, this much beyond it, so that the wall never covers
     * what the cook would take out. Afloat high in a liquid, little is hidden and things may go near the wall.
     */
    private static final float FRONT_HIDES = 0.51F;
    private static final float FRONT_SEEN = 0.6F;
    /** How far under the rim an unseen thing rises while it is shown, pixels, and how quickly (a second). */
    private static final float SEEN_UNDER_RIM = 1.6F;
    /** Where the resting whisk's handle covers the floor from the camera: so far across the middle from its loops, so far behind. */
    private static final float HANDLE_ACROSS = 3.4F;
    private static final float HANDLE_BEHIND = 3.6F;
    /** How far round that place nothing rests, pixels, and how fast what lies there slides out of it, pixels a second. */
    private static final float HANDLE_CLEAR = 2.2F;
    private static final float HANDLE_SLIDE = 8.0F;
    private static final float RISE_RATE = 10.0F;
    private static final ResourceLocation LIQUID = BowlMass.LIQUID;

    /** Where a thing is laid, a hand's way: round the middle, then the middle. */
    private static final float[][] PLACES = {{-2.5F, -2.4F}, {2.5F, 2.4F}, {2.5F, -2.4F}, {-2.5F, 2.4F}, {0.0F, -2.9F}, {0.0F, 2.9F},
            {-2.9F, 0.0F}, {2.9F, 0.0F}, {0.0F, 0.0F}};
    /** What a liquid is poured from: over the right of the bowl as the cook sees it, tipped towards the middle. */
    private static final float POUR_X = -2.0F;
    private static final float POUR_Z = 0.4F;
    /** The size of a container brought over, of an egg and its inside. */
    private static final float HAND_SIZE = 2.9F;
    private static final float EGG_SIZE = 2.4F;
    private static final float EGG_INSIDE = 2.6F;

    enum Kind { PIECE, HEAP, POURED, CRACKED }

    /** One thing in the bowl: a piece (a slot's own node or a copy), or what flour or grain was shaken out of. */
    private static final class Body {
        final int slot;
        final int copy;
        final String node;
        final Kind kind;
        ItemStack stack;
        boolean meat;
        boolean blockLike;
        float height;
        float radius;
        /** How far its drawn pixels reach from its middle, turned any way: the walls keep that much off. */
        float reach;
        float x;
        float z;
        float vx;
        float vz;
        float yaw;
        float spin;
        float over;
        float bob;
        float flyAge = Float.NaN;
        float flyTime;
        float fromX;
        float fromY;
        float fromZ;
        float arc;
        float hopAge = Float.NaN;
        float hopHeight;
        float hopTime;
        float turnAge = Float.NaN;
        float meltAge = Float.NaN;
        float knocked;
        /** Shaken out: how much of it is left to work in, 0 to 1, and how big it lies, of a piece. */
        float left = 1.0F;
        float wide = 1.0F;
        float lastTint = -1.0F;
        /** How high it lies on the things under it, pixels, as shown. */
        float lift;
        boolean waitsForShake;
        /** Unseen, it stands for a poured or cracked thing, so that it can be found and taken back: it pushes nothing. */
        boolean stands;
        /** Shown to the hand that looks for it, and how far it has risen for that, pixels. */
        boolean seen;
        float rise;

        Body(int slot, int copy, String node, Kind kind) {
            this.slot = slot;
            this.copy = copy;
            this.node = node;
            this.kind = kind;
        }

        boolean rests() {
            return Float.isNaN(flyAge) && Float.isNaN(meltAge) && !waitsForShake;
        }
    }

    /** The whisk as the bowl feels it: where its loops are and how they go, scene pixels from the middle; held in the bowl or at rest. */
    record Whisk(float x, float z, float vx, float vz, boolean working) {
        float speed() {
            return (float) Math.hypot(vx, vz);
        }
    }

    private final RandomSource random = RandomSource.create();
    private final String[] slotNodes;
    private final ItemStack[] shown;
    private final List<Body> bodies = new ArrayList<>();
    private final KitchenMotions.Motion[] going;
    /** Liquid poured in by slot, pixels it fills, once it has run in; and how much shows now in all. */
    private final float[] poured;
    private final boolean[] flowed;
    private final int[] liquidColors;
    private float liquid;
    private float level;
    /** How far the mixture has turned into its mass, 0 to 1, as last drawn. */
    private float thickNow;
    private float shownStirred;
    private float clock;
    private float mixedAt = -10.0F;
    /** Where the whisk's loops rest, scene pixels from the middle: nothing is laid there. */
    private float restX = Float.NaN;
    private float restZ;
    private boolean waits;
    private int mixColor = 0xFFE9D9B2;
    private int finalColor = 0xFFE9D9B2;
    /** What the bowlful turns into, and the colour of what it makes, RGB; the kind its sheet shows now. */
    private BowlMass mass = BowlMass.DOUGH;
    private int becomesColor = 0xFFFFFF;
    /** The mass it shows as, made again only when the kind or the colour of what it makes changes; what it holds as pieces. */
    private MassLook massLook;
    private BowlMass lookedMass;
    private int lookedColor;
    private List<Floater> held = List.of();
    /** What went in or what it becomes changed since the held pieces were worked out. */
    private boolean heldChanged = true;
    private float splash;
    private float waves;
    private float squelch;

    BowlMix(String[] slotNodes) {
        this.slotNodes = slotNodes;
        int slots = slotNodes.length;
        shown = new ItemStack[slots];
        java.util.Arrays.fill(shown, ItemStack.EMPTY);
        going = new KitchenMotions.Motion[slots];
        poured = new float[slots];
        flowed = new boolean[slots];
        liquidColors = new int[slots];
    }

    /** How a stack goes into the bowl: poured, cracked, shaken out into a heap, or thrown in as pieces. */
    static Kind kindOf(ItemStack stack) {
        if (KitchenLooks.isPoured(stack)) {
            return Kind.POURED;
        }
        if (KitchenLooks.isCracked(stack)) {
            return Kind.CRACKED;
        }
        if (KitchenTags.is(stack, KitchenTags.DISSOLVES) || KitchenTags.grains(stack)) {
            return Kind.HEAP;
        }
        return Kind.PIECE;
    }

    /**
     * How hard the whisk goes through the bowlful, 0 to 1: as heavy as what it is now (the liquid poured, or as far as it has turned
     * into its mass, that mass's heaviness) and as full as the bowl is. Only how the whisk feels: the server counts the hand's way.
     */
    float resistance() {
        float heavy = Mth.lerp(thickNow, LIQUID_HEAVINESS, mass.heaviness());
        return Mth.clamp(heavy * level / DONE_LEVEL, 0.0F, 1.0F);
    }

    /** How thick the mixture is now, 0 (water, or a dry bowl) to 1 (a finished batter): pieces drag in it, the whisk digs and splashes. */
    float viscosity() {
        return level < 0.4F ? 0.0F : Mth.clamp(0.15F + 0.85F * KitchenMotions.smooth(shownStirred), 0.0F, 1.0F);
    }

    /** The mixture's surface, scene pixels over the floor's bottom. */
    float surface() {
        return FLOOR + level;
    }

    /** The colour of the mixture now, ARGB. */
    int color() {
        return mixColor;
    }

    /** The bowlful is mixed: what goes from its slots now swells and goes. */
    void mixed() {
        mixedAt = clock;
        for (Body body : bodies) {
            if (Float.isNaN(body.meltAge) && shown[body.slot].isEmpty()) {
                body.meltAge = 0.0F;
            }
        }
    }

    /**
     * The slots as they are now; {@code quiet} lays what is new where it would lie, without it coming in. Things that came in fly from
     * the hand, several one after the other, or drop from the cursor when it is one.
     */
    void sync(StationView view, ItemStack[] now, boolean quiet) {
        heldChanged = true;
        int arriving = 0;
        for (int i = 0; i < now.length; i++) {
            if (!quiet && !now[i].isEmpty() && (shown[i].isEmpty() || ItemStack.isSameItemSameTags(now[i], shown[i])
                    && now[i].getCount() > shown[i].getCount())) {
                arriving++;
            }
        }
        int k = 0;
        int filled = 0;
        for (ItemStack stack : now) {
            filled += stack.isEmpty() ? 0 : 1;
        }
        for (int i = 0; i < now.length; i++) {
            ItemStack before = shown[i];
            ItemStack stack = now[i];
            if (ItemStack.isSameItemSameTags(before, stack) && before.getCount() == stack.getCount()) {
                continue;
            }
            boolean same = !before.isEmpty() && ItemStack.isSameItemSameTags(before, stack);
            shown[i] = stack.copy();
            if (!same) {
                leave(view, i);
                if (stack.isEmpty()) {
                    continue;
                }
                enter(view, i, stack, quiet, k, arriving, filled);
                if (!quiet) {
                    k++;
                }
            } else {
                more(view, i, stack, before.getCount(), quiet, k, arriving, filled);
                if (!quiet && stack.getCount() > before.getCount()) {
                    k++;
                }
            }
        }
    }

    /** Slot {@code slot} gave up what it held: mixed, it swells and goes; taken, it is gone at once. */
    private void leave(StationView view, int slot) {
        boolean mixing = clock - mixedAt < 0.6F || shownStirred > 0.8F;
        if (going[slot] != null) {
            going[slot].stop();
            going[slot] = null;
        }
        poured[slot] = 0.0F;
        flowed[slot] = false;
        for (int j = bodies.size() - 1; j >= 0; j--) {
            Body body = bodies.get(j);
            if (body.slot != slot) {
                continue;
            }
            if (mixing && body.rests()) {
                if (Float.isNaN(body.meltAge)) {
                    body.meltAge = -MELT_STAGGER * j;
                }
            } else {
                clear(view, body);
                bodies.remove(j);
            }
        }
        if (!mixing) {
            view.node(slotNodes[slot]).ifPresent(node -> node.scaleTo(0.0F, 0.0F));
        }
    }

    private void clear(StationView view, Body body) {
        view.node(body.node).ifPresent(node -> {
            node.setShape(null, null);
            node.setVisual(null);
            node.setOutlined(false);
            // A slot's own node stays where its piece was, as big as it: its box is in the item's pixels.
            node.scaleTo(body.copy == 0 ? SIZE / ITEM_PIXELS : 0.0F, 0.0F);
        });
    }

    private static String copyNode(int slot, int copy) {
        return "copy_" + slot + "_" + copy;
    }

    /**
     * How many pieces a stack shows, with {@code filled} slots holding something: a few of a stack, fewer in a full bowl; a block only
     * itself.
     */
    private static int pieces(ItemStack stack, int filled) {
        if (ItemVoxels.icon(stack).isEmpty()) {
            return 1;
        }
        int room = Math.max(1, MOST_PIECES / Math.max(1, filled));
        return Math.max(1, Math.min(Math.min(stack.getCount(), 1 + COPIES), room));
    }

    /** Something new in slot {@code slot}: thrown in, poured, cracked or shaken out, or there already when {@code quiet}. */
    private void enter(StationView view, int slot, ItemStack stack, boolean quiet, int k, int count, int filled) {
        Kind kind = kindOf(stack);
        view.node(slotNodes[slot]).ifPresent(node -> node.scaleTo(kind == Kind.PIECE || kind == Kind.HEAP ? 1.0F : 0.0F, 0.0F));
        switch (kind) {
            case POURED, CRACKED -> {
                liquidColors[slot] = kind == Kind.CRACKED ? 0xFFF2C14E : liquidLook(stack);
                // Unseen where it went in, as what was shaken out: found under an empty hand and taken back by a click.
                Body stand = new Body(slot, 0, slotNodes[slot], Kind.HEAP);
                stand.stands = true;
                stand.stack = stack.copy();
                float[] at = freePlace(stand);
                stand.x = at[0];
                stand.z = at[1];
                stand.yaw = random.nextFloat() * 90.0F;
                heapLook(view, stand, 1);
                bodies.add(stand);
                if (quiet) {
                    poured[slot] = volume(stack, stack.getCount());
                    flowed[slot] = true;
                } else {
                    stand.waitsForShake = true;
                    view.node(stand.node).ifPresent(node -> node.scaleTo(0.0F, 0.0F));
                    bringIn(view, slot, stack, k, count);
                }
            }
            case HEAP -> {
                Body heap = new Body(slot, 0, slotNodes[slot], Kind.HEAP);
                heap.stack = stack.copy();
                float[] at = freePlace(heap);
                heap.x = at[0];
                heap.z = at[1];
                heap.yaw = random.nextFloat() * 90.0F;
                heapLook(view, heap, stack.getCount());
                bodies.add(heap);
                if (!quiet) {
                    heap.waitsForShake = true;
                    view.node(heap.node).ifPresent(node -> node.scaleTo(0.0F, 0.0F));
                    bringIn(view, slot, stack, k, count);
                }
            }
            case PIECE -> {
                int pieces = pieces(stack, filled);
                for (int c = 0; c < pieces; c++) {
                    addPiece(view, slot, c, stack, quiet, k + c, count + pieces - 1);
                }
            }
        }
    }

    /** More of what slot {@code slot} holds, or less: more pieces fly in or go, another portion is poured, a heap grows or shrinks. */
    private void more(StationView view, int slot, ItemStack stack, int before, boolean quiet, int k, int count, int filled) {
        Kind kind = kindOf(stack);
        boolean grows = stack.getCount() > before;
        switch (kind) {
            case POURED, CRACKED -> {
                if (grows && !quiet) {
                    if (going[slot] == null) {
                        bringIn(view, slot, stack.copyWithCount(stack.getCount() - before), k, count);
                    } else {
                        poured[slot] = volume(stack, stack.getCount());
                        flowed[slot] = true;
                    }
                } else {
                    poured[slot] = volume(stack, stack.getCount());
                    flowed[slot] = true;
                }
            }
            case HEAP -> bodies.stream().filter(body -> body.slot == slot).findFirst().ifPresent(heap -> {
                heap.stack = stack.copy();
                heapLook(view, heap, stack.getCount());
                if (grows && !quiet && going[slot] == null) {
                    bringIn(view, slot, stack, k, count);
                }
            });
            case PIECE -> {
                int want = pieces(stack, filled);
                int have = (int) bodies.stream().filter(body -> body.slot == slot).count();
                for (int c = have; c < want; c++) {
                    addPiece(view, slot, c, stack, quiet, k + c - have, count + want - have - 1);
                }
                for (int j = bodies.size() - 1; j >= 0 && have > want; j--) {
                    Body body = bodies.get(j);
                    if (body.slot == slot && body.copy >= want) {
                        clear(view, body);
                        bodies.remove(j);
                        have--;
                    }
                }
            }
        }
    }

    /** The colour a poured liquid has in the bowl: water clear, anything else as its container shows it. */
    private static int liquidLook(ItemStack stack) {
        int color = KitchenLooks.liquidColor(stack);
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        boolean water = stack.is(Items.WATER_BUCKET) || stack.getItem() instanceof net.minecraft.world.item.PotionItem
                || blue > red + 50 && blue > green + 20;
        return water ? WATER : color;
    }

    /**
     * The colour of a heap: what shows of a powder in its bag or jar is its lightest part (the flour at a sack's mouth), so a powder
     * is mostly that; grain lies in its own colours.
     */
    private static int heapColor(ItemStack stack) {
        int main = 0xFF000000 | FoodColors.main(stack);
        Optional<ItemVoxels.Icon> icon = ItemVoxels.icon(stack);
        if (icon.isEmpty()) {
            return main;
        }
        int[] at = ItemVoxels.lightest(icon.get(), Optional.empty());
        int light = 0xFF000000 | icon.get().color(at[0], at[1]);
        return KitchenLooks.mix(main, light, KitchenTags.grains(stack) ? 0.4F : 0.75F);
    }

    /** The whisk rests with its loops here, scene pixels from the middle: things are laid elsewhere. */
    void rest(float x, float z) {
        restX = x;
        restZ = z;
    }

    /** What {@code count} of a liquid fill, pixels. */
    private static float volume(ItemStack stack, int count) {
        if (KitchenLooks.isCracked(stack)) {
            return EGGFUL * Math.min(count, 4);
        }
        boolean bucket = KitchenMotions.containerOf(stack).map(item -> item == Items.BUCKET).orElse(false);
        return (bucket ? BUCKETFUL : BOTTLEFUL) * Math.min(count, 3);
    }

    private void addPiece(StationView view, int slot, int copy, ItemStack stack, boolean quiet, int k, int count) {
        String node = copy == 0 ? slotNodes[slot] : copyNode(slot, copy);
        Body piece = new Body(slot, copy, node, Kind.PIECE);
        piece.stack = stack.copy();
        FoodProperties food = stack.getItem().getFoodProperties();
        piece.meat = food != null && food.isMeat() || stack.is(ItemTags.FISHES);
        Optional<ItemVoxels.Icon> icon = ItemVoxels.icon(stack);
        piece.blockLike = icon.isEmpty();
        piece.height = piece.blockLike ? SIZE * 0.5F : itemThickness();
        piece.radius = RADIUS * (piece.blockLike ? 1.1F : 1.0F);
        piece.reach = icon.map(BowlMix::reach).orElse(piece.radius);
        piece.bob = random.nextFloat() * Mth.TWO_PI;
        piece.yaw = random.nextFloat() * 360.0F;
        showItem(view, node, stack);
        float[] at = freePlace(piece);
        piece.x = at[0];
        piece.z = at[1];
        bodies.add(piece);
        if (!quiet) {
            arrive(view, piece, k, count);
        }
    }

    /**
     * How far an item's pixels reach from its middle at the piece's size, turned any way, pixels: a bone drawn corner to corner reaches
     * much further than its middle's width, and lying by a wall it would stick out through it.
     */
    private static float reach(ItemVoxels.Icon icon) {
        float most = 0.0F;
        for (int row = 0; row < icon.height(); row++) {
            for (int column = 0; column < icon.width(); column++) {
                if (icon.solid(column, row)) {
                    float dx = Math.abs(column + 0.5F - icon.width() / 2.0F) + 0.5F;
                    float dz = Math.abs(row + 0.5F - icon.height() / 2.0F) + 0.5F;
                    most = Math.max(most, (float) Math.hypot(dx, dz));
                }
            }
        }
        return most * SIZE / Math.max(1, icon.width()) + 0.1F;
    }

    /** A flat item lying, pixels: its one pixel of depth at the piece's size, and the little it stands proud of the surface. */
    private static float itemThickness() {
        return SIZE / 16.0F + 0.2F;
    }

    /** The node draws {@code stack} as the item it is, lit by the scene, in its own colours. */
    private static void showItem(StationView view, String node, ItemStack stack) {
        view.node(node).ifPresent(shown -> {
            shown.setShape(null, null);
            shown.setVisual(Visual.ofItem(stack.getItem()).withAnchor(ITEM_MIDDLE));
            shown.setTint(0xFFFFFFFF);
            shown.setVisible(true);
        });
    }

    /** Piece {@code piece} comes in: the {@code k}th of {@code count} from the hand, or dropped from the cursor when it is alone. */
    private void arrive(StationView view, Body piece, int k, int count) {
        Vector3f cursor = view.cursorOn(FLOOR).map(at -> at.sub(MIDDLE, 0.0F, MIDDLE)).orElse(new Vector3f(100.0F, 0.0F, 100.0F));
        boolean over = Math.abs(cursor.x) < HALF && Math.abs(cursor.z) < HALF;
        if (count > 1 || !over) {
            Vector3f from = FromPlayer.point(view, FLOOR + FLY_FROM, k, Math.max(count, 1))
                    .map(at -> at.sub(MIDDLE, 0.0F, MIDDLE)).orElse(new Vector3f(0.0F, FLOOR + FLY_FROM, -12.0F));
            piece.fromX = from.x;
            piece.fromY = from.y;
            piece.fromZ = from.z;
            piece.flyTime = FLY_TIME;
            piece.arc = FLY_ARC;
            piece.flyAge = -k * STAGGER;
        } else {
            float r = Math.max(piece.radius, piece.reach);
            piece.x = Mth.clamp(cursor.x + (random.nextFloat() - 0.5F), -HALF + r, HALF - r);
            piece.z = Mth.clamp(cursor.z + (random.nextFloat() - 0.5F), front(FLOOR, r), HALF - r);
            piece.fromX = piece.x;
            piece.fromY = FLOOR + DROP_FROM;
            piece.fromZ = piece.z;
            piece.flyTime = DROP_TIME;
            piece.arc = 0.0F;
            piece.flyAge = 0.0F;
        }
        piece.vx = 0.0F;
        piece.vz = 0.0F;
        piece.spin = (random.nextFloat() - 0.5F) * 2.0F * THROWN_SPIN;
        view.node(piece.node).ifPresent(node -> node.scaleTo(piece.flyAge < 0.0F ? 0.0F : 1.0F, 0.0F));
    }

    /**
     * A liquid, an egg or a heap's flour comes in by hand: a container brought over and poured out, an egg knocked on the rim and
     * opened, flour shaken out over where its heap will lie. From the cursor when it was let go over the bowl, else from the hand.
     */
    private void bringIn(StationView view, int slot, ItemStack stack, int k, int count) {
        NodeView hand = view.node("hand_" + slot).orElse(null);
        NodeView mixture = view.node("mixture").orElse(null);
        Kind kind = kindOf(stack);
        if (hand == null) {
            poured[slot] = kind == Kind.HEAP ? 0.0F : volume(stack, shown[slot].getCount());
            flowed[slot] = true;
            bodies.stream().filter(body -> body.slot == slot).forEach(body -> body.waitsForShake = false);
            return;
        }
        float height = RIM + 4.0F;
        Vector3f from = count > 1 ? FromPlayer.point(view, height, k, count).orElse(null) : view.cursorOn(height).orElse(null);
        if (from == null) {
            from = new Vector3f(MIDDLE, height, MIDDLE - 10.0F);
        }
        DoubleSupplier surface = this::surface;
        int total = shown[slot].getCount();
        Vector3f onto = new Vector3f(MIDDLE + POUR_X, 0.0F, MIDDLE + POUR_Z + 0.7F * (slot % 3 - 1));
        if (kind == Kind.CRACKED) {
            Vector3f rim = new Vector3f(MIDDLE - HALF + 0.4F, RIM, onto.z);
            NodeView inside = view.node("spill_" + slot).orElse(hand);
            going[slot] = new KitchenMotions.Crack(hand, inside, stack, from, rim, onto, surface, mixture, EGG_SIZE, EGG_INSIDE, true, 0.0F)
                    .onLanded(() -> {
                        poured[slot] = volume(stack, total);
                        flowed[slot] = true;
                        view.playSound(PotSounds.PLOP, "mixture", 0.3F, 1.25F);
                        landingSplash(view, onto.x - MIDDLE, onto.z - MIDDLE, 0xFFF2C14E);
                    });
        } else if (kind == Kind.POURED) {
            ItemStack empty = KitchenMotions.containerOf(stack).map(ItemStack::new).orElse(ItemStack.EMPTY);
            going[slot] = new KitchenMotions.Pour(hand, "pour_" + slot, stack, empty, liquidColors[slot], from, onto, surface, "mixture",
                    HAND_SIZE, -1.0F, 0.0F).lipAtLeast(RIM + 1.2F).onFlow(() -> {
                        poured[slot] = volume(stack, total);
                        flowed[slot] = true;
                    });
        } else {
            Body heap = bodies.stream().filter(body -> body.slot == slot).findFirst().orElse(null);
            Vector3f over = heap == null ? onto : new Vector3f(MIDDLE + heap.x, 0.0F, MIDDLE + heap.z);
            going[slot] = new KitchenMotions.Shake(hand, stack, from, over, surface, mixture, HAND_SIZE * 0.8F, -1.0F, 0.0F);
        }
    }

    /**
     * Flour or grain shaken out is in the mixture, not lying in it: nothing of it shows but the dust it went in with. Its sack or jar stays unseen where it was shaken out, a little bigger for more, so that an empty hand pointing
     * there finds it, sees it see-through and takes it back.
     */
    private void heapLook(StationView view, Body heap, int count) {
        heap.wide = Math.min(HEAP_LARGEST, 1.0F + HEAP_MORE * (Math.min(count, 8) - 1));
        heap.radius = RADIUS * heap.wide;
        heap.height = itemThickness();
        showItem(view, heap.node, heap.stack);
        view.node(heap.node).ifPresent(node -> node.setTint(UNSEEN));
    }

    /**
     * How far {@code place} is from where the resting whisk's handle hangs over the floor as the camera sees it: the handle rises from
     * the loops' foot across the middle to the far rim, and from the camera (in front, 63 degrees down) it covers the floor behind it.
     * Something there would be under the handle for a click.
     */
    private float handleRoom(float[] place) {
        float out = (float) Math.hypot(restX, restZ);
        if (out < 0.01F) {
            return Float.MAX_VALUE;
        }
        float x = -restX / out * HANDLE_ACROSS;
        float z = -restZ / out * HANDLE_ACROSS + HANDLE_BEHIND;
        return (float) Math.hypot(place[0] - x, place[1] - z) - WHISK_RADIUS;
    }

    /**
     * A piece under the resting whisk's handle, as the camera sees it, slides out from under it: there the handle, nearer the camera,
     * would take the click meant for the piece (and the cook would take the whisk instead).
     */
    private void clearOfHandle(Body piece, float h) {
        float out = (float) Math.hypot(restX, restZ);
        if (Float.isNaN(restX) || out < 0.01F) {
            return;
        }
        float dx = piece.x - (-restX / out * HANDLE_ACROSS);
        float dz = piece.z - (-restZ / out * HANDLE_ACROSS + HANDLE_BEHIND);
        float apart = (float) Math.hypot(dx, dz);
        float least = HANDLE_CLEAR + piece.radius * 0.5F;
        if (apart >= least) {
            return;
        }
        float nx = apart > 0.001F ? dx / apart : -restX / out;
        float nz = apart > 0.001F ? dz / apart : -restZ / out;
        float step = Math.min(least - apart, HANDLE_SLIDE * h);
        piece.x += nx * step;
        piece.z += nz * step;
    }

    /** Of the laying places, the one farthest from everything already in the bowl. */
    private float[] freePlace(Body body) {
        float[] best = PLACES[(body.slot * 3 + body.copy) % PLACES.length];
        float bestRoom = -1.0F;
        for (float[] place : PLACES) {
            float room = Float.isNaN(restX) ? Float.MAX_VALUE : Math.min((float) Math.hypot(place[0] - restX, place[1] - restZ) - WHISK_RADIUS,
                    handleRoom(place));
            for (Body other : bodies) {
                if (other != body && Float.isNaN(other.meltAge)) {
                    room = Math.min(room, (float) Math.hypot(place[0] - other.x, place[1] - other.z) - other.radius);
                }
            }
            if (room > bestRoom + 0.01F) {
                bestRoom = room;
                best = place;
            }
        }
        float jitter = 0.6F;
        float z = Math.max(best[1] + (random.nextFloat() - 0.5F) * jitter, front(FLOOR, RADIUS));
        return new float[] {best[0] + (random.nextFloat() - 0.5F) * jitter, z};
    }

    /**
     * One frame: the whisk as it goes, the bowlful {@code stirred} so far (0 to 1); its result waits ({@code waiting}) in the colour
     * {@code finished}; the local hand holds the whisk ({@code holding}).
     */
    void frame(StationView view, float dt, Whisk whisk, float stirred, boolean waiting, int finished, int becomes, boolean holding) {
        clock += dt;
        dt = Math.min(dt, 0.05F);
        if (dt < 1.0E-4F) {
            return;
        }
        waits = waiting;
        finalColor = finished;
        becomesColor = becomes & 0xFFFFFF;
        shownStirred = waiting ? 1.0F : stirred < shownStirred - 0.3F ? stirred : Mth.lerp(Math.min(1.0F, dt * 6.0F), shownStirred, stirred);
        motions(view, dt);
        mixture(view, dt, whisk);
        float visc = viscosity();
        for (int step = 0; step < 2; step++) {
            float h = dt / 2.0F;
            for (Body body : bodies) {
                if (body.rests()) {
                    if (body.kind == Kind.PIECE) {
                        slide(view, body, h, whisk, visc);
                    } else {
                        worked(view, body, h, whisk);
                    }
                }
            }
            shoulder();
        }
        layers(dt);
        for (int j = bodies.size() - 1; j >= 0; j--) {
            Body body = bodies.get(j);
            if (!draw(view, body, dt)) {
                clear(view, body);
                bodies.remove(j);
            }
        }
        whisking(view, dt, whisk, visc);
        outlines(view, holding);
    }

    /** The hands at work: a container poured out, an egg, flour shaken; flour that is out lies as its heap. */
    private void motions(StationView view, float dt) {
        for (int i = 0; i < going.length; i++) {
            if (going[i] != null && !going[i].frame(view, dt)) {
                going[i] = null;
                int slot = i;
                bodies.stream().filter(body -> body.slot == slot && body.waitsForShake).forEach(heap -> {
                    heap.waitsForShake = false;
                    heap.hopAge = 0.0F;
                    heap.hopHeight = 0.0F;
                    heap.hopTime = 0.25F;
                    Vector3f at = new Vector3f(MIDDLE + heap.x, FLOOR + 0.6F, MIDDLE + heap.z);
                    int color = heapColor(heap.stack);
                    // Flour lands in a soft cloud of itself.
                    view.spawnParticles(DiegesisParticles.DUST, at, new Vector3f(0.0F, 2.5F, 0.0F), HEAP_WIDE * 0.35F, 8, color);
                    view.playSound(SoundEvents.SAND_PLACE, "mixture", 0.22F, 1.3F);
                    if (level > 0.4F) {
                        view.node("mixture").ifPresent(mixture -> mixture.ripple(at.x, at.z, 0.35F));
                    }
                });
            }
        }
    }

    /**
     * The mixture: its level (what was poured, rising and thickening as it is whisked, full once mixed, scooped out once taken), its
     * colour, how long the whisk's furrows stay. It never turns round with the whisk: the whisk leaves its trail where it went.
     */
    private void mixture(StationView view, float dt, Whisk whisk) {
        float want = 0.0F;
        for (int i = 0; i < poured.length; i++) {
            want += flowed[i] ? poured[i] : 0.0F;
        }
        want = Math.min(MOST_LIQUID, want);
        liquid = want > liquid ? Math.min(want, liquid + POUR_RISE * dt) : Math.max(want, liquid - POUR_RISE * dt);
        boolean anything = liquid > 0.01F || bodies.stream().anyMatch(body -> Float.isNaN(body.meltAge));
        float target;
        if (waits) {
            target = DONE_LEVEL;
        } else if (!anything && clock - mixedAt > 1.0F) {
            target = 0.0F;
        } else {
            float worked = (float) Math.pow(KitchenMotions.smooth(shownStirred), 0.85F);
            target = liquid + (DONE_LEVEL - liquid) * worked;
        }
        float fall = target < level - 0.5F && !anything ? SCOOPED : 6.0F;
        level = target > level ? Mth.lerp(Math.min(1.0F, dt * 5.0F), level, target) : Math.max(target, level - fall * dt);
        int start = startColor();
        float s = KitchenMotions.smooth(shownStirred);
        int rgb = KitchenLooks.mix(start, finalColor, waits ? 1.0F : s) & 0xFFFFFF;
        int startAlpha = liquid > 0.05F ? milky(start) ? ALPHA_MILK : ALPHA_WATER : ALPHA_THICK;
        int alpha = Math.round(Mth.lerp(waits ? 1.0F : s, startAlpha, ALPHA_THICK));
        mixColor = alpha << 24 | rgb;
        float visc = viscosity();
        float wet = liquid > 0.05F ? 1.0F : 0.0F;
        // What was poured thickens into the mass as it is whisked: lumps of it form in the liquid and grow together.
        float thick = waits || wet == 0.0F ? 1.0F : KitchenMotions.smooth((shownStirred - THICK_FROM) / (THICK_FULL - THICK_FROM));
        thickNow = thick;
        dough(view);
        int film = mass.color(becomesColor) & 0xFFFFFF;
        int over = KitchenLooks.mix(mixColor, film, thick);
        boolean shows = level > 0.06F;
        float fraction = Mth.clamp(level / TANK_HEIGHT, 0.0F, 1.0F);
        int shownColor = over;
        if (lookedMass != mass || lookedColor != becomesColor) {
            lookedMass = mass;
            lookedColor = becomesColor;
            massLook = mass.look(becomesColor);
        }
        MassLook look = massLook;
        List<Floater> pieces = heldPieces(shows);
        int heldCount = waits ? mass.heldMost() : Math.round(Mth.clamp((s - HELD_FROM) / (HELD_ALL - HELD_FROM), 0.0F, 1.0F) * mass.heldMost());
        view.node("mixture").ifPresent(node -> {
            node.setVisible(shows);
            if (!shows) {
                return;
            }
            node.showLiquid(LIQUID, fraction);
            node.setLiquidColor(shownColor);
            node.setMass(look);
            node.setThickness(thick);
            // Plain: patches turned with the swirl showed as slanted squares.
            node.setMottle(0.0F);
            node.setWakeLook(Mth.lerp(s, WAKE_THIN, WAKE_THICK), Mth.lerp(s, WAKE_CONTRAST_THIN, WAKE_CONTRAST_THICK));
            node.setCurrent(0.0F);
            node.setAgitation(whisk.working() ? Math.min(0.5F, whisk.speed() / FULL_PACE * 0.5F) * (1.0F - 0.7F * visc) : 0.0F);
            node.setFloatingWake(0.4F * (1.0F - visc));
            // Side by side, not over each other: a bowl holds as many as fit.
            node.setFloatingClearance(PIECE_CLEARANCE);
            List<Float> obstacles = new ArrayList<>();
            obstacles.add(MIDDLE + whisk.x());
            obstacles.add(MIDDLE + whisk.z());
            obstacles.add(WHISK_RADIUS);
            for (Body body : bodies) {
                if (body.rests() && body.kind == Kind.PIECE) {
                    obstacles.add(MIDDLE + body.x);
                    obstacles.add(MIDDLE + body.z);
                    obstacles.add(body.radius);
                }
            }
            float[] flat = new float[obstacles.size()];
            for (int i = 0; i < flat.length; i++) {
                flat[i] = obstacles.get(i);
            }
            node.setFloatingObstacles(flat);
            // Nothing floats on the liquid (bits drifting on it looked out of place); the mass holds what went in as pieces.
            node.setFloating(pieces, Math.min(heldCount, pieces.size() * mass.heldEach()));
        });
    }

    /**
     * What the mass holds as pieces, one entry for each thing thrown in (twice for a porridge's oats, so more flakes than berries): only a
     * porridge, a salad or cat food (its meat and fish too) holds any. Kept while the bowlful waits, when its ingredients are used up; nothing once it is gone.
     */
    private List<Floater> heldPieces(boolean shows) {
        if (!shows || !mass.holdsPieces()) {
            held = List.of();
            heldChanged = true;
            return held;
        }
        if (!heldChanged) {
            return held;
        }
        heldChanged = false;
        List<Floater> now = new ArrayList<>();
        for (ItemStack stack : shown) {
            if (stack.isEmpty()) {
                continue;
            }
            Kind kind = kindOf(stack);
            if (kind == Kind.PIECE && (!isMeat(stack) || mass.holdsMeat())) {
                now.addAll(BowlMass.pieces(stack));
            } else if (kind == Kind.HEAP && KitchenTags.grains(stack)) {
                Floater flake = BowlMass.flake(stack);
                now.add(flake);
                now.add(flake);
            }
        }
        if (!now.isEmpty() && !now.equals(held)) {
            held = List.copyOf(now);
        }
        return held;
    }

    private static boolean isMeat(ItemStack stack) {
        FoodProperties food = stack.getItem().getFoodProperties();
        return food != null && food.isMeat();
    }

    /**
     * The finished bowlful's top, at the mixture's surface while its result waits: not drawn (the mixture is the bowlful), but outlined
     * under an empty hand and taken with a click.
     */
    private void dough(StationView view) {
        boolean shows = waits && level > 0.25F;
        float y = surface() - DOUGH_SHEET;
        view.node("dough").ifPresent(node -> {
            node.setVisible(shows);
            if (shows) {
                node.moveTo(0.0F, y, 0.0F, 0.0F);
                node.setTint(UNDRAWN);
            }
        });
    }

    /** What the bowlful turns into: the mixture becomes that mass from now on. */
    void mass(BowlMass kind) {
        heldChanged |= mass != kind;
        mass = kind;
    }


    /** The colour it starts from: what was poured, by how much of each; with nothing poured, what lies in it. */
    private int startColor() {
        float red = 0.0F;
        float green = 0.0F;
        float blue = 0.0F;
        float weight = 0.0F;
        for (int i = 0; i < poured.length; i++) {
            if (flowed[i] && poured[i] > 0.0F) {
                int color = liquidColors[i];
                red += (color >> 16 & 0xFF) * poured[i];
                green += (color >> 8 & 0xFF) * poured[i];
                blue += (color & 0xFF) * poured[i];
                weight += poured[i];
            }
        }
        if (weight > 0.0F) {
            return 0xFF000000 | Math.round(red / weight) << 16 | Math.round(green / weight) << 8 | Math.round(blue / weight);
        }
        List<ItemStack> solids = new ArrayList<>();
        for (Body body : bodies) {
            if (body.copy == 0) {
                solids.add(body.stack);
            }
        }
        return solids.isEmpty() ? finalColor : 0xFF000000 | FoodColors.mix(solids);
    }

    /** Whether a colour is pale enough to be milk or cream: it hides what is under it. */
    private static boolean milky(int color) {
        return (color >> 16 & 0xFF) > 200 && (color >> 8 & 0xFF) > 200 && (color & 0xFF) > 190;
    }


    /**
     * One step of a piece: on the dry floor it slides after a knock and stops, in a liquid it floats and goes with it, the whisk drawing
     * it along; the whisk knocks it, the walls stop it.
     */
    private void slide(StationView view, Body piece, float h, Whisk whisk, float visc) {
        boolean afloat = level > piece.height * 0.6F + 0.3F;
        // What the whisk pushed out comes back from the walls instead of lining them: a stirred liquid runs in again, and what is
        // tossed dry tumbles back off them while the whisk works.
        float out = (float) Math.hypot(piece.x, piece.z);
        float room = HALF - Math.max(piece.radius, piece.reach);
        float back = out > 0.001F && room > 0.0F
                ? RUN_BACK * Mth.clamp((out - room * RUN_BACK_FROM) / (room * (1.0F - RUN_BACK_FROM)), 0.0F, 1.0F) : 0.0F;
        float backX = back > 0.0F ? -piece.x / out * back : 0.0F;
        float backZ = back > 0.0F ? -piece.z / out * back : 0.0F;
        if (afloat) {
            float d = (float) Math.hypot(piece.x - whisk.x(), piece.z - whisk.z());
            float near = whisk.working() ? DRAWN_ALONG * (float) Math.exp(-(d * d) / (DRAWN_REACH * DRAWN_REACH)) : 0.0F;
            float flowX = whisk.vx() * near + backX;
            float flowZ = whisk.vz() * near + backZ;
            float take = 1.0F - (float) Math.exp(-h * Mth.lerp(visc, DRAG_THIN, DRAG_THICK));
            piece.vx += (flowX - piece.vx) * take;
            piece.vz += (flowZ - piece.vz) * take;
        } else {
            float speed = (float) Math.hypot(piece.vx, piece.vz);
            float keep = speed <= FRICTION * h ? 0.0F : (speed - FRICTION * h) / speed;
            piece.vx *= keep;
            piece.vz *= keep;
            if (whisk.working()) {
                piece.x += backX * DRY_BACK * h;
                piece.z += backZ * DRY_BACK * h;
            }
        }
        push(view, piece, whisk, afloat);
        if (!whisk.working()) {
            clearOfHandle(piece, h);
        }
        float speed = (float) Math.hypot(piece.vx, piece.vz);
        if (speed > FASTEST) {
            piece.vx *= FASTEST / speed;
            piece.vz *= FASTEST / speed;
        }
        piece.x += piece.vx * h;
        piece.z += piece.vz * h;
        walls(view, piece, afloat);
        piece.spin = Mth.clamp(piece.spin, -MOST_SPIN, MOST_SPIN) * (float) Math.pow(0.5F, h / SPIN_HALF_LIFE);
        piece.yaw += piece.spin * h;
    }

    /** The whisk's loops meet the piece: it is pushed out of them and knocked away by as much as they move into it. */
    private void push(StationView view, Body piece, Whisk whisk, boolean afloat) {
        float dx = piece.x - whisk.x();
        float dz = piece.z - whisk.z();
        float apart = (float) Math.hypot(dx, dz);
        float least = WHISK_RADIUS + piece.radius;
        if (apart >= least) {
            return;
        }
        float nx = apart > 0.001F ? dx / apart : Mth.cos(piece.bob);
        float nz = apart > 0.001F ? dz / apart : Mth.sin(piece.bob);
        piece.x += nx * (least - apart);
        piece.z += nz * (least - apart);
        float into = (piece.vx - whisk.vx()) * nx + (piece.vz - whisk.vz()) * nz;
        if (into >= 0.0F) {
            return;
        }
        float kick = -(1.0F + WHISK_KICK) * into;
        piece.vx += kick * nx;
        piece.vz += kick * nz;
        float rub = (piece.vx - whisk.vx()) * -nz + (piece.vz - whisk.vz()) * nx;
        piece.spin -= rub * SPIN_FROM_RUB;
        knocked(view, piece, -into, afloat, true);
    }

    /** The walls stop a piece: it gives back a little of its speed, dry more than wet, and turns as it rubs along them. */
    private void walls(StationView view, Body piece, boolean afloat) {
        float r = Math.max(piece.radius, piece.reach);
        float bounce = afloat ? BOUNCE_WET : BOUNCE_DRY;
        if (Math.abs(piece.x) > HALF - r) {
            float into = Math.abs(piece.vx);
            piece.x = Math.signum(piece.x) * (HALF - r);
            piece.spin += piece.vz * SPIN_FROM_RUB * Math.signum(piece.x);
            if (piece.vx * Math.signum(piece.x) > 0.0F) {
                piece.vx = -piece.vx * bounce;
                knocked(view, piece, into, afloat, false);
            }
        }
        float front = front(afloat ? surface() : FLOOR, r);
        if (piece.z > HALF - r || piece.z < front) {
            float side = piece.z > 0.0F ? 1.0F : -1.0F;
            float into = Math.abs(piece.vz);
            piece.z = side > 0.0F ? HALF - r : front;
            piece.spin -= piece.vx * SPIN_FROM_RUB * side;
            if (piece.vz * side > 0.0F) {
                piece.vz = -piece.vz * bounce;
                // Only the wall itself knocks: the edge of the hidden strip is no wall to hear.
                if (side > 0.0F || front <= -HALF + r + 0.01F) {
                    knocked(view, piece, into, afloat, false);
                }
            }
        }
    }

    /**
     * The nearest to the bowl's front wall (scene -z, the camera's side) that a thing {@code r} pixels across the middle may lie at
     * height {@code y}: out of the strip the wall hides from the camera, never past the back wall's room.
     */
    private static float front(float y, float r) {
        float hidden = Math.max(0.0F, (RIM - y) * FRONT_HIDES);
        float least = -HALF + Math.max(r, hidden + FRONT_SEEN);
        return Math.min(least, HALF - r - 0.5F);
    }

    /** A piece knocked at {@code into} pixels a second: heard on the dry wood, rippling a liquid; hard enough, it may turn over. */
    private void knocked(StationView view, Body piece, float into, boolean afloat, boolean byWhisk) {
        if (into < KNOCK_FROM || piece.knocked > clock) {
            return;
        }
        piece.knocked = clock + KNOCK_GAP;
        if (afloat) {
            view.node("mixture").ifPresent(node -> node.ripple(MIDDLE + piece.x, MIDDLE + piece.z, Math.min(0.3F, into * 0.012F)));
        } else {
            float volume = Math.min(0.22F, 0.05F + into * 0.006F);
            view.playSound(piece.meat ? SoundEvents.SLIME_SQUISH_SMALL : SoundEvents.WOOD_HIT, piece.node, volume,
                    (piece.meat ? 1.3F : 1.5F) + random.nextFloat() * 0.2F);
            if (byWhisk) {
                view.playSound(SoundEvents.CHAIN_HIT, piece.node, volume * 0.5F, 1.8F + random.nextFloat() * 0.2F);
            }
        }
        if (into >= TURN_FROM && Float.isNaN(piece.turnAge) && random.nextFloat() < (afloat ? TURN_CHANCE * 0.5F : TURN_CHANCE)) {
            piece.turnAge = 0.0F;
        }
    }

    /** Pieces do not lie in each other: they push apart and trade their speed; a heap is something they go round. */
    private void shoulder() {
        for (int i = 0; i < bodies.size(); i++) {
            Body a = bodies.get(i);
            if (!a.rests()) {
                continue;
            }
            for (int j = i + 1; j < bodies.size(); j++) {
                Body b = bodies.get(j);
                if (!b.rests() || a.kind != Kind.PIECE && b.kind != Kind.PIECE || a.stands || b.stands) {
                    continue;
                }
                float dx = b.x - a.x;
                float dz = b.z - a.z;
                float apart = (float) Math.hypot(dx, dz);
                float least = (a.kind == Kind.HEAP ? a.radius * 0.7F : a.radius) + (b.kind == Kind.HEAP ? b.radius * 0.7F : b.radius);
                if (apart >= least) {
                    continue;
                }
                float nx = apart > 0.001F ? dx / apart : Mth.cos(i + j);
                float nz = apart > 0.001F ? dz / apart : Mth.sin(i + j);
                float push = least - apart;
                float aShare = a.kind == Kind.HEAP ? 0.0F : b.kind == Kind.HEAP ? 1.0F : 0.5F;
                a.x -= nx * push * aShare;
                a.z -= nz * push * aShare;
                b.x += nx * push * (1.0F - aShare);
                b.z += nz * push * (1.0F - aShare);
                float closing = (b.vx - a.vx) * nx + (b.vz - a.vz) * nz;
                if (closing < 0.0F && a.kind == Kind.PIECE && b.kind == Kind.PIECE) {
                    float impulse = -1.2F * closing / 2.0F;
                    a.vx -= impulse * nx;
                    a.vz -= impulse * nz;
                    b.vx += impulse * nx;
                    b.vz += impulse * nz;
                    float rub = (b.vx - a.vx) * -nz + (b.vz - a.vz) * nx;
                    a.spin -= rub * SPIN_FROM_RUB * 0.5F;
                    b.spin += rub * SPIN_FROM_RUB * 0.5F;
                }
                for (Body body : new Body[] {a, b}) {
                    float r = Math.max(body.radius, body.reach);
                    body.x = Mth.clamp(body.x, -HALF + r, HALF - r);
                    body.z = Mth.clamp(body.z, front(level > body.height * 0.6F + 0.3F ? surface() : FLOOR, r), HALF - r);
                }
            }
        }
    }

    /** The whisk goes through a heap: it is worked in, faster the faster the whisk, and a dry heap puffs its dust. */
    private void worked(StationView view, Body heap, float h, Whisk whisk) {
        if (!whisk.working()) {
            return;
        }
        float d = (float) Math.hypot(heap.x - whisk.x(), heap.z - whisk.z());
        if (d > WHISK_RADIUS + heap.radius) {
            return;
        }
        float pace = Math.min(1.0F, whisk.speed() / FULL_PACE);
        heap.left = Math.max(0.2F, heap.left - h * HEAP_WORKED * pace);
        if (!heap.stands && level < 0.5F && pace > 0.2F && random.nextFloat() < h * 12.0F * pace) {
            int color = heapColor(heap.stack);
            view.spawnParticles(DiegesisParticles.DUST, new Vector3f(MIDDLE + heap.x, FLOOR + 0.8F, MIDDLE + heap.z),
                    new Vector3f(whisk.vx() * 0.05F, 2.0F, whisk.vz() * 0.05F), HEAP_WIDE * 0.3F, 2, color);
        }
    }

    /**
     * Things never lie in one plane where they overlap, or their faces fight: each lies on those that came before it and reach under
     * it, an item's depth higher for each, rising and settling smoothly as they slide over and off each other.
     */
    private void layers(float dt) {
        float settle = Math.min(1.0F, dt * LAYER_SETTLE);
        for (int j = 0; j < bodies.size(); j++) {
            Body upper = bodies.get(j);
            int under = 0;
            for (int i = 0; i < j; i++) {
                Body lower = bodies.get(i);
                if (Float.isNaN(lower.meltAge) && !lower.waitsForShake && !lower.stands
                        && Math.hypot(upper.x - lower.x, upper.z - lower.z) < SIZE * Math.max(upper.wide, lower.wide)) {
                    under++;
                }
            }
            upper.lift += (under * LAYER - upper.lift) * settle;
        }
    }

    /** Puts a body where it is now: in the air, landing, lying, turning over, going at the end; coated and shrunk by now. */
    private boolean draw(StationView view, Body body, float dt) {
        NodeView node = view.node(body.node).orElse(null);
        if (node == null) {
            return true;
        }
        float s = KitchenMotions.smooth(shownStirred);
        if (body.kind == Kind.HEAP) {
            if (body.waitsForShake) {
                return true;
            }
            float grow = 1.0F;
            if (!Float.isNaN(body.hopAge)) {
                body.hopAge += dt;
                grow = KitchenMotions.out(body.hopAge / body.hopTime);
                if (body.hopAge >= body.hopTime) {
                    body.hopAge = Float.NaN;
                }
            }
            float melt = 1.0F;
            if (!Float.isNaN(body.meltAge)) {
                body.meltAge += dt;
                melt = 1.0F - Mth.clamp(body.meltAge / GONE_TIME, 0.0F, 1.0F);
                if (body.meltAge >= GONE_TIME) {
                    return false;
                }
            }
            float left = (float) Math.sqrt(Math.max(0.0F, body.left * (1.0F - 0.8F * s)));
            float size = Math.max(0.02F, grow * left * melt) * body.wide;
            boolean afloat = level > body.height * 0.6F + 0.3F;
            float y = afloat ? Math.max(FLOOR, surface() - body.height * (0.45F + 0.4F * s)) : FLOOR;
            // Shown to the hand, it rises over what lies on it, under the rim: what is seen there is what a click takes.
            float wantRise = body.seen ? Math.max(0.0F, RIM - SEEN_UNDER_RIM - y) : 0.0F;
            body.rise = Mth.lerp(Math.min(1.0F, dt * RISE_RATE), body.rise, wantRise);
            node.moveTo(body.x, y + body.height * 0.5F + body.lift + body.rise, body.z, 0.0F);
            node.rotateTo(-90.0F, body.yaw, 0.0F, 0.0F);
            node.scaleTo(size * SIZE / ITEM_PIXELS, 0.0F);
            return true;
        }
        float px = body.x;
        float pz = body.z;
        boolean afloat = level > body.height * 0.6F + 0.3F;
        float py = afloat ? Math.max(FLOOR, surface() - body.height * (0.45F + 0.4F * s)) : FLOOR;
        if (afloat && Float.isNaN(body.flyAge)) {
            py += 0.08F * Mth.sin(clock * 3.0F + body.bob);
        }
        float pitch = body.over;
        float scale = 1.0F - DISSOLVE * s;
        if (!Float.isNaN(body.flyAge)) {
            float before = body.flyAge;
            body.flyAge += dt;
            if (body.flyAge < 0.0F) {
                node.scaleTo(0.0F, 0.0F);
                return true;
            }
            if (before < 0.0F) {
                node.scaleTo(1.0F, 0.0F);
            }
            float t = Math.min(1.0F, body.flyAge / body.flyTime);
            float eased = body.arc > 0.0F ? t * t * (3.0F - 2.0F * t) : t;
            px = Mth.lerp(eased, body.fromX, body.x);
            pz = Mth.lerp(eased, body.fromZ, body.z);
            float fall = body.arc > 0.0F ? t : t * t;
            py = Mth.lerp(fall, body.fromY, py) + body.arc * Mth.sin(t * Mth.PI);
            pitch += (1.0F - eased) * (body.arc > 0.0F ? 360.0F : 90.0F);
            body.yaw += body.spin * dt;
            if (t >= 1.0F) {
                body.flyAge = Float.NaN;
                body.vx = (body.x - body.fromX) / body.flyTime * SKID;
                body.vz = (body.z - body.fromZ) / body.flyTime * SKID;
                body.spin *= 0.4F;
                hop(body, afloat ? 0.2F : BOUNCE_HOP, BOUNCE_TIME);
                landed(view, body, afloat);
            }
        } else if (!Float.isNaN(body.meltAge)) {
            body.meltAge += dt;
            float age = body.meltAge;
            if (age >= SWELL_TIME + GONE_TIME) {
                Vector3f puff = new Vector3f(MIDDLE + px, py + 0.5F, MIDDLE + pz);
                view.spawnParticles(DiegesisParticles.DUST, puff, new Vector3f(0.0F, 2.5F, 0.0F), 0.6F, 3, mixColor | 0xFF000000);
                view.spawnParticles(DiegesisParticles.GLINT, puff, new Vector3f(0.0F, 2.5F, 0.0F), 0.8F, 2, GLINT_COLOR);
                return false;
            }
            float base = scale;
            scale = age < 0.0F ? base : age < SWELL_TIME ? Mth.lerp(age / SWELL_TIME, base, base * SWELL)
                    : base * SWELL * Mth.square(1.0F - (age - SWELL_TIME) / GONE_TIME);
        }
        if (!Float.isNaN(body.turnAge)) {
            body.turnAge += dt;
            float t = Math.min(1.0F, body.turnAge / TURN_TIME);
            pitch += 180.0F * t * t * (3.0F - 2.0F * t);
            py += TURN_HOP * Mth.sin(t * Mth.PI);
            if (t >= 1.0F) {
                body.turnAge = Float.NaN;
                body.over = (body.over + 180.0F) % 360.0F;
                pitch = body.over;
                landed(view, body, afloat);
            }
        }
        if (!Float.isNaN(body.hopAge)) {
            body.hopAge += dt;
            float t = body.hopAge / body.hopTime;
            if (t >= 1.0F) {
                body.hopAge = Float.NaN;
            } else {
                py += body.hopHeight * Mth.sin(t * Mth.PI);
            }
        }
        tint(node, body, s);
        // The item is drawn about its middle: it lies with its underside on what it lies on, and turns over about its middle.
        node.moveTo(px, py + body.height * scale * 0.5F + body.lift, pz, 0.0F);
        node.rotateTo(body.blockLike ? pitch : pitch - 90.0F, body.yaw, 0.0F, 0.0F);
        node.scaleTo(scale * SIZE / ITEM_PIXELS, 0.0F);
        return true;
    }

    /** As it is whisked a thing is coated in the mixture and takes its colour. */
    private void tint(NodeView node, Body body, float stirred) {
        float coat = COATED * stirred;
        if (Math.abs(coat - body.lastTint) > 0.01F) {
            body.lastTint = coat;
            node.setTint(KitchenLooks.mix(0xFFFFFFFF, 0xFF000000 | mixColor, coat));
        }
    }

    private static void hop(Body body, float height, float time) {
        body.hopAge = 0.0F;
        body.hopHeight = height;
        body.hopTime = time;
    }

    /** A piece lands: on the dry wood with a soft knock and a little dust of its colour, in the mixture with a plop and a splash. */
    private void landed(StationView view, Body piece, boolean afloat) {
        int color = 0xFF000000 | FoodColors.main(piece.stack);
        if (afloat) {
            view.playSound(PotSounds.PLOP, piece.node, 0.32F, 1.05F + 0.05F * piece.slot + random.nextFloat() * 0.1F);
            landingSplash(view, piece.x, piece.z, mixColor | 0xFF000000);
        } else {
            view.playSound(piece.meat ? SoundEvents.SLIME_SQUISH_SMALL : SoundEvents.WOOD_PLACE, piece.node, 0.2F,
                    1.3F + 0.04F * piece.slot + random.nextFloat() * 0.1F);
            view.spawnParticles(DiegesisParticles.DUST, new Vector3f(MIDDLE + piece.x, FLOOR + 0.3F, MIDDLE + piece.z),
                    new Vector3f(0.0F, 1.2F, 0.0F), 0.8F, 3, KitchenLooks.mix(color, 0xFFFFFFFF, 0.3F));
        }
    }

    /** Something drops into the mixture at {@code x, z}: rings and a splash of its colour. */
    private void landingSplash(StationView view, float x, float z, int color) {
        if (level < 0.15F) {
            return;
        }
        view.node("mixture").ifPresent(node -> node.ripple(MIDDLE + x, MIDDLE + z, 0.45F));
        view.spawnParticles(DiegesisParticles.SPLASH, new Vector3f(MIDDLE + x, surface(), MIDDLE + z), new Vector3f(0.0F, 5.0F, 0.0F), 0.5F,
                4, color);
    }

    /**
     * The whisk at work in the mixture: it leaves its trail and splashes at speed (no bubbles in any dish), a thick
     * batter squelches under it.
     */
    private void whisking(StationView view, float dt, Whisk whisk, float visc) {
        if (!whisk.working() || level < 0.3F) {
            return;
        }
        float speed = whisk.speed();
        float pace = Math.min(1.0F, speed / FULL_PACE);
        float wx = MIDDLE + whisk.x();
        float wz = MIDDLE + whisk.z();
        if (speed > 1.5F) {
            // A thin liquid closes behind a slow whisk; a thick mass is pushed aside however slowly the whisk goes through it.
            float dig = WAKE_DEPTH * Mth.lerp(visc, Math.min(1.0F, speed / WAKE_FULL), 1.0F);
            view.node("mixture").ifPresent(node -> node.leaveWake(wx, wz, WAKE_WIDTH, dig));
        }
        waves -= dt;
        if (visc < WAVES_UNTIL && speed > WAVES_FROM && waves <= 0.0F) {
            // A thin liquid gives way to the whisk and runs in waves round it; a batter only keeps the furrows.
            waves = WAVE_GAP;
            float strength = Math.min(0.35F, speed * 0.008F) * (1.0F - visc / WAVES_UNTIL);
            view.node("mixture").ifPresent(node -> node.ripple(wx, wz, strength));
        }
        int color = mixColor | 0xFF000000;
        if (speed > SPLASH_FROM) {
            // A mass does not splash: only what is still liquid of it.
            splash += dt * SPLASH_RATE * (speed - SPLASH_FROM) / (FULL_PACE - SPLASH_FROM) * (1.0F - 0.6F * visc) * (1.0F - thickNow);
            while (splash >= 1.0F) {
                splash -= 1.0F;
                view.spawnParticles(DiegesisParticles.SPLASH, new Vector3f(wx, surface(), wz),
                        new Vector3f(whisk.vx() * 0.1F, 4.0F + random.nextFloat() * 3.0F, whisk.vz() * 0.1F), 0.8F, 1, color);
            }
        }
        squelch -= dt;
        if (visc > 0.45F && pace > 0.3F && squelch <= 0.0F) {
            squelch = SQUELCH_GAP + random.nextFloat() * 0.25F;
            view.playSound(SoundEvents.HONEY_BLOCK_STEP, "mixture", 0.08F + 0.1F * pace * visc, 0.9F + random.nextFloat() * 0.2F);
        }
    }

    /**
     * The whisk hit the wall toward {@code nx, nz} at {@code speed} pixels a second with its loops at {@code x, z}: a soft knock of wire on
     * wood; going fast in a liquid it flicks drops of it up (not from a mass); in a dry bowl a little dust.
     */
    void knock(StationView view, float x, float z, float nx, float nz, float speed) {
        view.playSound(SoundEvents.WOOD_HIT, "mixture", Math.min(0.18F, 0.04F + speed * 0.003F), 1.6F + random.nextFloat() * 0.2F);
        if (speed < FLICK_FROM) {
            return;
        }
        float wallX = nx != 0.0F ? nx * (HALF - 0.2F) : x;
        float wallZ = nz != 0.0F ? nz * (HALF - 0.2F) : z;
        if (level < 0.4F) {
            if (bodies.stream().anyMatch(body -> body.kind == Kind.HEAP && !body.stands)) {
                view.spawnParticles(DiegesisParticles.DUST, new Vector3f(MIDDLE + wallX, FLOOR + 1.0F, MIDDLE + wallZ),
                        new Vector3f(-nx * 1.5F, 2.0F, -nz * 1.5F), 0.6F, 2, 0xFFEDE6D6);
            }
            return;
        }
        if (thickNow > 0.5F) {
            // A mass is not thrown over the rim.
            return;
        }
        int color = mixColor | 0xFF000000;
        int drops = 2 + random.nextInt(3);
        for (int k = 0; k < drops; k++) {
            view.spawnParticles(DiegesisParticles.DROPLET, new Vector3f(MIDDLE + wallX, surface() + 0.2F, MIDDLE + wallZ),
                    new Vector3f(nx * (3.0F + random.nextFloat() * 4.0F) + (random.nextFloat() - 0.5F) * 3.0F, 9.0F + random.nextFloat() * 6.0F,
                            nz * (3.0F + random.nextFloat() * 4.0F) + (random.nextFloat() - 0.5F) * 3.0F), 0.2F, 1, color);
        }
    }

    /**
     * The outline goes round what is under an empty hand, as round every other thing that can be taken, and round every piece of the same
     * ingredient with it: a click on any of them takes that ingredient. Not while the hand whisks.
     */
    private void outlines(StationView view, boolean holding) {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean free = view.isFocused() && !holding && player != null && player.inventoryMenu.getCarried().isEmpty();
        boolean over = free && view.cursorOn(FLOOR + 2.0F).map(at -> Math.abs(at.x - MIDDLE) < HALF + OVER_BOWL
                && Math.abs(at.z - MIDDLE) < HALF + OVER_BOWL).orElse(false);
        for (Body body : bodies) {
            if (body.stands && body.waitsForShake && flowed[body.slot]) {
                // Poured or cracked in: from now on it can be found where it went in.
                body.waitsForShake = false;
            }
        }
        boolean carrying = player != null && !player.inventoryMenu.getCarried().isEmpty();
        for (int slot = 0; slot < slotNodes.length; slot++) {
            // An empty slot has nothing for an empty hand to take: its node must not catch a click meant for what lies under it (the
            // finished bowlful). While something is carried it stays: a click takes on the press and decides on the release, which
            // must land on the slot it took from, or what was taken is thrown straight back in.
            boolean usable = carrying || !shown[slot].isEmpty();
            view.node(slotNodes[slot]).ifPresent(node -> node.setEnabled(usable));
        }
        boolean[] pointed = new boolean[shown.length];
        for (Body body : bodies) {
            if (free && body.rests() && view.node(body.node).map(NodeView::isHovered).orElse(false)) {
                pointed[body.slot] = true;
            }
        }
        for (Body body : bodies) {
            boolean lit = pointed[body.slot] && body.rests();
            view.node(body.node).ifPresent(node -> {
                node.setOutlined(lit);
                if (body.kind == Kind.HEAP) {
                    boolean seen = lit || over && body.rests();
                    body.seen = seen;
                    node.setTint(lit ? FOUND : seen ? SHOWN : UNSEEN);
                    // Seen over everything while the hand looks for it: a milky mixture or the whisk would hide it again.
                    node.setOnTop(seen);
                }
            });
        }
    }

    /** A liquid was drawn back: rings where the container dipped. */
    void drawn(StationView view) {
        view.node("mixture").ifPresent(node -> node.ripple(MIDDLE + POUR_X, MIDDLE + POUR_Z, 0.5F));
        view.spawnParticles(DiegesisParticles.SPLASH, new Vector3f(MIDDLE + POUR_X, surface(), MIDDLE + POUR_Z), new Vector3f(0.0F, 5.0F, 0.0F),
                0.6F, 4, mixColor | 0xFF000000);
    }
}
