package com.pockyl.handson_letsdo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.state.TankContents;
import com.pockyl.handson_letsdo.station.BarrelStation;
import com.pockyl.handson_letsdo.station.Press;
import com.pockyl.handson_letsdo.station.PressBasket;
import com.pockyl.handson_letsdo.station.PressStation;
import com.pockyl.handson_letsdo.station.VineryPressing;

import java.util.ArrayList;
import java.util.List;

/**
 * What the apple press shows while somebody works at it: the whole press, both of its blocks, the basket's front swung open in
 * two halves, the fruit standing in it, the screw turning the plate down on the fruit as the cook presses its cap (a click on it)
 * and letting go when the load is mash, the mash lying before the fruit, juice raining off the plate's lip into the tub under it, where bottles are dipped to be
 * filled ({@link TubBottles}). The badge shows what comes out in the end with a bar for each step, or how many bottles the tub
 * still fills. While nobody does, the press is Vinery's two blocks themselves with the tub standing under them, its juice as it
 * is: the station hides the blocks while it is used, while it presses and bottles, and until the basket is shut again behind the
 * last one to leave.
 */
public final class PressBehavior implements StationBehavior {
    /** The press's own parts, drawn while the scene stands in for its blocks; the tub and its juice are ours and always there. */
    private static final String[] SHOWN_IN_USE = {"lower", "upper", "plate", "screw", "door_a", "door_b"};
    /**
     * The basket's front swings open in two halves about its corners when the cook clicks it, and shut with another click (the
     * press presses only while it is shut): seconds each way, and how far, degrees.
     */
    private static final float DOOR_MOVE = 0.35F;
    private static final float DOOR_OPEN = 100.0F;
    /**
     * The screw: how far the plate comes down on the fruit while one is pressed (its cap then sits on the beam), how many turns
     * it makes on the way, and how fast it follows, per second: quickly back up when the fruit is mash.
     */
    private static final float PRESS_DEPTH = 3.0F;
    private static final float PRESS_TURNS = 3.0F;
    private static final float PRESS_FOLLOW = 7.0F;
    /**
     * Where the juice leaves the plate's lip, over the tub, scene pixels: under the front edge of Vinery's plate (z 2, its underside
     * at y 7), which stands over the tub's juice.
     */
    private static final Vector3f LIP = new Vector3f(8.0F, 6.95F, 2.2F);
    /** How far to each side of the middle and how deep under the plate's edge the drops leave it: all of them over the juice. */
    private static final float LIP_HALF = 2.5F;
    private static final float LIP_DEPTH = 0.3F;
    /**
     * The tub (apple_press.json, node tub_juice): the middle of its inside at the rim, its floor and how high a full tub's juice
     * stands, scene pixels; the tub node's place, from which its juice's pixels count.
     */
    private static final Vector3f TUB_MIDDLE = new Vector3f(8.0F, 4.0F, 3.0F);
    private static final float TUB_FLOOR = 1.0F;
    private static final float TUB_FULL = 3.6F;
    private static final Vector3f TUB_NODE = new Vector3f(8.0F, 0.0F, 8.0F);
    /**
     * While a bottle's worth runs into the tub, the juice rains off the plate's lip into it, fine drops straight down, steadily (a
     * second: some ten of them in the air at once, so that it reads as rain), and the tub fills as it runs. While a fruit is pressed hard, juice beads at the basket's foot between its staves (a
     * second, at the hardest) and the first drops fall off the lip.
     */
    private static final float RAIN = 45.0F;
    private static final float PRESS_DRIPS = 2.5F;
    private static final float BEADS = 4.0F;
    /** A drop rings the juice where it lands, this strongly; one now and then is heard (seconds between, the least and the most). */
    private static final float DROP_RING = 0.05F;
    private static final float DRIP_HEARD_SOONEST = 0.16F;
    private static final float DRIP_HEARD_LATEST = 0.42F;
    /**
     * The screw creaks every quarter turn it is driven down, the fruit squeezes under the plate now and then (seconds between,
     * at the least and the most) once it is pressed hard.
     */
    private static final float CREAK_TURN = 120.0F;
    /** A load pressed by hand: the plate stays down on the mash this long before it lets go, seconds. */
    private static final float PRESSED_HOLD = 0.3F;
    /**
     * A press of the cap is hard work: the hand bears down on the cap (the cap gives a little first), then drives the screw down slowly
     * into the fruit, deeper than it stays, trembling, and the fruit pushes the plate back up part of the way. Seconds for each part;
     * how much deeper than its step the plate goes before it springs back. Held down, the cap is pressed again as soon as a press is
     * done; clicks while one is under way wait for it and come one after another.
     */
    private static final float PUSH_BEAR = 0.1F;
    private static final float PUSH_DRIVE = 0.24F;
    private static final float PUSH_BACK = 0.22F;
    private static final float PUSH_OVER = 0.7F;
    private static final float CAP_GIVES = 0.35F;
    /** The camera dips with the cook leaning on the cap, pixels. */
    private static final float LEAN_IN = 0.5F;
    private static final float SQUEEZE_SHORTEST = 2.0F;
    private static final float SQUEEZE_LONGEST = 3.4F;
    private static final float DONE_SWELL = 0.35F;
    private static final float DONE_TIME = 0.6F;
    private static final float READY_BREATH = 0.04F;

