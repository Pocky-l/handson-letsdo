package com.pockyl.handson_letsdo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.MeshShape;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.station.HerbalTeas;
import com.pockyl.handson_letsdo.station.Kettle;
import com.pockyl.handson_letsdo.station.KettleStation;
import com.pockyl.handson_letsdo.station.Tea;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What the tea kettle shows while somebody brews at it: the kettle open, its lid set down on the stove before it, the water at its
 * level, what a drink is made of floating on it and colouring it as the bottle brews, steam by how hot it is, the bottles under
 * the spout filled one by one, what feeds the heat flaring at its foot. The badge shows the drink, how far its bottle is, and the
 * water and the heat as two gauges with a mark where the drink needs them. While nobody brews, the kettle is HerbalBrews' block
 * itself: the station hides the block while it is used and until the lid lies on it again behind the last one to leave. Hot, it
 * steams, boils and whistles as Hands-On Delight's kettle does, at the station and in the world, for whoever passes by.
 */
public final class KettleBehavior implements StationBehavior {
    /** The inside of the kettle, in pixels: its floor, and where the water stands in a full one, under the ring of its top. */
    static final float FLOOR = 1.0F;
    static final float FULL = 6.5F;
    private static final float RIM = 8.0F;
    private static final float HALF = 8.0F;
    /** How far from the middle what floats goes round: the opening is 6 px wide. */
    private static final float FLOAT_RADIUS = 1.5F;

    private static final String[] ITEM_NODES = {"item_0", "item_1", "item_2", "item_3"};
    /** The badge's own gauges, beside the shared parts ({@link StepBadge}). */
    private static final String[] GAUGES = {"gauge_water", "gauge_heat", "gauge_marks"};
    /** The scene's kettle and what stands round it, drawn while the scene stands in for the block: the world's kettle has none. */
    private static final String[] SHOWN_IN_USE = {"body", "water", "served", "embers"};
    @SuppressWarnings("removal")
    private static final ResourceLocation GAUGE_TEXTURE = new ResourceLocation("minecraft", "block/white_concrete");
    /**
     * The lid lies on the kettle as the block has it. The cook takes it off with a click and puts it back with another, as at
     * Hands-On Delight's kettle: slid out from under the copper kettle's bail, lifted, carried and set on its edge on the stove
     * before the kettle, on the side away from its spout, leaning on the kettle's side, its knob outwards: where Hands-On Delight's
     * kettle has it, seen from the same camera. Seconds each way; where it lies on the kettle, from the kettle's middle (scene
     * pixels); half its size; how far from the middle its lower edge stands, and how far it stands aside towards the cook's right
     * (the scene's -x), clear of the heat at the front corner.
     */
    private static final float LID_MOVE = 0.55F;
    private static final Vector3f LID_ON = new Vector3f(0.0F, 8.0F, 0.0F);
    private static final float LID_HALF = 3.0F;
    private static final float LID_FOOT = 7.0F;
    private static final float LID_BACK = 1.6F;
    /**
     * The way the lid leaves the kettle's top first, towards the cook and just clear of the rim (scene pixels): the copper kettle's
     * bail spans its top 4 px above the rim, over the middle; its one curve off heads this way before it rises.
     */
    private static final Vector3f LID_SLID = new Vector3f(0.0F, 1.2F, -4.6F);
    /** Half the kettle's body, from its middle: what the lid leans on. */
    private static final float BODY_HALF = 5.0F;
    /**
     * The kettle's turn in the scene (its node's, tea_kettle.json): half round, so that it stands as its block does, its spout (on
     * its model's -x) on the cook's left, the scene's +x. The camera stands where Hands-On Delight's stands to its kettle, whose
     * spout faces its cook: in front of the spout, on the cook's left, and the kettle is seen as that one is.
     */
    private static final Quaternionf TURN = new Quaternionf().rotationY(Mth.PI);
    /**
     * Pouring at the spout, as Hands-On Delight's kettle pours (a cartoon): the kettle hops off the stove into the cook's hand, up
     * (pixels), then towards the camera ({@link #HELD}), gets smaller in the hand, is turned side on to the cook with its spout to
     * the right of the screen ({@link #POUR_WAY}, so that it is tipped in profile), leans back a little as it is lifted and is
     * tipped over the bottle (degrees), all about its middle; set down, it bounces.
     */
    private static final Vector3f MIDDLE = new Vector3f(0.0F, 4.0F, 0.0F);
    private static final float LIFT = 8.0F;
    private static final float HOP = 1.5F;
    private static final Vector3f HELD = new Vector3f(1.0F, 0.0F, -1.0F).normalize(12.0F);
    /** The way to the right of the camera, level (tea_kettle.json: from (36.52, 31.09, -16.52) to (12, 4, 8)). */
    private static final Vector3f POUR_WAY = new Vector3f(-1.0F, 0.0F, -1.0F).normalize();
    /** How the camera is turned about y, degrees: 0 looking along +z; it looks along (-1, 0, 1), so a vessel poured faces it. */
    private static final float CAMERA_YAW = -45.0F;
    /** How far the kettle is turned about y in the hand: from its spout on the cook's left to its spout along {@link #POUR_WAY}. */
    private static final float IN_HAND_TURN = 135.0F;
    /** How far it is lifted before it comes out: up off the stove first, clear of the lid set down before it. */
    private static final float OUT_AFTER = 0.3F;
    private static final float IN_HAND = 0.6F;
    private static final float TILT = 55.0F;
    private static final float LEAN_BACK = 10.0F;
    private static final float BOUNCE = 0.08F;
    private static final float BOUNCE_TIME = 0.25F;
    /**
     * Where the drink leaves the spout, the lower lip of its mouth (the end of the spout's top, model x 0, y 8..10, turned half
     * round), from the middle of the kettle's foot in the scene's axes, the kettle standing on the stove.
     */
    private static final Vector3f LIP = new Vector3f(7.9F, 8.4F, 0.0F);
    /** Clear water, and how far a drink that brews has coloured it by the time its bottle is done. */
    private static final int WATER_COLOR = 0xC84A86E8;
    private static final float STEEPED = 0.85F;
    private static final float LEVEL_FOLLOW = 1.6F;
    private static final Vector3f RISE = new Vector3f(0.0F, 3.0F, 0.0F);
    /**
     * Hands-On Delight's kettle's steam: a second, from the spout of a hot kettle (from the opening with the lid off), more as a
     * brew goes on; bubbles a second in it; how it rises.
     */
    private static final float STEAM_HOT = 0.25F;
    private static final float STEAM_BREWING = 1.6F;
    /** Water, or a drink that waits, left on the heat boils on for as long as it stands there: a steady plume, puffs a second. */
    private static final float STEAM_BOILING = 7.0F;
    private static final float BUBBLES_HOT = 1.0F;
    private static final float BUBBLES_BREWING = 5.0F;
    private static final int STEAM_COLOR = 0x96FFFFFF;
    private static final Vector3f STEAM_OUT = new Vector3f(0.0F, 4.0F, 0.0F);
    /** The whistle when a brew is done, the lid on: its jet of steam from the spout, seconds, and how much. */
    private static final float JET_TIME = 2.2F;
    private static final float JET_RATE = 45.0F;
    /** How loud the whistle is: a kettle in the next room, not a train. */
    private static final float WHISTLE_VOLUME = 0.12F;
    /**
     * The boil, heard as long as something stands in the kettle on the heat (HerbalBrews' clip, about 4 dB louder and brighter than
     * Hands-On Delight's simmer: its levels, 0.15 to 0.5, by two thirds), at its own pitch.
     */
    private static final float BOIL_QUIETEST = 0.1F;
    private static final float BOIL_LOUDER = 0.22F;
    /** Which way the spout points, as the kettle stands: the scene's +x, the cook's left. */
    private static final Vector3f SPOUT_WAY = new Vector3f(1.0F, 0.0F, 0.0F);
    /** How much of a full kettle a drink that waits in it stands for. */
    private static final int WATER_OF_A_DRINK = 12;
    /** Rings from where a thing lands on the water. */
    private static final float LANDING_RINGS = 0.5F;
    /** Several things put in at once (a drink picked in the book) jump in one after the other from the player. */
    private static final float ARRIVE_STAGGER = 0.16F;
    private static final float ARRIVE_TIME = 0.4F;
    private static final float ARRIVE_FROM = 8.0F;
    private static final float ARRIVE_ARC = 3.5F;
    /** Where a vessel of water poured at the opening is drawn from and how high, pixels; how far over the top its lip is held. */
    private static final float POUR_FROM_ABOVE = 9.0F;
    private static final float POUR_SIZE = 4.2F;
    private static final float POUR_OVER_RIM = 1.5F;
    /** The scene's {@code offer} node is this scaled, and stands here: what it is drawn at is moved from there, pixels. */
    private static final float OFFER_BASE = 0.35F;
    private static final Vector3f OFFER_ORIGIN = new Vector3f(8.0F, 0.0F, 8.0F);
    private static final int OFFERED = 0x8CFFFFFF;
    private static final float OFFER_SIZE = 4.2F;
    private static final Vector3f ICON_MIDDLE = new Vector3f(8.0F, 8.0F, 8.0F);
    /**
     * Beside the badge's icon, on its left the water, on its right the heat: a gauge each as high as the icon, with a mark where the
     * drink needs it, blinking while the kettle has less.
     */
    private static final int WATER_GAUGE = 0xFF6FB4FF;
    private static final int HEAT_GAUGE = 0xFFFF8A3C;
    private static final int MARK_COLOR = 0xFFFFFFFF;
    private static final int GAUGE_TOP = -8;
    private static final int GAUGE_HEIGHT = 16;
    private static final int GAUGE_WIDTH = 2;
    private static final int WATER_LEFT = -12;
    private static final int HEAT_LEFT = 10;
    private static final float BLINK = 0.45F;
    private static final float DONE_SWELL = 0.35F;
    private static final float DONE_TIME = 0.6F;
    private static final float READY_BREATH = 0.04F;

