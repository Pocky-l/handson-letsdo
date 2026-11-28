package com.pockyl.handson_letsdo.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_letsdo.HandsOnConfig;
import com.pockyl.handson_letsdo.HandsOnLetsDo;

import java.util.function.Supplier;

/**
 * Wok &amp; Bowl's wok (1.21.1) as a station: food thrown in one by one, the wok taken by a handle and tossed with a flick when it
 * calls, the sauce poured and the flambé struck, the dishes served and taken out, all by the wok's own rules.
 */
public final class WokStation {
    public static final ResourceLocation ID = HandsOnLetsDo.id("wok");

    /** Where a stack let go over the wok is caught: one slot that never keeps anything (the logic hands it to the wok). */
    public static final String HAND = "hand";

    /** {@code place}: one of the stack on the cursor, or of inventory slot {@link #FROM}, goes into the wok as it takes it. */
    public static final String ACTION_PLACE = "place";
    /** {@code toss}: the cook's flick; the wok's empty hand while it cooks (a toss when called, serving when ready). */
    public static final String ACTION_TOSS = "toss";
    /** {@code take}: a dish out, burnt rice dumped: the wok's empty hand while it does not cook. */
    public static final String ACTION_TAKE = "take";
    /** {@code fill}: what the recipe {@link #RECIPE} still lacks, in its order, from the inventory. */
    public static final String ACTION_FILL = "fill";
    public static final String FROM = "from";
    public static final String BY_USE = "by_use";
    public static final String RECIPE = "recipe";

    /** Played for everybody who sees it: something went in (data {@link #STACK}), or the wok would not have it. */
    public static final ResourceLocation EFFECT_ADDED = HandsOnLetsDo.id("wok_added");
    public static final ResourceLocation EFFECT_REFUSED = HandsOnLetsDo.id("wok_refused");
    public static final String STACK = "stack";

    public static final Supplier<Block> WOK = () -> BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(WokAndBowlWok.MOD_ID, "wok"));

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(HAND, 1, SlotRules.DEFAULT)
            .build();

    /** Turned to the cook; the scene turns the wok back to its own facing. */
    public static final StationType TYPE = Diegesis.station(ID)
            .block(WOK)
            .schema(SCHEMA)
            .logic(WokLogic::new)
            .orientation(StationType.Orientation.PLAYER)
            .openWhen(WokStation::opens)
            .idleLogic()
            .build();

    private WokStation() {
    }

    public static void register() {
        if (HandsOnLetsDo.has(WokAndBowlWok.MOD_ID) && HandsOnConfig.wok()) {
            Diegesis.register(TYPE);
        }
    }

    /** Sneaking, or with the bamboo steamer on, the wok is used as Wok &amp; Bowl has it. */
    public static boolean opens(Player player, BlockPos pos, BlockState state) {
        return !player.isSecondaryUseActive() && !WokAndBowlWok.hasSteamer(state) && WokAndBowlWok.of(player.level().getBlockEntity(pos)) != null;
    }

    public static boolean isWok(BlockEntity entity) {
        return entity.getBlockState().is(WOK.get());
    }
}