    private final RecipeBook book = new RecipeBook(new PressBook());
    private final StepBadge badge = new StepBadge(10, -10, false);
    private final TubBottles tub = new TubBottles();
    private final UsableCache usable = new UsableCache();
    private List<VineryPressing.Step> mashing = List.of();
    private List<VineryPressing.Step> bottling = List.of();
    private float time;
    /** How far the basket's front is open, 0 shut to 1. */
    private float doors;
    /** The front as the cook last clicked it, shown at once while the press answers; null once it agrees. */
    private Boolean doorsAsked;
    private float doorsAskedAt;
    /** How far the plate is down on the fruit, pixels. */
    private float pressed;
    private float drips;
    private float beads;
    /** The screw's turn last frame and how far it has been driven down since its last creak, degrees. */
    private float lastTurn;
    private float creakTurn;
    /** The plate let go of the fruit and is on its way up: it made its sound. */
    private boolean released;
    private float squeezeIn;
    /** The drops off the lip on their way into the tub: when each lands, and where. */
    private final List<Drop> falling = new ArrayList<>();
    /** When a drop landing is heard next at the soonest. */
    private float dripHeardAt;
    /** The drink that runs into the tub, as the tub's juice shows it while it fills; null while it follows the tub. */
    private ResourceLocation filling;
    private float doneAt = -10.0F;
    /** When the last load was pressed by hand: the plate stays down a moment. */
    private float pressedAt = -10.0F;
    /** A press under way: how far into it, NaN for none; whether it is the local cook's, and whether it was told to the server. */
    private float pushAge = Float.NaN;
    private boolean pushOwn;
    private boolean pushSent;
    /** Presses clicked while one is under way, done one after another, a load's at most. */
    private int pushQueued;
    private boolean holdingCap;
    private float pushFrom;
    private float pushEndedAt = -10.0F;
    /** How many presses of the load the press had last, to show another cook's presses as they come. */
    private int seenPresses;
    private boolean leaning;
    /** What comes out in the end, or what stands bottled. */
    private ItemStack dish = ItemStack.EMPTY;
    /** Bottles that stand done, while the press makes no more. */
    private int servings;
    private int juiceColor = 0xFFD9A441;
    /** What the tub holds, a bottle's worth a portion, and the colour of its juice. */
    private ItemStack tubDrink = ItemStack.EMPTY;
    private int tubColor = 0xFFD9A441;

    public PressBehavior(StationView view) {
    }

    @Override
    public void onSceneReady(StationView view) {
        filling = null;
        falling.clear();
        tub.ready(view);
        read(view);
        badge.ready(view);
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        refresh(view);
    }

    /** The press's recipes, and what goes into its basket shown see-through while it is empty. */
    private void read(StationView view) {
        mashing = VineryPressing.mashing(view.level());
        bottling = VineryPressing.bottling(view.level());
        List<ItemStack> fruit = new ArrayList<>();
        for (VineryPressing.Step step : mashing) {
            for (ItemStack stack : step.input().getItems()) {
                fruit.add(stack.copyWithCount(1));
            }
        }
        view.node("fruit").ifPresent(node -> node.setGhostItems(fruit));
    }