    private final RecipeBook book = new RecipeBook(new KettleBook());
    private final UsableCache usable = new UsableCache();
    private final ItemStack[] shown = new ItemStack[ITEM_NODES.length];
    /** Things just landed on the water, to splash where they lie once they are placed there. */
    private final boolean[] splashAt = new boolean[ITEM_NODES.length];
    /** Seconds since each thing set off from the player (below 0 while it waits its turn, NaN once in), and where it set off. */
    private final float[] arriveAge = new float[ITEM_NODES.length];
    private final Vector3f[] arriveFrom = new Vector3f[ITEM_NODES.length];
    private final KettleCups cups = new KettleCups();
    private List<Tea> teas = List.of();
    private float time;
    /** How far the kettle was in the hand last frame, and when it last landed on the stove. */
    private float lastHold;
    private float landedAt = -10.0F;
    /** How far the lid is on its way off, 0 on the kettle to 1 set down before it. */
    private float lid;
    /** The lid as the cook last clicked it, shown at once while the kettle answers; null once the kettle agrees. */
    private Boolean lidAsked;
    private float lidAskedAt;
    /** The drink that waits in the kettle, and how many bottles of it. */
    private ItemStack waiting = ItemStack.EMPTY;
    private int waitingColor = WATER_COLOR;
    private float level;
    private float bubbles;
    private float steam;
    /** Seconds left of the whistle and its jet. */
    private float jet;
    /** Drinks done (waiting and bottled) and whether one was brewing at the last look: one more after a brew is a brew done. */
    private int readyBefore = -1;
    private boolean wasBrewing;
    /** When the last drink was brewed into the kettle; long ago for none. */
    private float doneAt = -10.0F;
    private final StepBadge badge = new StepBadge(14, -10, true);
    private String drawnGauges = "";
    /** What the kettle is made hotter with, as HerbalBrews names it: what the badge asks for while a drink waits for heat. */
    private ItemStack heatItem = ItemStack.EMPTY;
    private String shownModels = "";
    /** The drink the kettle makes of what is in it, or the one that stands done: the icon of the badge. */
    private ItemStack dish = ItemStack.EMPTY;
    private Tea making;
    /** Bottles of the drink that stand done, while the kettle makes no more. */
    private int servings;
    private int liquidColor = WATER_COLOR;
    private ItemStack offered = ItemStack.EMPTY;
    private int offeredSlot = -1;
    private Item offeredItem;
    private KitchenMotions.Pour pour;

    public KettleBehavior(StationView view) {
        Arrays.fill(shown, ItemStack.EMPTY);
        Arrays.fill(arriveAge, Float.NaN);
    }

    @Override
    public void onSceneReady(StationView view) {
        drawnGauges = "";
        offeredItem = null;
        heatItem = BuiltInRegistries.ITEM.getTag(HerbalTeas.HEAT_ITEMS).flatMap(tag -> tag.stream().findFirst())
                .map(holder -> new ItemStack(holder.value())).orElse(ItemStack.EMPTY);
        shownModels = "";
        if (pour != null) {
            pour.stop();
            pour = null;
        }
        teas = HerbalTeas.all(view.level());
        badge.ready(view);
        view.node("gauge_water").ifPresent(node -> node.setTint(WATER_GAUGE));
        view.node("gauge_heat").ifPresent(node -> node.setTint(HEAT_GAUGE));
        view.node("gauge_marks").ifPresent(node -> node.setTint(MARK_COLOR));
        // The badge stays in sight over the kettle and its lid.
        for (String part : GAUGES) {
            view.node(part).ifPresent(node -> node.setOnTop(true));
        }
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        view.node("offer").ifPresent(node -> node.setOnTop(true));
        cups.ready(view);
        readyBefore = -1;
        refresh(view);
    }

