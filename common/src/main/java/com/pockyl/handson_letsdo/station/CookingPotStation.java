package com.pockyl.handson_letsdo.station;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.state.TankSpec;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_letsdo.HandsOnLetsDo;

/**
 * Farm &amp; Charm's cooking pot as a station, worked as Hands-On Delight's pot is: food is thrown into the water, nothing cooks
 * unless it is stirred, the whole pot cooks as one batch, and the soup is filled into bowls when it is done. Its slots are windows
 * on the pot's own inventory: hoppers reach it and it keeps its items whoever is at it. The scene stands in for the block wherever
 * it is drawn, with somebody at it or not: the same pot, open.
 */
public final class CookingPotStation {
    public static final ResourceLocation ID = HandsOnLetsDo.id("cooking_pot");
    public static final ResourceLocation ROASTER_ID = HandsOnLetsDo.id("roaster");
    public static final ResourceLocation CANDLELIGHT_ID = HandsOnLetsDo.id("candlelight_cooking_pot");
    public static final ResourceLocation CANDLELIGHT_PAN_ID = HandsOnLetsDo.id("candlelight_cooking_pan");
    public static final ResourceLocation BAKERY_ID = HandsOnLetsDo.id("bakery_small_cooking_pot");

    public static final String INGREDIENTS = "ingredients";
    /** The soup that waits in the pot, as the dish it will be: a view, nothing is put there or taken from it. */
    public static final String MEAL = "meal";
    /** The dish in the pot's own dish slot: what needs no bowl, and what hoppers' bowls were filled with. */
    public static final String SERVED = "served";
    /** The pot's own vessel slot, where hoppers and its menu lay bowls. Never shown: at the station a bowl is dipped. */
    public static final String VESSELS = "vessels";
    public static final String BROTH = "broth";
    public static final String PROGRESS = "progress";
    public static final String HEATED = "heated";
    /** On while the pot counts as stirred; where the spoon's end is, -1 to 1 of its reach in the pot's own axes. */
    public static final String STIRRING = "stirring";
    public static final String SPOON_X = "spoon_x";
    public static final String SPOON_Z = "spoon_z";
    /** How well the pot is stirred, 0 to 3 stars; what it is when a batch is done brings bonus servings. */
    public static final String STARS = "stars";
    /** What the logic tells the clients in the station's data: how long cooking still takes, in ticks. */
    public static final String COOK_LEFT = "cook_left";
    /** And the stars the last batch was done with, and how many the cook at the pot has opened for this dish. */
    public static final String GRADE = "grade";
    public static final String OPEN_STARS = "open_stars";
    /** And how many servings the stars of the last batch added. */
    public static final String BONUS = "bonus";
    /** And how many servings of soup wait in the pot, and the recipe of the dish they are. */
    public static final String SERVINGS = "servings";
    public static final String SOUP = "soup";
    /** The servings the batch on the fire makes, and whether it waits for the spoon: the badge's count and what it asks for. */
    public static final String BATCH = "batch";
    public static final String WAITS_FOR_SPOON = "waits_for_spoon";
    /** And how many ticks one serving takes under the spoon, as the server's settings make it. */
    public static final String SERVING_TIME = "serving_time";
    /** And whether the pot is stirred at all: a pot of another build of its mod only holds what is laid into it. */
    public static final String STIRRED_POT = "stirred_pot";

    public static final SlotRef MEAL_SLOT = SlotRef.of(MEAL, 0);
    public static final SlotRef SERVED_SLOT = SlotRef.of(SERVED, 0);
    public static final SlotRef VESSEL_SLOT = SlotRef.of(VESSELS, 0);

