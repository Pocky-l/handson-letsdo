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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.handson_letsdo.station.BarrelStation;
import com.pockyl.handson_letsdo.station.VineryWines;
import com.pockyl.handson_letsdo.station.Wine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What the fermentation barrel shows while somebody works at it: the barrel shut as it stands, its hatch taken off with a click and
 * leant on its side, the juice at its level in the colour of its grapes turning to the wine's as wine is made of it, what ferments
 * floating on it, and the wine drawn at the bung into bottles. While nobody does, the barrel is Vinery's block itself, shut: the station hides the block
 * while it is used and until the hatch lies on it again behind the last one to leave. A barrel that ferments breathes at its hatch
 * for whoever passes.
 */
public final class BarrelBehavior implements StationBehavior {
    /**
     * The inside of the barrel, in pixels: where the juice starts and how high it stands when the barrel is full, three quarters
     * up the inside under the hatch; the opening the hatch sits in; the seam where it sits on the walls.
     */
    static final float FLOOR = 3.0F;
    static final float FULL = 12.0F;
    static final float RIM_X0 = 2.0F;
    static final float RIM_X1 = 14.0F;
    static final float RIM_Z0 = 1.0F;
    static final float RIM_Z1 = 15.0F;
    static final float SEAM = 16.05F;
    /** The top of the thin slab over the floor that takes what is poured and put in (fermentation_barrel.json: mouth, dip). */
    private static final float DIP_TOP = 3.6F;
    private static final float HALF = 8.0F;
    /** The bung in the head, where the wine is drawn, scene pixels, and the way the head faces. */
    private static final Vector3f BUNG = new Vector3f(5.0F, 8.6F, -0.3F);
    private static final Vector3f OUTWARD = new Vector3f(0.0F, 0.0F, -1.0F);
    /** How far from the middle what floats goes round. */
    private static final float FLOAT_RADIUS = 3.2F;

    private static final String[] ITEM_NODES = {"item_0", "item_1", "item_2"};
    private static final String[] SHOWN_IN_USE = {"barrel", "lid", "juice", "mouth"};
    /**
     * The hatch is lifted off and set down on the floor on the cook's right, leaning on the barrel's side, when the cook clicks it,
     * and put back with another click or when the last one goes: lifted clear of the rim, carried over in an arc while it is turned
     * on its edge, set down. Seconds each way; the way it goes, pixels from where it sits; how far it leans, degrees.
     */
    private static final float LID_MOVE = 0.42F;
    private static final float LID_TURN = 68.0F;
    private static final Vector3f LID_LIFT = new Vector3f(0.0F, 3.0F, 0.0F);
    private static final Vector3f LID_SWING = new Vector3f(-8.5F, 3.5F, 0.0F);
    private static final Vector3f LID_LEAN = new Vector3f(-9.75F, -9.75F, 0.0F);
    /** How hard a wine nobody watches seems to work. */
    private static final float IDLE_VIGOUR = 0.6F;
    /** The scene's {@code offer} node is this scaled, and stands here: what it is drawn at is moved from there, pixels. */
    private static final float OFFER_BASE = 0.35F;
    private static final Vector3f OFFER_ORIGIN = new Vector3f(8.0F, 0.0F, 8.0F);
    /** Rings from where a thing lands on the juice, and the small ones of things bobbing on it every so many seconds. */
    private static final float LANDING_RINGS = 0.6F;
    private static final float BOB_RINGS = 0.12F;
    private static final float BOB_RING_EVERY = 1.6F;
    private static final float BOB_RING_SPREAD = 1.8F;
    private static final Vector3f RISE = new Vector3f(0.0F, 3.0F, 0.0F);
    /** Bubbles a second while it ferments, and how hard it seethes along the staves then. */
    private static final float FERMENT_BUBBLES = 1.5F;
    private static final float FERMENT_SEETHE = 0.2F;
    /** The head of foam on a working must: how much of the surface it covers at its height, and when in the time it has gone. */
    private static final float HEAD = 0.55F;
    private static final float HEAD_GONE = 0.85F;
    /**
     * Several things put in at once (a wine picked in the book) jump in one after the other from the player at the bottom of the
     * screen ({@link FromPlayer}): seconds between them, seconds in the air, how high they start over the juice and how high the
     * arc goes, pixels.
     */
    private static final float ARRIVE_STAGGER = 0.16F;
    private static final float ARRIVE_TIME = 0.4F;
    private static final float ARRIVE_FROM = 9.0F;
    private static final float ARRIVE_ARC = 4.0F;
    /** Where a bottle poured at the opening is drawn from and how high, pixels; how far over the walls its lip is held. */
    private static final float POUR_FROM_ABOVE = 9.0F;
    private static final float POUR_SIZE = 4.2F;
    private static final float RIM = 16.0F;
    private static final float POUR_OVER_RIM = 1.5F;
    /** Done, the badge swells with a chime as a bottle gets there, and breathes while bottles wait. */
    private static final float DONE_SWELL = 0.35F;
    private static final float DONE_TIME = 0.6F;
    private static final float READY_BREATH = 0.04F;
    private static final Vector3f ICON_MIDDLE = new Vector3f(8.0F, 8.0F, 8.0F);
    /** A bottle of juice from the inventory offered over the opening to an empty hand: how see-through, how high it is drawn. */
    private static final int OFFERED = 0x8CFFFFFF;
    private static final float OFFER_SIZE = 4.5F;
    /** How fast what floats follows the level, per second. */
    private static final float LEVEL_FOLLOW = 1.6F;
    /** How much darker a wine runs than the juice it was made of. */
    private static final float WINE_DEPTH = 0.25F;

