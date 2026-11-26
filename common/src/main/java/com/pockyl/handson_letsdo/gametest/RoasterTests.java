package com.pockyl.handson_letsdo.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_letsdo.Cookable;
import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.station.CookingPotStation;
import com.pockyl.handson_letsdo.station.Dish;
import com.pockyl.handson_letsdo.station.FarmCharmDishes;
import com.pockyl.handson_letsdo.station.Pot;
import com.pockyl.handson_letsdo.station.PotCooking;
import com.pockyl.handson_letsdo.station.PotLogic;
import com.pockyl.handson_letsdo.station.StirredPot;

import java.util.ArrayList;
import java.util.List;

/**
 * The roaster's rules against a real roaster of Farm &amp; Charm: the cooking pot's ({@link PotTests}), on the roaster's block and
 * recipes; and the same against Candlelight's cooking pan ({@link Under}), the same on every loader. They pass at once without the
 * pan's mods.
 */
public final class RoasterTests {
    /** A pan the tests run against: its kind, its station, its scene and the nodes it has, and the mod besides Farm &amp; Charm. */
    public record Under(Pot.Kind kind, ResourceLocation id, StationType type, String scene, List<String> nodes, String mod) {
    }

    /** Farm &amp; Charm's roaster: the pan in its frame, under its lid, a handle on each side. */
    public static final Under FARM_AND_CHARM = new Under(Pot.ROASTER, CookingPotStation.ROASTER_ID, CookingPotStation.ROASTER_TYPE, "roaster",
            List.of("pot", "frame", "pan", "lid", "ladle", "ladle_left"), FarmCharmDishes.MOD_ID);
    /** Candlelight's cooking pan: Farm &amp; Charm's roasts in a pan of Candlelight's own, one handle, no lid, a grate over a campfire. */
    public static final Under CANDLELIGHT = new Under(Pot.CANDLELIGHT_PAN, CookingPotStation.CANDLELIGHT_PAN_ID,
            CookingPotStation.CANDLELIGHT_PAN_TYPE, "candlelight_cooking_pan", List.of("pot", "support", "pan", "ladle"), Pot.CANDLELIGHT);

    private RoasterTests() {
    }

    @SuppressWarnings("removal")
    private static Item farm(String path) {
        return BuiltInRegistries.ITEM.get(new ResourceLocation(FarmCharmDishes.MOD_ID, path));
    }

    private record Setup(TestStation station, PotLogic logic, Pot pot, PotCooking cooking) {
        Container container() {
            return pot.container();
        }
    }

    /** A pan of {@code under} at {@code at} on a magma block, with the logic the station would make for it. */
    private static Setup roaster(GameTestHelper helper, Under under, BlockPos at) {
        helper.setBlock(at.below(), Blocks.MAGMA_BLOCK);
        helper.setBlock(at, under.kind().blockSupplier().get());
        BlockPos pos = helper.absolutePos(at);
        TestStation station = new TestStation(helper.getLevel(), pos, under.type());
        Pot pot = Pot.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(pot != null && pot.kind() == under.kind(), under.kind().block() + " is the pan the station was made for");
        helper.assertTrue(pot.cooking() != null, "the pan waits to be swirled: the stirring is in it");
        return new Setup(station, new PotLogic(station, under.kind()), pot, pot.cooking());
    }

    /** Whether the pan's mods are missing: the test then passes at once. */
    private static boolean without(GameTestHelper helper, Under under) {
        if (installed(under)) {
            return false;
        }
        helper.succeed();
        return true;
    }

    private static boolean installed(Under under) {
        return HandsOnLetsDo.has(FarmCharmDishes.MOD_ID) && HandsOnLetsDo.has(under.mod());
    }

    private static void attend(GameTestHelper helper, Setup setup, Player player) {
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        helper.onEachTick(() -> {
            if (!setup.station.users.isEmpty()) {
                setup.logic.tick(setup.station);
            }
        });
    }

    private static void putIn(Setup setup, int slot, ItemStack stack) {
        SlotRef ref = SlotRef.of(CookingPotStation.INGREDIENTS, slot);
        setup.station.state().setItem(ref, stack);
        setup.logic.onSlotChanged(setup.station, ref);
    }

