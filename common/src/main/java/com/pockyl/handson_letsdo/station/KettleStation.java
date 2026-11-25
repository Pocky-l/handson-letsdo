package com.pockyl.handson_letsdo.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.state.TankSpec;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_letsdo.HandsOnConfig;
import com.pockyl.handson_letsdo.HandsOnLetsDo;

import java.util.function.Supplier;

/**
 * HerbalBrews' tea kettles (iron and copper) as a station, as Hands-On Delight's kettle: the cook takes the lid off with a click,
 * pours water in from a bucket or a bottle, drops leaves and sugar into it, which colour it, and lays what feeds the heat at the
 * kettle's foot; the kettle brews in its own time, one drink of each set of leaves, and the drinks wait in it
 * ({@link KettleDrinks}) until the cook pours them into bottles at the spout. Its slots are windows on the kettle's own
 * inventory: hoppers reach it and it keeps its items whoever is at it. The block is hidden while somebody brews and the scene
 * shows the same kettle, open. Every client near a kettle nobody is at runs the logic on its copy of the kettle (the kettle tells
 * them what it keeps), so that it steams, boils and whistles there too.
 */
public final class KettleStation {
    public static final ResourceLocation ID = HandsOnLetsDo.id("tea_kettle");

    public static final String INGREDIENTS = "ingredients";
    /** The bottles the drinks are brewed into, under the spout. */
    public static final String BOTTLES = "bottles";
    /** The drinks that are done. */
    public static final String SERVED = "served";
    /** What feeds the heat: the kettle burns one whenever it has cooled enough. */
    public static final String EMBERS = "embers";
    /** A bucket or bottle of water that waits to be poured in: the kettle pours it on its next tick. Nothing is put here by hand. */
    public static final String WAITING = "waiting";
    public static final SlotRef BOTTLE_SLOT = SlotRef.of(BOTTLES, 0);
    public static final SlotRef SERVED_SLOT = SlotRef.of(SERVED, 0);
    public static final SlotRef EMBER_SLOT = SlotRef.of(EMBERS, 0);
    public static final SlotRef WAITING_SLOT = SlotRef.of(WAITING, 0);

    /** The water in the kettle, of {@link Kettle#FULL}. */
    public static final String WATER = "water";
    public static final ResourceLocation WATER_LIQUID = HandsOnLetsDo.id("water");

    /**
     * What the logic tells the clients in the station's data: how hot the kettle is, of {@link Kettle#FULL}, and whether it
     * stands on something hot; the drink its contents make, absent for none, with the water and the heat a bottle of it needs
     * and the ticks it takes; the game time the bottle that brews began at, absent while none does, and the ticks the kettle
     * counts to for it.
     */
    public static final String HEAT = "heat";
    public static final String HEATED = "heated";
    public static final String MAKING = "making";
    public static final String NEEDS_WATER = "needs_water";
    public static final String NEEDS_HEAT = "needs_heat";
    public static final String STARTED = "started";
    public static final String TOTAL = "total";
    /**
     * Whether a bottle brews now. The server tells its start ({@link #STARTED}); a client near a kettle nobody is at knows only this,
     * from its copy of the kettle.
     */
    public static final String BREWING = "brewing";
    /** The drink that waits in the kettle (absent for none), and how many bottles of it. */
    public static final String DRINK = "drink";
    public static final String DRINKS = "drinks";

    /**
     * Whether the lid is off (1) or on (0). The cook takes it off and puts it back with a click on it ({@link #ACTION_LID}); only
     * with it off does anything go in at the top. The last cook to go puts it back on.
     */
    public static final String LID = "lid";
    /** A custom action: the lid is taken off or put on. Data: {@link #OPEN}. */
    public static final String ACTION_LID = "lid";
    public static final String OPEN = "open";
    /**
     * A custom action: drinks poured at the spout into the glass bottle on the cursor ({@link #FROM}: in that inventory slot; with
     * {@link #ANY}, the first in the inventory), one, or with {@link #ALL} as many as wait and there are bottles.
     */
    public static final String ACTION_DRAW = "draw";
    public static final String ANY = "any";

    /**
     * A custom action: the bucket or bottle of water on the cursor is poured in at the opening; {@link #FROM}: the stack in that
     * inventory slot instead of the cursor's.
     */
    public static final String ACTION_POUR = "pour";
    public static final String FROM = "from";
    /**
     * A custom action: a drink picked in the book; what it is made of, its bottles and its water go in from the inventory, for
     * one bottle or with {@link #ALL} for as many as fit. Data: {@link #TEA}, the recipe's id.
     */
    public static final String ACTION_FILL = "fill";
    public static final String TEA = "tea";
    public static final String ALL = "all";

