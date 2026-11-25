package com.pockyl.handson_letsdo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.scene.Visual;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What lies in the roaster's pan and how it behaves: each thing as a thick piece cut from its icon, lying on the floor, browning
 * towards its cooked colour and shrinking a little as meat does; slid about by the pan as the cook swirls it (it lags behind the
 * pan's moves, slides on the oil, knocks against the walls, turns over now and then); landing with a hiss; the fat it lets out, slowly:
 * flat puddles under it first, left behind where it slid, then a layer of oil over the floor that its trails run through; oil
 * seething round it, fat spitting off it, steam, a little smoke as it gets brown, the air over the hot pan shimmering. At the end each piece swells and is gone, and the roast lies in the
 * pan glistening. Pan pixels from the middle of its floor; the pan's own node carries them to the scene.
 */
final class RoasterPan {
    /** The pan's inside: half its width across the handles and half its depth; the floor's top. */
    private static final float HALF_X = 5.0F;
    private static final float HALF_Z = 4.0F;
    private static final float FLOOR = 1.0F;
    /** A piece: pixels across, how far round its middle it keeps others and the walls off, how thick it is in icon pixels. */
    private static final float SIZE = 3.8F;
    private static final float RADIUS = 1.45F;
    private static final float THICK_MEAT = 2.0F;
    private static final float THICK = 1.5F;
    /** The roast that is done, as it lies in the pan. */
    private static final float ROAST_SIZE = 6.4F;
    private static final float ROAST_THICK = 2.0F;

    /**
     * Sliding: the pull (pixels a second squared), how hard the floor holds a piece, dry and in fat (shares of the pull), how much the
     * pan's own moves throw what lies in it, how much a wall gives back, the fastest a piece slides.
     */
    private static final float GRAVITY = 157.0F;
    private static final float GRIP_DRY = 0.3F;
    private static final float GRIP_FAT = 0.11F;
    private static final float INERTIA = 1.8F;
    private static final float BOUNCE = 0.3F;
    private static final float FASTEST = 30.0F;
    /** How quickly the pan's acceleration is followed, seconds; how fast a piece spinning by itself slows (half life, seconds). */
    private static final float ACCELERATION_EASE = 0.04F;
    /** The hardest the pan throws, pixels a second squared: a jump of the hand between two frames is no throw. */
    private static final float MOST_THROW = 400.0F;
    private static final float SPIN_HALF_LIFE = 0.35F;
    /** Degrees a second of spin for each pixel a second a piece rubs along a wall or another piece. */
    private static final float SPIN_FROM_RUB = 14.0F;
    /** A knock on a wall: from what speed it shows, from what speed it spits and pops, from what speed it may turn a piece over. */
    private static final float KNOCK_FROM = 5.0F;
    private static final float KNOCK_LOUD = 10.0F;
    private static final float TURN_FROM = 14.0F;
    private static final float TURN_CHANCE = 0.4F;
    private static final float TURN_TIME = 0.45F;
    private static final float TURN_HOP = 1.4F;
    private static final float KNOCK_GAP = 0.15F;

    /** Coming in: seconds in the air, from the hand at the bottom of the screen and dropped from the cursor; the stagger between several. */
    private static final float FLY_TIME = 0.42F;
    private static final float DROP_TIME = 0.26F;
    private static final float DROP_FROM = 5.0F;
    private static final float FLY_FROM = 9.0F;
    private static final float FLY_ARC = 4.0F;
    private static final float STAGGER = 0.16F;
    /** What is left of the speed it came with, as it skids on landing; the bounce after it, pixels and seconds. */
    private static final float SKID = 0.25F;
    private static final float BOUNCE_HOP = 0.45F;
    private static final float BOUNCE_TIME = 0.18F;

    /** Browning: what roasts goes towards its cooked colours, meat with a sear; food without a cooked form only goes golden. */
    private static final int SEARED = 0xFFB8875C;
    private static final float SEAR = 0.7F;
    private static final int GOLDEN = 0xFFC08A55;
    private static final float GOLDEN_MOST = 0.4F;
    /** Meat and fish shrink this much by the time they are done. */
    private static final float SHRINK = 0.1F;

    /** Done: each piece swells for so long, then shrinks away; they go one after the other. */
    private static final float SWELL_TIME = 0.18F;
    private static final float GONE_TIME = 0.5F;
    private static final float SWELL = 1.25F;
    private static final float MELT_STAGGER = 0.12F;
    /** The roast that waits: it breathes this much every so many seconds, with glints at its fullest. */
    private static final float READY_SWELL = 0.04F;
    private static final float READY_BEAT = 1.3F;

    /**
     * Oil comes slowly, as on Hands-On Delight's skillet: first only flat fat under each thing (its puddles), then, from this far
     * through the roast to that far, a layer over the whole floor wells up out of them; a roast that is done lies in a full layer.
     * An empty pan dries in so many seconds.
     */
    private static final float LAYER_FROM = 0.3F;
    private static final float LAYER_FULL = 0.85F;
    private static final float OIL_DRY = 4.0F;
    /** How clear the layer is at most: the pan shows through it. */
    private static final int OIL_ALPHA = 0x9C;
    /**
     * The oil as the pot's liquid shows it (its tank of the station is unused at a roaster): the liquid it is drawn as, in this
     * colour; how deep a full layer stands, of the pan's inside (5 px); how patchy it looks, how long trails stay on it and how
     * clearly; how hard it seethes along the walls while it roasts and while it only stands hot.
     */
    private static final ResourceLocation OIL = new ResourceLocation("minecraft", "water");
    private static final int OIL_RGB = 0xE8A93A;
    private static final float OIL_DEPTH = 0.04F;
    private static final float OIL_MOTTLE = 0.45F;
    private static final float OIL_WAKE_LIFE = 1.6F;
    private static final float OIL_WAKE_CONTRAST = 0.6F;
    private static final float OIL_SEETHE = 0.7F;
    private static final float OIL_SEETHE_HOT = 0.3F;
    /**
     * Puddles: seconds for one under a piece to run out whole; a piece that slid off its puddle leaves it there and starts another
     * once it is this far (pixels) from its middle, with this share of the old one (fat smeared along); a puddle left behind dries
     * in so many seconds while the pan is hot (the layer takes it over), a cold one in so many. How big a puddle is, of a piece,
     * at first and by the time the roast is done; how many there can be.
     */
    private static final float PUDDLE_GROW = 6.0F;
    private static final float PUDDLE_LEAVE = 1.1F;
    private static final float PUDDLE_SMEAR = 0.45F;
    private static final float PUDDLE_FADE = 10.0F;
    private static final float PUDDLE_DRY = 3.0F;
    private static final float PUDDLE_SIZE = 1.3F;
    private static final float PUDDLE_SPREAD = 0.6F;
    private static final int PUDDLES = 32;
    /** A trail through the oil: from what speed a piece leaves one, at what speed it is clearest (pixels a second). */
    private static final float WAKE_FROM = 0.5F;
    private static final float WAKE_FULL = 12.0F;

    /** Particles a second: seething oil, spitting fat, steam, smoke of a roast getting brown, shimmer over the pan. */
    private static final float SEETHE_RATE = 12.0F;
    private static final float SPIT_ROASTING = 4.5F;
    private static final float SPIT_HOT = 0.8F;
    private static final float VAPOUR_RATE = 1.4F;
    private static final float SMOKE_RATE = 1.2F;
    private static final float SMOKE_FROM = 0.6F;
    private static final float SHIMMER_RATE = 1.2F;
    private static final float SHIMMER_EMPTY_RATE = 2.4F;
    private static final float WISP_RATE = 1.4F;
    /** A swirl through hot fat is heard: from how much sliding (pixels a second, all pieces), how often at most. */
    private static final float SLIDE_HEARD = 8.0F;
    private static final float SLIDE_SIZZLE_GAP = 0.7F;
    /** Now and then a piece jumps a hair in the hot fat: how often a second, how high, how long. */
    private static final float TWITCH_RATE = 0.25F;
    private static final float TWITCH_HOP = 0.2F;
    private static final float TWITCH_TIME = 0.15F;

    private static final int FAT_COLOR = 0xFFFFE7A8;
    private static final int GLINT_COLOR = 0xFFFFE9A0;
    private static final int STEAM_COLOR = 0x40FFFFFF;
    private static final int UNSEEN = 0x00FFFFFF;
    private static final Vector3f SEETHE_UP = new Vector3f(0.0F, 0.6F, 0.0F);

    /** The free places a piece is laid on, a hand's way: two rows of three. */
    private static final float[][] PLACES = {{-3.1F, 1.9F}, {0.1F, 2.0F}, {3.1F, 1.8F}, {-3.0F, -1.9F}, {0.0F, -2.0F}, {3.2F, -1.9F}};

    private static final Map<Item, ItemStack> COOKED = new HashMap<>();

    private final String[] nodes;
    private final String[] looks;
    private final int slots;
    private final PanFat fat = new PanFat();
    private final RandomSource random = RandomSource.create();

    private final Item[] item;
    private final ItemStack[] raw;
    private final boolean[] meat;
    private final boolean[] blockLike;
    private final float[] height;
    private final float[] x;
    private final float[] z;
    private final float[] vx;
    private final float[] vz;
    private final float[] yaw;
    private final float[] spin;
    private final float[] over;
    private final float[] turnAge;
    private final float[] hopAge;
    private final float[] hopHeight;
    private final float[] hopTime;
    private final float[] flyAge;
    private final float[] flyTime;
    private final float[] fromX;
    private final float[] fromY;
    private final float[] fromZ;
    private final float[] arc;
    private final float[] meltAge;
    /** The puddles: where, how far run out (0 to 1), which piece makes it (-1: none any more). */
    private final float[] puddleX = new float[PUDDLES];
    private final float[] puddleZ = new float[PUDDLES];
    private final float[] puddle = new float[PUDDLES];
    private final int[] puddleOf = new int[PUDDLES];
    private final float[] knocked;
    private final float[] lastTint;

    private float lastPanX = Float.NaN;
    private float lastPanZ;
    private float panVX;
    private float panVZ;
    private float accX;
    private float accZ;
    private float pool;
    private float clock;
    private float seethe;
    private float spit;
    private float vapour;
    private float smoke;
    private float shimmer;
    private float wisps;
    private float slideSizzle;
    private Item roast;
    private boolean roastShape;
    private float roastAge = Float.NaN;
    private float readyClock;
    private boolean painted;

    RoasterPan(String[] nodes) {
        this.nodes = nodes;
        this.slots = nodes.length;
        looks = new String[slots];
        for (int i = 0; i < slots; i++) {
            looks[i] = "look_" + i;
        }
        item = new Item[slots];
        raw = new ItemStack[slots];
        java.util.Arrays.fill(raw, ItemStack.EMPTY);
        meat = new boolean[slots];
        blockLike = new boolean[slots];
        height = new float[slots];
        x = new float[slots];
        z = new float[slots];
        vx = new float[slots];
        vz = new float[slots];
        yaw = new float[slots];
        spin = new float[slots];
        over = new float[slots];
        turnAge = nan(slots);
        hopAge = nan(slots);
        hopHeight = new float[slots];
        hopTime = new float[slots];
        flyAge = nan(slots);
        flyTime = new float[slots];
        fromX = new float[slots];
        fromY = new float[slots];
        fromZ = new float[slots];
        arc = new float[slots];
        meltAge = nan(slots);
        java.util.Arrays.fill(puddleOf, -1);
        knocked = new float[slots];
        lastTint = new float[slots];
    }

    private static float[] nan(int count) {
        float[] values = new float[count];
        java.util.Arrays.fill(values, Float.NaN);
        return values;
    }

    /**
     * The slots as they are now; {@code quiet} lays what is new where it would lie, without it flying in. Things that came in
     * fly from the hand, several one after the other, or drop from the cursor when it is one.
     */
    void sync(StationView view, ItemStack[] shown, boolean quiet) {
        if (!painted) {
            painted = true;
            view.node("fat").ifPresent(node -> node.setPainter(fat));
        }
        int arriving = 0;
        for (int i = 0; i < slots; i++) {
            Item now = shown[i].isEmpty() ? null : shown[i].getItem();
            if (now == item[i]) {
                raw[i] = shown[i].copy();
                continue;
            }
            if (now != null && !quiet && item[i] == null) {
                arriving++;
            }
        }
        int k = 0;
        for (int i = 0; i < slots; i++) {
            Item now = shown[i].isEmpty() ? null : shown[i].getItem();
            if (now == item[i]) {
                continue;
            }
            if (now == null) {
                if (Float.isNaN(meltAge[i])) {
                    clear(view, i);
                }
                continue;
            }
            boolean flies = !quiet && item[i] == null;
            meltAge[i] = Float.NaN;
            item[i] = now;
            raw[i] = shown[i].copy();
            look(view, i);
            if (flies) {
                arrive(view, i, k++, arriving);
            } else {
                float[] at = freePlace(i);
                x[i] = at[0];
                z[i] = at[1];
                vx[i] = 0.0F;
                vz[i] = 0.0F;
                yaw[i] = (random.nextFloat() - 0.5F) * 50.0F;
                flyAge[i] = Float.NaN;
            }
        }
    }

    /** Draws slot {@code index}'s thing as a piece cut from its icon, or as itself where it has none (a block). */
    private void look(StationView view, int index) {
        ItemStack stack = raw[index];
        FoodProperties food = stack.getItem().getFoodProperties();
        meat[index] = food != null && food.isMeat() || stack.is(ItemTags.FISHES);
        Optional<ItemVoxels.Icon> icon = ItemVoxels.icon(stack);
        blockLike[index] = icon.isEmpty();
        lastTint[index] = -1.0F;
        over[index] = 0.0F;
        turnAge[index] = Float.NaN;
        hopAge[index] = Float.NaN;
        float thick = meat[index] ? THICK_MEAT : THICK;
        height[index] = icon.map(it -> it.pixel(SIZE) * thick).orElse(0.0F);
        view.node(nodes[index]).ifPresent(node -> {
            node.scaleTo(1.0F, 0.0F);
            node.setTint(0xFFFFFFFF);
            node.setVisual(null);
            node.setShape(icon.map(it -> ItemVoxels.build(it, SIZE, List.of(), 0.0F, thick, true)).orElse(null),
                    icon.map(ItemVoxels.Icon::sprite).orElse(null));
        });
        // The item itself lies there too, at the size of the piece: unseen, the outline under the pointer is drawn round it; a thing
        // drawn as a block (no icon to cut) is shown by it.
        view.node(looks[index]).ifPresent(node -> {
            node.setVisual(Visual.ofItem(stack.getItem()).withAnchor(new Vector3f(8.0F, 8.0F, 8.0F)));
            node.setTint(blockLike[index] ? 0xFFFFFFFF : UNSEEN);
            node.setVisible(true);
        });
    }

    private void clear(StationView view, int index) {
        item[index] = null;
        raw[index] = ItemStack.EMPTY;
        flyAge[index] = Float.NaN;
        meltAge[index] = Float.NaN;
        view.node(nodes[index]).ifPresent(node -> {
            node.setShape(null, null);
            node.setVisual(null);
        });
        view.node(looks[index]).ifPresent(node -> {
            node.setVisible(false);
            node.setOutlined(false);
        });
    }

    /** Piece {@code index} comes in: the {@code k}th of {@code count} from the hand, or dropped from the cursor when it is alone. */
    private void arrive(StationView view, int index, int k, int count) {
        Matrix4f toPan = panMatrix(view).invert();
        Vector3f from;
        float[] to;
        Vector3f cursor = view.cursorOn(at(view, 0.0F, FLOOR, 0.0F).y).map(toPan::transformPosition).orElse(new Vector3f(100.0F, FLOOR, 100.0F));
        boolean overPan = Math.abs(cursor.x) < HALF_X && Math.abs(cursor.z) < HALF_Z;
        if (count > 1 || !overPan) {
            // From the hand at the bottom of the screen onto a free place: a stack sent in from the inventory, a dish picked in the book.
            from = FromPlayer.point(view, FLOOR + FLY_FROM, k, count).orElse(new Vector3f(8.0F, FLOOR + FLY_FROM, -2.0F));
            toPan.transformPosition(from);
            to = freePlace(index);
            flyTime[index] = FLY_TIME;
            arc[index] = FLY_ARC;
            flyAge[index] = -k * STAGGER;
        } else {
            // Let go over the pan: it drops where the hand is.
            to = new float[] {Mth.clamp(cursor.x, -HALF_X + RADIUS, HALF_X - RADIUS), Mth.clamp(cursor.z, -HALF_Z + RADIUS, HALF_Z - RADIUS)};
            from = new Vector3f(to[0], FLOOR + DROP_FROM, to[1]);
            flyTime[index] = DROP_TIME;
            arc[index] = 0.0F;
            flyAge[index] = 0.0F;
        }
        fromX[index] = from.x;
        fromY[index] = from.y;
        fromZ[index] = from.z;
        x[index] = to[0];
        z[index] = to[1];
        vx[index] = 0.0F;
        vz[index] = 0.0F;
        yaw[index] = (random.nextFloat() - 0.5F) * 50.0F;
        spin[index] = 0.0F;
        view.node(nodes[index]).ifPresent(node -> node.scaleTo(flyAge[index] < 0.0F ? 0.0F : 1.0F, 0.0F));
    }

    /** Of the laying places, the one farthest from everything already in the pan. */
    private float[] freePlace(int index) {
        float[] best = PLACES[index % PLACES.length];
        float bestRoom = -1.0F;
        for (float[] place : PLACES) {
            float room = Float.MAX_VALUE;
            for (int j = 0; j < slots; j++) {
                if (j != index && item[j] != null && Float.isNaN(meltAge[j])) {
                    room = Math.min(room, (float) Math.hypot(place[0] - x[j], place[1] - z[j]));
                }
            }
            if (room > bestRoom + 0.01F) {
                bestRoom = room;
                best = place;
            }
        }
        float jitter = 0.4F;
        return new float[] {best[0] + (random.nextFloat() - 0.5F) * jitter, best[1] + (random.nextFloat() - 0.5F) * jitter};
    }

    /**
     * The roast is done: what went in and is gone from its slot swells and goes, one after the other. Returns the seconds until the
     * last is gone, for the roast to show only then.
     */
    float cookedThrough(ItemStack[] items) {
        int melting = 0;
        for (int i = 0; i < slots; i++) {
            if (item[i] != null && items[i].isEmpty() && Float.isNaN(flyAge[i])) {
                meltAge[i] = -melting++ * MELT_STAGGER;
            }
        }
        return melting == 0 ? 0.0F : (melting - 1) * MELT_STAGGER + SWELL_TIME + GONE_TIME;
    }

    /** The roast that waits to be served lies in the pan; {@code null}: none. */
    void roast(StationView view, Item dish) {
        if (dish == roast) {
            return;
        }
        roast = dish;
        Optional<ItemVoxels.Icon> icon = dish == null ? Optional.empty() : ItemVoxels.icon(new ItemStack(dish));
        roastShape = icon.isPresent();
        view.node("roast_piece").ifPresent(node -> {
            node.setVisible(roastShape);
            if (roastShape) {
                node.setShape(ItemVoxels.build(icon.get(), ROAST_SIZE, List.of(), 0.0F, ROAST_THICK, true), icon.get().sprite());
                node.scaleTo(0.0F, 0.0F);
                node.scaleTo(1.0F, 0.25F);
            }
        });
        view.node("roast").ifPresent(node -> {
            node.setVisible(dish != null && !roastShape);
            if (dish != null && !roastShape) {
                node.setVisual(Visual.ofItem(dish).withAnchor(new Vector3f(8.0F, 8.0F, 8.0F)));
            }
        });
        roastAge = dish == null ? Float.NaN : 0.0F;
        if (dish != null) {
            Vector3f middle = at(view, 0.0F, FLOOR + 1.0F, 0.0F);
            view.spawnParticles(DiegesisParticles.STEAM, middle, new Vector3f(0.0F, 3.0F, 0.0F), 2.0F, 4, 0x60FFFFFF);
            view.spawnParticles(DiegesisParticles.GLINT, middle, new Vector3f(0.0F, 2.0F, 0.0F), 2.5F, 4, GLINT_COLOR);
        }
    }

    /**
     * One frame: the pan at {@code panX, panZ} (it follows the hand that swirls it), turned {@code turn} degrees to the block; it is
     * {@code heated}, it roasts ({@code cooking}), this far ({@code progress}); the local hand holds the pan ({@code holding}).
     */
    void frame(StationView view, float dt, float panX, float panZ, float turn, boolean heated, boolean cooking, float progress,
            boolean holding) {
        clock += dt;
        dt = Math.min(dt, 0.05F);
        if (dt < 1.0E-4F) {
            // Two frames in one instant: nothing moved, and a speed over no time would be no number at all.
            return;
        }
        panMoves(dt, panX, panZ);
        Matrix4f pan = panMatrix(view);
        int lying = 0;
        float sliding = 0.0F;
        for (int i = 0; i < slots; i++) {
            if (item[i] != null && Float.isNaN(flyAge[i]) && Float.isNaN(meltAge[i])) {
                lying++;
            }
        }
        float grip = Mth.lerp(pool, GRIP_DRY, GRIP_FAT) * GRAVITY;
        for (int step = 0; step < 2; step++) {
            float h = dt / 2.0F;
            for (int i = 0; i < slots; i++) {
                if (rests(i)) {
                    slide(view, i, h, grip, heated);
                }
            }
            shoulder();
        }
        for (int i = 0; i < slots; i++) {
            if (rests(i)) {
                sliding += (float) Math.hypot(vx[i], vz[i]);
                wake(view, i);
            }
        }
        for (int i = 0; i < slots; i++) {
            if (item[i] != null) {
                draw(view, pan, i, dt, turn, heated, cooking, progress);
            }
        }
        fatFrame(view, dt, heated, lying, progress, cooking);
        heat(view, dt, heated, cooking, progress, lying, sliding);
        readyRoast(view, dt, heated);
        outlines(view, holding);
    }

    /** The pan's own acceleration, from where the hand has put it frame by frame, eased: what lies in it is thrown the other way. */
    private void panMoves(float dt, float panX, float panZ) {
        if (Float.isNaN(lastPanX)) {
            lastPanX = panX;
            lastPanZ = panZ;
            return;
        }
        float nowVX = (panX - lastPanX) / dt;
        float nowVZ = (panZ - lastPanZ) / dt;
        float ease = 1.0F - (float) Math.exp(-dt / ACCELERATION_EASE);
        accX = Mth.clamp(Mth.lerp(ease, accX, (nowVX - panVX) / dt), -MOST_THROW, MOST_THROW);
        accZ = Mth.clamp(Mth.lerp(ease, accZ, (nowVZ - panVZ) / dt), -MOST_THROW, MOST_THROW);
        panVX = nowVX;
        panVZ = nowVZ;
        lastPanX = panX;
        lastPanZ = panZ;
    }

    /** Whether piece {@code i} lies on the floor now, to be slid about. */
    private boolean rests(int i) {
        return item[i] != null && Float.isNaN(flyAge[i]) && Float.isNaN(meltAge[i]);
    }

    private float radius(int i) {
        return RADIUS * (blockLike[i] ? 1.1F : 1.0F);
    }

    /** One step of a piece on the floor: thrown by the pan, held back by the floor, stopped by the walls. */
    private void slide(StationView view, int i, float dt, float grip, boolean heated) {
        float ax = -accX * INERTIA;
        float az = -accZ * INERTIA;
        float speed = (float) Math.hypot(vx[i], vz[i]);
        if (speed < 0.05F && Math.hypot(ax, az) <= grip) {
            // It sticks where it lies until the pan throws it harder than the floor holds it.
            vx[i] = 0.0F;
            vz[i] = 0.0F;
        } else {
            vx[i] += ax * dt;
            vz[i] += az * dt;
            speed = (float) Math.hypot(vx[i], vz[i]);
            float slower = grip * dt;
            float keep = speed <= slower ? 0.0F : Math.min(FASTEST, speed - slower) / speed;
            vx[i] *= keep;
            vz[i] *= keep;
        }
        x[i] += vx[i] * dt;
        z[i] += vz[i] * dt;
        float r = radius(i);
        if (x[i] > HALF_X - r) {
            x[i] = HALF_X - r;
            wall(view, i, vx[i], vz[i], 1.0F, 0.0F, heated);
            vx[i] = -Math.max(0.0F, vx[i]) * BOUNCE;
        } else if (x[i] < -HALF_X + r) {
            x[i] = -HALF_X + r;
            wall(view, i, -vx[i], -vz[i], -1.0F, 0.0F, heated);
            vx[i] = -Math.min(0.0F, vx[i]) * BOUNCE;
        }
        if (z[i] > HALF_Z - r) {
            z[i] = HALF_Z - r;
            wall(view, i, vz[i], -vx[i], 0.0F, 1.0F, heated);
            vz[i] = -Math.max(0.0F, vz[i]) * BOUNCE;
        } else if (z[i] < -HALF_Z + r) {
            z[i] = -HALF_Z + r;
            wall(view, i, -vz[i], vx[i], 0.0F, -1.0F, heated);
            vz[i] = -Math.min(0.0F, vz[i]) * BOUNCE;
        }
        spin[i] *= (float) Math.pow(0.5F, dt / SPIN_HALF_LIFE);
        yaw[i] += spin[i] * dt;
    }

    /**
     * Piece {@code i} meets the wall towards {@code nx, nz} at {@code into} pixels a second, rubbing along it at {@code along}: it
     * turns as it rubs, and a hard knock makes the fat jump and may turn it over.
     */
    private void wall(StationView view, int i, float into, float along, float nx, float nz, boolean heated) {
        spin[i] += along * SPIN_FROM_RUB;
        if (into < KNOCK_FROM || knocked[i] > clock) {
            return;
        }
        knocked[i] = clock + KNOCK_GAP;
        float r = radius(i);
        Vector3f contact = at(view, x[i] + nx * r, FLOOR + 0.15F, z[i] + nz * r);
        if (heated && pool > 0.05F) {
            view.spawnParticles(HandsOnLetsDoClient.OIL_POP, contact, SEETHE_UP, 0.4F, 2 + random.nextInt(3), 0);
            view.node("oil").ifPresent(oil -> oil.ripple(x[i] + nx * r, z[i] + nz * r, Math.min(0.3F, into * 0.015F)));
        }
        if (into >= KNOCK_LOUD) {
            if (heated) {
                for (int k = 0; k < 2; k++) {
                    view.spawnParticles(HandsOnLetsDoClient.FAT, contact, direction(view, -nx * 3.0F + (random.nextFloat() - 0.5F) * 3.0F,
                            8.0F + random.nextFloat() * 4.0F, -nz * 3.0F + (random.nextFloat() - 0.5F) * 3.0F), 0.1F, 1, FAT_COLOR);
                }
            }
            view.playSound(SoundEvents.LAVA_POP, nodes[i], 0.08F + 0.008F * Math.min(into, 25.0F), 1.5F + random.nextFloat() * 0.3F);
        }
        if (into >= TURN_FROM && Float.isNaN(turnAge[i]) && random.nextFloat() < TURN_CHANCE) {
            turnAge[i] = 0.0F;
        }
    }

    /** Pieces do not lie on each other: they push apart, trade their speed along the push and set each other turning. */
    private void shoulder() {
        for (int i = 0; i < slots; i++) {
            for (int j = i + 1; j < slots; j++) {
                if (!rests(i) || !rests(j)) {
                    continue;
                }
                float dx = x[j] - x[i];
                float dz = z[j] - z[i];
                float apart = (float) Math.hypot(dx, dz);
                float least = radius(i) + radius(j);
                if (apart >= least) {
                    continue;
                }
                float nx = apart > 0.001F ? dx / apart : Mth.cos(i + j);
                float nz = apart > 0.001F ? dz / apart : Mth.sin(i + j);
                float push = (least - apart) / 2.0F;
                x[i] -= nx * push;
                z[i] -= nz * push;
                x[j] += nx * push;
                z[j] += nz * push;
                float closing = (vx[j] - vx[i]) * nx + (vz[j] - vz[i]) * nz;
                if (closing < 0.0F) {
                    float impulse = -1.2F * closing / 2.0F;
                    vx[i] -= impulse * nx;
                    vz[i] -= impulse * nz;
                    vx[j] += impulse * nx;
                    vz[j] += impulse * nz;
                    float rub = (vx[j] - vx[i]) * -nz + (vz[j] - vz[i]) * nx;
                    spin[i] -= rub * SPIN_FROM_RUB * 0.5F;
                    spin[j] += rub * SPIN_FROM_RUB * 0.5F;
                }
                for (int k : new int[] {i, j}) {
                    float r = radius(k);
                    x[k] = Mth.clamp(x[k], -HALF_X + r, HALF_X - r);
                    z[k] = Mth.clamp(z[k], -HALF_Z + r, HALF_Z - r);
                }
            }
        }
    }

    /** A piece sliding through the oil leaves a trail behind it, the clearer the faster. */
    private void wake(StationView view, int i) {
        float speed = (float) Math.hypot(vx[i], vz[i]);
        if (speed > WAKE_FROM && pool > 0.1F) {
            view.node("oil").ifPresent(oil -> oil.leaveWake(x[i], z[i], SIZE * 0.8F, Math.min(1.0F, speed / WAKE_FULL)));
        }
    }

    /** Puts piece {@code i} where it is: in the air, landing, lying, turning over, going at the end; browned and shrunk by now. */
    private void draw(StationView view, Matrix4f pan, int i, float dt, float turn, boolean heated, boolean cooking, float progress) {
        NodeView node = view.node(nodes[i]).orElse(null);
        if (node == null) {
            return;
        }
        float px = x[i];
        float py = FLOOR;
        float pz = z[i];
        float pitch = over[i];
        float scale = 1.0F;
        if (!Float.isNaN(flyAge[i])) {
            float before = flyAge[i];
            flyAge[i] += dt;
            if (flyAge[i] < 0.0F) {
                node.scaleTo(0.0F, 0.0F);
                return;
            }
            if (before < 0.0F) {
                node.scaleTo(1.0F, 0.0F);
            }
            float t = Math.min(1.0F, flyAge[i] / flyTime[i]);
            float eased = arc[i] > 0.0F ? t * t * (3.0F - 2.0F * t) : t;
            px = Mth.lerp(eased, fromX[i], x[i]);
            pz = Mth.lerp(eased, fromZ[i], z[i]);
            // Dropped, it falls as things fall; thrown in, it comes over in an arc, turning.
            float fall = arc[i] > 0.0F ? t : t * t;
            py = Mth.lerp(fall, fromY[i], FLOOR) + arc[i] * Mth.sin(t * Mth.PI);
            pitch += (1.0F - eased) * (arc[i] > 0.0F ? 180.0F : 0.0F);
            if (t >= 1.0F) {
                flyAge[i] = Float.NaN;
                vx[i] = (x[i] - fromX[i]) / flyTime[i] * SKID;
                vz[i] = (z[i] - fromZ[i]) / flyTime[i] * SKID;
                hop(i, BOUNCE_HOP, BOUNCE_TIME);
                landed(view, i, heated);
            }
        } else if (!Float.isNaN(meltAge[i])) {
            meltAge[i] += dt;
            float age = meltAge[i];
            if (age >= SWELL_TIME + GONE_TIME) {
                Vector3f puff = at(view, px, FLOOR + 1.0F, pz);
                view.spawnParticles(DiegesisParticles.STEAM, puff, new Vector3f(0.0F, 4.0F, 0.0F), 0.5F, 4, 0);
                view.spawnParticles(DiegesisParticles.GLINT, puff, new Vector3f(0.0F, 4.0F, 0.0F), 0.9F, 3, GLINT_COLOR);
                clear(view, i);
                return;
            }
            scale = age < 0.0F ? 1.0F : age < SWELL_TIME ? Mth.lerp(age / SWELL_TIME, 1.0F, SWELL)
                    : SWELL * Mth.square(1.0F - (age - SWELL_TIME) / GONE_TIME);
        }
        if (!Float.isNaN(turnAge[i])) {
            turnAge[i] += dt;
            float t = Math.min(1.0F, turnAge[i] / TURN_TIME);
            pitch += 180.0F * t * t * (3.0F - 2.0F * t);
            py += TURN_HOP * Mth.sin(t * Mth.PI);
            if (t >= 1.0F) {
                turnAge[i] = Float.NaN;
                over[i] = (over[i] + 180.0F) % 360.0F;
                pitch = over[i];
                landed(view, i, heated);
            }
        }
        if (!Float.isNaN(hopAge[i])) {
            hopAge[i] += dt;
            float t = hopAge[i] / hopTime[i];
            if (t >= 1.0F) {
                hopAge[i] = Float.NaN;
            } else {
                py += hopHeight[i] * Mth.sin(t * Mth.PI);
            }
        } else if (cooking && rests(i) && random.nextFloat() < dt * TWITCH_RATE) {
            hop(i, TWITCH_HOP, TWITCH_TIME);
        }
        float done = KitchenMotions.smooth(Mth.clamp(progress, 0.0F, 1.0F));
        if (meat[i] && Float.isNaN(meltAge[i])) {
            scale *= 1.0F - SHRINK * done;
        }
        if (Math.abs(done - lastTint[i]) > 0.01F) {
            lastTint[i] = done;
            int tint = browned(view, i, done);
            view.node(blockLike[i] ? looks[i] : nodes[i]).ifPresent(shown -> shown.setTint(tint));
        }
        // It turns over about its middle, not about its foot: lifted by as much as its foot swings under it.
        py += height[i] * scale * (1.0F - Mth.cos(pitch * Mth.DEG_TO_RAD)) / 2.0F;
        Vector3f scene = new Vector3f(px, py, pz);
        pan.transformPosition(scene);
        node.moveTo(scene.x - 8.0F, scene.y - 8.0F, scene.z - 8.0F, 0.0F);
        node.rotateTo(pitch, yaw[i] + turn, 0.0F, 0.0F);
        node.scaleTo(scale, 0.0F);
    }

    private void hop(int i, float height, float time) {
        hopAge[i] = 0.0F;
        hopHeight[i] = height;
        hopTime[i] = time;
    }

    /** Lands on the iron: a soft knock, and in a hot pan a hiss, oil seething up round it, fat spitting, a breath of steam. */
    private void landed(StationView view, int i, boolean heated) {
        view.playSound(SoundEvents.WOOD_PLACE, nodes[i], 0.2F, 1.3F + 0.04F * i);
        if (!heated) {
            return;
        }
        view.playSound(PotSounds.SIZZLE, nodes[i], 0.3F, 1.15F + random.nextFloat() * 0.1F);
        view.node("oil").ifPresent(oil -> oil.ripple(x[i], z[i], 0.3F));
        for (int k = 0; k < 10; k++) {
            view.spawnParticles(HandsOnLetsDoClient.OIL_POP, round(view, i), SEETHE_UP, 0.1F, 1, 0);
        }
        for (int k = 0; k < 4; k++) {
            spitFrom(view, i);
        }
        view.spawnParticles(DiegesisParticles.STEAM, at(view, x[i], FLOOR + 0.8F, z[i]), new Vector3f(0.0F, 3.0F, 0.0F), 1.2F, 3, 0x50FFFFFF);
    }

    /**
     * The colour of piece {@code i} {@code done} through: its raw colours towards its cooked ones; without a cooked form meat gets a
     * sear and anything else goes golden.
     */
    private int browned(StationView view, int i, float done) {
        ItemStack cooked = cooked(view, raw[i]);
        if (cooked.isEmpty() || cooked.is(raw[i].getItem())) {
            return meat[i] ? KitchenLooks.mix(0xFFFFFFFF, SEARED, SEAR * done) : KitchenLooks.mix(0xFFFFFFFF, GOLDEN, GOLDEN_MOST * done);
        }
        return KitchenLooks.fryingTint(raw[i], cooked, Math.min(done, 0.999F), SEARED, SEAR);
    }

    /** What the furnace makes of {@code stack}: the look it browns towards. */
    private static ItemStack cooked(StationView view, ItemStack stack) {
        return COOKED.computeIfAbsent(stack.getItem(), it -> view.level().getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SimpleContainer(new ItemStack(it)), view.level())
                .map(recipe -> recipe.getResultItem(view.level().registryAccess()).copy()).orElse(ItemStack.EMPTY));
    }

    /**
     * The oil, slowly: flat fat puddles under what lies in the hot pan first, left behind where a piece slid off; then a layer over
     * the whole floor wells up as the roast gets on, seething along the walls; an empty pan dries.
     */
    private void fatFrame(StationView view, float dt, boolean heated, int lying, float progress, boolean cooking) {
        boolean food = lying > 0 || roast != null;
        float wanted = roast != null ? 1.0F : Mth.clamp((progress - LAYER_FROM) / (LAYER_FULL - LAYER_FROM), 0.0F, 1.0F);
        if (heated && food) {
            pool = Math.max(pool, KitchenMotions.smooth(wanted));
        } else if (!food) {
            pool = Math.max(0.0F, pool - dt / OIL_DRY);
        }
        puddles(dt, heated);
        fat.frame(dt);
        float layer = pool;
        view.node("oil").ifPresent(oil -> {
            oil.showLiquid(OIL, Math.max(0.2F, layer) * OIL_DEPTH);
            oil.setLiquidColor(Math.round(OIL_ALPHA * layer) << 24 | OIL_RGB);
            oil.setMottle(OIL_MOTTLE);
            oil.setWakeLook(OIL_WAKE_LIFE, OIL_WAKE_CONTRAST * layer);
            oil.setAgitation(heated ? (cooking ? 0.05F : 0.02F) * layer : 0.0F);
            oil.setEdgeBoil(heated ? (cooking ? OIL_SEETHE : OIL_SEETHE_HOT) * layer * layer : 0.0F);
            oil.setVisible(layer > 0.02F);
        });
        float size = SIZE * PUDDLE_SIZE * (1.0F + PUDDLE_SPREAD * Mth.clamp(progress, 0.0F, 1.0F));
        boolean roastPuddle = roast != null && roastShape;
        int count = roastPuddle ? 1 : 0;
        for (int k = 0; k < PUDDLES; k++) {
            count += puddle[k] > 0.0F ? 1 : 0;
        }
        float[][] shown = new float[count][];
        int at = 0;
        for (int k = 0; k < PUDDLES; k++) {
            if (puddle[k] > 0.0F) {
                shown[at++] = new float[] {puddleX[k], puddleZ[k], size, puddle[k]};
            }
        }
        if (roastPuddle) {
            shown[at] = new float[] {0.0F, 0.0F, ROAST_SIZE * 1.15F, heated ? 1.0F : 0.6F};
        }
        fat.puddles(shown);
    }

    /** Each piece in the hot pan runs out a puddle where it lies; one it slid off stays behind and dries off slowly. */
    private void puddles(float dt, boolean heated) {
        for (int k = 0; k < PUDDLES; k++) {
            int i = puddleOf[k];
            if (i >= 0 && !(rests(i) && heated)) {
                puddleOf[k] = -1;
            }
            if (puddleOf[k] < 0 && puddle[k] > 0.0F) {
                puddle[k] = Math.max(0.0F, puddle[k] - dt / (heated ? PUDDLE_FADE : PUDDLE_DRY));
            }
        }
        if (!heated) {
            return;
        }
        for (int i = 0; i < slots; i++) {
            if (!rests(i)) {
                continue;
            }
            int own = -1;
            for (int k = 0; k < PUDDLES; k++) {
                if (puddleOf[k] == i) {
                    own = k;
                }
            }
            if (own >= 0 && Math.hypot(x[i] - puddleX[own], z[i] - puddleZ[own]) > PUDDLE_LEAVE) {
                // Slid off it: it stays, and the fat smeared along starts the next one.
                puddleOf[own] = -1;
                float smeared = puddle[own] * PUDDLE_SMEAR;
                own = newPuddle(i);
                puddle[own] = smeared;
            } else if (own < 0) {
                own = newPuddle(i);
            }
            puddle[own] = Math.min(1.0F, puddle[own] + dt / PUDDLE_GROW);
        }
    }

    /** A new puddle under piece {@code i}, in place of the driest left-behind one. */
    private int newPuddle(int i) {
        int best = 0;
        float driest = Float.MAX_VALUE;
        for (int k = 0; k < PUDDLES; k++) {
            float value = puddleOf[k] >= 0 ? Float.MAX_VALUE : puddle[k];
            if (value < driest) {
                driest = value;
                best = k;
            }
        }
        puddleOf[best] = i;
        puddleX[best] = x[i];
        puddleZ[best] = z[i];
        puddle[best] = 0.0F;
        return best;
    }

    /**
     * The pan at work. Hot: the air over it shimmers. With food: oil seethes round each piece, the more the further the fat has run,
     * fat spits off it, more while it roasts, steam rises, and a little smoke once it gets brown. A swirl through the hot fat hisses.
     */
    private void heat(StationView view, float dt, boolean heated, boolean cooking, float progress, int lying, float sliding) {
        if (!heated) {
            return;
        }
        shimmer += dt * (lying > 0 || roast != null ? SHIMMER_RATE : SHIMMER_EMPTY_RATE);
        while (shimmer >= 1.0F) {
            shimmer -= 1.0F;
            view.spawnParticles(HandsOnLetsDoClient.HEAT, at(view, randomly(4.5F), FLOOR + 3.0F + random.nextFloat() * 2.0F, randomly(3.5F)),
                    new Vector3f(0.0F, 3.0F, 0.0F), 0.3F, 1, 0);
        }
        if (lying == 0) {
            return;
        }
        float fullness = 0.4F + 0.6F * Math.min(1.0F, lying / 4.0F);
        seethe += dt * SEETHE_RATE * fullness * (cooking ? 1.0F : 0.35F) * (0.4F + 0.6F * pool);
        while (seethe >= 1.0F) {
            seethe -= 1.0F;
            view.spawnParticles(HandsOnLetsDoClient.OIL_POP, round(view, anyLying()), SEETHE_UP, 0.1F, 1, 0);
        }
        spit += dt * (cooking ? SPIT_ROASTING : SPIT_HOT) * fullness;
        while (spit >= 1.0F) {
            spit -= 1.0F;
            spitFrom(view, anyLying());
        }
        if (cooking) {
            vapour += dt * VAPOUR_RATE * fullness;
            while (vapour >= 1.0F) {
                vapour -= 1.0F;
                int i = anyLying();
                view.spawnParticles(DiegesisParticles.STEAM, at(view, x[i], FLOOR + 0.8F, z[i]),
                        direction(view, randomly(0.4F), 2.2F, randomly(0.4F)), 0.4F, 1, STEAM_COLOR);
            }
            smoke += dt * SMOKE_RATE * Math.max(0.0F, (progress - SMOKE_FROM) / (1.0F - SMOKE_FROM)) * fullness;
            while (smoke >= 1.0F) {
                smoke -= 1.0F;
                view.spawnParticles(HandsOnLetsDoClient.PAN_SMOKE, round(view, anyLying()), direction(view, randomly(0.5F), 4.0F, randomly(0.5F)),
                        0.3F, 1, 0);
            }
        }
        slideSizzle -= dt;
        if (sliding > SLIDE_HEARD && slideSizzle <= 0.0F) {
            slideSizzle = SLIDE_SIZZLE_GAP;
            view.playSound(PotSounds.SIZZLE, "pot", Math.min(0.35F, 0.1F + sliding * 0.008F), 1.1F + random.nextFloat() * 0.1F);
        }
    }

    /** The roast that waits breathes a little, warm glints at its fullest, steam curling off it while the pan is hot. */
    private void readyRoast(StationView view, float dt, boolean heated) {
        if (roast == null || Float.isNaN(roastAge)) {
            return;
        }
        roastAge += dt;
        if (roastAge < 0.3F) {
            return;
        }
        float before = readyClock;
        readyClock = (readyClock + dt) % READY_BEAT;
        float beat = Mth.sin(readyClock / READY_BEAT * Mth.TWO_PI);
        if (roastShape) {
            view.node("roast_piece").ifPresent(node -> node.scaleTo(1.0F + READY_SWELL * Math.max(0.0F, beat), 0.0F));
        }
        boolean fullest = before < READY_BEAT * 0.25F && readyClock >= READY_BEAT * 0.25F;
        if (fullest) {
            for (int k = 0; k < 2; k++) {
                view.spawnParticles(DiegesisParticles.GLINT, at(view, randomly(2.0F), FLOOR + ROAST_THICK * 0.5F + 0.4F, randomly(1.6F)),
                        new Vector3f(0.0F, 0.8F, 0.0F), 0.4F, 1, GLINT_COLOR);
            }
        }
        if (!heated) {
            return;
        }
        wisps += dt * WISP_RATE;
        while (wisps >= 1.0F) {
            wisps -= 1.0F;
            view.spawnParticles(DiegesisParticles.STEAM, at(view, randomly(2.0F), FLOOR + 1.0F, randomly(1.6F)),
                    direction(view, randomly(0.2F), 1.4F, randomly(0.2F)), 0.3F, 1, 0x30FFFFFF);
        }
        seethe += dt * 3.0F;
        while (seethe >= 1.0F) {
            seethe -= 1.0F;
            float angle = random.nextFloat() * Mth.TWO_PI;
            view.spawnParticles(HandsOnLetsDoClient.OIL_POP, at(view, Mth.cos(angle) * 3.2F, FLOOR + 0.15F, Mth.sin(angle) * 2.6F), SEETHE_UP,
                    0.1F, 1, 0);
        }
    }

    /**
     * The outline goes round a piece under an empty hand, as round every other thing that can be taken; not while the hand holds the
     * pan by its handle.
     */
    private void outlines(StationView view, boolean holding) {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean free = view.isFocused() && !holding && player != null && player.inventoryMenu.getCarried().isEmpty();
        for (int i = 0; i < slots; i++) {
            boolean lit = free && rests(i) && view.node(nodes[i]).map(NodeView::isHovered).orElse(false);
            view.node(looks[i]).ifPresent(node -> node.setOutlined(lit));
        }
    }

    /** A drop of fat spat off piece {@code i}: up and out from its edge. */
    private void spitFrom(StationView view, int i) {
        float angle = random.nextFloat() * Mth.TWO_PI;
        float out = 2.5F + random.nextFloat() * 3.5F;
        view.spawnParticles(HandsOnLetsDoClient.FAT, round(view, i), direction(view, Mth.cos(angle) * out, 7.0F + random.nextFloat() * 6.0F,
                Mth.sin(angle) * out), 0.1F, 1, FAT_COLOR);
    }

    /** A piece lying in the pan, any of them. */
    private int anyLying() {
        int count = 0;
        for (int i = 0; i < slots; i++) {
            count += rests(i) ? 1 : 0;
        }
        int pick = random.nextInt(Math.max(1, count));
        for (int i = 0; i < slots; i++) {
            if (rests(i) && pick-- == 0) {
                return i;
            }
        }
        return 0;
    }

    /** A point on the floor just round the edge of piece {@code i}, scene pixels: where the fat seethes and spits from. */
    private Vector3f round(StationView view, int i) {
        float angle = random.nextFloat() * Mth.TWO_PI;
        float out = radius(i) * (0.8F + 0.3F * random.nextFloat());
        return at(view, x[i] + Mth.cos(angle) * out, FLOOR + 0.15F, z[i] + Mth.sin(angle) * out);
    }

    private float randomly(float half) {
        return (random.nextFloat() * 2.0F - 1.0F) * half;
    }

    /** A point of the pan, scene pixels. */
    private static Vector3f at(StationView view, float px, float py, float pz) {
        return panMatrix(view).transformPosition(new Vector3f(px, py, pz));
    }

    /** A way in the pan, turned as the scene has it. */
    private static Vector3f direction(StationView view, float dx, float dy, float dz) {
        return panMatrix(view).transformDirection(new Vector3f(dx, dy, dz));
    }

    private static Matrix4f panMatrix(StationView view) {
        return view.node("pan").map(node -> new Matrix4f(node.sceneMatrix())).orElseGet(() -> new Matrix4f().translation(8.0F, 0.0F, 8.0F));
    }
}
