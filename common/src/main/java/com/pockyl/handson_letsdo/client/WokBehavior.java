package com.pockyl.handson_letsdo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.MeshShape;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.handson_letsdo.station.WokAndBowlWok;
import com.pockyl.handson_letsdo.station.WokStation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the wok shows, to the cook and to everybody around: the wok as Wok &amp; Bowl draws it (its moves at a toss, while it calls
 * and while it simmers, read from the wok itself, so everybody sees the same), what lies in it sizzling and thrown up by a toss, the
 * dishes; for the cook the wok taken by a handle and tossed with a flick, the badge and the book.
 */
public final class WokBehavior implements StationBehavior {
    /** Wok &amp; Bowl's renderer's moves, in its units (ticks, blocks, degrees), turned into scene pixels where they place things. */
    private static final float TOSS_TICKS = 12.0F;
    private static final float DUMP_TICKS = 16.0F;
    private static final float DUMP_TILT = 70.0F;
    private static final float DUMP_LIFT = 0.25F * 16.0F;
    private static final float TOSS_HEIGHT = 0.3F * 16.0F;
    private static final float TOSS_TILT = 22.0F;
    private static final float TOSS_PIVOT = 0.1F * 16.0F;
    private static final float WOBBLE_ANGLE = 3.0F;
    private static final float WOBBLE_SPEED = 0.9F;
    private static final float SIMMER_ANGLE = 1.5F;
    private static final float SIMMER_SPEED = 0.15F;
    private static final float SIMMER_HOP = 0.006F * 16.0F;
    private static final float INGREDIENT_HEIGHT = 0.075F * 16.0F;
    private static final float INGREDIENT_RADIUS = 0.12F * 16.0F;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
    private static final float SIZZLE_SPEED = 0.45F;
    private static final float SIZZLE_HOP = 0.018F * 16.0F;
    private static final float SIZZLE_TILT = 8.0F;
    private static final float TOSS_STAGGER = 0.06F;
    private static final float TOSS_ITEM_HEIGHT = 0.28F * 16.0F;
    private static final float TOSS_ITEM_SPIN = 360.0F;
    private static final float DISH_HEIGHT = 0.08F * 16.0F;
    private static final float DISH_STEP = 0.035F * 16.0F;
    /** What lies in the wok and the dishes, built from their icons at Wok &amp; Bowl's sizes (0.28 and 0.36 of a block). */
    private static final float PIECE_SIZE = 0.28F * 16.0F;
    private static final float DISH_SIZE = 0.36F * 16.0F;
    /** A new piece drops in from this high over the wok, so long. */
    private static final float DROP_HEIGHT = 6.0F;
    private static final float DROP_TICKS = 5.0F;
    /** The sauce darkens and glosses what lies in the wok. */
    private static final int SAUCED = 0xFFD2A878;
    /**
     * The hand on the wok: how much of its move the wok follows (pixels per pixel, and at most), and the flick that tosses: the hand
     * rising this far within this long, then a pause before the next one.
     */
    private static final float FOLLOW = 0.3F;
    private static final float FOLLOW_UP = 2.0F;
    private static final float FOLLOW_SIDE = 1.0F;
    private static final float FLICK_RISE = 2.2F;
    private static final float FLICK_TIME = 0.14F;
    private static final float FLICK_PAUSE = 0.5F;
    /** A short press on a handle takes the wok in hand; a longer one holds it while pressed. */
    private static final float CLICK = 0.25F;
    /** The badge's flames over the icon: one small flame each, three at most. */
    private static final String[] FLAME = {"..#..", ".##..", ".###.", "#####", ".###."};
    private static final int FLAME_TOP = -15;
    private static final int FLAME_STEP = 6;

