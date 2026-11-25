package com.pockyl.handson_letsdo.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.state.TankSpec;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_letsdo.HandsOnConfig;
import com.pockyl.handson_letsdo.HandsOnLetsDo;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Vinery's apple press as a station: the cook opens the basket's front, lays fruit into it and shuts it, and presses the screw's cap:
 * every press drives the plate further down on the fruit, and the last of a load's presses presses up to eight fruits at once. The
 * mash lies before it and the juice runs off the plate into the wooden tub under its lip quickly, from which the cook fills empty wine
 * bottles with a click on it. Left alone, the press does what it always does, in its own time, only with its basket shut. Every client near it shows that, with nobody at it
 * too: the press tells them ({@link PressBasket}) and each runs the logic on its copy. Its slots are windows on the press's own inventory: hoppers reach it and it keeps its items
 * whoever is at it. Both of its blocks are hidden while somebody works at it and the scene shows the same press, open.
 */
public final class PressStation {
    public static final ResourceLocation ID = HandsOnLetsDo.id("apple_press");

    /** The fruit in the basket. */
    public static final String FRUIT = "fruit";
    /** What it is pressed to; the press bottles it, and it can be taken as it is. */
    public static final String MASH = "mash";
    /** The press's own slot for empty wine bottles: for hoppers and its menu, not shown at the station. */
    public static final String BOTTLES = "bottles";
    /** What is bottled in the press's own way, in bottles a hopper or its menu laid in. */
    public static final String SERVED = "served";
    /** The tub under the lip: how many portions it holds, of the drink whose item id is the tank's liquid. */
    public static final String TUB = "tub";
    public static final SlotRef FRUIT_SLOT = SlotRef.of(FRUIT, 0);
    public static final SlotRef MASH_SLOT = SlotRef.of(MASH, 0);
    public static final SlotRef BOTTLE_SLOT = SlotRef.of(BOTTLES, 0);
    public static final SlotRef SERVED_SLOT = SlotRef.of(SERVED, 0);

    /**
     * What the logic tells the clients in the station's data: for the pressing and for the bottling, the game time the one that
     * runs began at (absent while it does not run) and the ticks one takes (Vinery's settings); the item the press makes of
     * what is in it in the end, absent for nothing.
     */
    public static final String MASH_STARTED = "mash_started";
    public static final String MASH_TOTAL = "mash_total";
    public static final String BOTTLE_STARTED = "bottle_started";
    public static final String BOTTLE_TOTAL = "bottle_total";
    public static final String MAKING = "making";
    /** Whether the basket's front stands open, as the press keeps it. */
    public static final String OPEN = "open";
    /**
     * Pressed by hand: how many times the screw's cap was pressed on the load in the basket, and whether a cook works at the press
     * (its own pressing waits for the hand meanwhile).
     */
    public static final String PRESSES = "presses";
    public static final String BY_HAND = "by_hand";

    /**
     * A custom action: something picked in the book; its fruit and its bottles go in from the inventory, for one or with
     * {@link #ALL} for as many as fit. Data: {@link #STEP}, the id of the recipe that makes it.
     */
    public static final String ACTION_FILL = "fill";
    public static final String STEP = "step";
    /**
     * A custom action: empty wine bottles are filled at the tub, one or with {@link #ALL} as many as it holds: the ones on the
     * cursor, those of inventory slot {@link #FROM}, or with an empty hand the inventory's.
     */
    public static final String ACTION_DRAW = "draw";
    public static final String FROM = "from";
    /** A custom action: the cook opens or shuts the basket's front. Data: {@link #OPEN}, how it is to be. */
    public static final String ACTION_DOORS = "doors";
    /** A custom action: the cook presses the screw's cap once; an open basket is shut first. */
    public static final String ACTION_PRESS = "press";
    public static final String ALL = "all";

    /** Played for everybody who sees it: a fruit is pressed to mash. */
    public static final ResourceLocation EFFECT_PRESSED = HandsOnLetsDo.id("press_pressed");
    /** Played for everybody who sees it: a bottle's worth ran into the tub, or a bottle was filled. Data: {@link #VESSEL}, what. */
    public static final ResourceLocation EFFECT_BOTTLED = HandsOnLetsDo.id("press_bottled");
    /** Played for everybody who sees it: the cook pressed the screw's cap and nothing was left to press under it. */
    public static final ResourceLocation EFFECT_NOTHING = HandsOnLetsDo.id("press_nothing");
    /** Played for everybody who sees it: bottles were filled at the tub. Data: {@link #VESSEL}, {@link #COUNT}. */
    public static final ResourceLocation EFFECT_DRAWN = HandsOnLetsDo.id("press_drawn");
    public static final String COUNT = "count";
    public static final String VESSEL = "vessel";
    public static final String BASKET = "basket";
    public static final String LIP = "lip";

    @SuppressWarnings("removal")
    public static final Supplier<Block> PRESS = () -> BuiltInRegistries.BLOCK.get(new ResourceLocation(BarrelStation.MOD_ID, "apple_press"));

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(FRUIT, 1, SlotRules.DEFAULT.kept())
            .slots(MASH, 1, SlotRules.DEFAULT.kept())
            .slots(BOTTLES, 1, SlotRules.DEFAULT.kept().accepts(BarrelStation::isWineBottle))
            .slots(SERVED, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .tank(TUB, TankSpec.of(Press.TUB_MOST))
            .build();

    /**
     * The press faces whoever set it down: the scene's front is its front. It lives at its lower half, where it keeps its things;
     * its upper half opens the same station and is hidden with it.
     */
    public static final StationType TYPE = Diegesis.station(ID)
            .block(PRESS)
            .anchor((level, pos, state) -> Press.lower(pos, state))
            .schema(SCHEMA)
            .logic(PressLogic::new)
            .orientation(StationType.Orientation.BLOCK)
            .openWhen(PressStation::opens)
            .hidesBlocks(PressStation::upperHalf)
            .idleLogic()
            .build();

    private PressStation() {
    }

    /** With Vinery installed and the station left on in the config; otherwise the press keeps Vinery's menu. */
    public static void register() {
        if (HandsOnLetsDo.has(BarrelStation.MOD_ID) && HandsOnConfig.applePress()) {
            Diegesis.register(TYPE);
        }
    }

    /** The block over the press's lower half at {@code pos}, while it is the press's upper half. */
    private static Collection<BlockPos> upperHalf(BlockGetter level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        return above.is(PRESS.get()) && Press.isUpper(above) ? List.of(pos.above()) : List.of();
    }

    /** Sneaking keeps Vinery's own use, its menu. A press that is not the one this mod knows keeps its menu too. */
    private static boolean opens(Player player, BlockPos pos, BlockState state) {
        return !player.isSecondaryUseActive() && Press.of(player.level().getBlockEntity(Press.lower(pos, state))) != null;
    }
}