    private final RecipeBook book = new RecipeBook(new BarrelBook());
    private final StepBadge badge = new StepBadge(10, -10, true);
    private final UsableCache usable = new UsableCache();
    private final ItemStack[] shown = new ItemStack[ITEM_NODES.length];
    /** Things just landed on the juice, to splash where they lie once they are placed there. */
    private final boolean[] splashAt = new boolean[ITEM_NODES.length];
    /** Seconds until each floating thing sends its next small ring. */
    private final float[] ringIn = {0.7F, 1.6F, 2.4F};
    /** Seconds since each thing set off from the player (below 0 while it waits its turn, NaN once in), and where it set off. */
    private final float[] arriveAge = new float[ITEM_NODES.length];
    private final Vector3f[] arriveFrom = new Vector3f[ITEM_NODES.length];
    private final BarrelFerment ferment = new BarrelFerment();
    private final BarrelDraughts draughts = new BarrelDraughts();
    private List<Wine> wines = List.of();
    private float time;
    /** How far the hatch is on its way off, 0 on the barrel to 1 leaning on its side. */
    private float lid;
    private boolean wasOpen;
    /** The hatch as the cook last clicked it, shown at once while the barrel answers; null once the barrel agrees. */
    private Boolean lidAsked;
    private float lidAskedAt;
    private float level;
    private float bubbles;
    /** The wine the barrel makes of what is in it, or the one that waits before it. */
    private ItemStack dish = ItemStack.EMPTY;
    /** Bottles of wine that wait to be taken. */
    private int servings;
    private float doneAt = -10.0F;
    private int liquidColor = JuiceColors.of("");
    private ItemStack offered = ItemStack.EMPTY;
    private int offeredSlot = -1;
    private Item offeredItem;
    private KitchenMotions.Pour pour;

    public BarrelBehavior(StationView view) {
        Arrays.fill(shown, ItemStack.EMPTY);
        Arrays.fill(arriveAge, Float.NaN);
    }

    @Override
    public void onSceneReady(StationView view) {
        offeredItem = null;
        if (pour != null) {
            pour.stop();
            pour = null;
        }
        wines = VineryWines.all(view.level());
        draughts.ready(view);
        badge.ready(view);
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        view.node("offer").ifPresent(node -> node.setOnTop(true));
        refresh(view);
    }

