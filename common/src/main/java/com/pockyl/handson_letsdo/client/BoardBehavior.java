package com.pockyl.handson_letsdo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.MeshShape;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.handson_letsdo.station.BoardLogic;
import com.pockyl.handson_letsdo.station.CuttingBoardStation;
import com.pockyl.handson_letsdo.station.FarmCharmBoard;
import com.pockyl.handson_letsdo.station.FarmCharmDishes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What Farm &amp; Charm's board shows, to the cook and to everybody around (Hands-On Delight's board brought to it): the item lying on
 * it, built from its icon so that it falls apart where the knife came down, the cook's knife over it (or an axe along a log), a dish
 * growing layer by layer, the tool stuck into the board.
 */
public final class BoardBehavior implements StationBehavior {
    /** The board's top and the middle of its base (x 1..11, z 3..13 of the block facing north), scene pixels. */
    private static final float TOP = 1.0F;
    private static final float BASE_X = 6.0F;
    private static final float BASE_Z = 8.0F;
    /** How big an item lying to be cut is, and a layer of a dish: the base is 10 pixels square. */
    private static final float ITEM_SIZE = 9.0F;
    private static final float LAYER_SIZE = 8.0F;
    private static final float GAP = 0.8F;
    private static final float GAP_TIME = 0.12F;
    private static final float REACH = 4.0F;
    private static final float HOVER = 2.4F;
    private static final float REST_HEIGHT = 4.0F;
    private static final float REST_Z = -6.0F;
    private static final float CHOP_DOWN = 0.05F;
    private static final float CHOP_UP = 0.14F;
    private static final float EDGE = 1.0F;
    private static final float MARK_WIDTH = 0.16F;
    private static final float DASH = 0.6F;
    private static final float DASH_GAP = 0.45F;
    private static final float SNAP = 1.4F;
    private static final int MARK_COLOR = 0xA0FFFFFF;
    /** A log lies as a block at this part of its size (the scene's {@code item_block} scale), pixels. */
    private static final float BLOCK_SCALE = 0.65F;
    private static final float BLOCK_SIZE = 8.0F * BLOCK_SCALE;
    @SuppressWarnings("removal")
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("minecraft", "block/white_concrete");
    private static final float MELT_TIME = 0.28F;
    private static final float POP_TIME = 0.35F;
    private static final float POP_HEIGHT = 1.6F;
    private static final float LAYER_STEP = 0.65F;
    private static final float LAND_TIME = 0.2F;
    private static final float LAND_HOP = 0.8F;
    private static final float SETTLE_TIME = 0.12F;
    private static final float SETTLE_HOP = 0.25F;
    /** The dust off a dish pressed together on the board. */
    private static final int CHAFF = 0xFFD9C79A;
    /** How see-through a tool offered from the inventory or the board stands over the food. */
    private static final int OFFERED = 0x8CFFFFFF;
    private static final float LAYER_STAGGER = 0.16F;
    private static final float PLAYER_LAND_TIME = 0.32F;
    private static final float DROP_HEIGHT = 3.0F;
    private static final float FROM_CURSOR = 10.0F;
    private static final float LIFT_TIME = 0.1F;
    private static final float LIFT_HEIGHT = 2.5F;
    private static final float AWAY_TIME = 0.2F;
    /** The stuck tool as Farm &amp; Charm stands it: turned 25 degrees off the board's line, edge down and 12 degrees askew. */
    private static final float STUCK_YAW = 25.0F;
    private static final float STUCK_ROLL = -147.0F;
    private static final float PULL_HEIGHT = 5.0F;
    private static final float PULL_TIME = 0.18F;
    private static final float BOUNCE_DOWN = 0.09F;
    private static final float BOUNCE_UP = 0.22F;
    private static final int GHOSTS = 3;
    private static final float PENDING_WAIT = 1.0F;
    private static final float MARK_FADE = 0.18F;
    private static final float LONG_LEAN = 20.0F * Mth.DEG_TO_RAD;
    private static final float LONG = 1.4F;

    private ItemStack shownItem = ItemStack.EMPTY;
    private List<ItemVoxels.Cut> shownCuts = List.of();
    private float gap = GAP;
    private boolean opening;
    private final ItemStack[] shownLayers = new ItemStack[CuttingBoardStation.MOST_LAYERS];
    private boolean whole;
    private final List<ItemStack> lastLayers = new ArrayList<>();
    private final float[] restX = new float[CuttingBoardStation.MOST_LAYERS];
    private final float[] restY = new float[CuttingBoardStation.MOST_LAYERS];
    private final float[] restZ = new float[CuttingBoardStation.MOST_LAYERS];
    private float melting = Float.NaN;
    private final BoardBook pages = new BoardBook();
    private final RecipeBook book = new RecipeBook(pages);
    private final UsableCache usable = new UsableCache();
    private final StepBadge badge = new StepBadge(10, -10, false);
    private Item knife;
    private Item stuckShown;
    private float knifeX;
    private float knifeZ = REST_Z;
    private float knifeAngle;
    private float knifeY = REST_HEIGHT;
    private float chopAge = Float.NaN;
    private int overMark = -1;
    private int pendingChops;
    private int answeredMarks;
    private float pendingSince;
    private int drawnMarks = -1;
    private float markFade = 1.0F;
    private ItemStack lastCut = ItemStack.EMPTY;
    private final List<ItemVoxels.Cut> lastCuts = new ArrayList<>();
    private boolean quiet = true;
    private int ghost;
    private float bouncing;
    private ToolStroke stroke = ToolStroke.CUT;
    private float strokeAge = Float.NaN;
    private int strokeMark = -1;
    private boolean strokeSent;
    private Item strokeTool;
    private Item shownTool;
    private float strokeHeight;
    private float toolX;
    private float toolZ = REST_Z;
    private float toolYaw;
    private ItemStack lastShown = ItemStack.EMPTY;
    private float clock;
    private float itemRestY;
    private final List<Step> steps = new ArrayList<>();
    private float middleX = BASE_X;
    private float middleZ = BASE_Z;
    private int workTurn = -1;

    private record Step(float at, Runnable run) {
    }

    public BoardBehavior(StationView view) {
        Arrays.fill(shownLayers, ItemStack.EMPTY);
    }

