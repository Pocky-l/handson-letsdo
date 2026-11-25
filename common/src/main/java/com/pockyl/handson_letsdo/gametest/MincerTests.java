package com.pockyl.handson_letsdo.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_letsdo.HandsOnConfig;
import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.station.Dish;
import com.pockyl.handson_letsdo.station.FarmCharmDishes;
import com.pockyl.handson_letsdo.station.Mincer;
import com.pockyl.handson_letsdo.station.MincerLogic;
import com.pockyl.handson_letsdo.station.MincerRecipes;
import com.pockyl.handson_letsdo.station.MincerStation;

import java.util.List;

/**
 * The mincer's rules against a real mincer of Farm &amp; Charm: what lies on its hopper is minced in loads of up to eight, two whole
 * turns of the crank a load, by the mincer's own tick, and only while the crank is turned. They pass at once without Farm &amp; Charm.
 */
public final class MincerTests {
    private static final BlockPos AT = new BlockPos(1, 2, 1);
    /** How far one report of the hand turns the crank, radians: less than the server counts in a tick under the heaviest load. */
    private static final float STEP = 0.3F;

    private MincerTests() {
    }

    @SuppressWarnings("removal")
    private static Item farmCharm(String path) {
        return BuiltInRegistries.ITEM.get(new ResourceLocation(FarmCharmDishes.MOD_ID, path));
    }

    /** A mincer with the logic the station makes for it, and the crank's angle as the cook's hand last reported it. */
    private record Setup(TestStation station, MincerLogic logic, Mincer mincer, float[] hand) {
        Container container() {
            return mincer.container();
        }
    }