    @Override
    public void onOpen(StationView view) {
        wines = VineryWines.all(view.level());
        usable.clear();
        view.setInventoryFilter(stack -> usable.test(stack, this::usable));
        book.opened(view);
        refresh(view);
        // Juice the cook came with is poured in; anything else stays in its slot.
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && view.openedByUse() && player.inventoryMenu.getCarried().isEmpty()
                && BarrelStation.pourable(view.state(), player.getMainHandItem()) > 0) {
            // The hatch comes off for it.
            lidAsked = true;
            lidAskedAt = time;
            sendPour(view, false, player.getInventory().selected);
        }
    }

    @Override
    public void onClose(StationView view) {
        book.shut(view);
        draughts.clear(view);
        refresh(view);
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        refresh(view);
    }

    /** The hatch is still on its way back after the last one left: the scene stands in for the block until it lies on the rim. */
    @Override
    public boolean holdsBlock(StationView view) {
        return !view.isInUse() && lid > 0.0F;
    }

    /** What the inventory at the barrel shows: juice, what some wine is made of, and empty wine bottles. */
    private boolean usable(ItemStack stack) {
        return !VineryWines.juiceOf(stack).isEmpty() || BarrelStation.isWineBottle(stack)
                || wines.stream().anyMatch(wine -> wine.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack)));
    }

    private static void sendPour(StationView view, boolean all, int from) {
        CompoundTag how = new CompoundTag();
        how.putBoolean(BarrelStation.ALL, all);
        if (from >= 0) {
            how.putInt(BarrelStation.FROM, from);
        }
        view.sendAction(BarrelStation.ACTION_POUR, how);
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (!(event instanceof SceneEvent.Click click)) {
            return;
        }
        if (click.node().equals("book_open")) {
            book.click(view, click.button());
        } else if (click.button() != 0) {
            return;
        } else if (click.node().equals("lid")) {
            toggleLid(view);
        } else if (click.node().equals("dip") && !offered.isEmpty()) {
            sendPour(view, Screen.hasShiftDown(), offeredSlot);
        } else if (click.node().equals("bung")) {
            // An empty hand draws the wine that waits into an empty bottle from the inventory: it comes to the bung and goes back.
            Item wine = drawnByHand(view);
            boolean all = Screen.hasShiftDown();
            if (wine != null) {
                draughts.draw(view, wine, wineColor(wine), false, 1, all);
            } else {
                // Nothing to draw: the empty bottles a hopper left in the barrel come back.
                view.playSound(SoundEvents.BOTTLE_FILL, "bung", 0.3F, 1.6F);
            }
            sendDraw(view, all, -1);
        }
    }

    /** The wine that waits in the barrel, still in it, or null for none. */
    private static Item storedWine(StationView view) {
        CompoundTag data = view.state().data();
        if (data.getInt(BarrelStation.STORED) <= 0) {
            return null;
        }
        Item wine = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(data.getString(BarrelStation.STORED_WINE)));
        return wine == Items.AIR ? null : wine;
    }

    /**
     * What a click on the bung with an empty hand draws: the wine that waits, when an empty bottle is in the inventory (or its recipe
     * needs none), else a bottled one from the barrel's wine slot; null for nothing.
     */
    private static Item drawnByHand(StationView view) {
        Item stored = storedWine(view);
        LocalPlayer player = Minecraft.getInstance().player;
        if (stored != null && (!view.state().data().getBoolean(BarrelStation.STORED_BOTTLE) || player != null && hasEmptyBottle(player.getInventory()))) {
            return stored;
        }
        ItemStack served = view.state().getItem(BarrelStation.SERVED_SLOT);
        return served.isEmpty() ? null : served.getItem();
    }

    private static boolean hasEmptyBottle(Inventory inventory) {
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (BarrelStation.isWineBottle(inventory.getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    /** Whether the barrel holds juice enough to fill a bottle at the opening. */
    private static boolean juiceToTakeBack(StationView view) {
        CompoundTag data = view.state().data();
        return data.getInt(BarrelStation.LEVEL) >= Math.max(1, data.getInt(BarrelStation.PER_BOTTLE));
    }

    private static void sendDraw(StationView view, boolean all, int from) {
        CompoundTag how = new CompoundTag();
        how.putBoolean(BarrelStation.ALL, all);
        if (from >= 0) {
            how.putInt(BarrelStation.FROM, from);
        }
        view.sendAction(BarrelStation.ACTION_DRAW, how);
    }

    /** Whether the hatch is off: as the cook clicked it, until the barrel says so too. */
    private boolean isLidOff(StationView view) {
        boolean off = BarrelStation.isOpen(view.state());
        if (lidAsked != null && (lidAsked == off || time - lidAskedAt > 1.0F)) {
            lidAsked = null;
        }
        return lidAsked != null ? lidAsked : off;
    }

    /** A click on the hatch takes it off, or puts it back on. */
    private void toggleLid(StationView view) {
        boolean open = !isLidOff(view);
        lidAsked = open;
        lidAskedAt = time;
        CompoundTag data = new CompoundTag();
        data.putBoolean(BarrelStation.OPEN, open);
        view.sendAction(BarrelStation.ACTION_LID, data);
    }

    /** The colour of a wine as it runs from the bung: its juice's, deeper; the juice in the barrel's for one no recipe names. */
    private int wineColor(Item wine) {
        int juice = wines.stream().filter(it -> it.result().is(wine)).findFirst().map(it -> JuiceColors.of(it.juice())).orElse(liquidColor);
        return wineOf(juice);
    }

    private static int wineOf(int juiceColor) {
        return blend(0xFF000000 | juiceColor, 0xFF000000, WINE_DEPTH) & 0xFFFFFF;
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
     * A bottle of juice clicked on the opening is poured in while the barrel takes it, one bottle or with shift all it takes;
     * anything else goes in as an ingredient, a bottle of juice too once the barrel is full (apple wine is made of one). An empty
     * wine bottle clicked on the opening takes a bottle's worth of juice back (shift: as many as it can); clicked on the bung it is
     * filled with the wine that waits. A click on the hatch takes it off or puts it back, whatever the hand holds.
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
        if ("bung".equals(node)) {
            Item wine = storedWine(view);
            if (!BarrelStation.isWineBottle(stack) || wine == null) {
                return false;
            }
            draughts.draw(view, wine, wineColor(wine), true, stack.getCount(), shift);
            sendDraw(view, shift, -1);
            return true;
        }
        if (!"mouth".equals(node) && !"juice".equals(node) && !"dip".equals(node)) {
            return false;
        }
        if (BarrelStation.pourable(view.state(), stack) > 0) {
            sendPour(view, shift, -1);
            return true;
        }
        if (BarrelStation.isWineBottle(stack) && juiceToTakeBack(view)) {
            CompoundTag how = new CompoundTag();
            how.putBoolean(BarrelStation.ALL, shift);
            view.sendAction(BarrelStation.ACTION_DRAW_BACK, how);
            return true;
        }
        return "mouth".equals(node) && FullZone.offer(view, BarrelStation.INGREDIENTS, stack, button);
    }

    /**
     * Shift + click on empty wine bottles in the inventory fills them with all the wine that waits at the bung; on juice, while the
     * hatch is off, it pours in all the barrel takes. Everything else goes to its place as usual.
     */
    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        Item wine = storedWine(view);
        if (BarrelStation.isWineBottle(stack) && wine != null) {
            draughts.draw(view, wine, wineColor(wine), false, stack.getCount(), true);
            sendDraw(view, true, slot);
            return true;
        }
        if (BarrelStation.pourable(view.state(), stack) <= 0 || !isLidOff(view)) {
            return false;
        }
        sendPour(view, true, slot);
        return true;
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        if (BarrelStation.EFFECT_POURED.equals(effect)) {
            poured(view, data);
        } else if (BarrelStation.EFFECT_BOTTLED.equals(effect)) {
            // A wine is done: the badge swells with a chime, and a few glints rise at the bung it is drawn from.
            doneAt = time;
            view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.35F, 1.26F);
            view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.3F, 1.5F);
            view.spawnParticles(DiegesisParticles.GLINT, "bung", 8, 0xFFFFE6A0);
        } else if (BarrelStation.EFFECT_DRAWN_BACK.equals(effect)) {
            // Juice taken back into bottles at the opening: they are dipped in.
            view.node("juice").ifPresent(juice -> juice.ripple(0.0F, 0.0F, 0.5F));
            view.spawnOnLiquid(DiegesisParticles.SPLASH, "juice", 4, 0xFF000000 | liquidColor, RISE);
            view.playSound(SoundEvents.BOTTLE_FILL, "juice", 0.6F, 1.0F);
        } else if (BarrelStation.EFFECT_DRAWN.equals(effect)) {
            Item wine = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(data.getString(BarrelStation.VESSEL)));
            if (wine != null && wine != Items.AIR) {
                draughts.drawn(wine, wineColor(wine), data.getInt(BarrelStation.COUNT));
            }
        }
    }

    /**
     * Juice was poured in at the opening: the bottle is tipped over it and the juice runs in a stream, the level waiting for it,
     * and the empty bottle is taken away. For those who only watch, it splashes.
     */
    private void poured(StationView view, CompoundTag data) {
        Item vessel = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(data.getString(BarrelStation.VESSEL)));
        int color = JuiceColors.of(data.getString(BarrelStation.JUICE));
        NodeView hand = view.node("pour_hand").orElse(null);
        if (hand == null || vessel == null || vessel == Items.AIR || !view.isFocused()) {
            view.node("juice").ifPresent(juice -> juice.ripple(0.0F, 0.0F, 0.6F));
            view.spawnOnLiquid(DiegesisParticles.SPLASH, "juice", 6, color, RISE);
            view.playSound(SoundEvents.BOTTLE_EMPTY, "juice", 0.7F, 1.0F);
            return;
        }
        if (pour != null) {
            pour.stop();
        }
        Vector3f onto = new Vector3f(HALF + 0.5F, surfaceY(), HALF + 0.5F);
        Vector3f from = view.cursorOn(FULL + POUR_FROM_ABOVE).orElse(new Vector3f(HALF, FULL + POUR_FROM_ABOVE, HALF - 4.0F));
        // Tipped over the rim of the opening, not down in the barrel: the stream falls to the juice.
        pour = new KitchenMotions.Pour(hand, "barrel_pour", new ItemStack(vessel), new ItemStack(BarrelStation.wineBottle()), color, from, onto,
                this::surfaceY, "juice", POUR_SIZE, -1.0F, 0.0F).lipAtLeast(RIM + POUR_OVER_RIM);
        float before = pour.pouredBy() - pour.flowTime();
        float flow = pour.flowTime();
        view.node("juice").ifPresent(juice -> juice.holdLevel(before, flow));
    }

    private float surfaceY() {
        return FLOOR + (FULL - FLOOR) * level;
    }

    private static float levelOf(StationView view) {
        return view.state().getTank(BarrelStation.JUICE).amount() / (float) BarrelStation.FULL;
    }

    /** How far the wine in the barrel has fermented, 0 to 1; 0 while nothing does. With the hatch off it stands still. */
    private static float progress(StationView view) {
        CompoundTag data = view.state().data();
        int total = data.getInt(BarrelStation.TOTAL);
        if (total <= 0) {
            return 0.0F;
        }
        if (data.contains(BarrelStation.HELD)) {
            return Mth.clamp((float) data.getInt(BarrelStation.HELD) / total, 0.001F, 1.0F);
        }
        if (!data.contains(BarrelStation.STARTED)) {
            return 0.0F;
        }
        return Mth.clamp((float) (view.level().getGameTime() - data.getLong(BarrelStation.STARTED)) / total, 0.001F, 1.0F);
    }

    /** Ticks until the wine in the barrel is done; 0 while nothing ferments. With the hatch off they do not count down. */
    private static int ticksLeft(StationView view) {
        CompoundTag data = view.state().data();
        if (data.contains(BarrelStation.HELD)) {
            return Math.max(1, data.getInt(BarrelStation.TOTAL) - data.getInt(BarrelStation.HELD));
        }
        if (!data.contains(BarrelStation.STARTED)) {
            return 0;
        }
        return (int) Math.max(1L, data.getLong(BarrelStation.STARTED) + data.getInt(BarrelStation.TOTAL) - view.level().getGameTime());
    }

    private void refresh(StationView view) {
        StationState state = view.state();
        ItemStack[] items = state.getItems(BarrelStation.INGREDIENTS);
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
        // The liquid: the juice's colour, turning to the wine's by the share of the barrel the wine made of it fills.
        CompoundTag data = state.data();
        String juice = BarrelStation.juiceOf(state);
        if (!juice.isEmpty()) {
            int juiceColor = JuiceColors.of(juice);
            int wineVolume = data.getInt(BarrelStation.STORED_VOLUME);
            int all = wineVolume + data.getInt(BarrelStation.LEVEL);
            int wineColor = wineOf(JuiceColors.of(data.contains(BarrelStation.STORED) ? data.getString(BarrelStation.STORED_JUICE) : juice));
            // The juice's own alpha is kept: it is how clear the liquid is drawn.
            liquidColor = all > 0 ? blend(juiceColor, juiceColor & 0xFF000000 | wineColor, (float) wineVolume / all) : juiceColor;
        }
        int color = liquidColor;
        view.node("juice").ifPresent(node -> node.setLiquidColor(color));
        // What the badge shows: the wine that ferments, else the bottles of it that wait to be drawn.
        String making = data.getString(BarrelStation.MAKING);
        ItemStack served = state.getItem(BarrelStation.SERVED_SLOT);
        Item stored = storedWine(view);
        dish = wines.stream().filter(wine -> wine.id().toString().equals(making)).findFirst().map(Wine::result).orElse(ItemStack.EMPTY);
        servings = 0;
        if (dish.isEmpty() && (stored != null || !served.isEmpty())) {
            dish = stored != null ? new ItemStack(stored) : served;
            servings = data.getInt(BarrelStation.STORED) + served.getCount();
        }
    }

    /** Something lands in the barrel: a splash on the juice and a plop, or a knock on the wood of a dry barrel. */
    private void splashIn(StationView view, int index) {
        if (view.state().getTank(BarrelStation.JUICE).amount() == 0) {
            view.playSound(SoundEvents.WOOD_HIT, ITEM_NODES[index], 0.3F, 1.2F + 0.05F * index);
            return;
        }
        // Splashes and drops of the juice itself where it lies on it (in the frame it is placed there), and rings across it.
        splashAt[index] = true;
        view.playSound(BarrelSounds.PLOP, ITEM_NODES[index], 0.45F, 0.95F + 0.04F * index);
    }

    private static int blend(int from, int to, float amount) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            result |= Math.round(Mth.lerp(amount, from >>> shift & 0xFF, to >>> shift & 0xFF)) << shift;
        }
        return result;
    }

    /** The foam of the must: its own colour, much lightened. */
    private int foamColor() {
        return blend(0xFF000000 | liquidColor, 0xFFFFF4EC, 0.7F);
    }

    @Override
    public void frame(StationView view, float dt) {
        time += dt;
        boolean inUse = view.isInUse();
        float progress = progress(view);
        // With the hatch off the wine waits: its juice lies still until the hatch is back.
        boolean fermenting = progress > 0.0F && !view.state().data().contains(BarrelStation.HELD);
        // The barrel comes shut, as it stands; the cook takes the hatch off, and it goes back on behind the last one.
        boolean opened = inUse && isLidOff(view);
        if (opened && !wasOpen) {
            view.playSound(SoundEvents.BARREL_OPEN, "lid", 0.45F, 1.05F + view.level().random.nextFloat() * 0.1F);
            if (fermenting) {
                ferment.opened(view, foamColor());
            }
        }
        wasOpen = opened;
        swingLid(view, dt, opened);
        boolean lidOff = opened && lid >= 1.0F;
        // Nothing goes in through the hatch.
        view.node("mouth").ifPresent(node -> node.setEnabled(lidOff));
        // The scene's barrel exactly while the block's own is left out of the world: the two swap in one frame.
        boolean open = view.isBlockHidden();
        for (String id : SHOWN_IN_USE) {
            view.node(id).ifPresent(node -> node.setVisible(open));
        }
        level = Mth.lerp(Math.min(1.0F, dt * LEVEL_FOLLOW), level, levelOf(view));
        boolean afloat = level > 0.02F;
        floaters(view, dt, open, open && afloat, afloat ? surfaceY() : FLOOR);
        book.frame(view, dt);
        liquidLife(view, dt, open, fermenting, progress);
        // Under its hatch a working wine breathes: for those who pass, as long as its contents make a wine.
        boolean works = inUse ? fermenting : view.state().data().contains(BarrelStation.MAKING);
        ferment.frame(view, dt, !works || lid > 0.0F ? 0.0F : inUse ? 1.0F : IDLE_VIGOUR, open, foamColor());
        if (pour != null && !pour.frame(view, dt)) {
            pour = null;
        }
        draughts.frame(view, dt, BUNG, OUTWARD);
        offer(view, inUse, lidOff);
        // The badge comes with the camera as the book does, growing as it flies in and going as it flies back.
        float appear = RecipeBook.appear(view);
        StepBadge.Step step = step(view, progress);
        badge.show(view, inUse && RecipeBook.isWorkedAt(view) && appear > RecipeBook.SPECK && step != null, step, StepBadge.Extras.NONE,
                time);
        badge.scale(view, appear * badgeSwell());
    }

    /** The hatch goes off or back on along a curve through two points, so that it never cuts through the edge of the barrel. */
    private void swingLid(StationView view, float dt, boolean off) {
        float before = lid;
        lid = Mth.clamp(lid + (off ? dt : -dt) / LID_MOVE, 0.0F, 1.0F);
        if (before < 1.0F && lid >= 1.0F) {
            view.playSound(SoundEvents.WOOD_PLACE, "lid", 0.35F, 1.1F + view.level().random.nextFloat() * 0.1F);
        } else if (before > 0.0F && lid <= 0.0F) {
            view.playSound(SoundEvents.BARREL_CLOSE, "lid", 0.4F, 1.0F + view.level().random.nextFloat() * 0.1F);
        }
        float k = KitchenMotions.smooth(lid);
        float knock = lid <= 0.0F ? ferment.bump() : 0.0F;
        float u = 1.0F - k;
        Vector3f at = new Vector3f(LID_LIFT).mul(3.0F * u * u * k).add(new Vector3f(LID_SWING).mul(3.0F * u * k * k))
                .add(new Vector3f(LID_LEAN).mul(k * k * k));
        // It is turned on its edge once it is clear of the rim.
        float turn = LID_TURN * KitchenMotions.smooth((lid - 0.15F) / 0.7F);
        view.node("lid").ifPresent(node -> {
            node.moveTo(at.x, at.y + knock, at.z, 0.0F);
            node.rotateTo(0.0F, 0.0F, turn, 0.0F);
        });
    }

    /**
     * The juice lives while it ferments: a head of foam grows on it, peaks and sinks before the wine is done, it seethes along the
     * staves and sends up bubbles.
     */
    private void liquidLife(StationView view, float dt, boolean open, boolean fermenting, float progress) {
        float head = fermenting ? HEAD * Mth.sin(Mth.PI * Math.min(1.0F, progress / HEAD_GONE)) : 0.0F;
        float foam = open && level > 0.02F ? head : 0.0F;
        int foamColor = foamColor();
        view.node("juice").ifPresent(node -> {
            node.setFoam(foam, foamColor);
            node.setAgitation(fermenting ? 0.04F : 0.01F);
            node.setEdgeBoil(fermenting ? FERMENT_SEETHE : 0.0F);
        });
        if (!open || level <= 0.02F || !fermenting) {
            return;
        }
        bubbles += dt * FERMENT_BUBBLES;
        if (bubbles >= 1.0F) {
            view.spawnInLiquid(DiegesisParticles.BUBBLE, "juice", (int) bubbles, 0xE0FFFFFF, RISE);
            bubbles -= (int) bubbles;
        }
    }

    /** What lies in the barrel floats on the juice, a little apart, bobbing; with no juice it lies on the floor. */
    private void floaters(StationView view, float dt, boolean open, boolean afloat, float surface) {
        int present = 0;
        for (ItemStack stack : shown) {
            present += stack.isEmpty() ? 0 : 1;
        }
        int rank = 0;
        for (int i = 0; i < ITEM_NODES.length; i++) {
            int index = i;
            boolean there = !shown[i].isEmpty() && open;
            float place = rank * Mth.TWO_PI / Math.max(1, present) + time * (afloat ? 0.15F : 0.0F);
            if (!shown[i].isEmpty()) {
                rank++;
            }
            float bob = afloat ? Mth.sin(time * 0.9F + index * 1.3F) * 0.07F : 0.0F;
            Vector3f at = new Vector3f(Mth.cos(place) * FLOAT_RADIUS, surface + 0.25F + bob, Mth.sin(place) * FLOAT_RADIUS);
            boolean waiting = arrive(view, i, dt, at);
            if (splashAt[i] && !waiting) {
                splashAt[i] = false;
                int color = 0xFF000000 | liquidColor;
                Vector3f where = new Vector3f(at).add(HALF, 0.0F, HALF);
                view.spawnParticles(DiegesisParticles.SPLASH, where, new Vector3f(0.0F, 9.0F, 0.0F), 1.4F, 7, color);
                view.spawnParticles(DiegesisParticles.DROPLET, where, new Vector3f(0.0F, 14.0F, 0.0F), 2.2F, 4, color);
                ringsAt(view, where, LANDING_RINGS);
            } else if (afloat && there && !waiting) {
                // Bobbing on it, each now and then sends a small ring out.
                ringIn[i] -= dt;
                if (ringIn[i] <= 0.0F) {
                    ringIn[i] = BOB_RING_EVERY + view.level().random.nextFloat() * BOB_RING_SPREAD;
                    ringsAt(view, new Vector3f(at).add(HALF, 0.0F, HALF), BOB_RINGS);
                }
            }
            view.node(ITEM_NODES[i]).ifPresent(node -> {
                node.setVisible(open);
                node.setEnabled(there && !waiting);
                node.moveTo(at.x, at.y, at.z, 0.0F);
                node.rotateTo(Mth.sin(time * 0.5F + index) * 3.0F, -place * Mth.RAD_TO_DEG, 0.0F, 0.0F);
            });
        }
    }

    /** Rings across the juice from a point of the scene (pixels), as strong as {@code strength}. */
    private static void ringsAt(StationView view, Vector3f where, float strength) {
        view.node("juice").ifPresent(juice -> {
            Vector3f local = new org.joml.Matrix4f(juice.sceneMatrix()).invert().transformPosition(new Vector3f(where));
            juice.ripple(local.x, local.z, strength);
        });
    }

    /**
     * A thing on its way in from the player: moves {@code at} (offset from the node of the item, pixels) along its arc from where
     * it set off to its place on the juice, and splashes as it lands. True while it still waits for its turn.
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
     * With an empty hand over the opening, a bottle of juice from the inventory that the barrel takes hangs see-through over it:
     * a click pours it in. Over the bung, the wine that waits hangs see-through under it: a click draws it. The bung answers while a
     * wine waits, or an empty wine bottle is on the cursor.
     */
    private void offer(StationView view, boolean inUse, boolean lidOff) {
        offered = ItemStack.EMPTY;
        offeredSlot = -1;
        NodeView node = view.node("offer").orElse(null);
        LocalPlayer player = Minecraft.getInstance().player;
        boolean emptyHand = player != null && player.inventoryMenu.getCarried().isEmpty();
        boolean free = emptyHand && view.isFocused() && pour == null && !draughts.busy();
        int slot = lidOff && free ? juiceSlot(view, player.getInventory()) : -1;
        view.node("dip").ifPresent(dip -> dip.setEnabled(slot >= 0));
        Item wine = drawnByHand(view);
        boolean bottleOnCursor = player != null && BarrelStation.isWineBottle(player.inventoryMenu.getCarried());
        boolean bottlesLeft = emptyHand && !view.state().getItem(BarrelStation.BOTTLE_SLOT).isEmpty();
        view.node("bung").ifPresent(bung -> bung.setEnabled(inUse && (emptyHand && wine != null || bottleOnCursor && storedWine(view) != null
                || bottlesLeft)));
        boolean overOpening = slot >= 0 && view.node("dip").map(NodeView::isHovered).orElse(false);
        boolean overBung = inUse && free && wine != null && view.node("bung").map(NodeView::isHovered).orElse(false);
        if (node == null) {
            return;
        }
        node.setVisible(overOpening || overBung);
        if (overOpening) {
            offered = player.getInventory().getItem(slot);
            offeredSlot = slot;
            showOffered(node, offered.getItem());
            // Under the cursor, over the point of the floor (or of the juice on it) the cursor points at, ready to be tipped.
            float under = Math.max(DIP_TOP, surfaceY());
            Vector3f at = CursorAim.clamped(view, under, OFFER_SIZE * 0.5F, RIM_X0 + 1.5F, RIM_X1 - 1.5F, RIM_Z0 + 1.5F,
                    RIM_Z1 - 1.5F).orElse(new Vector3f(HALF, under + OFFER_SIZE * 0.5F, HALF));
            node.moveTo(at.x - OFFER_ORIGIN.x, at.y - OFFER_ORIGIN.y, at.z - OFFER_ORIGIN.z, 0.0F);
            node.rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
        } else if (overBung) {
            showOffered(node, wine);
            // Where a bottle is held to be filled, leaning in as it will be.
            Vector3f at = BarrelDraughts.heldMiddle(BUNG, OUTWARD).sub(OFFER_ORIGIN);
            node.moveTo(at.x, at.y, at.z, 0.0F);
            node.rotateTo(-BarrelDraughts.TILT, 0.0F, 0.0F, 0.0F);
        }
    }

    private void showOffered(NodeView node, Item item) {
        if (offeredItem != item) {
            offeredItem = item;
            node.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
            node.setTint(OFFERED);
            node.scaleTo(OFFER_SIZE / 16.0F / OFFER_BASE, 0.0F);
        }
    }

    /** The inventory slot whose juice the barrel takes now: the one in hand first; -1 without one. */
    private static int juiceSlot(StationView view, Inventory inventory) {
        if (BarrelStation.pourable(view.state(), inventory.getSelected()) > 0) {
            return inventory.selected;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (BarrelStation.pourable(view.state(), inventory.getItem(slot)) > 0) {
                return slot;
            }
        }
        return -1;
    }

    /** How much bigger the badge is drawn now: a swell as a wine gets done, a slow breath while it waits to be taken. */
    private float badgeSwell() {
        float since = time - doneAt;
        if (since >= 0.0F && since < DONE_TIME) {
            return 1.0F + DONE_SWELL * Mth.sin(Mth.PI * since / DONE_TIME);
        }
        return servings > 0 ? 1.0F + READY_BREATH * (0.5F - 0.5F * Mth.cos(time * 2.4F)) : 1.0F;
    }

    /**
     * What the badge shows: while a wine ferments, the wine, how far it is and the time left; with the hatch off the count stands still,
     * greyed, and the badge asks for the hatch. Once done, the empty wine bottle it is drawn into (blinking while the cook has none).
     * Ready: the bottles that wait; still to come: the bottles the batch makes. Null for nothing.
     */
    private StepBadge.Step step(StationView view, float progress) {
        CompoundTag data = view.state().data();
        int ready = servings;
        if (servings <= 0 && !dish.isEmpty()) {
            int left = ticksLeft(view);
            int seconds = left > 0 ? Math.max(1, Mth.ceil(left / 20.0F)) : 0;
            Component name = dish.getHoverName();
            Component tip = seconds >= 120 ? Component.translatable("handson_letsdo.barrel.minutes", name, Mth.ceil(seconds / 60.0F))
                    : seconds > 0 ? Component.translatable("handson_letsdo.barrel.seconds", name, seconds) : name;
            boolean held = data.contains(BarrelStation.HELD);
            int more = Math.max(1, data.getInt(BarrelStation.BATCH));
            return new StepBadge.Step(dish, held ? StepBadge.Glyph.LID : null, false, 0, more, progress, held ? 0 : seconds, held, tip);
        }
        if (ready > 0) {
            boolean noBottle = StepBadge.lacks(BarrelStation::isWineBottle);
            ItemStack bottle = new ItemStack(BarrelStation.wineBottle());
            return new StepBadge.Step(bottle, null, noBottle, ready, 0, 1.0F, 0, false, dish.getHoverName());
        }
        return null;
    }
}
