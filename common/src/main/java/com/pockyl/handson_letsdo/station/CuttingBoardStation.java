package com.pockyl.handson_letsdo.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_letsdo.HandsOnConfig;
import com.pockyl.handson_letsdo.HandsOnLetsDo;

import java.util.function.Supplier;

/**
 * Farm &amp; Charm's cutting board (1.21.1) as a station, Hands-On Delight's board brought to it: food lies on the board and is cut on
 * dashed marks with the knife, a log stripped with an axe, a dish put together layer by layer as the board's own recipes have it.
 * Nothing new is made: the cuts and the dishes are the board's recipes, their results and wear as the board gives them.
 */
public final class CuttingBoardStation {
    public static final ResourceLocation ID = HandsOnLetsDo.id("cutting_board");

    /** What lies on the board to be cut: the board's one stack, or what its stuck tool was cutting. */
    public static final String BOARD = "board";
    /** What a dish is being put together from, bottom first: the board's own list. */
    public static final String LAYERS = "layers";
    /** The tool stuck into the board, which cuts at the station too. */
    public static final String KNIFE = "knife";
    /** Where a stack let go on the board is caught: one slot that never keeps anything (see Hands-On Delight's board). */
    public static final String HAND = "hand";
    public static final int MOST_LAYERS = 5;
    public static final SlotRef BOARD_SLOT = SlotRef.of(BOARD, 0);
    public static final SlotRef KNIFE_SLOT = SlotRef.of(KNIFE, 0);

    /** How many items of the stack one cut goes through, and how many dishes one stack of layers makes at most. */
    public static final int BATCH = 16;

    /** Custom actions, as at Hands-On Delight's board. */
    public static final String ACTION_PLACE = "place";
    public static final String ACTION_CARVE = "carve";
    public static final String ACTION_CHOP = "chop";
    public static final String ACTION_TAKE = "take";
    public static final String ACTION_ASSEMBLE = "assemble";
    public static final String ACTION_PICK = "pick";
    public static final String BY_USE = "by_use";
    public static final String ALL = "all";
    public static final String ONE = "one";
    public static final String FROM = "from";
    public static final String AT = "at";
    public static final String AT_X = "at_x";
    public static final String MARK = "mark";

    /** Data the logic tells the clients: the dish the layers make, how many marks are cut, where the marks lie and how. */
    public static final String DISH = "dish";
    public static final String MARKS = "marks";
    public static final String SPOTS = "spots";
    public static final String ANGLES = "angles";
    public static final String SPOT = "spot";
    public static final String ANGLE = "angle";

    public static final ResourceLocation EFFECT_CHOPPED = HandsOnLetsDo.id("board_chopped");
    public static final ResourceLocation EFFECT_PLACED = HandsOnLetsDo.id("board_placed");
    public static final ResourceLocation EFFECT_REFUSED = HandsOnLetsDo.id("board_refused");
    public static final ResourceLocation EFFECT_ASSEMBLED = HandsOnLetsDo.id("board_assembled");
    public static final ResourceLocation EFFECT_TAKEN = HandsOnLetsDo.id("board_taken");
    public static final ResourceLocation EFFECT_CARVED = HandsOnLetsDo.id("board_carved");
    public static final String CARVED = "carved";
    public static final String STACK = "stack";
    public static final String DONE = "done";
    public static final String TOOL = "tool";
    public static final String COUNT = "count";

    public static final Supplier<Block> CUTTING_BOARD = () -> BuiltInRegistries.BLOCK.get(
            ResourceLocation.fromNamespaceAndPath(FarmCharmDishes.MOD_ID, "cutting_board"));

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(BOARD, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .slots(LAYERS, MOST_LAYERS, SlotRules.DEFAULT.maxStack(BATCH).kept().accepts(stack -> false))
            .slots(KNIFE, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .slots(HAND, 1, SlotRules.DEFAULT)
            .build();

    /** Opens from any side, turned to the cook; what lies there stays on the board's base, its handle beside it. */
    public static final StationType TYPE = Diegesis.station(ID)
            .block(CUTTING_BOARD)
            .schema(SCHEMA)
            .logic(BoardLogic::new)
            .orientation(StationType.Orientation.PLAYER)
            .openWhen(CuttingBoardStation::opens)
            .idleLogic()
            .build();

    private CuttingBoardStation() {
    }

    /** With Farm &amp; Charm's board (its 1.21.1 builds) and the station left on in the config. */
    public static void register() {
        if (HandsOnLetsDo.has(FarmCharmDishes.MOD_ID) && HandsOnConfig.cuttingBoard() && hasBoard()) {
            Diegesis.register(TYPE);
        }
    }

    /** Whether this Farm &amp; Charm has the board (it came in autumn 2026): an older one keeps its blocks as they are. */
    private static boolean hasBoard() {
        try {
            Class.forName("net.satisfy.farm_and_charm.core.block.entity.CuttingBoardBlockEntity", false,
                    CuttingBoardStation.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** Sneaking keeps the board's own use; anything else opens the station. */
    public static boolean opens(Player player, BlockPos pos, BlockState state) {
        return !player.isSecondaryUseActive() && FarmCharmBoard.of(player.level().getBlockEntity(pos)) != null;
    }

    /** Whether {@code entity} is the board this station draws. */
    public static boolean isBoard(BlockEntity entity) {
        return entity instanceof BoardItems && entity.getBlockState().is(CUTTING_BOARD.get());
    }
}