    private static Setup mincer(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at.below(), Blocks.STONE);
        helper.setBlock(at, MincerStation.MINCER.get().defaultBlockState());
        BlockPos pos = helper.absolutePos(at);
        TestStation station = new TestStation(helper.getLevel(), pos, MincerStation.TYPE);
        Mincer mincer = Mincer.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(mincer != null, "Farm & Charm's mincer is the mincer the station was made for, with what the station adds to it");
        return new Setup(station, new MincerLogic(station), mincer, new float[] {0.0F});
    }

    private static boolean withoutFarmCharm(GameTestHelper helper) {
        if (HandsOnLetsDo.has(FarmCharmDishes.MOD_ID)) {
            return false;
        }
        helper.succeed();
        return true;
    }

    private static Player cook(GameTestHelper helper, Setup setup) {
        Player player = helper.makeMockPlayer();
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        return player;
    }

    private static void put(Setup setup, SlotRef slot, ItemStack stack) {
        setup.station.state().setItem(slot, stack);
        setup.logic.onSlotChanged(setup.station, slot);
    }

    /** The cook's hand turns the crank {@code turns} times round, reported as the client reports it, in small steps. */
    private static void crank(Setup setup, Player player, float turns) {
        report(setup, player, setup.hand[0]);
        int steps = Math.round(turns * Mth.TWO_PI / STEP);
        for (int i = 0; i < steps; i++) {
            report(setup, player, setup.hand[0] + STEP);
        }
    }

    private static void report(Setup setup, Player player, float angle) {
        setup.hand[0] = Mth.wrapDegrees(angle * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        angle = setup.hand[0];
        CompoundTag data = new CompoundTag();
        data.putFloat(MincerStation.ANGLE, angle);
        setup.logic.onCustomAction(setup.station, player, MincerStation.ACTION_CRANK, data);
    }

    /**
     * The mincer is a station exactly when Farm &amp; Charm is installed, and then its block entity is what the station expects: two
     * slots, the station's part in it, its recipes read.
     */
    public static void theMincerIsAStationOnlyWithFarmCharm(GameTestHelper helper) {
        boolean installed = HandsOnLetsDo.has(FarmCharmDishes.MOD_ID);
        helper.assertTrue(Diegesis.stationType(MincerStation.ID).isPresent() == (installed && HandsOnConfig.mincer()),
                "the mincer's station exists with Farm & Charm and only with it");
        if (!installed) {
            helper.succeed();
            return;
        }
        Setup setup = mincer(helper, AT);
        BlockState state = helper.getLevel().getBlockState(setup.station.pos());
        helper.assertTrue(Diegesis.stationFor(state).filter(type -> type == MincerStation.TYPE).isPresent(), "the mincer opens its station");
        helper.assertTrue(setup.container().getContainerSize() == Mincer.SLOTS, "two slots");
        helper.assertTrue(setup.mincer.hand() != null, "the station's part is in the mincer (its mixin applied)");
        helper.assertTrue(MincerRecipes.all(helper.getLevel()).size() > 40, "Farm & Charm's mincing is read");
        helper.succeed();
    }

    /**
     * The owner's numbers: a load is up to eight items and takes two whole turns. One beef: nothing after one turn and a half, one
     * minced beef after two. Eight beef: all eight after two turns. Nine: eight after two turns, the ninth after two more.
     */
    public static void aLoadOfUpToEightTakesTwoTurns(GameTestHelper helper) {
        if (withoutFarmCharm(helper)) {
            return;
        }
        Item minced = farmCharm("minced_beef");
        Setup one = mincer(helper, AT);
        Player player = cook(helper, one);
        put(one, MincerStation.INPUT_SLOT, new ItemStack(Items.BEEF));
        crank(one, player, 1.5F);
        helper.assertTrue(one.container().getItem(Mincer.OUTPUT_SLOT).isEmpty() && one.container().getItem(Mincer.INPUT_SLOT).getCount() == 1,
                "one beef after one turn and a half: nothing yet");
        crank(one, player, 0.6F);
        helper.assertTrue(one.container().getItem(Mincer.OUTPUT_SLOT).is(minced) && one.container().getItem(Mincer.OUTPUT_SLOT).getCount() == 1
                && one.container().getItem(Mincer.INPUT_SLOT).isEmpty(), "one beef is minced after two turns, got "
                + one.container().getItem(Mincer.OUTPUT_SLOT));
        helper.assertTrue(one.station.effects.contains(MincerStation.EFFECT_MINCED), "everybody saw it come out");

        Setup eight = mincer(helper, AT.east(2));
        cook(helper, eight);
        put(eight, MincerStation.INPUT_SLOT, new ItemStack(Items.BEEF, 8));
        crank(eight, player, 1.9F);
        helper.assertTrue(eight.container().getItem(Mincer.OUTPUT_SLOT).isEmpty(), "eight beef: nothing before two turns");
        crank(eight, player, 0.2F);
        helper.assertTrue(eight.container().getItem(Mincer.OUTPUT_SLOT).getCount() == 8 && eight.container().getItem(Mincer.INPUT_SLOT).isEmpty(),
                "eight beef are minced together after two turns, got " + eight.container().getItem(Mincer.OUTPUT_SLOT));

        Setup nine = mincer(helper, AT.south(2));
        cook(helper, nine);
        put(nine, MincerStation.INPUT_SLOT, new ItemStack(Items.BEEF, 9));
        crank(nine, player, 2.1F);
        helper.assertTrue(nine.container().getItem(Mincer.OUTPUT_SLOT).getCount() == 8 && nine.container().getItem(Mincer.INPUT_SLOT).getCount() == 1,
                "nine beef: eight after two turns, got " + nine.container().getItem(Mincer.OUTPUT_SLOT));
        crank(nine, player, 1.5F);
        helper.assertTrue(nine.container().getItem(Mincer.OUTPUT_SLOT).getCount() == 8, "the ninth not before four turns in all");
        crank(nine, player, 0.6F);
        helper.assertTrue(nine.container().getItem(Mincer.OUTPUT_SLOT).getCount() == 9 && nine.container().getItem(Mincer.INPUT_SLOT).isEmpty(),
                "all nine after four turns, got " + nine.container().getItem(Mincer.OUTPUT_SLOT));
        helper.succeed();
    }

    /**
     * Nothing is minced without the hand: beef on the hopper with a cook at the mincer stays as it is while nobody turns the crank,
     * and a crank shaken to and fro counts nothing. A turn faster than a hand turns it counts at a hand's pace.
     */
    public static void nothingIsMincedWithoutTheHand(GameTestHelper helper) {
        if (withoutFarmCharm(helper)) {
            return;
        }
        Setup setup = mincer(helper, AT);
        Player player = cook(helper, setup);
        helper.onEachTick(() -> setup.logic.tick(setup.station));
        put(setup, MincerStation.INPUT_SLOT, new ItemStack(Items.BEEF, 4));
        for (int i = 0; i < 40; i++) {
            report(setup, player, 0.0F);
            report(setup, player, 1.0F);
        }
        helper.assertTrue(Math.abs(setup.mincer.hand().handson$turned()) < 1.1F, "shaking the crank to and fro counts nothing");
        float before = setup.mincer.hand().handson$turned();
        report(setup, player, setup.hand[0] + 3.0F);
        helper.assertTrue(setup.mincer.hand().handson$turned() - before < 1.1F, "a jump is counted at a hand's pace");
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(setup.container().getItem(Mincer.INPUT_SLOT).getCount() == 4 && setup.container().getItem(Mincer.OUTPUT_SLOT).isEmpty(),
                    "beef nobody cranks stays on the hopper");
            helper.succeed();
        });
    }

    /**
     * The crank turns heavier under a heavier load: a hand going round as fast as it can minces eight beef, but no more than half a
     * load's turns go through eight iron ingots in as many reports; the reports a hand makes at a load's own pace mince the iron too.
     */
    public static void aHeavyLoadTurnsSlowly(GameTestHelper helper) {
        if (withoutFarmCharm(helper)) {
            return;
        }
        helper.assertTrue(MincerStation.heaviness("METAL", 8) > MincerStation.heaviness("MEAT", 8)
                && MincerStation.heaviness("MEAT", 8) > MincerStation.heaviness("MEAT", 1) && MincerStation.heaviness("MEAT", 0) == 0.0F,
                "metal is heavier than meat, a full load than one");
        float meatPace = Mth.TWO_PI / MincerStation.turnTicks(MincerStation.heaviness("MEAT", 8));
        int reports = Mth.ceil(MincerStation.LOAD_TURN / meatPace) + 2;
        Setup beef = mincer(helper, AT);
        Player player = cook(helper, beef);
        put(beef, MincerStation.INPUT_SLOT, new ItemStack(Items.BEEF, 8));
        report(beef, player, 0.0F);
        for (int i = 0; i < reports; i++) {
            report(beef, player, beef.hand[0] + meatPace * 0.99F);
        }
        helper.assertTrue(beef.container().getItem(Mincer.OUTPUT_SLOT).getCount() == 8, "eight beef go through at meat's pace, got "
                + beef.container().getItem(Mincer.OUTPUT_SLOT));
        Setup iron = mincer(helper, AT.east(2));
        cook(helper, iron);
        put(iron, MincerStation.INPUT_SLOT, new ItemStack(Items.IRON_INGOT, 8));
        if (MincerRecipes.taking(MincerRecipes.all(helper.getLevel()), iron.container().getItem(Mincer.INPUT_SLOT)) == null) {
            helper.succeed();
            return;
        }
        report(iron, player, 0.0F);
        for (int i = 0; i < reports; i++) {
            report(iron, player, iron.hand[0] + meatPace * 0.99F);
        }
        helper.assertTrue(iron.container().getItem(Mincer.OUTPUT_SLOT).isEmpty()
                && Math.abs(iron.mincer.hand().handson$turned()) < MincerStation.LOAD_TURN * 0.7F,
                "iron does not turn at meat's pace: " + iron.mincer.hand().handson$turned() + " radians counted");
        crank(iron, player, MincerStation.TURNS_PER_LOAD);
        helper.assertTrue(!iron.container().getItem(Mincer.OUTPUT_SLOT).isEmpty(), "at its own pace the iron goes through, got "
                + iron.container().getItem(Mincer.OUTPUT_SLOT));
        iron.container().clearContent();
        beef.container().clearContent();
        helper.succeed();
    }

    /**
     * What came out lies under the spout and is not thrown out while it waits; the clients are told what the mincer holds. Cranked
     * Farm &amp; Charm's way by a player without the station, the mincer throws it out as it does.
     */
    public static void whatIsMincedWaitsUnderTheSpout(GameTestHelper helper) {
        if (withoutFarmCharm(helper)) {
            return;
        }
        Setup setup = mincer(helper, AT);
        Player player = cook(helper, setup);
        put(setup, MincerStation.INPUT_SLOT, new ItemStack(Items.BEEF, 3));
        crank(setup, player, 2.05F);
        BlockPos pos = setup.station.pos();
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(setup.container().getItem(Mincer.OUTPUT_SLOT).getCount() == 3, "the minced beef still lies under the spout");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3.0)).isEmpty(), "nothing was thrown out");
            BlockEntity entity = helper.getLevel().getBlockEntity(pos);
            CompoundTag told = entity.getUpdateTag();
            helper.assertTrue(told.getCompound("HandsOnMincer").getBoolean("Kept") && told.contains("Items"),
                    "the clients are told what lies there, got " + told);
            BlockState state = helper.getLevel().getBlockState(pos);
            for (var property : state.getProperties()) {
                if (property instanceof IntegerProperty number && property.getName().equals("crank")) {
                    helper.getLevel().setBlock(pos, state.setValue(number, 10), 3);
                }
            }
        });
        helper.runAfterDelay(16, () -> {
            helper.assertTrue(setup.container().getItem(Mincer.OUTPUT_SLOT).isEmpty(), "cranked Farm & Charm's way, the mincer throws it out");
            helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3.0)).isEmpty(), "as that mod does");
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3.0)).forEach(ItemEntity::discard);
            helper.succeed();
        });
    }

    /** A load that makes more than a stack: what fits lies under the spout, the rest goes to the cook. */
    public static void whatDoesNotFitGoesToTheCook(GameTestHelper helper) {
        if (withoutFarmCharm(helper)) {
            return;
        }
        Setup setup = mincer(helper, AT);
        Player player = cook(helper, setup);
        put(setup, MincerStation.INPUT_SLOT, new ItemStack(Items.GOLD_INGOT, 8));
        crank(setup, player, 2.05F);
        ItemStack lying = setup.container().getItem(Mincer.OUTPUT_SLOT);
        int nuggets = MincerRecipes.taking(MincerRecipes.all(helper.getLevel()), new ItemStack(Items.GOLD_INGOT)).result().getCount() * 8;
        helper.assertTrue(lying.is(Items.GOLD_NUGGET) && lying.getCount() == Math.min(64, nuggets), "a stack lies under the spout, got " + lying);
        helper.assertTrue(player.getInventory().countItem(Items.GOLD_NUGGET) == nuggets - lying.getCount(),
                "the cook has the rest, " + player.getInventory().countItem(Items.GOLD_NUGGET));
        setup.container().clearContent();
        helper.succeed();
    }

    /** Every recipe of the mincer, picked in the book: a load of what it minces goes onto the hopper and two turns make it. */
    public static void everythingTheMincerMakesIsLaidFromTheBook(GameTestHelper helper) {
        if (withoutFarmCharm(helper)) {
            return;
        }
        Player player = helper.makeMockPlayer();
        for (Dish recipe : MincerRecipes.all(helper.getLevel())) {
            helper.setBlock(AT, Blocks.AIR);
            Setup setup = mincer(helper, AT);
            setup.station.users.add(player);
            setup.logic.onOpen(setup.station, player);
            player.getInventory().clearContent();
            ItemStack input = recipe.ingredients().get(0).getItems()[0].copyWithCount(1);
            player.getInventory().add(input.copyWithCount(Math.min(input.getMaxStackSize(), 20)));
            CompoundTag fill = new CompoundTag();
            fill.putString(MincerStation.RECIPE, recipe.id().toString());
            setup.logic.onCustomAction(setup.station, player, MincerStation.ACTION_FILL, fill);
            int load = Math.min(MincerStation.LOAD, input.getMaxStackSize());
            helper.assertTrue(setup.container().getItem(Mincer.INPUT_SLOT).getCount() == Math.min(load, 20),
                    recipe.id() + ": a load lies on the hopper, got " + setup.container().getItem(Mincer.INPUT_SLOT));
            if (!recipe.id().equals(MincerRecipes.taking(MincerRecipes.all(helper.getLevel()), input).id())) {
                // Another recipe takes the same item first, as the mincer itself looks them up.
                setup.container().clearContent();
                continue;
            }
            crank(setup, player, 2.05F);
            int made = setup.container().getItem(Mincer.OUTPUT_SLOT).getCount() + player.getInventory().countItem(recipe.result().getItem());
            helper.assertTrue(made == load * recipe.result().getCount(), recipe.id() + ": two turns make the load, got " + made);
            setup.container().clearContent();
        }
        helper.setBlock(AT, Blocks.AIR);
        helper.succeed();
    }

    /** The mincer's scene is one the library reads, with every part the station shows things on. */
    public static void theMincersSceneIsRead(GameTestHelper helper) {
        StationTests.sceneIsRead(helper, "mincer", List.of("body", "crank", "crank_plane", "hopper", "input", "minced", "die", "badge",
                "dial_track", "dial", "preview", "book_open"), MincerStation.SCHEMA, List.of(MincerStation.INPUT, MincerStation.MINCED));
        helper.succeed();
    }
}