    /** A custom action: the vessel on the player's cursor takes a serving of the soup. */
    public static final String ACTION_SCOOP = "scoop";
    /** A custom action: the spoon moved; data: where its end is now, {@link #STIR_X} and {@link #STIR_Z}. */
    public static final String ACTION_STIR = "stir";
    /**
     * A custom action: a dish picked in the recipe book; its ingredients go from the inventory into the pot, one set or, with
     * {@link #SCOOP_ALL}, as many as fit. Data: {@link #DISH}, the recipe's id.
     */
    public static final String ACTION_FILL = "fill";
    /**
     * A custom action: an empty hand clicked the soup off anything floating; what went in without floating (water poured, an egg
     * cracked, sugar dissolved) comes back out whole, the last first.
     */
    public static final String ACTION_TAKE_BACK = "take_back";
    public static final String DISH = "dish";
    /** How many sets one fill puts in at most: as many as one batch cooks without taking longer. */
    public static final int FILL_MOST = 16;
    /** Data of {@link #ACTION_SCOOP}: the whole stack on the cursor, not one vessel. */
    public static final String SCOOP_ALL = "all";
    /** Data of {@link #ACTION_SCOOP}: the hand is empty, the vessels come from the inventory. */
    public static final String FROM_INVENTORY = "from_inventory";
    public static final String STIR_X = "x";
    /** Data of {@link #ACTION_STIR}: the cook's own clock, in ticks, so that the server judges the pace the hand had. */
    public static final String STIR_AT = "at";
    public static final String STIR_Z = "z";
    /** Played when a serving leaves the pot, for everybody who sees it. */
    public static final ResourceLocation EFFECT_SCOOPED = HandsOnLetsDo.id("scooped");

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(INGREDIENTS, Pot.INGREDIENTS, SlotRules.DEFAULT.kept().accepts(stack -> !isVessel(stack)))
            .slots(MEAL, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .slots(SERVED, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .slots(VESSELS, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .tank(BROTH, TankSpec.of(100))
            .control(PROGRESS, ControlSpec.stepped(0.0F, 1.0F, 100, 0.0F).locked())
            .control(HEATED, ControlSpec.toggle(false).locked())
            .control(STIRRING, ControlSpec.toggle(false).locked())
            .control(SPOON_X, ControlSpec.stepped(-1.0F, 1.0F, 64, 0.0F).locked())
            .control(SPOON_Z, ControlSpec.stepped(-1.0F, 1.0F, 64, 0.0F).locked())
            .control(STARS, ControlSpec.stepped(0.0F, StirSkill.MOST, 60, 0.0F).locked())
            .build();

    /**
     * The scene faces the player who opens the pot, and turns the pot in it back to the way the block stands. It is the pot in the
     * world too, with nobody at it: the pot tells the clients near it what it holds ({@link StirredPot}) and each runs the logic on
     * its copy, so that opening and leaving it changes nothing but what moves.
     */
    public static final StationType TYPE = Diegesis.station(ID)
            .block(Pot.COOKING_POT.blockSupplier())
            .schema(SCHEMA)
            .logic(station -> new PotLogic(station, Pot.COOKING_POT))
            .orientation(StationType.Orientation.PLAYER)
            .openWhen(CookingPotStation::opens)
            .hidesBlock()
            .idleLogic()
            .build();

    /**
     * Farm &amp; Charm's roaster, the same station dry: what roasts lies in the pan, the roaster is swirled by its handle as the
     * soup is stirred, and the roast that is done is served into bowls.
     */
    public static final StationType ROASTER_TYPE = Diegesis.station(ROASTER_ID)
            .block(Pot.ROASTER.blockSupplier())
            .schema(SCHEMA)
            .logic(station -> new PotLogic(station, Pot.ROASTER))
            .orientation(StationType.Orientation.PLAYER)
            .openWhen(CookingPotStation::opens)
            .hidesBlock()
            .build();

    /**
     * Candlelight's cooking pot, Farm &amp; Charm's pot made taller: the same station, stirred, its soup filled into bowls, and its
     * scene the pot in the world too.
     */
    public static final StationType CANDLELIGHT_TYPE = Diegesis.station(CANDLELIGHT_ID)
            .block(Pot.CANDLELIGHT_POT.blockSupplier())
            .schema(SCHEMA)
            .logic(station -> new PotLogic(station, Pot.CANDLELIGHT_POT))
            .orientation(StationType.Orientation.PLAYER)
            .openWhen(CookingPotStation::opens)
            .hidesBlock()
            .idleLogic()
            .build();

    /**
     * Candlelight's cooking pan, a roaster of its own without a lid: the roaster's station, swirled by its one handle, the roast
     * served into bowls; and its scene the pan in the world too, what lies in it in sight as at the station.
     */
    public static final StationType CANDLELIGHT_PAN_TYPE = Diegesis.station(CANDLELIGHT_PAN_ID)
            .block(Pot.CANDLELIGHT_PAN.blockSupplier())
            .schema(SCHEMA)
            .logic(station -> new PotLogic(station, Pot.CANDLELIGHT_PAN))
            .orientation(StationType.Orientation.PLAYER)
            .openWhen(CookingPotStation::opens)
            .hidesBlock()
            .idleLogic()
            .build();

    /**
     * Bakery's small cooking pot, Farm &amp; Charm's pot made small, with a handle and a lid of its own: the same station, stirred, its
     * soup filled into bowls, and its scene the pot in the world too.
     */
    public static final StationType BAKERY_TYPE = Diegesis.station(BAKERY_ID)
            .block(Pot.BAKERY_POT.blockSupplier())
            .schema(SCHEMA)
            .logic(station -> new PotLogic(station, Pot.BAKERY_POT))
            .orientation(StationType.Orientation.PLAYER)
            .openWhen(CookingPotStation::opens)
            .hidesBlock()
            .idleLogic()
            .build();

    private CookingPotStation() {
    }

    /**
     * With Farm &amp; Charm installed (and Candlelight for its pot and pan, Bakery for its small pot, which cook Farm &amp; Charm's
     * dishes) and the station left on in the config; otherwise the pot keeps its own menu.
     */
    public static void register() {
        if (HandsOnLetsDo.has(FarmCharmDishes.MOD_ID) && Pot.COOKING_POT.active()) {
            Diegesis.register(TYPE);
        }
        if (HandsOnLetsDo.has(FarmCharmDishes.MOD_ID) && Pot.ROASTER.active()) {
            Diegesis.register(ROASTER_TYPE);
        }
        if (HandsOnLetsDo.has(FarmCharmDishes.MOD_ID) && HandsOnLetsDo.has(Pot.CANDLELIGHT) && Pot.CANDLELIGHT_POT.active()) {
            Diegesis.register(CANDLELIGHT_TYPE);
        }
        if (HandsOnLetsDo.has(FarmCharmDishes.MOD_ID) && HandsOnLetsDo.has(Pot.CANDLELIGHT) && Pot.CANDLELIGHT_PAN.active()) {
            Diegesis.register(CANDLELIGHT_PAN_TYPE);
        }
        if (HandsOnLetsDo.has(FarmCharmDishes.MOD_ID) && HandsOnLetsDo.has(Pot.BAKERY) && Pot.BAKERY_POT.active()) {
            Diegesis.register(BAKERY_TYPE);
        }
    }

    /** Bowls and bottles are for the soup, they do not go into the water. Other vessels are found in the recipes, by the logic. */
    private static boolean isVessel(ItemStack stack) {
        return stack.is(Items.BOWL) || stack.is(Items.GLASS_BOTTLE);
    }

    /** Sneaking keeps the pot's own use, its menu. A pot that is not the one this mod knows keeps its menu too. */
    private static boolean opens(Player player, BlockPos pos, BlockState state) {
        return !player.isSecondaryUseActive() && Pot.of(player.level().getBlockEntity(pos)) != null;
    }
}
