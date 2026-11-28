package com.pockyl.handson_letsdo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.handson_letsdo.station.Dish;
import com.pockyl.handson_letsdo.station.FarmCharmDishes;
import com.pockyl.handson_letsdo.station.MincerHand;
import com.pockyl.handson_letsdo.station.MincerRecipes;
import com.pockyl.handson_letsdo.station.MincerStation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The mincer: Farm &amp; Charm's own model, its crank turned by the cook's hand. A click on the crank takes it by the grip, and it goes
 * round after the mouse until the next click, but not freely: it has weight, the load on the hopper holds it back (meat less, wood,
 * stone and metal more, a full load more than one item), the blade catches on it four times a turn, so the grip lags the hand, picks
 * up speed slowly, stalls and lurches on at each catch with a crunch of what is ground, and runs down by itself ({@link CrankFeel});
 * the mincer strains and creaks under a hard pull. What stands in the hopper's mouth goes down into it as the load is turned, and every
 * piece of the load comes out of the die one after another and drops onto the foot under the spout ({@link MincerOutlet}). Let go, the
 * crank runs down and comes round to where it stands at rest. The scene is the mincer in the world wherever it is drawn, also with
 * nobody at it.
 */
public final class MincerBehavior implements StationBehavior {
    @SuppressWarnings("removal")
    private static final SoundEvent CRANKING = SoundEvent.createVariableRangeEvent(new ResourceLocation(FarmCharmDishes.MOD_ID,
            "mincer_cranking"));
    /** A press shorter than this takes the crank in hand until the next click. */
    private static final float TAKE_CLICK = 0.3F;
    /** The crank's axle in the scene (X through y 10, z 8), and how the node turns: its angle is the grip's, the other way. */
    private static final float AXLE_Y = 10.0F;
    private static final float AXLE_Z = 8.0F;
    private static final float TURN_SIGN = -1.0F;
    /** Another cook's crank, as the server tells it, follows at most as fast as the server counts it: a whole turn in six ticks. */
    private static final float FASTEST = Mth.TWO_PI / 0.3F;
    /** Let go, it runs down first: slower than this, radians a second, it comes round to rest. */
    private static final float RUN_DOWN = 0.4F;
    /** Straining harder than this share of the hand's strength, the mincer shakes a little and creaks now and then. */
    private static final float STRAIN_FROM = 0.55F;
    private static final float CREAK_GAP = 0.4F;
    private static final float CATCH_GAP = 0.06F;
    /** How often the hand's angle is told to the server, seconds, and the least change worth telling, radians. */
    private static final float SEND_EVERY = 0.05F;
    private static final float SEND_LEAST = 0.03F;
    /** Let go, the crank comes round to rest at this pace, radians a second at the most. */
    private static final float REST_PACE = 7.0F;
    /** Another's crank that has not moved this long, seconds, is let go. */
    private static final float OTHERS_HOLD = 0.5F;
    /** What stands in the hopper's mouth goes this far down into it, and shrinks by this much, while a load is turned. */
    private static final float SINK = 3.2F;
    private static final float SINK_SHRINK = 0.2F;
    /** The crank's sound at full pace, and the pace that is full, radians a second. */
    private static final float CRANK_VOLUME = 1.0F;
    private static final float FULL_PACE = Mth.TWO_PI / 0.8F;
    private static final Vector3f ICON_MIDDLE = new Vector3f(8.0F, 8.0F, 8.0F);
    private static final Vector3f DIE = new Vector3f(1.0F, 9.5F, 8.0F);
    private static final float DONE_SWELL = 0.35F;
    private static final float DONE_TIME = 0.6F;
    /**
     * A stack carried over the hopper's mouth stands there see-through, as big as what is minced stands (the input slot's 5 pixels; an
     * item visual at scale 1 is as big as a slot of 16), turned as it (an item visual faces the other way than a slot's item).
     */
    private static final float INPUT_SIZE = 5.0F;
    private static final int GHOST_TINT = 0xA8FFFFFF;
    private static final Vector3f ITEM_FOOT = new Vector3f(8.0F, 0.0F, 8.0F);