    private final WokBook pages = new WokBook();
    private final RecipeBook book = new RecipeBook(pages);
    private final StepBadge badge = new StepBadge(10, -16, false);
    private final UsableCache usable = new UsableCache();
    private final List<ItemStack> shownIngredients = new ArrayList<>();
    private final List<ItemStack> shownDishes = new ArrayList<>();
    private boolean shownSauced;
    private int workTurn = Integer.MIN_VALUE;
    private float clock;
    /** The cook's hand on the wok: stroking while a button holds it, held by a click until the next. */
    private boolean stroking;
    private boolean heldByClick;
    private float strokeStart;
    /** Whether the hand moved while the button was down (a drag or a flick, not a click that takes the wok in hand). */
    private boolean moved;
    private Vector3f grab;
    private Vector3f hand;
    private final List<float[]> rises = new ArrayList<>();
    private float lastFlick = -10.0F;
    /**
     * A toss of the cook's own flick, drawn at once (game tick and its part); the wok's own ({@code lastToss}) for everybody else.
     * The server's answer to the cook's own flick is the same toss: it does not start again.
     */
    private long localTossTick = Long.MIN_VALUE / 2;
    private float localTossPartial;
    private long seenToss = Long.MIN_VALUE;
    private long absorbedToss = Long.MIN_VALUE;
    private int flamesLit = 3;
    private int flamesOpen;
    private final StepBadge.Extras flames = new StepBadge.Extras() {
        @Override
        public String key() {
            return flamesOpen + "/" + flamesLit;
        }

        @Override
        public void track(MeshShape.Builder track) {
            for (int flame = 0; flame < flamesOpen; flame++) {
                glyph(track, flameLeft(flame), FLAME_TOP);
            }
        }

        @Override
        public void lit(MeshShape.Builder lit) {
            for (int flame = 0; flame < Math.min(flamesLit, flamesOpen); flame++) {
                glyph(lit, flameLeft(flame), FLAME_TOP);
            }
        }
    };

    public WokBehavior(StationView view) {
    }

    private static WokAndBowlWok wok(StationView view) {
        return WokAndBowlWok.of(view.level().getBlockEntity(view.pos()));
    }

    /** How far the scene is turned from the wok's own facing, degrees: the wok node turns back by it. */
    private void placeWok(StationView view) {
        BlockState state = view.level().getBlockState(view.pos());
        int turn = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? yaw(view.facing()) - yaw(state.getValue(BlockStateProperties.HORIZONTAL_FACING)) : 0;
        workTurn = Math.floorMod(turn, 360);
    }

