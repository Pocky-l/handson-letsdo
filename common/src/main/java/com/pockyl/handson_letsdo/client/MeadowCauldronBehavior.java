package com.pockyl.handson_letsdo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.handson_letsdo.station.CauldronTold;
import com.pockyl.handson_letsdo.station.MeadowCauldron;
import com.pockyl.handson_letsdo.station.MeadowCauldronStation;

import java.util.Set;

/**
 * Meadow's cooking cauldron at the station and in the world. Its water stands as high as Meadow counts it; the food floats on it,
 * going round slowly, and the dish that is done floats up in the middle, taken by a click. Water is poured in from a bucket or a
 * bottle, food dropped in. A ladle stirs: taken by its upper part, its foot follows the hand round the water, which swirls after it,
 * and the server hurries the dish by the way it went round. The lid is on while a dish cooks, as Meadow shows it, and set down behind
 * the cauldron while somebody works at it. An empty hand over the cauldron is offered water from the inventory while it takes some.
 */
final class MeadowCauldronBehavior implements StationBehavior {
    private static final float HALF = 8.0F;
    /** The cauldron's own heights standing, pixels: its floor, its water at 100, its rim; hanging, all of it 11 px higher. */
    private static final float FLOOR = 1.0F;
    private static final float FULL = 8.0F;
    private static final float RIM = 9.0F;
    private static final float HANGING = 11.0F;
    /** Its 8 by 8 lid on its walls 10 by 10, the model centred on the block; a hanging one under its frame's bar at y 30. */
    private static final LidRest LID = new LidRest(4.0F, 4.0F, 5.0F, 5.0F, RIM, 0.0F, new Vector3f());
    private static final LidRest LID_HANGING = new LidRest(4.0F, 4.0F, 5.0F, 5.0F, RIM + HANGING, 30.0F, new Vector3f());
    private static final float LID_MOVE = 0.45F;

    /**
     * The ladle at rest: its foot on the floor near the west wall, leaning on the rim there, nearly upright, so that it hides little
     * of the water from the camera.
     */
    private static final Vector3f REST_FOOT = new Vector3f(-2.0F, FLOOR, 1.0F);
    private static final Vector3f REST_AXIS = new Vector3f(-2.4F, 9.7F, 0.0F).normalize();
    /** In the hand its foot goes round just over the floor, and the hand holds it over the rim, a little further out. */
    private static final float STIR_FOOT = FLOOR + 0.6F;
    private static final float HANDLE = RIM + 1.5F;
    private static final float HANDLE_OUT = 3.5F / MeadowCauldronStation.LADLE_REACH;
    private static final float FOOT_SPEED = 40.0F;
    private static final float GRIP_RATE = 7.0F;
    private static final float TAKE_CLICK = 0.3F;
    private static final float SEND_INTERVAL = 0.1F;
    private static final float STIR_VOLUME = 0.6F;
    private static final float STIR_CURRENT = 0.7F;
    private static final float SIMMER = 0.3F;

    /** The food afloat: how far out from the middle, how fast the water carries it by itself; the dish rising, seconds. */
    private static final float DRIFT_RADIUS = 2.3F;
    private static final float CALM_DRIFT = 0.12F;
    private static final float RISE_TIME = 0.7F;
    private static final float ITEM_REST = 0.15F;
    /** The dish afloat, pixels across. */
    private static final float DISH_SIZE = 4.0F;

    private static final int WATER_COLOR = 0xC84A86E8;
    private static final int STEAM_COLOR = 0x6CFFFFFF;
    private static final Vector3f STEAM_RISE = new Vector3f(0.0F, 3.2F, 0.0F);
    private static final Vector3f SPLASH_UP = new Vector3f(0.0F, 4.0F, 0.0F);
    private static final float POUR_SIZE = 5.0F;
    private static final Vector3f OFFER_ORIGIN = new Vector3f(8.0F, RIM + 4.0F, 8.0F);
    private static final Vector3f ICON_MIDDLE = new Vector3f(8.0F, 8.0F, 8.0F);
    private static final float OFFER_SIZE = 4.2F;
    private static final float OFFER_BASE = 0.35F;
    private static final int OFFERED = 0x8CFFFFFF;
    private static final float DONE_SWELL = 0.35F;
    private static final float DONE_TIME = 0.6F;
    private static final float READY_BREATH = 0.04F;