    /** Played for everybody who sees it: water poured in at the opening. Data: {@link #VESSEL}, what it was poured from. */
    public static final ResourceLocation EFFECT_POURED = HandsOnLetsDo.id("kettle_poured");
    /** Played for everybody who sees it: a bottle is brewed and filled at the spout. Data: {@link #VESSEL}, the drink; {@link #COUNT}. */
    public static final ResourceLocation EFFECT_BREWED = HandsOnLetsDo.id("kettle_brewed");
    /** Played for everybody who sees it: drinks poured at the spout. Data: {@link #VESSEL}, the drink; {@link #COUNT}. */
    public static final ResourceLocation EFFECT_DRAWN = HandsOnLetsDo.id("kettle_drawn");
    /** Played for everybody who sees it: the kettle burned one of what feeds its heat. */
    public static final ResourceLocation EFFECT_FLARED = HandsOnLetsDo.id("kettle_flared");
    public static final String VESSEL = "vessel";
    public static final String COUNT = "count";
    public static final String MOUTH = "mouth";
    public static final String SPOUT = "spout";
    public static final String FIRE = "fire";

    public static final Supplier<Block> IRON = block("tea_kettle");
    public static final Supplier<Block> COPPER = block("copper_tea_kettle");

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(INGREDIENTS, Kettle.INGREDIENTS, SlotRules.DEFAULT.kept().accepts(stack -> !isBottle(stack) && !isEmber(stack)
                    && HerbalTeas.waterOf(stack) == 0))
            .slots(BOTTLES, 1, SlotRules.DEFAULT.kept().accepts(KettleStation::isBottle))
            .slots(SERVED, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .slots(EMBERS, 1, SlotRules.DEFAULT.kept().accepts(KettleStation::isEmber))
            .slots(WAITING, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .tank(WATER, TankSpec.of(Kettle.FULL))
            .control(LID, ControlSpec.stepped(0.0F, 1.0F, 1, 0.0F).locked())
            .build();

    /**
     * The kettle's facing names the way its placer looked, with the spout on their left: the scene's front is the side they
     * stood on, so the kettle is seen as it was set down, the spout on the left and the bottles under it.
     */
    public static final StationType TYPE = Diegesis.station(ID)
            .block(IRON)
            .block(COPPER)
            .schema(SCHEMA)
            .logic(KettleLogic::new)
            .orientation(StationType.Orientation.BLOCK_OPPOSITE)
            .openWhen(KettleStation::opens)
            .hidesBlock()
            .idleLogic()
            .build();

    private static boolean registered;

    private KettleStation() {
    }

    /** With HerbalBrews installed and the station left on in the config; otherwise the kettles keep their own menu. */
    public static void register() {
        if (HandsOnLetsDo.has(HerbalTeas.MOD_ID) && HandsOnConfig.teaKettle()) {
            Diegesis.register(TYPE);
            registered = true;
        }
    }

    /** Whether the kettles brew into themselves ({@link KettleDrinks}): while the station is on. */
    public static boolean brewsIntoItself() {
        return registered;
    }

    /** Whether the lid is off. */
    public static boolean isOpen(StationState state) {
        return state.getControl(LID) > 0.5F;
    }

    private static Supplier<Block> block(String path) {
        return () -> BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(HerbalTeas.MOD_ID, path));
    }

    public static boolean isBottle(ItemStack stack) {
        return !stack.isEmpty() && stack.is(HerbalTeas.BOTTLES);
    }

    public static boolean isEmber(ItemStack stack) {
        return !stack.isEmpty() && stack.is(HerbalTeas.HEAT_ITEMS);
    }

    /** How much water stands in the kettle, of {@link Kettle#FULL}. */
    public static int water(StationState state) {
        return state.getTank(WATER).amount();
    }

    /**
     * Whether {@code stack} can be poured in now: water in a bucket or a bottle, into a kettle that is not full and has no
     * water waiting to be poured.
     */
    public static boolean pourable(StationState state, ItemStack stack) {
        return HerbalTeas.waterOf(stack) > 0 && water(state) < Kettle.FULL && HerbalTeas.waterOf(state.getItem(WAITING_SLOT)) == 0;
    }

    /** Sneaking keeps the kettle's own use, its menu. A kettle that is not the one this mod knows keeps its menu too. */
    private static boolean opens(Player player, BlockPos pos, BlockState state) {
        return !player.isSecondaryUseActive() && Kettle.of(player.level().getBlockEntity(pos)) != null;
    }
}
