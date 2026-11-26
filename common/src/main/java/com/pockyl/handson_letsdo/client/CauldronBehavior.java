package com.pockyl.handson_letsdo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.Floater;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.station.Cauldron;
import com.pockyl.handson_letsdo.station.CauldronBrews;
import com.pockyl.handson_letsdo.station.CauldronStation;
import com.pockyl.handson_letsdo.station.CauldronTold;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The brewing cauldron at the station and in the world. The potions are the brew: each one poured fills a third of the cauldron and
 * floats in it in its own colour, patches of each going round in the others; once the infusion is thrown in they mix as it brews,
 * the patches thinning out and the whole taking the colour the flask will have. The cauldron's own stick stirs: taken by its upper
 * part, its foot follows the hand round the brew, which swirls after it, and the server hurries the brew by the way it went round.
 * The flask that is done is the brew too, in the colour that flask has, glinting until it is filled at the cauldron for the cook. An empty hand over the
 * opening is offered what the step takes from the inventory, see-through: a potion, the infusion, or the flask to fill.
 */
final class CauldronBehavior implements StationBehavior {
    /** The brew's own liquid: white, coloured by the code. */
    static final ResourceLocation LIQUID = HandsOnLetsDo.id("cauldron_brew");

    /** The cauldron's own heights, pixels: the shaft's floor, the brew of three potions (where the block draws its brew), the rim. */
    private static final float FLOOR = 5.0F;
    private static final float FULL = 13.0F;
    private static final float RIM = 16.0F;
    private static final float HALF = 8.0F;
    /**
     * The stick at rest, as the block shows it: its foot on the floor near the west wall, leaning on the opening's edge there, nearly
     * upright, so that it hides little of the brew from the camera.
     */
    private static final Vector3f REST_FOOT = new Vector3f(-1.4F, FLOOR, 1.0F);
    private static final Vector3f REST_AXIS = new Vector3f(-2.6F, 11.0F, 0.0F).normalize();
    /** In the hand its foot goes round just over the floor, and the hand holds it over the rim, as far out again as a third. */
    private static final float STIR_FOOT = FLOOR + 0.6F;
    private static final float HANDLE = RIM + 1.0F;
    private static final float HANDLE_OUT = 4.0F / CauldronStation.STICK_REACH;
    /** The fastest the foot follows the hand, pixels a second; how fast the hand takes the stick up and lets it rest. */
    private static final float FOOT_SPEED = 45.0F;
    private static final float GRIP_RATE = 7.0F;
    /** A press shorter than this takes the stick: it stirs as the mouse goes, until the next click. */
    private static final float TAKE_CLICK = 0.3F;
    /** The hand's place goes to the server this often, seconds, while it moves. */
    private static final float SEND_INTERVAL = 0.1F;

    /**
     * The brew before it is mixed: the last potion poured on top, the one before in patches along the walls and further in, the first in
     * drops; how uneven it looks.
     */
    private static final float UNMIXED_FOAM = 0.45F;
    private static final int DROPS = 6;
    private static final float DROP_SIZE = 2.4F;
    private static final float UNMIXED_MOTTLE = 0.25F;
    /** How fast the brew goes round by itself while it brews over the fire, radians a second, and with a stick through it. */
    private static final float SIMMER = 0.35F;
    private static final float STIR_CURRENT = 0.7F;
    private static final float STIR_VOLUME = 0.6F;
    /** The infusion afloat, pixels from the middle; its size as the scene draws it. */
    private static final float SACHET_OUT = 2.3F;

    /** The bottle a potion is poured from, the flask and the bottle dipped, the infusion thrown: pixels high. */
    private static final float POUR_SIZE = 4.6F;
    private static final float DIP_SCALE = 0.6F;
    private static final float THROW_TIME = 0.45F;
    private static final float THROW_ARC = 6.0F;
    private static final Vector3f DIP_ORIGIN = new Vector3f(8.0F, 18.0F, 8.0F);
    private static final Vector3f THROW_ORIGIN = new Vector3f(8.0F, 18.0F, 8.0F);
    private static final Vector3f OFFER_ORIGIN = new Vector3f(8.0F, 18.0F, 8.0F);
    private static final Vector3f ICON_MIDDLE = new Vector3f(8.0F, 8.0F, 8.0F);
    private static final float OFFER_SIZE = 4.2F;
    private static final float OFFER_BASE = 0.35F;
    private static final int OFFERED = 0x8CFFFFFF;
    /** The flask goes down into the brew see-through, as glass with nothing in it, and comes up full. */
    private static final int EMPTY_GLASS = 0x90FFFFFF;

    private static final float DONE_SWELL = 0.35F;
    private static final float DONE_TIME = 0.6F;
    private static final float READY_BREATH = 0.04F;

    /** The nodes a stack on the cursor is brought to the cauldron at. */
    private static final Set<String> INSIDE = Set.of("mouth", "dip", "stick", "sachet");