    /**
     * The scene faces the cook from whichever side they come, the block keeps its own facing: what lies on the board goes to the
     * middle of its base (x 1..11 of the block's own frame, the handle beside it), turned into the scene's frame.
     */
    private void placeWork(StationView view) {
        BlockState state = view.level().getBlockState(view.pos());
        int turn = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? yaw(view.facing()) - yaw(state.getValue(BlockStateProperties.HORIZONTAL_FACING)) : 0;
        turn = Math.floorMod(turn, 360);
        if (turn == workTurn) {
            return;
        }
        workTurn = turn;
        float angle = turn * Mth.DEG_TO_RAD;
        middleX = 8.0F + (BASE_X - 8.0F) * Mth.cos(angle);
        middleZ = 8.0F - (BASE_X - 8.0F) * Mth.sin(angle);
        float x = middleX - BASE_X;
        float z = middleZ - BASE_Z;
        view.node("work").ifPresent(node -> node.moveTo(x, 0.0F, z, 0.0F));
        drawStuck(view, true);
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
        workTurn = -1;
        stuckShown = null;
        placeWork(view);
        shownItem = ItemStack.EMPTY;
        Arrays.fill(shownLayers, ItemStack.EMPTY);
        knife = null;
        shownTool = null;
        strokeAge = Float.NaN;
        steps.clear();
        quiet = true;
        badge.ready(view);
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        refresh(view);
    }

