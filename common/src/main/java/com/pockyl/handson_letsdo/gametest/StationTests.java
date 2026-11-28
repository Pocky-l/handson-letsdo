package com.pockyl.handson_letsdo.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.world.level.GameType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
import com.pockyl.diegesis.api.scene.NodeDefinition;
import com.pockyl.diegesis.api.scene.SceneDefinition;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.handson_letsdo.HandsOnConfig;
import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.station.Barrel;
import com.pockyl.handson_letsdo.station.BarrelLogic;
import com.pockyl.handson_letsdo.station.BarrelStation;
import com.pockyl.handson_letsdo.station.VineryWines;
import com.pockyl.handson_letsdo.station.Wine;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The game tests of both loaders; each loader's holder class calls them. Those of a mod's stations pass at once without it. */
public final class StationTests {
    /** Longer than a wine takes with Vinery's settings as they come (6000 ticks): one test waits for a wine. */
    public static final int WINE_TIMEOUT = 6600;

    private StationTests() {
    }

    /** The mod starts with any set of the series' mods, also with none of them. */
    public static void modLoads(GameTestHelper helper) {
        helper.succeed();
    }

    private static Item vinery(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(BarrelStation.MOD_ID, path));
    }

    private record Setup(TestStation station, BarrelLogic logic, Barrel barrel) {
    }

    /** A barrel at {@code at} with the logic the station would make for it. */
    private static Setup barrel(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at.below(), Blocks.STONE);
        helper.setBlock(at, BarrelStation.BARREL.get());
        BlockPos pos = helper.absolutePos(at);
        TestStation station = new TestStation(helper.getLevel(), pos, BarrelStation.TYPE);
        Barrel barrel = Barrel.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(barrel != null, "Vinery's barrel is the barrel the station was made for");
        return new Setup(station, new BarrelLogic(station), barrel);
    }

    private static boolean withoutVinery(GameTestHelper helper) {
        if (HandsOnLetsDo.has(BarrelStation.MOD_ID)) {
            return false;
        }
        helper.succeed();
        return true;
    }

    private static CompoundTag lid(boolean open) {
        CompoundTag data = new CompoundTag();
        data.putBoolean(BarrelStation.OPEN, open);
        return data;
    }

    /** The cook puts the hatch back on, as the last cook to go does: the barrel ferments only shut. */
    private static void shut(Setup setup, Player player) {
        setup.logic.onCustomAction(setup.station, player, BarrelStation.ACTION_LID, lid(false));
    }

    private static void draw(Setup setup, Player player, boolean all) {
        CompoundTag data = new CompoundTag();
        data.putBoolean(BarrelStation.ALL, all);
        setup.logic.onCustomAction(setup.station, player, BarrelStation.ACTION_DRAW, data);
    }

    private static void pour(Setup setup, Player player, ItemStack juice, boolean all) {
        player.inventoryMenu.setCarried(juice);
        CompoundTag data = new CompoundTag();
        data.putBoolean(BarrelStation.ALL, all);
        setup.logic.onCustomAction(setup.station, player, BarrelStation.ACTION_POUR, data);
    }

    /**
     * The barrel is a station exactly when Vinery is installed, and then its block entity is what the station expects: six slots
     * by face, four numbers.
     */
    public static void theBarrelIsAStationOnlyWithVinery(GameTestHelper helper) {
        boolean vinery = HandsOnLetsDo.has(BarrelStation.MOD_ID);
        helper.assertTrue(Diegesis.stationType(BarrelStation.ID).isPresent() == (vinery && HandsOnConfig.fermentationBarrel()),
                "the barrel's station exists with Vinery and only with it");
        if (!vinery) {
            helper.succeed();
            return;
        }
        Setup setup = barrel(helper, new BlockPos(1, 2, 1));
        BlockState state = helper.getLevel().getBlockState(setup.station.pos());
        helper.assertTrue(Diegesis.stationFor(state).filter(type -> type == BarrelStation.TYPE).isPresent(), "the barrel opens its station");
        helper.assertTrue(setup.barrel.container().getContainerSize() == Barrel.SLOTS, "six slots");
        helper.assertTrue(setup.barrel.total() > 0, "the barrel tells how long a wine takes, got " + setup.barrel.total());
        helper.assertTrue(setup.barrel.level() == 0 && setup.barrel.juice().isEmpty(), "a new barrel is empty");
        helper.assertTrue(Barrel.of(helper.getLevel().getBlockEntity(setup.station.pos().below())) == null, "stone is no barrel");
        int wines = VineryWines.all(helper.getLevel()).size();
        helper.assertTrue(wines >= 20, "Vinery's wines are read, got " + wines);
        helper.succeed();
    }

    /**
     * What is put in through the station lies in the barrel's own container, and what comes into the barrel is seen at the station.
     * Juice is poured straight in, one bottle a click or with shift all the barrel takes, and its bottles come back empty.
     */
    public static void theStationsSlotsAreTheBarrelsOwn(GameTestHelper helper) {
        if (withoutVinery(helper)) {
            return;
        }
        Setup setup = barrel(helper, new BlockPos(1, 2, 1));
        Container container = setup.barrel.container();
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        SlotRef first = SlotRef.of(BarrelStation.INGREDIENTS, 0);
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, first, new ItemStack(Items.SUGAR)), "nothing goes in through the hatch");
        setup.logic.onCustomAction(setup.station, player, BarrelStation.ACTION_LID, lid(true));
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, first, new ItemStack(Items.SUGAR)), "sugar is in a wine: it goes in");
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, first, new ItemStack(Items.COBBLESTONE)), "no wine takes cobblestone");
        setup.station.state().setItem(first, new ItemStack(Items.SUGAR, 3));
        helper.assertTrue(container.getItem(Barrel.FIRST_INGREDIENT).is(Items.SUGAR) && container.getItem(Barrel.FIRST_INGREDIENT).getCount() == 3,
                "the sugar lies in the barrel's first ingredient slot");
        container.setItem(Barrel.WINE_SLOT, new ItemStack(vinery("red_wine"), 2));
        setup.station.state().pollStorages();
        helper.assertTrue(setup.station.state().getItem(BarrelStation.SERVED_SLOT).is(vinery("red_wine")), "the barrel's wine is the station's");
        helper.assertFalse(BarrelStation.SCHEMA.slotGroups().get(BarrelStation.SERVED).rules().accepts().test(new ItemStack(Items.SUGAR)),
                "nothing is put where the wine comes out");
        pour(setup, player, new ItemStack(vinery("red_grapejuice"), 2), false);
        helper.assertTrue(setup.barrel.level() == VineryWines.perBottle() && setup.barrel.juice().equals("red_general"),
                "one bottle of juice is poured straight in, got " + setup.barrel.level() + " of " + setup.barrel.juice());
        helper.assertTrue(player.inventoryMenu.getCarried().getCount() == 1 && player.getInventory().countItem(vinery("wine_bottle")) == 1,
                "the bottle it came in comes back empty");
        helper.assertTrue(setup.station.effects.contains(BarrelStation.EFFECT_POURED), "everybody sees it poured");
        helper.assertTrue(BarrelStation.juiceOf(setup.station.state()).equals("red_general")
                && setup.station.state().data().getInt(BarrelStation.LEVEL) == VineryWines.perBottle(), "the station shows red juice");
        pour(setup, player, new ItemStack(vinery("white_grapejuice")), false);
        helper.assertTrue(setup.barrel.juice().equals("red_general") && setup.barrel.level() == VineryWines.perBottle(),
                "white juice is not poured onto red");
        player.getInventory().clearContent();
        pour(setup, player, new ItemStack(vinery("red_grapejuice"), 8), true);
        int more = VineryWines.capacity() / VineryWines.perBottle() - 1;
        helper.assertTrue(setup.barrel.level() == VineryWines.capacity() && player.inventoryMenu.getCarried().getCount() == 8 - more
                && player.getInventory().countItem(vinery("wine_bottle")) == more,
                "with shift as many bottles as the barrel still takes, their bottles back, got " + setup.barrel.level());
        helper.succeed();
    }

    /**
     * The hatch is the cook's: a click takes it off and puts it back, juice brought to a shut barrel takes it off, and the last cook
     * to go puts it back on, as does the barrel itself when nobody is at it.
     */
    public static void theHatchIsTakenOffByTheCook(GameTestHelper helper) {
        if (withoutVinery(helper)) {
            return;
        }
        Setup setup = barrel(helper, new BlockPos(1, 2, 1));
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        setup.station.users.add(player);
        helper.assertFalse(BarrelStation.isOpen(setup.station.state()), "the barrel comes shut");
        setup.logic.onCustomAction(setup.station, player, BarrelStation.ACTION_LID, lid(true));
        helper.assertTrue(BarrelStation.isOpen(setup.station.state()), "a click takes the hatch off");
        setup.logic.onCustomAction(setup.station, player, BarrelStation.ACTION_LID, lid(false));
        helper.assertFalse(BarrelStation.isOpen(setup.station.state()), "another puts it back");
        pour(setup, player, new ItemStack(vinery("red_grapejuice")), false);
        helper.assertTrue(BarrelStation.isOpen(setup.station.state()), "juice brought to the shut barrel takes the hatch off");
        setup.logic.onClose(setup.station, player);
        helper.assertFalse(BarrelStation.isOpen(setup.station.state()), "the last cook to go puts it back on");
        setup.logic.onCustomAction(setup.station, player, BarrelStation.ACTION_LID, lid(true));
        setup.station.users.clear();
        setup.logic.tick(setup.station);
        helper.assertFalse(BarrelStation.isOpen(setup.station.state()), "a barrel nobody is at is never left open");
        helper.succeed();
    }

    /**
     * The barrel ferments only shut: with its hatch off, what lies in it (also what a hopper brought in meanwhile) does not ferment
     * on, its count waits where it was and the station tells it as standing still; once the hatch is back on it goes on from there.
     */
    public static void theBarrelFermentsOnlyShut(GameTestHelper helper) {
        if (withoutVinery(helper)) {
            return;
        }
        Setup setup = barrel(helper, new BlockPos(1, 2, 1));
        Container container = setup.barrel.container();
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        setup.station.users.add(player);
        BlockPos side = new BlockPos(2, 2, 1);
        helper.setBlock(side, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
        ((Container) helper.getBlockEntity(side)).setItem(0, new ItemStack(Items.SUGAR));
        // The juice brought takes the hatch off.
        pour(setup, player, new ItemStack(vinery("red_grapejuice")), false);
        helper.assertTrue(BarrelStation.isOpen(setup.station.state()), "the hatch is off");
        int total = VineryWines.fermentationTime();
        helper.onEachTick(() -> setup.logic.tick(setup.station));
        helper.runAfterDelay(14, () -> {
            boolean sugar = false;
            for (int i = 0; i < Barrel.INGREDIENTS; i++) {
                sugar |= container.getItem(Barrel.FIRST_INGREDIENT + i).is(Items.SUGAR);
            }
            helper.assertTrue(sugar, "the hopper laid its sugar into the open barrel");
            helper.assertTrue(setup.barrel.time() == 0, "nothing ferments with the hatch off, counted " + setup.barrel.time());
            setup.barrel.setTime(total - 1);
        });
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(setup.barrel.portions() == 0, "no wine is made with the hatch off");
            helper.assertTrue(setup.barrel.time() == total - 1, "its count waits, at " + setup.barrel.time() + " of " + total);
            CompoundTag data = setup.station.state().data();
            helper.assertTrue(data.getInt(BarrelStation.HELD) == total - 1 && !data.contains(BarrelStation.STARTED),
                    "the station tells the count standing still");
            setup.logic.onCustomAction(setup.station, player, BarrelStation.ACTION_LID, lid(false));
        });
        helper.runAfterDelay(24, () -> {
            helper.assertTrue(setup.barrel.portions() == 1, "shut, it goes on and the wine is made, got " + setup.barrel.portions());
            helper.assertFalse(setup.station.state().data().contains(BarrelStation.HELD), "nothing stands still any more");
            helper.succeed();
        });
    }

    private static Wine wineMaking(GameTestHelper helper, String result) {
        Wine wine = VineryWines.all(helper.getLevel()).stream().filter(it -> it.result().is(vinery(result))).findFirst().orElse(null);
        helper.assertTrue(wine != null, "Vinery makes " + result);
        return wine;
    }

    /**
     * Everything that lies in the barrel ferments together, in the time Vinery gives one wine, and no bottle is needed for it: four
     * bottles of red juice and five sugar make five red wines, which wait in the barrel; the juice they were made of is gone. Empty
     * bottles a hopper puts into the barrel's bottle slot are filled with it, into its wine slot, for the hopper under it.
     */
    public static void everythingInTheBarrelFermentsAtOnce(GameTestHelper helper) {
        if (withoutVinery(helper)) {
            return;
        }
        Setup setup = barrel(helper, new BlockPos(1, 2, 1));
        Container container = setup.barrel.container();
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        Wine red = wineMaking(helper, "red_wine");
        pour(setup, player, new ItemStack(vinery("red_grapejuice"), 4), true);
        container.setItem(Barrel.FIRST_INGREDIENT, new ItemStack(Items.SUGAR, 5));
        shut(setup, player);
        helper.runAfterDelay(2, () -> {
            setup.logic.tick(setup.station);
            helper.assertTrue(setup.barrel.time() > 0, "it ferments with no bottle in the barrel");
            helper.assertTrue(setup.station.state().data().getInt(BarrelStation.BATCH) == 5, "five wines in one go, got "
                    + setup.station.state().data().getInt(BarrelStation.BATCH));
            setup.barrel.setTime(VineryWines.fermentationTime() - 1);
        });
        helper.runAfterDelay(4, () -> {
            setup.logic.tick(setup.station);
            helper.assertTrue(setup.barrel.portions() == 5 && setup.barrel.brew().handson$wine().equals(red.id().toString()),
                    "five red wines wait in the barrel, got " + setup.barrel.portions());
            helper.assertTrue(container.getItem(Barrel.FIRST_INGREDIENT).isEmpty(), "every sugar was used");
            helper.assertTrue(setup.barrel.level() == VineryWines.capacity() - 5 * red.juiceAmount(), "the juice they were made of is gone, "
                    + setup.barrel.level() + " left");
            helper.assertTrue(setup.station.state().data().getInt(BarrelStation.STORED) == 5, "the station shows them");
            container.setItem(Barrel.BOTTLE_SLOT, new ItemStack(vinery("wine_bottle"), 2));
        });
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(container.getItem(Barrel.WINE_SLOT).is(vinery("red_wine")) && container.getItem(Barrel.WINE_SLOT).getCount() == 2
                    && container.getItem(Barrel.BOTTLE_SLOT).isEmpty() && setup.barrel.portions() == 3,
                    "a hopper's bottles are filled into the wine slot, got " + container.getItem(Barrel.WINE_SLOT));
            helper.succeed();
        });
    }

    /**
     * The wine is drawn at the bung into empty wine bottles: with an empty hand from one in the inventory (none, nothing), from the
     * cursor's back onto the cursor, with shift as many as are held; anything else in the hand draws nothing. Bottled wine in the
     * wine slot comes to an empty hand, and with nothing left to draw the empty bottles a hopper left come back.
     */
    public static void theWineIsDrawnAtTheBung(GameTestHelper helper) {
        if (withoutVinery(helper)) {
            return;
        }
        Setup setup = barrel(helper, new BlockPos(1, 2, 1));
        Container container = setup.barrel.container();
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        Item wine = vinery("red_wine");
        Item bottle = vinery("wine_bottle");
        setup.barrel.brew().handson$setWine(wineMaking(helper, "red_wine").id().toString(), 4);
        draw(setup, player, false);
        helper.assertTrue(setup.barrel.portions() == 4 && player.getInventory().countItem(wine) == 0, "no bottle, no wine");
        player.getInventory().add(new ItemStack(bottle, 2));
        draw(setup, player, false);
        helper.assertTrue(setup.barrel.portions() == 3 && player.getInventory().countItem(wine) == 1
                && player.getInventory().countItem(bottle) == 1, "an empty hand fills a bottle from the inventory");
        helper.assertTrue(setup.station.effects.contains(BarrelStation.EFFECT_DRAWN), "everybody sees it drawn");
        player.inventoryMenu.setCarried(new ItemStack(Items.SUGAR));
        draw(setup, player, false);
        helper.assertTrue(setup.barrel.portions() == 3, "sugar in the hand draws nothing");
        player.inventoryMenu.setCarried(new ItemStack(bottle));
        draw(setup, player, false);
        helper.assertTrue(player.inventoryMenu.getCarried().is(wine) && setup.barrel.portions() == 2, "the bottle on the cursor is filled there");
        player.inventoryMenu.setCarried(new ItemStack(bottle, 3));
        draw(setup, player, true);
        helper.assertTrue(setup.barrel.portions() == 0 && player.inventoryMenu.getCarried().getCount() == 1
                && player.getInventory().countItem(wine) == 3, "shift fills as many as wait; the wine goes into the inventory");
        helper.assertTrue(setup.barrel.brew().handson$wine().isEmpty(), "the barrel holds no wine any more");
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        container.setItem(Barrel.WINE_SLOT, new ItemStack(wine, 2));
        draw(setup, player, true);
        helper.assertTrue(container.getItem(Barrel.WINE_SLOT).isEmpty() && player.getInventory().countItem(wine) == 5,
                "bottled wine comes to an empty hand");
        container.setItem(Barrel.BOTTLE_SLOT, new ItemStack(bottle, 5));
        int before = player.getInventory().countItem(bottle);
        draw(setup, player, false);
        helper.assertTrue(container.getItem(Barrel.BOTTLE_SLOT).isEmpty() && player.getInventory().countItem(bottle) == before + 5,
                "with nothing to draw, the bottles a hopper left come back");
        helper.succeed();
    }

    /** Juice comes back out at the opening into empty wine bottles, a bottle's worth each; what is less than a bottle stays. */
    public static void theJuiceComesBackIntoBottles(GameTestHelper helper) {
        if (withoutVinery(helper)) {
            return;
        }
        Setup setup = barrel(helper, new BlockPos(1, 2, 1));
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        pour(setup, player, new ItemStack(vinery("red_grapejuice"), 2), true);
        player.getInventory().clearContent();
        player.inventoryMenu.setCarried(new ItemStack(vinery("wine_bottle"), 3));
        CompoundTag back = new CompoundTag();
        back.putBoolean(BarrelStation.ALL, true);
        setup.logic.onCustomAction(setup.station, player, BarrelStation.ACTION_LID, lid(false));
        setup.logic.onCustomAction(setup.station, player, BarrelStation.ACTION_DRAW_BACK, back);
        helper.assertTrue(setup.barrel.level() == 2 * VineryWines.perBottle(), "nothing comes out through the hatch");
        setup.logic.onCustomAction(setup.station, player, BarrelStation.ACTION_LID, lid(true));
        setup.logic.onCustomAction(setup.station, player, BarrelStation.ACTION_DRAW_BACK, back);
        ItemStack juice = VineryWines.bottleOf("red_general");
        helper.assertTrue(setup.barrel.level() == 0 && player.inventoryMenu.getCarried().getCount() == 1
                && player.getInventory().countItem(juice.getItem()) == 2, "two bottles of red juice back, got " + setup.barrel.level());
        helper.assertTrue(setup.station.effects.contains(BarrelStation.EFFECT_DRAWN_BACK), "everybody sees it taken back");
        helper.succeed();
    }

    /**
     * The same wine from the same things, once laid in by hoppers with nobody near (the bottle its juice came in is filled for the
     * hopper under it) and once through the station (it waits in the barrel and is drawn): the same bottle, as many, after as many
     * ticks of fermenting, as much juice left.
     */
    public static void wineThroughTheStationIsWineByHoppers(GameTestHelper helper) {
        if (withoutVinery(helper)) {
            return;
        }
        BlockPos alone = new BlockPos(1, 3, 1);
        helper.setBlock(alone, BarrelStation.BARREL.get());
        helper.setBlock(alone.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        helper.setBlock(alone.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        helper.setBlock(alone.east(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
        ((Container) helper.getBlockEntity(alone.above())).setItem(0, new ItemStack(vinery("red_grapejuice")));
        ((Container) helper.getBlockEntity(alone.east())).setItem(0, new ItemStack(Items.SUGAR));
        Barrel byHoppers = Barrel.of(helper.getBlockEntity(alone));
        Container below = (Container) helper.getBlockEntity(alone.below());

        Setup setup = barrel(helper, new BlockPos(5, 2, 1));
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        pour(setup, player, new ItemStack(vinery("red_grapejuice")), false);
        setup.station.state().setItem(SlotRef.of(BarrelStation.INGREDIENTS, 0), new ItemStack(Items.SUGAR));

        // Ticks each barrel fermented: from the first tick its count ran to the tick its wine was there.
        long[] began = {-1, -1};
        long[] done = {-1, -1};
        ItemStack[] wine = {ItemStack.EMPTY, ItemStack.EMPTY};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            setup.logic.tick(setup.station);
            Barrel[] barrels = {byHoppers, setup.barrel};
            for (int i = 0; i < 2; i++) {
                if (began[i] < 0 && barrels[i].time() > 0) {
                    began[i] = now - barrels[i].time();
                }
            }
            if (done[0] < 0) {
                ItemStack made = byHoppers.container().getItem(Barrel.WINE_SLOT);
                made = made.isEmpty() ? below.getItem(0) : made;
                if (!made.isEmpty()) {
                    done[0] = now;
                    wine[0] = made.copy();
                }
            }
            if (done[1] < 0 && setup.barrel.portions() > 0) {
                done[1] = now;
                player.getInventory().clearContent();
                player.inventoryMenu.setCarried(new ItemStack(vinery("wine_bottle")));
                draw(setup, player, true);
                wine[1] = player.inventoryMenu.getCarried().copy();
            }
            if (done[0] >= 0 && done[1] >= 0) {
                helper.assertTrue(wine[0].is(vinery("red_wine")) && ItemStack.isSameItem(wine[0], wine[1]) && wine[0].getCount() == wine[1].getCount(),
                        "the same wine, as many: " + wine[0] + " by hoppers, " + wine[1] + " at the station");
                long ticksAlone = done[0] - began[0];
                long ticksAtStation = done[1] - began[1];
                helper.assertTrue(Math.abs(ticksAlone - ticksAtStation) <= 2 && Math.abs(ticksAlone - VineryWines.fermentationTime()) <= 2,
                        "as long as Vinery takes (" + VineryWines.fermentationTime() + "): " + ticksAlone + " by hoppers, " + ticksAtStation
                                + " at the station");
                helper.assertTrue(setup.barrel.level() == byHoppers.level(), "as much juice left in both");
                helper.assertTrue(setup.station.effects.contains(BarrelStation.EFFECT_BOTTLED), "the station shows the wine done");
                helper.succeed();
            }
        });
    }

    /**
     * What the barrel sends its clients shows the wine that waits in it, and after the last bottle is drawn from an emptied barrel
     * holds nothing: a client's copy that had shown juice, ingredients and wine is empty again.
     */
    public static void aClientsCopyOfAnEmptiedBarrelIsEmpty(GameTestHelper helper) {
        if (withoutVinery(helper)) {
            return;
        }
        Setup setup = barrel(helper, new BlockPos(1, 2, 1));
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        // A wine that takes a whole bottle of juice: the barrel is dry when it is done.
        Wine whole = VineryWines.all(helper.getLevel()).stream().filter(wine -> wine.bottle() && wine.juiceAmount() == VineryWines.perBottle()
                && wine.juice().equals("red_general")).findFirst().orElse(null);
        helper.assertTrue(whole != null, "Vinery has a wine of a whole bottle of red juice");
        give(player, whole);
        fill(setup, player, whole);
        shut(setup, player);
        BlockEntity real = helper.getLevel().getBlockEntity(setup.station.pos());
        BlockEntity copy = ((EntityBlock) real.getBlockState().getBlock()).newBlockEntity(setup.station.pos(), real.getBlockState());
        helper.runAfterDelay(3, () -> {
            copy.loadWithComponents(real.getUpdateTag(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            Barrel seen = Barrel.of(copy);
            helper.assertTrue(seen != null && seen.level() == VineryWines.perBottle() && !seen.container().isEmpty(),
                    "the client's copy shows the juice and what lies in it");
            setup.barrel.setTime(VineryWines.fermentationTime() - 1);
        });
        helper.runAfterDelay(6, () -> {
            setup.logic.tick(setup.station);
            copy.loadWithComponents(real.getUpdateTag(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            helper.assertTrue(setup.barrel.portions() == 1 && Barrel.of(copy).portions() == 1, "the wine is done and the client's copy shows it");
            draw(setup, player, false);
            copy.loadWithComponents(real.getUpdateTag(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            Barrel seen = Barrel.of(copy);
            helper.assertTrue(seen.container().isEmpty() && seen.portions() == 0, "nothing lies in the client's copy");
            helper.assertTrue(seen.level() == 0 && seen.juice().isEmpty(), "and no juice stands in it, got " + seen.level());
            helper.succeed();
        });
    }

    /** Puts what {@code wine} takes into the player's inventory: the bottles of its juice, one of each ingredient, empty bottles. */
    private static void give(Player player, Wine wine) {
        player.getInventory().clearContent();
        for (Item item : BuiltInRegistries.ITEM) {
            if (wine.juice().equals(VineryWines.juiceOf(new ItemStack(item)))) {
                player.getInventory().add(new ItemStack(item, (wine.juiceAmount() + VineryWines.perBottle() - 1) / VineryWines.perBottle()));
                break;
            }
        }
        for (Ingredient ingredient : wine.ingredients()) {
            if (ingredient.getItems().length > 0) {
                player.getInventory().add(ingredient.getItems()[0].copyWithCount(1));
            }
        }
        player.getInventory().add(new ItemStack(vinery("wine_bottle"), 2));
    }

    private static void fill(Setup setup, Player player, Wine wine) {
        CompoundTag data = new CompoundTag();
        data.putString(BarrelStation.WINE, wine.id().toString());
        setup.logic.onCustomAction(setup.station, player, BarrelStation.ACTION_FILL, data);
    }

    /**
     * Every wine Vinery and its addons have, picked in the book: its juice and ingredients go in from the inventory as the barrel
     * reads them, it starts to ferment, and at the end of its time that wine is there: waiting in the barrel, or bottled in its wine
     * slot when its recipe needs no bottle. The wait is skipped.
     */
    public static void everyWineIsLaidFromTheBookAndFerments(GameTestHelper helper) {
        if (withoutVinery(helper)) {
            return;
        }
        List<Wine> wines = VineryWines.all(helper.getLevel());
        BlockPos at = new BlockPos(1, 2, 1);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        Setup[] setup = new Setup[1];
        int step = 6;
        helper.onEachTick(() -> {
            int tick = (int) helper.getTick();
            int index = tick / step;
            if (index >= wines.size()) {
                helper.succeed();
                return;
            }
            Wine wine = wines.get(index);
            switch (tick % step) {
                case 0 -> {
                    // A new barrel for each wine.
                    if (setup[0] != null) {
                        setup[0].barrel.container().clearContent();
                        setup[0].barrel.brew().handson$setWine("", 0);
                    }
                    helper.setBlock(at, Blocks.AIR);
                    setup[0] = barrel(helper, at);
                    give(player, wine);
                    fill(setup[0], player, wine);
                    shut(setup[0], player);
                }
                case 3 -> {
                    setup[0].logic.tick(setup[0].station);
                    helper.assertTrue(setup[0].barrel.time() > 0, wine.id() + " ferments once the book laid it in");
                    String making = setup[0].station.state().data().getString(BarrelStation.MAKING);
                    helper.assertTrue(wine.id().toString().equals(making), wine.id() + " is what the station says is made, got " + making);
                    setup[0].barrel.setTime(VineryWines.fermentationTime() - 1);
                }
                case 5 -> {
                    Barrel barrel = setup[0].barrel;
                    boolean waits = barrel.portions() > 0 && barrel.brew().handson$wine().equals(wine.id().toString());
                    helper.assertTrue(waits || ItemStack.isSameItem(barrel.container().getItem(Barrel.WINE_SLOT), wine.result()),
                            wine.id() + " is made, got " + barrel.portions() + " waiting and " + barrel.container().getItem(Barrel.WINE_SLOT));
                }
                default -> {
                }
            }
        });
    }

    /** All the station tells a client who comes to a full barrel is far below what one packet may hold. */
    public static void theStationsDataStaysSmall(GameTestHelper helper) {
        if (withoutVinery(helper)) {
            return;
        }
        Setup setup = barrel(helper, new BlockPos(1, 2, 1));
        Container container = setup.barrel.container();
        container.setItem(Barrel.FIRST_INGREDIENT, new ItemStack(Items.SUGAR, 64));
        container.setItem(Barrel.FIRST_INGREDIENT + 1, new ItemStack(Items.FEATHER, 64));
        container.setItem(Barrel.FIRST_INGREDIENT + 2, new ItemStack(Items.BLAZE_ROD, 64));
        container.setItem(Barrel.BOTTLE_SLOT, new ItemStack(vinery("wine_bottle"), 16));
        container.setItem(Barrel.WINE_SLOT, new ItemStack(vinery("red_wine"), 16));
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        pour(setup, player, new ItemStack(vinery("red_grapejuice"), 4), true);
        helper.runAfterDelay(3, () -> {
            container.setItem(Barrel.JUICE_SLOT, new ItemStack(vinery("red_grapejuice"), 16));
            setup.logic.tick(setup.station);
            setup.station.state().markAllDirty();
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            setup.station.state().writeChanges(buffer, true);
            int bytes = buffer.readableBytes();
            helper.assertTrue(bytes > 0 && bytes < 32 * 1024, "the station's whole data is " + bytes + " bytes");
            helper.succeed();
        });
    }

    /**
     * The barrel's scene is one the library reads, with every part the station shows things on: a place for each ingredient and
     * no other slot (juice is poured, wine is drawn, bottles are brought), the liquid, the hatch, the bung, the hands, the badge and
     * the book.
     */
    public static void theBarrelsSceneIsRead(GameTestHelper helper) {
        sceneIsRead(helper, "fermentation_barrel", List.of("barrel", "lid", "juice", "mouth", "dip", "bung", "item_0", "item_1", "item_2", "offer",
                "pour_hand", "draught", "badge", "dial_track", "dial", "preview", "book_open"), BarrelStation.SCHEMA, List.of(BarrelStation.INGREDIENTS));
        helper.succeed();
    }

    /**
     * The scene file {@code name} is one the library reads, has every node of {@code nodes}, and shows the slots of the groups
     * {@code shown} of {@code schema} and of no other group.
     */
    static void sceneIsRead(GameTestHelper helper, String name, List<String> nodes, StationSchema schema, List<String> shown) {
        String path = "/assets/handson_letsdo/diegesis/scenes/" + name + ".json";
        try (InputStream in = StationTests.class.getResourceAsStream(path)) {
            helper.assertTrue(in != null, "scene " + path + " is there");
            JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            withoutNotes(root);
            // Repeated nodes (lift_0, lift_1...) are laid out as the library lays them out when it loads a scene.
            DataResult<JsonElement> expanded = com.pockyl.diegesis.impl.scene.NodeTemplates.expand(root, id -> null);
            helper.assertTrue(expanded.error().isEmpty(), "the scene's repeated nodes are laid out");
            root = expanded.result().orElse(root);
            DataResult<SceneDefinition> read = SceneDefinition.CODEC.parse(JsonOps.INSTANCE, root);
            helper.assertTrue(read.error().isEmpty(), "the scene is read: " + read.error().map(Object::toString).orElse(""));
            Set<String> ids = new HashSet<>();
            Set<String> slots = new HashSet<>();
            collect(read.result().orElseThrow().nodes(), ids, slots);
            for (String id : nodes) {
                helper.assertTrue(ids.contains(id), "the scene " + name + " has " + id);
            }
            schema.slotGroups().forEach((group, slotGroup) -> {
                boolean seen = shown.contains(group);
                for (int i = 0; i < slotGroup.size(); i++) {
                    helper.assertTrue(slots.contains(slotGroup.ref(i).toString()) == seen,
                            (seen ? "the scene shows slot " : "the scene does not show slot ") + slotGroup.ref(i));
                }
            });
        } catch (IOException exception) {
            helper.fail("scene " + path + " could not be read: " + exception);
        }
    }

    /** Takes out the notes a scene file keeps for its readers ({@code $comment}, {@code $schema}), as the library does. */
    private static void withoutNotes(JsonElement json) {
        if (json.isJsonObject()) {
            json.getAsJsonObject().entrySet().removeIf(entry -> entry.getKey().startsWith("$"));
            json.getAsJsonObject().entrySet().forEach(entry -> withoutNotes(entry.getValue()));
        } else if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(StationTests::withoutNotes);
        }
    }

    private static void collect(List<NodeDefinition> nodes, Set<String> ids, Set<String> slots) {
        for (NodeDefinition node : nodes) {
            ids.add(node.id());
            node.slot().ifPresent(slot -> slots.add(slot.slot().toString()));
            collect(node.children(), ids, slots);
        }
    }
}