    /** The nodes a stack on the cursor is brought to the cauldron at. */
    private static final Set<String> INSIDE = Set.of("mouth", "dip", "ladle", "dish", "item_0", "item_1", "item_2", "item_3", "item_4",
            "item_5");
    private static final String[] ITEM_NODES = {"item_0", "item_1", "item_2", "item_3", "item_4", "item_5"};

    private final RecipeBook book = new RecipeBook(new MeadowCauldronBook());
    private final StepBadge badge = new StepBadge(10, -10, true);
    private final UsableCache usable = new UsableCache();
    private float time;

    /** How much higher everything is: 11 px in a hanging cauldron. */
    private float lift;
    private float shownTurn = Float.NaN;
    private final Matrix4f inverse = new Matrix4f();

    private boolean stroking;
    private boolean heldByClick;
    private float strokeStart;
    private float footX = REST_FOOT.x;
    private float footZ = REST_FOOT.z;
    private float grip;
    private float turning;
    private float lastAngle = Float.NaN;
    private float sendClock;
    private float sentX = Float.NaN;
    private float sentZ = Float.NaN;
    private float current;
    private float carried;

    /** The water as it looks, 0 to 1: it follows the count, held back while a pour runs. */
    private float level = -1.0F;
    private float lid;
    private final int[] countsBefore = new int[MeadowCauldron.INGREDIENTS];
    private boolean seen;
    private float risen = 1.0F;
    private ItemStack output = ItemStack.EMPTY;
    private Item dishItem;

    private KitchenMotions.Pour pour;
    private ItemStack offered = ItemStack.EMPTY;
    private int offeredSlot = -1;
    private Item offeredItem;
    private float doneAt = -10.0F;
    private float steamClock;
    private float blubClock;

    MeadowCauldronBehavior(StationView view) {
    }

    @Override
    public void onSceneReady(StationView view) {
        badge.ready(view);
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        view.node("offer").ifPresent(node -> node.setOnTop(true));
        shownTurn = Float.NaN;
        offeredItem = null;
        dishItem = null;
        seen = false;
        level = -1.0F;
        if (pour != null) {
            pour.stop();
            pour = null;
        }
        footX = REST_FOOT.x;
        footZ = REST_FOOT.z;
        lid = view.isInUse() ? 1.0F : 0.0F;
        refresh(view);
    }

