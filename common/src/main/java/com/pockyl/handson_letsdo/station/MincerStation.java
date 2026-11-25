package com.pockyl.handson_letsdo.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
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
 * Farm &amp; Charm's mincer as a station: the cook lays what is to be minced on the hopper, takes the crank by its grip and turns it
 * round, and what comes out of the die lies on the mincer's foot under the spout until it is taken. It minces in loads: up to
 * {@link #LOAD} of what lies on the hopper become their results together after {@link #TURNS_PER_LOAD} whole turns. Every result is
 * made by the mincer's own tick. The scene is the mincer in the world too, wherever it is drawn: the hopper and the foot show what
 * lies on them with nobody at it.
 */
public final class MincerStation {
    public static final ResourceLocation ID = HandsOnLetsDo.id("mincer");

    /** What is to be minced, on the hopper: the mincer's own input slot. */
    public static final String INPUT = "input";
    /** What came out, on the foot under the spout: the mincer's own output slot, kept there while it waits. */
    public static final String MINCED = "minced";
    public static final SlotRef INPUT_SLOT = SlotRef.of(INPUT, 0);
    public static final SlotRef MINCED_SLOT = SlotRef.of(MINCED, 0);

    /** Items minced together, and the whole turns of the crank they take. */
    public static final int LOAD = 8;
    public static final int TURNS_PER_LOAD = 2;
    /** The crank's way round for one load, radians. */
    public static final float LOAD_TURN = Mth.TWO_PI * TURNS_PER_LOAD;
    /**
     * The crank turns no faster than a whole turn in so many ticks with nothing to grind, and so many more under the heaviest load: the
     * blade has to get through what is on it.
     */
    public static final float FASTEST_TURN_TICKS = 6.0F;
    public static final float HEAVY_TURN_TICKS = 8.0F;

    /**
     * What the logic tells the clients in the station's data: how far the load on the hopper is turned, 0 to 1; how many items it
     * is; the item they become, absent for nothing; the crank's angle as the cook turns it, radians.
     */
    public static final String TURNED = "turned";
    public static final String LOAD_SIZE = "load";
    public static final String MAKING = "making";
    public static final String CRANK = "crank";
    /** What the load on the hopper is to grind, Farm &amp; Charm's class of its recipe ({@code MEAT}, {@code WOOD}, ...); absent for nothing. */
    public static final String GRINDS = "grinds";

    /** A custom action: the cook turns the crank; data: its angle now, {@link #ANGLE}, radians round its axle. */
    public static final String ACTION_CRANK = "crank";
    public static final String ANGLE = "angle";
    /**
     * A custom action: something picked in the book goes onto the hopper from the inventory, a load or with {@link #ALL} a stack.
     * Data: {@link #RECIPE}, the id of the recipe that makes it.
     */
    public static final String ACTION_FILL = "fill";
    public static final String RECIPE = "recipe";
    public static final String ALL = "all";

    /** Played for everybody who sees it: a load came out of the die. Data: {@link #ITEM}, what, {@link #KIND}, what was ground. */
    public static final ResourceLocation EFFECT_MINCED = HandsOnLetsDo.id("mincer_minced");
    public static final String ITEM = "item";
    public static final String KIND = "kind";
    public static final String DIE = "die";

    @SuppressWarnings("removal")
    public static final Supplier<Block> MINCER = () -> BuiltInRegistries.BLOCK.get(new ResourceLocation(FarmCharmDishes.MOD_ID, "mincer"));

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(INPUT, 1, SlotRules.DEFAULT.kept())
            .slots(MINCED, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .build();

    /**
     * The mincer's facing names its back (the way its placer looked): the scene's front is its front, as Farm &amp; Charm draws it.
     */
    public static final StationType TYPE = Diegesis.station(ID)
            .block(MINCER)
            .schema(SCHEMA)
            .logic(MincerLogic::new)
            .orientation(StationType.Orientation.BLOCK_OPPOSITE)
            .openWhen(MincerStation::opens)
            .hidesBlock()
            .idleLogic()
            .build();

    private MincerStation() {
    }

    /** With Farm &amp; Charm installed and the station left on in the config; otherwise the mincer is cranked Farm &amp; Charm's way. */
    public static void register() {
        if (HandsOnLetsDo.has(FarmCharmDishes.MOD_ID) && HandsOnConfig.mincer()) {
            Diegesis.register(TYPE);
        }
    }

    /** Whether the cook's own use reaches the mincer: sneaking empties it Farm &amp; Charm's way; a mincer this mod does not know too. */
    private static boolean opens(Player player, BlockPos pos, BlockState state) {
        return !player.isSecondaryUseActive() && Mincer.of(player.level().getBlockEntity(pos)) != null;
    }

    /**
     * How heavy the crank turns with {@code items} of a load of Farm &amp; Charm's recipe class {@code kind} on the hopper: 0 for nothing,
     * up to 1 for a full load of metal. Meat gives easily, wood and stone less, metal least; more items, more to get through.
     */
    public static float heaviness(String kind, int items) {
        if (items <= 0) {
            return 0.0F;
        }
        float weight = switch (kind) {
            case "MEAT" -> 0.25F;
            case "WOOD" -> 0.55F;
            case "STONE" -> 0.75F;
            case "METAL" -> 1.0F;
            default -> 0.45F;
        };
        return weight * (0.4F + 0.6F * Math.min(items, LOAD) / LOAD);
    }

    /** The fewest ticks a whole turn of the crank takes under a load as heavy as {@code heaviness}. */
    public static float turnTicks(float heaviness) {
        return FASTEST_TURN_TICKS + HEAVY_TURN_TICKS * Mth.clamp(heaviness, 0.0F, 1.0F);
    }

    /** Whether {@code entity} is a mincer the station knows: its block entity renderer draws what the scene draws. */
    public static boolean isMincer(BlockEntity entity) {
        return entity instanceof MincerHand && entity.getBlockState().is(MINCER.get());
    }
}
