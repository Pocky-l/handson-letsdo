package com.pockyl.handson_letsdo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.Floater;
import com.pockyl.diegesis.api.client.MeshShape;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.station.CookingPotStation;
import com.pockyl.handson_letsdo.station.Dish;
import com.pockyl.handson_letsdo.station.Pot;
import com.pockyl.handson_letsdo.station.PotLogic;
import com.pockyl.handson_letsdo.station.StirredPot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * What a pot of Farm &amp; Charm shows while somebody cooks at it, to the cook and to everybody around, as Hands-On Delight's pot
 * does. The block is left out of the world while the pot is used (the cooking pot's wherever its scene is drawn, {@link
 * Shape#worldLook}) and the scene shows the same pot, open: the water, what swims in it, how it colours and simmers, the spoon
 * going round, the lid stood up behind it. Everything real comes from the state; the rest is reading it.
 */
public final class PotBehavior implements StationBehavior {
    /**
     * The inside of a pot, in pixels: where the liquid starts, how deep a full pot is, the height of the spoon's end, where it
     * pivots in its model, and where that end rests in the pot's own axes from its middle. A full pot stays 1.5 px under the rim:
     * higher, what floats, the foam at the walls and the waves showed as a thin line over the rim from the side. The scene's
     * {@code broth} tank ends at the same height. {@code rim}: where an egg is knocked; {@code lean}: how the spoon leans at rest
     * in its model, degrees, plus to the right. {@code dry}: a pan without water, where food lies on the floor and browns, and
     * which is swirled by its handle instead of stirred with a spoon. {@code models}: the folder of its body's models; {@code lit}:
     * whether its body has a look of its own on something hot, and its lid lies on it only then. The lid: where it lies on the pot
     * and how it is turned there (pixels from the pot's middle, degrees), and where it is set down while somebody cooks.
     * {@code worldLook}: the scene is the pot's look in the world too, with nobody at it as well, wherever it is drawn: its own
     * block shows it otherwise (a flat surface where the scene has water and what swims in it), and would change the moment a cook
     * came. {@code inside}: a dry pan's floor and walls, where what lies in it slides; null in a pot. A pan whose scene has no lid
     * ({@code lidRest} null) shows none.
     */
    public record Shape(Pot.Kind kind, float floor, float depth, float spoonEnd, float rim, float lean, float homeX, float homeZ,
            boolean dry, String models, boolean lit, Vector3f lidOn, float lidOnYaw, LidRest lidRest, boolean worldLook,
            RoasterPan.Inside inside) {
        /** Farm &amp; Charm's cooking pot: walls and floor 1 px, rim at 8; its stick stands off the middle, to the front. */
        public static final Shape COOKING_POT = new Shape(Pot.COOKING_POT, 1.0F, 5.5F, 2.0F, 8.0F, 22.5F, 0.87F, -2.0F, false,
                "cooking_pot", true, new Vector3f(-1.123F, 8.0F, 2.685F), -22.5F, LidRest.COOKING_POT, true, null);
        /**
         * Farm &amp; Charm's roaster: a pan 10 by 8 inside, floor 1 px, rim at 6, under a lid that covers it whole.
         */
        public static final Shape ROASTER = new Shape(Pot.ROASTER, 1.0F, 0.0F, 1.0F, 6.0F, 0.0F, 0.0F, 0.0F, true,
                "roaster", false, new Vector3f(0.0F, 6.0F, 0.0F), 0.0F, LidRest.ROASTER, false, RoasterPan.Inside.ROASTER);
        /**
         * Candlelight's cooking pot: Farm &amp; Charm's pot 4 px taller, walls and floor 1 px, rim at 12; its longer stick stands on
         * the floor off the middle, to the back, and its lid lies askew the other way round.
         */
        public static final Shape CANDLELIGHT_POT = new Shape(Pot.CANDLELIGHT_POT, 1.0F, 9.5F, 1.0F, 12.0F, 22.5F, -1.05F, 2.0F, false,
                "candlelight_cooking_pot", true, new Vector3f(-2.364F, 12.0F, 2.158F), 22.5F, LidRest.CANDLELIGHT_POT, true, null);
        /**
         * Candlelight's cooking pan: a pan 8 by 8 inside, floor 1 px, rim at 4, one long handle and no lid; what lies in it is in
         * sight in the world as at the station.
         */
        public static final Shape CANDLELIGHT_PAN = new Shape(Pot.CANDLELIGHT_PAN, 1.0F, 0.0F, 1.0F, 4.0F, 0.0F, 0.0F, 0.0F, true,
                "candlelight_cooking_pan", false, new Vector3f(0.0F, 4.0F, 0.0F), 0.0F, null, true, RoasterPan.Inside.CANDLELIGHT_PAN);
    }

    /** Arrays are as long as the biggest pot needs; a pot uses its own {@link Shape#slots} of them. */
    private static final int SLOTS = Pot.INGREDIENTS;
    private static final String[] ITEM_NODES = nodes("item_");
    private static final String[] MELT_NODES = nodes("melt_");
    /** A press on the spoon shorter than this is a click: the spoon stays in the hand, stirring as the mouse goes, until the next click. */
    private static final float SPOON_CLICK = 0.3F;
    /** The height of the item nodes in the scene, less the part of an item that stays under the surface. */
    private static final float ITEM_REST = 8.0F - 0.25F;
    /** How far the ingredients drift from the middle of the pot, in pixels. */
    private static final float DRIFT_RADIUS = 2.2F;
    /**
     * The badge ({@link StepBadge}) is drawn in the pixels of the icon, which is 16 of them wide and centred on it; over the icon the
     * pot's own three stars for how well it is stirred.
     */
    private static final float PIXEL = 0.125F;
    static final String[] STAR = {"..#..", ".###.", "#####", ".###.", ".#.#."};
    static final int STAR_SIZE = 5;
    /** The most stars, the pixel between two of them, the top of a star; of three, the middle one stands a pixel higher. */
    private static final int STARS = 3;
    private static final int STAR_GAP = 1;
    private static final int STAR_TOP = -14;
    /** After a batch, its stars light up after the first, stay lit until the second, seconds, and flash one after the other this far apart. */
    private static final float GRADE_WAIT = 0.3F;
    private static final float GRADE_SHOWN = 2.8F;
    private static final float GRADE_STAGGER = 0.18F;
    private static final float[] GRADE_PITCH = {1.0F, 1.26F, 1.5F};
    /** The servings the stars added, shown as a stack count is, over the icon's lower right corner: digits three pixels by five. */
    private static final String[] PLUS = {"...", ".#.", "###", ".#.", "..."};
    static final String[][] DIGITS = {
            {"###", "#.#", "#.#", "#.#", "###"}, {".#.", "##.", ".#.", ".#.", "###"}, {"###", "..#", "###", "#..", "###"},
            {"###", "..#", ".##", "..#", "###"}, {"#.#", "#.#", "###", "..#", "..#"}, {"###", "#..", "###", "..#", "###"},
            {"###", "#..", "###", "#.#", "###"}, {"###", "..#", ".#.", ".#.", ".#."}, {"###", "#.#", "###", "#.#", "###"},
            {"###", "#.#", "###", "..#", "###"}};
    private static final int COUNT_RIGHT = 9;
    /** A small flame, the stove's badge's over its icon while its fire burns. */
    static final String[] FLAME = {"..#..", ".##.#", "#####", "#####", ".###."};
    private static final int COUNT_TOP = 4;
    private static final Vector3f ITEM_MIDDLE = new Vector3f(8.0F, 8.0F, 8.0F);
    private static final Vector3f RISE = new Vector3f(0.0F, 3.5F, 0.0F);
    private static final Vector3f STEAM_RISE = new Vector3f(0.0F, 3.2F, 0.0F);
    /** The pot's steam: whiter than the library's, a pot on the fire steams plainly; it still leaves the soup in sight. */
    private static final int STEAM_COLOR = 0x6CFFFFFF;
    private static final Vector3f SPLASH_UP = new Vector3f(0.0F, 7.0F, 0.0F);
    /** How much floats in a soup for the servings in the pot. */
    private static final int PIECES_BASE = 14;
    private static final int PIECES_PER_SERVING = 5;
    private static final int PIECES_MOST = 32;
    /** The end of cooking: an ingredient swells for this long, then shrinks away until {@link #MELT_TIME}, one after the other. */
    private static final float SWELL_TIME = 0.16F;
    private static final float SWELL = 1.4F;
    private static final float MELT_TIME = 0.55F;
    private static final float MELT_STAGGER = 0.07F;
    /** How long after the last ingredient the pieces start to come up. */
    private static final float PIECES_WAIT = 0.25F;
    private static final Vector3f PUFF_UP = new Vector3f(0.0F, 4.0F, 0.0F);
    private static final int GLINT_COLOR = 0xFFFFE9A0;
    /** Water takes this alpha, a finished soup the other: it thickens as it cooks. */
    private static final int WATER_ALPHA = 0xA0;
    private static final int SOUP_ALPHA = 0xF0;
    /** How much of the food's colour the water has before any cooking. */
    private static final float RAW_TINT = 0.3F;

    /** How far the spoon's end goes from the pot's middle, in pixels. */
    private static final float SPOON_REACH = 2.3F;
    /** How much the spoon leans in the hand, degrees; at rest it leans as its model does ({@link Shape#lean}). */
    private static final float STIR_LEAN = 7.0F;
    /** The spoon's half width; how far from it an item's middle stays; how far from the pot's middle a wall lets an item go. */
    private static final float SPOON_RADIUS = 1.05F;
    /** The spoon going back to its rest by itself: how long it takes to get moving (seconds), how fast it then goes and rises out of the soup. */
    private static final float SPOON_HOME_EASE = 0.6F;
    private static final float SPOON_HOME_RATE = 1.8F;
    private static final float SPOON_HOME_GRIP = 2.0F;
    private static final float ITEM_CLEARANCE = 2.1F;
    private static final float ITEM_ROOM = 2.9F;
    private static final float ITEM_RADIUS = 1.15F;
    private static final float POT_HALF = 5.0F;
    /** A somersault of an item in the soup: from how strong a swirl, how often at the strongest (per second), how long, how high. */
    private static final float TUMBLE_FROM = 0.3F;
    private static final float TUMBLE_RATE = 0.35F;
    private static final float TUMBLE_TIME = 0.7F;
    private static final float TUMBLE_HOP = 0.7F;
    /** How fast an item pushed aside drifts back to its way. */
    private static final float PUSH_HALF_LIFE = 0.35F;
    /** For how long after the local hand let go the spoon is still its own, seconds: longer than a stir keeps the pot going. */
    private static final float HAND_MEMORY = 2.5F;
    /** Seconds between what the hand tells the server, and how far it must have moved by then, in pixels. */
    private static final float SEND_INTERVAL = 0.12F;
    private static final float SEND_TRAVEL = 0.2F;
    private static final float RIPPLE_INTERVAL = 0.09F;
    private static final float RIPPLE_STRENGTH = 0.17F;
    /** How loud the layers of the pot's sound are; a piece coming up, and how far apart those come at most, seconds. */
    private static final float BODY_RESTING = 0.3F;
    private static final float BODY_COOKING = 0.7F;
    private static final float STIR_VOLUME = 0.7F;
    private static final float BLUB_VOLUME = 0.32F;
    private static final float SIZZLE_VOLUME = 0.12F;
    private static final float BOIL_VOLUME = 0.35F;
    /** Farmer's Delight's boiling comes in clips with soft ends: laid over each other this often (seconds) they make one sound. */
    private static final float BOIL_EVERY = 2.6F;
    private static final float BOIL_EVERY_COOKING = 1.7F;
    private static final float SIZZLE_EVERY = 3.0F;
    private static final float BLUB_GAP = 0.11F;
    private static final int MOST_BLUBS = 9;
    /** How long the soup keeps turning after the spoon stops, seconds, and how many radians a second it turns at most. */
    private static final float SWIRL_HALF_LIFE = 0.9F;
    /** A whole item's trail: how clear at its clearest, how wide (pixels), from what speed and how much more it takes to be full (pixels per second). */
    private static final float ITEM_WAKE = 0.7F;
    /** Trails on plain water and on a soup that is done: how long they stay (seconds), how clearly they stand out. */
    private static final float WATER_WAKE_LIFE = 1.2F;
    private static final float SOUP_WAKE_LIFE = 9.0F;
    private static final float WATER_WAKE_CONTRAST = 0.25F;
    private static final float SOUP_WAKE_CONTRAST = 1.0F;
    private static final float ITEM_WAKE_WIDTH = 1.4F;
    private static final float ITEM_WAKE_FROM = 0.4F;
    private static final float ITEM_WAKE_RANGE = 1.6F;
    private static final float SWIRL_SPEED = 1.5F;
    private static final float CALM_DRIFT = 0.05F;
    /** Bubbles along the walls are small beside the ones that rise from the bottom. */
    private static final float EDGE_BUBBLE_SIZE = 0.5F;
    /**
     * Several things put in at once (a dish picked in the book, a stack shared out) jump in one after the other,
     * from the player at the bottom of the screen ({@link FromPlayer}): seconds between them, seconds in the air, how high they
     * start over the soup and how high the arc goes, pixels.
     */
    private static final float ARRIVE_STAGGER = 0.16F;
    private static final float ARRIVE_TIME = 0.4F;
    private static final float ARRIVE_FROM = 9.0F;
    private static final float ARRIVE_ARC = 4.0F;
    /**
     * A bowl from the inventory offered over a finished soup to an empty hand, so a
     * soup can be served without picking the bowl first: how see-through, how high over the soup it hangs (pixels), how long it stays away after a scoop (seconds).
     */
    private static final int OFFERED = 0x8CFFFFFF;
    private static final float OFFER_LIFT = 1.5F;
    private static final float OFFER_AWAY = 0.35F;

    /**
     * The pot of the scene stands in for the block while it is left out of the world: its body as it stands cold and on something
     * hot, and what is shown with it.
     */
    private static final String[] SHOWN_IN_USE = {"pot", "ladle", "broth", "served"};
    /**
     * The lid lies on the pot as the block has it. While somebody cooks it is opened on its back edge and stands on the back rim
     * ({@link LidRest}): seconds each way.
     */
    private static final float LID_MOVE = 0.6F;
    /** A hanging roaster hangs this much higher in its block than a standing one stands. */
    private static final float HANGING = 2.0F;
    /**
     * A dry pan: how far it follows the hand that swirls it, of the hand's way; how often its own sizzle is laid over itself,
     * seconds. What lies in it and its fat: {@link RoasterPan}.
     */
    private static final float PAN_FOLLOW = 0.25F;
    private static final float ROAST_EVERY = 2.2F;


    private final int slots = SLOTS;
    private final Pot.Kind kind;
    private final float homeX;
    private final float homeZ;
    private float brothFloor;
    private final float floorOfShape;
    private final float rimOfShape;
    private final Shape shape;
    /** The dry pan's food, fat and heat; null in a pot. */
    private final RoasterPan pan;
    private final boolean dry;
    private final float brothDepth;
    private final float spoonEnd;
    private float rimTop;
    private final float restLean;
    private final RecipeBook book;
    private final StepBadge badge = new StepBadge(10, -16, false);
    /** The stars the badge shows now: how many are open, how many rows of them are lit, the servings they added. */
    private int starsOpen = 1;
    private int starRows;
    private int starsAdded;
    private final StepBadge.Extras stars = new StepBadge.Extras() {
        @Override
        public String key() {
            return starsOpen + "/" + starRows + "/" + starsAdded;
        }

        @Override
        public void track(MeshShape.Builder track) {
            for (int star = 0; star < starsOpen; star++) {
                glyph(track, STAR, starLeft(starsOpen, star), starTop(starsOpen, star));
            }
        }

        @Override
        public void lit(MeshShape.Builder lit) {
            litStars(lit, starsOpen, starRows, starsAdded);
        }
    };
    /** Whether an item goes into some dish or serves one: only those show in the inventory at the pot. */
    private final UsableCache usable = new UsableCache();
    private final ItemStack[] shown = new ItemStack[SLOTS];
    private ItemStack preview = ItemStack.EMPTY;
    /** Whether the cook is here: the camera at the pot, flying in or flying back. The badge shows for them then. */
    private boolean here;
    private float shownProgress;
    private float shownStars;
    /** How long the stars the last batch got have been shown. */
    private float gradeAge = Float.NaN;
    private float time;
    private float bubbles;
    private float edgeBubbles;
    private float steam;
    private final float[] places = new float[SLOTS];
    private float level;
    private float turned;
    /**
     * Where the circle of what floats starts, radians: the spoon at rest lies in a gap between the things, a lone one floats
     * across from it, never behind it. NaN until the spoon is first seen at rest.
     */
    private float circleStart = Float.NaN;
    private int bubbleColor = 0xFFFFFFFF;
    private Item forMeal;
    private List<Floater> mealPieces = List.of();
    private int servings = -1;
    /** The ghosts of ingredients that just cooked away: how long they have been going (negative: waiting), where they lie. */
    private final float[] meltAge = new float[SLOTS];
    private final float[] meltX = new float[SLOTS];
    private final float[] meltY = new float[SLOTS];
    private final float[] meltZ = new float[SLOTS];
    /** Items jumping in: how long they have been in the air (negative: waiting for their turn, NaN: floating), where from. */
    private final float[] arriveAge = new float[SLOTS];
    private final float[] arriveX = new float[SLOTS];
    private final float[] arriveZ = new float[SLOTS];
    /**
     * What does not float whole (a liquid poured, an egg cracked, sugar dissolving) is brought over the soup by a hand first: the
     * motion while it runs, null while it floats or before it is in; once gone it stays gone for as long as the slot holds it.
     */
    private final KitchenMotions.Motion[] going = new KitchenMotions.Motion[SLOTS];
    private final boolean[] sunk = new boolean[SLOTS];
    /** More of what is in a slot went in while its hand was still busy: the hand comes again once it is done. */
    private final boolean[] again = new boolean[SLOTS];
    /** A container offered over the soup dipped into it, coming up full; null while none is. */
    private KitchenMotions.Dip dip;
    /** Where the offered container hangs now (scene pixels), and how much it is scaled. */
    private final Vector3f scoopAt = new Vector3f();
    private float scoopScale = 1.0F;
    /** When the local hand last scooped with a bowl it held: the soup rang where the bowl went in. */
    private float scoopedHere = -10.0F;
    /** The colour the soup is going to and the one shown, which follows it: a liquid poured in spreads as it runs. */
    private int brothTarget;
    private int brothShown;
    private boolean brothKnown;
    private boolean recolour;
    private float piecesWait;
    private int brothColor;

    /**
     * How big what a hand brings over the soup is, pixels: a bottle or a bucket, sugar; an egg and its inside. How far from the
     * middle what is poured lands; where the rim an egg is knocked on is, from the middle and over the floor.
     */
    private static final float HAND_SIZE = 2.9F;
    private static final float EGG_SIZE = 2.4F;
    private static final float EGG_INSIDE = 2.6F;
    private static final float POUR_SPREAD = 1.4F;
    private static final float POUR_X = 7.4F;
    private static final float POUR_Z = 8.8F;
    private static final float RIM_OUT = 5.0F;
    /** How fast the soup's colour follows what goes in, per second. */
    private static final float RECOLOUR_RATE = 4.0F;
    private static final Vector3f SCOOP_ORIGIN = new Vector3f(8.0F, 8.0F, 8.0F);
    /** The scene's {@code scoop} node is this scaled: an item 16 pixels high drawn so many pixels high. */
    private static final float SCOOP_BASE = 0.3F;

    /** How much of the water a liquid poured into it takes over. */
    private static final float POURED_SHARE = 0.6F;
    /** A block container waits this small, this high over the soup. */
    private static final float FILLED_SCALE = 0.45F;
    private static final float FILLED_LIFT = 5.0F;
    /** What is taken back hangs this small over the soup: a hand's container, not a pot's worth. */
    private static final float TAKE_BACK_SCALE = 0.6F;

    private float shownTurn = Float.NaN;
    private float shownFacing = Float.NaN;
    /** The local player's hand is on the spoon. */
    private boolean stroking;
    /** Where the spoon's end is, in the pot's own pixels from its middle. */
    private float tipX;
    private float tipZ;
    /** 0: leaning at rest, 1: in the soup. */
    private float grip;
    private float homing;
    private float swirl;
    private float handLeft;
    /** Where the spoon goes through the surface, and where the items float, in pixels from the pot's middle in the scene. */
    private float spoonX;
    private float spoonZ;
    private final float[] itemX = new float[SLOTS];
    private final float[] itemZ = new float[SLOTS];
    private final float[] lastItemX = new float[SLOTS];
    private final float[] lastItemZ = new float[SLOTS];
    private final boolean[] itemFloating = new boolean[SLOTS];
    private final float[] pushX = new float[SLOTS];
    private final float[] tumbling = new float[SLOTS];
    private final float[] pushZ = new float[SLOTS];
    private float swirlSign = 1.0F;
    /** When the hand took the spoon; whether a click did, so the next click puts it down. */
    private float strokeStart;
    /** The container offered over the soup, empty while none is; when the last scoop with it went. */
    private ItemStack offered = ItemStack.EMPTY;
    private Item offeredItem;
    /** What went in without floating, shown over the soup to be taken back out; empty while none is. */
    private ItemStack takeBack = ItemStack.EMPTY;
    private float scoopedAt = -10.0F;
    private boolean heldByClick;
    /** Kept between frames, not made anew in each. */
    private final Matrix4f potInverse = new Matrix4f();
    private final Quaternionf lean = new Quaternionf();
    private final Vector3f restAxis = new Vector3f();
    private final Vector3f leanAxis = new Vector3f();
    private final Vector3f poseAngles = new Vector3f();
    private final Vector3f spoonTip = new Vector3f();
    private final Vector3f spoonThrough = new Vector3f();
    private final float[][] obstacles = new float[SLOTS + 1][];
    /** The ingredients the dish was last looked up for, and the dish. */
    private final ItemStack[] cookedFor = new ItemStack[SLOTS];
    private ItemStack cookedMade = ItemStack.EMPTY;
    private Dish cookedDish;
    private List<Dish> dishes = List.of();
    /** The body the pot is shown with: its own look on something hot and its plain one. */
    private Boolean shownBody;
    /** How far the lid is on its way from the pot to where it stands behind it, 0 to 1. */
    private float lid;
    private boolean lidShown;
    /** How much higher than it stands the pot hangs; the dry pan's own shift after the hand; what its food was last browned to. */
    private float lift;
    private float panX;
    private float panZ;
    private Item shownRoast;
    private float sendClock;
    private float sendTravel;
    private float rippleClock;
    private float boilClock;
    private float sizzleClock;
    private int blubs;
    private float blubClock;
    private int piecesShown;

    public PotBehavior(StationView view) {
        this(view, Shape.COOKING_POT);
    }

    public PotBehavior(StationView view, Shape shape) {
        this.shape = shape;
        kind = shape.kind();
        dry = shape.dry();
        pan = dry ? new RoasterPan(ITEM_NODES, shape.inside()) : null;
        floorOfShape = shape.floor();
        rimOfShape = shape.rim();
        brothFloor = shape.floor();
        brothDepth = shape.depth();
        spoonEnd = shape.spoonEnd();
        rimTop = shape.rim();
        restLean = shape.lean();
        homeX = shape.homeX();
        homeZ = shape.homeZ();
        tipX = homeX;
        tipZ = homeZ;
        book = new RecipeBook(new PotBook(kind));
        java.util.Arrays.fill(shown, ItemStack.EMPTY);
        java.util.Arrays.fill(meltAge, Float.NaN);
        java.util.Arrays.fill(arriveAge, Float.NaN);
        java.util.Arrays.fill(sunk, false);
    }

    @Override
    public void onSceneReady(StationView view) {
        dishes = kind.dishes(view.level());
        shownBody = null;
        shownTurn = Float.NaN;
        badge.ready(view);
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        // A rebuilt scene (F3+T, a resource pack change) has fresh nodes: what is in the pot is laid in again as it is when the pot is
        // first seen, so a poured liquid or a cracked egg stays sunk, and the container offered over the soup gets its look again.
        for (int i = 0; i < SLOTS; i++) {
            if (going[i] != null) {
                going[i].stop();
                going[i] = null;
            }
        }
        if (dip != null) {
            dip.stop();
            dip = null;
        }
        java.util.Arrays.fill(shown, ItemStack.EMPTY);
        java.util.Arrays.fill(meltAge, Float.NaN);
        offeredItem = null;
        forMeal = null;
        refresh(view, true);
    }

    @Override
    public void onOpen(StationView view) {
        dishes = kind.dishes(view.level());
        usable.clear();
        // Asked for every slot every frame, so remembered by item; a stack with its own data may match differently, it is asked each time.
        view.setInventoryFilter(stack -> usable.test(stack, it -> usable(view, it)));
        book.opened(view);
        refresh(view, true);
    }

    @Override
    public void onClose(StationView view) {
        stroking = false;
        heldByClick = false;
        book.shut(view);
        refresh(view, true);
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        refresh(view, false);
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Stroke stroke) {
            switch (stroke.phase()) {
                case START -> {
                    stroking = true;
                    if (!heldByClick) {
                        strokeStart = time;
                    }
                }
                case END -> {
                    stroking = false;
                    if (heldByClick) {
                        // The next click puts the spoon down.
                        heldByClick = false;
                    } else if (time - strokeStart < SPOON_CLICK) {
                        // A click takes the spoon in hand: it stirs as the mouse goes, with no button to hold.
                        heldByClick = true;
                        view.holdStroke();
                    }
                }
                default -> {
                }
            }
        }
        if (event instanceof SceneEvent.Click click && click.node().equals("book_open")) {
            book.click(view, click.button());
        } else if (event instanceof SceneEvent.Click click && click.node().equals("soup") && click.button() == 0 && !offered.isEmpty()) {
            CompoundTag how = new CompoundTag();
            how.putBoolean(CookingPotStation.FROM_INVENTORY, true);
            how.putBoolean(CookingPotStation.SCOOP_ALL, Screen.hasShiftDown());
            view.sendAction(CookingPotStation.ACTION_SCOOP, how);
            scoopedAt = time;
            ItemStack meal = view.state().getItem(CookingPotStation.MEAL_SLOT);
            if (!isFilledNotDipped(offered) && !meal.isEmpty()) {
                dipOffered(view, meal.getItem(), () -> {
                });
            }
        } else if (event instanceof SceneEvent.Click click && click.node().equals("sunk") && !takeBack.isEmpty()) {
            view.sendAction(CookingPotStation.ACTION_TAKE_BACK, new CompoundTag());
            dipOffered(view, takeBack.getItem(), () -> view.playSound(PotSounds.SCOOP, "broth", 0.45F, 1.1F));
        }
    }

    /** Keys go to the book first: its search while it is typed, its page keys. */
    @Override
    public boolean onKey(StationView view, int keyCode, int scanCode, int modifiers) {
        return book.key(view, keyCode, modifiers);
    }

    @Override
    public boolean onChar(StationView view, char character, int modifiers) {
        return book.typed(view, character);
    }

    /** The wheel over the recipe book goes through its ingredients or its list. */
    @Override
    public boolean onScroll(StationView view, String node, double amount) {
        return "book_open".equals(node) && book.scroll(amount);
    }

    /** A bowl dipped into the soup fills; the book takes its click with a stack in the hand too; the rest goes on to the pot as usual. */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ("book_open".equals(node)) {
            book.click(view, button);
            return true;
        }
        if ("preview".equals(node) || !canScoop(view, stack)) {
            // A pot whose six slots hold other food may still take it: a dish that reads it in place of some of them.
            return "mouth".equals(node) && FullZone.offer(view, CookingPotStation.INGREDIENTS, stack, button);
        }
        CompoundTag how = new CompoundTag();
        how.putBoolean(CookingPotStation.SCOOP_ALL, shift);
        view.sendAction(CookingPotStation.ACTION_SCOOP, how);
        // The bowl in the hand goes in where the hand is: the soup rings there.
        float surface = brothFloor + brothDepth * level;
        view.cursorOn(surface).ifPresent(point -> {
            scoopedHere = time;
            view.node("broth").ifPresent(broth -> broth.ripple(point.x, point.z, 0.45F));
            view.spawnParticles(DiegesisParticles.SPLASH, new Vector3f(point.x, surface, point.z), new Vector3f(0.0F, 6.0F, 0.0F), 0.6F, 4,
                    0xFF000000 | brothColor);
        });
        return true;
    }

    /** The container offered over the soup goes down into it where it hangs and comes up as {@code filled}, for the cook. */
    private void dipOffered(StationView view, Item filled, Runnable onDipped) {
        NodeView node = view.node("scoop").orElse(null);
        if (node == null || !node.isVisible()) {
            return;
        }
        float surface = brothFloor + brothDepth * level;
        Vector3f away = FromPlayer.point(view, surface + 6.0F, 0, 1).orElse(new Vector3f(scoopAt.x, surface + 8.0F, scoopAt.z));
        dip = new KitchenMotions.Dip(node, filled, scoopAt, SCOOP_ORIGIN, away, () -> brothFloor + brothDepth * level,
                view.node("broth").orElse(null), scoopScale, 8.0F * SCOOP_BASE * scoopScale, 0xFF000000 | brothColor, onDipped);
    }

    /** What the inventory at the pot shows: what some dish is made of, and what some dish is served in. */
    private boolean usable(StationView view, ItemStack stack) {
        for (Dish dish : dishes) {
            if (dish.servedIn(stack) || dish.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack))) {
                return true;
            }
        }
        return false;
    }

    /** The dish of the soup that waits in the pot; null for none. */
    private Dish soup(StationView view) {
        String id = view.state().data().getString(CookingPotStation.SOUP);
        if (id.isEmpty()) {
            return null;
        }
        for (Dish dish : dishes) {
            if (dish.id().toString().equals(id)) {
                return dish;
            }
        }
        return null;
    }

    private boolean canScoop(StationView view, ItemStack stack) {
        Dish soup = soup(view);
        return soup != null && soup.servedIn(stack);
    }

    /**
     * With an empty hand over a finished soup, a container from the inventory hangs see-through where the hand is, ready to dip: a
     * click scoops with it, Shift as many as there are, and the hand stays empty. Not over the spoon, nor while it stirs.
     */
    private void offerBowl(StationView view) {
        offered = ItemStack.EMPTY;
        if (dip != null) {
            // The last one is on its way to the cook; the next is offered once it is gone.
            view.node("soup").ifPresent(soup -> soup.setEnabled(false));
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        NodeView node = view.node("scoop").orElse(null);
        boolean spoon = stroking || heldByClick || view.node("ladle").map(NodeView::isHovered).orElse(false);
        if (node == null || player == null || !view.isFocused() || spoon || !player.inventoryMenu.getCarried().isEmpty()
                || time - scoopedAt < OFFER_AWAY || soup(view) == null) {
            if (node != null) {
                node.setVisible(false);
            }
            view.node("soup").ifPresent(soup -> soup.setEnabled(false));
            return;
        }
        int slot = PotLogic.vesselSlot(soup(view), player.getInventory());
        float surface = brothFloor + brothDepth * level;
        // Over the soup the cursor points at, also at its front, where the line to a point higher up is over the front rim.
        Optional<Vector3f> over = slot < 0 ? Optional.empty() : CursorAim.within(view, surface, OFFER_LIFT, 8.0F - POT_HALF,
                8.0F + POT_HALF, 8.0F - POT_HALF, 8.0F + POT_HALF);
        node.setVisible(over.isPresent());
        view.node("soup").ifPresent(soup -> soup.setEnabled(over.isPresent()));
        if (over.isEmpty()) {
            return;
        }
        offered = player.getInventory().getItem(slot);
        if (offeredItem != offered.getItem()) {
            offeredItem = offered.getItem();
            node.setVisual(Visual.ofItem(offeredItem).withAnchor(ITEM_MIDDLE));
            node.setTint(OFFERED);
            // A pumpkin to be stuffed is not dipped like a bowl: it waits over the rim, small, and the soup is ladled into it.
        }
        scoopScale = isFilledNotDipped(offered) ? FILLED_SCALE : 1.0F;
        node.scaleTo(scoopScale, 0.0F);
        float lift = isFilledNotDipped(offered) ? FILLED_LIFT : OFFER_LIFT;
        scoopAt.set(over.get().x, surface + lift, over.get().z);
        node.moveTo(over.get().x - 8.0F, surface + lift - 8.0F, over.get().z - 8.0F, 0.0F);
    }

    /**
     * With an empty hand over the soup and off anything floating, what went in without floating (milk poured, an egg cracked, sugar
     * dissolved) hangs see-through where the hand is: a click takes it back out, the last first. A bowl offered over a done soup
     * comes first.
     */
    private void offerTakeBack(StationView view) {
        takeBack = ItemStack.EMPTY;
        NodeView sunkNode = view.node("sunk").orElse(null);
        LocalPlayer player = Minecraft.getInstance().player;
        int last = -1;
        for (int i = 0; i < slots; i++) {
            if (sunk[i] && !shown[i].isEmpty()) {
                last = i;
            }
        }
        boolean spoon = stroking || heldByClick || view.node("ladle").map(NodeView::isHovered).orElse(false);
        boolean on = sunkNode != null && player != null && view.isFocused() && !spoon && offered.isEmpty() && last >= 0
                && player.inventoryMenu.getCarried().isEmpty() && dip == null && time - scoopedAt >= OFFER_AWAY;
        float surface = brothFloor + brothDepth * level;
        if (sunkNode != null) {
            // The whole surface of the soup takes the click; what floats lies higher and keeps its own.
            sunkNode.setEnabled(on);
            sunkNode.moveTo(0.0F, surface, 0.0F, 0.0F);
        }
        NodeView node = view.node("scoop").orElse(null);
        if (!on || node == null || !sunkNode.isHovered()) {
            return;
        }
        Optional<Vector3f> over = view.cursorOn(surface + OFFER_LIFT);
        if (over.isEmpty()) {
            return;
        }
        takeBack = shown[last];
        // A liquid is scooped back with its empty container; an egg or sugar shows as itself.
        Item container = takeBack.getItem().getCraftingRemainingItem();
        Item shownItem = container != null && container != Items.AIR ? container : takeBack.getItem();
        if (offeredItem != shownItem) {
            offeredItem = shownItem;
            node.setVisual(Visual.ofItem(offeredItem).withAnchor(ITEM_MIDDLE));
            node.setTint(OFFERED);
        }
        scoopScale = TAKE_BACK_SCALE;
        node.scaleTo(TAKE_BACK_SCALE, 0.0F);
        node.setVisible(true);
        scoopAt.set(over.get().x, surface + OFFER_LIFT, over.get().z);
        node.moveTo(over.get().x - 8.0F, surface + OFFER_LIFT - 8.0F, over.get().z - 8.0F, 0.0F);
    }

    /** A container that is a block (a pumpkin for a stuffed pumpkin): filled from the pot, never dipped into it. */
    private static boolean isFilledNotDipped(ItemStack container) {
        return container.getItem() instanceof net.minecraft.world.item.BlockItem;
    }

    /** Whether the hand holds a bowl over the soup: it fills on a click, and the pot says so with its outline. */
    private boolean bowlOverSoup(StationView view) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !view.isFocused() || !canScoop(view, player.inventoryMenu.getCarried())) {
            return false;
        }
        return view.cursorOn(brothFloor + brothDepth * level)
                .filter(point -> Math.abs(point.x - 8.0F) < POT_HALF && Math.abs(point.z - 8.0F) < POT_HALF).isPresent();
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        if (CookingPotStation.EFFECT_SCOOPED.equals(effect)) {
            book.statsChanged();
            // The cook's own scoop rang the soup where the bowl went in; the others see a bowl go in somewhere.
            if (dip == null && time - scoopedHere > 1.0F) {
                view.node("broth").ifPresent(broth -> broth.ripple(8.0F, 8.0F, 0.5F));
                view.spawnOnLiquid(DiegesisParticles.SPLASH, "broth", 4, 0xFF000000 | brothColor, SPLASH_UP);
            }
            if (isFilledNotDipped(offered)) {
                // Ladled up into the pumpkin over the rim.
                view.node("scoop").map(NodeView::scenePosition).ifPresent(into -> view.spawnParticles(DiegesisParticles.DROPLET,
                        new Vector3f(8.0F, brothFloor + brothDepth * level, 8.0F), new Vector3f(into).sub(8.0F, brothFloor + brothDepth * level, 8.0F),
                        0.3F, 8, 0xFF000000 | brothColor));
            }
            view.playSound(dry ? SoundEvents.ITEM_FRAME_REMOVE_ITEM : PotSounds.SCOOP, "pot", dry ? 0.4F : 0.55F, 1.0F);
        }
    }

    /** Reads the state again; {@code quiet} shows it as it is, without the splashes of things arriving. */
    private void refresh(StationView view, boolean quiet) {
        StationState state = view.state();
        ItemStack[] items = state.getItems(CookingPotStation.INGREDIENTS);
        ItemStack meal = state.getItem(CookingPotStation.MEAL_SLOT);
        if (!quiet && servings >= 0 && meal.getCount() > servings) {
            cookedThrough(view, items);
            graded(view);
        }
        servings = meal.getCount();
        List<Integer> arrivals = new ArrayList<>();
        List<Integer> more = new ArrayList<>();
        for (int i = 0; i < slots; i++) {
            ItemStack now = items[i];
            if (!now.isEmpty() && shown[i].isEmpty() && !quiet && !dry) {
                arrivals.add(i);
            } else if (!quiet && sinks(now) && ItemStack.isSameItem(now, shown[i]) && now.getCount() > shown[i].getCount()) {
                // A second egg or bottle of milk stacks in the slot of the first: it is cracked or poured all the same.
                more.add(i);
            }
            if (now.isEmpty() || quiet) {
                arriveAge[i] = Float.NaN;
            }
            if (!ItemStack.isSameItem(now, shown[i])) {
                // Another thing in this slot: it floats until it goes in; one found there when the pot is first seen is in already.
                if (going[i] != null) {
                    going[i].stop();
                    going[i] = null;
                }
                again[i] = false;
                sunk[i] = quiet && sinks(now);
                int index = i;
                view.node(ITEM_NODES[i]).ifPresent(node -> node.scaleTo(sunk[index] ? 0.0F : 1.0F, 0.0F));
            }
            shown[i] = now.copy();
        }
        if (dry) {
            pan.sync(view, shown, quiet);
        }
        if (arrivals.size() > 1) {
            float height = brothFloor + brothDepth * level + ARRIVE_FROM;
            for (int k = 0; k < arrivals.size(); k++) {
                int index = arrivals.get(k);
                Vector3f from = FromPlayer.point(view, height, k, arrivals.size()).orElse(new Vector3f(8.0F, height, 8.0F));
                arriveAge[index] = -k * ARRIVE_STAGGER;
                arriveX[index] = from.x - 8.0F;
                arriveZ[index] = from.z - 8.0F;
                if (sinks(shown[index])) {
                    // Brought by a hand when its turn comes; until then it is nowhere.
                    view.node(ITEM_NODES[index]).ifPresent(node -> node.scaleTo(0.0F, 0.0F));
                }
            }
        } else {
            arrivals.forEach(index -> splashIn(view, index));
        }
        for (int index : more) {
            if (going[index] != null || arriveAge[index] < 0.0F) {
                again[index] = true;
            } else {
                splashIn(view, index);
            }
        }
        for (int i = 0; i < slots; i++) {
            boolean there = !shown[i].isEmpty() && !(arriveAge[i] < 0.0F) && !sunk[i];
            view.node(ITEM_NODES[i]).ifPresent(node -> node.setEnabled(there));
        }
        view.node("served").ifPresent(node -> node.setEnabled(!state.getItem(CookingPotStation.SERVED_SLOT).isEmpty()));
        preview = meal.isEmpty() ? cooked(view, items) : meal;
        if (dry) {
            roast(view, meal);
        } else {
            colour(view, meal, items);
            if (quiet) {
                brothKnown = false;
            }
            pieces(view, meal);
        }
    }

    /** An item comes into the soup: rings, drops, a plop, and a hiss on a hot pot. */
    private void splashIn(StationView view, int index) {
        if (sinks(shown[index])) {
            float height = brothFloor + brothDepth * level + ARRIVE_FROM;
            goesIn(view, index, view.cursorOn(height).orElse(new Vector3f(8.0F, height, 8.0F)));
            return;
        }
        view.node("broth").ifPresent(broth -> broth.ripple(8.0F, 8.0F, 0.5F));
        view.spawnParticles(DiegesisParticles.SPLASH, ITEM_NODES[index], 5, 0xFF000000 | brothColor);
        view.playSound(PotSounds.PLOP, ITEM_NODES[index], 0.5F, 0.92F + 0.03F * index);
    }

    /**
     * Whether {@code stack} goes into the soup without floating in it whole: a liquid is poured out of its container, an egg is
     * cracked, sugar and the like dissolve.
     */
    private boolean sinks(ItemStack stack) {
        return !dry && PotLogic.sinks(stack);
    }

    /**
     * Something that does not float whole went in: a hand brings it from {@code from} (scene pixels) and pours it out, cracks it on
     * the rim or shakes it out over the soup. The slot's own node shows nothing meanwhile.
     */
    private void goesIn(StationView view, int index, Vector3f from) {
        ItemStack stack = shown[index];
        view.node(ITEM_NODES[index]).ifPresent(node -> node.scaleTo(0.0F, 0.0F));
        NodeView hand = view.node("hand_" + index).orElse(null);
        NodeView broth = view.node("broth").orElse(null);
        if (hand == null) {
            sunk[index] = true;
            return;
        }
        java.util.function.DoubleSupplier surface = () -> brothFloor + brothDepth * level;
        // Poured on the right of the pot, where the spoon is not, tipped towards the middle; things that go in together come
        // down one behind the other, so that they do not stand in each other.
        Vector3f onto = new Vector3f(POUR_X, 0.0F, POUR_Z + POUR_SPREAD * (index % 3 - 1));
        if (KitchenLooks.isCracked(stack)) {
            // Knocked on the right rim, away from the spoon, and opened over the soup next to it.
            Vector3f rim = new Vector3f(8.0F - RIM_OUT, rimTop, onto.z);
            Vector3f over = new Vector3f(8.0F - 2.0F, 0.0F, onto.z);
            NodeView inside = view.node("spill_" + index).orElse(hand);
            going[index] = new KitchenMotions.Crack(hand, inside, stack, from, rim, over, surface, broth, EGG_SIZE, EGG_INSIDE, true, 0.0F)
                    .onLanded(() -> view.playSound(PotSounds.PLOP, "broth", 0.35F, 1.25F));
        } else if (KitchenLooks.isPoured(stack)) {
            ItemStack empty = KitchenMotions.containerOf(stack).map(ItemStack::new).orElse(ItemStack.EMPTY);
            going[index] = new KitchenMotions.Pour(hand, "pour_" + index, stack, empty, KitchenLooks.liquidColor(stack), from, onto, surface,
                    "broth", HAND_SIZE, -1.0F, 0.0F).onFlow(() -> recolour = true);
        } else {
            going[index] = new KitchenMotions.Shake(hand, stack, from, onto, surface, broth, HAND_SIZE * 0.8F, -1.0F, 0.0F);
        }
    }

    /** What was brought over the soup is in it now: what dissolves sends up its last bubbles. */
    private void wentIn(StationView view, int index) {
        ItemStack stack = shown[index];
        if (!KitchenLooks.isCracked(stack) && !KitchenLooks.isPoured(stack)) {
            view.spawnInLiquid(DiegesisParticles.BUBBLE, "broth", 6, bubbleColor, RISE);
            view.playSound(PotSounds.PLOP, "broth", 0.25F, 1.35F);
        }
    }

    /** Items waiting for their turn come out one by one; what lands splashes. */
    private void arrivals(StationView view, float dt) {
        for (int i = 0; i < slots; i++) {
            if (Float.isNaN(arriveAge[i])) {
                continue;
            }
            float before = arriveAge[i];
            arriveAge[i] += dt;
            if (before < 0.0F && arriveAge[i] >= 0.0F && sinks(shown[i])) {
                arriveAge[i] = Float.NaN;
                goesIn(view, i, new Vector3f(arriveX[i] + 8.0F, brothFloor + brothDepth * level + ARRIVE_FROM, arriveZ[i] + 8.0F));
                continue;
            }
            if (before < 0.0F && arriveAge[i] >= 0.0F) {
                view.node(ITEM_NODES[i]).ifPresent(node -> node.setEnabled(true));
            }
            if (arriveAge[i] >= ARRIVE_TIME) {
                arriveAge[i] = Float.NaN;
                splashIn(view, i);
            }
        }
    }

    /**
     * The badge comes with the camera as the book does, growing as it flies in and going as it flies back. It shows while there is a dish
     * to cook or serve, for the cook here.
     */
    private void badgeFrame(StationView view) {
        float appear = RecipeBook.appear(view);
        here = RecipeBook.isWorkedAt(view) && appear > RecipeBook.SPECK;
        StepBadge.Step step = step(view);
        litStars(view);
        badge.show(view, here && step != null, step, stars, time);
        badge.scale(view, appear);
    }

    /**
     * What the badge shows. While a batch cooks: the dish, how far it is, the stars; with no fire under the pot, the heat it lacks, the
     * bar greyed; while the batch waits for the cook's hand, the pot's spoon (the roaster's handle). Once done, the vessel the soup is
     * filled into, blinking while the cook has none (a dish that needs none is taken as it is), and the heat again while the pot is off
     * its fire, for it serves nothing cold. Ready: the servings that wait; still to come: the batch's. Null for nothing.
     */
    private StepBadge.Step step(StationView view) {
        StationState state = view.state();
        CompoundTag data = state.data();
        boolean heated = state.getControl(CookingPotStation.HEATED) > 0.5F;
        int ready = data.getInt(CookingPotStation.SERVINGS);
        Dish soup = soup(view);
        if (ready > 0 && soup != null) {
            Component tip = Component.translatable("handson_letsdo.pot.ready", ready, soup.result().getHoverName());
            if (!heated) {
                return new StepBadge.Step(ItemStack.EMPTY, StepBadge.Glyph.FLAME, true, ready, 0, 1.0F, 0, true, tip);
            }
            if (soup.vessel().isEmpty()) {
                return new StepBadge.Step(soup.result(), null, false, ready, 0, 1.0F, 0, false, tip);
            }
            return new StepBadge.Step(soup.vessel(), null, StepBadge.lacks(soup::servedIn), ready, 0, 1.0F, 0, false, tip);
        }
        if (preview.isEmpty()) {
            return null;
        }
        int more = data.getInt(CookingPotStation.BATCH);
        float progress = !Float.isNaN(gradeAge) ? 1.0F : shownProgress;
        Component tip = preview.getHoverName();
        if (heated && state.getControl(CookingPotStation.STIRRING) > 0.5F) {
            int seconds = Mth.ceil(data.getInt(CookingPotStation.COOK_LEFT) / 20.0F);
            tip = Component.translatable("handson_letsdo.pot.cooking", preview.getHoverName(), seconds);
        }
        if (!heated) {
            return new StepBadge.Step(ItemStack.EMPTY, StepBadge.Glyph.FLAME, true, 0, more, progress, 0, true, tip);
        }
        if (data.getBoolean(CookingPotStation.WAITS_FOR_SPOON)) {
            return new StepBadge.Step(preview, dry ? StepBadge.Glyph.PAN : StepBadge.Glyph.SPOON, false, 0, more, progress, 0, false, tip);
        }
        return new StepBadge.Step(preview, null, false, 0, more, progress, 0, false, tip);
    }

    /** A batch is done: the stars it got light up one after the other and stay lit for a moment. The server's word may come a tick later. */
    private void graded(StationView view) {
        gradeAge = view.isFocused() ? 0.0F : Float.NaN;
        book.statsChanged();
    }

    private static int grade(StationView view) {
        return Mth.clamp(view.state().data().getInt(CookingPotStation.GRADE), 0, STARS);
    }

    private static int bonus(StationView view) {
        return Math.max(0, view.state().data().getInt(CookingPotStation.BONUS));
    }

    /** The stars the cook has opened for this dish: one the first time, all three once they know it. */
    private static int openStars(StationView view) {
        return Mth.clamp(view.state().data().getInt(CookingPotStation.OPEN_STARS), 1, STARS);
    }

    /** Where the left edge of a star stands, the open ones centred over the icon; five pixels wide, they sit between its pixels. */
    private static float starLeft(int open, int star) {
        return -(open * (STAR_SIZE + STAR_GAP) - STAR_GAP) / 2.0F + star * (STAR_SIZE + STAR_GAP);
    }

    private static int starTop(int open, int star) {
        return open == STARS && star == 1 ? STAR_TOP - 1 : STAR_TOP;
    }

    private void showGrade(StationView view, float dt) {
        if (Float.isNaN(gradeAge)) {
            return;
        }
        float before = gradeAge;
        gradeAge += dt;
        NodeView plate = view.node("badge").orElse(null);
        for (int star = 0; star < grade(view) && plate != null; star++) {
            float at = GRADE_WAIT + star * GRADE_STAGGER;
            if (before <= at && gradeAge > at) {
                float x = -(starLeft(openStars(view), star) + STAR_SIZE / 2.0F) * PIXEL;
                float z = -(starTop(openStars(view), star) + STAR_SIZE / 2.0F) * PIXEL;
                Vector3f middle = plate.sceneMatrix().transformPosition(new Vector3f(x, 0.0F, z));
                view.spawnParticles(DiegesisParticles.GLINT, middle, PUFF_UP, 0.4F, 3, GLINT_COLOR);
                if (bonus(view) > 0) {
                    // The chime is for stars that pay: one star lights up quietly, so the first ones teach what the stars are for.
                    view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.35F, GRADE_PITCH[star]);
                }
            }
        }
        if (gradeAge >= GRADE_SHOWN) {
            gradeAge = Float.NaN;
        }
    }

    /**
     * The moment it is done, the cartoon way: what went in swells and bursts one after the other, and only then the pieces come up.
     * Farmer's Delight has taken the items by now, so what bursts are copies where they floated; a stack with more left goes on floating.
     */
    private void cookedThrough(StationView view, ItemStack[] items) {
        if (dry) {
            float wait = pan.cookedThrough(items);
            if (wait > 0.0F) {
                piecesWait = wait + PIECES_WAIT;
            }
            return;
        }
        float surface = brothFloor + brothDepth * level - ITEM_REST;
        int melting = 0;
        for (int i = 0; i < slots; i++) {
            int index = i;
            if (shown[i].isEmpty() || !floats(i)) {
                continue;
            }
            if (!items[i].isEmpty()) {
                continue;
            }
            meltX[i] = itemX[i];
            meltY[i] = surface;
            meltZ[i] = itemZ[i];
            meltAge[i] = -melting++ * MELT_STAGGER;
            view.node(MELT_NODES[i]).ifPresent(node -> {
                node.setVisual(Visual.ofItem(shown[index].getItem()).withAnchor(ITEM_MIDDLE));
                node.moveTo(meltX[index], meltY[index], meltZ[index], 0.0F);
                node.scaleTo(1.0F, 0.0F);
                node.setVisible(true);
            });
        }
        if (melting > 0) {
            piecesWait = melting * MELT_STAGGER + MELT_TIME + PIECES_WAIT;
            view.spawnInLiquid(DiegesisParticles.BUBBLE, "broth", 10, bubbleColor, RISE);
            view.node("broth").ifPresent(broth -> broth.ripple(8.0F, 8.0F, 0.3F));
        }
    }

    /** Swells, shrinks away and is gone with a puff. */
    private void melt(StationView view, float dt) {
        for (int i = 0; i < slots; i++) {
            if (Float.isNaN(meltAge[i])) {
                continue;
            }
            meltAge[i] += dt;
            float age = meltAge[i];
            if (age < 0.0F) {
                continue;
            }
            NodeView node = view.node(MELT_NODES[i]).orElse(null);
            if (node == null) {
                meltAge[i] = Float.NaN;
            } else if (age >= MELT_TIME) {
                meltAge[i] = Float.NaN;
                node.setVisible(false);
                burst(view, i);
            } else {
                float left = 1.0F - (age - SWELL_TIME) / (MELT_TIME - SWELL_TIME);
                node.scaleTo(age < SWELL_TIME ? Mth.lerp(age / SWELL_TIME, 1.0F, SWELL) : SWELL * left * left, 0.0F);
                node.moveTo(meltX[i], meltY[i] - age * 0.7F, meltZ[i], 0.0F);
            }
        }
        if (piecesWait > 0.0F) {
            piecesWait -= dt;
            if (piecesWait <= 0.0F && dry) {
                roast(view, view.state().getItem(CookingPotStation.MEAL_SLOT));
            } else if (piecesWait <= 0.0F) {
                pieces(view, view.state().getItem(CookingPotStation.MEAL_SLOT));
            }
        }
    }

    private void burst(StationView view, int slot) {
        Vector3f at = new Vector3f(8.0F + meltX[slot], 8.0F + meltY[slot], 8.0F + meltZ[slot]);
        view.spawnParticles(DiegesisParticles.STEAM, at, PUFF_UP, 0.5F, 4, 0);
        view.spawnParticles(DiegesisParticles.GLINT, at, PUFF_UP, 0.9F, 3, GLINT_COLOR);
        view.node("broth").ifPresent(broth -> broth.ripple(at.x, at.z, 0.35F));
        if (!dry) {
            view.playSound(PotSounds.BURST, "broth", 0.55F, 0.95F + 0.05F * slot);
        }
    }

    /** The roast that is done lies in the middle of the pan, as the dish it is, until the last serving is taken. */
    private void roast(StationView view, ItemStack meal) {
        Item item = meal.isEmpty() || piecesWait > 0.0F ? null : meal.getItem();
        if (item == shownRoast) {
            return;
        }
        shownRoast = item;
        pan.roast(view, item);
    }

    /** The soup holds what it is made of, and thins out serving by serving. */
    private void pieces(StationView view, ItemStack meal) {
        if (piecesWait > 0.0F) {
            return;
        }
        int count = meal.isEmpty() ? 0 : Math.min(PIECES_MOST, PIECES_BASE + PIECES_PER_SERVING * meal.getCount());
        if (preview.getItem() != forMeal) {
            forMeal = preview.isEmpty() ? null : preview.getItem();
            Dish dish = dishOf(view);
            mealPieces = preview.isEmpty() || dish == null ? List.of()
                    : CookedPieces.of(view.level(), dish.ingredients(), FoodColors.broth(preview, dish.vessel()));
        }
        int shownCount = count;
        view.node("broth").ifPresent(broth -> broth.setFloating(mealPieces, shownCount));
        // What comes up can be heard coming up, one after the other.
        blubs = Math.min(MOST_BLUBS, blubs + Math.max(0, count - piecesShown));
        piecesShown = count;
    }

    private boolean hasIngredients(StationView view) {
        return java.util.Arrays.stream(view.state().getItems(CookingPotStation.INGREDIENTS)).anyMatch(stack -> !stack.isEmpty());
    }

    /** Clear water, tinted by what is in it, deepening to the colour of the food as it cooks. */
    private void colour(StationView view, ItemStack meal, ItemStack[] items) {
        int water = WATER_ALPHA << 24 | BiomeColors.getAverageWaterColor(view.level(), view.pos()) & 0xFFFFFF;
        if (meal.isEmpty()) {
            // A liquid poured in colours the water from when it runs: milk makes it milky before anything cooks.
            for (int i = 0; i < items.length; i++) {
                ItemStack item = items[i];
                boolean waiting = i < SLOTS && !sunk[i] && (going[i] instanceof KitchenMotions.Pour pour && !pour.hasFlowed()
                        || sinks(item) && arriveAge[i] < 0.0F);
                if (KitchenLooks.isPoured(item) && !waiting) {
                    water = blend(water, SOUP_ALPHA << 24 | KitchenLooks.liquidColor(item) & 0xFFFFFF, POURED_SHARE);
                }
            }
        }
        List<ItemStack> food = new ArrayList<>();
        if (!meal.isEmpty()) {
            food.add(meal);
        } else {
            food.addAll(List.of(items));
            food.add(view.state().getItem(CookingPotStation.SERVED_SLOT));
        }
        food.removeIf(ItemStack::isEmpty);
        int colour = water;
        float uneven = 0.0F;
        if (!food.isEmpty()) {
            float cooked = meal.isEmpty() ? RAW_TINT + (1.0F - RAW_TINT) * view.state().getControl(CookingPotStation.PROGRESS) : 1.0F;
            Dish dish = dishOf(view);
            int soup = preview.isEmpty() ? FoodColors.mix(food) : FoodColors.broth(preview, dish == null ? ItemStack.EMPTY : dish.vessel());
            colour = blend(water, SOUP_ALPHA << 24 | soup, cooked);
            uneven = cooked;
        }
        float mottle = uneven;
        view.node("broth").ifPresent(broth -> {
            broth.setMottle(mottle);
            broth.setWakeLook(Mth.lerp(mottle, WATER_WAKE_LIFE, SOUP_WAKE_LIFE), Mth.lerp(mottle, WATER_WAKE_CONTRAST, SOUP_WAKE_CONTRAST));
        });
        brothTarget = colour;
    }

    /** The soup's colour follows what it is made of: what is poured in spreads through it as it runs. */
    private void showColour(StationView view, float dt) {
        if (recolour) {
            recolour = false;
            StationState state = view.state();
            colour(view, state.getItem(CookingPotStation.MEAL_SLOT), state.getItems(CookingPotStation.INGREDIENTS));
        }
        int was = brothShown;
        brothShown = brothKnown ? blend(brothShown, brothTarget, Math.min(1.0F, dt * RECOLOUR_RATE)) : brothTarget;
        if (brothKnown && was == brothShown) {
            return;
        }
        brothKnown = true;
        int shownColor = brothShown;
        brothColor = shownColor & 0xFFFFFF;
        bubbleColor = 0xE0000000 | blend(shownColor, 0xFFFFFFFF, 0.6F) & 0xFFFFFF;
        view.node("broth").ifPresent(broth -> broth.setLiquidColor(shownColor));
    }

    private static int blend(int from, int to, float amount) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            result |= Math.round(Mth.lerp(amount, from >>> shift & 0xFF, to >>> shift & 0xFF)) << shift;
        }
        return result;
    }

    /** What the ingredients make; recipes are on the client too, so the player sees it before anything cooks. */
    private ItemStack cooked(StationView view, ItemStack[] items) {
        boolean same = true;
        for (int i = 0; i < slots && same; i++) {
            same = cookedFor[i] != null && ItemStack.matches(cookedFor[i], items[i]);
        }
        if (!same) {
            SimpleContainer food = new SimpleContainer(Pot.SLOTS);
            for (int i = 0; i < slots; i++) {
                cookedFor[i] = items[i].copy();
                food.setItem(i, items[i].copy());
            }
            cookedDish = Pot.making(view.level(), kind, food);
            cookedMade = cookedDish == null ? ItemStack.EMPTY : cookedDish.result();
        }
        return cookedMade;
    }

    /** The dish the badge shows: the soup that waits, or what the food makes. */
    private Dish dishOf(StationView view) {
        Dish soup = soup(view);
        return soup != null ? soup : cookedDish;
    }

    private static String[] nodes(String prefix) {
        String[] names = new String[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            names[i] = prefix + i;
        }
        return names;
    }

    @Override
    public void frame(StationView view, float dt) {
        time += dt;
        StationState state = view.state();
        float progress = state.getControl(CookingPotStation.PROGRESS);
        shownProgress = Mth.lerp(Math.min(1.0F, dt * 6.0F), shownProgress, progress);
        shownStars = Mth.lerp(Math.min(1.0F, dt * 6.0F), shownStars, state.getControl(CookingPotStation.STARS));
        boolean heated = state.getControl(CookingPotStation.HEATED) > 0.5F;
        boolean stirred = state.getControl(CookingPotStation.STIRRING) > 0.5F;
        boolean meal = !state.getItem(CookingPotStation.MEAL_SLOT).isEmpty();
        boolean cooking = heated && stirred && !preview.isEmpty() && !meal;
        boolean soup = !preview.isEmpty() || meal;

        turnToBlock(view);
        level = Mth.lerp(Math.min(1.0F, dt * 3.0F), level, state.getTank(CookingPotStation.BROTH).amount() / 100.0F);
        float boil = 1.0F;
        showPot(view, dt, heated);
        moveSpoon(view, dt, stirred);
        arrivals(view, dt);
        drift(view, dt);
        melt(view, dt);
        book.frame(view, dt);
        badgeFrame(view);
        if (dip != null && !dip.frame(view, dt)) {
            dip = null;
            // The offer is drawn anew, as whatever is offered next.
            offeredItem = null;
            scoopedAt = time;
        }
        offerBowl(view);
        offerTakeBack(view);
        if (!dry) {
            showColour(view, dt);
        } else {
            roasting(view, dt, heated, cooking);
        }
        boolean offering = view.node("mouth").map(NodeView::isHovered).orElse(false) || bowlOverSoup(view) || !offered.isEmpty();
        view.node("pot").ifPresent(pot -> pot.setOutlined(offering));
        view.node("broth").ifPresent(broth -> {
            broth.setAgitation((!heated ? 0.0F : cooking ? 0.12F : 0.03F) * boil);
            // A pot starts to boil along its walls.
            broth.setEdgeBoil(Math.min(1.0F, (!heated ? 0.0F : cooking ? 0.9F : soup ? 0.55F : 0.3F) * boil));
        });

        // Hot water only breathes; what cooks simmers all over, and a soup left alone goes on bubbling slowly.
        if (dry) {
            sounds(view, dt, heated, cooking, soup || hasFood());
            showGrade(view, dt);
            return;
        }
        bubbles += dt * boil * (!heated ? 0.0F : cooking ? 7.0F : soup ? 2.2F : 0.8F);
        if (bubbles >= 1.0F) {
            view.spawnInLiquid(DiegesisParticles.BUBBLE, "broth", (int) bubbles, bubbleColor, RISE);
            bubbles -= (int) bubbles;
        }
        steam += dt * (!heated ? 0.0F : cooking ? 6.5F : soup ? 3.2F : 1.6F);
        if (steam >= 1.0F) {
            view.spawnOnLiquid(DiegesisParticles.STEAM, "broth", (int) steam, STEAM_COLOR, STEAM_RISE);
            steam -= (int) steam;
        }
        edgeBubbles += dt * boil * (!heated ? 0.0F : cooking ? 16.0F : soup ? 8.0F : 4.0F);
        if (edgeBubbles >= 1.0F) {
            view.spawnAtLiquidEdge(DiegesisParticles.BUBBLE_POP, "broth", (int) edgeBubbles, bubbleColor, EDGE_BUBBLE_SIZE);
            edgeBubbles -= (int) edgeBubbles;
        }
        sounds(view, dt, heated, cooking, soup || hasFood());
        showGrade(view, dt);
    }

    /** A dry pan on the fire: what lies in it, slid about by the swirl, browning, its fat and its heat ({@link RoasterPan}). */
    private void roasting(StationView view, float dt, boolean heated, boolean cooking) {
        pan.frame(view, dt, panX, panZ, Float.isNaN(shownTurn) ? 0.0F : shownTurn, heated, cooking, shownProgress, stroking);
    }

    private boolean hasFood() {
        for (ItemStack stack : shown) {
            if (!stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** With nothing in it the pot keeps Farmer's Delight's own sound: {@code CookingPotBlockMixin} lets it through. */
    private void sounds(StationView view, float dt, boolean heated, boolean cooking, boolean soup) {
        if (dry) {
            // The roaster's own clip, laid over itself on a clock: quietly while it only stands hot, plainly while it roasts.
            sizzleClock -= dt;
            if (heated && soup && sizzleClock <= 0.0F) {
                sizzleClock = cooking ? ROAST_EVERY : SIZZLE_EVERY;
                view.playSound(PotSounds.SIZZLE, "pot", cooking ? 0.3F : SIZZLE_VOLUME, 0.95F + 0.1F * view.level().getRandom().nextFloat());
            }
            return;
        }
        view.loopSound("body", PotSounds.BODY, !heated || !soup ? 0.0F : cooking ? BODY_COOKING : BODY_RESTING, 1.0F);
        view.loopSound("stir", PotSounds.STIR, swirl * STIR_VOLUME, 0.9F + 0.15F * swirl);
        boilClock -= dt;
        if (heated && soup && boilClock <= 0.0F) {
            boilClock = cooking ? BOIL_EVERY_COOKING : BOIL_EVERY;
            view.playSound(PotSounds.BOIL, "broth", BOIL_VOLUME, 0.95F + 0.1F * view.level().getRandom().nextFloat());
        }
        sizzleClock -= dt;
        if (cooking && sizzleClock <= 0.0F) {
            sizzleClock = SIZZLE_EVERY;
            view.playSound(PotSounds.SIZZLE, "broth", SIZZLE_VOLUME, 0.9F);
        }
        blubClock -= dt;
        if (blubs > 0 && blubClock <= 0.0F) {
            blubs--;
            blubClock = BLUB_GAP;
            view.playSound(PotSounds.BLUB, "broth", BLUB_VOLUME, 0.9F + 0.04F * blubs);
        }
    }

    /**
     * The scene's pot is there exactly while the block's own is left out of the world, so the two swap in one frame: its body as the
     * block has it cold or on something hot, the grate it stands on over a campfire, and its lid, which the cook finds set down
     * behind the pot, leaning on it, and which goes back onto it behind the last one to leave.
     */
    private void showPot(StationView view, float dt, boolean heated) {
        boolean open = view.isBlockHidden();
        if (shownBody == null || shownBody != heated) {
            shownBody = heated;
            ResourceLocation body = HandsOnLetsDo.id("diegesis/" + shape.models() + (heated && shape.lit() ? "/body_lit" : "/body"));
            view.node(dry ? "pan" : "pot").ifPresent(node -> node.setVisual(Visual.ofModel(body).asLitAsBlock()));
        }
        for (String id : SHOWN_IN_USE) {
            view.node(id).ifPresent(node -> node.setVisible(open));
        }
        for (int i = 0; i < slots; i++) {
            view.node(ITEM_NODES[i]).ifPresent(node -> node.setVisible(open));
        }
        BlockState block = view.level().getBlockState(view.pos());
        boolean grate = open && isOn(block, "needs_support");
        view.node("support").ifPresent(node -> node.setVisible(grate));
        // A hanging roaster hangs higher in its frame: everything in it is as much higher.
        boolean hanging = isOn(block, "hanging");
        lift = hanging ? HANGING : 0.0F;
        brothFloor = floorOfShape + lift;
        rimTop = rimOfShape + lift;
        view.node("frame").ifPresent(node -> node.setVisible(open && hanging));
        view.node("pan").ifPresent(node -> {
            node.setVisible(open);
            node.moveTo(panX, lift, panZ, 0.0F);
        });
        float before = lid;
        lid = Mth.clamp(lid + (view.isInUse() ? dt : -dt) / LID_MOVE, 0.0F, 1.0F);
        // A pot without a lid in its scene (Candlelight's pan) has none to set down.
        lidShown = open && (heated || !shape.lit()) && view.node("lid").isPresent();
        if (lidShown && (before < 1.0F && lid >= 1.0F || before > 0.0F && lid <= 0.0F)) {
            view.playSound(SoundEvents.LANTERN_PLACE, "lid", 0.25F, 0.8F + view.level().getRandom().nextFloat() * 0.1F);
        }
        float k = KitchenMotions.smooth(lid);
        view.node("lid").ifPresent(node -> {
            node.setVisible(lidShown);
            // From where the scene has it on the pot (in the pot's frame, or the pan's, which rides in the pot's) to its rest.
            float turn = Float.isNaN(shownTurn) ? 0.0F : shownTurn;
            Quaternionf potTurn = new Quaternionf().rotationY(turn * Mth.DEG_TO_RAD);
            Vector3f parentAt = dry ? potTurn.transform(new Vector3f(panX, lift, panZ)) : new Vector3f();
            Quaternionf ownTurn = new Quaternionf().rotationY(shape.lidOnYaw() * Mth.DEG_TO_RAD);
            LidRest.Pose on = shape.lidRest().lying(potTurn.transform(new Vector3f(shape.lidOn())).add(parentAt),
                    new Quaternionf(potTurn).mul(ownTurn));
            shape.lidRest().open(node, on, k, turn, hanging, lift, groundOf(view, grate), turn, parentAt, shape.lidOn(), ownTurn);
        });
    }

    /**
     * What the pot stands on beside it, where its lid is set down: the grate it stands on over a campfire, else the top of the block
     * under it at that point (scene pixels from the pot's middle); NaN where nothing is there.
     */
    private static LidRest.Ground groundOf(StationView view, boolean grate) {
        return (x, z) -> {
            float sceneX = 8.0F + x;
            float sceneZ = 8.0F + z;
            if (grate && sceneX >= 0.0F && sceneX <= 16.0F && sceneZ >= 0.0F && sceneZ <= 16.0F) {
                return 0.0F;
            }
            Vec3 at = view.toWorld(sceneX, -0.01F, sceneZ);
            BlockPos below = BlockPos.containing(at);
            VoxelShape shape = view.level().getBlockState(below).getCollisionShape(view.level(), below);
            double inX = at.x - below.getX();
            double inZ = at.z - below.getZ();
            double top = Double.NaN;
            for (AABB box : shape.toAabbs()) {
                if (inX >= box.minX && inX <= box.maxX && inZ >= box.minZ && inZ <= box.maxZ && !(top >= box.maxY)) {
                    top = box.maxY;
                }
            }
            return Double.isNaN(top) ? Float.NaN : (float) ((below.getY() + top - view.pos().getY()) * 16.0);
        };
    }

    /** Whether the block shows the state {@code name}: read by its name, the block being another mod's. */
    private static boolean isOn(BlockState state, String name) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof BooleanProperty flag && property.getName().equals(name)) {
                return state.getValue(flag);
            }
        }
        return false;
    }

    /**
     * The lid is still on its way back after the last one left: the scene stands in for the block until it lies on the pot. A pot
     * whose scene is its look in the world ({@link Shape#worldLook}) is the scene wherever it is drawn, with nobody at it too, once
     * the pot told this client what it holds (a server without this mod tells nothing: its pots stay Farm &amp; Charm's). Further
     * off its own block shows again, as the scene is not drawn there.
     */
    @Override
    public boolean holdsBlock(StationView view) {
        if (shape.worldLook() && view.level().getBlockEntity(view.pos()) instanceof StirredPot pot && pot.handson$told()) {
            return DiegesisClient.isSceneDrawn(view.pos());
        }
        return !view.isInUse() && lidShown && lid > 0.0F;
    }

    /**
     * The block keeps its place whichever side the scene faces. Scenes turn to the player who opens the pot, so what stands in the
     * block turns back, and what floats keeps its place in the world.
     */
    private void turnToBlock(StationView view) {
        BlockState state = view.level().getBlockState(view.pos());
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return;
        }
        Direction block = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        float facing = view.facing().toYRot();
        float turn = facing - block.toYRot();
        if (turn != shownTurn) {
            shownTurn = turn;
            view.node("pot").ifPresent(node -> node.rotateTo(0.0F, turn, 0.0F, 0.0F));
        }
        if (facing != shownFacing) {
            if (!Float.isNaN(shownFacing)) {
                turned -= (facing - shownFacing) * Mth.DEG_TO_RAD;
            }
            shownFacing = facing;
        }
    }

    /** The spoon's end follows the hand round the pot; for everybody else it follows what the server was told. */
    private void moveSpoon(StationView view, float dt, boolean stirred) {
        NodeView spoon = view.node("ladle").orElse(null);
        NodeView pot = view.node("pot").orElse(null);
        if (spoon == null || pot == null) {
            return;
        }
        float wantX = homeX;
        float wantZ = homeZ;
        boolean hand = stroking && view.isFocused();
        if (hand) {
            Optional<Vector3f> cursor = view.cursorOn(brothFloor + brothDepth * level);
            if (cursor.isPresent()) {
                Vector3f inPot = potInverse.set(pot.sceneMatrix()).invert().transformPosition(cursor.get());
                float distance = (float) Math.hypot(inPot.x, inPot.z);
                float shorter = distance > SPOON_REACH ? SPOON_REACH / distance : 1.0F;
                wantX = inPot.x * shorter;
                wantZ = inPot.z * shorter;
            }
            handLeft = HAND_MEMORY;
        } else if (stirred && handLeft > 0.0F) {
            // Let go, it stands where the hand left it: what the server last heard lies a little behind.
            wantX = tipX;
            wantZ = tipZ;
        } else if (stirred) {
            wantX = view.state().getControl(CookingPotStation.SPOON_X) * SPOON_REACH;
            wantZ = view.state().getControl(CookingPotStation.SPOON_Z) * SPOON_REACH;
        }
        handLeft -= dt;
        // Nobody holds it any more: it sets off softly and takes its time, the rest of its moves answer a hand.
        boolean goingHome = !hand && !stirred;
        homing = goingHome ? Math.min(1.0F, homing + dt / SPOON_HOME_EASE) : 0.0F;
        grip = Mth.lerp(Math.min(1.0F, dt * (goingHome ? SPOON_HOME_GRIP * homing : 7.0F)), grip, goingHome ? 0.0F : 1.0F);
        float follow = 1.0F - (float) Math.exp(-dt * (hand ? 18.0F : goingHome ? SPOON_HOME_RATE * homing : 9.0F));
        float stepX = (wantX - tipX) * follow;
        float stepZ = (wantZ - tipZ) * follow;
        // Round which way the hand goes: the sign of the turn about the middle.
        float across = tipX * stepZ - tipZ * stepX;
        tipX += stepX;
        tipZ += stepZ;
        float moved = (float) Math.hypot(stepX, stepZ);
        boolean inSoup = grip > 0.5F;
        if (inSoup && dt > 0.0F) {
            // Water takes up a stir slowly: what floats speeds up and turns round smoothly, whatever the hand does from frame to frame.
            if (moved > 0.02F && Math.abs(across) > 1.0e-3F) {
                swirlSign = Mth.lerp(Math.min(1.0F, dt * 1.5F), swirlSign, Math.signum(across));
            }
            float wanted = Math.min(1.0F, moved / dt / 7.0F);
            if (wanted > swirl) {
                swirl = Mth.lerp(Math.min(1.0F, dt * 2.5F), swirl, wanted);
            }
            stirEffects(view, pot, moved, dt, hand);
        }
        swirl *= (float) Math.pow(0.5F, dt / SWIRL_HALF_LIFE);
        pose(spoon, pot);
    }

    /** What a moving spoon does: rings from its end, a low sound, and word to the server that the pot is being stirred. */
    private void stirEffects(StationView view, NodeView pot, float moved, float dt, boolean hand) {
        if (moved > 0.005F) {
            view.node("broth").ifPresent(broth -> broth.leaveWake(spoonX + 8.0F, spoonZ + 8.0F));
        }
        rippleClock -= dt;
        if (moved > 0.01F && rippleClock <= 0.0F) {
            rippleClock = RIPPLE_INTERVAL;
            Vector3f at = pot.sceneMatrix().transformPosition(new Vector3f(tipX, 0.0F, tipZ));
            view.node("broth").ifPresent(broth -> broth.ripple(at.x, at.z, RIPPLE_STRENGTH));
        }
        if (hand) {
            sendClock -= dt;
            sendTravel += moved;
            if (sendClock <= 0.0F && sendTravel >= SEND_TRAVEL) {
                sendClock = SEND_INTERVAL;
                sendTravel = 0.0F;
                CompoundTag where = new CompoundTag();
                where.putFloat(CookingPotStation.STIR_X, tipX / SPOON_REACH);
                where.putFloat(CookingPotStation.STIR_Z, tipZ / SPOON_REACH);
                where.putFloat(CookingPotStation.STIR_AT, time * 20.0F);
                view.sendAction(CookingPotStation.ACTION_STIR, where);
            }
        }
    }

    /** Leans the spoon from its rest towards the hand's upright, its end staying where the hand has it. */
    private void pose(NodeView spoon, NodeView pot) {
        if (dry) {
            // No spoon: the pan itself is in the hand and goes after it a little; nothing stands in the way of what lies in it.
            panX = tipX * PAN_FOLLOW;
            panZ = tipZ * PAN_FOLLOW;
            spoonX = 100.0F;
            spoonZ = 100.0F;
            return;
        }
        // The lean grows with the distance from the middle, so it goes through the middle without a jump.
        float rest = (float) Math.tan(restLean * Mth.DEG_TO_RAD);
        float held = (float) Math.tan(STIR_LEAN * Mth.DEG_TO_RAD) / SPOON_REACH;
        float leanX = Mth.lerp(grip, rest, tipX * held);
        float leanZ = Mth.lerp(grip, 0.0F, tipZ * held);
        // The shortest way from how the model leans by itself: the handle tips, it never twists about itself.
        Quaternionf turn = lean.rotationTo(restAxis.set(rest, 1.0F, 0.0F).normalize(), leanAxis.set(leanX, 1.0F, leanZ).normalize());
        Vector3f angles = turn.getEulerAnglesYXZ(poseAngles).mul(Mth.RAD_TO_DEG);
        Vector3f end = turn.transform(spoonTip.set(0.0F, spoonEnd, 0.0F));
        spoon.moveTo(tipX - end.x, spoonEnd - end.y, tipZ - end.z, 0.0F);
        spoon.rotateTo(angles.x, angles.y, angles.z, 0.0F);
        // Where the handle goes through the surface, for what floats there to go round it.
        float height = brothFloor + brothDepth * level - spoonEnd;
        Vector3f through = pot.sceneMatrix().transformPosition(spoonThrough.set(tipX + leanX * height, 0.0F, tipZ + leanZ * height));
        spoonX = through.x - 8.0F;
        spoonZ = through.z - 8.0F;
    }

    /** The ingredients lie nearly still on calm water and go round with the soup, each at its own place on the circle. */
    private void drift(StationView view, float dt) {
        if (dry) {
            // What lies in a pan is moved by the pan itself ({@link #roasting}).
            view.node("served").ifPresent(node -> node.moveTo(0.0F, brothFloor - ITEM_REST, 0.0F, 0.0F));
            return;
        }
        float current = ((dry ? 0.0F : CALM_DRIFT) + swirl * SWIRL_SPEED) * swirlSign;
        turned += dt * current;
        view.node("broth").ifPresent(broth -> broth.setCurrent(current));
        int present = 0;
        for (int i = 0; i < slots; i++) {
            present += floats(i) ? 1 : 0;
        }
        float surface = brothFloor + brothDepth * level - ITEM_REST;
        if (grip < 0.05F && spoonX * spoonX + spoonZ * spoonZ > 0.01F) {
            float wanted = (float) Mth.atan2(spoonZ, spoonX) + Mth.PI / Math.max(present, 1);
            circleStart = Float.isNaN(circleStart) ? wanted
                    : circleStart + Mth.wrapDegrees((wanted - circleStart) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * Math.min(1.0F, dt * 2.5F);
        }
        float start = Float.isNaN(circleStart) ? 0.0F : circleStart;
        int rank = 0;
        for (int i = 0; i < slots; i++) {
            // What floats shares the circle evenly, and makes room when something new drops in.
            if (floats(i)) {
                places[i] = Mth.lerp(Math.min(1.0F, dt * 2.5F), places[i], rank++ * Mth.TWO_PI / Math.max(present, 1));
                float angle = turned + start + places[i];
                place(i, Mth.cos(angle) * DRIFT_RADIUS, Mth.sin(angle) * DRIFT_RADIUS, dt);
            }
        }
        shoulder();
        itemWakes(view, dt);
        for (int i = 0; i < slots; i++) {
            int index = i;
            if (going[i] != null) {
                if (!going[i].frame(view, dt)) {
                    going[i] = null;
                    sunk[i] = true;
                    wentIn(view, i);
                    if (again[i]) {
                        again[i] = false;
                        splashIn(view, i);
                    }
                }
                continue;
            }
            if (sunk[i]) {
                continue;
            }
            view.node(ITEM_NODES[i]).ifPresent(node -> {
                float angle = turned + start + places[index];
                float bob = dry ? 0.0F : Mth.sin(time * 0.9F + index * 1.3F) * 0.07F;
                float roll = tumble(view, index, dt);
                float hop = Mth.sin(roll * Mth.PI) * TUMBLE_HOP;
                float over = roll * roll * (3.0F - 2.0F * roll) * 360.0F;
                float x = itemX[index] + panX;
                float y = surface + bob + hop;
                float z = itemZ[index] + panZ;
                float flight = arriveAge[index];
                if (flight >= 0.0F) {
                    // On its way in from the hand: an arc that comes down onto its place on the circle.
                    float t = Math.min(1.0F, flight / ARRIVE_TIME);
                    float eased = t * t * (3.0F - 2.0F * t);
                    x = Mth.lerp(eased, arriveX[index], x);
                    z = Mth.lerp(eased, arriveZ[index], z);
                    y = Mth.lerp(t, y + ARRIVE_FROM, y) + ARRIVE_ARC * Mth.sin(t * Mth.PI);
                    over += (1.0F - eased) * 180.0F;
                }
                node.moveTo(x, y, z, 0.0F);
                node.rotateTo(Mth.sin(time * 0.5F + index) * 3.0F + over, -angle * Mth.RAD_TO_DEG, Mth.cos(time * 0.4F + index) * 3.0F, 0.0F);
            });
        }
        // What floats in pieces goes round the spoon and the whole things.
        if (obstacles[present] == null) {
            obstacles[present] = new float[3 + present * 3];
        }
        float[] standing = obstacles[present];
        standing[0] = spoonX + 8.0F;
        standing[1] = spoonZ + 8.0F;
        standing[2] = SPOON_RADIUS;
        int at = 3;
        for (int i = 0; i < slots; i++) {
            if (floats(i)) {
                standing[at++] = itemX[i] + 8.0F;
                standing[at++] = itemZ[i] + 8.0F;
                standing[at++] = ITEM_RADIUS;
            }
        }
        view.node("broth").ifPresent(broth -> broth.setFloatingObstacles(standing));
        view.node("served").ifPresent(node -> node.moveTo(0.0F, surface, 0.0F, 0.0F));
    }

    /** Whole items draw a trail through the soup, the faster the clearer; on calm water they leave none. */
    private void itemWakes(StationView view, float dt) {
        view.node("broth").ifPresent(broth -> {
            for (int i = 0; i < slots; i++) {
                if (!floats(i) || !Float.isNaN(arriveAge[i])) {
                    itemFloating[i] = false;
                    continue;
                }
                if (itemFloating[i] && dt > 0.0F) {
                    float speed = (float) Math.hypot(itemX[i] - lastItemX[i], itemZ[i] - lastItemZ[i]) / dt;
                    float strength = ITEM_WAKE * Math.min(1.0F, (speed - ITEM_WAKE_FROM) / ITEM_WAKE_RANGE);
                    if (strength > 0.0F) {
                        broth.leaveWake(itemX[i] + 8.0F, itemZ[i] + 8.0F, ITEM_WAKE_WIDTH, strength);
                    }
                }
                lastItemX[i] = itemX[i];
                lastItemZ[i] = itemZ[i];
                itemFloating[i] = true;
            }
        });
    }

    /** Whether the thing in slot {@code i} floats whole in the soup now. */
    private boolean floats(int i) {
        return !shown[i].isEmpty() && !sunk[i] && going[i] == null && !(sinks(shown[i]) && arriveAge[i] < 0.0F);
    }

    /** Items in the pot do not lie on each other: they push apart, and neither the spoon nor a wall lets them through. */
    private void shoulder() {
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < slots; i++) {
                for (int j = i + 1; j < slots; j++) {
                    if (!floats(i) || !floats(j)) {
                        continue;
                    }
                    float dx = itemX[j] - itemX[i];
                    float dz = itemZ[j] - itemZ[i];
                    float apart = (float) Math.hypot(dx, dz);
                    if (apart < ITEM_RADIUS * 2.0F) {
                        float nx = apart > 0.001F ? dx / apart : Mth.cos(i + j);
                        float nz = apart > 0.001F ? dz / apart : Mth.sin(i + j);
                        float move = (ITEM_RADIUS * 2.0F - apart) / 2.0F;
                        shift(i, -nx * move, -nz * move);
                        shift(j, nx * move, nz * move);
                    }
                }
            }
        }
    }

    private void shift(int index, float x, float z) {
        float nx = Mth.clamp(itemX[index] + x, -ITEM_ROOM, ITEM_ROOM);
        float nz = Mth.clamp(itemZ[index] + z, -ITEM_ROOM, ITEM_ROOM);
        pushX[index] += nx - itemX[index];
        pushZ[index] += nz - itemZ[index];
        itemX[index] = nx;
        itemZ[index] = nz;
    }

    /** How far an item is through a somersault, 0 to 1: a good stir turns one over now and then. */
    private float tumble(StationView view, int index, float dt) {
        if (tumbling[index] > 0.0F) {
            tumbling[index] += dt / TUMBLE_TIME;
            if (tumbling[index] >= 1.0F) {
                tumbling[index] = 0.0F;
            }
        } else if (swirl > TUMBLE_FROM && view.level().getRandom().nextFloat() < dt * TUMBLE_RATE * swirl) {
            tumbling[index] = 0.001F;
        }
        return tumbling[index];
    }

    /** Where an item floats: on its way round, unless the spoon pushed it aside; it never goes through the spoon or a wall. */
    private void place(int index, float x, float z, float dt) {
        float settle = (float) Math.pow(0.5F, dt / PUSH_HALF_LIFE);
        pushX[index] *= settle;
        pushZ[index] *= settle;
        float px = x + pushX[index];
        float pz = z + pushZ[index];
        for (int pass = 0; pass < 2; pass++) {
            float dx = px - spoonX;
            float dz = pz - spoonZ;
            float away = (float) Math.hypot(dx, dz);
            if (away < ITEM_CLEARANCE) {
                float out = ITEM_CLEARANCE / Math.max(away, 0.01F);
                px = spoonX + dx * out;
                pz = spoonZ + dz * out;
            }
            px = Mth.clamp(px, -ITEM_ROOM, ITEM_ROOM);
            pz = Mth.clamp(pz, -ITEM_ROOM, ITEM_ROOM);
        }
        pushX[index] = px - x;
        pushZ[index] = pz - z;
        itemX[index] = px;
        itemZ[index] = pz;
    }

    /** How far the stars are lit: as far as they are earned, filling from the bottom row; after a batch, its grade star by star. */
    private void litStars(StationView view) {
        boolean holding = !Float.isNaN(gradeAge);
        int lit = holding ? Mth.clamp(Mth.floor((gradeAge - GRADE_WAIT) / GRADE_STAGGER) + 1, 0, grade(view)) : 0;
        starsOpen = openStars(view);
        starRows = Math.min(starsOpen * STAR.length, holding ? lit * STAR.length : Math.round(shownStars * STAR.length));
        // The added servings show once the last star is lit.
        starsAdded = holding && lit == grade(view) && gradeAge > GRADE_WAIT + grade(view) * GRADE_STAGGER ? Math.min(bonus(view), 99) : 0;
    }

    private static void glyph(MeshShape.Builder shape, String[] rows, float left, float top) {
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].length(); column++) {
                if (rows[row].charAt(column) == '#') {
                    pixels(shape, left + column, top + row, 1, 1);
                }
            }
        }
    }

    /** "+N" right-aligned over the icon's lower right corner, where a stack shows its count. */
    private static void count(MeshShape.Builder shape, int added) {
        String digits = "+" + added;
        int x = COUNT_RIGHT - digits.length() * 4 + 1;
        for (char c : digits.toCharArray()) {
            String[] glyph = c == '+' ? PLUS : DIGITS[c - '0'];
            for (int row = 0; row < glyph.length; row++) {
                for (int column = 0; column < 3; column++) {
                    if (glyph[row].charAt(column) == '#') {
                        pixels(shape, x + column, COUNT_TOP + row, 1, 1);
                    }
                }
            }
            x += 4;
        }
    }

    /** {@code rows} rows of the {@code open} stars, counted from the bottom of the first star on, and the servings they {@code added}. */
    private static void litStars(MeshShape.Builder shape, int open, int rows, int added) {
        if (added > 0) {
            count(shape, added);
        }
        for (int star = 0; star < open; star++) {
            int lit = Mth.clamp(rows - star * STAR.length, 0, STAR.length);
            for (int row = STAR.length - lit; row < STAR.length; row++) {
                for (int column = 0; column < STAR_SIZE; column++) {
                    if (STAR[row].charAt(column) == '#') {
                        pixels(shape, starLeft(open, star) + column, starTop(open, star) + row, 1, 1);
                    }
                }
            }
        }
    }

    /** A flat rectangle of the badge, in pixels of the icon: x to the right and z down as the player sees it, the node's own axes the other way. */
    static void pixels(MeshShape.Builder shape, float x, float z, float width, float height) {
        float left = -(x + width) * PIXEL;
        float right = -x * PIXEL;
        float top = -z * PIXEL;
        float bottom = -(z + height) * PIXEL;
        shape.corner(left, 0.0F, top, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F)
                .corner(right, 0.0F, top, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F)
                .corner(right, 0.0F, bottom, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F)
                .corner(left, 0.0F, bottom, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F);
    }
}