    @Override
    public void onOpen(StationView view) {
        usable.clear();
        view.setInventoryFilter(stack -> usable.test(stack, it -> MeadowCauldron.waterOf(it) > 0
                || MeadowCauldron.isIngredient(view.level(), it)));
        book.opened(view);
        refresh(view);
        // Water the cook came with is poured in; anything else stays in its slot.
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && view.openedByUse() && player.inventoryMenu.getCarried().isEmpty()
                && pourable(view, player.getMainHandItem())) {
            send(view, MeadowCauldronStation.ACTION_POUR, player.getInventory().selected);
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

    /** Food that came in: a plop and rings where it lands, for everybody who sees it. */
    private void refresh(StationView view) {
        output = view.state().getItem(MeadowCauldronStation.SERVED_SLOT);
        for (int i = 0; i < MeadowCauldron.INGREDIENTS; i++) {
            int count = view.state().getItem(SlotRef.of(MeadowCauldronStation.INGREDIENTS, i)).getCount();
            if (seen && count > countsBefore[i]) {
                landed(view, i);
            }
            countsBefore[i] = count;
        }
        seen = true;
    }

    private void landed(StationView view, int index) {
        view.playSound(PotSounds.PLOP, "water", 0.4F, 0.95F + 0.1F * view.level().getRandom().nextFloat());
        if (level > 0.0F) {
            Vector3f at = floatPlace(index, presentCount());
            view.node("water").ifPresent(water -> water.ripple(at.x, at.z, 0.45F));
            view.spawnOnLiquid(DiegesisParticles.SPLASH, "water", 4, WATER_COLOR, SPLASH_UP);
        }
    }

    // Clicks.

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Stroke stroke && "ladle".equals(stroke.node())) {
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
        } else if (event instanceof SceneEvent.Click click && "dish".equals(click.node()) && !output.isEmpty()) {
            view.sendAction(MeadowCauldronStation.ACTION_TAKE, new CompoundTag());
            view.playSound(PotSounds.SCOOP, "dish", 0.4F, 1.0F);
            if (level > 0.0F) {
                view.node("water").ifPresent(water -> water.ripple(0.0F, 0.0F, 0.5F));
            }
        } else if (event instanceof SceneEvent.Click click && "dip".equals(click.node()) && click.button() == 0 && offeredSlot >= 0) {
            send(view, MeadowCauldronStation.ACTION_POUR, offeredSlot);
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
     * A stack brought to the cauldron, its inside or anything in it: water is poured in while it fits; food goes into the water as
     * usual, and into a cauldron whose slots hold other food when it still makes a dish with it. Anything else it does not take.
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
        if (MeadowCauldron.waterOf(stack) > 0) {
            if (pourable(view, stack)) {
                view.sendAction(MeadowCauldronStation.ACTION_POUR, new CompoundTag());
            }
            return true;
        }
        return FullZone.offer(view, MeadowCauldronStation.INGREDIENTS, stack, button);
    }