    private final RecipeBook book = new RecipeBook(new CauldronBook());
    private final StepBadge badge = new StepBadge(10, -10, true);
    private final UsableCache usable = new UsableCache();
    private float time;

    /** The local cook's hand is on the stick; a click took it, so the next click lets go; when the press began. */
    private boolean stroking;
    private boolean heldByClick;
    private float strokeStart;
    /** Where the stick's foot is in the brew, pixels from the middle in the cauldron's own axes; how far it is in a hand, 0 to 1. */
    private float footX;
    private float footZ;
    private float grip;
    /** How fast the foot goes round the middle, radians a second, smoothed; its angle in the last frame. */
    private float turning;
    private float lastAngle = Float.NaN;
    private float sendClock;
    private float sentX = Float.NaN;
    private float sentZ = Float.NaN;
    /** How the brew goes round now, radians a second, and how far round the infusion has been carried. */
    private float current;
    private float carried;

    /** The cauldron as the block stands, against the scene that faces the cook. */
    private float shownTurn = Float.NaN;
    private final Matrix4f inverse = new Matrix4f();

    /** Potions shown in the brew: a poured one shows once its stream runs. */
    private int shownPotions = -1;
    private final ItemStack[] potions = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private int count;
    private ItemStack flask = ItemStack.EMPTY;
    private ItemStack catalyst = ItemStack.EMPTY;
    private int catalystBefore = -1;
    private String floatersKey = "";
    private int shownColor = -1;

    private KitchenMotions.Pour pour;
    private final Deque<CompoundTag> pours = new ArrayDeque<>();
    private KitchenMotions.Dip dip;
    /** A vessel goes into the brew: it stays as it was until the vessel is under (a third more, or all of it for the flask). */
    private int heldPotions;
    private boolean heldFull;
    /** The infusion on its way in: how long it has flown, from where (scene pixels); NaN while none flies. */
    private float thrownAge = Float.NaN;
    private final Vector3f thrownFrom = new Vector3f();

    private ItemStack offered = ItemStack.EMPTY;
    private int offeredSlot = -1;
    private Item offeredItem;
    private float doneAt = -10.0F;
    private float blubClock;
    private float glintClock;

    CauldronBehavior(StationView view) {
    }

    @Override
    public void onSceneReady(StationView view) {
        badge.ready(view);
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        view.node("offer").ifPresent(node -> node.setOnTop(true));
        shownTurn = Float.NaN;
        offeredItem = null;
        floatersKey = "";
        shownColor = -1;
        shownPotions = -1;
        catalystBefore = -1;
        stopMotions();
        footX = REST_FOOT.x;
        footZ = REST_FOOT.z;
        refresh(view);
    }