    private final RecipeBook book = new RecipeBook(new MincerBook());
    private final StepBadge badge = new StepBadge(10, -10, false);
    private final CrankFeel feel = new CrankFeel();
    private final MincerOutlet outlet = new MincerOutlet();
    private String grinds = "";
    /** How what it grinds looks and sounds going through ({@link MincerStation#looks}). */
    private String looks = "";
    private float creakAt = -10.0F;
    private float caughtAt = -10.0F;
    private final UsableCache usable = new UsableCache();
    private List<Dish> recipes = List.of();
    private float time;
    /** The local player's hand is on the crank; a click took it, so the next click lets go; when the press began. */
    private boolean stroking;
    private boolean heldByClick;
    private float strokeStart;
    /** The crank's angle as shown, radians, the grip's way round from where it rests; never wrapped. */
    private float angle;
    /** Which way it last went, for coming round to rest the same way. */
    private float going = 1.0F;
    private float pace;
    private Item lookOf;
    private float sentAngle = Float.NaN;
    private float sendClock;
    /** Another cook's crank as last told, and when it last changed. */
    private float othersAngle = Float.NaN;
    private float othersSince = -10.0F;
    private float sink;
    private float grindClock;
    private float doneAt = -10.0F;
    private ItemStack making = ItemStack.EMPTY;
    private int inputColor = 0xFFB0A090;
    private int mincedColor = 0xFFB0A090;
    private Item ghostOf;

    public MincerBehavior(StationView view) {
    }

    @Override
    public void onSceneReady(StationView view) {
        ghostOf = null;
        badge.ready(view);
        view.node("input_ghost").ifPresent(node -> {
            node.setOnTop(true);
            node.setTint(GHOST_TINT);
            node.rotateTo(0.0F, 180.0F, 0.0F, 0.0F);
            node.scaleTo(INPUT_SIZE / 16.0F, 0.0F);
        });
        read(view);
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        refresh(view);
    }

    /** The mincer's recipes, and what goes onto its hopper shown see-through while it is empty. */
    private void read(StationView view) {
        recipes = MincerRecipes.all(view.level());
        List<ItemStack> takes = new ArrayList<>();
        for (Dish recipe : recipes) {
            for (ItemStack stack : recipe.ingredients().get(0).getItems()) {
                takes.add(stack.copyWithCount(1));
            }
        }
        view.node("input").ifPresent(node -> node.setGhostItems(takes));
    }

    @Override
    public void onOpen(StationView view) {
        read(view);
        usable.clear();
        view.setInventoryFilter(stack -> usable.test(stack, it -> MincerRecipes.taking(recipes, it) != null));
        book.opened(view);
        refresh(view);
    }

    @Override
    public void onClose(StationView view) {
        stroking = false;
        heldByClick = false;
        sentAngle = Float.NaN;
        book.shut(view);
        refresh(view);
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        refresh(view);
    }