    /** Shift + click on water in the inventory pours it in; everything else goes to its place as usual. */
    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        if (MeadowCauldron.waterOf(stack) <= 0) {
            return false;
        }
        if (pourable(view, stack)) {
            send(view, MeadowCauldronStation.ACTION_POUR, slot);
        }
        return true;
    }

    /** Whether water from {@code stack} fits in the cauldron now, all of it (as the server asks). */
    private static boolean pourable(StationView view, ItemStack stack) {
        int water = MeadowCauldron.waterOf(stack);
        return water > 0 && view.state().data().getInt(MeadowCauldronStation.WATER_LEVEL) + water <= MeadowCauldron.MOST_WATER;
    }

    private static void send(StationView view, String action, int from) {
        CompoundTag how = new CompoundTag();
        if (from >= 0) {
            how.putInt(MeadowCauldronStation.FROM, from);
        }
        view.sendAction(action, how);
    }

    // What everybody sees happen.

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        if (MeadowCauldronStation.EFFECT_POURED.equals(effect)) {
            poured(view, data);
        } else if (MeadowCauldronStation.EFFECT_COOKED.equals(effect)) {
            doneAt = time;
            risen = 0.0F;
            view.playSound(PotSounds.BURST, "water", 0.35F, 1.0F);
            view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.3F, 1.26F);
            if (level > 0.0F) {
                view.spawnOnLiquid(DiegesisParticles.BUBBLE_POP, "water", 6, WATER_COLOR, SPLASH_UP);
            }
        }
    }

    /**
     * Water was poured in: its vessel is tipped over the cauldron and the water runs in a stream, the level waiting for it, and the
     * empty vessel is taken away. For those who only watch, it splashes.
     */
    private void poured(StationView view, CompoundTag data) {
        Item vessel = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(data.getString(MeadowCauldronStation.ITEM)));
        NodeView hand = view.node("pour_hand").orElse(null);
        if (hand == null || vessel == null || vessel == Items.AIR || !view.isFocused()) {
            view.node("water").ifPresent(water -> water.ripple(0.0F, 0.0F, 0.6F));
            view.spawnOnLiquid(DiegesisParticles.SPLASH, "water", 6, WATER_COLOR, SPLASH_UP);
            view.playSound(SoundEvents.BUCKET_EMPTY, "water", 0.6F, 1.1F);
            return;
        }
        if (pour != null) {
            pour.stop();
        }
        // A bucket comes back empty; of a bottle the cauldron leaves nothing, and it is shown as plain glass, not as a potion.
        Item left = KitchenMotions.containerOf(new ItemStack(vessel)).orElse(null);
        ItemStack full = new ItemStack(left == null ? Items.GLASS_BOTTLE : vessel);
        ItemStack empty = left == null ? ItemStack.EMPTY : new ItemStack(left);
        Vector3f onto = scene(view, new Vector3f(0.0F, 0.0F, 0.0F));
        float rim = RIM + lift;
        Vector3f from = view.cursorOn(rim + 6.0F).orElse(new Vector3f(HALF + 4.0F, rim + 6.0F, HALF - 4.0F));
        pour = new KitchenMotions.Pour(hand, "meadow_cauldron_pour", full, empty, WATER_COLOR, from, onto, this::surfaceY, "water",
                POUR_SIZE, -1.0F, 0.0F).lipAtLeast(rim + 1.5F);
        float before = pour.pouredBy() - pour.flowTime();
        float flow = pour.flowTime();
        view.node("water").ifPresent(water -> water.holdLevel(before, flow));
    }

    // Each frame.

    @Override
    public void frame(StationView view, float dt) {
        time += dt;
        BlockState block = view.level().getBlockState(view.pos());
        lift = MeadowCauldron.hangs(block) ? HANGING : 0.0F;
        turnToBlock(view, block);
        CompoundTag data = view.state().data();
        if (pour != null && !pour.frame(view, dt)) {
            pour = null;
        }
        float wanted = Mth.clamp(data.getInt(MeadowCauldronStation.WATER_LEVEL) / (float) MeadowCauldron.MOST_WATER, 0.0F, 1.0F);
        level = level < 0.0F || pour == null && Math.abs(level - wanted) < 0.002F ? wanted
                : pour != null ? level : Mth.lerp(Math.min(1.0F, dt * 3.0F), level, wanted);
        lid(view, dt, block);
        ladle(view, dt, data);
        water(view, dt, data);
        floating(view, dt);
        offer(view);
        book.frame(view, dt);
        float appear = RecipeBook.appear(view);
        StepBadge.Step step = step(view, data);
        badge.show(view, view.isInUse() && RecipeBook.isWorkedAt(view) && appear > RecipeBook.SPECK && step != null, step,
                StepBadge.Extras.NONE, time);
        badge.scale(view, appear * badgeSwell());
    }

    /** The cauldron keeps the block's sides whichever side the scene faces: scenes turn to the cook who opens it. */
    private void turnToBlock(StationView view, BlockState state) {
        Direction block = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                : Direction.NORTH;
        float turn = view.facing().toYRot() - block.toYRot();
        if (turn != shownTurn) {
            shownTurn = turn;
            view.node("cauldron").ifPresent(node -> node.rotateTo(0.0F, turn, 0.0F, 0.0F));
        }
    }

    private float surface() {
        return FLOOR + lift + (FULL - FLOOR) * Math.max(0.0F, level);
    }

    private double surfaceY() {
        return surface();
    }

    /** A point of the cauldron's own axes in the scene. */
    private static Vector3f scene(StationView view, Vector3f own) {
        return view.node("cauldron").map(node -> node.sceneMatrix().transformPosition(new Vector3f(own))).orElse(own.add(HALF, 0.0F, HALF));
    }

    // The lid.

    /**
     * The lid is on while a dish cooks, as Meadow shows it, and set down behind the cauldron while somebody works at it: lifted,
     * swung over the back rim and stood there; put back on as the last cook leaves.
     */
    private void lid(StationView view, float dt, BlockState block) {
        NodeView node = view.node("lid").orElse(null);
        if (node == null) {
            return;
        }
        boolean shown = MeadowCauldron.lidded(block);
        float before = lid;
        lid = Mth.clamp(lid + (view.isInUse() ? dt : -dt) / LID_MOVE, 0.0F, 1.0F);
        if (shown && (before < 1.0F && lid >= 1.0F || before > 0.0F && lid <= 0.0F)) {
            view.playSound(SoundEvents.LANTERN_PLACE, "lid", 0.25F, 0.8F + view.level().getRandom().nextFloat() * 0.1F);
        }
        node.setVisible(shown);
        if (!shown) {
            return;
        }
        boolean hanging = lift > 0.0F;
        LidRest rest = hanging ? LID_HANGING : LID;
        float turn = Float.isNaN(shownTurn) ? 0.0F : shownTurn;
        Quaternionf cauldronTurn = new Quaternionf().rotationY(turn * Mth.DEG_TO_RAD);
        Vector3f lidOn = new Vector3f(-0.04F, RIM + lift, 0.0F);
        Quaternionf ownTurn = new Quaternionf();
        LidRest.Pose on = rest.lying(cauldronTurn.transform(new Vector3f(lidOn)), new Quaternionf(cauldronTurn));
        rest.open(node, on, KitchenMotions.smooth(lid), turn, hanging, 0.0F, hanging ? LidRest.Ground.NONE : groundOf(view), turn,
                new Vector3f(), lidOn, ownTurn);
    }

    /** What stands beside the cauldron, where its lid is set down: the top of the block under it at that point; NaN for nothing. */
    private static LidRest.Ground groundOf(StationView view) {
        return (x, z) -> {
            Vec3 at = view.toWorld(HALF + x, -0.01F, HALF + z);
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

    // The ladle.

    /**
     * The ladle's foot follows the local cook's hand round the water (the hand holds it over the rim), for everybody else what the
     * server was told; at rest it leans on the west rim. The way the foot goes round the middle swirls the water and goes to the
     * server, which hurries the dish by it.
     */
    private void ladle(StationView view, float dt, CompoundTag data) {
        NodeView ladle = view.node("ladle").orElse(null);
        NodeView body = view.node("cauldron").orElse(null);
        if (ladle == null || body == null) {
            return;
        }
        if (!view.isFocused()) {
            stroking = false;
            heldByClick = false;
        }
        boolean hand = stroking && view.isFocused();
        boolean others = !hand && data.getBoolean(MeadowCauldronStation.STIRRING) && data.contains(MeadowCauldronStation.LADLE_X);
        float wantX = footX;
        float wantZ = footZ;
        if (hand) {
            var cursor = view.cursorOn(HANDLE + lift);
            if (cursor.isPresent()) {
                Vector3f own = inverse.set(body.sceneMatrix()).invert().transformPosition(new Vector3f(cursor.get()));
                float reach = MeadowCauldronStation.LADLE_REACH;
                wantX = own.x / HANDLE_OUT;
                wantZ = own.z / HANDLE_OUT;
                float length = Mth.sqrt(wantX * wantX + wantZ * wantZ);
                if (length > reach) {
                    wantX *= reach / length;
                    wantZ *= reach / length;
                }
            }
        } else if (others) {
            wantX = data.getFloat(MeadowCauldronStation.LADLE_X);
            wantZ = data.getFloat(MeadowCauldronStation.LADLE_Z);
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
                where.putFloat(MeadowCauldronStation.X, footX);
                where.putFloat(MeadowCauldronStation.Z, footZ);
                view.sendAction(MeadowCauldronStation.ACTION_STIR, where);
            }
        } else {
            sentX = Float.NaN;
        }
        if (held && level > 0.0F && radius > 0.3F) {
            view.node("water").ifPresent(water -> water.leaveWake(footX, footZ));
        }
        view.loopSound("stir", PotSounds.STIR, level > 0.0F ? Math.min(1.0F, Math.abs(turning) / 6.0F) * STIR_VOLUME : 0.0F,
                0.9F + 0.03F * Math.min(6.0F, Math.abs(turning)));
        pose(ladle);
        ladle.setOutlined(hand || ladle.isHovered());
    }

    /** The ladle from its foot: at rest as it leans on the rim, in the hand upright but leaning out a little towards the hand. */
    private void pose(NodeView ladle) {
        float g = KitchenMotions.smooth(grip);
        Vector3f heldFoot = new Vector3f(footX, STIR_FOOT + lift, footZ);
        Vector3f heldAxis = new Vector3f(footX * (HANDLE_OUT - 1.0F), HANDLE - STIR_FOOT, footZ * (HANDLE_OUT - 1.0F)).normalize();
        Vector3f foot = new Vector3f(REST_FOOT).add(0.0F, lift, 0.0F).lerp(heldFoot, g);
        Vector3f axis = new Vector3f(REST_AXIS).lerp(heldAxis, g).normalize();
        float tilt = (float) Math.acos(Mth.clamp(axis.y, -1.0F, 1.0F)) * Mth.RAD_TO_DEG;
        float heading = (float) Mth.atan2(axis.x, axis.z) * Mth.RAD_TO_DEG;
        ladle.moveTo(foot.x, foot.y, foot.z, 0.0F);
        ladle.rotateTo(tilt, heading, 0.0F, 0.0F);
        ladle.setVisible(true);
    }

    // The water.

    /** The water goes round after the ladle, simmers while a dish cooks, steams while it is hot. */
    private void water(StationView view, float dt, CompoundTag data) {
        NodeView water = view.node("water").orElse(null);
        if (water == null) {
            return;
        }
        boolean cooking = data.getBoolean(MeadowCauldronStation.COOKING);
        boolean heated = data.getBoolean(MeadowCauldronStation.HEATED);
        float wanted = Mth.clamp(turning * STIR_CURRENT, -3.0F, 3.0F);
        if (Math.abs(wanted) < SIMMER && cooking) {
            wanted = SIMMER * Math.signum(current == 0.0F ? 1.0F : current);
        }
        current = Mth.lerp(Math.min(1.0F, dt * 1.5F), current, wanted);
        water.setCurrent(current);
        water.setAgitation(cooking ? 0.3F : level > 0.0F && heated ? 0.12F : 0.0F);
        water.setEdgeBoil(cooking ? 0.25F : 0.0F);
        if (grip > 0.0F) {
            water.setFloatingObstacles(footX, footZ, 1.0F);
        }
        if (level <= 0.0F || !heated) {
            return;
        }
        steamClock -= dt;
        if (steamClock <= 0.0F) {
            steamClock = cooking ? 0.35F : 0.9F;
            view.spawnOnLiquid(DiegesisParticles.STEAM, "water", 1, STEAM_COLOR, STEAM_RISE);
        }
        if (cooking) {
            blubClock -= dt;
            if (blubClock <= 0.0F) {
                blubClock = 0.7F + 0.8F * view.level().getRandom().nextFloat();
                view.spawnOnLiquid(DiegesisParticles.BUBBLE_POP, "water", 2, WATER_COLOR, new Vector3f(0.0F, 1.0F, 0.0F));
                view.playSound(PotSounds.BLUB, "water", 0.12F, 0.9F + 0.2F * view.level().getRandom().nextFloat());
            }
        }
    }

    // What floats.

    private int presentCount() {
        int present = 0;
        for (int count : countsBefore) {
            present += count > 0 ? 1 : 0;
        }
        return present;
    }

    /** Where the food of slot {@code index} floats, in the cauldron's own axes: evenly round the middle, carried by the water. */
    private Vector3f floatPlace(int index, int present) {
        int rank = 0;
        for (int i = 0; i < index; i++) {
            rank += countsBefore[i] > 0 ? 1 : 0;
        }
        float angle = carried + rank * Mth.TWO_PI / Math.max(1, present);
        float y = level > 0.0F ? surface() - ITEM_REST + Mth.sin(time * 0.9F + index * 1.3F) * 0.07F : FLOOR + lift;
        return new Vector3f(Mth.cos(angle) * DRIFT_RADIUS, y, Mth.sin(angle) * DRIFT_RADIUS);
    }

    /** The food goes round slowly on the water; the dish floats up in the middle once it is cooked. */
    private void floating(StationView view, float dt) {
        if (level > 0.0F) {
            carried += (CALM_DRIFT * Math.signum(current == 0.0F ? 1.0F : current) + current) * dt;
        }
        int present = presentCount();
        for (int i = 0; i < ITEM_NODES.length; i++) {
            int index = i;
            Vector3f at = floatPlace(i, present);
            view.node(ITEM_NODES[i]).ifPresent(node -> {
                node.moveTo(at.x, at.y - (FLOOR + lift), at.z, 0.0F);
                node.rotateTo(Mth.sin(time * 0.5F + index) * 3.0F, -(carried + index) * Mth.RAD_TO_DEG, Mth.cos(time * 0.4F + index) * 3.0F,
                        0.0F);
            });
        }
        risen = Math.min(1.0F, risen + dt / RISE_TIME);
        float top = level > 0.0F ? surface() - ITEM_REST + Mth.sin(time * 1.1F) * 0.08F : FLOOR + lift;
        float y = Mth.lerp(KitchenMotions.smooth(risen), FLOOR + lift, top);
        view.node("dish").ifPresent(node -> {
            node.setVisible(!output.isEmpty());
            if (output.isEmpty()) {
                return;
            }
            if (dishItem != output.getItem()) {
                dishItem = output.getItem();
                node.setVisual(Visual.ofItem(dishItem).withAnchor(ICON_MIDDLE));
                node.scaleTo(DISH_SIZE / 16.0F, 0.0F);
            }
            node.moveTo(0.0F, y - (FLOOR + lift), 0.0F, 0.0F);
            node.setOutlined(node.isHovered());
        });
    }

    // What an empty hand is offered.

    /** With an empty hand over the cauldron: water from the inventory, see-through, while the cauldron takes it and wants it. */
    private void offer(StationView view) {
        offered = ItemStack.EMPTY;
        offeredSlot = -1;
        NodeView node = view.node("offer").orElse(null);
        LocalPlayer player = Minecraft.getInstance().player;
        CompoundTag data = view.state().data();
        int water = data.getInt(MeadowCauldronStation.WATER_LEVEL);
        boolean wants = water <= 0
                || data.contains(MeadowCauldronStation.MAKING) && water < data.getInt(MeadowCauldronStation.MAKING_WATER);
        boolean free = player != null && player.inventoryMenu.getCarried().isEmpty() && view.isFocused() && pour == null && !stroking
                && !heldByClick && wants;
        if (free) {
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE && offeredSlot < 0; slot++) {
                int at = slot == 0 ? inventory.selected : slot == inventory.selected ? 0 : slot;
                if (pourable(view, inventory.getItem(at))) {
                    offeredSlot = at;
                    offered = inventory.getItem(at);
                }
            }
        }
        boolean any = !offered.isEmpty();
        view.node("dip").ifPresent(dip -> dip.setEnabled(any));
        boolean over = any && view.node("dip").map(NodeView::isHovered).orElse(false);
        if (node == null) {
            return;
        }
        node.setVisible(over);
        if (!over) {
            return;
        }
        Item item = offered.getItem();
        if (offeredItem != item) {
            offeredItem = item;
            node.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
            node.setTint(OFFERED);
            node.scaleTo(OFFER_SIZE / 16.0F / OFFER_BASE, 0.0F);
        }
        float rim = RIM + lift;
        float up = 1.0F + OFFER_SIZE * 0.5F;
        Vector3f at = CursorAim.clamped(view, rim, up, 4.5F, 11.5F, 4.5F, 11.5F).orElse(new Vector3f(HALF, rim + up, HALF));
        node.moveTo(at.x - OFFER_ORIGIN.x, at.y - OFFER_ORIGIN.y - lift, at.z - OFFER_ORIGIN.z, 0.0F);
    }

    // The badge.

    /**
     * What the badge shows: the dish that waits in the water, to be taken; while one cooks, the ladle to hurry it (the dish while it
     * is stirred), how far it is and the time it still takes by itself. Before it cooks, what the food lacks: water (a bucket,
     * blinking while the cook has none, the bar how much of what the dish takes is in), heat, else the dish it makes. Null for no
     * food that makes a dish.
     */
    private StepBadge.Step step(StationView view, CompoundTag data) {
        if (!output.isEmpty()) {
            Component tip = Component.translatable("handson_letsdo.pot.ready", output.getCount(), output.getHoverName());
            return new StepBadge.Step(output, null, false, output.getCount(), 0, 1.0F, 0, false, tip);
        }
        ResourceLocation id = data.contains(MeadowCauldronStation.MAKING)
                ? ResourceLocation.tryParse(data.getString(MeadowCauldronStation.MAKING)) : null;
        MeadowCauldron.Dish dish = id == null ? null : MeadowCauldron.dishes(view.level()).stream().filter(it -> it.id().equals(id))
                .findFirst().orElse(null);
        if (dish == null) {
            return null;
        }
        ItemStack made = dish.result();
        int water = data.getInt(MeadowCauldronStation.WATER_LEVEL);
        if (data.getBoolean(MeadowCauldronStation.COOKING)) {
            int total = Math.max(1, data.getInt(MeadowCauldronStation.DURATION));
            long started = data.getLong(MeadowCauldronStation.STARTED);
            long now = view.level().getGameTime();
            float progress = Mth.clamp((now - started) / (float) total, 0.0F, 1.0F);
            int seconds = (int) Math.max(0L, Mth.ceil((started + total - now) / 20.0F));
            Component tip = seconds > 0 ? Component.translatable("handson_letsdo.barrel.seconds", made.getHoverName(), seconds)
                    : made.getHoverName();
            return new StepBadge.Step(made, grip > 0.5F ? null : StepBadge.Glyph.SPOON, false, 0, made.getCount(), progress, seconds,
                    false, tip);
        }
        if (water < dish.water()) {
            ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
            return new StepBadge.Step(bucket, null, StepBadge.lacks(stack -> MeadowCauldron.waterOf(stack) > 0), 0, made.getCount(),
                    water / (float) Math.max(1, dish.water()), 0, false, bucket.getHoverName());
        }
        if (!data.getBoolean(MeadowCauldronStation.HEATED)) {
            return new StepBadge.Step(ItemStack.EMPTY, StepBadge.Glyph.FLAME, true, 0, made.getCount(), Float.NaN, 0, true,
                    made.getHoverName());
        }
        return new StepBadge.Step(made, null, false, 0, made.getCount(), Float.NaN, 0, false, made.getHoverName());
    }

    /** How much bigger the badge is drawn now: a swell as the dish gets done, a slow breath while it waits. */
    private float badgeSwell() {
        float since = time - doneAt;
        if (since >= 0.0F && since < DONE_TIME) {
            return 1.0F + DONE_SWELL * Mth.sin(Mth.PI * since / DONE_TIME);
        }
        return !output.isEmpty() ? 1.0F + READY_BREATH * (0.5F - 0.5F * Mth.cos(time * 2.4F)) : 1.0F;
    }
}
