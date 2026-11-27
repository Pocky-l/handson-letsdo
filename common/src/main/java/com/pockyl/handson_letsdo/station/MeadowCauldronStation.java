package com.pockyl.handson_letsdo.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
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

import java.util.function.Supplier;

/**
 * Meadow's cooking cauldron as a station, as this mod's Farm &amp; Charm pot and HerbalBrews' cauldron: the cook pours water in from
 * a bucket or a bottle, drops the food in, where it floats, and the cauldron cooks it over the heat under it (or its own fire, hung in
 * its frame) into one dish in Meadow's time; the cook stirs with a ladle to hurry it and takes the dish out of the water. Its slots
 * are windows on the cauldron's own inventory: hoppers reach it and it keeps its items whoever is at it, and the dish is made by
 * Meadow's own tick. The block is hidden wherever the scene is drawn and the scene shows the same cauldron: every client near it
 * runs the logic on its copy of the cauldron (the cauldron tells them what it holds), so that the water, the food and the dish show
 * in the world too. A standing and a hanging cauldron are the same station in two scenes: the hanging one hangs higher in its frame.
 */
public final class MeadowCauldronStation {
    public static final ResourceLocation ID = HandsOnLetsDo.id("meadow_cooking_cauldron");
    public static final ResourceLocation HANGING_ID = HandsOnLetsDo.id("meadow_cooking_cauldron_hanging");

    /** The dish, the cauldron's own slot 0: it floats up in the middle once it is cooked, and is taken ({@link #ACTION_TAKE}). */
    public static final String SERVED = "served";
    public static final SlotRef SERVED_SLOT = SlotRef.of(SERVED, 0);
    /** The food, the cauldron's own slots 1 to 6: afloat on the water. */
    public static final String INGREDIENTS = "ingredients";
    /** The water, the cauldron's own count (0 to 100) as the station's tank: the kettle's water. */
    public static final String WATER = "water";
    /** Plain water, as Diegesis draws it. */
    @SuppressWarnings("removal")
    public static final ResourceLocation WATER_LIQUID = new ResourceLocation("minecraft", "water");

    /** Ladle turns that cook a whole dish, besides the cauldron's own count, as at HerbalBrews' cauldron: an eighth of it a turn. */
    public static final int TURNS = 8;
    /** The fastest the server counts the ladle going round: a whole turn in this many ticks. */
    public static final float FASTEST_TURN_TICKS = 8.0F;
    /** Where the ladle's end may go in the water, pixels from the middle either way: the inside is 8 px, the ladle's bowl 2 px. */
    public static final float LADLE_REACH = 2.5F;
    /** A ladle not reported for this many ticks has been let go. */
    public static final int STIR_GAP = 10;

    /**
     * What the logic tells the clients in the station's data: whether a dish cooks; the game time it began at (earlier as it is
     * stirred) and the ticks it takes; the cauldron's water (0 to 100); whether it is hot; the dish the food in it makes (an id, none
     * for nothing) and the water that dish takes; whether a cook stirs now, and where the ladle's end is, pixels from the middle in
     * the cauldron's own axes.
     */
    public static final String COOKING = "cooking";
    public static final String STARTED = "started";
    public static final String DURATION = "duration";
    public static final String WATER_LEVEL = "water";
    public static final String HEATED = "heated";
    public static final String MAKING = "making";
    public static final String MAKING_WATER = "making_water";
    public static final String STIRRING = "stirring";
    public static final String LADLE_X = "ladle_x";
    public static final String LADLE_Z = "ladle_z";

    /** A custom action: water from the cursor ({@link #FROM}: from that inventory slot instead) is poured in, a bucket or a bottle. */
    public static final String ACTION_POUR = "pour";
    /** A custom action: the ladle moved; data: where its end is now, {@link #X} and {@link #Z}, pixels from the middle. */
    public static final String ACTION_STIR = "stir";
    public static final String X = "x";
    public static final String Z = "z";
    /**
     * A custom action: a dish picked in the book ({@link #DISH}); its food comes in from the inventory (with {@link #ALL} as many sets
     * as the slots hold), and water when the cauldron has less than it takes.
     */
    public static final String ACTION_FILL = "fill";
    /** A custom action: the dish that waits goes into the cook's inventory, or at their feet when it is full. */
    public static final String ACTION_TAKE = "take";
    public static final String DISH = "dish";
    public static final String ALL = "all";
    public static final String FROM = "from";

    /** Played for everybody who sees it: water poured in. Data: {@link #ITEM}, what it was poured from. */
    public static final ResourceLocation EFFECT_POURED = HandsOnLetsDo.id("meadow_cauldron_poured");
    /** Played for everybody who sees it: a dish is done. Data: {@link #ITEM}, the dish. */
    public static final ResourceLocation EFFECT_COOKED = HandsOnLetsDo.id("meadow_cauldron_cooked");
    public static final String ITEM = "item";
    public static final String MOUTH = "mouth";

    @SuppressWarnings("removal")
    public static final Supplier<Block> CAULDRON = () -> BuiltInRegistries.BLOCK.get(MeadowCauldron.BLOCK);

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(SERVED, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .slots(INGREDIENTS, MeadowCauldron.INGREDIENTS, SlotRules.DEFAULT.kept())
            .tank(WATER, TankSpec.of(MeadowCauldron.MOST_WATER))
            .build();

    /** The cauldron faces the cook, whichever side they come from; the code turns it back to the way the block stands. */
    public static final StationType TYPE = type(ID, false);
    public static final StationType HANGING_TYPE = type(HANGING_ID, true);

    private static boolean registered;

    private MeadowCauldronStation() {
    }

    private static StationType type(ResourceLocation id, boolean hanging) {
        return Diegesis.station(id)
                .block(CAULDRON)
                .when(state -> MeadowCauldron.hangs(state) == hanging)
                .schema(SCHEMA)
                .logic(MeadowCauldronLogic::new)
                .orientation(StationType.Orientation.PLAYER)
                .openWhen(MeadowCauldronStation::opens)
                .hidesBlock()
                .idleLogic()
                .build();
    }

    /** With Meadow installed and the station left on in the config; otherwise the cauldron keeps its own menu. */
    public static void register() {
        if (HandsOnLetsDo.has(MeadowCauldron.MOD_ID) && HandsOnConfig.meadowCookingCauldron()) {
            Diegesis.register(TYPE);
            Diegesis.register(HANGING_TYPE);
            registered = true;
        }
    }

    /** Whether the station is on: only then does the cauldron tell its clients what it holds. */
    public static boolean isRegistered() {
        return registered;
    }

    /** Whether {@code id} is one of the two station types. */
    public static boolean isOne(ResourceLocation id) {
        return ID.equals(id) || HANGING_ID.equals(id);
    }

    /** Sneaking keeps the cauldron's own use, its menu. A cauldron that is not the one this mod knows keeps its menu too. */
    private static boolean opens(Player player, BlockPos pos, BlockState state) {
        return !player.isSecondaryUseActive() && MeadowCauldron.of(player.level().getBlockEntity(pos)) != null;
    }
}