    @Override
    public void onOpen(StationView view) {
        usable.clear();
        view.setInventoryFilter(stack -> usable.test(stack, it -> Cauldron.isPotion(it) || Cauldron.isCatalyst(it) || it.is(Items.GLASS_BOTTLE)));
        book.opened(view);
        refresh(view);
        // A potion the cook came with is poured in; anything else stays in its slot.
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && view.openedByUse() && player.inventoryMenu.getCarried().isEmpty() && pourable(player.getMainHandItem())) {
            send(view, CauldronStation.ACTION_POUR, player.getInventory().selected, false);
        }
    }

    @Override
    public void onClose(StationView view) {
        stroking = false;
        heldByClick = false;
        book.shut(view);
        refresh(view);
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        refresh(view);
    }

    /** The scene is the cauldron in the world wherever it is drawn, where the server told the cauldron what it holds. */
    @Override
    public boolean holdsBlock(StationView view) {
        return DiegesisClient.isSceneDrawn(view.pos()) && view.level().getBlockEntity(view.pos()) instanceof CauldronTold told
                && told.handson$told();
    }

    private void refresh(StationView view) {
        count = 0;
        for (int slot = 0; slot < Cauldron.POTIONS; slot++) {
            ItemStack potion = view.state().getItem(SlotRef.of(CauldronStation.POTIONS, slot));
            potions[slot] = potion;
            if (!potion.isEmpty()) {
                count++;
            }
        }
        flask = view.state().getItem(CauldronStation.FLASK_SLOT);
        catalyst = view.state().getItem(CauldronStation.CATALYST_SLOT);
        if (shownPotions < 0) {
            shownPotions = count;
        }
        // The infusion came: it is thrown in from the cook's side.
        if (catalystBefore >= 0 && catalyst.getCount() > catalystBefore && Float.isNaN(thrownAge)) {
            thrownAge = 0.0F;
            thrownFrom.set(FromPlayer.point(view, RIM + 4.0F, 0, 1).orElse(new Vector3f(HALF, RIM + 10.0F, -2.0F)));
        }
        catalystBefore = catalyst.getCount();
    }

    // Clicks.

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Stroke stroke && "stick".equals(stroke.node())) {
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
                        heldByClick = false;
                    } else if (time - strokeStart < TAKE_CLICK) {
                        heldByClick = true;
                        view.holdStroke();
                    }
                }
                default -> {
                }
            }
        } else if (event instanceof SceneEvent.Click click && "book_open".equals(click.node())) {
            book.click(view, click.button());
        } else if (event instanceof SceneEvent.Click click && "dip".equals(click.node()) && click.button() == 0) {
            if (!flask.isEmpty()) {
                view.sendAction(CauldronStation.ACTION_TAKE, new CompoundTag());
            } else if (offeredSlot >= 0 && Cauldron.isCatalyst(offered)) {
                send(view, CauldronStation.ACTION_THROW, offeredSlot, Screen.hasShiftDown());
            } else if (offeredSlot >= 0) {
                send(view, CauldronStation.ACTION_POUR, offeredSlot, false);
            }
        }
    }

    @Override
    public boolean onKey(StationView view, int keyCode, int scanCode, int modifiers) {
        return book.key(view, keyCode, modifiers);
    }

    @Override
    public boolean onChar(StationView view, char character, int modifiers) {
        return book.typed(view, character);
    }

    @Override
    public boolean onScroll(StationView view, String node, double amount) {
        return "book_open".equals(node) && book.scroll(amount);
    }

    /**
     * A stack brought to the cauldron, its opening or anything in it (the stick over the opening, the infusion afloat): a potion is
     * poured, a glass bottle draws the last potion back while nothing is brewed of it, the infusion is thrown in (the stack, or one with
     * a right click). Anything else the cauldron does not take.
     */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ("book_open".equals(node)) {
            book.click(view, button);
            return true;
        }
        if (!INSIDE.contains(node)) {
            return false;
        }
        if (Cauldron.isPotion(stack)) {
            if (pourable(stack)) {
                view.sendAction(CauldronStation.ACTION_POUR, new CompoundTag());
            }
        } else if (stack.is(Items.GLASS_BOTTLE)) {
            if (count > 0 && flask.isEmpty()) {
                view.sendAction(CauldronStation.ACTION_DRAW, new CompoundTag());
            }
        } else if (Cauldron.isCatalyst(stack)) {
            send(view, CauldronStation.ACTION_THROW, -1, button == 0);
        }
        return true;
    }

    /** Shift + click on a potion in the inventory pours it in; everything else goes to its place as usual. */
    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        if (!Cauldron.isPotion(stack)) {
            return false;
        }
        if (pourable(stack)) {
            send(view, CauldronStation.ACTION_POUR, slot, false);
        }
        return true;
    }

    private boolean pourable(ItemStack stack) {
        return Cauldron.isPotion(stack) && flask.isEmpty() && count < Cauldron.POTIONS;
    }

    private static void send(StationView view, String action, int from, boolean all) {
        CompoundTag how = new CompoundTag();
        if (from >= 0) {
            how.putInt(CauldronStation.FROM, from);
        }
        how.putBoolean(CauldronStation.ALL, all);
        view.sendAction(action, how);
    }

    // What everybody sees happen.

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        if (CauldronStation.EFFECT_POURED.equals(effect)) {
            pours.add(data.copy());
        } else if (CauldronStation.EFFECT_DRAWN.equals(effect)) {
            int color = 0xFF000000 | data.getInt(CauldronStation.COLOR);
            heldPotions++;
            dipAt(view, new ItemStack(Items.GLASS_BOTTLE), 0xFFFFFFFF, Items.GLASS_BOTTLE, color, () -> {
                heldPotions = Math.max(0, heldPotions - 1);
                dipTint(view, color);
                view.playSound(SoundEvents.BOTTLE_FILL, "brew", 0.5F, 1.0F);
            });
        } else if (CauldronStation.EFFECT_TAKEN.equals(effect)) {
            Item filled = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(data.getString(CauldronStation.ITEM)));
            int color = 0xFF000000 | (shownColor >= 0 ? shownColor : data.getInt(CauldronStation.COLOR));
            heldFull = true;
            dipAt(view, new ItemStack(filled), EMPTY_GLASS, filled, color, () -> {
                heldFull = false;
                view.playSound(SoundEvents.BOTTLE_FILL, "brew", 0.55F, 0.9F);
            });
        } else if (CauldronStation.EFFECT_BREWED.equals(effect)) {
            doneAt = time;
            int color = 0xFF000000 | data.getInt(CauldronStation.COLOR);
            view.playSound(SoundEvents.BREWING_STAND_BREW, "brew", 0.5F, 1.0F);
            view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.3F, 1.26F);
            view.spawnOnLiquid(DiegesisParticles.GLINT, "brew", 10, color, new Vector3f(0.0F, 4.0F, 0.0F));
            view.spawnInLiquid(DiegesisParticles.BUBBLE, "brew", 8, color, new Vector3f(0.0F, 3.0F, 0.0F));
        }
    }

    /**
     * A vessel ({@code shown}, tinted {@code tint}) comes over the brew and is dipped into it: down under the surface with a ring and
     * a splash, up as {@code filled}, and away to the cook.
     */
    private void dipAt(StationView view, ItemStack shown, int tint, Item filled, int color, Runnable onDipped) {
        NodeView node = view.node("dip_hand").orElse(null);
        if (node == null) {
            return;
        }
        if (dip != null) {
            dip.stop();
        }
        Vector3f at = scene(view, new Vector3f(0.0F, surface() + 4.0F, 0.0F));
        node.setVisual(Visual.ofItem(shown.getItem()).withAnchor(ICON_MIDDLE));
        node.setTint(tint);
        node.scaleTo(DIP_SCALE, 0.0F);
        node.moveTo(at.x - DIP_ORIGIN.x, at.y - DIP_ORIGIN.y, at.z - DIP_ORIGIN.z, 0.0F);
        node.setVisible(true);
        Vector3f away = FromPlayer.point(view, surface() + 6.0F, 0, 1).orElse(new Vector3f(at.x, at.y + 6.0F, at.z - 8.0F));
        dip = new KitchenMotions.Dip(node, filled, at, DIP_ORIGIN, away, this::surfaceY, view.node("brew").orElse(null), DIP_SCALE,
                8.0F * DIP_SCALE, color, onDipped);
    }

    /** The bottle that drew a potion back comes up in its colour. */
    private static void dipTint(StationView view, int color) {
        view.node("dip_hand").ifPresent(node -> node.setTint(color | 0xFF000000));
    }

    private void stopMotions() {
        if (pour != null) {
            pour.stop();
            pour = null;
        }
        pours.clear();
        if (dip != null) {
            dip.stop();
            dip = null;
        }
        heldPotions = 0;
        heldFull = false;
        thrownAge = Float.NaN;
    }

    // Each frame.

    @Override
    public void frame(StationView view, float dt) {
        time += dt;
        turnToBlock(view);
        CompoundTag data = view.state().data();
        float mixed = mixed(view, data);
        motions(view, dt);
        stick(view, dt, data);
        brew(view, dt, data, mixed);
        sachet(view, dt);
        offer(view);
        book.frame(view, dt);
        float appear = RecipeBook.appear(view);
        StepBadge.Step step = step(view, data, mixed);
        badge.show(view, view.isInUse() && RecipeBook.isWorkedAt(view) && appear > RecipeBook.SPECK && step != null, step,
                StepBadge.Extras.NONE, time);
        badge.scale(view, appear * badgeSwell());
    }

    /** The cauldron keeps the block's sides whichever side the scene faces: scenes turn to the cook who opens it. */
    private void turnToBlock(StationView view) {
        BlockState state = view.level().getBlockState(view.pos());
        Direction block = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                : Direction.NORTH;
        float turn = view.facing().toYRot() - block.toYRot();
        if (turn != shownTurn) {
            shownTurn = turn;
            view.node("cauldron").ifPresent(node -> node.rotateTo(0.0F, turn, 0.0F, 0.0F));
        }
    }

    /** How far the brew is mixed, 0 to 1: as far as it has brewed, all of it once the flask is done. */
    private float mixed(StationView view, CompoundTag data) {
        if (!flask.isEmpty()) {
            return 1.0F;
        }
        if (!data.getBoolean(CauldronStation.BREWING) || !data.contains(CauldronStation.STARTED)) {
            return 0.0F;
        }
        int total = Math.max(1, data.getInt(CauldronStation.TOTAL));
        return Mth.clamp((view.level().getGameTime() - data.getLong(CauldronStation.STARTED)) / (float) total, 0.0F, 1.0F);
    }

    private int ticksLeft(StationView view, CompoundTag data) {
        if (!data.getBoolean(CauldronStation.BREWING) || !data.contains(CauldronStation.STARTED)) {
            return 0;
        }
        long done = data.getLong(CauldronStation.STARTED) + data.getInt(CauldronStation.TOTAL);
        return (int) Math.max(0L, done - view.level().getGameTime());
    }

    /** The brew's level: a third a potion shown, all of it while the flask waits; the floor with nothing in. */
    private float level() {
        return !flask.isEmpty() || heldFull ? 1.0F : Mth.clamp(shownPotions + heldPotions, 0, Cauldron.POTIONS) / (float) Cauldron.POTIONS;
    }

    private float surface() {
        return FLOOR + (FULL - FLOOR) * level();
    }

    private double surfaceY() {
        return surface();
    }

    /** A point of the cauldron's own axes in the scene. */
    private static Vector3f scene(StationView view, Vector3f own) {
        return view.node("cauldron").map(node -> node.sceneMatrix().transformPosition(new Vector3f(own))).orElse(own.add(HALF, 0.0F, HALF));
    }

    /** The pour that runs, the next one when it is over; the flask or bottle dipped; the infusion on its way in. */
    private void motions(StationView view, float dt) {
        if (pour != null && !pour.frame(view, dt)) {
            pour = null;
        }
        if (pour == null && !pours.isEmpty()) {
            startPour(view, pours.poll());
        }
        if (pour == null && pours.isEmpty() && shownPotions != count) {
            shownPotions = count;
        }
        if (dip != null && !dip.frame(view, dt)) {
            dip = null;
            heldPotions = 0;
            heldFull = false;
        }
        if (!Float.isNaN(thrownAge)) {
            thrownAge += dt;
            throwFrame(view);
        }
    }

    /** A potion poured in: its bottle comes over the opening, tips, and its stream runs into the brew, which rises as it runs. */
    private void startPour(StationView view, CompoundTag data) {
        NodeView hand = view.node("pour_hand").orElse(null);
        if (hand == null) {
            shownPotions = count;
            return;
        }
        int color = 0xFF000000 | data.getInt(CauldronStation.COLOR);
        Vector3f onto = scene(view, new Vector3f(0.5F, 0.0F, 0.5F));
        Vector3f from = view.cursorOn(RIM + 6.0F).orElse(new Vector3f(HALF + 4.0F, RIM + 6.0F, HALF - 4.0F));
        // The bottle is drawn as glass: the game draws a potion item without its potion; its stream and the brew show its colour.
        pour = new KitchenMotions.Pour(hand, "cauldron_pour", new ItemStack(Items.GLASS_BOTTLE), new ItemStack(Items.GLASS_BOTTLE), color,
                from, onto, this::surfaceY, "brew", POUR_SIZE, -1.0F, 0.0F).lipAtLeast(RIM + 1.5F)
                .onFlow(() -> shownPotions = Math.min(Cauldron.POTIONS, shownPotions + 1));
    }

    /** The infusion flies in an arc from the cook into the brew, and floats there. */
    private void throwFrame(StationView view) {
        NodeView node = view.node("throw").orElse(null);
        float k = Math.min(1.0F, thrownAge / THROW_TIME);
        Vector3f land = scene(view, sachetPlace());
        if (node != null) {
            if (k < 1.0F) {
                if (!node.isVisible()) {
                    Item item = catalyst.isEmpty() ? BuiltInRegistries.ITEM.get(Cauldron.INFUSION) : catalyst.getItem();
                    node.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
                    node.scaleTo(0.4F, 0.0F);
                    node.setVisible(true);
                }
                Vector3f at = new Vector3f(thrownFrom).lerp(land, k).add(0.0F, THROW_ARC * Mth.sin(k * Mth.PI), 0.0F);
                node.moveTo(at.x - THROW_ORIGIN.x, at.y - THROW_ORIGIN.y, at.z - THROW_ORIGIN.z, 0.0F);
                node.rotateTo(0.0F, 0.0F, 540.0F * k, 0.0F);
            } else {
                node.setVisible(false);
            }
        }
        view.node("sachet").ifPresent(sachet -> sachet.setVisible(k >= 1.0F));
        if (k >= 1.0F) {
            thrownAge = Float.NaN;
            view.playSound(PotSounds.PLOP, "brew", 0.45F, 1.1F);
            if (level() > 0.0F) {
                Vector3f own = sachetPlace();
                view.node("brew").ifPresent(brew -> brew.ripple(own.x, own.z, 0.5F));
                view.spawnParticles(DiegesisParticles.SPLASH, land, new Vector3f(0.0F, 6.0F, 0.0F), 0.6F, 5, 0xFF000000 | shownColor);
            }
        }
    }

    // The stick.

    /**
     * The stick's foot follows the local cook's hand round the brew (the hand holds it over the rim), for everybody else what the
     * server was told; at rest it stands where the block shows it. The way the foot goes round the middle swirls the brew and goes
     * to the server, which hurries the brew by it.
     */
    private void stick(StationView view, float dt, CompoundTag data) {
        NodeView stick = view.node("stick").orElse(null);
        NodeView body = view.node("cauldron").orElse(null);
        if (stick == null || body == null) {
            return;
        }
        if (!view.isFocused()) {
            stroking = false;
            heldByClick = false;
        }
        boolean hand = stroking && view.isFocused();
        boolean others = !hand && data.getBoolean(CauldronStation.STIRRING) && data.contains(CauldronStation.STICK_X);
        float wantX = footX;
        float wantZ = footZ;
        if (hand) {
            var cursor = view.cursorOn(HANDLE);
            if (cursor.isPresent()) {
                Vector3f own = inverse.set(body.sceneMatrix()).invert().transformPosition(new Vector3f(cursor.get()));
                float reach = CauldronStation.STICK_REACH;
                wantX = own.x / HANDLE_OUT;
                wantZ = own.z / HANDLE_OUT;
                float length = Mth.sqrt(wantX * wantX + wantZ * wantZ);
                if (length > reach) {
                    wantX *= reach / length;
                    wantZ *= reach / length;
                }
            }
        } else if (others) {
            wantX = data.getFloat(CauldronStation.STICK_X);
            wantZ = data.getFloat(CauldronStation.STICK_Z);
        }
        boolean held = hand || others;
        grip = held ? Math.min(1.0F, grip + dt * GRIP_RATE) : Math.max(0.0F, grip - dt * GRIP_RATE);
        if (held) {
            float dx = wantX - footX;
            float dz = wantZ - footZ;
            float way = Mth.sqrt(dx * dx + dz * dz);
            float most = FOOT_SPEED * dt;
            float k = way > most ? most / way : 1.0F;
            footX += dx * k;
            footZ += dz * k;
        } else if (grip <= 0.0F) {
            footX = 0.0F;
            footZ = 0.0F;
        }
        // How fast the foot goes round the middle: the brew swirls after it.
        float angle = (float) Mth.atan2(footZ, footX);
        float radius = Mth.sqrt(footX * footX + footZ * footZ);
        if (held && radius > 0.5F && !Float.isNaN(lastAngle) && dt > 0.0F) {
            float step = Mth.wrapDegrees((angle - lastAngle) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
            turning = Mth.lerp(Math.min(1.0F, dt * 6.0F), turning, step / dt);
        } else {
            turning *= (float) Math.pow(0.3F, dt);
        }
        lastAngle = held ? angle : Float.NaN;
        if (hand) {
            sendClock -= dt;
            boolean moved = Float.isNaN(sentX) || Math.abs(footX - sentX) + Math.abs(footZ - sentZ) > 0.05F;
            if (sendClock <= 0.0F && moved) {
                sendClock = SEND_INTERVAL;
                sentX = footX;
                sentZ = footZ;
                CompoundTag where = new CompoundTag();
                where.putFloat(CauldronStation.X, footX);
                where.putFloat(CauldronStation.Z, footZ);
                view.sendAction(CauldronStation.ACTION_STIR, where);
            }
        } else {
            sentX = Float.NaN;
        }
        if (held && level() > 0.0F && radius > 0.3F) {
            view.node("brew").ifPresent(brew -> brew.leaveWake(footX, footZ));
        }
        view.loopSound("stir", PotSounds.STIR, level() > 0.0F ? Math.min(1.0F, Math.abs(turning) / 6.0F) * STIR_VOLUME : 0.0F,
                0.9F + 0.03F * Math.min(6.0F, Math.abs(turning)));
        pose(stick);
        stick.setOutlined(hand || stick.isHovered());
    }

    /** The stick from its foot: at rest as the block shows it, in the hand upright but leaning out a little towards the hand. */
    private void pose(NodeView stick) {
        float g = KitchenMotions.smooth(grip);
        Vector3f heldFoot = new Vector3f(footX, STIR_FOOT, footZ);
        Vector3f heldAxis = new Vector3f(footX * (HANDLE_OUT - 1.0F), HANDLE - STIR_FOOT, footZ * (HANDLE_OUT - 1.0F)).normalize();
        Vector3f foot = new Vector3f(REST_FOOT).lerp(heldFoot, g);
        Vector3f axis = new Vector3f(REST_AXIS).lerp(heldAxis, g).normalize();
        float tilt = (float) Math.acos(Mth.clamp(axis.y, -1.0F, 1.0F)) * Mth.RAD_TO_DEG;
        float heading = (float) Mth.atan2(axis.x, axis.z) * Mth.RAD_TO_DEG;
        stick.moveTo(foot.x, foot.y, foot.z, 0.0F);
        stick.rotateTo(tilt, heading, 0.0F, 0.0F);
        stick.setVisible(true);
    }

    // The brew.

    /**
     * The brew at its level in its colours, as the potions were poured: the last one on top, the one before it in patches along the walls
     * and further in, the first in drops; as it brews they mix into the colour the flask will have. It simmers while it brews, goes round
     * after the stick, glints while the flask waits.
     */
    private void brew(StationView view, float dt, CompoundTag data, float mixed) {
        NodeView brew = view.node("brew").orElse(null);
        if (brew == null) {
            return;
        }
        float level = level();
        boolean brewing = data.getBoolean(CauldronStation.BREWING);
        // While it brews it mixes into the colour of the potions' effects together; once done it is the flask it fills, as that flask
        // looks (HerbalBrews draws its flasks in one of six colours of their own).
        int target = !flask.isEmpty() ? FoodColors.main(flask) & 0xFFFFFF
                : data.getInt(CauldronStation.COLOR) & 0xFFFFFF;
        List<Integer> layers = layers();
        int top = layers.isEmpty() ? target : layers.get(layers.size() - 1);
        int color = !flask.isEmpty() ? target : lerpColor(top, target, mixed);
        if (shownColor >= 0 && (heldFull || flask.isEmpty() && layers.isEmpty())) {
            // The flask was filled: the brew keeps its colour as it is drawn off, down to the floor.
            color = shownColor;
        }
        if (color != shownColor) {
            shownColor = color;
            brew.setLiquidColor(0xFF000000 | color);
        }
        brew.showLiquid(LIQUID, level);
        float unmixed = level <= 0.0F || !flask.isEmpty() ? 0.0F : 1.0F - mixed;
        int under = layers.size() >= 2 ? layers.get(layers.size() - 2) : top;
        brew.setFoam(layers.size() >= 2 && under != top ? UNMIXED_FOAM * unmixed : 0.0F, 0xFF000000 | under);
        int first = layers.isEmpty() ? top : layers.get(0);
        int drops = layers.size() >= 3 && first != top && first != under ? Math.round(DROPS * unmixed) : 0;
        String key = first + "/" + drops;
        if (!key.equals(floatersKey)) {
            floatersKey = key;
            List<Floater> pieces = new ArrayList<>();
            for (int i = 0; i < drops; i++) {
                pieces.add(new Floater(Floater.DROP, first, DROP_SIZE * (0.8F + 0.08F * i), false));
            }
            brew.setFloating(pieces, pieces.size());
        }
        if (grip > 0.0F) {
            brew.setFloatingObstacles(footX, footZ, 1.2F);
        }
        brew.setMottle(level > 0.0F ? UNMIXED_MOTTLE * unmixed : 0.0F);
        float wanted = Mth.clamp(turning * STIR_CURRENT, -3.0F, 3.0F);
        if (Math.abs(wanted) < SIMMER && brewing) {
            wanted = SIMMER * Math.signum(current == 0.0F ? 1.0F : current);
        }
        current = Mth.lerp(Math.min(1.0F, dt * 1.5F), current, wanted);
        brew.setCurrent(current);
        brew.setAgitation(brewing ? 0.35F : level > 0.0F ? 0.12F : 0.0F);
        brew.setEdgeBoil(brewing ? 0.3F : 0.0F);
        if (level <= 0.0F) {
            return;
        }
        if (brewing) {
            blubClock -= dt;
            if (blubClock <= 0.0F) {
                blubClock = 0.6F + 0.8F * view.level().getRandom().nextFloat();
                view.spawnOnLiquid(DiegesisParticles.BUBBLE_POP, "brew", 2, 0xFF000000 | color, new Vector3f(0.0F, 1.0F, 0.0F));
                view.playSound(PotSounds.BLUB, "brew", 0.12F, 0.9F + 0.2F * view.level().getRandom().nextFloat());
            }
        }
        if (!flask.isEmpty()) {
            glintClock -= dt;
            if (glintClock <= 0.0F) {
                glintClock = 0.5F;
                view.spawnOnLiquid(DiegesisParticles.GLINT, "brew", 1, 0xFF000000 | color, new Vector3f(0.0F, 2.0F, 0.0F));
            }
        }
    }

    /** The colours of the potions shown, in the order they lie in the cauldron's slots: the order they were poured in. */
    private List<Integer> layers() {
        List<Integer> layers = new ArrayList<>(Cauldron.POTIONS);
        int shown = Math.min(Cauldron.POTIONS, shownPotions);
        for (int slot = 0; slot < Cauldron.POTIONS && layers.size() < shown; slot++) {
            if (!potions[slot].isEmpty()) {
                layers.add(CauldronBrews.color(potions[slot]));
            }
        }
        return layers;
    }

    private static int lerpColor(int from, int to, float k) {
        int r = Math.round(Mth.lerp(k, from >> 16 & 0xFF, to >> 16 & 0xFF));
        int g = Math.round(Mth.lerp(k, from >> 8 & 0xFF, to >> 8 & 0xFF));
        int b = Math.round(Mth.lerp(k, from & 0xFF, to & 0xFF));
        return r << 16 | g << 8 | b;
    }

    /** Where the infusion floats, in the cauldron's own axes: carried round by the brew, on the floor of an empty cauldron. */
    private Vector3f sachetPlace() {
        float level = level();
        float y = level > 0.0F ? surface() + 0.15F + 0.12F * Mth.sin(time * 2.2F) : FLOOR + 0.1F;
        float angle = 2.4F + carried;
        return new Vector3f(SACHET_OUT * Mth.cos(angle), y, SACHET_OUT * Mth.sin(angle));
    }

    private void sachet(StationView view, float dt) {
        if (level() > 0.0F) {
            carried += current * dt;
        }
        Vector3f at = sachetPlace();
        view.node("sachet").ifPresent(node -> {
            node.moveTo(at.x, at.y, at.z, 0.0F);
            node.rotateTo(0.0F, -carried * Mth.RAD_TO_DEG, 0.0F, 0.0F);
            if (Float.isNaN(thrownAge)) {
                node.setVisible(true);
            }
        });
    }

    // What an empty hand is offered.

    /**
     * With an empty hand over the opening: the flask that waits, see-through, to be filled with a click; else what the step takes from
     * the inventory, see-through: a potion while the cauldron takes one, the infusion once three are in.
     */
    private void offer(StationView view) {
        offered = ItemStack.EMPTY;
        offeredSlot = -1;
        NodeView node = view.node("offer").orElse(null);
        LocalPlayer player = Minecraft.getInstance().player;
        boolean free = player != null && player.inventoryMenu.getCarried().isEmpty() && view.isFocused() && pour == null && dip == null
                && !stroking && !heldByClick;
        ItemStack shown = ItemStack.EMPTY;
        if (free && !flask.isEmpty()) {
            shown = flask;
        } else if (free) {
            Inventory inventory = player.getInventory();
            offeredSlot = count < Cauldron.POTIONS ? slotOf(inventory, Cauldron::isPotion)
                    : catalyst.isEmpty() ? slotOf(inventory, Cauldron::isCatalyst) : -1;
            if (offeredSlot >= 0) {
                offered = inventory.getItem(offeredSlot);
                shown = offered;
            }
        }
        boolean any = !shown.isEmpty();
        view.node("dip").ifPresent(dip -> dip.setEnabled(any));
        boolean over = any && view.node("dip").map(NodeView::isHovered).orElse(false);
        if (node == null) {
            return;
        }
        node.setVisible(over);
        if (!over) {
            return;
        }
        Item item = shown.getItem();
        if (offeredItem != item) {
            offeredItem = item;
            node.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
            node.setTint(OFFERED);
            node.scaleTo(OFFER_SIZE / 16.0F / OFFER_BASE, 0.0F);
        }
        float lift = 1.0F + OFFER_SIZE * 0.5F;
        Vector3f at = CursorAim.clamped(view, RIM, lift, 4.5F, 11.5F, 4.5F, 11.5F).orElse(new Vector3f(HALF, RIM + lift, HALF));
        node.moveTo(at.x - OFFER_ORIGIN.x, at.y - OFFER_ORIGIN.y, at.z - OFFER_ORIGIN.z, 0.0F);
    }

    private static int slotOf(Inventory inventory, Predicate<ItemStack> wanted) {
        if (wanted.test(inventory.getSelected())) {
            return inventory.selected;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (wanted.test(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    // The badge.

    /**
     * What the badge shows: the flask while it waits to be filled; while it brews, the stick to hurry it (the flask while it is
     * stirred), how far it is and the time it still takes by itself; three potions that make nothing on this line, a potion, greyed;
     * three potions, the infusion they wait for, blinking while the cook has none; fewer, a potion, how many are in on the bar,
     * blinking while the cook has none. Ready: the flask that waits; still to come: the one the potions make.
     */
    private StepBadge.Step step(StationView view, CompoundTag data, float mixed) {
        Item flaskItem = BuiltInRegistries.ITEM.get(Cauldron.FLASK);
        ItemStack made = new ItemStack(flaskItem);
        if (!flask.isEmpty()) {
            return new StepBadge.Step(flask, null, false, 1, 0, 1.0F, 0, false, flask.getHoverName());
        }
        if (count >= Cauldron.POTIONS && !data.getBoolean(CauldronStation.MAKES)) {
            return new StepBadge.Step(potionIcon(), null, false, 0, 1, Float.NaN, 0, true, potionIcon().getHoverName());
        }
        if (data.getBoolean(CauldronStation.BREWING)) {
            int left = ticksLeft(view, data);
            int seconds = left > 0 ? Math.max(1, Mth.ceil(left / 20.0F)) : 0;
            Component name = made.getHoverName();
            Component tip = seconds > 0 ? Component.translatable("handson_letsdo.barrel.seconds", name, seconds) : name;
            boolean stirred = grip > 0.5F;
            return new StepBadge.Step(made, stirred ? null : StepBadge.Glyph.SPOON, false, 0, 1, mixed, seconds, false, tip);
        }
        if (count >= Cauldron.POTIONS) {
            ItemStack infusion = new ItemStack(BuiltInRegistries.ITEM.get(Cauldron.INFUSION));
            return new StepBadge.Step(infusion, null, StepBadge.lacks(Cauldron::isCatalyst), 0, 1, Float.NaN, 0, false,
                    infusion.getHoverName());
        }
        ItemStack potion = potionIcon();
        return new StepBadge.Step(potion, null, StepBadge.lacks(Cauldron::isPotion), 0, 1, count / (float) Cauldron.POTIONS, 0, false,
                potion.getHoverName());
    }

    /** A potion for the badge: the first the cook has, else a bottle of water. */
    private static ItemStack potionIcon() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            int slot = slotOf(player.getInventory(), Cauldron::isPotion);
            if (slot >= 0) {
                return player.getInventory().getItem(slot).copyWithCount(1);
            }
        }
        return CauldronBrews.waterBottle();
    }

    /** How much bigger the badge is drawn now: a swell as the brew gets done, a slow breath while the flask waits. */
    private float badgeSwell() {
        float since = time - doneAt;
        if (since >= 0.0F && since < DONE_TIME) {
            return 1.0F + DONE_SWELL * Mth.sin(Mth.PI * since / DONE_TIME);
        }
        return !flask.isEmpty() ? 1.0F + READY_BREATH * (0.5F - 0.5F * Mth.cos(time * 2.4F)) : 1.0F;
    }
}