    private static void swirl(Setup setup, Player player) {
        CompoundTag where = new CompoundTag();
        where.putFloat(CookingPotStation.STIR_X, 0.9F);
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_STIR, where);
    }

    private static void scoop(Setup setup, Player player, boolean all, boolean fromInventory) {
        CompoundTag how = new CompoundTag();
        how.putBoolean(CookingPotStation.SCOOP_ALL, all);
        how.putBoolean(CookingPotStation.FROM_INVENTORY, fromInventory);
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_SCOOP, how);
    }

    /** The ingredients of bacon with eggs, as the roaster reads them: two slots of bacon, two of eggs, butter. */
    private static void baconAndEggs(Setup setup, int sets) {
        putIn(setup, 0, new ItemStack(farm("bacon"), sets));
        putIn(setup, 1, new ItemStack(farm("bacon"), sets));
        putIn(setup, 2, new ItemStack(Items.EGG, sets));
        putIn(setup, 3, new ItemStack(Items.EGG, sets));
        putIn(setup, 4, new ItemStack(farm("butter"), sets));
    }

    /**
     * The roaster is a station exactly when Farm &amp; Charm is installed, and then its block entity is what the station expects:
     * the pot's eight slots and two numbers, the stirring in it, its own dishes.
     */
    public static void theRoasterIsAStationOnlyWithFarmAndCharm(GameTestHelper helper) {
        theRoasterIsAStationOnlyWithFarmAndCharm(helper, FARM_AND_CHARM);
    }

    /** {@link #theRoasterIsAStationOnlyWithFarmAndCharm(GameTestHelper)} at the pan {@code under}. */
    public static void theRoasterIsAStationOnlyWithFarmAndCharm(GameTestHelper helper, Under under) {
        boolean installed = installed(under);
        helper.assertTrue(Diegesis.stationType(under.id()).isPresent() == (installed && under.kind().active()),
                "the pan's station exists with its mods and only with them");
        if (!installed) {
            helper.succeed();
            return;
        }
        Setup setup = roaster(helper, under, new BlockPos(1, 2, 1));
        BlockState state = helper.getLevel().getBlockState(setup.station.pos());
        helper.assertTrue(Diegesis.stationFor(state).filter(type -> type == under.type()).isPresent(), "the pan opens its station");
        helper.assertTrue(setup.container().getContainerSize() == Pot.SLOTS, "eight slots");
        int dishes = under.kind().dishes(helper.getLevel()).size();
        helper.assertTrue(dishes >= 9, "Farm & Charm's roasts are read, got " + dishes);
        helper.assertTrue(setup.pot.isHeated(), "a magma block under the pan heats it");
        helper.succeed();
    }

    /**
     * A roast a cook laid in at the station waits to be swirled: with a bowl lying in the roaster, where Farm &amp; Charm alone
     * would cook, nothing moves. Swirled, the whole pan roasts as one, and a bowl takes a serving: three plates of bacon with eggs
     * for each set, one of each thing gone from every slot.
     */
    public static void aSwirledRoasterRoastsAndAnUnswirledOneWaits(GameTestHelper helper) {
        aSwirledRoasterRoastsAndAnUnswirledOneWaits(helper, FARM_AND_CHARM);
    }

    /** {@link #aSwirledRoasterRoastsAndAnUnswirledOneWaits(GameTestHelper)} at the pan {@code under}. */
    public static void aSwirledRoasterRoastsAndAnUnswirledOneWaits(GameTestHelper helper, Under under) {
        if (without(helper, under)) {
            return;
        }
        Setup setup = roaster(helper, under, new BlockPos(1, 2, 1));
        Player player = helper.makeMockPlayer();
        attend(helper, setup, player);
        setup.container().setItem(Pot.VESSEL_SLOT, new ItemStack(Items.BOWL));
        baconAndEggs(setup, 2);
        Item dish = farm("bacon_with_eggs");
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(setup.cooking.isByHand() && setup.pot.time() == 0 && setup.pot.output().isEmpty(),
                    "left alone the cook's roast does not cook, got " + setup.pot.time() + " ticks");
            // No bowl in the roaster: the roast waits in the pan for one.
            setup.container().setItem(Pot.VESSEL_SLOT, ItemStack.EMPTY);
            setup.cooking.setProgress(setup.pot, 0.995F);
            swirl(setup, player);
        });
        helper.runAfterDelay(38, () -> {
            helper.assertTrue(setup.cooking.servings() == 2, "two servings wait in the pan, got " + setup.cooking.servings());
            player.inventoryMenu.setCarried(new ItemStack(Items.BOWL, 3));
            scoop(setup, player, true, false);
            int made = player.getInventory().countItem(dish);
            helper.assertTrue(made == 6 && player.inventoryMenu.getCarried().getCount() == 1, "two bowls take two servings of three, got " + made);
            for (int slot = 0; slot < Pot.INGREDIENTS; slot++) {
                helper.assertTrue(setup.container().getItem(slot).isEmpty(), "every slot gave one thing a serving, slot " + slot + " holds "
                        + setup.container().getItem(slot));
            }
            helper.succeed();
        });
    }

    /**
     * Food hoppers bring, bowls too, roasts its own mod's way with nobody at the pan: one serving in its own time (Farm &amp; Charm's
     * roaster's, or Candlelight's pan's), into its dish slot.
     */
    public static void aHopperFedRoasterRoastsUpstreamsWay(GameTestHelper helper) {
        aHopperFedRoasterRoastsUpstreamsWay(helper, FARM_AND_CHARM);
    }

    /** {@link #aHopperFedRoasterRoastsUpstreamsWay(GameTestHelper)} at the pan {@code under}. */
    public static void aHopperFedRoasterRoastsUpstreamsWay(GameTestHelper helper, Under under) {
        if (without(helper, under)) {
            return;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        Setup setup = roaster(helper, under, at);
        // Its food laid as a line of hoppers lays it, one thing a slot, the last brought by a hopper; its bowl comes from the side.
        Dish cod = under.kind().dishes(helper.getLevel()).stream().filter(dish -> dish.result().is(farm("cooked_cod"))).findFirst().orElse(null);
        helper.assertTrue(cod != null && Cookable.all(cod.ingredients()), "Farm & Charm roasts cod");
        List<ItemStack> food = new ArrayList<>();
        for (Ingredient ingredient : cod.ingredients()) {
            food.add(ingredient.getItems()[0].copyWithCount(1));
        }
        for (int slot = 0; slot < food.size() - 1; slot++) {
            setup.container().setItem(slot, food.get(slot));
        }
        helper.setBlock(at.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        ((Container) helper.getBlockEntity(at.above())).setItem(0, food.get(food.size() - 1));
        helper.setBlock(at.east(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
        ((Container) helper.getBlockEntity(at.east())).setItem(0, new ItemStack(Items.BOWL));
        long[] began = {-1};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (began[0] < 0 && setup.pot.time() > 0) {
                began[0] = now - setup.pot.time();
                helper.assertFalse(setup.cooking.isByHand(), "a hopper's food is nobody's");
            }
            if (!setup.pot.output().isEmpty()) {
                long took = now - began[0];
                int own = under.kind().upstreamTime();
                helper.assertTrue(began[0] >= 0 && Math.abs(took - own) <= 2, "in the pan's own time (" + own + "), took " + took);
                helper.assertTrue(setup.cooking.servings() == 0, "and no roast waits for a cook");
                setup.container().clearContent();
                helper.succeed();
            }
        });
    }

    private static void give(Player player, Dish dish) {
        player.getInventory().clearContent();
        for (Ingredient ingredient : dish.ingredients()) {
            if (ingredient.getItems().length > 0) {
                player.getInventory().add(ingredient.getItems()[0].copyWithCount(1));
            }
        }
        if (!dish.vessel().isEmpty()) {
            player.getInventory().add(dish.vessel().copyWithCount(1));
        }
    }

    /**
     * Every roast the roaster has, with whatever mods add theirs, picked in the book: its ingredients go in from the inventory as
     * the roaster reads them, it makes that roast of them, and swirled it is done and served in its bowl. The swirling is skipped.
     */
    public static void everyRoastIsLaidFromTheBookAndRoasts(GameTestHelper helper) {
        everyRoastIsLaidFromTheBookAndRoasts(helper, FARM_AND_CHARM);
    }

    /** {@link #everyRoastIsLaidFromTheBookAndRoasts(GameTestHelper)} at the pan {@code under}. */
    public static void everyRoastIsLaidFromTheBookAndRoasts(GameTestHelper helper, Under under) {
        if (without(helper, under)) {
            return;
        }
        List<Dish> dishes = under.kind().dishes(helper.getLevel()).stream().filter(dish -> !dish.learned() && Cookable.all(dish.ingredients()))
                .toList();
        BlockPos at = new BlockPos(1, 2, 1);
        Player player = helper.makeMockPlayer();
        Setup[] setup = new Setup[1];
        int step = 8;
        helper.onEachTick(() -> {
            int tick = (int) helper.getTick();
            int index = tick / step;
            if (index >= dishes.size()) {
                helper.succeed();
                return;
            }
            Dish dish = dishes.get(index);
            if (setup[0] != null && tick % step != 0) {
                setup[0].logic.tick(setup[0].station);
            }
            switch (tick % step) {
                case 0 -> {
                    if (setup[0] != null) {
                        setup[0].container().clearContent();
                    }
                    helper.setBlock(at, Blocks.AIR);
                    setup[0] = roaster(helper, under, at);
                    setup[0].station.users.add(player);
                    setup[0].logic.onOpen(setup[0].station, player);
                    give(player, dish);
                    CompoundTag fill = new CompoundTag();
                    fill.putString(CookingPotStation.DISH, dish.id().toString());
                    setup[0].logic.onCustomAction(setup[0].station, player, CookingPotStation.ACTION_FILL, fill);
                }
                case 2 -> {
                    Dish making = setup[0].cooking.making(helper.getLevel(), setup[0].pot);
                    helper.assertTrue(making != null && making.id().equals(dish.id()), dish.id() + " is what the roaster makes of what the book laid in, got "
                            + (making == null ? "nothing" : making.id()));
                    setup[0].cooking.setProgress(setup[0].pot, 0.995F);
                    swirl(setup[0], player);
                }
                case 5 -> {
                    helper.assertTrue(setup[0].cooking.servings() >= 1, dish.id() + " is done and waits for its bowl");
                    scoop(setup[0], player, false, true);
                    helper.assertTrue(player.getInventory().countItem(dish.result().getItem()) >= dish.result().getCount(),
                            dish.id() + " is served in " + dish.vessel());
                }
                default -> {
                }
            }
        });
    }

    /**
     * The pan tells its clients what it holds, so that each shows it as its station does with nobody at it: a client's copy made from
     * what the pan sends knows it came from a server with the station and holds what lies in the pan, and nothing once the pan is
     * emptied. What the pan saves to the world does not carry that word. Candlelight's pan sends what it saves by itself.
     */
    public static void theRoasterTellsItsClientsWhatItHolds(GameTestHelper helper) {
        theRoasterTellsItsClientsWhatItHolds(helper, FARM_AND_CHARM);
    }

    /** {@link #theRoasterTellsItsClientsWhatItHolds(GameTestHelper)} at the pan {@code under}. */
    public static void theRoasterTellsItsClientsWhatItHolds(GameTestHelper helper, Under under) {
        if (without(helper, under)) {
            return;
        }
        Setup setup = roaster(helper, under, new BlockPos(1, 2, 1));
        Player player = helper.makeMockPlayer();
        attend(helper, setup, player);
        baconAndEggs(setup, 2);
        helper.runAfterDelay(4, () -> {
            BlockState state = helper.getLevel().getBlockState(setup.station.pos());
            BlockEntity copy = ((EntityBlock) state.getBlock()).newBlockEntity(setup.station.pos(), state);
            helper.assertTrue(copy instanceof StirredPot, "the pan's block entity has the station's part");
            StirredPot seen = (StirredPot) copy;
            copy.load(setup.pot.entity().saveWithoutMetadata());
            helper.assertFalse(seen.handson$told(), "what the pan saves does not say it was told");
            copy.load(setup.pot.entity().getUpdateTag());
            helper.assertTrue(seen.handson$told(), "what it sends says so");
            Container held = (Container) copy;
            helper.assertTrue(held.getItem(0).is(farm("bacon")) && held.getItem(0).getCount() == 2 && held.getItem(4).is(farm("butter")),
                    "a client's copy holds what lies in the pan");
            setup.station.users.clear();
            setup.container().clearContent();
            copy.load(setup.pot.entity().getUpdateTag());
            helper.assertTrue(held.isEmpty(), "and nothing once it is emptied");
            helper.succeed();
        });
    }

    /**
     * The pan's scene is one the library reads, with every part the station shows things on: the pan (the roaster's in its frame,
     * under its lid), its handles, a place for each ingredient and for the dish, and no other slot, the roast, the bowl, the badge
     * and the book.
     */
    public static void theRoastersSceneIsRead(GameTestHelper helper) {
        theRoastersSceneIsRead(helper, FARM_AND_CHARM);
    }

    /** {@link #theRoastersSceneIsRead(GameTestHelper)} at the pan {@code under}. */
    public static void theRoastersSceneIsRead(GameTestHelper helper, Under under) {
        List<String> nodes = new ArrayList<>(under.nodes());
        nodes.addAll(List.of("fat", "oil", "roast", "roast_piece", "mouth", "served", "scoop", "soup", "badge", "dial_track", "dial",
                "dial_more", "preview", "book_open"));
        for (int i = 0; i < Pot.INGREDIENTS; i++) {
            nodes.addAll(List.of("item_" + i, "look_" + i, "melt_" + i));
        }
        StationTests.sceneIsRead(helper, under.scene(), nodes, CookingPotStation.SCHEMA,
                List.of(CookingPotStation.INGREDIENTS, CookingPotStation.SERVED));
        helper.succeed();
    }
}
