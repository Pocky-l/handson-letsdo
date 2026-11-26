package com.pockyl.handson_letsdo.client;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.handson_letsdo.station.Bowl;
import com.pockyl.handson_letsdo.station.BowlHand;
import com.pockyl.handson_letsdo.station.BowlRecipes;
import com.pockyl.handson_letsdo.station.BowlStation;
import com.pockyl.handson_letsdo.station.Dish;
import com.pockyl.handson_letsdo.station.FarmCharmDishes;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * The crafting bowl: Farm &amp; Charm's own bowl and whisk, standing as the block stands whichever side the cook comes from. A click on
 * the whisk's handle takes it in hand, and until the next click it goes where the hand goes, as a whisk does: its loops come after
 * the hand through the mixture, lagging the more the thicker it is, the handle leaning ahead as it is dragged, wobbling when it stops,
 * knocking on the walls; round, to and fro or in figures of eight, every way counts. What is in the bowl is thrown about by it and
 * the mixture swirls and splashes under it ({@link BowlMix}). Once it has had its way through the bowlful the mixture is done, and
 * what it became shows on the badge to the right, where a click takes it, as one on the finished dough in the bowl does. Let go,
 * the whisk is set back to rest against the rim. The scene is the bowl in the world wherever it is drawn, also with nobody at it.
 */