    @Override
    public void onOpen(StationView view) {
        teas = HerbalTeas.all(view.level());
        usable.clear();
        view.setInventoryFilter(stack -> usable.test(stack, this::usable));
        book.opened(view);
        refresh(view);
        // What the cook came with is meant for the kettle: water is poured in, anything else of use comes to the hand.
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && view.openedByUse() && player.inventoryMenu.getCarried().isEmpty()) {
            ItemStack held = player.getMainHandItem();
            if (KettleStation.pourable(view.state(), held)) {
                sendPour(view, player.getInventory().selected);
            } else if (!held.isEmpty() && usable(held)) {
                view.takeFromInventory(player.getInventory().selected);
            }
        }
    }

    @Override
    public void onClose(StationView view) {
        book.shut(view);
        cups.clear(view);
        refresh(view);
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        refresh(view);
    }

    /**
     * The lid is still on its way back after the last one left, or a bottle still being filled: the scene stands in for the block
     * until both are done.
     */
    @Override
    public boolean holdsBlock(StationView view) {
        return !view.isInUse() && DiegesisClient.isSceneDrawn(view.pos()) && (lid > 0.0F || cups.busy());
    }

    /** What the inventory at the kettle shows: water, glass bottles, what feeds the heat and what some drink is made of. */
    private boolean usable(ItemStack stack) {
        return HerbalTeas.waterOf(stack) > 0 || stack.is(Items.GLASS_BOTTLE) || KettleStation.isEmber(stack)
                || teas.stream().anyMatch(tea -> tea.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack)));
    }

    private static void sendPour(StationView view, int from) {
        CompoundTag how = new CompoundTag();
        if (from >= 0) {
            how.putInt(KettleStation.FROM, from);
        }
        view.sendAction(KettleStation.ACTION_POUR, how);
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (!(event instanceof SceneEvent.Click click)) {
            return;
        }
        if (click.node().equals("book_open")) {
            book.click(view, click.button());
        } else if (click.button() == 0 && click.node().equals("lid")) {
            toggleLid(view);
        } else if (click.button() == 0 && click.node().equals("spout")) {
            // An empty hand fills a glass bottle from the inventory.
            LocalPlayer player = Minecraft.getInstance().player;
            int slot = player == null ? -1 : glassSlot(player.getInventory());
            if (slot >= 0 && !waiting.isEmpty()) {
                draw(view, player.getInventory().getItem(slot), false, false);
            }
        } else if (click.button() == 0 && click.node().equals("dip") && !offered.isEmpty()) {
            sendPour(view, offeredSlot);
        }
    }

    /** Whether the lid is off: as the cook clicked it, until the kettle says so too. */
    private boolean isLidOff(StationView view) {
        boolean off = KettleStation.isOpen(view.state());
        if (lidAsked != null && (lidAsked == off || time - lidAskedAt > 1.0F)) {
            lidAsked = null;
        }
        return lidAsked != null ? lidAsked : off;
    }

    private void toggleLid(StationView view) {
        boolean off = !isLidOff(view);
        lidAsked = off;
        lidAskedAt = time;
        CompoundTag data = new CompoundTag();
        data.putBoolean(KettleStation.OPEN, off);
        view.sendAction(KettleStation.ACTION_LID, data);
    }

    /**
     * Pours at the spout into {@code bottles}: one, or with {@code all} as many as wait. The bottle comes from the hand
     * ({@code fromHand}) or the inventory, and the kettle is picked up and tipped over it.
     */
    private void draw(StationView view, ItemStack bottles, boolean fromHand, boolean all) {
        CompoundTag data = new CompoundTag();
        data.putBoolean(KettleStation.ALL, all);
        if (!fromHand) {
            data.putBoolean(KettleStation.ANY, true);
        }
        view.sendAction(KettleStation.ACTION_DRAW, data);
        ItemStack poured = bottles.copyWithCount(all ? Math.min(bottles.getCount(), waiting.getCount()) : 1);
        cups.pour(view, poured, waiting.copyWithCount(1), null, waitingColor, fromHand, all);
    }

    /** The inventory slot of the first glass bottle, the one in hand first; -1 for none. */
    private static int glassSlot(Inventory inventory) {
        if (inventory.getSelected().is(Items.GLASS_BOTTLE)) {
            return inventory.selected;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (inventory.getItem(slot).is(Items.GLASS_BOTTLE)) {
                return slot;
            }
        }
        return -1;
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

    /**
     * A bucket or bottle of water clicked on the opening is poured in while the kettle takes it; anything else goes in as an
     * ingredient, also when every place holds other things that still make a drink with it. Bottles and what feeds the heat go to
     * their own places.
     */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ("book_open".equals(node)) {
            book.click(view, button);
            return true;
        }
        if ("lid".equals(node)) {
            toggleLid(view);
            return true;
        }
        if ("spout".equals(node)) {
            if (stack.is(Items.GLASS_BOTTLE) && !waiting.isEmpty() && button == 0) {
                draw(view, stack, true, shift);
            }
            return true;
        }
        if (!"mouth".equals(node) && !"water".equals(node) && !"dip".equals(node)) {
            return false;
        }
        if (HerbalTeas.waterOf(stack) > 0) {
            // Water is only ever poured: a full kettle takes none, and none goes in as a thing.
            if (KettleStation.pourable(view.state(), stack)) {
                sendPour(view, -1);
            }
            return true;
        }
        return "mouth".equals(node) && FullZone.offer(view, KettleStation.INGREDIENTS, stack, button);
    }

    /** Shift + click on water in the inventory pours it in; everything else goes to its place as usual. */
    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        if (HerbalTeas.waterOf(stack) <= 0) {
            return false;
        }
        if (KettleStation.pourable(view.state(), stack)) {
            sendPour(view, slot);
        }
        return true;
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        if (KettleStation.EFFECT_POURED.equals(effect)) {
            poured(view, data);
        } else if (KettleStation.EFFECT_BREWED.equals(effect)) {
            // A drink is brewed: it waits in the kettle, the badge swells with a chime; the kettle whistles by what it holds.
            doneAt = time;
            view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.3F, 1.26F);
        } else if (KettleStation.EFFECT_DRAWN.equals(effect)) {
            // Drinks poured at the spout: the cook's own bottle goes on, somebody else's comes now.
            Item drink = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(data.getString(KettleStation.VESSEL)));
            if (drink != null && drink != Items.AIR) {
                ItemStack made = new ItemStack(drink);
                cups.served(new ItemStack(Items.GLASS_BOTTLE), made, null, 0xFF000000 | FoodColors.main(made), data.getInt(KettleStation.COUNT));
            }
        } else if (KettleStation.EFFECT_FLARED.equals(effect)) {
            // The kettle burned one of what feeds its heat: it flares at its foot.
            view.spawnParticles(DiegesisParticles.FLAME, "embers", 10, 0xFFFFFFFF);
            view.spawnParticles(DiegesisParticles.SPARK, "embers", 6, 0xFFFFC870);
            view.playSound(SoundEvents.BLAZE_SHOOT, "embers", 0.25F, 1.5F);
        }
    }

    /**
     * Water was poured in at the opening: its vessel is tipped over it and the water runs in a stream, the level waiting for it,
     * and the empty vessel is taken away. For those who only watch, it splashes.
     */
    private void poured(StationView view, CompoundTag data) {
        Item vessel = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(data.getString(KettleStation.VESSEL)));
        NodeView hand = view.node("pour_hand").orElse(null);
        if (hand == null || vessel == null || vessel == Items.AIR || !view.isFocused()) {
            view.node("water").ifPresent(water -> water.ripple(0.0F, 0.0F, 0.6F));
            view.spawnOnLiquid(DiegesisParticles.SPLASH, "water", 6, WATER_COLOR, RISE);
            view.playSound(SoundEvents.BUCKET_EMPTY, "water", 0.6F, 1.1F);
            return;
        }
        if (pour != null) {
            pour.stop();
        }
        // A bucket comes back empty; of a bottle the kettle leaves nothing, and it is shown as plain glass, not as a potion.
        Item left = KitchenMotions.containerOf(new ItemStack(vessel)).orElse(null);
        ItemStack full = new ItemStack(left == null ? Items.GLASS_BOTTLE : vessel);
        ItemStack empty = left == null ? ItemStack.EMPTY : new ItemStack(left);
        Vector3f onto = new Vector3f(HALF, surfaceY(), HALF);
        Vector3f from = view.cursorOn(RIM + POUR_FROM_ABOVE).orElse(new Vector3f(HALF, RIM + POUR_FROM_ABOVE, HALF - 4.0F));
        pour = new KitchenMotions.Pour(hand, "kettle_pour", full, empty, WATER_COLOR, from, onto, this::surfaceY, "water", POUR_SIZE,
                -1.0F, CAMERA_YAW).lipAtLeast(RIM + POUR_OVER_RIM);
        float before = pour.pouredBy() - pour.flowTime();
        float flow = pour.flowTime();
        view.node("water").ifPresent(water -> water.holdLevel(before, flow));
    }

    private float surfaceY() {
        return FLOOR + (FULL - FLOOR) * level;
    }

    /** How full the kettle looks: its water, and the drinks that wait in it. */
    private static float levelOf(StationView view) {
        int drinks = view.state().data().getInt(KettleStation.DRINKS);
        return Math.min(1.0F, (KettleStation.water(view.state()) + drinks * WATER_OF_A_DRINK) / (float) Kettle.FULL);
    }

    /** How far the bottle that brews is, 0 to 1; 0 while none does. */
    private static float progress(StationView view) {
        CompoundTag data = view.state().data();
        int total = data.getInt(KettleStation.TOTAL);
        if (!data.contains(KettleStation.STARTED) || total <= 0) {
            return 0.0F;
        }
        return Mth.clamp((float) (view.level().getGameTime() - data.getLong(KettleStation.STARTED)) / total, 0.001F, 1.0F);
    }

    /** Ticks until the bottle that brews is done; 0 while none does. */
    private static int ticksLeft(StationView view) {
        CompoundTag data = view.state().data();
        if (!data.contains(KettleStation.STARTED)) {
            return 0;
        }
        return (int) Math.max(1L, data.getLong(KettleStation.STARTED) + data.getInt(KettleStation.TOTAL) - view.level().getGameTime());
    }

    private void refresh(StationView view) {
        StationState state = view.state();
        ItemStack[] items = state.getItems(KettleStation.INGREDIENTS);
        List<Integer> arrivals = new ArrayList<>();
        for (int i = 0; i < ITEM_NODES.length && i < items.length; i++) {
            if (!items[i].isEmpty() && shown[i].isEmpty() && view.isFocused()) {
                arrivals.add(i);
            }
            if (items[i].isEmpty()) {
                arriveAge[i] = Float.NaN;
            }
            shown[i] = items[i].copy();
        }
        if (arrivals.size() > 1) {
            float height = surfaceY() + ARRIVE_FROM;
            for (int k = 0; k < arrivals.size(); k++) {
                int index = arrivals.get(k);
                arriveAge[index] = -k * ARRIVE_STAGGER;
                arriveFrom[index] = FromPlayer.point(view, height, k, arrivals.size()).orElse(new Vector3f(HALF, height, HALF));
            }
        } else {
            arrivals.forEach(index -> splashIn(view, index));
        }
        // The drink that waits in the kettle, to be poured at the spout.
        Item drink = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(state.data().getString(KettleStation.DRINK)));
        int drinks = state.data().getInt(KettleStation.DRINKS);
        waiting = drink == null || drink == Items.AIR || drinks <= 0 ? ItemStack.EMPTY : new ItemStack(drink, drinks);
        if (!waiting.isEmpty()) {
            waitingColor = WATER_COLOR & 0xFF000000 | FoodColors.main(waiting) & 0xFFFFFF;
        }
        // What the badge shows: the drink the kettle makes, else the drinks that wait in it or stand done in bottles.
        String id = state.data().getString(KettleStation.MAKING);
        making = teas.stream().filter(tea -> tea.id().toString().equals(id)).findFirst().orElse(null);
        ItemStack served = state.getItem(KettleStation.SERVED_SLOT);
        dish = making != null ? making.result() : !waiting.isEmpty() ? waiting : served;
        servings = making == null ? waiting.getCount() + served.getCount() : 0;
        // One more drink done after a brew: it whistles, for whoever is near, at the station or passing by.
        int ready = waiting.getCount() + served.getCount();
        boolean brewing = state.data().contains(KettleStation.STARTED) || state.data().getBoolean(KettleStation.BREWING);
        if (readyBefore >= 0 && ready > readyBefore && wasBrewing) {
            done(view);
        }
        readyBefore = ready;
        wasBrewing = brewing;
    }

    /**
     * A brew is done: with the lid on, the kettle whistles and a jet of steam shoots from its spout, as Hands-On Delight's kettle
     * does; with it off, the steam only rises from the opening.
     */
    private void done(StationView view) {
        if (view.state().data().getInt(KettleStation.HEAT) <= 0) {
            return;
        }
        if (!(view.isInUse() && isLidOff(view))) {
            jet = JET_TIME;
        } else if (view.isBlockHidden()) {
            view.spawnOnLiquid(DiegesisParticles.STEAM, "water", 8, 0xA0FFFFFF, STEAM_OUT);
        } else {
            view.spawnParticles(DiegesisParticles.STEAM, new Vector3f(HALF, RIM, HALF), STEAM_OUT, 0.6F, 8, 0xA0FFFFFF);
        }
    }

    /** Something lands in the kettle: a splash on the water and a plop, or a knock on the metal of a dry kettle. */
    private void splashIn(StationView view, int index) {
        if (KettleStation.water(view.state()) == 0) {
            view.playSound(SoundEvents.COPPER_HIT, ITEM_NODES[index], 0.3F, 1.3F + 0.05F * index);
            return;
        }
        splashAt[index] = true;
        view.playSound(PotSounds.PLOP, ITEM_NODES[index], 0.4F, 1.0F + 0.04F * index);
    }

    private static int blend(int from, int to, float amount) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            result |= Math.round(Mth.lerp(amount, from >>> shift & 0xFF, to >>> shift & 0xFF)) << shift;
        }
        return result;
    }

    @Override
    public void frame(StationView view, float dt) {
        time += dt;
        boolean inUse = view.isInUse();
        CompoundTag data = view.state().data();
        float progress = progress(view);
        // Nobody at it, a client knows only that a brew goes on, not how far: the station's count is the server's.
        boolean brewing = progress > 0.0F || data.getBoolean(KettleStation.BREWING);
        int heat = data.getInt(KettleStation.HEAT);
        // The scene's kettle exactly while the block's own is left out of the world: the two swap in one frame.
        boolean open = view.isBlockHidden();
        showKettle(view, dt, open, inUse);
        level = Mth.lerp(Math.min(1.0F, dt * LEVEL_FOLLOW), level, levelOf(view));
        boolean afloat = level > 0.02F;
        // What floats in it is not drawn while the kettle is in the hand, as its water.
        boolean inKettle = open && cups.hold() <= 0.01F;
        floaters(view, dt, inKettle, inKettle && afloat, afloat ? surfaceY() : FLOOR);
        book.frame(view, dt);
        liquidLife(view, brewing, progress);
        heat(view, dt, open, inUse, heat, brewing, progress);
        if (pour != null && !pour.frame(view, dt)) {
            pour = null;
        }
        pourAtSpout(view, dt);
        offer(view);
        // The badge comes with the camera as the book does, growing as it flies in and going as it flies back.
        float appear = RecipeBook.appear(view);
        StepBadge.Step step = step(view, progress, heat);
        boolean shown = inUse && RecipeBook.isWorkedAt(view) && appear > RecipeBook.SPECK && step != null;
        badge.show(view, shown, step, GAUGE_TRACKS, time);
        if (shown) {
            gauges(view, heat);
        }
        float swell = badgeSwell();
        badge.scale(view, appear * swell);
        for (String part : GAUGES) {
            view.node(part).ifPresent(node -> {
                node.setVisible(shown);
                node.scaleTo(appear * swell, 0.0F);
            });
        }
    }

    /**
     * Bottles filled at the spout ({@link KettleCups}): the kettle is picked up, tipped over each with its spout going down and set
     * down after. What floats in it and its water are not drawn while it is in the hand.
     */
    private void pourAtSpout(StationView view, float dt) {
        float hold = cups.hold();
        float tip = cups.tip();
        if (hold > 0.0F && lastHold <= 0.0F) {
            view.playSound(SoundEvents.LANTERN_HIT, "spout", 0.3F, 1.45F);
        } else if (hold <= 0.0F && lastHold > 0.0F) {
            landedAt = time;
            view.playSound(SoundEvents.LANTERN_PLACE, "spout", 0.35F, 1.3F);
        }
        lastHold = hold;
        Matrix4f held = held(hold, tip);
        cups.frame(view, dt, held(1.0F, 1.0F).transformPosition(new Vector3f(LIP)).add(HALF, 0.0F, HALF),
                held.transformPosition(new Vector3f(LIP)).add(HALF, 0.0F, HALF), POUR_WAY, null);
        // The node stands at the middle of the kettle's foot and is turned a quarter in the scene: its own turn is the scene's
        // turn seen from there.
        Vector3f move = held.transformPosition(new Vector3f());
        Quaternionf own = new Quaternionf(TURN).conjugate().mul(tippedOf(hold, tip)).mul(TURN);
        Vector3f euler = own.getEulerAnglesYXZ(new Vector3f());
        float scale = scaleOf(hold);
        view.node("kettle").ifPresent(node -> {
            node.moveTo(move.x, move.y, move.z, 0.0F);
            node.rotateTo(euler.x * Mth.RAD_TO_DEG, euler.y * Mth.RAD_TO_DEG, euler.z * Mth.RAD_TO_DEG, 0.0F);
            node.scaleTo(scale, 0.0F);
        });
        boolean inHand = hold > 0.01F;
        view.node("water").ifPresent(node -> node.setVisible(view.isBlockHidden() && !inHand));
    }

    /**
     * The kettle picked up {@code hold} and tipped {@code tip} of the way: where it takes a point of the kettle standing on the
     * stove (scene axes, from the middle of its foot).
     */
    private Matrix4f held(float hold, float tip) {
        float scale = scaleOf(hold);
        float out = KitchenMotions.smooth((hold - OUT_AFTER) / (1.0F - OUT_AFTER));
        // Up off the stove, then out; bouncing as it lands, its foot stays on the stove.
        Vector3f off = new Vector3f(HELD).mul(out).add(0.0F, LIFT * hold + HOP * Mth.sin(Mth.PI * hold), 0.0F)
                .add(0.0F, Math.max(0.0F, scale - 1.0F) * MIDDLE.y, 0.0F);
        return new Matrix4f().translate(off.add(MIDDLE)).rotate(tippedOf(hold, tip)).scale(scale)
                .translate(-MIDDLE.x, -MIDDLE.y, -MIDDLE.z);
    }

    /**
     * The kettle's turn in the scene's axes: turned side on as it is picked up, back a little, and its spout (+x as it stands)
     * down over the bottle.
     */
    private static Quaternionf tippedOf(float hold, float tip) {
        float lean = TILT * tip - LEAN_BACK * Mth.sin(Mth.PI * hold) * (1.0F - tip);
        return new Quaternionf().rotationY(IN_HAND_TURN * hold * Mth.DEG_TO_RAD).rotateZ(-lean * Mth.DEG_TO_RAD);
    }

    /** How big the kettle is drawn: smaller in the hand, with a bounce as it lands on the stove. */
    private float scaleOf(float hold) {
        float bounce = time - landedAt < BOUNCE_TIME ? BOUNCE * Mth.sin(Mth.PI * (time - landedAt) / BOUNCE_TIME) : 0.0F;
        return Mth.lerp(hold, 1.0F, IN_HAND) * (1.0F + bounce);
    }

    /**
     * The kettle's own parts: the iron kettle's models or the copper one's, by the block; the lid, taken off and put back by the
     * cook and put back behind the last one to leave, and riding on the kettle while it is in the hand to pour.
     */
    private void showKettle(StationView view, float dt, boolean open, boolean inUse) {
        String models = BuiltInRegistries.BLOCK.getKey(view.level().getBlockState(view.pos()).getBlock()).getPath().startsWith("copper")
                ? "copper_tea_kettle" : "tea_kettle";
        if (!models.equals(shownModels)) {
            shownModels = models;
            view.node("body").ifPresent(node -> node.setVisual(Visual.ofModel(HandsOnLetsDo.id("diegesis/" + models + "/body")).asLitAsBlock()));
            view.node("lid").ifPresent(node -> node.setVisual(Visual.ofModel(HandsOnLetsDo.id("diegesis/" + models + "/lid")).asLitAsBlock()));
        }
        for (String id : SHOWN_IN_USE) {
            view.node(id).ifPresent(node -> node.setVisible(open));
        }
        boolean off = inUse && isLidOff(view);
        float before = lid;
        lid = Mth.clamp(lid + (off ? dt : -dt) / LID_MOVE, 0.0F, 1.0F);
        if (before < 1.0F && lid >= 1.0F || before > 0.0F && lid <= 0.0F) {
            view.playSound(SoundEvents.COPPER_PLACE, "lid", 0.3F, 1.5F + view.level().random.nextFloat() * 0.1F);
        } else if (before <= 0.0F && lid > 0.0F) {
            view.playSound(SoundEvents.COPPER_HIT, "lid", 0.3F, 1.6F);
        }
        // One easing over the whole way, so that it never stops between a slide and a lift.
        float k = KitchenMotions.smooth(lid);
        // On the kettle it rides along when the kettle is in the hand (as small as the kettle); set down, it stays on the stove.
        float hold = cups.hold();
        Matrix4f kettle = held(hold, cups.tip());
        LidRest.Pose on = new LidRest.Pose(kettle.transformPosition(new Vector3f(LID_ON)), tippedOf(hold, cups.tip()).mul(TURN));
        LidRest.Pose rest = lidRest();
        // Out from under the copper kettle's bail towards the cook, up, over and down in one curve.
        Vector3f slide = kettle.transformDirection(new Vector3f(LID_SLID));
        float scale = Mth.lerp(k, scaleOf(hold), 1.0F);
        view.node("lid").ifPresent(node -> {
            node.setVisible(open);
            LidRest.place(node, on, slide, rest, k, 0.0F, new Vector3f(), LID_ON, TURN, LID_HALF + 1.5F, 90.0F, new Vector3f());
            node.scaleTo(scale, 0.0F);
        });
        // Nothing goes in through the lid.
        view.node("mouth").ifPresent(node -> node.setEnabled(off && lid >= 1.0F));
    }

    /**
     * Where the lid is set down: on its edge on the stove before the kettle, on the side away from the spout (where Hands-On
     * Delight's kettle has it, seen from the same camera), its lower edge inside the block, leaning on the kettle's side with its
     * upper edge, its knob outwards (scene axes from the kettle's middle).
     */
    private static LidRest.Pose lidRest() {
        float lean = (float) Math.asin((LID_FOOT - BODY_HALF) / (2.0F * LID_HALF));
        LidRest.Pose stood = LidRest.stood(new Vector3f(0.0F, 0.0F, -1.0F), LID_FOOT, lean, 0.0F, LID_HALF);
        return new LidRest.Pose(stood.at().add(-LID_BACK, 0.0F, 0.0F), stood.turn());
    }

    /** A drink that brews colours the water, from clear to the drink's own colour, and it is clear again for the next bottle. */
    private void liquidLife(StationView view, boolean brewing, float progress) {
        int drink = making == null ? WATER_COLOR : WATER_COLOR & 0xFF000000 | FoodColors.main(making.result()) & 0xFFFFFF;
        liquidColor = brewing ? blend(WATER_COLOR, drink, STEEPED * KitchenMotions.smooth(progress)) : WATER_COLOR;
        int color = liquidColor;
        view.node("water").ifPresent(node -> node.setLiquidColor(color));
    }

    /**
     * The kettle on its heat, as Hands-On Delight's: what stands in it simmers, steam rises from the spout (from the opening with
     * the lid off), more as a brew nears done; a drink that waits for more heat warms as the heat rises towards it; water, or a drink
     * done, left on the heat boils on, a steady plume from the spout. The boil is heard for as long as there is something in it on
     * the heat, at the station or passing by, and stops while it is in the hand; a brew done makes it whistle with a jet of steam.
     */
    private void heat(StationView view, float dt, boolean shownHere, boolean inUse, int heat, boolean brewing, float progress) {
        CompoundTag data = view.state().data();
        boolean wet = KettleStation.water(view.state()) > 0 || data.getInt(KettleStation.DRINKS) > 0;
        boolean onHeat = data.getBoolean(KettleStation.HEATED);
        int needs = data.getInt(KettleStation.NEEDS_HEAT);
        boolean warming = making != null && !brewing && heat < needs;
        float hot = Mth.clamp(heat / (float) Kettle.FULL, 0.0F, 1.0F);
        // Nobody at it, this client does not know how far a brew is, only that one goes on; off the heat it cools down.
        float strength = !wet || heat <= 0 ? 0.0F : brewing ? inUse && progress > 0.0F ? 0.35F + 0.65F * progress : 0.6F
                : warming ? 0.1F + 0.5F * heat / (float) needs : onHeat ? 1.0F : 0.5F * hot;
        boolean inHand = cups.hold() > 0.0F;
        boolean onFire = wet && heat > 0 && !inHand;
        boolean boiling = onFire && onHeat && !brewing && !warming;
        boolean lidOff = inUse && isLidOff(view);
        view.node("water").ifPresent(node -> {
            node.setAgitation(onFire ? 0.02F + 0.06F * strength : 0.005F);
            node.setEdgeBoil(onFire ? 0.1F + 0.4F * strength : 0.0F);
        });
        float rate = !onFire ? 0.0F : boiling ? STEAM_BOILING : STEAM_HOT + STEAM_BREWING * strength;
        steam += dt * rate;
        if (steam >= 1.0F) {
            int puffs = (int) steam;
            steam -= puffs;
            if (lidOff && shownHere) {
                view.spawnOnLiquid(DiegesisParticles.STEAM, "water", puffs, STEAM_COLOR, STEAM_OUT);
            } else if (lidOff) {
                view.spawnParticles(DiegesisParticles.STEAM, new Vector3f(HALF, RIM, HALF), STEAM_OUT, 0.6F, puffs, STEAM_COLOR);
            } else {
                float push = boiling ? 5.0F : 2.0F;
                view.spawnParticles(DiegesisParticles.STEAM, lipNow(), spoutWay().mul(push).add(0.0F, 3.5F, 0.0F), 0.3F, puffs,
                        boiling ? 0xB0FFFFFF : STEAM_COLOR);
            }
        }
        if (shownHere && onFire && level > 0.02F) {
            bubbles += dt * (BUBBLES_HOT + BUBBLES_BREWING * strength);
            if (bubbles >= 1.0F) {
                view.spawnInLiquid(DiegesisParticles.BUBBLE, "water", (int) bubbles, 0xE0FFFFFF, RISE);
                bubbles -= (int) bubbles;
            }
        }
        view.loopSound("boil", KettleSounds.BOIL, onFire ? BOIL_QUIETEST + BOIL_LOUDER * strength : 0.0F, 1.0F);
        if (inHand) {
            // Picked up, it is off the heat: the whistle stops.
            jet = 0.0F;
        }
        view.loopSound("whistle", KettleSounds.WHISTLE, jet > 0.0F ? WHISTLE_VOLUME * Math.min(1.0F, jet / 0.5F) : 0.0F, 1.0F);
        if (jet > 0.0F) {
            jet -= dt;
            int puffs = Math.max(1, Math.round(dt * JET_RATE * Math.min(1.0F, jet / JET_TIME * 2.0F)));
            view.spawnParticles(DiegesisParticles.STEAM, lipNow(), spoutWay().mul(14.0F).add(0.0F, 5.0F, 0.0F), 0.5F, puffs, 0xD0FFFFFF);
        }
    }

    /** The lower lip of the spout's mouth, where the kettle is now (on the stove or in the hand), scene pixels. */
    private Vector3f lipNow() {
        return held(cups.hold(), cups.tip()).transformPosition(new Vector3f(LIP)).add(HALF, 0.0F, HALF);
    }

    /** Which way the spout points now, level. */
    private Vector3f spoutWay() {
        Vector3f way = held(cups.hold(), cups.tip()).transformDirection(new Vector3f(SPOUT_WAY));
        way.y = 0.0F;
        return way.lengthSquared() > 1.0E-4F ? way.normalize() : new Vector3f(SPOUT_WAY);
    }

    /** What lies in the kettle floats on the water, a little apart, bobbing; with no water it lies on the floor. */
    private void floaters(StationView view, float dt, boolean open, boolean afloat, float surface) {
        int present = 0;
        for (ItemStack stack : shown) {
            present += stack.isEmpty() ? 0 : 1;
        }
        int rank = 0;
        for (int i = 0; i < ITEM_NODES.length; i++) {
            int index = i;
            boolean there = !shown[i].isEmpty() && open;
            float place = rank * Mth.TWO_PI / Math.max(1, present) + time * (afloat ? 0.2F : 0.0F);
            if (!shown[i].isEmpty()) {
                rank++;
            }
            float bob = afloat ? Mth.sin(time * 0.9F + index * 1.3F) * 0.06F : 0.0F;
            float radius = present > 1 ? FLOAT_RADIUS : 0.0F;
            Vector3f at = new Vector3f(Mth.cos(place) * radius, surface + 0.2F + bob, Mth.sin(place) * radius);
            boolean waiting = arrive(view, i, dt, at);
            if (splashAt[i] && !waiting) {
                splashAt[i] = false;
                Vector3f where = new Vector3f(at).add(HALF, 0.0F, HALF);
                view.spawnParticles(DiegesisParticles.SPLASH, where, new Vector3f(0.0F, 8.0F, 0.0F), 1.0F, 5, 0xFF000000 | liquidColor);
                ringsAt(view, where, LANDING_RINGS);
            }
            view.node(ITEM_NODES[i]).ifPresent(node -> {
                node.setVisible(open);
                node.setEnabled(there && !waiting);
                node.moveTo(at.x, at.y, at.z, 0.0F);
                node.rotateTo(Mth.sin(time * 0.5F + index) * 3.0F, -place * Mth.RAD_TO_DEG, 0.0F, 0.0F);
            });
        }
    }

    /** Rings across the water from a point of the scene (pixels), as strong as {@code strength}. */
    private static void ringsAt(StationView view, Vector3f where, float strength) {
        view.node("water").ifPresent(water -> {
            Vector3f local = new org.joml.Matrix4f(water.sceneMatrix()).invert().transformPosition(new Vector3f(where));
            water.ripple(local.x, local.z, strength);
        });
    }

    /**
     * A thing on its way in from the player: moves {@code at} (offset from the node of the item, pixels) along its arc from where
     * it set off to its place on the water, and splashes as it lands. True while it still waits for its turn.
     */
    private boolean arrive(StationView view, int index, float dt, Vector3f at) {
        if (Float.isNaN(arriveAge[index])) {
            return false;
        }
        arriveAge[index] += dt;
        if (arriveAge[index] < 0.0F) {
            return true;
        }
        float k = arriveAge[index] / ARRIVE_TIME;
        if (k >= 1.0F) {
            arriveAge[index] = Float.NaN;
            splashIn(view, index);
            return false;
        }
        Vector3f from = new Vector3f(arriveFrom[index]).sub(HALF, 0.0F, HALF);
        float eased = KitchenMotions.smooth(k);
        at.set(Mth.lerp(eased, from.x, at.x), Mth.lerp(k, from.y, at.y) + ARRIVE_ARC * Mth.sin(k * Mth.PI), Mth.lerp(eased, from.z, at.z));
        return false;
    }

    /**
     * With an empty hand over the opening, a bucket or bottle of water from the inventory that the kettle takes hangs see-through
     * over it: a click pours it in.
     */
    private void offer(StationView view) {
        offered = ItemStack.EMPTY;
        offeredSlot = -1;
        NodeView node = view.node("offer").orElse(null);
        LocalPlayer player = Minecraft.getInstance().player;
        boolean free = player != null && player.inventoryMenu.getCarried().isEmpty() && view.isFocused() && pour == null && lid >= 1.0F
                && !cups.busy();
        int slot = free ? waterSlot(view, player.getInventory()) : -1;
        view.node("dip").ifPresent(dip -> dip.setEnabled(slot >= 0));
        boolean over = slot >= 0 && view.node("dip").map(NodeView::isHovered).orElse(false);
        if (node == null) {
            return;
        }
        node.setVisible(over);
        if (!over) {
            return;
        }
        offered = player.getInventory().getItem(slot);
        offeredSlot = slot;
        Item item = offered.getItem();
        if (offeredItem != item) {
            offeredItem = item;
            node.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
            node.setTint(OFFERED);
            node.scaleTo(OFFER_SIZE / 16.0F / OFFER_BASE, 0.0F);
        }
        // Under the cursor, over the point of the opening it points at, ready to be tipped.
        float lift = 1.0F + OFFER_SIZE * 0.5F;
        Vector3f at = CursorAim.clamped(view, RIM, lift, 5.5F, 10.5F, 5.5F, 10.5F).orElse(new Vector3f(HALF, RIM + lift, HALF));
        node.moveTo(at.x - OFFER_ORIGIN.x, at.y - OFFER_ORIGIN.y, at.z - OFFER_ORIGIN.z, 0.0F);
    }

    /** The inventory slot whose water the kettle takes now: the one in hand first; -1 without one. */
    private static int waterSlot(StationView view, Inventory inventory) {
        if (KettleStation.pourable(view.state(), inventory.getSelected())) {
            return inventory.selected;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (KettleStation.pourable(view.state(), inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    /** How much bigger the badge is drawn now: a swell as a bottle gets done, a slow breath while drinks wait to be taken. */
    private float badgeSwell() {
        float since = time - doneAt;
        if (since >= 0.0F && since < DONE_TIME) {
            return 1.0F + DONE_SWELL * Mth.sin(Mth.PI * since / DONE_TIME);
        }
        return servings > 0 ? 1.0F + READY_BREATH * (0.5F - 0.5F * Mth.cos(time * 2.4F)) : 1.0F;
    }

    /** The gauges' own frames on the badge's track. */
    private static final StepBadge.Extras GAUGE_TRACKS = new StepBadge.Extras() {
        @Override
        public String key() {
            return "gauges";
        }

        @Override
        public void track(MeshShape.Builder track) {
            Badge.pixels(track, WATER_LEFT, GAUGE_TOP, GAUGE_WIDTH, GAUGE_HEIGHT);
            Badge.pixels(track, HEAT_LEFT, GAUGE_TOP, GAUGE_WIDTH, GAUGE_HEIGHT);
        }
    };

    /**
     * What the badge shows: while the kettle holds what makes a drink, what it lacks (water poured in, or what makes it hotter, as
     * HerbalBrews names it, while it is not as hot as the drink needs), blinking while the cook has none, else the drink, how far its
     * bottle has brewed and the time left; once done, the empty bottle the drink is poured into, blinking while the cook has none. Ready:
     * the drinks that stand done; still to come: the one that brews. Null for nothing.
     */
    private StepBadge.Step step(StationView view, float progress, int heat) {
        if (making != null) {
            CompoundTag data = view.state().data();
            ItemStack tea = making.result();
            int ready = servings;
            if (KettleStation.water(view.state()) < data.getInt(KettleStation.NEEDS_WATER)) {
                boolean dry = StepBadge.lacks(stack -> KettleStation.pourable(view.state(), stack));
                return new StepBadge.Step(new ItemStack(Items.WATER_BUCKET), null, dry, ready, 1, progress, 0, false, tea.getHoverName());
            }
            if (heat < data.getInt(KettleStation.NEEDS_HEAT) && !heatItem.isEmpty()) {
                boolean cold = StepBadge.lacks(stack -> stack.is(HerbalTeas.HEAT_ITEMS));
                return new StepBadge.Step(heatItem, null, cold, ready, 1, progress, 0, false, tea.getHoverName());
            }
            int left = ticksLeft(view);
            int seconds = left > 0 ? Math.max(1, Mth.ceil(left / 20.0F)) : 0;
            Component name = tea.getHoverName();
            Component tip = seconds > 0 ? Component.translatable("handson_letsdo.barrel.seconds", name, seconds) : name;
            return new StepBadge.Step(tea, null, false, ready, 1, progress, seconds, false, tip);
        }
        if (servings > 0) {
            boolean noBottle = StepBadge.lacks(stack -> stack.is(Items.GLASS_BOTTLE));
            return new StepBadge.Step(new ItemStack(Items.GLASS_BOTTLE), null, noBottle, servings, 0, 1.0F, 0, false, dish.getHoverName());
        }
        return null;
    }

    /**
     * The gauges beside the icon: water on the left and heat on the right, each lit from its foot as high as the kettle has, with
     * a mark across it where the drink it makes needs it; the mark blinks while the kettle has less.
     */
    private void gauges(StationView view, int heat) {
        int water = KettleStation.water(view.state());
        CompoundTag data = view.state().data();
        int needsWater = making == null ? 0 : data.getInt(KettleStation.NEEDS_WATER);
        int needsHeat = making == null ? 0 : data.getInt(KettleStation.NEEDS_HEAT);
        boolean blink = (int) (time / BLINK) % 2 == 0;
        boolean waterMark = needsWater > 0 && (water >= needsWater || blink);
        boolean heatMark = needsHeat > 0 && (heat >= needsHeat || blink);
        String drawn = water + "/" + heat + "/" + needsWater + "/" + needsHeat + "/" + waterMark + "/" + heatMark;
        if (drawn.equals(drawnGauges)) {
            return;
        }
        drawnGauges = drawn;
        gauge(view, "gauge_water", WATER_LEFT, water);
        gauge(view, "gauge_heat", HEAT_LEFT, heat);
        MeshShape.Builder marks = MeshShape.builder();
        if (waterMark) {
            Badge.pixels(marks, WATER_LEFT - 1, markRow(needsWater), GAUGE_WIDTH + 2, 1);
        }
        if (heatMark) {
            Badge.pixels(marks, HEAT_LEFT - 1, markRow(needsHeat), GAUGE_WIDTH + 2, 1);
        }
        view.node("gauge_marks").ifPresent(node -> node.setShape(waterMark || heatMark ? marks.build() : null, GAUGE_TEXTURE, true));
    }

    private static void gauge(StationView view, String id, int left, int amount) {
        int lit = Mth.clamp(Math.round(GAUGE_HEIGHT * amount / (float) Kettle.FULL), amount > 0 ? 1 : 0, GAUGE_HEIGHT);
        MeshShape.Builder shape = MeshShape.builder();
        if (lit > 0) {
            Badge.pixels(shape, left, GAUGE_TOP + GAUGE_HEIGHT - lit, GAUGE_WIDTH, lit);
        }
        view.node(id).ifPresent(node -> node.setShape(lit > 0 ? shape.build() : null, GAUGE_TEXTURE, true));
    }

    /** The row of a gauge a mark for {@code amount} of a hundred lies on: the top of what the gauge is lit to with that much. */
    private static int markRow(int amount) {
        int lit = Mth.clamp(Math.round(GAUGE_HEIGHT * amount / (float) Kettle.FULL), 1, GAUGE_HEIGHT);
        return GAUGE_TOP + GAUGE_HEIGHT - lit;
    }
}
