package com.pockyl.handson_letsdo.station;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_letsdo.HandsOnConfig;
import com.pockyl.handson_letsdo.HandsOnLetsDo;

/**
 * An oven as a station, Hands-On Delight's shared oven: its door opens, the tray slides out with what bakes laid on it, the tray
 * goes back in and it bakes as the oven always does. One station for every oven ({@link KitchenOven}); each oven has its type and
 * scene. Farm &amp; Charm's stove ({@link #STOVE_TYPE}): the iron plate over its mouth drops, its grate slides out with three
 * places, the dish that is baked lies on the grate, and the fuel burns in the firebox under it.
 */
public final class OvenStation {
    public static final ResourceLocation STOVE_ID = HandsOnLetsDo.id("stove");

    public static final String INGREDIENTS = "ingredients";
    /** What is baked waits in the oven's output slots. */
    public static final String SERVED = "served";
    /** The oven's fuel, for an oven that burns it. */
    public static final String FUEL = "fuel";
    /** The tool places on top of an oven that has them. */
    public static final String TOOLS = "tools";
    public static final String PROGRESS = "progress";
    /**
     * Whether the door is open (1) or shut (0). The cook opens and shuts it with a click on it ({@link #ACTION_DOOR}); things go on the
     * tray and come off it only with it open.
     */
    public static final String DOOR = "door";
    public static final String ACTION_DOOR = "door";
    public static final String OPEN = "open";
    /** A custom action: a recipe picked in the book; its ingredients go in from the inventory. Data: {@link #DISH}, {@link #ALL}. */
    public static final String ACTION_FILL = "fill";
    public static final String DISH = "dish";
    public static final String ALL = "all";
    /**
     * A custom action: the stack on the cursor, or the one in an inventory slot ({@link #FROM}), is laid on the tray: where alike lies
     * or into free places. {@link #PLACE}: onto that place only, one ({@link #ONE}); {@link #ALL}: the inventory's alike things
     * follow once the stack is laid.
     */
    public static final String ACTION_LAY = "lay";
    public static final String FROM = "from";
    public static final String PLACE = "place";
    public static final String ONE = "one";
    /**
     * A custom action: fuel goes into the oven, the stack on the cursor, or with an empty hand ({@link #FROM_INVENTORY}) a stack of fuel
     * from the inventory; one of it with {@link #ONE}.
     */
    public static final String ACTION_FUEL = "fuel";
    public static final String FROM_INVENTORY = "from_inventory";
    /**
     * What the logic tells the clients in the station's data: the ticks until the next thing is done ({@link #LEFT}), how far each
     * place has baked in hundredths ({@link #BAKED}), whether the oven is hot ({@link #HOT}) and whether its things wait for heat
     * ({@link #COLD}).
     */
    public static final String LEFT = "left";
    public static final String BAKED = "baked";
    public static final String HOT = "hot";
    public static final String COLD = "cold";
    public static final SlotRef FUEL_SLOT = SlotRef.of(FUEL, 0);

    public static final StationSchema STOVE_SCHEMA = schema(FarmCharmStove.PLACES, 64, 1, true, 0);

    /**
     * Farm &amp; Charm's stove. Its block is hidden while somebody works at it and the scene shows it with its mouth open; while
     * nobody does, the block and its own renderer are the stove, and each client near it reads it with its own copy of the logic
     * for the puff of steam when something is done.
     */
    public static final StationType STOVE_TYPE = Diegesis.station(STOVE_ID)
            .block(FarmCharmStove.BLOCK)
            .schema(STOVE_SCHEMA)
            .logic(OvenLogic::new)
            // Worked from its mouth: the scene stands as the stove faces, whichever side the cook comes from.
            .orientation(StationType.Orientation.BLOCK)
            .openWhen(OvenStation::opensStove)
            .hidesBlock()
            .idleLogic()
            .build();

    private OvenStation() {
    }

    /** The slots of an oven with {@code places} places holding {@code perPlace} each, its outputs, fuel and tool places. */
    public static StationSchema schema(int places, int perPlace, int outputs, boolean fuel, int tools) {
        StationSchema.Builder builder = StationSchema.builder()
                .slots(INGREDIENTS, places, SlotRules.DEFAULT.kept().maxStack(perPlace))
                .slots(SERVED, outputs, SlotRules.DEFAULT.kept().accepts(stack -> false));
        if (fuel) {
            builder.slots(FUEL, 1, SlotRules.DEFAULT.kept());
        }
        if (tools > 0) {
            builder.slots(TOOLS, tools, SlotRules.DEFAULT.kept().maxStack(1));
        }
        return builder
                .control(PROGRESS, ControlSpec.stepped(0.0F, 1.0F, 100, 0.0F).locked())
                .control(DOOR, ControlSpec.stepped(0.0F, 1.0F, 1, 0.0F).locked())
                .build();
    }

    /** With Farm &amp; Charm installed and the station left on in the config; otherwise the stove keeps its own menu. */
    public static void register() {
        if (HandsOnLetsDo.has(FarmCharmDishes.MOD_ID) && HandsOnConfig.stove()) {
            Diegesis.register(STOVE_TYPE);
        }
    }

    /** Sneaking keeps the stove's own use, its menu. A stove that is not the one this mod knows keeps its menu too. */
    private static boolean opensStove(Player player, BlockPos pos, BlockState state) {
        return !player.isSecondaryUseActive() && FarmCharmStove.of(player.level().getBlockEntity(pos)) != null;
    }
}
