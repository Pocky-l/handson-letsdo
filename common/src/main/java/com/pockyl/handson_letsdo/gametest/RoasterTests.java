package com.pockyl.handson_letsdo.gametest;

import net.minecraft.world.level.GameType;
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
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_letsdo.Cookable;
import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.station.CookingPotStation;
import com.pockyl.handson_letsdo.station.Dish;
import com.pockyl.handson_letsdo.station.FarmCharmDishes;
import com.pockyl.handson_letsdo.station.Pot;
import com.pockyl.handson_letsdo.station.PotCooking;
import com.pockyl.handson_letsdo.station.PotLogic;

import java.util.ArrayList;
import java.util.List;

/**
 * The roaster's rules against a real roaster of Farm &amp; Charm: the cooking pot's ({@link PotTests}), on the roaster's block and
 * recipes. They pass at once without Farm &amp; Charm.
 */
public final class RoasterTests {
    private RoasterTests() {
    }

    private static Item farm(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(FarmCharmDishes.MOD_ID, path));
    }

    private record Setup(TestStation station, PotLogic logic, Pot pot, PotCooking cooking) {
        Container container() {
            return pot.container();
        }
    }

    /** A roaster at {@code at} on a magma block, with the logic the station would make for it. */
    private static Setup roaster(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at.below(), Blocks.MAGMA_BLOCK);
        helper.setBlock(at, Pot.ROASTER.blockSupplier().get());
        BlockPos pos = helper.absolutePos(at);
        TestStation station = new TestStation(helper.getLevel(), pos, CookingPotStation.ROASTER_TYPE);
        Pot pot = Pot.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(pot != null && pot.kind() == Pot.ROASTER, "Farm & Charm's roaster is the roaster the station was made for");
        helper.assertTrue(pot.cooking() != null, "the roaster waits to be swirled: the stirring is in it");
        return new Setup(station, new PotLogic(station, Pot.ROASTER), pot, pot.cooking());
    }

    private static boolean withoutFarmAndCharm(GameTestHelper helper) {
        if (HandsOnLetsDo.has(FarmCharmDishes.MOD_ID)) {
            return false;
        }
        helper.succeed();
        return true;
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
        boolean installed = HandsOnLetsDo.has(FarmCharmDishes.MOD_ID);
        helper.assertTrue(Diegesis.stationType(CookingPotStation.ROASTER_ID).isPresent() == (installed && Pot.ROASTER.active()),
                "the roaster's station exists with Farm & Charm and only with it");
        if (!installed) {
            helper.succeed();
            return;
        }
        Setup setup = roaster(helper, new BlockPos(1, 2, 1));
        BlockState state = helper.getLevel().getBlockState(setup.station.pos());
        helper.assertTrue(Diegesis.stationFor(state).filter(type -> type == CookingPotStation.ROASTER_TYPE).isPresent(), "the roaster opens its station");
        helper.assertTrue(setup.container().getContainerSize() == Pot.SLOTS, "eight slots");
        int dishes = Pot.ROASTER.dishes(helper.getLevel()).size();
        helper.assertTrue(dishes >= 9, "Farm & Charm's roasts are read, got " + dishes);
        helper.assertTrue(setup.pot.isHeated(), "a magma block under the roaster heats it");
        helper.succeed();
    }

    /**
     * A roast a cook laid in at the station waits to be swirled: with a bowl lying in the roaster, where Farm &amp; Charm alone
     * would cook, nothing moves. Swirled, the whole pan roasts as one, and a bowl takes a serving: three plates of bacon with eggs
     * for each set, one of each thing gone from every slot.
     */
    public static void aSwirledRoasterRoastsAndAnUnswirledOneWaits(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        Setup setup = roaster(helper, new BlockPos(1, 2, 1));
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
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
     * Food hoppers bring, bowls too, roasts Farm &amp; Charm's way with nobody at the roaster: one serving in its own time, into
     * its dish slot.
     */
    public static void aHopperFedRoasterRoastsUpstreamsWay(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        Setup setup = roaster(helper, at);
        // Its food laid as a line of hoppers lays it, one thing a slot, the last brought by a hopper; its bowl comes from the side.
        Dish cod = Pot.ROASTER.dishes(helper.getLevel()).stream().filter(dish -> dish.result().is(farm("cooked_cod"))).findFirst().orElse(null);
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
                helper.assertTrue(began[0] >= 0 && Math.abs(took - Pot.UPSTREAM_TIME) <= 2, "in Farm & Charm's own time ("
                        + Pot.UPSTREAM_TIME + "), took " + took);
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
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        List<Dish> dishes = Pot.ROASTER.dishes(helper.getLevel()).stream().filter(dish -> !dish.learned() && Cookable.all(dish.ingredients()))
                .toList();
        BlockPos at = new BlockPos(1, 2, 1);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
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
                    setup[0] = roaster(helper, at);
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
     * The roaster's scene is one the library reads, with every part the station shows things on: the pan in its frame, its lid and
     * handles, a place for each ingredient and for the dish, and no other slot, the roast, the bowl, the badge and the book.
     */
    public static void theRoastersSceneIsRead(GameTestHelper helper) {
        List<String> nodes = new ArrayList<>(List.of("pot", "frame", "pan", "lid", "ladle", "ladle_left", "roast", "mouth", "served", "scoop", "soup",
                "badge", "dial_track", "dial", "preview", "book_open"));
        for (int i = 0; i < Pot.INGREDIENTS; i++) {
            nodes.addAll(List.of("item_" + i, "melt_" + i));
        }
        StationTests.sceneIsRead(helper, "roaster", nodes, CookingPotStation.SCHEMA, List.of(CookingPotStation.INGREDIENTS, CookingPotStation.SERVED));
        helper.succeed();
    }
}