public final class BowlBehavior implements StationBehavior {
    private static final String[] ITEM_NODES = {"item_0", "item_1", "item_2", "item_3"};
    private static final SoundEvent STIRRING = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(FarmCharmDishes.MOD_ID,
            "crafting_bowl_stirring"));
    private static final float TAKE_CLICK = 0.3F;

    /** The height the hand's cursor is read at, over the bowl's floor: about where the whisk's loops are; how far out it reaches. */
    private static final float HAND_HEIGHT = 4.0F;
    private static final float HAND_REACH = 3.3F;
    /**
     * The mixture resists the whisk, the more the heavier it is and the fuller the bowl ({@link BowlMix#resistance}). The hand pulls the loops as a spring (a second squared) that
     * a thin liquid hardly holds back, so they slip after it and overshoot a little; in a batter the spring is weak and the mixture
     * holds them back hard (a second), so they lag the hand and stop dead. No jerk goes through a batter: the loops gather speed no
     * faster than this (pixels a second squared). A batter also clings to them: it is drawn along slowly behind them (seconds to
     * catch up) and pulls them back towards it (a second squared), so it takes steady pushing to keep them going and they sag back
     * when the hand stops. Their fastest goes from the loose pace to the thick one ({@link BowlStation#PACE_LOOSE}, {@link
     * BowlStation#PACE_THICK}) as it resists, a little under it; the server counts the hand's way alike.
     */
    private static final float PULL_THIN = 420.0F;
    private static final float PULL_THICK = 55.0F;
    private static final float HOLD_THIN = 20.0F;
    private static final float HOLD_THICK = 30.0F;
    private static final float SURGE_THIN = 1100.0F;
    private static final float SURGE_THICK = 120.0F;
    private static final float CLING_TIME = 0.3F;
    private static final float CLING = 60.0F;
    private static final float UNDER_PACE = 0.96F;
    /** What a wall gives back of the loops' speed; from what speed they knock on it, how often at most. */
    private static final float WALL_GIVE = 0.25F;
    private static final float KNOCK_FROM = 6.0F;
    private static final float KNOCK_GAP = 0.1F;
    /**
     * The handle leans: ahead of the loops by this much for each pixel they lag behind the hand, towards the cook while it is held, at
     * most this far (the sine of its lean); it swings there as a spring that does not quite stop at once, and a hand at speed shakes it
     * a little.
     */
    private static final float LEAD = 0.18F;
    private static final float HOLD_LEAN = 0.1F;
    private static final float MOST_LEAN = 0.5F;
    private static final float LEAN_PULL = 300.0F;
    private static final float LEAN_HOLD = 11.0F;
    private static final float TREMOR = 0.035F;
    /**
     * The whisk's length from its loops' foot to the handle's end, pixels, and how far from the bowl's middle that end may be: its handle
     * leans on the rim, it never goes through it.
     */
    private static final float LENGTH = 12.0F;
    private static final float TOP_REACH = 5.6F;
    /**
     * The loops stay inside the walls (5 px from the middle, a hair less): their foot is 5 px wide, so it goes no further out than
     * this; their top, narrower, 6 px up, leans with the handle no further out than that.
     */
    private static final float FOOT_REACH = 2.35F;
    private static final float LOOPS_HIGH = 6.0F;
    private static final float LOOPS_TOP_REACH = 3.65F;
    /** It turns about its own handle as the hand works it, degrees, every so many pixels of the way. */
    private static final float ROLL = 18.0F;
    private static final float ROLL_WAY = 5.0F;
    /** Taken in hand it leaves its rest this quickly, seconds; let go, it is set back in this long. */
    private static final float TAKE_TIME = 0.14F;
    private static final float REST_TIME = 0.4F;
    /**
     * Its rest, as Farm &amp; Charm draws it: the loops' foot (the scene's whisk_pivot, from the bowl's middle in the bowl's own sides)
     * and its lean, the model's upright turned 40 degrees about +z, the handle over the -x rim. The loops' foot rides this high over the
     * floor for each part of its lean, so that they never go through it.
     */
    private static final Vector3f REST = new Vector3f(2.84814F, 2.82621F, 0.0F);
    private static final Quaternionf REST_TURN = new Quaternionf().rotationZ(39.998F * Mth.DEG_TO_RAD);
    private static final Vector3f UP = new Vector3f(0.0F, 1.0F, 0.0F);
    private static final float FOOT = 0.4F;
    private static final float FOOT_PER_LEAN = 2.4F;
    private static final float SEND_EVERY = 0.05F;
    private static final float SEND_LEAST = 0.12F;
    private static final float OTHERS_HOLD = 0.5F;
    private static final float STIR_VOLUME = 0.8F;
    private static final float FULL_PACE = 24.0F;
    /**
     * The colour a finished mixture leans to besides its result's: Farm &amp; Charm's dough, which is what its bowl shows for every
     * recipe.
     */
    private static final int BATTER = 0xFFE9D9B2;
    private static final float DONE_SWELL = 0.35F;
    private static final float DONE_TIME = 0.6F;
    /** What waits on the badge breathes, by this much, every so many seconds. */
    private static final float READY_SWELL = 0.06F;
    private static final float READY_BEAT = 1.4F;

    private final RecipeBook book = new RecipeBook(new BowlBook());
    private final StepBadge badge = new StepBadge(10, -10, false);
    private final UsableCache usable = new UsableCache();
    private final BowlMix mix = new BowlMix(ITEM_NODES);
    private List<Dish> recipes = List.of();
    private float time;
    private boolean stroking;
    private boolean heldByClick;
    private float strokeStart;
    /** The loops' foot and its way, the handle's lean and its swing: scene pixels from the bowl's middle. */
    private float tipX;
    private float tipZ;
    private float tipVX;
    private float tipVZ;
    /** Where the batter that clings to the loops is, drawn along behind them. */
    private float clingX;
    private float clingZ;
    private float leanX;
    private float leanZ;
    private float leanVX;
    private float leanVZ;
    private float tremorX;
    private float tremorZ;
    private float rolled;
    /** 0 at rest against the rim, 1 in the hand (or another cook's). */
    private float held;
    private boolean atRest = true;
    private float knockedAt = -10.0F;
    private float sentX = Float.NaN;
    private float sentZ;
    private float sendClock;
    private float othersX = Float.NaN;
    private float othersZ;
    private float othersSince = -10.0F;
    /** The bowl's turn back to the world's sides, degrees, as last shown. */
    private float shownTurn = Float.NaN;
    private boolean quiet = true;
    private boolean resultWaits;
    private ItemStack waiting = ItemStack.EMPTY;
    private float doneAt = -10.0F;
    private ItemStack making = ItemStack.EMPTY;
    private int finalColor = BATTER;
    private int becomesColor = 0xFFFFFFFF;

    public BowlBehavior(StationView view) {
    }

    @Override
    public void onSceneReady(StationView view) {
        shownTurn = Float.NaN;
        quiet = true;
        recipes = BowlRecipes.all(view.level());
        badge.ready(view);
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        refresh(view);
    }

    @Override
    public void onOpen(StationView view) {
        recipes = BowlRecipes.all(view.level());
        usable.clear();
        view.setInventoryFilter(stack -> usable.test(stack, it -> BowlRecipes.uses(recipes, it)) || drawsBack(view, stack));
        book.opened(view);
        refresh(view);
    }

    @Override
    public void onClose(StationView view) {
        stroking = false;
        heldByClick = false;
        sentX = Float.NaN;
        book.shut(view);
        refresh(view);
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        refresh(view);
    }

    @Override
    public boolean holdsBlock(StationView view) {
        return DiegesisClient.isSceneDrawn(view.pos()) && view.level().getBlockEntity(view.pos()) instanceof BowlHand hand && hand.handson$told();
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Stroke stroke && "whisk".equals(stroke.node())) {
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
                        // A click takes the whisk by its handle: it goes where the mouse goes, with no button to hold.
                        heldByClick = true;
                        view.holdStroke();
                    }
                }
                default -> {
                }
            }
        } else if (event instanceof SceneEvent.Click click && "book_open".equals(click.node())) {
            book.click(view, click.button());
        } else if (event instanceof SceneEvent.Click click && ("preview".equals(click.node()) || "dough".equals(click.node())) && resultWaits) {
            // What the bowlful became is taken from the badge, or from the dough waiting in the bowl, as an empty hand takes it from the block.
            view.sendAction(BowlStation.ACTION_TAKE, new CompoundTag());
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
        if (!"preview".equals(node) && !"whisk".equals(node) && drawsBack(view, stack)) {
            // An empty container over the bowl draws back what was poured in such a one.
            CompoundTag data = new CompoundTag();
            data.putBoolean(BowlStation.ALL, shift);
            view.sendAction(BowlStation.ACTION_DRAW, data);
            return true;
        }
        return false;
    }

    /** Whether {@code stack} is an empty container that what lies in the bowl was poured from. */
    private static boolean drawsBack(StationView view, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (ItemStack in : ingredientsOf(view)) {
            if (!in.isEmpty() && ItemStack.isSameItemSameComponents(BowlStation.containerOf(in), stack.copyWithCount(1))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        if (BowlStation.EFFECT_MIXED.equals(effect)) {
            doneAt = time;
            mix.mixed();
            view.playSound(SoundEvents.HONEY_BLOCK_PLACE, "mixture", 0.3F, 1.0F);
            view.playSound(SoundEvents.SLIME_SQUISH_SMALL, "mixture", 0.2F, 0.8F);
            Vector3f top = new Vector3f(8.0F, mix.surface() + 0.3F, 8.0F);
            view.spawnParticles(DiegesisParticles.DUST, top, new Vector3f(0.0F, 2.0F, 0.0F), 2.6F, 8, finalColor);
            view.spawnParticles(DiegesisParticles.GLINT, top, new Vector3f(0.0F, 1.5F, 0.0F), 2.4F, 5, 0xFFFFF0C8);
        } else if (BowlStation.EFFECT_TAKEN.equals(effect)) {
            // Scooped out: the mixture goes as the result goes to the cook.
            view.playSound(SoundEvents.ITEM_PICKUP, BowlStation.RESULT, 0.3F, 1.1F);
            view.playSound(SoundEvents.HONEY_BLOCK_BREAK, "mixture", 0.2F, 1.2F);
            view.spawnParticles(DiegesisParticles.DUST, new Vector3f(8.0F, mix.surface(), 8.0F), new Vector3f(0.0F, 2.5F, 0.0F), 2.0F, 4,
                    finalColor);
        } else if (BowlStation.EFFECT_DRAWN.equals(effect)) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(data.getString(BowlStation.ITEM)));
            boolean bucket = item != null && item.getCraftingRemainingItem() == Items.BUCKET;
            view.playSound(bucket ? SoundEvents.BUCKET_FILL : SoundEvents.BOTTLE_FILL, "mixture", 0.4F, 1.0F);
            mix.drawn(view);
        }
    }

    private void refresh(StationView view) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(view.state().data().getString(BowlStation.MAKING)));
        making = item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        waiting = view.state().getItem(BowlStation.RESULT_SLOT).copy();
        resultWaits = !waiting.isEmpty();
        // The finished dough is what waits: then, and only then, a click on it takes the result.
        view.node("dough").ifPresent(dough -> dough.setEnabled(resultWaits));
        ItemStack becomes = resultWaits ? waiting : making;
        if (!becomes.isEmpty()) {
            becomesColor = 0xFF000000 | FoodColors.main(becomes);
            BowlMass mass = BowlMass.of(becomes, ingredientsOf(view).stream().filter(stack -> !stack.isEmpty()).toList());
            finalColor = mass.color(becomesColor);
            mix.mass(mass);
        }
        mix.sync(view, ingredientsOf(view).toArray(ItemStack[]::new), quiet);
        quiet = false;
    }

    @Override
    public void frame(StationView view, float dt) {
        time += dt;
        CompoundTag data = view.state().data();
        turnToWorld(view);
        float turn = shownTurn * Mth.DEG_TO_RAD;
        BowlMix.Whisk whisk = whisk(view, dt, data, turn);
        mix.frame(view, dt, whisk, data.getFloat(BowlStation.STIRRED), resultWaits, finalColor, becomesColor, stroking || heldByClick);
        book.frame(view, dt);
        float appear = RecipeBook.appear(view);
        StepBadge.Step step = step(view, data.getFloat(BowlStation.STIRRED), data.getInt(BowlStation.SETS));
        badge.show(view, view.isInUse() && RecipeBook.isWorkedAt(view) && appear > RecipeBook.SPECK && step != null, step,
                StepBadge.Extras.NONE, time);
        float breath = resultWaits ? 1.0F + READY_SWELL * Math.max(0.0F, Mth.sin(time / READY_BEAT * Mth.TWO_PI)) : 1.0F;
        badge.scale(view, appear * badgeSwell(), breath);
    }

    /**
     * The bowl stands as the block does: the scene turns to the cook, the bowl turns back. Its whisk always rests the same way in the
     * world, so nothing moves when somebody comes or goes.
     */
    private void turnToWorld(StationView view) {
        float turn = view.facing().toYRot() - Direction.NORTH.toYRot();
        if (turn != shownTurn) {
            shownTurn = turn;
            view.node("bowl").ifPresent(node -> node.rotateTo(0.0F, turn, 0.0F, 0.0F));
        }
    }

    /**
     * The whisk: in the local hand it follows the cursor, in another cook's the places the server tells; let go, it is set back to rest.
     * The local hand's place goes to the server, which counts its way. Returns the whisk as the bowl feels it.
     */
    private BowlMix.Whisk whisk(StationView view, float dt, CompoundTag data, float turn) {
        if (data.contains(BowlStation.WHISK_X)) {
            Vector3f told = new Vector3f(data.getFloat(BowlStation.WHISK_X), 0.0F, data.getFloat(BowlStation.WHISK_Z)).rotateY(turn);
            if (Float.isNaN(othersX) || Math.abs(told.x - othersX) > 1.0e-4F || Math.abs(told.z - othersZ) > 1.0e-4F) {
                othersX = told.x;
                othersZ = told.z;
                othersSince = time;
            }
        }
        boolean hand = stroking && view.isFocused();
        boolean others = !hand && !heldByClick && !Float.isNaN(othersX) && time - othersSince < OTHERS_HOLD;
        boolean active = hand || heldByClick || others;
        Vector3f rest = new Vector3f(REST).rotateY(turn);
        Vector3f restLean = new Vector3f(REST_TURN.transform(new Vector3f(UP))).rotateY(turn);
        mix.rest(rest.x, rest.z);
        if (atRest && !active) {
            tipX = rest.x;
            tipZ = rest.z;
            clingX = tipX;
            clingZ = tipZ;
            tipVX = 0.0F;
            tipVZ = 0.0F;
            leanX = restLean.x;
            leanZ = restLean.z;
            leanVX = 0.0F;
            leanVZ = 0.0F;
        }
        float before = held;
        held = active ? Math.min(1.0F, held + dt / TAKE_TIME) : Math.max(0.0F, held - dt / REST_TIME);
        atRest = held <= 0.0F;
        if (before > 0.0F && atRest) {
            // Set back against the rim: wire on wood.
            view.playSound(SoundEvents.WOOD_HIT, "whisk", 0.14F, 1.25F);
            view.playSound(SoundEvents.CHAIN_HIT, "whisk", 0.06F, 1.7F);
        }
        float goalX = tipX;
        float goalZ = tipZ;
        if (hand) {
            Optional<Vector3f> cursor = view.cursorOn(HAND_HEIGHT);
            if (cursor.isPresent()) {
                float x = cursor.get().x - 8.0F;
                float z = cursor.get().z - 8.0F;
                float far = (float) Math.hypot(x, z);
                float keep = far > HAND_REACH ? HAND_REACH / far : 1.0F;
                goalX = x * keep;
                goalZ = z * keep;
            }
        } else if (others) {
            goalX = othersX;
            goalZ = othersZ;
        }
        if (!atRest) {
            move(view, dt, goalX, goalZ, active);
        }
        pose(view, turn, rest);
        if (hand) {
            // The hand's way is what the server counts, not the loops' that the batter holds back: it resists only here.
            send(view, dt, turn, goalX, goalZ);
        }
        float speed = (float) Math.hypot(tipVX, tipVZ);
        view.loopSound("stir", STIRRING, STIR_VOLUME * Math.min(1.0F, speed / FULL_PACE) * held, 1.0F);
        return atRest ? new BowlMix.Whisk(rest.x, rest.z, 0.0F, 0.0F, false) : new BowlMix.Whisk(tipX, tipZ, tipVX, tipVZ, held > 0.5F);
    }

    /**
     * The loops after the hand's goal, as a spring the mixture holds back: easily in a thin liquid or a bowl nearly empty, hard in a full
     * bowl of dough, which also clings to them and drags them back; no faster than the server counts them, stopped by the
     * walls; the handle leaning ahead of them and swinging there.
     */
    private void move(StationView view, float dt, float goalX, float goalZ, boolean active) {
        float visc = mix.resistance();
        float pull = Mth.lerp(visc, PULL_THIN, PULL_THICK);
        float hold = Mth.lerp(visc, HOLD_THIN, HOLD_THICK);
        float surge = Mth.lerp(visc, SURGE_THIN, SURGE_THICK);
        float cling = CLING * visc;
        float fastest = Mth.lerp(visc, BowlStation.PACE_LOOSE, BowlStation.PACE_THICK) * 20.0F * UNDER_PACE;
        float reach = Math.min(BowlStation.WHISK_REACH, FOOT_REACH);
        int steps = Math.max(1, Mth.ceil(dt / 0.008F));
        float h = dt / steps;
        float pace = Math.min(1.0F, (float) Math.hypot(tipVX, tipVZ) / FULL_PACE);
        if (active && view.level().random.nextFloat() < dt * 12.0F) {
            tremorX = (view.level().random.nextFloat() - 0.5F) * TREMOR * pace;
            tremorZ = (view.level().random.nextFloat() - 0.5F) * TREMOR * pace;
        }
        float follow = 1.0F - (float) Math.exp(-h / CLING_TIME);
        for (int i = 0; i < steps; i++) {
            float ax = pull * (goalX - tipX) - hold * tipVX + cling * (clingX - tipX);
            float az = pull * (goalZ - tipZ) - hold * tipVZ + cling * (clingZ - tipZ);
            float push = (float) Math.hypot(ax, az);
            if (push > surge) {
                ax *= surge / push;
                az *= surge / push;
            }
            tipVX += ax * h;
            tipVZ += az * h;
            float speed = (float) Math.hypot(tipVX, tipVZ);
            if (speed > fastest) {
                tipVX *= fastest / speed;
                tipVZ *= fastest / speed;
            }
            tipX += tipVX * h;
            tipZ += tipVZ * h;
            clingX += (tipX - clingX) * follow;
            clingZ += (tipZ - clingZ) * follow;
            if (Math.abs(tipX) > reach) {
                float side = Math.signum(tipX);
                tipX = side * reach;
                if (tipVX * side > 0.0F) {
                    knock(view, side, 0.0F, Math.abs(tipVX));
                    tipVX = -tipVX * WALL_GIVE;
                }
            }
            if (Math.abs(tipZ) > reach) {
                float side = Math.signum(tipZ);
                tipZ = side * reach;
                if (tipVZ * side > 0.0F) {
                    knock(view, 0.0F, side, Math.abs(tipVZ));
                    tipVZ = -tipVZ * WALL_GIVE;
                }
            }
            float wantX = (goalX - tipX) * LEAD + tremorX;
            float wantZ = (goalZ - tipZ) * LEAD - (active ? HOLD_LEAN : 0.0F) + tremorZ;
            float want = (float) Math.hypot(wantX, wantZ);
            if (want > MOST_LEAN) {
                wantX *= MOST_LEAN / want;
                wantZ *= MOST_LEAN / want;
            }
            leanVX += (LEAN_PULL * (wantX - leanX) - LEAN_HOLD * leanVX) * h;
            leanVZ += (LEAN_PULL * (wantZ - leanZ) - LEAN_HOLD * leanVZ) * h;
            leanX += leanVX * h;
            leanZ += leanVZ * h;
            rim();
        }
        float lean = (float) Math.hypot(leanX, leanZ);
        if (lean > 0.7F) {
            leanX *= 0.7F / lean;
            leanZ *= 0.7F / lean;
        }
        rolled += (float) Math.hypot(tipVX, tipVZ) * dt;
    }

    /**
     * The handle leans on the rim: its end goes no further out than the rim lets it, and stops swinging that way there; nor does it
     * lean the loops' top through a wall.
     */
    private void rim() {
        float loopsX = tipX + leanX * LOOPS_HIGH;
        float loopsZ = tipZ + leanZ * LOOPS_HIGH;
        if (Math.abs(loopsX) > LOOPS_TOP_REACH) {
            leanX = (Math.signum(loopsX) * LOOPS_TOP_REACH - tipX) / LOOPS_HIGH;
            leanVX = Math.signum(loopsX) * leanVX > 0.0F ? 0.0F : leanVX;
        }
        if (Math.abs(loopsZ) > LOOPS_TOP_REACH) {
            leanZ = (Math.signum(loopsZ) * LOOPS_TOP_REACH - tipZ) / LOOPS_HIGH;
            leanVZ = Math.signum(loopsZ) * leanVZ > 0.0F ? 0.0F : leanVZ;
        }
        float topX = tipX + leanX * LENGTH;
        float topZ = tipZ + leanZ * LENGTH;
        if (Math.abs(topX) > TOP_REACH) {
            leanX = (Math.signum(topX) * TOP_REACH - tipX) / LENGTH;
            leanVX = Math.signum(topX) * leanVX > 0.0F ? 0.0F : leanVX;
        }
        if (Math.abs(topZ) > TOP_REACH) {
            leanZ = (Math.signum(topZ) * TOP_REACH - tipZ) / LENGTH;
            leanVZ = Math.signum(topZ) * leanVZ > 0.0F ? 0.0F : leanVZ;
        }
    }

    private void knock(StationView view, float nx, float nz, float speed) {
        if (speed < KNOCK_FROM || time - knockedAt < KNOCK_GAP) {
            return;
        }
        knockedAt = time;
        mix.knock(view, tipX, tipZ, nx, nz, speed);
    }

    /**
     * The whisk where it is: its foot and lean in the hand blended with its rest by how far it is taken, put on the scene's pivot in the
     * bowl's own sides (the pivot stands at the rest, turned as the rest is).
     */
    private void pose(StationView view, float turn, Vector3f rest) {
        float k = KitchenMotions.smooth(held);
        Vector3f position = new Vector3f(REST);
        Quaternionf rotation = new Quaternionf(REST_TURN);
        if (k > 0.0F) {
            float lean = Math.min(0.95F, (float) Math.hypot(leanX, leanZ));
            Vector3f axis = new Vector3f(leanX, (float) Math.sqrt(1.0F - lean * lean), leanZ).normalize().rotateY(-turn);
            Quaternionf inHand = new Quaternionf().rotationTo(UP, axis)
                    .rotateY(ROLL * Mth.DEG_TO_RAD * Mth.sin(rolled / ROLL_WAY));
            Vector3f foot = new Vector3f(tipX, BowlMix.FLOOR + FOOT + FOOT_PER_LEAN * lean, tipZ).rotateY(-turn);
            position.lerp(foot, k);
            rotation.slerp(inHand, k);
        }
        Vector3f offset = position.sub(REST);
        Vector3f euler = new Quaternionf(REST_TURN).conjugate().mul(rotation).getEulerAnglesYXZ(new Vector3f());
        view.node("whisk_pivot").ifPresent(node -> {
            node.moveTo(offset.x, offset.y, offset.z, 0.0F);
            node.rotateTo(euler.x * Mth.RAD_TO_DEG, euler.y * Mth.RAD_TO_DEG, euler.z * Mth.RAD_TO_DEG, 0.0F);
        });
    }

    /** The loops' place goes to the server now and then, while they move, in the bowl's own sides. */
    private void send(StationView view, float dt, float turn, float x, float z) {
        sendClock -= dt;
        if (sendClock > 0.0F || !Float.isNaN(sentX) && Math.hypot(x - sentX, z - sentZ) < SEND_LEAST) {
            return;
        }
        sendClock = SEND_EVERY;
        sentX = x;
        sentZ = z;
        Vector3f own = new Vector3f(x, 0.0F, z).rotateY(-turn);
        CompoundTag where = new CompoundTag();
        where.putFloat(BowlStation.X, own.x);
        where.putFloat(BowlStation.Z, own.z);
        view.sendAction(BowlStation.ACTION_STIR, where);
    }

    private float badgeSwell() {
        float since = time - doneAt;
        if (since >= 0.0F && since < DONE_TIME) {
            return 1.0F + DONE_SWELL * Mth.sin(Mth.PI * since / DONE_TIME);
        }
        return 1.0F;
    }

    /**
     * What the badge to the right of the bowl shows: while it holds a bowlful, the whisk as long as nobody whisks (the bowlful waits for
     * the hand), what it becomes while it is whisked, with a bar for its stirring; once mixed, what waits, which a click takes. Ready:
     * what waits; still to come: what the bowlful makes. Null for nothing.
     */
    private StepBadge.Step step(StationView view, float stirred, int sets) {
        if (resultWaits) {
            return new StepBadge.Step(waiting, null, false, waiting.getCount(), 0, 1.0F, 0, false, waiting.getHoverName());
        }
        if (making.isEmpty()) {
            return null;
        }
        Dish recipe = BowlRecipes.reads(recipes, ingredientsOf(view));
        int more = recipe == null ? 0 : sets * recipe.result().getCount();
        boolean whisked = stroking || heldByClick || held > 0.5F;
        return new StepBadge.Step(making, whisked ? null : StepBadge.Glyph.WHISK, false, 0, more, stirred, 0, false, making.getHoverName());
    }

    static List<ItemStack> ingredientsOf(StationView view) {
        return IntStream.range(0, Bowl.INGREDIENTS).mapToObj(i -> view.state().getItem(SlotRef.of(BowlStation.INGREDIENTS, i))).toList();
    }
}
