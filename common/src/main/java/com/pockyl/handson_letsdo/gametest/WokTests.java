package com.pockyl.handson_letsdo.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.handson_letsdo.HandsOnConfig;
import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.station.WokAndBowlWok;
import com.pockyl.handson_letsdo.station.WokLogic;
import com.pockyl.handson_letsdo.station.WokStation;

import java.util.List;

/**
 * The wok's station against a real wok of Wok &amp; Bowl (1.21.1): what it hands the wok is taken by the wok's own rules, the flick
 * is its toss when it calls, the sauce and a flick when ready serve, a picked recipe goes in in its order. They pass at once
 * without Wok &amp; Bowl.
 */
public final class WokTests {
    private static final BlockPos AT = new BlockPos(1, 3, 1);

    private WokTests() {
    }

    private static Item wokAndBowl(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(WokAndBowlWok.MOD_ID, path));
    }

    private record Setup(TestStation station, WokLogic logic, WokAndBowlWok wok, Player cook) {
    }

    private static boolean withoutWok(GameTestHelper helper) {
        if (HandsOnLetsDo.has(WokAndBowlWok.MOD_ID) && Diegesis.stationType(WokStation.ID).isPresent()) {
            return false;
        }
        helper.succeed();
        return true;
    }

    private static BlockState with(BlockState state, String name, boolean value) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        return property instanceof BooleanProperty bool ? state.setValue(bool, value) : state;
    }

    /** A wok over a lit campfire, heated as it is when placed there, and a cook at it. */
    private static Setup wok(GameTestHelper helper, boolean heated) {
        helper.setBlock(AT.below(2), Blocks.STONE);
        helper.setBlock(AT.below(), heated ? Blocks.CAMPFIRE.defaultBlockState() : Blocks.STONE.defaultBlockState());
        BlockState state = with(with(WokStation.WOK.get().defaultBlockState(), "heat", heated), "needs_support", heated);
        helper.setBlock(AT, state);
        BlockPos pos = helper.absolutePos(AT);
        TestStation station = new TestStation(helper.getLevel(), pos, WokStation.TYPE);
        WokAndBowlWok wok = WokAndBowlWok.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(wok != null, "Wok & Bowl's wok is the wok the station was made for");
        WokLogic logic = new WokLogic(station);
        Player cook = helper.makeMockPlayer(GameType.SURVIVAL);
        cook.getInventory().clearContent();
        station.users.add(cook);
        logic.onOpen(station, cook);
        return new Setup(station, logic, wok, cook);
    }

    private static void place(Setup setup, ItemStack stack) {
        setup.cook.inventoryMenu.setCarried(stack);
        setup.logic.onCustomAction(setup.station, setup.cook, WokStation.ACTION_PLACE, new CompoundTag());
    }

    private static void act(Setup setup, String action) {
        setup.logic.onCustomAction(setup.station, setup.cook, action, new CompoundTag());
    }

    private static int count(Player player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static void theWokIsAStationOnlyWithWokAndBowl(GameTestHelper helper) {
        boolean installed = HandsOnLetsDo.has(WokAndBowlWok.MOD_ID) && HandsOnConfig.wok();
        helper.assertTrue(Diegesis.stationType(WokStation.ID).isPresent() == installed, "the wok's station exists with Wok & Bowl and only with it");
        if (!installed) {
            helper.succeed();
            return;
        }
        Setup setup = wok(helper, true);
        helper.assertTrue(Diegesis.stationFor(helper.getLevel().getBlockState(setup.station.pos())).filter(type -> type == WokStation.TYPE).isPresent(),
                "the wok opens its station");
        helper.assertTrue(!WokStation.opens(setup.cook, setup.station.pos(), with(helper.getLevel().getBlockState(setup.station.pos()), "has_steamer", true)),
                "with the steamer on, the wok keeps Wok & Bowl's own use");
        helper.succeed();
    }

    /** One of the stack goes in at a click, as the wok takes it: nothing while it is cold, nothing that starts no recipe. */
    public static void foodGoesInOneByOneAsTheWokTakesIt(GameTestHelper helper) {
        if (withoutWok(helper)) {
            return;
        }
        Setup cold = wok(helper, false);
        place(cold, new ItemStack(wokAndBowl("rice"), 3));
        helper.assertTrue(cold.wok.ingredients().isEmpty() && cold.cook.inventoryMenu.getCarried().getCount() == 3, "a cold wok takes nothing");
        Setup setup = wok(helper, true);
        place(setup, new ItemStack(Items.EGG, 2));
        helper.assertTrue(setup.wok.ingredients().isEmpty(), "an egg starts no recipe of the wok");
        place(setup, new ItemStack(wokAndBowl("rice"), 3));
        helper.assertTrue(setup.wok.ingredients().size() == 1 && setup.wok.isCooking(), "one rice went in and the wok cooks");
        helper.assertTrue(setup.cook.inventoryMenu.getCarried().getCount() == 2, "two rice are left in the hand");
        place(setup, new ItemStack(Items.STICK));
        helper.assertTrue(setup.wok.ingredients().size() == 1, "a stick does not go in");
        helper.succeed();
    }

    /** When the wok calls for a toss, the cook's flick is its toss. */
    public static void aFlickTossesWhenTheWokCalls(GameTestHelper helper) {
        if (withoutWok(helper)) {
            return;
        }
        Setup setup = wok(helper, true);
        place(setup, new ItemStack(wokAndBowl("rice")));
        helper.succeedWhen(() -> {
            helper.assertTrue(setup.wok.isAwaitingToss() || setup.wok.lastToss() > 0, "the wok calls for a toss");
            if (setup.wok.isAwaitingToss()) {
                act(setup, WokStation.ACTION_TOSS);
            }
            helper.assertTrue(!setup.wok.isAwaitingToss() && setup.wok.lastToss() == helper.getLevel().getGameTime(), "the flick tossed");
        });
    }

    /** The sauce poured in and a flick once it is ready serve the dish into the wok; a click takes it out to the cook. */
    public static void theSauceAndAFlickServeTheDish(GameTestHelper helper) {
        if (withoutWok(helper)) {
            return;
        }
        Setup setup = wok(helper, true);
        place(setup, new ItemStack(wokAndBowl("rice")));
        place(setup, new ItemStack(wokAndBowl("fermented_sauce")));
        helper.assertTrue(setup.wok.isSauced(), "the sauce went in");
        boolean[] served = {false};
        helper.succeedWhen(() -> {
            if (setup.wok.isAwaitingToss()) {
                act(setup, WokStation.ACTION_TOSS);
            }
            if (!served[0] && setup.wok.isCooking() && setup.wok.timer() >= setup.wok.readyTicks() && !setup.wok.isAwaitingToss()) {
                act(setup, WokStation.ACTION_TOSS);
                served[0] = !setup.wok.isCooking();
            }
            helper.assertTrue(served[0] && setup.wok.hasDishes(), "the dish was served into the wok");
            act(setup, WokStation.ACTION_TAKE);
            helper.assertTrue(count(setup.cook, wokAndBowl("simple_fried_rice")) > 0, "the cook took the fried rice");
        });
    }

    /** A recipe picked in the book goes in in its order, from the inventory. */
    public static void aPickedRecipeGoesInInItsOrder(GameTestHelper helper) {
        if (withoutWok(helper)) {
            return;
        }
        Setup setup = wok(helper, true);
        setup.cook.getInventory().setItem(3, new ItemStack(Items.EGG, 4));
        setup.cook.getInventory().setItem(5, new ItemStack(wokAndBowl("rice"), 4));
        CompoundTag pick = new CompoundTag();
        pick.putString(WokStation.RECIPE, ResourceLocation.fromNamespaceAndPath(WokAndBowlWok.MOD_ID, "wok/egg_fried_rice").toString());
        setup.logic.onCustomAction(setup.station, setup.cook, WokStation.ACTION_FILL, pick);
        List<ItemStack> in = setup.wok.ingredients();
        helper.assertTrue(in.size() == 2 && in.get(0).is(wokAndBowl("rice")) && in.get(1).is(Items.EGG), "rice, then the egg, went in: " + in);
        helper.assertTrue(count(setup.cook, Items.EGG) == 3 && count(setup.cook, wokAndBowl("rice")) == 3, "one of each was taken");
        helper.succeed();
    }

    public static void theWoksSceneIsRead(GameTestHelper helper) {
        StationTests.sceneIsRead(helper, "wok", List.of("wok", "handle_left", "handle_right", "piece_0", "dish_0", "pan", "mouth", "badge",
                "dial_track", "dial", "preview", "book_open"), WokStation.SCHEMA, List.of());
        helper.succeed();
    }
}