    private static int yaw(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    @Override
    public void onSceneReady(StationView view) {
        workTurn = Integer.MIN_VALUE;
        shownIngredients.clear();
        shownDishes.clear();
        badge.ready(view);
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        WokAndBowlWok wok = wok(view);
        if (wok != null) {
            seenToss = wok.lastToss();
        }
    }

    @Override
    public void onOpen(StationView view) {
        book.opened(view);
        usable.clear();
        view.setInventoryFilter(stack -> usable.test(stack, it -> usable(view, it)));
        LocalPlayer player = Minecraft.getInstance().player;
        WokAndBowlWok wok = wok(view);
        if (player != null && wok != null && view.openedByUse() && player.inventoryMenu.getCarried().isEmpty()
                && wok.takes(view.level(), player.getMainHandItem())) {
            CompoundTag how = new CompoundTag();
            how.putInt(WokStation.FROM, player.getInventory().selected);
            how.putBoolean(WokStation.BY_USE, true);
            view.sendAction(WokStation.ACTION_PLACE, how);
        }
    }

    @Override
    public void onClose(StationView view) {
        book.shut(view);
        stroking = false;
        heldByClick = false;
    }

    /** What goes into the wok at all: anything of its recipes, the sauce, flint and steel; any food while it cooks. */
    private boolean usable(StationView view, ItemStack stack) {
        if (WokAndBowlWok.isSauce(stack) || WokAndBowlWok.isFlint(stack)) {
            return true;
        }
        WokAndBowlWok wok = wok(view);
        if (wok != null && wok.takes(view.level(), stack)) {
            return true;
        }
        return WokAndBowlWok.recipes(view.level()).stream()
                .anyMatch(recipe -> WokAndBowlWok.ingredients(recipe.value()).stream().anyMatch(ingredient -> ingredient.test(stack)));
    }

    private static float partial() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    @Override
    public void frame(StationView view, float dt) {
        clock += dt;
        if (workTurn == Integer.MIN_VALUE) {
            placeWok(view);
        }
        WokAndBowlWok wok = wok(view);
        if (wok == null) {
            return;
        }
        long now = view.level().getGameTime();
        float partial = partial();
        // Periodic moves on a clock that wraps, as Wok & Bowl's renderer has it; the rest in ticks since what started them.
        float time = now % 72000L + partial;
        float sinceLocal = now - localTossTick + partial - localTossPartial;
        if (wok.lastToss() != seenToss) {
            seenToss = wok.lastToss();
            if (sinceLocal < TOSS_TICKS + 4.0F) {
                // The server's answer to the cook's own flick, already being drawn.
                absorbedToss = seenToss;
            }
        }
        float sinceServer = wok.lastToss() == absorbedToss ? Float.MAX_VALUE : now - wok.lastToss() + partial;
        float sinceToss = Math.min(sinceLocal, sinceServer);
        moveWok(view, wok, time, sinceToss, now - wok.lastDump() + partial);
        drawContents(view, wok, time, sinceToss, now - wok.lastAdded() + partial);
        showBadge(view, wok, now - wok.tossCalled() + partial);
        book.frame(view, dt);
    }

    /** The wok as Wok &amp; Bowl's renderer moves it, and as the cook's hand holds it. */
    private void moveWok(StationView view, WokAndBowlWok wok, float time, float sinceToss, float sinceDump) {
        NodeView node = view.node("wok").orElse(null);
        if (node == null) {
            return;
        }
        float x = 0.0F;
        float y = 0.0F;
        float z = 0.0F;
        float tiltX = 0.0F;
        float tiltZ = 0.0F;
        if (sinceDump >= 0.0F && sinceDump < DUMP_TICKS) {
            float tilt = Mth.sin(Mth.PI * sinceDump / DUMP_TICKS);
            y += tilt * DUMP_LIFT;
            tiltZ += tilt * DUMP_TILT;
        } else if (sinceToss >= 0.0F && sinceToss < TOSS_TICKS) {
            float progress = sinceToss / TOSS_TICKS;
            float tilt = Mth.sin(2.0F * Mth.PI * progress) * TOSS_TILT;
            y += Mth.sin(Mth.PI * progress) * TOSS_HEIGHT;
            // About a point TOSS_PIVOT over the wok's foot, as its renderer tilts it.
            y += TOSS_PIVOT * (1.0F - Mth.cos(tilt * Mth.DEG_TO_RAD));
            z += TOSS_PIVOT * Mth.sin(tilt * Mth.DEG_TO_RAD);
            tiltX += tilt;
        } else if (wok.isAwaitingToss()) {
            tiltZ += Mth.sin(time * WOBBLE_SPEED) * WOBBLE_ANGLE;
        } else if (wok.isCooking()) {
            y += Math.max(0.0F, Mth.sin(time * SIMMER_SPEED * 2.0F)) * SIMMER_HOP;
            tiltX += Mth.sin(time * SIMMER_SPEED) * SIMMER_ANGLE;
        }
        if ((stroking || heldByClick) && grab != null && hand != null && view.isFocused()) {
            // The hand on a handle: the wok follows a little, up and to the side, leaning towards the hand.
            float up = Mth.clamp((hand.y - grab.y) * FOLLOW, -0.3F, FOLLOW_UP);
            float side = Mth.clamp((hand.x - grab.x) * FOLLOW, -FOLLOW_SIDE, FOLLOW_SIDE);
            y += Math.max(0.0F, up);
            x += side;
            tiltZ += side * 4.0F;
            tiltX -= up * 3.0F;
        }
        node.moveTo(x, y, z, 0.0F);
        node.rotateTo(tiltX, workTurn, tiltZ, 0.0F);
    }

    /** What lies in the wok, as Wok &amp; Bowl's renderer lays it: sizzling, thrown up by a toss; the dishes when it is done. */
    private void drawContents(StationView view, WokAndBowlWok wok, float time, float sinceToss, float sinceAdded) {
        List<ItemStack> ingredients = wok.ingredients();
        boolean sauced = wok.isSauced();
        boolean changed = !sameItems(ingredients, shownIngredients) || sauced != shownSauced;
        if (changed) {
            shownIngredients.clear();
            ingredients.forEach(stack -> shownIngredients.add(stack.copy()));
            shownSauced = sauced;
        }
        float tossProgress = sinceToss >= 0.0F && sinceToss < TOSS_TICKS ? sinceToss / TOSS_TICKS : -1.0F;
        int count = ingredients.size();
        for (int i = 0; i < WokAndBowlWok.MOST_INGREDIENTS; i++) {
            int index = i;
            view.node("piece_" + i).ifPresent(node -> {
                if (index >= count) {
                    node.setVisible(false);
                    return;
                }
                ItemStack stack = ingredients.get(index);
                if (changed) {
                    dress(node, stack, PIECE_SIZE);
                    node.setTint(sauced ? SAUCED : 0xFFFFFFFF);
                }
                node.setVisible(true);
                double angle = index * GOLDEN_ANGLE;
                double radius = count == 1 ? 0.0 : INGREDIENT_RADIUS * Math.sqrt((index + 0.5) / count);
                float x = (float) (Math.cos(angle) * radius);
                float z = (float) (Math.sin(angle) * radius);
                float phase = index * 1.7F;
                float lift = Math.max(0.0F, Mth.sin(time * SIZZLE_SPEED + phase)) * SIZZLE_HOP;
                float tilt = Mth.sin(time * SIZZLE_SPEED * 0.5F + phase) * SIZZLE_TILT;
                float spin = 0.0F;
                if (tossProgress >= 0.0F) {
                    float delayed = Mth.clamp((tossProgress - index * TOSS_STAGGER) / Math.max(0.4F, 1.0F - TOSS_STAGGER * (count - 1)), 0.0F, 1.0F);
                    lift += Mth.sin(Mth.PI * delayed) * TOSS_ITEM_HEIGHT;
                    spin = delayed * TOSS_ITEM_SPIN * (index % 2 == 0 ? 1 : -1);
                }
                if (index == count - 1 && sinceAdded >= 0.0F && sinceAdded < DROP_TICKS) {
                    // The newest one comes down into the wok.
                    float left = 1.0F - sinceAdded / DROP_TICKS;
                    lift += left * left * DROP_HEIGHT;
                }
                node.moveTo(x, INGREDIENT_HEIGHT + index * 0.064F + lift, z, 0.0F);
                node.rotateTo(tilt + spin, index * 67.0F, 0.0F, 0.0F);
            });
        }
        List<ItemStack> dishes = new ArrayList<>();
        if (count == 0) {
            wok.dishes().stream().filter(stack -> !stack.isEmpty()).forEach(dishes::add);
        }
        boolean dishesChanged = !sameItems(dishes, shownDishes);
        if (dishesChanged) {
            shownDishes.clear();
            dishes.forEach(stack -> shownDishes.add(stack.copy()));
        }
        for (int i = 0; i < WokAndBowlWok.DISHES; i++) {
            int layer = i;
            view.node("dish_" + i).ifPresent(node -> {
                if (layer >= dishes.size()) {
                    node.setVisible(false);
                    return;
                }
                if (dishesChanged) {
                    dress(node, dishes.get(layer), DISH_SIZE);
                }
                node.setVisible(true);
                node.moveTo(0.0F, DISH_HEIGHT + layer * DISH_STEP, 0.0F, 0.0F);
                node.rotateTo(0.0F, layer * 25.0F, 0.0F, 0.0F);
            });
        }
    }

    private static boolean sameItems(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** A thing lying in the wok, built flat from its icon; a block-like one drawn as an item, small. */
    private static void dress(NodeView node, ItemStack stack, float size) {
        Optional<ItemVoxels.Icon> icon = ItemVoxels.icon(stack);
        if (icon.isPresent()) {
            node.setVisual(null);
            node.setShape(ItemVoxels.build(icon.get(), size, List.of(), 0.0F), icon.get().sprite());
            node.scaleTo(1.0F, 0.0F);
        } else {
            node.setShape(null, null);
            node.setVisual(Visual.ofItem(stack.getItem()).withAnchor(new Vector3f(8.0F, 8.0F, 8.0F)));
            node.scaleTo(size / 16.0F, 0.0F);
        }
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Stroke stroke && stroke.node().startsWith("handle")) {
            handStroke(view, stroke);
            return;
        }
        if (event instanceof SceneEvent.Click click && "book_open".equals(click.node())) {
            book.click(view, click.button());
            return;
        }
        if (event instanceof SceneEvent.Click click && "pan".equals(click.node())) {
            // An empty hand on the wok does what Wok & Bowl's empty hand does: toss or serve while it cooks, else a dish out.
            WokAndBowlWok wok = wok(view);
            if (wok == null) {
                return;
            }
            if (wok.isCooking()) {
                toss(view, wok);
            } else if (wok.hasDishes()) {
                view.sendAction(WokStation.ACTION_TAKE, new CompoundTag());
            }
        }
    }

    /** The hand on a handle: a click takes the wok in hand until the next click; while held, a quick flick up tosses. */
    private void handStroke(StationView view, SceneEvent.Stroke stroke) {
        Vector3fc point = stroke.point();
        switch (stroke.phase()) {
            case START -> {
                stroking = true;
                if (!heldByClick) {
                    strokeStart = clock;
                    grab = new Vector3f(point);
                    rises.clear();
                    moved = false;
                }
                hand = new Vector3f(point);
                rises.add(new float[] {clock, point.y()});
            }
            case MOVE -> {
                hand = new Vector3f(point);
                if (grab != null && hand.distance(grab) > 1.0F) {
                    moved = true;
                }
                rises.add(new float[] {clock, point.y()});
                rises.removeIf(sample -> clock - sample[0] > FLICK_TIME);
                if (!rises.isEmpty() && point.y() - rises.get(0)[1] > FLICK_RISE && clock - lastFlick > FLICK_PAUSE) {
                    lastFlick = clock;
                    rises.clear();
                    WokAndBowlWok wok = wok(view);
                    if (wok != null) {
                        toss(view, wok);
                    }
                }
            }
            case END -> {
                stroking = false;
                if (heldByClick) {
                    heldByClick = false;
                    grab = null;
                } else if (clock - strokeStart < CLICK && !moved) {
                    heldByClick = true;
                    view.holdStroke();
                } else {
                    grab = null;
                }
            }
            default -> {
            }
        }
    }

    /**
     * The toss of the cook's hand, drawn at once: the wok jerks and what lies in it flies. The wok is told when it cooks; it makes
     * its own toss if it called for one, serves if it is ready, and sounds them itself. A toss it did not ask for only clatters.
     */
    private void toss(StationView view, WokAndBowlWok wok) {
        localTossTick = view.level().getGameTime();
        localTossPartial = partial();
        if (wok.isCooking()) {
            view.sendAction(WokStation.ACTION_TOSS, new CompoundTag());
        }
        boolean answered = wok.isAwaitingToss() || wok.isSauced() && wok.timer() >= wok.readyTicks();
        if (!answered) {
            view.playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), "wok", 0.35F, 1.4F);
        }
        Vector3f middle = new Vector3f(8.0F, 3.0F, 8.0F);
        view.spawnParticles(DiegesisParticles.DUST, middle, new Vector3f(0.0F, 3.0F, 0.0F), 1.5F, 4, 0xFFE0C090);
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