    /** The scene is the mincer in the world wherever it is drawn, where the server told the mincer what the station adds to it. */
    @Override
    public boolean holdsBlock(StationView view) {
        return DiegesisClient.isSceneDrawn(view.pos()) && view.level().getBlockEntity(view.pos()) instanceof MincerHand hand && hand.handson$told();
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Stroke stroke && "crank".equals(stroke.node())) {
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
                        // A click takes the crank by its grip: it turns as the mouse goes round, with no button to hold.
                        heldByClick = true;
                        view.holdStroke();
                    }
                }
                default -> {
                }
            }
        } else if (event instanceof SceneEvent.Click click && "book_open".equals(click.node())) {
            book.click(view, click.button());
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

    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ("book_open".equals(node)) {
            book.click(view, button);
            return true;
        }
        return false;
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        if (!MincerStation.EFFECT_MINCED.equals(effect)) {
            return;
        }
        // The load is through: its last piece is out, with a puff at the die and the sound of what was ground.
        doneAt = time;
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(data.getString(MincerStation.ITEM)));
        if (item != null && item != Items.AIR) {
            mincedColor = 0xFF000000 | FoodColors.main(new ItemStack(item));
        }
        view.spawnParticles(DiegesisParticles.DUST, DIE, new Vector3f(-2.0F, 0.5F, 0.0F), 0.6F, 5, mincedColor);
        view.spawnParticles(DiegesisParticles.SMOKE, DIE, new Vector3f(-1.5F, 0.8F, 0.0F), 0.3F, 2, 0x80E8E4DC);
        view.playSound(grind(data.getString(MincerStation.KIND)), MincerStation.DIE, 0.3F, 1.0F);
    }

    /** What a load sounds like coming out: soft for meat and fruit, gritty for stone, wood and metal, sandy for meal. */
    private static SoundEvent grind(String kind) {
        return switch (kind) {
            case "WOOD" -> SoundEvents.WOOD_BREAK;
            case "STONE" -> SoundEvents.GRAVEL_BREAK;
            case "METAL" -> SoundEvents.GRINDSTONE_USE;
            case MincerStation.MILLED -> SoundEvents.SAND_BREAK;
            default -> SoundEvents.SLIME_SQUISH_SMALL;
        };
    }

    private void refresh(StationView view) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(view.state().data().getString(MincerStation.MAKING)));
        making = item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        ItemStack lying = view.state().getItem(MincerStation.INPUT_SLOT);
        if (!lying.isEmpty()) {
            inputColor = 0xFF000000 | FoodColors.main(lying);
        }
        grinds = view.state().data().getString(MincerStation.GRINDS);
        looks = MincerStation.looks(grinds, making);
    }

    @Override
    public void frame(StationView view, float dt) {
        time += dt;
        CompoundTag data = view.state().data();
        turnCrank(view, dt, data);
        float turned = data.getFloat(MincerStation.TURNED);
        // What stands in the hopper's mouth goes down into it as the load is turned; a new load comes up at once.
        sink = turned < sink ? turned : Mth.lerp(Math.min(1.0F, dt * 10.0F), sink, turned);
        float down = SINK * sink;
        view.node("input").ifPresent(node -> {
            node.moveTo(0.0F, -down, 0.0F, 0.0F);
            node.scaleTo(1.0F - SINK_SHRINK * sink, 0.0F);
        });
        grinding(view, dt);
        ItemStack lying = view.state().getItem(MincerStation.INPUT_SLOT);
        Dish recipe = MincerRecipes.taking(recipes, lying);
        int perInput = recipe == null ? 1 : recipe.result().getCount();
        outlet.frame(view, dt, view.state().getItem(MincerStation.MINCED_SLOT), making, perInput, data.getInt(MincerStation.LOAD_SIZE), turned,
                Math.min(1.0F, pace / FULL_PACE), looks);
        outline(view);
        guide(view);
        book.frame(view, dt);
        float appear = RecipeBook.appear(view);
        StepBadge.Step step = step(view, turned);
        badge.show(view, view.isInUse() && RecipeBook.isWorkedAt(view) && appear > RecipeBook.SPECK && step != null, step,
                StepBadge.Extras.NONE, time);
        badge.scale(view, appear * badgeSwell());
    }

    /**
     * A stack carried over the hopper's mouth is guided onto it: it stands there see-through where it will stand, over the mincer's
     * body, instead of hanging on the cursor where the body would hide it. Only what the hopper takes.
     */
    private void guide(StationView view) {
        LocalPlayer player = Minecraft.getInstance().player;
        ItemStack carried = player == null || !view.isFocused() ? ItemStack.EMPTY : player.inventoryMenu.getCarried();
        boolean over = !carried.isEmpty() && (view.node("hopper").map(NodeView::isHovered).orElse(false)
                || view.node("input").map(NodeView::isHovered).orElse(false));
        ItemStack lying = view.state().getItem(MincerStation.INPUT_SLOT);
        boolean takes = over && MincerRecipes.taking(recipes, carried) != null
                && (lying.isEmpty() || ItemStack.isSameItemSameTags(lying, carried) && lying.getCount() < lying.getMaxStackSize());
        view.setCarriedHidden(takes);
        view.node("input_ghost").ifPresent(node -> {
            node.setVisible(takes);
            if (takes && ghostOf != carried.getItem()) {
                ghostOf = carried.getItem();
                node.setVisual(Visual.ofItem(ghostOf).withAnchor(ITEM_FOOT));
            }
        });
    }

    /**
     * The crank goes with the hand that holds it as a crank with a load on it goes, or with another cook's as the server tells it; let
     * go, it runs down and comes round the way it went to where it rests. Its angle goes to the server, which counts the turns.
     */
    private void turnCrank(StationView view, float dt, CompoundTag data) {
        float before = angle;
        boolean hand = stroking && view.isFocused();
        float heaviness = MincerStation.heaviness(grinds, data.getInt(MincerStation.LOAD_SIZE));
        if (data.contains(MincerStation.CRANK)) {
            float told = data.getFloat(MincerStation.CRANK);
            if (Float.isNaN(othersAngle) || Math.abs(told - othersAngle) > 1.0e-4F) {
                othersAngle = told;
                othersSince = time;
            }
        }
        boolean others = !hand && !heldByClick && !Float.isNaN(othersAngle) && time - othersSince < OTHERS_HOLD;
        if (hand || heldByClick) {
            // The cook's hand on the grip: the crank goes as heavily as its load lets it.
            float want = hand ? gripAngle(view).orElse(Float.NaN) : Float.NaN;
            int catches = feel.step(dt, want, heaviness);
            angle = feel.angle();
            caught(view, catches, heaviness);
            strained(view);
            if (hand) {
                send(view, dt);
            }
        } else if (others) {
            // Another cook turns it: it goes as the server was told.
            follow(othersAngle, dt);
            feel.set(angle);
        } else if (Math.abs(feel.speed()) > RUN_DOWN) {
            // Let go while it turns: it runs down by itself under its load.
            feel.letGo();
            int catches = feel.step(dt, Float.NaN, heaviness);
            angle = feel.angle();
            caught(view, catches, heaviness);
        } else {
            // Round to the nearest rest the way it last went, softly.
            float rest = Mth.TWO_PI * (going > 0.0F ? Mth.ceil(angle / Mth.TWO_PI - 1.0e-3F) : Mth.floor(angle / Mth.TWO_PI + 1.0e-3F));
            float step = Mth.clamp((rest - angle) * Math.min(1.0F, dt * 6.0F), -REST_PACE * dt, REST_PACE * dt);
            angle += step;
            if (Math.abs(rest - angle) < 1.0e-3F) {
                angle = rest;
            }
            feel.set(angle);
        }
        float moved = angle - before;
        if (Math.abs(moved) > 1.0e-4F) {
            going = Math.signum(moved);
        }
        pace = dt > 0.0F ? Mth.lerp(Math.min(1.0F, dt * 8.0F), pace, Math.abs(moved) / dt) : pace;
        float shown = angle;
        view.node("crank").ifPresent(node -> node.rotateTo(TURN_SIGN * shown * Mth.RAD_TO_DEG, 0.0F, 0.0F, 0.0F));
        view.loopSound("crank", CRANKING, CRANK_VOLUME * Math.min(1.0F, pace / FULL_PACE), 1.0F);
    }

    /**
     * The blade caught on what it grinds and went through: the crank jerks, the mincer shudders, what stands in the hopper jolts, and
     * the crunch of what is ground; nothing to grind, nothing catches.
     */
    private void caught(StationView view, int catches, float heaviness) {
        if (catches <= 0 || heaviness <= 0.0F || time - caughtAt < CATCH_GAP) {
            return;
        }
        caughtAt = time;
        float hard = Math.min(1.0F, 0.4F + heaviness);
        view.node("crank").ifPresent(node -> node.shake(0.12F * hard, 0.1F));
        view.node("body").ifPresent(node -> node.shake(0.05F * hard, 0.08F));
        view.node("input").ifPresent(node -> node.shake(0.15F * hard, 0.1F));
        SoundEvent crunch = switch (looks) {
            case "WOOD" -> SoundEvents.WOOD_BREAK;
            case "STONE" -> SoundEvents.GRAVEL_HIT;
            case "METAL" -> SoundEvents.GRINDSTONE_USE;
            case MincerStation.MILLED -> SoundEvents.SAND_HIT;
            default -> SoundEvents.SLIME_SQUISH_SMALL;
        };
        float volume = "METAL".equals(looks) ? 0.07F : "WOOD".equals(looks) ? 0.1F : 0.16F;
        view.playSound(crunch, "hopper", volume * (0.7F + 0.6F * hard), 0.8F + view.level().random.nextFloat() * 0.3F);
        view.spawnParticles(DiegesisParticles.DUST, new Vector3f(9.5F, 16.2F, 8.0F), new Vector3f(0.0F, 1.2F, 0.0F), 1.0F, 2, inputColor);
    }

    /** Pulling hard: the crank trembles in the hand and the mincer creaks now and then. */
    private void strained(StationView view) {
        float strain = feel.strain();
        if (strain < STRAIN_FROM) {
            return;
        }
        if (view.level().random.nextFloat() < 0.3F) {
            view.node("crank").ifPresent(node -> node.shake(0.05F * strain, 0.06F));
        }
        if (time - creakAt > CREAK_GAP + view.level().random.nextFloat() * 0.4F) {
            creakAt = time;
            view.playSound(PressSounds.CREAK, "crank", 0.05F + 0.08F * strain, 0.5F + view.level().random.nextFloat() * 0.1F);
        }
    }

    /** The outline goes round the heap under the spout when an empty hand is over it, as round anything that can be taken. */
    private void outline(StationView view) {
        ItemStack lying = view.state().getItem(MincerStation.MINCED_SLOT);
        LocalPlayer player = Minecraft.getInstance().player;
        boolean free = view.isFocused() && !stroking && !heldByClick && player != null && player.inventoryMenu.getCarried().isEmpty();
        boolean lit = free && !lying.isEmpty() && view.node("minced").map(NodeView::isHovered).orElse(false);
        view.node("minced_look").ifPresent(node -> {
            if (!lying.isEmpty() && lookOf != lying.getItem()) {
                lookOf = lying.getItem();
                node.setVisual(Visual.ofItem(lookOf).withAnchor(ICON_MIDDLE));
                node.setTint(0x00FFFFFF);
            }
            node.setVisible(!lying.isEmpty());
            node.setOutlined(lit);
        });
    }

    /** Where the hand has the grip: the cursor on the plane the crank turns in, as an angle round the axle from where it rests. */
    private static Optional<Float> gripAngle(StationView view) {
        NodeView plane = view.node("crank_plane").orElse(null);
        if (plane == null) {
            return Optional.empty();
        }
        return view.pointerOn("crank_plane", 0.0F).map(local -> {
            Vector3f at = plane.sceneMatrix().transformPosition(new Vector3f(local));
            return (float) Math.atan2(at.y - AXLE_Y, at.z - AXLE_Z);
        });
    }

    /** Turns the crank the short way round towards {@code want}, no faster than a hand turns it. */
    private void follow(float want, float dt) {
        float way = Mth.wrapDegrees((want - angle) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        float step = way * Math.min(1.0F, dt * 25.0F);
        angle += Mth.clamp(step, -FASTEST * dt, FASTEST * dt);
    }

    /** The hand's angle goes to the server now and then, while it moves. */
    private void send(StationView view, float dt) {
        sendClock -= dt;
        if (sendClock > 0.0F || !Float.isNaN(sentAngle) && Math.abs(angle - sentAngle) < SEND_LEAST) {
            return;
        }
        sendClock = SEND_EVERY;
        sentAngle = angle;
        CompoundTag where = new CompoundTag();
        where.putFloat(MincerStation.ANGLE, Mth.wrapDegrees(angle * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD);
        view.sendAction(MincerStation.ACTION_CRANK, where);
    }

    /** While a load turns, crumbs of it fall into the hopper's mouth and out of the die. */
    private void grinding(StationView view, float dt) {
        if (pace < 1.0F || making.isEmpty()) {
            return;
        }
        grindClock -= dt * Math.min(1.0F, pace / FULL_PACE);
        if (grindClock > 0.0F) {
            return;
        }
        grindClock = 0.12F + view.level().random.nextFloat() * 0.12F;
        view.spawnParticles(DiegesisParticles.DUST, new Vector3f(9.5F, 16.2F, 8.0F), new Vector3f(0.0F, -1.0F, 0.0F), 1.2F, 1, inputColor);
        if (view.level().random.nextFloat() < 0.5F) {
            view.spawnParticles(DiegesisParticles.DUST, DIE, new Vector3f(-1.2F, -0.5F, 0.0F), 0.4F, 1, mincedColor);
        }
    }

    private float badgeSwell() {
        float since = time - doneAt;
        if (since >= 0.0F && since < DONE_TIME) {
            return 1.0F + DONE_SWELL * Mth.sin(Mth.PI * since / DONE_TIME);
        }
        return 1.0F;
    }

    /**
     * What the badge shows: while something lies on the hopper, the crank as long as nobody turns it (the load waits for the hand), what
     * it becomes while it is turned, with a bar for the load's two turns; what lies under the spout once nothing is left to mince, which
     * is simply taken. Ready: what lies under the spout; still to come: what is on the hopper makes. Null for nothing.
     */
    private StepBadge.Step step(StationView view, float turned) {
        ItemStack minced = view.state().getItem(MincerStation.MINCED_SLOT);
        ItemStack lying = view.state().getItem(MincerStation.INPUT_SLOT);
        Dish recipe = MincerRecipes.taking(recipes, lying);
        int ready = minced.getCount();
        if (recipe != null) {
            int more = lying.getCount() * recipe.result().getCount();
            ItemStack makes = making.isEmpty() ? recipe.result() : making;
            boolean turning = heldByClick || stroking || pace > 0.5F || time - othersSince < OTHERS_HOLD;
            return new StepBadge.Step(makes, turning ? null : StepBadge.Glyph.CRANK, false, ready, more, turned, 0, false,
                    makes.getHoverName());
        }
        if (ready > 0) {
            return new StepBadge.Step(minced, null, false, ready, 0, 1.0F, 0, false, minced.getHoverName());
        }
        return null;
    }
}