    @Override
    public void onOpen(StationView view) {
        read(view);
        usable.clear();
        view.setInventoryFilter(stack -> usable.test(stack, this::usable));
        book.opened(view);
        refresh(view);
        // What the cook came with is meant for the press: it comes to the hand.
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && view.openedByUse() && player.inventoryMenu.getCarried().isEmpty()) {
            ItemStack held = player.getMainHandItem();
            if (!held.isEmpty() && usable(held)) {
                view.takeFromInventory(player.getInventory().selected);
            }
        }
    }

    @Override
    public void onClose(StationView view) {
        book.shut(view);
        tub.clear(view);
        refresh(view);
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        refresh(view);
    }

    /**
     * The scene is the press in the world wherever it is drawn, for everybody near, with nobody at it as well: its plate and screw are
     * ours, and the press must look the same before, at and after the station; only what moves changes. Only where the server told
     * the press the station's part (a server without the mod leaves Vinery's press), and only where the scene is drawn: further off
     * the blocks show, else the press would be gone there.
     */
    @Override
    public boolean holdsBlock(StationView view) {
        return DiegesisClient.isSceneDrawn(view.pos()) && view.level().getBlockEntity(view.pos()) instanceof PressBasket basket
                && basket.handson$told();
    }

    /** Whether the basket's front is open: as the cook clicked it, until the press says so too. */
    private boolean isOpen(StationView view) {
        boolean open = view.state().data().getBoolean(PressStation.OPEN);
        if (doorsAsked != null && (doorsAsked == open || time - doorsAskedAt > 1.0F)) {
            doorsAsked = null;
        }
        return doorsAsked != null ? doorsAsked : open;
    }

    /** A click on either half opens the basket's front or shuts it. */
    private void toggleDoors(StationView view) {
        boolean open = !isOpen(view);
        doorsAsked = open;
        doorsAskedAt = time;
        CompoundTag data = new CompoundTag();
        data.putBoolean(PressStation.OPEN, open);
        view.sendAction(PressStation.ACTION_DOORS, data);
    }

    private static boolean isDoor(String node) {
        return "door_a".equals(node) || "door_b".equals(node);
    }

    /**
     * The cook presses the screw's cap: a press starts, or waits for the one under way. It is told to the server as the plate starts to
     * go down; the basket's front, if it stands open, shuts first.
     */
    private void pressCap(StationView view) {
        if (!Float.isNaN(pushAge)) {
            pushQueued = Math.min(pushQueued + 1, Press.PRESSES_PER_LOAD);
            return;
        }
        startPush(true);
    }

    private void startPush(boolean own) {
        pushAge = 0.0F;
        pushOwn = own;
        pushSent = false;
        pushFrom = pressed;
    }

    /** The press as the cook bears on it: tells the server once the plate starts down. */
    private void sendPush(StationView view) {
        if (isOpen(view)) {
            doorsAsked = false;
            doorsAskedAt = time;
        }
        view.sendAction(PressStation.ACTION_PRESS, new CompoundTag());
    }

    /** What the inventory at the press shows: what it presses, what it bottles, and empty wine bottles to fill at the tub. */
    private boolean usable(ItemStack stack) {
        return BarrelStation.isWineBottle(stack) || VineryPressing.taking(mashing, stack) != null || VineryPressing.taking(bottling, stack) != null;
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && click.node().equals("book_open")) {
            book.click(view, click.button());
        } else if (event instanceof SceneEvent.Click click && isDoor(click.node()) && click.button() == 0) {
            toggleDoors(view);
        } else if (event instanceof SceneEvent.Stroke stroke && "screw".equals(stroke.node()) && stroke.button() == 0) {
            // Pressed down on the cap: a press, and more of them as long as it is held down.
            if (stroke.phase() == SceneEvent.Stroke.Phase.START) {
                holdingCap = true;
                pressCap(view);
            } else if (stroke.phase() == SceneEvent.Stroke.Phase.END) {
                holdingCap = false;
            }
        } else if (event instanceof SceneEvent.Click click && "screw".equals(click.node()) && click.button() == 0) {
            pressCap(view);
        } else if (event instanceof SceneEvent.Click click && "tub".equals(click.node()) && click.button() <= 1) {
            // An empty hand fills the inventory's bottles: all the tub holds, or one with the right button.
            LocalPlayer player = Minecraft.getInstance().player;
            if (!tubDrink.isEmpty() && player != null && hasEmptyBottle(player)) {
                fillAtTub(view, false, 1, click.button() == 0);
            }
        }
    }

    private static boolean hasEmptyBottle(LocalPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (BarrelStation.isWineBottle(player.getInventory().getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The cook fills bottles at the tub: the bottle sets off at once, the press answers. A left click ({@code all}) fills as many
     * as the tub holds and there are bottles, so a whole pressing is taken at once; a right click one, Shift always all.
     */
    private void fillAtTub(StationView view, boolean fromHand, int held, boolean all) {
        all |= Screen.hasShiftDown();
        tub.fill(view, tubDrink.getItem(), tubColor, fromHand, held, all);
        CompoundTag data = new CompoundTag();
        data.putBoolean(PressStation.ALL, all);
        view.sendAction(PressStation.ACTION_DRAW, data);
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

    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ("book_open".equals(node)) {
            book.click(view, button);
            return true;
        }
        if (isDoor(node)) {
            toggleDoors(view);
            return true;
        }
        if ("screw".equals(node)) {
            pressCap(view);
            return true;
        }
        if ("tub".equals(node) && button <= 1 && BarrelStation.isWineBottle(stack)) {
            if (!tubDrink.isEmpty()) {
                fillAtTub(view, true, stack.getCount(), button == 0);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        if (PressStation.EFFECT_NOTHING.equals(effect)) {
            // Nothing under the plate to press: the cap gives a dull knock and stays.
            view.node("screw").ifPresent(cap -> cap.shake(0.25F, 0.2F));
            view.playSound(SoundEvents.WOOD_HIT, "screw", 0.3F, 1.2F);
        } else if (PressStation.EFFECT_PRESSED.equals(effect)) {
            // A fruit is mash: it gives under the plate with Vinery's own squeeze, and juice spurts at the basket's foot.
            pressedAt = time;
            othersPress(view);
            view.playSound(PressSounds.SQUEEZE, "fruit", 0.55F, 0.85F);
            view.playSound(SoundEvents.WOOD_PLACE, "screw", 0.2F, 0.8F);
            view.spawnParticles(DiegesisParticles.SPLASH, "mash", 6, juiceColor);
        } else if (PressStation.EFFECT_BOTTLED.equals(effect)) {
            // A bottle's worth more in the tub (or a bottle a hopper laid in is full): the badge swells with a chime.
            Item drink = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(data.getString(PressStation.VESSEL)));
            if (drink != null && drink != Items.AIR) {
                juiceColor = 0xFF000000 | FoodColors.main(new ItemStack(drink));
            }
            doneAt = time;
            view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.3F, 1.26F);
            if (PressStation.TUB.equals(node)) {
                // The bottle's worth that ran in while the tub filled is in: the last of it rings the juice.
                view.node("tub_juice").ifPresent(juice -> juice.ripple(LIP.x - TUB_NODE.x, LIP.z - TUB_NODE.z, 0.4F));
            } else {
                view.playSound(SoundEvents.BOTTLE_FILL, "served", 0.6F, 1.0F);
                view.spawnParticles(DiegesisParticles.GLINT, "served", 6, 0xFFFFE6A0);
            }
        } else if (PressStation.EFFECT_DRAWN.equals(effect)) {
            Item drink = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(data.getString(PressStation.VESSEL)));
            if (drink != null && drink != Items.AIR) {
                tub.filled(drink, 0xFF000000 | FoodColors.main(new ItemStack(drink)), data.getInt(PressStation.COUNT));
            }
        }
    }

    /** How far the step whose start is told under {@code started} is, 0 to 1; 0 while it does not run. */
    private static float progress(StationView view, String started, String total) {
        CompoundTag data = view.state().data();
        int ticks = data.getInt(total);
        if (!data.contains(started) || ticks <= 0) {
            return 0.0F;
        }
        return Mth.clamp((float) (view.level().getGameTime() - data.getLong(started)) / ticks, 0.001F, 1.0F);
    }

    /** Ticks until that step is done; 0 while it does not run. */
    private static int ticksLeft(StationView view, String started, String total) {
        CompoundTag data = view.state().data();
        if (!data.contains(started)) {
            return 0;
        }
        return (int) Math.max(1L, data.getLong(started) + data.getInt(total) - view.level().getGameTime());
    }

    private void refresh(StationView view) {
        StationState state = view.state();
        Item making = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(state.data().getString(PressStation.MAKING)));
        ItemStack served = state.getItem(PressStation.SERVED_SLOT);
        TankContents inTub = state.getTank(PressStation.TUB);
        Item tubbed = inTub.isEmpty() ? null : BuiltInRegistries.ITEM.get(inTub.main());
        tubDrink = tubbed == null || tubbed == Items.AIR ? ItemStack.EMPTY : new ItemStack(tubbed, inTub.amount());
        if (!tubDrink.isEmpty()) {
            tubColor = 0xFF000000 | FoodColors.main(tubDrink);
        }
        boolean makes = making != null && making != Items.AIR;
        dish = makes ? new ItemStack(making) : !tubDrink.isEmpty() ? tubDrink : served;
        servings = makes ? 0 : tubDrink.getCount() + served.getCount();
        if (!dish.isEmpty()) {
            juiceColor = 0xFF000000 | FoodColors.main(dish);
        }
    }

    /** Another cook's press, as the press tells it: it is shown the same way, unless the local cook's own press is under way. */
    private void othersPress(StationView view) {
        if (Float.isNaN(pushAge) && time - pushEndedAt > 0.4F) {
            startPush(false);
        }
    }

    @Override
    public void frame(StationView view, float dt) {
        time += dt;
        boolean inUse = view.isInUse();
        int presses = view.state().data().getInt(PressStation.PRESSES);
        if (presses > seenPresses) {
            othersPress(view);
        }
        seenPresses = presses;
        if (holdingCap && Float.isNaN(pushAge) && view.isFocused()) {
            pressCap(view);
        }
        // By hand the plate goes down a step with every press of the cap, and stays down a moment on a load pressed; left alone, it
        // follows the press's own pressing.
        float byHand = view.state().data().getInt(PressStation.PRESSES) / (float) Press.PRESSES_PER_LOAD;
        float mashed = time - pressedAt < PRESSED_HOLD ? 1.0F : Math.max(byHand, progress(view, PressStation.MASH_STARTED, PressStation.MASH_TOTAL));
        float bottled = progress(view, PressStation.BOTTLE_STARTED, PressStation.BOTTLE_TOTAL);
        // The scene's press exactly while the blocks' own is left out of the world: the two swap in one frame.
        boolean open = view.isBlockHidden();
        for (String id : SHOWN_IN_USE) {
            view.node(id).ifPresent(node -> node.setVisible(open));
        }
        // The tub stands under the press in the world as at the station: wherever the press keeps one.
        boolean tubThere = open || view.level().getBlockEntity(view.pos()) instanceof PressBasket basket && basket.handson$told();
        view.node("tub").ifPresent(node -> node.setVisible(tubThere));
        view.node("tub_juice").ifPresent(node -> node.setVisible(tubThere));
        swingDoors(view, dt, inUse, isOpen(view));
        // What lies in the basket is seen only through its open front.
        boolean inside = open && doors > 0.0F;
        view.node("fruit").ifPresent(node -> node.setVisible(inside));
        view.node("mash").ifPresent(node -> node.setVisible(inside));
        turnScrew(view, dt, open, mashed);
        float full = tubFilling(view, bottled);
        // The juice falls off the plate's lip into the tub in the world as in the scene: the lip and the tub are the same there.
        juice(view, dt, open, mashed, bottled, TUB_FLOOR + (TUB_FULL - TUB_FLOOR) * full);
        int color = filling != null && tubDrink.isEmpty() ? juiceColor : tubColor;
        view.node("tub_juice").ifPresent(node -> node.setLiquidColor(color));
        float surface = TUB_FLOOR + (TUB_FULL - TUB_FLOOR) * tubDrink.getCount() / (float) Press.TUB_MOST;
        tub.frame(view, dt, TUB_MIDDLE, surface, "tub_juice", TUB_NODE);
        book.frame(view, dt);
        // The badge comes with the camera as the book does, growing as it flies in and going as it flies back.
        float appear = RecipeBook.appear(view);
        StepBadge.Step step = step(view, bottled);
        badge.show(view, inUse && RecipeBook.isWorkedAt(view) && appear > RecipeBook.SPECK && step != null, step, StepBadge.Extras.NONE,
                time);
        badge.scale(view, appear * badgeSwell());
    }

    /** The basket's front opens and shuts as the cook clicks it, and stays as it was left. */
    private void swingDoors(StationView view, float dt, boolean inUse, boolean open) {
        float before = doors;
        doors = Mth.clamp(doors + (open ? dt : -dt) / DOOR_MOVE, 0.0F, 1.0F);
        if (before <= 0.0F && doors > 0.0F) {
            view.playSound(SoundEvents.WOODEN_TRAPDOOR_OPEN, "fruit", 0.4F, 1.1F);
        } else if (before > 0.0F && doors <= 0.0F) {
            view.playSound(SoundEvents.WOODEN_TRAPDOOR_CLOSE, "fruit", 0.4F, 1.1F);
        }
        float angle = DOOR_OPEN * KitchenMotions.smooth(doors);
        // Each half turns about its outer corner, its free edge coming out to the front.
        view.node("door_a").ifPresent(node -> node.rotateTo(0.0F, angle, 0.0F, 0.0F));
        view.node("door_b").ifPresent(node -> node.rotateTo(0.0F, -angle, 0.0F, 0.0F));
        // Nothing goes in through a shut basket.
        boolean wide = inUse && open && doors >= 1.0F;
        view.node("basket").ifPresent(node -> node.setEnabled(wide));
    }

    /**
     * The plate comes down on the fruit as it is pressed and goes back up when the fruit is mash, without turning; only the screw
     * on it turns.
     */
    private void turnScrew(StationView view, float dt, boolean open, float mashed) {
        float target = PRESS_DEPTH * KitchenMotions.smooth(mashed);
        float cap = push(view, dt, open);
        if (Float.isNaN(cap)) {
            pressed = Mth.lerp(Math.min(1.0F, dt * PRESS_FOLLOW), pressed, target);
            cap = 0.0F;
        }
        float down = pressed;
        float capDown = pressed + cap;
        float turn = 360.0F * PRESS_TURNS * pressed / PRESS_DEPTH;
        view.node("plate").ifPresent(node -> node.moveTo(0.0F, -down, 0.0F, 0.0F));
        view.node("screw").ifPresent(node -> {
            node.moveTo(0.0F, -capDown, 0.0F, 0.0F);
            node.rotateTo(0.0F, turn, 0.0F, 0.0F);
        });
        float driven = turn - lastTurn;
        lastTurn = turn;
        if (!open) {
            return;
        }
        if (driven > 0.0F) {
            // Driven down: the wooden screw creaks every quarter turn, the fruit squeezes under the plate once it is pressed hard.
            released = false;
            creakTurn += driven;
            if (creakTurn >= CREAK_TURN) {
                creakTurn -= CREAK_TURN;
                view.playSound(PressSounds.CREAK, "screw", 0.16F, 0.6F + view.level().random.nextFloat() * 0.08F);
            }
            squeezeIn -= dt;
            if (mashed > 0.3F && squeezeIn <= 0.0F) {
                squeezeIn = Mth.lerp(view.level().random.nextFloat(), SQUEEZE_SHORTEST, SQUEEZE_LONGEST);
                view.playSound(PressSounds.SQUEEZE, "fruit", 0.15F + 0.1F * mashed, 0.9F + view.level().random.nextFloat() * 0.15F);
            }
        } else if (driven < -1.0F && !released) {
            // The plate lets go of the mash and runs back up, the screw spinning free.
            released = true;
            creakTurn = 0.0F;
            view.playSound(PressSounds.RELEASE, "screw", 0.2F, 1.3F);
        }
    }

    /**
     * A press under way, if any: the cap gives under the hand, the screw is driven down into the fruit, trembling, deeper than its step,
     * and springs back to it; the fruit gives with a squelch and juice beads out, all the more the further the load is pressed; the
     * screw creaks under the strain and the camera dips as the cook leans in. Sets {@link #pressed}; returns how much further down
     * the cap is than the plate, or NaN while no press is under way.
     */
    private float push(StationView view, float dt, boolean open) {
        if (Float.isNaN(pushAge)) {
            lean(view, false);
            return Float.NaN;
        }
        float before = pushAge;
        pushAge += dt;
        float t = pushAge;
        int done = view.state().data().getInt(PressStation.PRESSES);
        float effort = Mth.clamp((done + (pushSent || !pushOwn ? 0 : 1)) / (float) Press.PRESSES_PER_LOAD, 0.25F, 1.0F);
        float step = PRESS_DEPTH / Press.PRESSES_PER_LOAD;
        float settled = Math.min(PRESS_DEPTH, pushFrom + step);
        float deepest = Math.min(PRESS_DEPTH, pushFrom + step * (1.0F + PUSH_OVER));
        float cap = 0.0F;
        if (t < PUSH_BEAR) {
            // The hand bears down: the cap gives a little before the screw moves.
            cap = CAP_GIVES * KitchenMotions.smooth(t / PUSH_BEAR);
            pressed = pushFrom;
        } else if (t < PUSH_BEAR + PUSH_DRIVE) {
            if (before < PUSH_BEAR) {
                if (pushOwn && !pushSent) {
                    pushSent = true;
                    sendPush(view);
                }
                lean(view, true);
                if (open) {
                    view.playSound(PressSounds.CREAK, "screw", 0.12F + 0.1F * effort, 0.62F - 0.12F * effort);
                }
            }
            float k = (t - PUSH_BEAR) / PUSH_DRIVE;
            // Slow at first, as the plate meets the fruit, then going.
            pressed = Mth.lerp(k * k * (3.0F - 2.0F * k), pushFrom, deepest);
            cap = CAP_GIVES;
            if (open && view.level().random.nextFloat() < 0.5F) {
                view.node("plate").ifPresent(node -> node.shake(0.05F * effort, 0.05F));
                view.node("screw").ifPresent(node -> node.shake(0.04F * effort, 0.05F));
            }
        } else if (t < PUSH_BEAR + PUSH_DRIVE + PUSH_BACK) {
            if (before < PUSH_BEAR + PUSH_DRIVE) {
                bites(view, open, effort);
                lean(view, false);
            }
            float k = (t - PUSH_BEAR - PUSH_DRIVE) / PUSH_BACK;
            // The fruit pushes the plate back up part of the way, with a little bounce.
            float back = 1.0F - (float) Math.exp(-5.0F * k) * Mth.cos(k * Mth.PI * 1.5F);
            pressed = Mth.lerp(Mth.clamp(back, 0.0F, 1.1F), deepest, settled);
            cap = CAP_GIVES * (1.0F - k);
        } else {
            pressed = settled;
            pushAge = Float.NaN;
            pushEndedAt = time;
            if (pushQueued > 0) {
                pushQueued--;
                startPush(true);
            }
        }
        return cap;
    }

    /** The plate bites into the fruit: it squelches, harder the further the load is pressed, and juice beads out round it. */
    private void bites(StationView view, boolean open, float effort) {
        if (!open) {
            return;
        }
        view.playSound(PressSounds.SQUEEZE, "fruit", 0.12F + 0.25F * effort, 1.0F - 0.15F * effort);
        view.node("fruit").ifPresent(node -> node.shake(0.12F + 0.15F * effort, 0.15F));
        int beadsOut = 2 + Math.round(4.0F * effort);
        for (int i = 0; i < beadsOut; i++) {
            bead(view);
        }
    }

    /** The camera dips as the cook leans on the cap, and comes back. */
    private void lean(StationView view, boolean in) {
        if (in == leaning || !view.isInUse() || !view.isFocused()) {
            return;
        }
        leaning = in;
        view.moveCamera(0.0F, in ? -LEAN_IN : 0.0F, 0.0F);
    }

    /**
     * How full the tub looks, 0 to 1 of a full tub: while the press bottles into it, the bottle's worth on its way runs in as it
     * brews, from the drink's own colour into the tub's juice; else what the tub holds.
     */
    private float tubFilling(StationView view, float bottled) {
        float held = tubDrink.getCount() / (float) Press.TUB_MOST;
        boolean intoTub = bottled > 0.0F && view.state().getItem(PressStation.BOTTLE_SLOT).isEmpty() && !dish.isEmpty()
                && (tubDrink.isEmpty() || tubDrink.is(dish.getItem()));
        ResourceLocation drink = intoTub ? BuiltInRegistries.ITEM.getKey(dish.getItem()) : null;
        float shown = intoTub ? Math.min(1.0F, held + bottled / Press.TUB_MOST) : held;
        if (drink != null || filling != null) {
            filling = drink;
            float fraction = shown;
            view.node("tub_juice").ifPresent(node -> node.showLiquid(drink, fraction));
        }
        return shown;
    }

    /**
     * The juice: it beads at the basket's foot between the staves while a fruit is pressed hard (seen only where the scene stands
     * in, {@code open}) and the first drops fall off the plate's lip; while a bottle's worth runs into the tub it rains off the lip
     * into it, fine drops straight down, each ringing the tub's juice where it lands ({@code surface}: how high it stands now).
     */
    private void juice(StationView view, float dt, boolean open, float mashed, float bottled, float surface) {
        landDrops(view, surface);
        if (open && mashed > 0.35F) {
            // The harder it is pressed, the more juice beads out.
            beads += dt * BEADS * (mashed - 0.35F) / 0.65F * (1.0F + mashed);
            while (beads >= 1.0F) {
                beads -= 1.0F;
                bead(view);
            }
        }
        float rate = bottled > 0.0F ? RAIN : mashed > 0.5F ? PRESS_DRIPS * (mashed - 0.5F) * 2.0F : 0.0F;
        drips += dt * rate;
        while (drips >= 1.0F) {
            drips -= 1.0F;
            drop(view, surface);
        }
    }

    /**
     * One drop off the plate's lip, somewhere along it over the tub, more of them near the middle, falling straight down onto the
     * juice: its fall is its whole life ({@link HandsOnLetsDoClient#juiceDrop}), so that it ends where it meets the juice.
     */
    private void drop(StationView view, float surface) {
        float along = (view.level().random.nextFloat() + view.level().random.nextFloat() - 1.0F) * LIP_HALF;
        Vector3f at = new Vector3f(LIP.x + along, LIP.y, LIP.z + view.level().random.nextFloat() * LIP_DEPTH);
        float height = Math.max(0.5F, at.y - surface);
        int style = HandsOnLetsDoClient.juiceDropFor(height);
        float fall = HandsOnLetsDoClient.juiceDropFall(style);
        // Off at the speed that brings it to the juice in that time, from the lip at nearly rest.
        float speed = (height - 0.5F * HandsOnLetsDoClient.JUICE_DROP_GRAVITY * fall * fall) / fall;
        view.spawnParticles(HandsOnLetsDoClient.juiceDrop(style), at, new Vector3f(0.0F, -speed, 0.0F), 0.0F, 1, juiceColor);
        falling.add(new Drop(time + fall, at.x, at.z));
    }

    /** A drop of juice on its way from the lip to the tub: when it lands and where. */
    private record Drop(float lands, float x, float z) {
    }

    /** A bead of juice squeezed out between the basket's staves at its foot, on the front or a side, onto the plate under it. */
    private void bead(StationView view) {
        float along = view.level().random.nextFloat();
        int side = view.level().random.nextInt(3);
        Vector3f at = side == 0 ? new Vector3f(4.0F + 8.0F * along, 9.3F, 2.9F)
                : new Vector3f(side == 1 ? 2.9F : 13.1F, 9.3F, 4.0F + 8.0F * along);
        Vector3f out = side == 0 ? new Vector3f(0.0F, -0.5F, -0.6F) : new Vector3f(side == 1 ? -0.6F : 0.6F, -0.5F, 0.0F);
        view.spawnParticles(DiegesisParticles.DROPLET, at, out, 0.05F, 1, juiceColor);
    }

    /**
     * The drops that reach the tub now ring its juice where they land; now and then one is heard, a drop into liquid (or onto the
     * wood of an empty tub's floor), quietly: rain is many drops, its sound a few.
     */
    private void landDrops(StationView view, float surface) {
        boolean wet = surface > TUB_FLOOR + 0.05F;
        for (int i = falling.size() - 1; i >= 0; i--) {
            Drop drop = falling.get(i);
            if (drop.lands() > time) {
                continue;
            }
            falling.remove(i);
            if (wet) {
                view.node("tub_juice").ifPresent(juice -> juice.ripple(drop.x() - TUB_NODE.x, drop.z() - TUB_NODE.z, DROP_RING));
            }
            if (time >= dripHeardAt) {
                dripHeardAt = time + Mth.lerp(view.level().random.nextFloat(), DRIP_HEARD_SOONEST, DRIP_HEARD_LATEST);
                view.playSound(wet ? PressSounds.DROP : PressSounds.DROP_ON_WOOD, "tub", 0.12F + 0.06F * view.level().random.nextFloat(),
                        0.95F + view.level().random.nextFloat() * 0.35F);
            }
        }
    }

    /** How much bigger the badge is drawn now: a swell as a bottle gets full, a slow breath while bottles wait to be taken. */
    private float badgeSwell() {
        float since = time - doneAt;
        if (since >= 0.0F && since < DONE_TIME) {
            return 1.0F + DONE_SWELL * Mth.sin(Mth.PI * since / DONE_TIME);
        }
        return servings > 0 ? 1.0F + READY_BREATH * (0.5F - 0.5F * Mth.cos(time * 2.4F)) : 1.0F;
    }

    /**
     * What the badge shows. Fruit in the basket waits for the cook's hand on the cap: the screw, a bar for the load's presses, or what
     * comes out while the cook presses. Mash runs into the tub by itself: what comes out, a bar for the bottle's worth on its way; but
     * with the tub full it waits for the tub to be emptied: an empty wine bottle. The juice that is done waits for one too. Ready: the
     * bottles' worth in the tub and what stands bottled; still to come: what the fruit and the mash make, one each. Null for nothing.
     */
    private StepBadge.Step step(StationView view, float bottled) {
        StationState state = view.state();
        int fruit = state.getItem(PressStation.FRUIT_SLOT).getCount();
        int mash = state.getItem(PressStation.MASH_SLOT).getCount();
        int ready = tubDrink.getCount() + state.getItem(PressStation.SERVED_SLOT).getCount();
        int more = fruit + mash;
        ItemStack bottle = new ItemStack(BarrelStation.wineBottle());
        boolean noBottle = StepBadge.lacks(BarrelStation::isWineBottle);
        if (fruit > 0) {
            boolean pressing = holdingCap || !Float.isNaN(pushAge);
            float presses = state.data().getInt(PressStation.PRESSES) / (float) Press.PRESSES_PER_LOAD;
            StepBadge.Glyph cap = pressing ? null : StepBadge.Glyph.PRESS;
            return new StepBadge.Step(dish, cap, false, ready, more, presses, 0, false, dish.getHoverName());
        }
        if (mash > 0 && tubDrink.getCount() < Press.TUB_MOST) {
            int left = ticksLeft(view, PressStation.BOTTLE_STARTED, PressStation.BOTTLE_TOTAL);
            int seconds = left > 0 ? Math.max(1, Mth.ceil(left / 20.0F)) : 0;
            Component name = dish.getHoverName();
            Component tip = seconds > 0 ? Component.translatable("handson_letsdo.barrel.seconds", name, seconds) : name;
            return new StepBadge.Step(dish, null, false, ready, more, bottled, 0, false, tip);
        }
        if (ready > 0 || more > 0) {
            return new StepBadge.Step(bottle, null, noBottle, ready, more, more > 0 ? bottled : 1.0F, 0, false, dish.getHoverName());
        }
        return null;
    }
}