    /** A stack let go on the wok: one of it goes in, as the wok takes it (food, the sauce, flint and steel for the flambé). */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ("book_open".equals(node)) {
            book.click(view, button);
            return true;
        }
        view.sendAction(WokStation.ACTION_PLACE, new CompoundTag());
        return true;
    }

    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        if (stack.isEmpty() || slot >= Inventory.INVENTORY_SIZE || !usable(view, stack)) {
            return false;
        }
        CompoundTag how = new CompoundTag();
        how.putInt(WokStation.FROM, slot);
        view.sendAction(WokStation.ACTION_PLACE, how);
        return true;
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        ItemStack stack = ItemStack.parseOptional(view.level().registryAccess(), data.getCompound(WokStation.STACK));
        if (WokStation.EFFECT_ADDED.equals(effect)) {
            Vector3f rim = new Vector3f(8.0F, 4.5F, 8.0F);
            if (WokAndBowlWok.isFlint(stack)) {
                view.playSound(SoundEvents.FLINTANDSTEEL_USE, "wok", 0.8F, 1.0F);
                view.spawnParticles(DiegesisParticles.GLINT, rim, new Vector3f(0.0F, 2.0F, 0.0F), 1.2F, 6, 0xFFFFD27A);
            } else if (WokAndBowlWok.isSauce(stack)) {
                view.spawnParticles(DiegesisParticles.DUST, rim, new Vector3f(0.0F, -2.0F, 0.0F), 1.0F, 6, 0xFF5A3418);
            }
        } else if (WokStation.EFFECT_REFUSED.equals(effect)) {
            view.playSound(SoundEvents.WOOD_HIT, "wok", 0.35F, 1.5F);
        }
    }

    /** The badge: what the wok needs now (heat, a toss, the sauce) or what it makes and how many, with the flames it has so far. */
    private void showBadge(StationView view, WokAndBowlWok wok, float sinceCalled) {
        StepBadge.Step step = step(view, wok, sinceCalled);
        flamesOpen = wok.isCooking() && WokAndBowlWok.hasQuality() ? 3 : 0;
        flamesLit = flamesOpen > 0 ? wok.flames(view.level().registryAccess()) : 0;
        badge.show(view, view.isInUse() && RecipeBook.isWorkedAt(view) && step != null, step, flamesOpen > 0 ? flames : StepBadge.Extras.NONE,
                clock);
    }

    private StepBadge.Step step(StationView view, WokAndBowlWok wok, float sinceCalled) {
        if (!wok.isCooking()) {
            List<ItemStack> dishes = wok.dishes().stream().filter(stack -> !stack.isEmpty()).toList();
            if (!dishes.isEmpty()) {
                int count = dishes.stream().mapToInt(ItemStack::getCount).sum();
                return new StepBadge.Step(dishes.get(0), null, false, count, 0, Float.NaN, 0, false, dishes.get(0).getHoverName());
            }
            if (!wok.isHeated()) {
                return new StepBadge.Step(ItemStack.EMPTY, StepBadge.Glyph.FLAME, true, 0, 0, Float.NaN, 0, false, null);
            }
            return null;
        }
        if (!wok.isHeated()) {
            return new StepBadge.Step(ItemStack.EMPTY, StepBadge.Glyph.FLAME, true, 0, 0, Float.NaN, 0, false, null);
        }
        ItemStack makes = wok.makes();
        int count = wok.dishCount(wok.flames(view.level().registryAccess()));
        if (wok.isAwaitingToss()) {
            float left = 1.0F - Mth.clamp(sinceCalled / WokAndBowlWok.tossWindow(), 0.0F, 1.0F);
            return new StepBadge.Step(makes, StepBadge.Glyph.WOK, true, 0, count, left, 0, false, makes.getHoverName());
        }
        int ready = wok.readyTicks();
        if (wok.timer() < ready) {
            float progress = ready <= 0 ? 1.0F : Mth.clamp((float) wok.timer() / ready, 0.0F, 1.0F);
            int seconds = Math.max(1, (ready - wok.timer() + 19) / 20);
            return new StepBadge.Step(makes, null, false, 0, count, progress, seconds, false, makes.getHoverName());
        }
        if (!wok.isSauced()) {
            ItemStack sauce = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath(WokAndBowlWok.MOD_ID, "fermented_sauce")));
            return new StepBadge.Step(sauce, null, StepBadge.lacks(WokAndBowlWok::isSauce), 0, count, 1.0F, 0, false, sauce.getHoverName());
        }
        return new StepBadge.Step(makes, StepBadge.Glyph.WOK, false, count, 0, 1.0F, 0, false, makes.getHoverName());
    }

    private static float flameLeft(int flame) {
        return -(3 * FLAME_STEP - 1) / 2.0F + flame * FLAME_STEP;
    }

    private static void glyph(MeshShape.Builder shape, float left, float top) {
        for (int row = 0; row < FLAME.length; row++) {
            for (int column = 0; column < FLAME[row].length(); column++) {
                if (FLAME[row].charAt(column) == '#') {
                    Badge.pixels(shape, left + column, top + row, 1, 1);
                }
            }
        }
    }
}
