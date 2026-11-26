package com.pockyl.handson_letsdo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.MeshShape;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.handson_letsdo.station.KitchenOven;
import com.pockyl.handson_letsdo.station.OvenLogic;
import com.pockyl.handson_letsdo.station.OvenStation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What an oven shows while somebody works at it ({@link OvenLook} for what differs between ovens), Hands-On Delight's shared oven:
 * its door open, the tray slid out with what bakes laid on its places, the fire inside glowing. What bakes browns and rises; done,
 * a puff of steam leaves the oven, a chime rings and the badge swells, and the dish lies on the tray. An oven that burns fuel takes
 * it at its firebox: fuel let go there or over the tray goes onto the fire, and an empty hand at the firebox is offered the
 * inventory's fuel. While nobody works at it the block is the oven again; the station hides the block while it is used and until
 * the tray is back in behind the last one to leave. An oven whose scene is its look in the world ({@link OvenLook#worldLook}) is the
 * scene whenever it is drawn, door shut, its fire burning in the firebox.
 */
public final class OvenBehavior implements StationBehavior {
    private static final int PLACES = 9;
    /** The badge's own flame over the icon, lit while the fire burns, beside the shared parts ({@link StepBadge}). */
    private static final String FLAME = "flame_lit";
    @SuppressWarnings("removal")
    private static final ResourceLocation BADGE_TEXTURE = new ResourceLocation("minecraft", "block/white_concrete");
    /** The tray starts out once the door is this far open. */
    private static final float TRAY_AFTER = 0.7F;
    /**
     * An oven that bakes sets: things of one kind lie piled on each other, each a little higher and turned a little further, the piles
     * spread over the tray. A pie crust lies in the middle as big as a dish, and its filling is laid on it in layers. Pixels, degrees.
     */
    private static final float PILE_STEP = 0.3F;
    private static final float PILE_TURN = 38.0F;
    private static final float[][][] PILES = {
            {{0.0F, -1.0F}},
            {{1.7F, -1.0F}, {-1.7F, -1.0F}},
            {{2.2F, 0.4F}, {-2.2F, 0.4F}, {0.0F, -2.6F}},
            {{1.9F, 0.6F}, {-1.9F, 0.6F}, {1.9F, -2.6F}, {-1.9F, -2.6F}}};
    private static final float CRUST_SIZE = 1.9F;
    private static final float FILLING_LIFT = 0.35F;
    private static final float FILLING_SIZE = 1.1F;
    private static final float LAYOUT_TIME = 0.25F;
    /** The dish of a set: its block, at most this big, else as an item. */
    private static final float ITEM_PIXELS = 8.0F;
    private static final float DISH_ITEM = 4.4F;
    private static final float DISH_BLOCK = 7.0F;
    private static final float DISH_BLOCK_SCALE = 0.7F;
    /** Baking, what lies there browns towards a crust's colour and rises a little, the most by the time it is done. */
    private static final int BROWNED = 0xFFB07A48;
    private static final float BROWNING = 0.7F;
    private static final float RISING = 0.12F;
    /** Several things laid at once come from the player one after the other ({@link FromPlayer}): seconds, seconds, pixels, pixels. */
    private static final float ARRIVE_STAGGER = 0.12F;
    private static final float ARRIVE_TIME = 0.4F;
    private static final float ARRIVE_FROM = 9.0F;
    private static final float ARRIVE_ARC = 3.0F;
    /** The heat inside: how see-through the glow is at most and how much it flickers. */
    private static final int GLOW_ALPHA = 0xC0;
    private static final int GLOW_FLICKER = 0x30;
    /** Smoke while it bakes shut, puffs a second; shimmer out of the open mouth while it is hot. */
    private static final float SMOKE_RATE = 1.4F;
    private static final float SHIMMER_RATE = 2.5F;
    private static final Vector3f SMOKE_RISE = new Vector3f(0.0F, 4.5F, -0.5F);
    private static final Vector3f STEAM_OUT = new Vector3f(0.0F, 5.0F, -3.0F);
    private static final int FLAME_COLOR = 0xFFFF9A3C;
    private static final int FLAME_TOP = -15;
    private static final float DONE_SWELL = 0.35F;
    private static final float DONE_TIME = 0.6F;
    private static final float READY_BREATH = 0.04F;
    private static final Vector3f ICON_MIDDLE = new Vector3f(8.0F, 8.0F, 8.0F);

    private final OvenLook look;
    private final RecipeBook book = new RecipeBook(new OvenBook());
    private final StepBadge badge = new StepBadge(10, -16, true);
    private final UsableCache usable = new UsableCache();
    private List<KitchenOven.Baking> recipes = List.of();
    private final ItemStack[] shown = new ItemStack[PLACES];
    /** Where each thing lies, pixels from its place, how big and turned how far; as laid out last. */
    private final Vector3f[] laidAt = new Vector3f[PLACES];
    private final float[] laidSize = new float[PLACES];
    private final float[] laidTurn = new float[PLACES];
    private final float[] browned = new float[PLACES];
    private final Item[] drawnFood = new Item[PLACES];
    private final Map<String, Item> drawnItems = new HashMap<>();
    private final Map<String, ResourceLocation> drawnModels = new HashMap<>();
    /** Seconds until each thing sets off from the player, NaN: not coming. */
    private final float[] arriveIn = new float[PLACES];
    private float time;
    /** How far the door is open, 0 shut to 1; how far the tray is out. */
    private float door;
    private float tray;
    private boolean wasOpening;
    /** The door as the cook last clicked it, shown at once while the oven answers; null once it agrees. */
    private Boolean doorAsked;
    private float doorAskedAt;
    private float smoke;
    private float shimmer;
    /** What bakes or waits; how many are ready; how many still bake. */
    private ItemStack dish = ItemStack.EMPTY;
    private int servings;
    private int baked;
    private boolean ready;
    private boolean sawBaking;
    private int servedBefore = -1;
    private float doneAt = -10.0F;
    private boolean baking;
    private Boolean shownHot;
    private ItemStack offered = ItemStack.EMPTY;
    /** The fuel shown see-through on the empty fire in place of the scene's, offered to an empty hand; null for the scene's. */
    private Item fuelGhost;

    public OvenBehavior(StationView view, OvenLook look) {
        this.look = look;
        Arrays.fill(shown, ItemStack.EMPTY);
        Arrays.fill(arriveIn, Float.NaN);
        Arrays.fill(browned, -1.0F);
        for (int i = 0; i < laidAt.length; i++) {
            laidAt[i] = new Vector3f();
            laidSize[i] = 1.0F;
        }
    }

    public static OvenBehavior stove(StationView view) {
        return new OvenBehavior(view, OvenLook.STOVE);
    }

    /** Candlelight's stove of {@code name}: its bamboo one has a door of its own. */
    public static OvenBehavior candlelightStove(StationView view, String name) {
        return new OvenBehavior(view, name.equals("bamboo") ? OvenLook.BAMBOO_STOVE : OvenLook.CANDLELIGHT_STOVE);
    }

    private static KitchenOven oven(StationView view) {
        return KitchenOven.of(view.level().getBlockEntity(view.pos()));
    }

    private static boolean eachPlace(StationView view) {
        KitchenOven oven = oven(view);
        return oven == null || oven.bakesEachPlace();
    }

    private static int outputs(StationView view) {
        return view.state().getItems(OvenStation.SERVED).length;
    }

    /** The node a served slot is drawn at: {@code served} for one, {@code served_N} for several. */
    private static String served(StationView view, int index) {
        return outputs(view) == 1 ? "served" : "served_" + index;
    }

    private static String dishNode(StationView view, int index) {
        return outputs(view) == 1 ? "dish" : "dish_" + index;
    }

    @Override
    public void onSceneReady(StationView view) {
        shownHot = null;
        Arrays.fill(drawnFood, null);
        Arrays.fill(browned, -1.0F);
        drawnItems.clear();
        drawnModels.clear();
        fuelGhost = null;
        recipes = recipesOf(view);
        badge.ready(view);
        MeshShape.Builder flame = MeshShape.builder();
        glyph(flame, PotBehavior.FLAME, -2, FLAME_TOP);
        MeshShape flameShape = flame.build();
        view.node("flame").ifPresent(node -> node.setVisible(false));
        view.node(FLAME).ifPresent(node -> {
            node.setTint(FLAME_COLOR);
            node.setShape(flameShape, BADGE_TEXTURE, true);
            node.setOnTop(true);
        });
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        refresh(view);
    }

    private static List<KitchenOven.Baking> recipesOf(StationView view) {
        KitchenOven oven = oven(view);
        return oven == null ? List.of() : oven.recipes(view.level());
    }

    @Override
    public void onOpen(StationView view) {
        recipes = recipesOf(view);
        usable.clear();
        view.setInventoryFilter(stack -> usable.test(stack, it -> usable(view, it)));
        book.opened(view);
        servedBefore = servedCount(view);
        // What the cook came with is what they meant to bake or burn: a right click with food lays it on the tray, never slower.
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && view.openedByUse() && player.inventoryMenu.getCarried().isEmpty()) {
            ItemStack held = player.getMainHandItem();
            KitchenOven oven = oven(view);
            if (oven != null && oven.takes(held)) {
                lay(view, player.getInventory().selected, false);
            } else if (oven != null && oven.burnsFuel() && oven.isFuel(held)) {
                fuel(view, player.getInventory().selected);
            }
        }
        refresh(view);
    }

    @Override
    public void onClose(StationView view) {
        book.shut(view);
        refresh(view);
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        refresh(view);
    }

    /**
     * The tray is still going in, or the door swinging shut, after the last one left: the scene stands in for the block until then.
     * A door that stays open while the oven burns is not waited for: the block shows its mouth open too. An oven whose scene is its
     * look in the world ({@link OvenLook#worldLook}) is the scene wherever it is drawn: near enough, with nobody at it too. Further
     * off its own block shows again, as the scene is not drawn there.
     */
    @Override
    public boolean holdsBlock(StationView view) {
        if (look.worldLook()) {
            return DiegesisClient.isSceneDrawn(view.pos());
        }
        boolean staysOpen = look.openWhileHot() && view.state().data().getBoolean(OvenStation.HOT);
        return !view.isInUse() && (tray > 0.0F || door > 0.0F && !staysOpen);
    }

    /** What the inventory at the oven shows: what bakes on it, and its fuel. */
    private boolean usable(StationView view, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        KitchenOven oven = oven(view);
        if (oven == null) {
            return false;
        }
        if (oven.burnsFuel() && oven.isFuel(stack)) {
            return true;
        }
        if (oven.bakesEachPlace()) {
            return oven.takes(stack);
        }
        return recipes.stream().anyMatch(recipe -> recipe.needed().stream().anyMatch(ingredient -> ingredient.test(stack)));
    }

    /** A shift click in the inventory lays the whole stack on the tray, or feeds the fire with it. */
    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        KitchenOven oven = oven(view);
        if (oven == null || !usable(view, stack)) {
            return false;
        }
        if (oven.burnsFuel() && oven.isFuel(stack) && !oven.takes(stack)) {
            fuel(view, slot);
        } else {
            lay(view, slot, false);
        }
        return true;
    }

    private void lay(StationView view, int slot, boolean all) {
        CompoundTag how = new CompoundTag();
        how.putInt(OvenStation.FROM, slot);
        how.putBoolean(OvenStation.ALL, all);
        view.sendAction(OvenStation.ACTION_LAY, how);
        openForIt(view);
    }

    private void fuel(StationView view, int slot) {
        fuel(view, slot, false);
    }

    private void fuel(StationView view, int slot, boolean one) {
        CompoundTag how = new CompoundTag();
        if (slot >= 0) {
            how.putInt(OvenStation.FROM, slot);
        }
        how.putBoolean(OvenStation.ONE, one);
        view.sendAction(OvenStation.ACTION_FUEL, how);
    }

    private void openForIt(StationView view) {
        if (!isDoorOpen(view) && view.state().getControl(OvenStation.PROGRESS) <= 0.0F) {
            askDoor(view, true);
        }
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (!(event instanceof SceneEvent.Click click)) {
            return;
        }
        if (isDoor(click.node()) && click.button() == 0) {
            askDoor(view, !isDoorOpen(view));
        } else if (click.node().equals("book_open")) {
            book.click(view, click.button());
        } else if (click.node().equals("panel") && !offered.isEmpty()) {
            // The fuel offered onto an empty fire: the stack, or one with a right click, as at every place.
            CompoundTag how = new CompoundTag();
            how.putBoolean(OvenStation.FROM_INVENTORY, true);
            how.putBoolean(OvenStation.ONE, click.button() == 1);
            view.sendAction(OvenStation.ACTION_FUEL, how);
        }
    }

    private boolean isDoor(String node) {
        return node.equals("door") || node.startsWith("door_");
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
            askDoor(view, !isDoorOpen(view));
            return true;
        }
        KitchenOven oven = oven(view);
        if (oven == null) {
            return false;
        }
        boolean place = node.startsWith("place_");
        boolean intoOven = place || "rack_zone".equals(node);
        if (intoOven && oven.burnsFuel() && oven.isFuel(stack) && !oven.takes(stack)) {
            // Fuel let go over the rack goes onto the fire under it: the stack, or one with a right click.
            fuel(view, -1, button == 1);
            return true;
        }
        if ((place || "rack_zone".equals(node)) && oven.takes(stack)) {
            // Let go over the rack: on every free place, or with a right click one on the place under the cursor (the free place
            // nearest it); Shift brings the inventory's alike things after it.
            CompoundTag how = new CompoundTag();
            if (button == 1) {
                int under = place ? Integer.parseInt(node.substring("place_".length())) : -1;
                boolean free = under >= 0 && view.state().getItem(SlotRef.of(OvenStation.INGREDIENTS, under)).isEmpty();
                how.putBoolean(OvenStation.ONE, true);
                how.putInt(OvenStation.PLACE, free ? under : nearestFree(view));
            }
            how.putBoolean(OvenStation.ALL, shift);
            view.sendAction(OvenStation.ACTION_LAY, how);
            return true;
        }
        return false;
    }

    /** The free place nearest the cursor on the tray; -1 for none. */
    private int nearestFree(StationView view) {
        ItemStack[] items = view.state().getItems(OvenStation.INGREDIENTS);
        int best = -1;
        float nearest = Float.MAX_VALUE;
        for (int place = 0; place < items.length && place < PLACES; place++) {
            if (!items[place].isEmpty()) {
                continue;
            }
            Optional<Vector3f> at = view.node(placeNode(place)).map(node -> node.sceneMatrix().transformPosition(new Vector3f()));
            Optional<Vector3f> cursor = at.flatMap(point -> view.cursorOn(point.y));
            float distance = at.isPresent() && cursor.isPresent() ? cursor.get().distanceSquared(at.get()) : place;
            if (distance < nearest) {
                nearest = distance;
                best = place;
            }
        }
        return best;
    }

    private static String placeNode(int index) {
        return "place_" + index;
    }

    /** Whether the door is open: as the cook clicked it, until the oven says so too. */
    private boolean isDoorOpen(StationView view) {
        boolean open = view.state().getControl(OvenStation.DOOR) > 0.5F;
        if (doorAsked != null && (doorAsked == open || time - doorAskedAt > 1.0F)) {
            doorAsked = null;
        }
        return doorAsked != null ? doorAsked : open;
    }

    private void askDoor(StationView view, boolean open) {
        doorAsked = open;
        doorAskedAt = time;
        CompoundTag data = new CompoundTag();
        data.putBoolean(OvenStation.OPEN, open);
        view.sendAction(OvenStation.ACTION_DOOR, data);
    }

    private void refresh(StationView view) {
        StationState state = view.state();
        ItemStack[] items = state.getItems(OvenStation.INGREDIENTS);
        // Things shared out over the tray come from where they lay, not from the player: only more on the tray is an arrival.
        boolean more = Arrays.stream(items).mapToInt(ItemStack::getCount).sum() > Arrays.stream(shown).mapToInt(ItemStack::getCount).sum();
        List<Integer> arrivals = new ArrayList<>();
        for (int i = 0; i < PLACES && i < items.length; i++) {
            if (!items[i].isEmpty() && shown[i].isEmpty() && view.isFocused() && more) {
                arrivals.add(i);
            }
            if (items[i].isEmpty()) {
                arriveIn[i] = Float.NaN;
            }
            shown[i] = items[i].copy();
        }
        layOut(view);
        if (arrivals.size() > 1) {
            for (int k = 0; k < arrivals.size(); k++) {
                int index = arrivals.get(k);
                Optional<Vector3f> from = FromPlayer.point(view, look.onTray() + ARRIVE_FROM, k, arrivals.size());
                if (from.isPresent()) {
                    arriveIn[index] = k * ARRIVE_STAGGER;
                    Vector3f start = offsetOf(view, index, from.get());
                    view.node(placeNode(index)).ifPresent(node -> {
                        node.setVisible(false);
                        node.moveTo(start.x, start.y, start.z, 0.0F);
                    });
                }
            }
        } else {
            arrivals.forEach(index -> view.playSound(SoundEvents.CHAIN_PLACE, placeNode(index), 0.25F, 1.5F + 0.04F * index));
        }
        badgeState(view);
    }

    /** The scene point {@code at} as an offset of a place's node from its place: the tray's own frame, the place taken off. */
    private Vector3f offsetOf(StationView view, int index, Vector3f at) {
        Optional<NodeView> tray = view.node("tray");
        Vector3f local = tray.map(node -> new Matrix4f(node.sceneMatrix()).invert().transformPosition(new Vector3f(at)))
                .orElse(new Vector3f(at).sub(8.0F, 0.0F, 8.0F));
        return local.sub(look.columnX()[index % 3], look.onTray(), look.rowZ()[index / 3]);
    }

    /**
     * Where each thing lies. An oven that bakes each place alone: each on its place. One that bakes sets: things of one kind piled on
     * each other, the piles spread over the tray; with a pie crust among them, the crust in the middle as big as the dish and
     * everything else laid on it in layers.
     */
    private void layOut(StationView view) {
        boolean each = eachPlace(view);
        int crust = -1;
        List<Item> kinds = new ArrayList<>();
        for (int i = 0; i < shown.length && !each; i++) {
            if (shown[i].isEmpty()) {
                continue;
            }
            if (crust < 0 && isCrust(shown[i])) {
                crust = i;
            } else if (!kinds.contains(shown[i].getItem())) {
                kinds.add(shown[i].getItem());
            }
        }
        int[] layers = new int[Math.max(1, kinds.size())];
        int filling = 0;
        for (int i = 0; i < shown.length; i++) {
            Vector3f home = new Vector3f(look.columnX()[i % 3], 0.0F, look.rowZ()[i / 3]);
            Vector3f at = new Vector3f();
            float size = 1.0F;
            float turn = 0.0F;
            if (shown[i].isEmpty() || each) {
                at.set(0.0F, 0.0F, 0.0F);
            } else if (crust >= 0 && i == crust) {
                at.set(-home.x, 0.0F, -1.0F - home.z);
                size = CRUST_SIZE;
            } else if (crust >= 0) {
                at.set(-home.x, FILLING_LIFT + PILE_STEP * filling, -1.0F - home.z);
                size = FILLING_SIZE;
                turn = 15.0F + PILE_TURN * filling++;
            } else {
                int kind = kinds.indexOf(shown[i].getItem());
                float[] place = kinds.size() <= PILES.length ? PILES[kinds.size() - 1][kind]
                        : new float[] {look.columnX()[kind % 3], look.rowZ()[kind / 3]};
                int layer = layers[kind]++;
                at.set(place[0] - home.x, PILE_STEP * layer, place[1] - home.z);
                turn = PILE_TURN * layer;
            }
            if (!at.equals(laidAt[i], 0.01F) || laidSize[i] != size || laidTurn[i] != turn) {
                laidAt[i].set(at);
                laidSize[i] = size;
                laidTurn[i] = turn;
                Vector3f to = at;
                float scale = size;
                float angle = turn;
                view.node(placeNode(i)).ifPresent(node -> {
                    node.moveTo(to.x, to.y, to.z, LAYOUT_TIME);
                    node.scaleTo(scale, LAYOUT_TIME);
                    node.rotateTo(0.0F, angle, 0.0F, LAYOUT_TIME);
                });
                browned[i] = -1.0F;
            }
            food(view, i);
        }
        for (int i = 0; i < outputs(view); i++) {
            servedDish(view, i);
        }
    }

    /** The thing on place {@code index}, drawn by its food node: an item lying flat, tinted as it browns. */
    private void food(StationView view, int index) {
        Item item = shown[index].isEmpty() ? null : shown[index].getItem();
        if (item == drawnFood[index]) {
            return;
        }
        drawnFood[index] = item;
        browned[index] = -1.0F;
        view.node("food_" + index).ifPresent(node -> {
            node.setVisible(item != null);
            if (item != null) {
                node.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
            }
        });
    }

    /**
     * What was baked: one dish lies on the tray, a dish that is a block (a pie, a cake) as its own block model sized to the tray; things
     * baked one by one wait in their slots on the open door, each as the item.
     */
    private void servedDish(StationView view, int index) {
        String node = dishNode(view, index);
        ItemStack stack = view.state().getItem(SlotRef.of(OvenStation.SERVED, index));
        Item item = stack.isEmpty() ? null : stack.getItem();
        if (drawnItems.containsKey(node) && drawnItems.get(node) == item) {
            return;
        }
        drawnItems.put(node, item);
        boolean single = outputs(view) == 1;
        view.node(node).ifPresent(dish -> {
            dish.setVisible(item != null);
            if (item == null) {
                return;
            }
            float extent = single ? blockExtent(item) : 0.0F;
            if (extent > 0.0F) {
                BlockState state = ((BlockItem) item).getBlock().defaultBlockState();
                dish.setVisual(Visual.ofBlock(state));
                dish.rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
                dish.scaleTo(Math.min(DISH_BLOCK_SCALE, DISH_BLOCK / extent), 0.0F);
                dish.moveTo(0.0F, 0.0F, 0.0F, 0.0F);
            } else if (single) {
                dish.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
                dish.rotateTo(-90.0F, 0.0F, 0.0F, 0.0F);
                dish.scaleTo(DISH_ITEM / ITEM_PIXELS, 0.0F);
                dish.moveTo(0.0F, 0.15F, 0.0F, 0.0F);
            } else {
                dish.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
            }
        });
    }

    /** How wide the block of a block item lies, pixels, when it is drawn as a model of its own; 0 for anything else. */
    private static float blockExtent(Item item) {
        if (!(item instanceof BlockItem blockItem)) {
            return 0.0F;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        if (state.getRenderShape() != RenderShape.MODEL) {
            return 0.0F;
        }
        VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (shape.isEmpty()) {
            return 0.0F;
        }
        AABB bounds = shape.bounds();
        return (float) Math.max(bounds.getXsize(), bounds.getZsize()) * 16.0F;
    }

    private static boolean isCrust(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("pie_crust");
    }

    private int servedCount(StationView view) {
        return Arrays.stream(view.state().getItems(OvenStation.SERVED)).mapToInt(ItemStack::getCount).sum();
    }

    /** What the badge says: what bakes or waits, how many, whether it was just done. */
    private void badgeState(StationView view) {
        StationState state = view.state();
        float progress = state.getControl(OvenStation.PROGRESS);
        ItemStack[] onTray = state.getItems(OvenStation.INGREDIENTS);
        Optional<KitchenOven.Baking> making = Optional.empty();
        if (eachPlace(view)) {
            // What bakes: the thing on the place furthest on of those still baking.
            byte[] baked = state.data().getByteArray(OvenStation.BAKED);
            int best = -1;
            for (int i = 0; i < onTray.length; i++) {
                int done = i < baked.length ? baked[i] : 0;
                if (!onTray[i].isEmpty() && done < 100 && KitchenOven.bakingOf(recipes, onTray[i]).isPresent()
                        && (best < 0 || done > (best < baked.length ? baked[best] : 0))) {
                    best = i;
                }
            }
            if (best >= 0) {
                making = KitchenOven.bakingOf(recipes, onTray[best]);
            }
        } else {
            making = KitchenOven.matching(recipes, onTray);
        }
        ItemStack[] served = state.getItems(OvenStation.SERVED);
        ItemStack waiting = Arrays.stream(served).filter(stack -> !stack.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
        int count = servedCount(view);
        baking = making.isPresent();
        servings = count;
        ready = false;
        baked = 0;
        if (making.isPresent()) {
            dish = making.get().result();
            baked = stillBaking(view, onTray, making.get());
        } else if (!waiting.isEmpty()) {
            dish = waiting;
            ready = true;
        } else {
            dish = ItemStack.EMPTY;
        }
        if (progress > 0.0F) {
            sawBaking = true;
        }
        if (servedBefore >= 0 && count > servedBefore && sawBaking) {
            sawBaking = progress > 0.0F;
            done(view);
        }
        servedBefore = count;
    }

    /** How many dishes what lies on the tray still makes: each place its own where they bake one by one, else the tray's one dish. */
    private int stillBaking(StationView view, ItemStack[] onTray, KitchenOven.Baking whole) {
        if (!eachPlace(view)) {
            return whole.result().getCount();
        }
        byte[] done = view.state().data().getByteArray(OvenStation.BAKED);
        int count = 0;
        for (int i = 0; i < onTray.length; i++) {
            if (!onTray[i].isEmpty() && (i >= done.length || done[i] < 100)) {
                count += KitchenOven.bakingOf(recipes, onTray[i]).map(baking -> baking.result().getCount()).orElse(0);
            }
        }
        return count;
    }

    /** Something is done: a puff of steam out of the oven and, at the station, the badge swells with a chime. */
    private void done(StationView view) {
        doneAt = time;
        view.spawnParticles(DiegesisParticles.STEAM, look.mouth(), STEAM_OUT, 1.6F, 10, 0xB0FFFFFF);
        if (view.isInUse()) {
            view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.35F, 1.26F);
            view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.3F, 1.5F);
            view.spawnParticles(DiegesisParticles.GLINT, served(view, 0), 6, 0xFFFFE6A0);
        }
    }

    @Override
    public void frame(StationView view, float dt) {
        time += dt;
        boolean inUse = view.isInUse();
        boolean open = inUse && isDoorOpen(view);
        boolean hot = view.state().data().getBoolean(OvenStation.HOT);
        swing(view, dt, open, look.openWhileHot() && hot);
        boolean whole = view.isBlockHidden();
        models(view, hot, door < 0.05F);
        // The inside and the grate of an oven painted glowing are dark while nothing burns.
        if (shownHot == null || shownHot != hot) {
            shownHot = hot;
            int tint = hot ? 0xFFFFFFFF : look.cold();
            view.node("inside").ifPresent(node -> node.setTint(tint));
            view.node("rack_model").ifPresent(node -> node.setTint(tint));
        }
        // The oven's own parts exactly while the block is left out of the world: the two swap in one frame.
        for (String part : look.blockParts()) {
            view.node(part).ifPresent(node -> node.setVisible(whole));
        }
        boolean reachable = open && tray >= 1.0F;
        for (int i = 0; i < PLACES; i++) {
            arrive(view, i, dt);
            boolean enabled = reachable;
            view.node(placeNode(i)).ifPresent(node -> node.setEnabled(enabled));
        }
        for (int i = 0; i < outputs(view); i++) {
            // An empty one is no place to click: the door under it is.
            boolean holds = !view.state().getItem(SlotRef.of(OvenStation.SERVED, i)).isEmpty();
            view.node(served(view, i)).ifPresent(node -> node.setEnabled(reachable && holds));
        }
        view.node("rack_zone").ifPresent(node -> node.setEnabled(reachable));
        view.node("mouth").ifPresent(node -> node.setEnabled(reachable));
        // Fuel goes in and out through the door where it burns behind it, at any time at a firebox of its own.
        KitchenOven fed = oven(view);
        boolean fuelReachable = whole && fed != null && fed.burnsFuel() && (!fed.fuelBehindDoor() || reachable);
        view.node("fuel").ifPresent(node -> node.setEnabled(fuelReachable));
        brown(view, open);
        heat(view, dt, hot, open, baking && !open);
        offerFuel(view, inUse && fuelReachable);
        book.frame(view, dt);
        float appear = RecipeBook.appear(view);
        StepBadge.Step step = step(view, open);
        boolean shown = inUse && RecipeBook.isWorkedAt(view) && appear > RecipeBook.SPECK && step != null;
        badge.show(view, shown, step, StepBadge.Extras.NONE, time);
        float swell = badgeSwell();
        badge.scale(view, appear * swell);
        view.node(FLAME).ifPresent(node -> {
            node.setVisible(shown && baking && hot);
            node.scaleTo(appear * swell, 0.0F);
        });
    }

    /** The models that follow the block: its colour, its lit front, the door lit through its glass while it burns shut. */
    private void models(StationView view, boolean hot, boolean doorShut) {
        Map<String, ResourceLocation> models = look.models().of(view.level().getBlockState(view.pos()), hot, doorShut);
        models.forEach((nodeId, model) -> {
            if (!model.equals(drawnModels.get(nodeId))) {
                drawnModels.put(nodeId, model);
                view.node(nodeId).ifPresent(node -> node.setVisual(Visual.ofModel(model).withAnchor(anchorOf(nodeId))));
            }
        });
    }

    /** The scene's door node turns on the door's hinge: its model is drawn from there. */
    private Vector3f anchorOf(String nodeId) {
        if (nodeId.equals("door")) {
            return look.door() == OvenLook.Door.SIDE ? new Vector3f(13.0F, 0.0F, 0.0F) : new Vector3f(8.0F, 0.0F, 0.0F);
        }
        return new Vector3f(8.0F, 0.0F, 8.0F);
    }

    /**
     * The door opens and the tray comes out after it; shutting, the tray goes in first and the door after it. With
     * {@code staysOpen} only the tray goes in: the door stays down.
     */
    private void swing(StationView view, float dt, boolean open, boolean staysOpen) {
        float before = door;
        if (open) {
            door = Math.min(1.0F, door + dt / look.doorTime());
            if (door >= TRAY_AFTER) {
                tray = Math.min(1.0F, tray + dt / look.trayTime());
            }
        } else {
            tray = Math.max(0.0F, tray - dt / look.trayTime());
            if (staysOpen) {
                door = Math.min(1.0F, door + dt / look.doorTime());
            } else if (tray <= 0.0F) {
                door = Math.max(0.0F, door - dt / look.doorTime());
            }
        }
        String hinge = look.door() == OvenLook.Door.LEAVES ? "door_left" : "door";
        if ((open || staysOpen) && !wasOpening && before < 1.0F) {
            view.playSound(look.openSound(), hinge, 0.5F, 0.9F + view.level().random.nextFloat() * 0.1F);
        }
        float closesAt = look.door() == OvenLook.Door.DROP ? 0.5F : 0.0F;
        if (before > closesAt && door <= closesAt && !open && !staysOpen) {
            view.playSound(look.closeSound(), hinge, 0.5F, 0.9F + view.level().random.nextFloat() * 0.1F);
        }
        if (look.door() == OvenLook.Door.LEAVES && before < 1.0F && door >= 1.0F) {
            // The leaves come to rest on their hinges with a small knock of iron.
            view.playSound(SoundEvents.CHAIN_HIT, hinge, 0.3F, 1.3F);
        }
        wasOpening = open || staysOpen;
        if (look.door() == OvenLook.Door.DROP) {
            // Eased as its own animator eases it: fast at first, slowing as it comes down.
            float eased = 1.0F - (1.0F - door) * (1.0F - door) * (1.0F - door);
            float angle = look.doorOpen() * eased;
            view.node("door").ifPresent(node -> node.rotateTo(-angle, 0.0F, 0.0F, 0.0F));
        } else if (look.door() == OvenLook.Door.SIDE) {
            // On its hinge at the cook's left: its free edge comes out towards the cook and round to the left.
            float angle = look.doorOpen() * KitchenMotions.smooth(door);
            view.node("door").ifPresent(node -> node.rotateTo(0.0F, -angle, 0.0F, 0.0F));
        } else {
            float angle = look.doorOpen() * KitchenMotions.smooth(door);
            view.node("door_left").ifPresent(node -> node.rotateTo(0.0F, angle, 0.0F, 0.0F));
            view.node("door_right").ifPresent(node -> node.rotateTo(0.0F, -angle, 0.0F, 0.0F));
        }
        float out = look.trayOut() * KitchenMotions.smooth(tray);
        view.node("tray").ifPresent(node -> node.moveTo(0.0F, 0.0F, -out, 0.0F));
        // What waits on the open door shows once it is down.
        boolean down = door >= 0.95F;
        for (int i = 0; i < outputs(view) && outputs(view) > 1; i++) {
            ItemStack stack = view.state().getItem(SlotRef.of(OvenStation.SERVED, i));
            view.node(dishNode(view, i)).ifPresent(node -> node.setVisible(down && !stack.isEmpty()));
        }
    }

    /** A thing on its way in from the player waits its turn hidden, then hops from where it set off to where it lies. */
    private void arrive(StationView view, int index, float dt) {
        if (Float.isNaN(arriveIn[index])) {
            return;
        }
        arriveIn[index] -= dt;
        if (arriveIn[index] > 0.0F) {
            return;
        }
        arriveIn[index] = Float.NaN;
        view.node(placeNode(index)).ifPresent(node -> {
            node.setVisible(true);
            Vector3f to = laidAt[index];
            node.hopTo(to.x, to.y, to.z, ARRIVE_ARC, ARRIVE_TIME);
        });
        view.playSound(SoundEvents.CHAIN_PLACE, placeNode(index), 0.2F, 1.5F + 0.04F * index);
    }

    /**
     * What bakes browns and rises as it bakes: each place by its own progress where the oven bakes each alone, all of the tray by the
     * set's where it bakes sets (only seen shut through its door, so held while the door is open).
     */
    private void brown(StationView view, boolean open) {
        boolean each = eachPlace(view);
        byte[] baked = view.state().data().getByteArray(OvenStation.BAKED);
        float set = baking && (!open || look.bakesOpen()) ? view.state().getControl(OvenStation.PROGRESS) : 0.0F;
        for (int i = 0; i < PLACES; i++) {
            float progress = each ? (i < baked.length ? baked[i] / 100.0F : 0.0F) : set;
            if (shown[i].isEmpty() || each && KitchenOven.bakingOf(recipes, shown[i]).isEmpty()) {
                progress = 0.0F;
            }
            float k = KitchenMotions.smooth(Mth.clamp(progress, 0.0F, 1.0F));
            if (Math.abs(k - browned[i]) < 0.01F) {
                continue;
            }
            browned[i] = k;
            int color = blend(0xFFFFFFFF, BROWNED, BROWNING * k);
            float size = laidSize[i] * (1.0F + RISING * k);
            view.node("food_" + i).ifPresent(node -> node.setTint(color));
            view.node(placeNode(i)).ifPresent(node -> node.scaleTo(size, 0.0F));
        }
    }

    /**
     * The heat: its glow inside, flickering; out of the open mouth the hot air shimmers; while it bakes shut, smoke rises from it.
     */
    private void heat(StationView view, float dt, boolean hot, boolean open, boolean bakingShut) {
        boolean shownHere = view.isBlockHidden();
        view.node("glow").ifPresent(node -> {
            node.setVisible(hot);
            float flicker = 0.5F + 0.3F * Mth.sin(time * 7.3F) + 0.2F * Mth.sin(time * 13.1F + 1.0F);
            node.setTint((GLOW_ALPHA - Math.round(GLOW_FLICKER * flicker)) << 24 | 0xFFFFFF);
        });
        if (!hot || !shownHere) {
            return;
        }
        if (open && door >= 1.0F) {
            shimmer += dt * SHIMMER_RATE;
            if (shimmer >= 1.0F) {
                view.spawnParticles(HandsOnLetsDoClient.HEAT, look.mouth(), new Vector3f(0.0F, 3.0F, -0.5F), 2.5F, (int) shimmer, 0x48FFFFFF);
                shimmer -= (int) shimmer;
            }
        }
        if (bakingShut) {
            smoke += dt * SMOKE_RATE;
            if (smoke >= 1.0F) {
                view.spawnParticles(HandsOnLetsDoClient.PAN_SMOKE, look.smokeFrom(), SMOKE_RISE, 1.2F, (int) smoke, 0x58C8C6C2);
                smoke -= (int) smoke;
            }
        }
    }

    /**
     * With an empty hand at an empty fire, the inventory's fuel shows see-through where it will lie, in place of what the empty
     * fire shows: a click puts it onto the fire, a right click one of it. Fuel that lies there is the hand's to take, as at every
     * place: the opening takes the click only while there is fuel to offer.
     */
    private void offerFuel(StationView view, boolean reachable) {
        offered = ItemStack.EMPTY;
        LocalPlayer player = Minecraft.getInstance().player;
        KitchenOven oven = oven(view);
        boolean can = player != null && oven != null && reachable && view.isFocused() && player.inventoryMenu.getCarried().isEmpty()
                && view.state().getItem(OvenStation.FUEL_SLOT).isEmpty();
        int slot = can ? OvenLogic.fuelSlot(oven, player.getInventory()) : -1;
        view.node("panel").ifPresent(panel -> panel.setEnabled(slot >= 0));
        if (slot >= 0 && view.node("panel").map(NodeView::isHovered).orElse(false)) {
            offered = player.getInventory().getItem(slot);
        }
        Item ghost = offered.isEmpty() ? null : offered.getItem();
        if (ghost != fuelGhost) {
            fuelGhost = ghost;
            view.node("fuel").ifPresent(fuel -> fuel.setGhostItems(ghost == null ? null : List.of(new ItemStack(ghost))));
        }
    }

    float badgeSwell() {
        float since = time - doneAt;
        if (since >= 0.0F && since < DONE_TIME) {
            return 1.0F + DONE_SWELL * Mth.sin(Mth.PI * since / DONE_TIME);
        }
        return ready ? 1.0F + READY_BREATH * (0.5F - 0.5F * Mth.cos(time * 2.4F)) : 1.0F;
    }

    /**
     * What the badge shows. While something bakes: what it makes, how far and the time left, and the fire's flame lit over it while it
     * burns; with no fire, the fuel it lacks (the cook's own, or coal, blinking while the cook has none; an oven heated from below shows
     * the heat); with its door open, where the bake waits for it, the door, the bar greyed. Once done, the dish itself, taken from the
     * tray. Ready: what lies done; still to come: what still bakes. Null for nothing.
     */
    private StepBadge.Step step(StationView view, boolean open) {
        CompoundTag data = view.state().data();
        if (baking) {
            float progress = view.state().getControl(OvenStation.PROGRESS);
            int left = data.getInt(OvenStation.LEFT);
            int seconds = progress > 0.0F && left > 0 ? Math.max(1, Mth.ceil(left / 20.0F)) : 0;
            Component name = dish.getHoverName();
            Component tip = seconds > 0 ? Component.translatable("handson_letsdo.pot.cooking", name, seconds) : name;
            if (data.getBoolean(OvenStation.COLD)) {
                KitchenOven oven = oven(view);
                if (oven == null || !oven.burnsFuel()) {
                    return new StepBadge.Step(ItemStack.EMPTY, StepBadge.Glyph.FLAME, true, servings, baked, progress, 0, true, tip);
                }
                LocalPlayer player = Minecraft.getInstance().player;
                int slot = player == null ? -1 : OvenLogic.fuelSlot(oven, player.getInventory());
                ItemStack fuel = slot >= 0 ? player.getInventory().getItem(slot) : new ItemStack(Items.COAL);
                return new StepBadge.Step(fuel, null, slot < 0, servings, baked, progress, 0, true, tip);
            }
            if (!eachPlace(view) && !look.bakesOpen() && open && progress > 0.0F) {
                return new StepBadge.Step(dish, StepBadge.Glyph.DOOR, false, servings, baked, progress, 0, true, tip);
            }
            return new StepBadge.Step(dish, null, false, servings, baked, progress, seconds, false, tip);
        }
        if (ready) {
            return new StepBadge.Step(dish, null, false, servings, 0, 1.0F, 0, false, dish.getHoverName());
        }
        return null;
    }

    private static void glyph(MeshShape.Builder shape, String[] rows, int left, int top) {
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].length(); column++) {
                if (rows[row].charAt(column) == '#') {
                    PotBehavior.pixels(shape, left + column, top + row, 1, 1);
                }
            }
        }
    }

    private static int blend(int from, int to, float amount) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            result |= Math.round(Mth.lerp(amount, from >>> shift & 0xFF, to >>> shift & 0xFF)) << shift;
        }
        return result;
    }

    static boolean shiftDown() {
        return Screen.hasShiftDown();
    }
}