    @Override
    public void onOpen(StationView view) {
        book.opened(view);
        usable.clear();
        view.setInventoryFilter(stack -> usable.test(stack, it -> usable(view, it)));
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && view.openedByUse()) {
            useHeld(view, player);
        }
    }

    /** What the cook came with is meant for the board: food is laid as a click with it would lay it; a knife stays in its slot. */
    private void useHeld(StationView view, LocalPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !player.inventoryMenu.getCarried().isEmpty() || BoardLogic.isTool(held)) {
            return;
        }
        CompoundTag how = new CompoundTag();
        how.putInt(CuttingBoardStation.FROM, player.getInventory().selected);
        how.putBoolean(CuttingBoardStation.BY_USE, true);
        view.sendAction(CuttingBoardStation.ACTION_PLACE, how);
    }

    @Override
    public void onClose(StationView view) {
        book.shut(view);
        pendingChops = 0;
        strokeAge = Float.NaN;
        refresh(view);
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        int marks = view.state().data().getInt(CuttingBoardStation.MARKS);
        if (changes.slots().contains(CuttingBoardStation.BOARD_SLOT) || marks < answeredMarks) {
            pendingChops = 0;
        } else if (marks > answeredMarks) {
            pendingChops = Math.max(0, pendingChops - (marks - answeredMarks));
        }
        answeredMarks = marks;
        refresh(view);
    }

    private void refresh(StationView view) {
        StationState state = view.state();
        ItemStack onBoard = state.getItem(CuttingBoardStation.BOARD_SLOT);
        List<ItemVoxels.Cut> cuts = cutsMade(view, onBoard);
        if (!ItemStack.matches(onBoard, shownItem) || !cuts.equals(shownCuts)) {
            if (cuts.size() > shownCuts.size() && ItemStack.isSameItemSameComponents(onBoard, shownItem)) {
                gap = 0.0F;
                opening = true;
            }
            boolean landed = !quiet && !onBoard.isEmpty() && Arrays.stream(shownLayers).allMatch(ItemStack::isEmpty)
                    && (shownItem.isEmpty() || ItemStack.isSameItemSameComponents(onBoard, shownItem) && onBoard.getCount() > shownItem.getCount());
            shownItem = onBoard.copy();
            shownCuts = cuts;
            if (!cuts.isEmpty()) {
                lastCut = onBoard.copyWithCount(1);
                lastCuts.clear();
                lastCuts.addAll(cuts);
            }
            if (!onBoard.isEmpty()) {
                lastShown = onBoard.copyWithCount(1);
            }
            drawItem(view);
            if (landed) {
                boolean flat = ItemVoxels.icon(onBoard).isPresent();
                view.node(flat ? "item" : "item_block").ifPresent(node -> land(view, node, 0.0F, flat ? pileLift() : 0.0F, 0.0F));
            }
            view.node("pile").ifPresent(pile -> {
                pile.setVisible(onBoard.getCount() > 1 && Float.isNaN(melting) && ItemVoxels.icon(onBoard).isPresent());
                if (onBoard.getCount() > 1) {
                    pile.setShape(shape(onBoard, ITEM_SIZE, List.of(), 0.0F), sprite(onBoard));
                }
            });
        }
        List<ItemStack> layers = BoardLogic.layers(state);
        int arriving = 0;
        for (int i = 0; i < CuttingBoardStation.MOST_LAYERS && Float.isNaN(melting) && !quiet; i++) {
            ItemStack layer = i < layers.size() ? layers.get(i) : ItemStack.EMPTY;
            arriving += shownLayers[i].isEmpty() && !layer.isEmpty() ? 1 : 0;
        }
        boolean together = arriving > 1;
        int landing = 0;
        for (int i = 0; i < CuttingBoardStation.MOST_LAYERS && Float.isNaN(melting); i++) {
            ItemStack layer = i < layers.size() ? layers.get(i) : ItemStack.EMPTY;
            if (ItemStack.isSameItemSameComponents(layer, shownLayers[i])) {
                continue;
            }
            boolean arrives = together && shownLayers[i].isEmpty() && !layer.isEmpty();
            shownLayers[i] = layer.copy();
            int index = i;
            int order = arrives ? landing++ : 0;
            float delay = order * LAYER_STAGGER;
            view.node("layer_" + i).ifPresent(node -> {
                node.setVisible(!layer.isEmpty() && !arrives);
                if (!layer.isEmpty()) {
                    dress(node, index, layer);
                    node.rotateTo(0.0F, turn(index), 0.0F, 0.0F);
                    node.moveTo(jitterX(index), 0.0F, jitterZ(index), 0.0F);
                    if (arrives) {
                        after(delay, () -> {
                            if (ItemStack.isSameItemSameComponents(shownLayers[index], layer)) {
                                node.setVisible(true);
                                landFromPlayer(view, node, jitterX(index), 0.0F, jitterZ(index));
                            }
                        });
                    } else if (!quiet) {
                        land(view, node, jitterX(index), 0.0F, jitterZ(index));
                    }
                }
            });
        }
        float lastLanded = landing > 0 ? (landing - 1) * LAYER_STAGGER + PLAYER_LAND_TIME : 0.0F;
        if (!layers.isEmpty()) {
            lastLayers.clear();
            layers.forEach(layer -> lastLayers.add(layer.copy()));
        }
        boolean nowWhole = !state.data().getString(CuttingBoardStation.DISH).isEmpty();
        if (nowWhole && !whole) {
            int count = layers.size();
            Runnable settle = () -> {
                for (int i = 0; i < count; i++) {
                    int index = i;
                    view.node("layer_" + i).ifPresent(node -> node.hopTo(jitterX(index), 0.0F, jitterZ(index), SETTLE_HOP, SETTLE_TIME));
                }
                view.playSound(SoundEvents.WOOD_PLACE, "surface", 0.5F, 0.8F);
            };
            if (lastLanded > 0.0F) {
                after(lastLanded, settle);
            } else {
                settle.run();
            }
        }
        whole = nowWhole;
        quiet = false;
        drawStuck(view, false);
    }

    /** The tool stuck into the board, where Farm &amp; Charm's renderer stands it, while nothing hides it in a hand. */
    private void drawStuck(StationView view, boolean anew) {
        ItemStack stuck = view.state().getItem(CuttingBoardStation.KNIFE_SLOT);
        // In use at the station, it is the knife over the food.
        boolean shown = !stuck.isEmpty() && !(view.isFocused() && usesStuck(view));
        view.node("stuck").ifPresent(node -> {
            node.setVisible(shown);
            if (!shown) {
                return;
            }
            if (anew || stuck.getItem() != stuckShown) {
                stuckShown = stuck.getItem();
                node.setVisual(Visual.ofItem(stuckShown).withAnchor(new Vector3f(8.0F, 8.0F, 8.0F)));
                node.rotateTo(0.0F, workTurn + STUCK_YAW, STUCK_ROLL, 0.0F);
            }
        });
    }

    /** Whether the knife the cook cuts with now is the one stuck into the board. */
    private boolean usesStuck(StationView view) {
        LocalPlayer player = Minecraft.getInstance().player;
        ItemStack stuck = view.state().getItem(CuttingBoardStation.KNIFE_SLOT);
        return player != null && player.inventoryMenu.getCarried().isEmpty() && !shownItem.isEmpty() && !stuck.isEmpty()
                && FarmCharmBoard.worksWith(view.level(), shownItem, stuck);
    }

    private static float turn(int layer) {
        return layer == 0 ? 0.0F : ((layer * 7919) % 31 - 15) * 1.1F;
    }

    private static float jitterX(int layer) {
        return ((layer * 4513) % 7 - 3) * 0.12F;
    }

    private static float jitterZ(int layer) {
        return ((layer * 2971) % 7 - 3) * 0.12F;
    }

    private void keepOnPile(StationView view) {
        view.node("item").ifPresent(item -> {
            float want = pileLift();
            if (!item.isMoving() && Math.abs(want - itemRestY) > 0.001F) {
                item.moveTo(0.0F, want, 0.0F, 0.08F);
                itemRestY = want;
            }
        });
    }

    private float pileLift() {
        return shownItem.getCount() > 1 ? 0.65F : 0.0F;
    }

    private void land(StationView view, NodeView node, float x, float y, float z) {
        Vector3f from = fromHand(view, node, y + DROP_HEIGHT);
        node.moveTo(from.x, from.y, from.z, 0.0F);
        node.hopTo(x, y, z, LAND_HOP, LAND_TIME);
        after(LAND_TIME, () -> node.hopTo(x, y, z, SETTLE_HOP, SETTLE_TIME));
    }

    private void landFromPlayer(StationView view, NodeView node, float x, float y, float z) {
        Vector3f from = FromPlayer.offset(view, TOP + DROP_HEIGHT, 0, 1);
        node.moveTo(x + from.x, y + DROP_HEIGHT, z + from.z, 0.0F);
        node.hopTo(x, y, z, LAND_HOP * 2.5F, PLAYER_LAND_TIME);
        after(PLAYER_LAND_TIME, () -> node.hopTo(x, y, z, SETTLE_HOP, SETTLE_TIME));
        view.playSound(SoundEvents.WOOD_PLACE, "surface", 0.5F, 1.2F);
    }

    private static Vector3f fromHand(StationView view, NodeView node, float height) {
        Vector3f home = new Vector3f(node.definition().transform().pos());
        Optional<Vector3f> cursor = view.isFocused() ? view.cursorOn(home.y + height) : Optional.empty();
        if (cursor.isEmpty()) {
            return new Vector3f(0.0F, height, 0.0F);
        }
        Vector3f away = new Vector3f(cursor.get().x - home.x, 0.0F, cursor.get().z - home.z);
        if (away.length() > FROM_CURSOR) {
            away.normalize(FROM_CURSOR);
        }
        return away.add(0.0F, height, 0.0F);
    }

    private void after(float seconds, Runnable run) {
        steps.add(new Step(clock + seconds, run));
    }

    private Optional<NodeView> ghost(StationView view, ItemStack stack, float size) {
        ghost = (ghost + 1) % GHOSTS;
        Optional<NodeView> node = view.node("lift_" + ghost);
        node.ifPresent(it -> {
            Optional<ItemVoxels.Icon> icon = ItemVoxels.icon(stack);
            if (icon.isPresent()) {
                it.setVisual(null);
                it.setShape(ItemVoxels.build(icon.get(), size, List.of(), 0.0F), icon.get().sprite());
            } else {
                it.setShape(null, null);
                it.setVisual(Visual.ofItem(stack.getItem()).withAnchor(new Vector3f(8.0F, 8.0F, 8.0F)));
            }
            it.rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
            it.scaleTo(icon.isPresent() ? 1.0F : BLOCK_SCALE, 0.0F);
            it.setVisible(true);
        });
        return node;
    }

    private static float rise(ItemStack stack) {
        return ItemVoxels.icon(stack).isPresent() ? 0.0F : BLOCK_SIZE / 2.0F;
    }

    private void taken(StationView view, ItemStack stack, int layer) {
        float x = layer >= 0 ? jitterX(layer) : 0.0F;
        float z = layer >= 0 ? jitterZ(layer) : 0.0F;
        float y = (layer >= 0 ? layer * LAYER_STEP : stack.getCount() > 1 ? 0.65F : 0.0F) + rise(stack);
        if (layer >= 0) {
            view.node("layer_" + layer).ifPresent(node -> node.setVisible(false));
        } else {
            view.node("item").ifPresent(node -> node.setVisible(false));
            view.node("item_block").ifPresent(node -> node.setVisible(false));
            view.node("pile").ifPresent(node -> node.setVisible(false));
        }
        ghost(view, stack, layer > 0 ? LAYER_SIZE : ITEM_SIZE).ifPresent(node -> {
            node.rotateTo(0.0F, layer >= 0 ? turn(layer) : 0.0F, 0.0F, 0.0F);
            node.moveTo(x, y, z, 0.0F);
            node.moveTo(x, y + LIFT_HEIGHT, z, LIFT_TIME);
            after(LIFT_TIME, () -> {
                node.moveTo(x, y + LIFT_HEIGHT + 1.0F, z, AWAY_TIME);
                node.scaleTo(0.0F, AWAY_TIME);
            });
            after(LIFT_TIME + AWAY_TIME, () -> node.setVisible(false));
        });
        view.playSound(SoundEvents.ITEM_PICKUP, "surface", 0.3F, 1.5F);
    }

    /** The stuck tool comes out: drawn where it stood, it is drawn up out of the wood and melts away. */
    private void pulledOut(StationView view, ItemStack tool) {
        view.node("stuck").ifPresent(node -> node.setVisible(false));
        ghost = (ghost + 1) % GHOSTS;
        view.node("lift_" + ghost).ifPresent(node -> {
            node.setShape(null, null);
            node.setVisual(Visual.ofItem(tool.getItem()).withAnchor(new Vector3f(8.0F, 8.0F, 8.0F)));
            node.rotateTo(0.0F, workTurn + STUCK_YAW, STUCK_ROLL, 0.0F);
            node.scaleTo(0.5F, 0.0F);
            float y = 3.2F - 1.01F;
            node.moveTo(0.0F, y, 0.0F, 0.0F);
            node.setVisible(true);
            node.moveTo(0.0F, y + PULL_HEIGHT, 0.0F, PULL_TIME);
            after(PULL_TIME, () -> {
                node.moveTo(0.0F, y + PULL_HEIGHT + 1.0F, 0.0F, AWAY_TIME);
                node.scaleTo(0.0F, AWAY_TIME);
            });
            after(PULL_TIME + AWAY_TIME, () -> node.setVisible(false));
        });
    }

    private void bounced(StationView view, ItemStack stack) {
        List<ItemStack> layers = BoardLogic.layers(view.state());
        float top = !layers.isEmpty() ? layers.size() * LAYER_STEP : shownItem.isEmpty() ? 0.0F : itemHeight() + 0.3F;
        float y = top + rise(stack);
        ghost(view, stack, LAYER_SIZE).ifPresent(node -> {
            Vector3f hand = fromHand(view, node, y + DROP_HEIGHT);
            node.moveTo(hand.x, hand.y, hand.z, 0.0F);
            node.moveTo(hand.x * 0.3F, y, hand.z * 0.3F, BOUNCE_DOWN);
            after(BOUNCE_DOWN, () -> node.hopTo(hand.x, hand.y, hand.z, 1.2F, BOUNCE_UP));
            after(BOUNCE_DOWN + BOUNCE_UP, () -> node.setVisible(false));
        });
        bouncing = BOUNCE_DOWN + BOUNCE_UP;
    }

    private List<ItemVoxels.Cut> cutsMade(StationView view, ItemStack stack) {
        List<ItemVoxels.Cut> cuts = new ArrayList<>();
        if (stack.isEmpty() || ItemVoxels.icon(stack).isEmpty()) {
            return cuts;
        }
        int made = Math.min(marks(view), view.state().data().getInt(CuttingBoardStation.MARKS) + pendingChops);
        for (int mark = 0; mark < made; mark++) {
            cuts.add(mark(view, stack, mark));
        }
        return cuts;
    }

    private void drawItem(StationView view) {
        NodeView item = view.node("item").orElse(null);
        NodeView block = view.node("item_block").orElse(null);
        if (item == null || block == null) {
            return;
        }
        boolean flat = !shownItem.isEmpty() && ItemVoxels.icon(shownItem).isPresent();
        item.setVisible(flat);
        block.setVisible(!shownItem.isEmpty() && !flat);
        if (flat) {
            item.setShape(ItemVoxels.build(ItemVoxels.icon(shownItem).get(), ITEM_SIZE, shownCuts, gap), sprite(shownItem));
            if (!item.isMoving()) {
                item.moveTo(0.0F, pileLift(), 0.0F, 0.0F);
                itemRestY = pileLift();
            }
        } else if (!shownItem.isEmpty()) {
            block.setVisual(Visual.ofItem(shownItem.getItem()).withAnchor(new Vector3f(8.0F, 8.0F, 8.0F)));
        }
    }

    private static MeshShape shape(ItemStack stack, float size, List<ItemVoxels.Cut> cuts, float gap) {
        return ItemVoxels.icon(stack).map(icon -> ItemVoxels.build(icon, size, cuts, gap)).orElse(null);
    }

    private static ResourceLocation sprite(ItemStack stack) {
        return ItemVoxels.icon(stack).map(ItemVoxels.Icon::sprite).orElse(null);
    }

    @Override
    public void frame(StationView view, float dt) {
        placeWork(view);
        clock += dt;
        bouncing = Math.max(0.0F, bouncing - dt);
        if (!steps.isEmpty()) {
            List<Step> due = steps.stream().filter(step -> step.at() <= clock).toList();
            steps.removeAll(due);
            due.forEach(step -> step.run().run());
        }
        if (opening) {
            gap = Math.min(GAP, gap + GAP * dt / GAP_TIME);
            opening = gap < GAP;
            drawItem(view);
        }
        if (pendingChops > 0 && clock - pendingSince > PENDING_WAIT) {
            pendingChops = 0;
            refresh(view);
        }
        keepOnPile(view);
        moveKnife(view, dt);
        drawStuck(view, false);
        showBadge(view);
        book.frame(view, dt);
    }

    /**
     * The tool that works what lies on the board: the one on the cook's cursor; with an empty hand the board's stuck one, else one in
     * the inventory, offered see-through over the food, so cutting never needs a trip to the inventory.
     */
    private Optional<ItemStack> tool(StationView view) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !view.isFocused() || shownItem.isEmpty() || !BoardLogic.layers(view.state()).isEmpty()) {
            return Optional.empty();
        }
        ItemStack carried = player.inventoryMenu.getCarried();
        if (!carried.isEmpty()) {
            return FarmCharmBoard.worksWith(view.level(), shownItem, carried) ? Optional.of(carried) : Optional.empty();
        }
        ItemStack stuck = view.state().getItem(CuttingBoardStation.KNIFE_SLOT);
        if (FarmCharmBoard.worksWith(view.level(), shownItem, stuck)) {
            return Optional.of(stuck);
        }
        int slot = BoardLogic.toolSlot(view.level(), shownItem, player.getInventory());
        return slot < 0 ? Optional.empty() : Optional.of(player.getInventory().getItem(slot));
    }

    private static boolean offered() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.inventoryMenu.getCarried().isEmpty();
    }

    private void moveKnife(StationView view, float dt) {
        NodeView node = view.node("knife").orElse(null);
        NodeView turn = view.node("knife_turn").orElse(null);
        if (node == null || turn == null) {
            return;
        }
        Optional<ItemStack> tool = tool(view);
        if (Float.isNaN(strokeAge)) {
            stroke = ToolStroke.of(view.level(), shownItem);
        }
        drawMarks(view, tool.isPresent() && stroke == ToolStroke.CUT, dt);
        overMark = -1;
        if (stroke != ToolStroke.CUT) {
            node.setVisible(false);
            chopAge = Float.NaN;
            moveTool(view, dt, tool);
            return;
        }
        view.node("tool").ifPresent(other -> other.setVisible(false));
        Optional<Vector3f> over = tool.isPresent() ? overItem(view) : Optional.empty();
        boolean drawn = over.isPresent() || !Float.isNaN(chopAge) && tool.isPresent();
        node.setVisible(drawn);
        view.setCarriedHidden(drawn || bouncing > 0.0F);
        if (!drawn) {
            knifeY = itemHeight() + HOVER + REST_HEIGHT;
            return;
        }
        if (tool.get().getItem() != knife) {
            knife = tool.get().getItem();
            node.setVisual(Visual.ofItem(knife).withAnchor(new Vector3f(8.0F, 8.0F, 8.0F)));
        }
        node.setTint(offered() && Float.isNaN(chopAge) ? OFFERED : 0xFFFFFFFF);
        float wantX = knifeX;
        float wantZ = knifeZ;
        float wantY = itemHeight() + HOVER;
        int next = nextMark(view);
        if (next >= 0) {
            knifeAngle = mark(view, shownItem, next).angle();
        }
        if (over.isPresent()) {
            wantX = over.get().x;
            wantZ = over.get().z;
            if (next >= 0) {
                ItemVoxels.Cut mark = mark(view, shownItem, next);
                float off = mark.distance(wantX, wantZ);
                if (Math.abs(off) < SNAP) {
                    overMark = next;
                    wantX -= mark.normalX() * off;
                    wantZ -= mark.normalZ() * off;
                }
            }
        }
        float follow = 1.0F - (float) Math.exp(-dt * 22.0F);
        knifeX += (wantX - knifeX) * follow;
        knifeZ += (wantZ - knifeZ) * follow;
        knifeY += (wantY - knifeY) * follow;
        float y = knifeY;
        if (!Float.isNaN(chopAge)) {
            chopAge += dt;
            float surface = itemHeight() - knifeY;
            float down = chopAge < CHOP_DOWN ? chopAge / CHOP_DOWN : Math.max(0.0F, 1.0F - (chopAge - CHOP_DOWN) / CHOP_UP);
            y += surface * down;
            if (chopAge > CHOP_DOWN + CHOP_UP) {
                chopAge = Float.NaN;
            }
        }
        turn.moveTo(knifeX, y + EDGE, knifeZ, 0.0F);
        turn.rotateTo(0.0F, -knifeAngle * Mth.RAD_TO_DEG, 0.0F, 0.0F);
    }

    /** The axe along a log: anywhere on its near side, its stroke played out here, the blow sent when the edge meets the bark. */
    private void moveTool(StationView view, float dt, Optional<ItemStack> tool) {
        NodeView turn = view.node("tool_turn").orElse(null);
        NodeView node = view.node("tool").orElse(null);
        if (turn == null || node == null) {
            return;
        }
        boolean striking = !Float.isNaN(strokeAge);
        Optional<Vector3f> over = tool.isPresent() ? overItem(view) : Optional.empty();
        boolean drawn = over.isPresent() || striking;
        node.setVisible(drawn);
        view.setCarriedHidden(drawn || bouncing > 0.0F);
        if (!drawn) {
            return;
        }
        Item item = striking || tool.isEmpty() ? strokeTool : tool.get().getItem();
        if (item != shownTool) {
            shownTool = item;
            node.setVisual(Visual.ofItem(item).withAnchor(new Vector3f(8.0F, 8.0F, 8.0F)));
        }
        node.setTint(offered() && !striking ? OFFERED : 0xFFFFFFFF);
        if (!striking) {
            float reach = BLOCK_SIZE / 2.0F - 1.2F;
            float follow = 1.0F - (float) Math.exp(-dt * 22.0F);
            toolX += (Mth.clamp(over.get().x, -reach, reach) - toolX) * follow;
            toolZ += (-BLOCK_SIZE / 2.0F - 0.15F - toolZ) * follow;
            toolYaw = 0.0F;
            overMark = nextMark(view);
            strokeTool = item;
            strokeHeight = itemHeight() - 0.3F;
        } else {
            strokeAge += dt;
            if (!strokeSent && strokeAge >= stroke.impact) {
                strokeSent = true;
                impact(view);
            }
        }
        ToolStroke.Pose pose = stroke.pose(new Vector3f(0.0F, strokeHeight, 0.0F), strokeAge, 0.0F, 0.0F);
        if (striking && strokeAge >= stroke.length) {
            strokeAge = Float.NaN;
        }
        Vector3f at = pose.nodePosition(stroke.icon);
        Vector3f turned = pose.degrees();
        turn.moveTo(toolX, 0.0F, toolZ, 0.0F);
        turn.rotateTo(0.0F, -toolYaw * Mth.RAD_TO_DEG, 0.0F, 0.0F);
        node.moveTo(at.x, at.y, at.z, 0.0F);
        node.rotateTo(turned.x, turned.y, turned.z, 0.0F);
    }

    private void startStroke(StationView view, Optional<Vector3f> over) {
        Optional<ItemStack> tool = tool(view);
        if (over.isEmpty() || tool.isEmpty() || !Float.isNaN(strokeAge)) {
            return;
        }
        strokeTool = tool.get().getItem();
        strokeAge = 0.0F;
        strokeSent = false;
        strokeMark = overMark;
        strokeHeight = itemHeight() - 0.3F;
    }

    private void impact(StationView view) {
        if (shownItem.isEmpty() || strokeMark < 0) {
            return;
        }
        pendingChops++;
        pendingSince = clock;
        CompoundTag at = new CompoundTag();
        at.putInt(CuttingBoardStation.MARK, strokeMark);
        at.putFloat(CuttingBoardStation.AT, middleZ + toolZ);
        at.putFloat(CuttingBoardStation.AT_X, middleX + toolX);
        view.sendAction(CuttingBoardStation.ACTION_CHOP, at);
        refresh(view);
    }

    private float itemHeight() {
        if (!shownItem.isEmpty() && ItemVoxels.icon(shownItem).isEmpty()) {
            return BLOCK_SIZE;
        }
        return (shownItem.getCount() > 1 ? 0.65F : 0.0F) + ITEM_SIZE / 16.0F;
    }

    /** How many marks the item lying there has: as many as the server laid for the tool, one before it says. */
    private static int marks(StationView view) {
        return Math.max(1, view.state().data().getList(CuttingBoardStation.SPOTS, Tag.TAG_FLOAT).size());
    }

    private ItemVoxels.Cut mark(StationView view, ItemStack stack, int mark) {
        CompoundTag data = view.state().data();
        ListTag spots = data.getList(CuttingBoardStation.SPOTS, Tag.TAG_FLOAT);
        ListTag angles = data.getList(CuttingBoardStation.ANGLES, Tag.TAG_FLOAT);
        float spot = mark < spots.size() ? spots.getFloat(mark) : (mark + 1.0F) / (marks(view) + 1.0F);
        return line(stack, spot, mark < angles.size() ? angles.getFloat(mark) : 0.0F);
    }

    private static ItemVoxels.Cut line(ItemStack stack, float spot, float angle) {
        ItemVoxels.Axis axis = axis(stack);
        float[] at = axis.at(spot);
        float lean = angle * (axis.elongation() >= LONG ? LONG_LEAN : BoardLogic.MOST_LEAN);
        return new ItemVoxels.Cut(at[0], at[1], ItemVoxels.Axis.normalized(axis.across() + lean));
    }

    private static ItemVoxels.Axis axis(ItemStack stack) {
        return ItemVoxels.icon(stack).map(icon -> icon.axis(ITEM_SIZE)).orElse(ItemVoxels.Axis.square(BLOCK_SIZE / 2.0F));
    }

    private int nextMark(StationView view) {
        int next = view.state().data().getInt(CuttingBoardStation.MARKS) + pendingChops;
        return next < marks(view) ? next : -1;
    }

    /** A dashed line across the item where it is to be cut next, for the cook alone, one at a time. */
    private void drawMarks(StationView view, boolean shown, float dt) {
        NodeView node = view.node("marks").orElse(null);
        if (node == null) {
            return;
        }
        Optional<ItemVoxels.Icon> icon = ItemVoxels.icon(shownItem);
        int next = shown && icon.isPresent() ? nextMark(view) : -1;
        ItemVoxels.Cut mark = next < 0 ? null : mark(view, shownItem, next);
        int key = next < 0 ? 0 : Objects.hash(next, mark, itemHeight(), shownItem.getItem());
        if (markFade < 1.0F) {
            markFade = Math.min(1.0F, markFade + dt / MARK_FADE);
            node.setTint(fade(markFade));
        }
        if (key == drawnMarks) {
            return;
        }
        drawnMarks = key;
        node.setVisible(next >= 0);
        if (next < 0) {
            return;
        }
        markFade = 0.0F;
        float alongX = Mth.cos(mark.angle());
        float alongZ = Mth.sin(mark.angle());
        float[] ends = icon.get().solidSpan(mark.x(), mark.z(), mark.angle(), ITEM_SIZE);
        float y = itemHeight() + 0.03F;
        float wideX = mark.normalX() * MARK_WIDTH;
        float wideZ = mark.normalZ() * MARK_WIDTH;
        MeshShape.Builder shape = MeshShape.builder();
        for (float t = ends[0]; t < ends[1]; t += DASH + DASH_GAP) {
            float end = Math.min(ends[1], t + DASH);
            float ax = mark.x() + alongX * t;
            float az = mark.z() + alongZ * t;
            float bx = mark.x() + alongX * end;
            float bz = mark.z() + alongZ * end;
            shape.corner(ax - wideX, y, az - wideZ, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F)
                    .corner(ax + wideX, y, az + wideZ, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F)
                    .corner(bx + wideX, y, bz + wideZ, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F)
                    .corner(bx - wideX, y, bz - wideZ, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F);
        }
        node.setTint(fade(markFade));
        node.setShape(shape.build(), WHITE, true);
    }

    private static int fade(float amount) {
        return Math.round((MARK_COLOR >>> 24) * amount) << 24 | MARK_COLOR & 0xFFFFFF;
    }

    private Optional<Vector3f> overItem(StationView view) {
        return view.cursorOn(TOP + itemHeight())
                .filter(point -> Math.abs(point.x - middleX) < ITEM_SIZE / 2.0F + 1.0F
                        && Math.abs(point.z - middleZ) < ITEM_SIZE / 2.0F + 1.0F)
                .map(point -> new Vector3f(Mth.clamp(point.x - middleX, -REACH, REACH), 0.0F, Mth.clamp(point.z - middleZ, -REACH, REACH)));
    }

    /**
     * The badge: food waiting, the tool it needs (blinking while the cook has none) or else what the cut makes and how many; layers
     * that make a dish, the dish and how many the layers make.
     */
    private void showBadge(StationView view) {
        StepBadge.Step step = step(view);
        badge.show(view, view.isInUse() && RecipeBook.isWorkedAt(view) && step != null, step, StepBadge.Extras.NONE, clock);
    }

    private StepBadge.Step step(StationView view) {
        String dish = view.state().data().getString(CuttingBoardStation.DISH);
        List<ItemStack> layers = BoardLogic.layers(view.state());
        if (!dish.isEmpty() && !layers.isEmpty()) {
            ResourceLocation id = ResourceLocation.tryParse(dish);
            Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
            int rounds = layers.stream().mapToInt(ItemStack::getCount).min().orElse(0);
            int each = FarmCharmBoard.dish(view.level(), layers).map(found -> FarmCharmBoard.result(found.value()).getCount()).orElse(1);
            ItemStack made = new ItemStack(item);
            return new StepBadge.Step(made, null, false, 0, rounds * each, Float.NaN, 0, false, made.getHoverName());
        }
        if (shownItem.isEmpty()) {
            return null;
        }
        Optional<ItemStack> tool = tool(view);
        if (tool.isEmpty() && view.isFocused()) {
            ItemStack wanted = wantedTool(view);
            return new StepBadge.Step(wanted, null, true, 0, 0, Float.NaN, 0, false, wanted.getHoverName());
        }
        int batch = Math.min(shownItem.getCount(), CuttingBoardStation.BATCH);
        Optional<ItemStack> makes = FarmCharmBoard.cut(view.level(), shownItem).map(recipe -> recipe.getResult().copy())
                .or(() -> FarmCharmBoard.stripped(shownItem));
        if (makes.isEmpty()) {
            return null;
        }
        float progress = (float) Math.min(marks(view), view.state().data().getInt(CuttingBoardStation.MARKS) + pendingChops) / marks(view);
        ItemStack result = makes.get();
        return new StepBadge.Step(result, null, false, 0, batch * result.getCount(), progress, 0, false, result.getHoverName());
    }

    /** The tool the food lying there wants, as the badge shows it: Farm &amp; Charm's iron cleaver, or an iron axe for a log. */
    private ItemStack wantedTool(StationView view) {
        if (FarmCharmBoard.work(view.level(), shownItem) == FarmCharmBoard.Work.STRIP) {
            return new ItemStack(Items.IRON_AXE);
        }
        Item cleaver = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(FarmCharmDishes.MOD_ID, "iron_cleaver"));
        return new ItemStack(cleaver == Items.AIR ? Items.IRON_AXE : cleaver);
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && "book_open".equals(click.node())) {
            book.click(view, click.button());
            return;
        }
        if (!(event instanceof SceneEvent.Click click) || !"surface".equals(click.node())) {
            return;
        }
        StationState state = view.state();
        if (!BoardLogic.layers(state).isEmpty()) {
            // A left click finishes the dish the layers make; a right click, or one on layers that make none yet, takes the top back.
            boolean ready = !state.data().getString(CuttingBoardStation.DISH).isEmpty() && click.button() == 0;
            view.sendAction(ready ? CuttingBoardStation.ACTION_ASSEMBLE : CuttingBoardStation.ACTION_TAKE, new CompoundTag());
            return;
        }
        if (!shownItem.isEmpty()) {
            if (click.button() == 0 && tool(view).isPresent()) {
                if (overItem(view).isPresent()) {
                    chop(view);
                }
            } else {
                view.sendAction(CuttingBoardStation.ACTION_TAKE, new CompoundTag());
            }
            return;
        }
        if (!state.getItem(CuttingBoardStation.KNIFE_SLOT).isEmpty()) {
            // The bare board: its stuck tool comes out into the inventory with either button.
            view.sendAction(CuttingBoardStation.ACTION_TAKE, new CompoundTag());
        }
    }

    private void chop(StationView view) {
        Optional<Vector3f> over = overItem(view);
        if (stroke != ToolStroke.CUT) {
            startStroke(view, over);
            return;
        }
        if (over.isEmpty() || !Float.isNaN(chopAge)) {
            return;
        }
        chopAge = 0.0F;
        if (overMark < 0) {
            view.playSound(SoundEvents.WOOD_HIT, "item", 0.25F, 1.6F);
            return;
        }
        pendingChops++;
        pendingSince = clock;
        CompoundTag at = new CompoundTag();
        at.putInt(CuttingBoardStation.MARK, overMark);
        at.putFloat(CuttingBoardStation.AT, middleZ + knifeZ);
        at.putFloat(CuttingBoardStation.AT_X, middleX + knifeX);
        view.sendAction(CuttingBoardStation.ACTION_CHOP, at);
        refresh(view);
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
     * The knife on the cursor chops, and the right button puts it away; shift with a tool over the bare board sticks it in, as the
     * board's own use does; any other stack let go on the board goes where the board says, all of it or, with the right button, one.
     */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ("book_open".equals(node)) {
            book.click(view, button);
            return true;
        }
        StationState state = view.state();
        if (shift && BoardLogic.isTool(stack) && shownItem.isEmpty() && state.getItem(CuttingBoardStation.KNIFE_SLOT).isEmpty()
                && BoardLogic.layers(state).isEmpty()) {
            view.sendAction(CuttingBoardStation.ACTION_CARVE, new CompoundTag());
            return true;
        }
        if (button == 1 && BoardLogic.isTool(stack)) {
            view.putAwayCarried();
            return true;
        }
        if (tool(view).isPresent()) {
            chop(view);
            return true;
        }
        if (BoardLogic.isTool(stack)) {
            view.playSound(SoundEvents.WOOD_HIT, "item", 0.25F, 1.6F);
            return true;
        }
        CompoundTag how = new CompoundTag();
        how.putBoolean(CuttingBoardStation.ONE, button == 1);
        view.sendAction(CuttingBoardStation.ACTION_PLACE, how);
        return true;
    }

    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        if (stack.isEmpty() || FarmCharmBoard.work(view.level(), stack) == FarmCharmBoard.Work.NONE && !BoardBook.goesIntoADish(view, stack)) {
            return false;
        }
        CompoundTag how = new CompoundTag();
        how.putInt(CuttingBoardStation.FROM, slot);
        view.sendAction(CuttingBoardStation.ACTION_PLACE, how);
        return true;
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        if (CuttingBoardStation.EFFECT_CHOPPED.equals(effect)) {
            chopped(view, data);
        } else if (CuttingBoardStation.EFFECT_PLACED.equals(effect)) {
            float pitch = data.contains(CuttingBoardStation.AT) ? 1.15F + 0.05F * data.getInt(CuttingBoardStation.AT) : 1.0F;
            view.playSound(SoundEvents.WOOD_PLACE, "surface", 0.7F, pitch);
        } else if (CuttingBoardStation.EFFECT_REFUSED.equals(effect)) {
            ItemStack refused = ItemStack.parseOptional(view.level().registryAccess(), data.getCompound(CuttingBoardStation.STACK));
            if (!refused.isEmpty()) {
                bounced(view, refused);
            }
            if (pendingChops > 0) {
                pendingChops = 0;
                refresh(view);
            }
            view.playSound(SoundEvents.WOOD_HIT, "surface", 0.4F, 1.4F);
        } else if (CuttingBoardStation.EFFECT_TAKEN.equals(effect)) {
            ItemStack taken = ItemStack.parseOptional(view.level().registryAccess(), data.getCompound(CuttingBoardStation.STACK));
            if (!taken.isEmpty() && data.getBoolean(CuttingBoardStation.CARVED)) {
                pulledOut(view, taken);
            } else if (!taken.isEmpty()) {
                taken(view, taken, data.getInt(CuttingBoardStation.AT));
            }
        } else if (CuttingBoardStation.EFFECT_ASSEMBLED.equals(effect)) {
            List<ItemStack> made = new ArrayList<>();
            ListTag layers = data.getList(CuttingBoardStation.LAYERS, Tag.TAG_COMPOUND);
            for (int i = 0; i < layers.size(); i++) {
                made.add(ItemStack.parseOptional(view.level().registryAccess(), layers.getCompound(i)));
            }
            ItemStack dish = ItemStack.parseOptional(view.level().registryAccess(), data.getCompound(CuttingBoardStation.STACK));
            assembled(view, made, dish);
            book.statsChanged();
        }
    }

    /** A stroke, for everybody who sees the board: bits of what is worked; the last one sends the pieces off to the cook. */
    private void chopped(StationView view, CompoundTag data) {
        ItemStack worked = !shownItem.isEmpty() ? shownItem : !lastShown.isEmpty() ? lastShown : lastCut;
        ToolStroke how = ToolStroke.of(view.level(), worked);
        boolean block = !worked.isEmpty() && ItemVoxels.icon(worked).isEmpty();
        int color = worked.isEmpty() ? 0xFFE0D0A0 : 0xFF000000 | FoodColors.main(worked);
        float x = data.contains(CuttingBoardStation.AT_X) ? data.getFloat(CuttingBoardStation.AT_X) : middleX;
        float top = TOP + (block ? BLOCK_SIZE : ITEM_SIZE / 16.0F);
        Vector3f at = new Vector3f(x, top, data.getFloat(CuttingBoardStation.AT));
        boolean done = data.getBoolean(CuttingBoardStation.DONE);
        if (how == ToolStroke.STRIP) {
            // Bark curls off the log's near side, down along it and out towards the cook.
            view.spawnParticles(DiegesisParticles.DUST, new Vector3f(x, TOP + BLOCK_SIZE * 0.35F, at.z), new Vector3f(0.0F, 1.0F, -3.0F),
                    2.0F, 12, color);
        } else {
            view.spawnParticles(DiegesisParticles.DUST, at, new Vector3f(0.0F, 4.0F, 0.0F), 1.2F, 4, color);
        }
        if (!done) {
            return;
        }
        if (block) {
            FarmCharmBoard.stripped(worked).ifPresent(stripped -> view.node("block_gone").ifPresent(gone -> {
                gone.setVisual(Visual.ofItem(stripped.getItem()).withAnchor(new Vector3f(8.0F, 8.0F, 8.0F)));
                gone.setVisible(true);
                gone.moveTo(0.0F, 0.0F, 0.0F, 0.0F);
                gone.scaleTo(1.0F, 0.0F);
                gone.moveTo(0.0F, 3.0F, 0.0F, 0.3F);
                gone.scaleTo(0.0F, 0.3F);
            }));
        } else {
            view.node("item_gone").ifPresent(gone -> {
                if (lastCut.isEmpty() || ItemVoxels.icon(lastCut).isEmpty()) {
                    return;
                }
                List<ItemVoxels.Cut> spread = new ArrayList<>(lastCuts);
                spread.add(line(lastCut, data.getFloat(CuttingBoardStation.SPOT), data.getFloat(CuttingBoardStation.ANGLE)));
                gone.setShape(ItemVoxels.build(ItemVoxels.icon(lastCut).get(), ITEM_SIZE, spread, GAP * 1.6F), sprite(lastCut));
                gone.setVisible(true);
                gone.moveTo(0.0F, 0.0F, 0.0F, 0.0F);
                gone.scaleTo(1.0F, 0.0F);
                gone.moveTo(0.0F, 2.5F, 0.0F, 0.25F);
                gone.scaleTo(0.0F, 0.25F);
            });
        }
        view.playSound(SoundEvents.ITEM_PICKUP, "item", 0.35F, 1.3F);
    }

    /** Food the board cuts, a tool it cuts with, or what goes into a dish put together on it. */
    private boolean usable(StationView view, ItemStack stack) {
        return BoardLogic.isTool(stack) || FarmCharmBoard.work(view.level(), stack) != FarmCharmBoard.Work.NONE
                || BoardBook.goesIntoADish(view, stack);
    }

    /**
     * The layers press together into their dish: those not on the board yet (a dish picked in the book is laid and made at once) first
     * come from the player one after the other, then they sink into each other and the dish pops up in their place and is taken.
     */
    private void assembled(StationView view, List<ItemStack> made, ItemStack dish) {
        for (int i = 0; i < CuttingBoardStation.MOST_LAYERS; i++) {
            restX[i] = jitterX(i);
            restY[i] = i * LAYER_STEP;
            restZ[i] = jitterZ(i);
        }
        float arrive = arrive(view, made);
        melting = arrive + MELT_TIME + POP_TIME;
        view.node("pile").ifPresent(pile -> pile.setVisible(false));
        if (arrive > 0.0F) {
            after(arrive, () -> pop(view, dish));
        } else {
            pop(view, dish);
        }
    }

    private float arrive(StationView view, List<ItemStack> made) {
        List<ItemStack> layers = made.isEmpty() ? List.copyOf(lastLayers) : made;
        float landed = 0.0F;
        int order = 0;
        for (int i = 0; i < layers.size() && i < CuttingBoardStation.MOST_LAYERS; i++) {
            ItemStack layer = layers.get(i);
            boolean seen = made.isEmpty() || ItemStack.isSameItemSameComponents(shownLayers[i], layer);
            float delay = order * LAYER_STAGGER;
            if (!seen) {
                order++;
                landed = Math.max(landed, delay + PLAYER_LAND_TIME + SETTLE_TIME);
            }
            int index = i;
            view.node("layer_" + i).ifPresent(node -> {
                dress(node, index, layer);
                node.rotateTo(0.0F, turn(index), 0.0F, 0.0F);
                node.scaleTo(1.0F, 0.0F);
                node.setVisible(seen);
                if (seen) {
                    node.moveTo(jitterX(index), 0.0F, jitterZ(index), 0.0F);
                    return;
                }
                after(delay, () -> {
                    node.setVisible(true);
                    landFromPlayer(view, node, jitterX(index), 0.0F, jitterZ(index));
                });
            });
        }
        lastLayers.clear();
        layers.forEach(layer -> lastLayers.add(layer.copy()));
        return landed;
    }

    /** The layers sink into each other, the dish pops up in their place and rises off to the cook. */
    private void pop(StationView view, ItemStack dish) {
        for (int i = 0; i < lastLayers.size(); i++) {
            int index = i;
            float x = restX[i] * 0.4F;
            float y = restY[i] * 0.08F - index * LAYER_STEP;
            float z = restZ[i] * 0.4F;
            view.node("layer_" + i).ifPresent(node -> {
                node.scaleTo(0.1F, MELT_TIME);
                node.moveTo(x, y, z, MELT_TIME);
            });
        }
        if (!dish.isEmpty()) {
            after(MELT_TIME, () -> ghost(view, dish, ITEM_SIZE).ifPresent(node -> {
                float y = rise(dish);
                node.moveTo(0.0F, y, 0.0F, 0.0F);
                node.scaleTo(0.3F, 0.0F);
                node.scaleTo(ItemVoxels.icon(dish).isPresent() ? 1.0F : BLOCK_SCALE, POP_TIME);
                node.hopTo(0.0F, y, 0.0F, POP_HEIGHT, POP_TIME);
                after(POP_TIME, () -> {
                    node.moveTo(0.0F, y + LIFT_HEIGHT + 1.0F, 0.0F, AWAY_TIME);
                    node.scaleTo(0.0F, AWAY_TIME);
                });
                after(POP_TIME + AWAY_TIME, () -> node.setVisible(false));
            }));
        }
        Vector3f middle = new Vector3f(middleX, TOP + 1.0F, middleZ);
        view.spawnParticles(DiegesisParticles.DUST, middle, new Vector3f(0.0F, 3.0F, 0.0F), 2.5F, 12, CHAFF);
        view.spawnParticles(DiegesisParticles.GLINT, middle, new Vector3f(0.0F, 2.0F, 0.0F), 2.0F, 5, 0xFFFFE9A0);
        view.playSound(SoundEvents.WOOD_PLACE, "surface", 0.8F, 0.75F);
    }

    private void dress(NodeView node, int index, ItemStack layer) {
        if (ItemVoxels.icon(layer).isPresent()) {
            node.setVisual(null);
            node.setShape(shape(layer, index == 0 ? ITEM_SIZE : LAYER_SIZE, List.of(), 0.0F), sprite(layer));
        } else {
            // A block among the layers (hay, a log) lies as the item it is, small.
            node.setShape(null, null);
            node.setVisual(Visual.ofItem(layer.getItem()).withAnchor(new Vector3f(8.0F, 8.0F, 8.0F)));
        }
    }

    @Override
    public void tick(StationView view) {
        if (!Float.isNaN(melting)) {
            melting -= 0.05F;
            if (melting <= 0.0F) {
                melting = Float.NaN;
                for (int i = 0; i < CuttingBoardStation.MOST_LAYERS; i++) {
                    shownLayers[i] = ItemStack.EMPTY;
                    view.node("layer_" + i).ifPresent(node -> {
                        node.scaleTo(1.0F, 0.0F);
                        node.setVisible(false);
                    });
                }
            }
        }
        refresh(view);
        view.node("item_gone").filter(gone -> gone.isVisible() && !gone.isMoving()).ifPresent(gone -> gone.setVisible(false));
        view.node("block_gone").filter(gone -> gone.isVisible() && !gone.isMoving()).ifPresent(gone -> gone.setVisible(false));
    }
}
