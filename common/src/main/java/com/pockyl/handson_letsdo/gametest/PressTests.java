package com.pockyl.handson_letsdo.gametest;

import net.minecraft.world.level.GameType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_letsdo.HandsOnConfig;
import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.station.BarrelStation;
import com.pockyl.handson_letsdo.station.Press;
import com.pockyl.handson_letsdo.station.PressLogic;
import com.pockyl.handson_letsdo.station.PressStation;
import com.pockyl.handson_letsdo.station.VineryPressing;

import java.util.List;

/**
 * The apple press's rules against a real press of Vinery: the station lays things into the press's own slots, the cook's presses of
 * the screw's cap press a load of up to eight at once and its juice runs quickly, each by the press's own steps; left alone, the
 * press presses and bottles in its own time. They pass at once without Vinery.
 */
public final class PressTests {
    private static final BlockPos AT = new BlockPos(1, 2, 1);

    private PressTests() {
    }

    private static Item vinery(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(BarrelStation.MOD_ID, path));
    }

    private record Setup(TestStation station, PressLogic logic, Press press) {
        Container container() {
            return press.container();
        }
    }

    /** A press at {@code at}, both of its halves as placing it sets them, with the logic the station would make for it. */
    private static Setup press(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at.below(), Blocks.STONE);
        BlockState lower = PressStation.PRESS.get().defaultBlockState();
        helper.setBlock(at, lower);
        BlockState upper = lower;
        for (var property : lower.getProperties()) {
            if (property.getName().equals("half")) {
                upper = lower.cycle(property);
            }
        }
        helper.setBlock(at.above(), upper);
        helper.assertTrue(!Press.isUpper(lower) && Press.isUpper(upper), "the press's halves are told apart");
        BlockPos pos = helper.absolutePos(at);
        TestStation station = new TestStation(helper.getLevel(), pos, PressStation.TYPE);
        Press press = Press.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(press != null, "Vinery's press is the press the station was made for");
        return new Setup(station, new PressLogic(station), press);
    }

    private static boolean withoutVinery(GameTestHelper helper) {
        if (HandsOnLetsDo.has(BarrelStation.MOD_ID)) {
            return false;
        }
        helper.succeed();
        return true;
    }

    /** {@code player} comes to the press, and its logic runs every tick from now, as it does while somebody is at a station. */
    private static void attend(GameTestHelper helper, Setup setup, Player player) {
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        helper.onEachTick(() -> setup.logic.tick(setup.station));
    }

    private static void put(Setup setup, SlotRef slot, ItemStack stack) {
        setup.station.state().setItem(slot, stack);
        setup.logic.onSlotChanged(setup.station, slot);
    }

    /**
     * The press is a station exactly when Vinery is installed, and then its block entity is what the station expects: four slots
     * and four numbers, its two steps read. Its upper half opens the station of its lower half, where it keeps everything.
     */
    public static void thePressIsAStationOnlyWithVinery(GameTestHelper helper) {
        boolean installed = HandsOnLetsDo.has(BarrelStation.MOD_ID);
        helper.assertTrue(Diegesis.stationType(PressStation.ID).isPresent() == (installed && HandsOnConfig.applePress()),
                "the press's station exists with Vinery and only with it");
        if (!installed) {
            helper.succeed();
            return;
        }
        Setup setup = press(helper, AT);
        BlockPos pos = setup.station.pos();
        BlockState lower = helper.getLevel().getBlockState(pos);
        BlockState upper = helper.getLevel().getBlockState(pos.above());
        helper.assertTrue(Diegesis.stationFor(lower).filter(type -> type == PressStation.TYPE).isPresent(), "the press opens its station");
        helper.assertTrue(Diegesis.stationFor(upper).filter(type -> type == PressStation.TYPE).isPresent(), "from its upper half too");
        helper.assertTrue(PressStation.TYPE.anchor().resolve(helper.getLevel(), pos.above(), upper).equals(pos)
                && PressStation.TYPE.anchor().resolve(helper.getLevel(), pos, lower).equals(pos), "both halves are the station of the lower one");
        helper.assertTrue(setup.container().getContainerSize() == Press.SLOTS, "four slots");
        helper.assertTrue(setup.press.mashTime() > 1 && setup.press.bottleTime() > 1, "its times are Vinery's settings");
        helper.assertTrue(!VineryPressing.mashing(helper.getLevel()).isEmpty() && !VineryPressing.bottling(helper.getLevel()).isEmpty(),
                "Vinery's pressing and bottling are read");
        helper.succeed();
    }

    /**
     * The station's slots are the press's own: what is laid at the station lies in the press, where hoppers and its menu reach
     * it, and what lies in the press is what the station shows.
     */
    public static void thePressesSlotsAreItsOwn(GameTestHelper helper) {
        if (withoutVinery(helper)) {
            return;
        }
        Setup setup = press(helper, AT);
        put(setup, PressStation.BOTTLE_SLOT, new ItemStack(BarrelStation.wineBottle(), 3));
        helper.assertTrue(setup.container().getItem(Press.BOTTLE_SLOT).getCount() == 3, "the bottles stand in the press's own bottle slot");
        Item juice = vinery("apple_juice");
        setup.container().setItem(Press.RESULT_SLOT, new ItemStack(juice, 2));
        helper.assertTrue(setup.station.state().getItem(PressStation.SERVED_SLOT).is(juice), "what the press bottled is what the station shows");
        setup.container().setItem(Press.MASH_SLOT, new ItemStack(vinery("apple_mash")));
        helper.assertTrue(setup.station.state().getItem(PressStation.MASH_SLOT).is(vinery("apple_mash")), "and its mash");
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, PressStation.FRUIT_SLOT, new ItemStack(Items.APPLE)),
                "nothing goes into the basket while its front is shut");
        open(setup, player, true);
        helper.assertTrue(setup.press.basket() != null && setup.press.basket().handson$isOpen(), "a click opens it");
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, PressStation.FRUIT_SLOT, new ItemStack(Items.APPLE))
                && !setup.logic.mayPlace(setup.station, player, PressStation.FRUIT_SLOT, new ItemStack(Items.COBBLESTONE)),
                "only what the press presses goes into the basket");
        setup.container().clearContent();
        helper.succeed();
    }

    /** The cook presses the screw's cap {@code times} times. */
    private static void pressCap(Setup setup, Player player, int times) {
        for (int i = 0; i < times; i++) {
            setup.logic.onCustomAction(setup.station, player, PressStation.ACTION_PRESS, new CompoundTag());
        }
    }

    /**
     * The owner's numbers by hand: a load is up to eight fruits, pressed together by four presses of the screw's cap. Eight apples:
     * nothing after three presses, eight apple mash after the fourth. Nine: eight after four presses, the ninth after four more. An
     * open basket is shut by the first press. Everybody sees a load pressed.
     */
    public static void aLoadOfUpToEightIsPressedByFourPresses(GameTestHelper helper) {
        if (withoutVinery(helper)) {
            return;
        }
        Item mash = vinery("apple_mash");
        Setup eight = press(helper, AT);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        eight.station.users.add(player);
        eight.logic.onOpen(eight.station, player);
        open(eight, player, true);
        put(eight, PressStation.FRUIT_SLOT, new ItemStack(Items.APPLE, 8));
        pressCap(eight, player, 3);
        helper.assertTrue(eight.press.basket().handson$presses() == 3 && !eight.press.isOpen(), "three presses count, the first shut the basket");
        helper.assertTrue(eight.container().getItem(Press.MASH_SLOT).isEmpty(), "eight apples: nothing after three presses");
        pressCap(eight, player, 1);
        helper.assertTrue(eight.container().getItem(Press.MASH_SLOT).is(mash) && eight.container().getItem(Press.MASH_SLOT).getCount() == 8
                && eight.container().getItem(Press.FRUIT_SLOT).isEmpty(), "all eight are mash after the fourth press, got "
                + eight.container().getItem(Press.MASH_SLOT));
        helper.assertTrue(eight.station.effects.contains(PressStation.EFFECT_PRESSED), "everybody saw it pressed");
        helper.assertTrue(eight.press.basket().handson$presses() == 0, "the next load starts afresh");
        eight.container().clearContent();

        helper.setBlock(AT.above(), Blocks.AIR);
        helper.setBlock(AT, Blocks.AIR);
        Setup nine = press(helper, AT);
        nine.station.users.add(player);
        nine.logic.onOpen(nine.station, player);
        nine.container().setItem(Press.FRUIT_SLOT, new ItemStack(Items.APPLE, 9));
        pressCap(nine, player, 4);
        helper.assertTrue(nine.container().getItem(Press.MASH_SLOT).getCount() == 8 && nine.container().getItem(Press.FRUIT_SLOT).getCount() == 1,
                "nine apples: eight after four presses");
        pressCap(nine, player, 3);
        helper.assertTrue(nine.container().getItem(Press.MASH_SLOT).getCount() == 8, "the ninth not before four more");
        pressCap(nine, player, 1);
        helper.assertTrue(nine.container().getItem(Press.MASH_SLOT).getCount() == 9 && nine.container().getItem(Press.FRUIT_SLOT).isEmpty(),
                "all nine after eight presses, got " + nine.container().getItem(Press.MASH_SLOT));
        nine.container().clearContent();
        helper.succeed();
    }

    /**
     * Nothing is pressed without the hand: with a cook at the press, apples in its shut basket wait however long, and a press of
     * the cap with nothing to press counts nothing. Left alone, the press presses them its own way again.
     */
    public static void nothingIsPressedWithoutTheHand(GameTestHelper helper) {
        if (withoutVinery(helper)) {
            return;
        }
        Setup setup = press(helper, AT);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        helper.onEachTick(() -> setup.logic.tick(setup.station));
        pressCap(setup, player, 2);
        helper.assertTrue(setup.press.basket().handson$presses() == 0, "pressing an empty basket counts nothing");
        helper.assertTrue(setup.station.effects.contains(PressStation.EFFECT_NOTHING), "everybody sees the cap give nothing");
        put(setup, PressStation.FRUIT_SLOT, new ItemStack(Items.APPLE, 2));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(setup.press.mashed() == 0 && setup.container().getItem(Press.FRUIT_SLOT).getCount() == 2,
                    "with a cook at it, the press presses nothing by itself, pressed " + setup.press.mashed());
            setup.station.users.clear();
        });
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(setup.press.mashed() > 0, "left alone, it presses its own way");
            setup.container().clearContent();
            helper.succeed();
        });
    }

    /**
     * The juice of a load pressed by hand runs into the tub at the hand's pace: eight apples fill it in a few seconds, not Vinery's
     * forty a bottle. No bottle is taken from the press or the cook; the clients are told it runs quickly.
     */
    public static void theJuiceOfALoadRunsQuickly(GameTestHelper helper) {
        if (withoutVinery(helper)) {
            return;
        }
        Setup setup = press(helper, AT);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        attend(helper, setup, player);
        Item juice = vinery("apple_juice");
        setup.container().setItem(Press.FRUIT_SLOT, new ItemStack(Items.APPLE, 8));
        pressCap(setup, player, Press.PRESSES_PER_LOAD);
        helper.runAfterDelay(Press.JUICE_TICKS + 2, () -> {
            helper.assertTrue(setup.press.tub().is(juice) && setup.press.tub().getCount() >= 1, "a bottle's worth runs at once, got " + setup.press.tub());
        });
        helper.runAfterDelay(Press.JUICE_TICKS * (Press.LOAD + 1), () -> {
            helper.assertTrue(setup.press.tub().getCount() == Press.LOAD, "eight bottles' worth in the tub, got " + setup.press.tub());
            helper.assertTrue(setup.container().getItem(Press.MASH_SLOT).isEmpty() && setup.container().getItem(Press.BOTTLE_SLOT).isEmpty()
                    && setup.container().getItem(Press.RESULT_SLOT).isEmpty(), "all mash ran out, no bottle stands in the press");
            helper.assertTrue(setup.station.effects.contains(PressStation.EFFECT_BOTTLED), "everybody saw the tub fill");
            setup.container().clearContent();
            setup.press.basket().handson$setTub(ItemStack.EMPTY);
            helper.succeed();
        });
    }

    /**
     * Apples pressed by hand with empty wine bottles laid into the press (a hopper's): the juice fills those bottles the press's own
     * way, quickly, into its result slot, and everybody sees it bottled.
     */
    public static void anAppleLaidAtTheStationIsPressedAndBottled(GameTestHelper helper) {
        if (withoutVinery(helper)) {
            return;
        }
        Setup setup = press(helper, AT);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        attend(helper, setup, player);
        Item juice = vinery("apple_juice");
        put(setup, PressStation.FRUIT_SLOT, new ItemStack(Items.APPLE, 2));
        put(setup, PressStation.BOTTLE_SLOT, new ItemStack(BarrelStation.wineBottle(), 2));
        helper.runAfterDelay(2, () -> {
            CompoundTag data = setup.station.state().data();
            helper.assertTrue(BuiltInRegistries.ITEM.getKey(juice).toString().equals(data.getString(PressStation.MAKING)),
                    "the station tells what comes out in the end, got " + data.getString(PressStation.MAKING));
            pressCap(setup, player, Press.PRESSES_PER_LOAD);
            helper.assertTrue(setup.container().getItem(Press.MASH_SLOT).getCount() == 2, "both apples are mash");
        });
        helper.runAfterDelay(2 + Press.JUICE_TICKS * 3, () -> {
            ItemStack made = setup.container().getItem(Press.RESULT_SLOT);
            helper.assertTrue(made.is(juice) && made.getCount() == 2, "two apple juice in the press's own bottles, got " + made);
            helper.assertTrue(setup.container().getItem(Press.BOTTLE_SLOT).isEmpty(), "both bottles used");
            helper.assertTrue(setup.press.tub().isEmpty(), "nothing ran into the tub");
            helper.assertTrue(setup.station.effects.contains(PressStation.EFFECT_BOTTLED), "everybody saw it bottled");
            setup.container().clearContent();
            helper.succeed();
        });
    }

    /**
     * With no bottles laid in, what the press bottles runs into the tub under its lip: an apple pressed by hand is bottled by the
     * press itself into a bottle it is lent, everybody sees the tub fill, no bottle is left over and none is taken. An empty wine
     * bottle on the cook's cursor is filled at the tub, which is then empty again.
     */
    public static void anAppleRunsIntoTheTubAndIsBottledThere(GameTestHelper helper) {
        if (withoutVinery(helper)) {
            return;
        }
        Setup setup = press(helper, AT);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        attend(helper, setup, player);
        Item juice = vinery("apple_juice");
        put(setup, PressStation.FRUIT_SLOT, new ItemStack(Items.APPLE));
        helper.runAfterDelay(2, () -> pressCap(setup, player, Press.PRESSES_PER_LOAD));
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(setup.container().getItem(Press.MASH_SLOT).is(vinery("apple_mash")), "the apple is mash");
            helper.assertTrue(setup.container().getItem(Press.BOTTLE_SLOT).isEmpty(), "the bottle it is lent is never left in it");
        });
        helper.runAfterDelay(4 + Press.JUICE_TICKS * 2, () -> {
            ItemStack tub = setup.press.tub();
            helper.assertTrue(tub.is(juice) && tub.getCount() == 1, "the juice ran into the tub, got " + tub);
            helper.assertTrue(setup.container().getItem(Press.RESULT_SLOT).isEmpty() && setup.container().getItem(Press.BOTTLE_SLOT).isEmpty(),
                    "no bottle stands in the press");
            helper.assertTrue(setup.station.effects.contains(PressStation.EFFECT_BOTTLED), "everybody saw the tub fill");
            helper.assertTrue(setup.station.state().getTank(PressStation.TUB).amount() == 1, "the station shows a bottle's worth in the tub");
            CompoundTag saved = helper.getLevel().getBlockEntity(helper.absolutePos(AT)).getUpdateTag(helper.getLevel().registryAccess());
            helper.assertTrue(saved.getCompound("HandsOnPress").contains("Tub"), "the clients are told what the tub holds");
            player.containerMenu.setCarried(ItemStack.EMPTY);
            player.inventoryMenu.setCarried(new ItemStack(BarrelStation.wineBottle(), 2));
            CompoundTag draw = new CompoundTag();
            draw.putBoolean(PressStation.ALL, true);
            setup.logic.onCustomAction(setup.station, player, PressStation.ACTION_DRAW, draw);
            helper.assertTrue(setup.press.tub().isEmpty(), "the tub is empty again");
            helper.assertTrue(player.getInventory().countItem(juice) == 1, "the cook has the juice");
            helper.assertTrue(player.inventoryMenu.getCarried().is(BarrelStation.wineBottle()) && player.inventoryMenu.getCarried().getCount() == 1,
                    "one of the two bottles was filled, got " + player.inventoryMenu.getCarried());
            helper.assertTrue(setup.station.effects.contains(PressStation.EFFECT_DRAWN), "everybody saw it filled");
            player.inventoryMenu.setCarried(ItemStack.EMPTY);
            setup.container().clearContent();
            helper.succeed();
        });
    }

    /**
     * A whole tub is taken at once. An empty hand with bottles in two stacks of the inventory fills as
     * many as the tub holds from both; one draw fills one; with no bottle left nothing is filled and the tub keeps its juice.
     */
    public static void aClickFillsEveryBottleTheTubHolds(GameTestHelper helper) {
        if (withoutVinery(helper)) {
            return;
        }
        Setup setup = press(helper, AT);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        attend(helper, setup, player);
        Item juice = vinery("apple_juice");
        setup.press.basket().handson$setTub(new ItemStack(juice, Press.TUB_MOST));
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        player.getInventory().setItem(3, new ItemStack(BarrelStation.wineBottle(), 4));
        player.getInventory().setItem(20, new ItemStack(BarrelStation.wineBottle(), 3));
        CompoundTag all = new CompoundTag();
        all.putBoolean(PressStation.ALL, true);
        setup.logic.onCustomAction(setup.station, player, PressStation.ACTION_DRAW, all);
        helper.assertTrue(player.getInventory().countItem(juice) == 7 && player.getInventory().countItem(BarrelStation.wineBottle()) == 0,
                "every bottle of both stacks is filled at once, got " + player.getInventory().countItem(juice));
        helper.assertTrue(setup.press.tub().is(juice) && setup.press.tub().getCount() == 1, "the tub keeps what no bottle took, got " + setup.press.tub());
        setup.logic.onCustomAction(setup.station, player, PressStation.ACTION_DRAW, all);
        helper.assertTrue(setup.press.tub().getCount() == 1, "with no bottle left nothing is filled");
        player.getInventory().setItem(3, new ItemStack(BarrelStation.wineBottle(), 2));
        setup.press.basket().handson$setTub(new ItemStack(juice, 3));
        setup.logic.onCustomAction(setup.station, player, PressStation.ACTION_DRAW, new CompoundTag());
        helper.assertTrue(setup.press.tub().getCount() == 2 && player.getInventory().countItem(BarrelStation.wineBottle()) == 1,
                "one draw fills one bottle");
        player.getInventory().clearContent();
        setup.press.basket().handson$setTub(ItemStack.EMPTY);
        setup.container().clearContent();
        helper.succeed();
    }

    /**
     * Left alone, the press presses only with its basket shut: an apple in an open basket waits, what was pressed stays, and once the
     * front is shut the press goes on in its own time. The front stays as it was left, with the block, and every client is told of it
     * and of the start of the pressing (the press's update).
     */
    public static void thePressWaitsWhileItsBasketIsOpen(GameTestHelper helper) {
        if (withoutVinery(helper)) {
            return;
        }
        Setup setup = press(helper, AT);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        open(setup, player, true);
        put(setup, PressStation.FRUIT_SLOT, new ItemStack(Items.APPLE, 2));
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(setup.press.mashed() == 0, "an open basket presses nothing, pressed " + setup.press.mashed());
            setup.logic.tick(setup.station);
            helper.assertTrue(setup.station.state().data().getBoolean(PressStation.OPEN), "the station tells it is open");
            open(setup, player, false);
        });
        helper.runAfterDelay(12, () -> {
            int pressed = setup.press.mashed();
            helper.assertTrue(pressed > 0, "shut, it presses");
            setup.logic.tick(setup.station);
            helper.assertTrue(setup.station.state().data().contains(PressStation.MASH_STARTED), "and the station tells since when");
            var entity = helper.getLevel().getBlockEntity(helper.absolutePos(AT));
            CompoundTag told = entity.getUpdateTag(helper.getLevel().registryAccess());
            helper.assertTrue(told.getCompound("HandsOnPress").contains("MashSince") && told.contains("Items"),
                    "the clients are told what it holds and since when it presses, got " + told);
            open(setup, player, true);
            CompoundTag saved = entity.saveWithoutMetadata(helper.getLevel().registryAccess());
            helper.assertTrue(saved.getCompound("HandsOnPress").getBoolean("Open"), "the open front is kept with the block");
        });
        helper.runAfterDelay(18, () -> {
            helper.assertTrue(setup.press.mashed() > 0 && setup.press.mashed() <= 8, "opened again, it waits where it was, at "
                    + setup.press.mashed());
            setup.container().clearContent();
            helper.succeed();
        });
    }

    /** The cook opens or shuts the basket's front at the station. */
    private static void open(Setup setup, Player player, boolean open) {
        CompoundTag data = new CompoundTag();
        data.putBoolean(PressStation.OPEN, open);
        setup.logic.onCustomAction(setup.station, player, PressStation.ACTION_DOORS, data);
    }

    /** Mash laid before the plate by hand is no pressing: nobody is told a fruit was pressed. */
    public static void mashLaidByHandIsNoPressing(GameTestHelper helper) {
        if (withoutVinery(helper)) {
            return;
        }
        Setup setup = press(helper, AT);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        attend(helper, setup, player);
        helper.runAfterDelay(3, () -> put(setup, PressStation.MASH_SLOT, new ItemStack(vinery("apple_mash"), 2)));
        helper.runAfterDelay(8, () -> {
            helper.assertFalse(setup.station.effects.contains(PressStation.EFFECT_PRESSED), "mash laid by hand was not pressed");
            setup.container().clearContent();
            helper.succeed();
        });
    }

    /**
     * Everything the press bottles, picked in the book: its fruit goes into the basket from the inventory, or with no fruit what is
     * bottled itself goes before the plate. No bottle is laid in: they are filled at the tub.
     */
    public static void everythingThePressBottlesIsLaidFromTheBook(GameTestHelper helper) {
        if (withoutVinery(helper)) {
            return;
        }
        List<VineryPressing.Step> mashing = VineryPressing.mashing(helper.getLevel());
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        for (VineryPressing.Step bottled : VineryPressing.bottling(helper.getLevel())) {
            helper.setBlock(AT, Blocks.AIR);
            Setup setup = press(helper, AT);
            setup.station.users.add(player);
            setup.logic.onOpen(setup.station, player);
            VineryPressing.Step pressed = mashing.stream().filter(step -> bottled.input().test(step.result())).findFirst().orElse(null);
            player.getInventory().clearContent();
            ItemStack fruit = (pressed != null ? pressed.input() : bottled.input()).getItems()[0].copyWithCount(1);
            player.getInventory().add(fruit.copy());
            player.getInventory().add(new ItemStack(BarrelStation.wineBottle(), 8));
            CompoundTag fill = new CompoundTag();
            fill.putString(PressStation.STEP, bottled.id().toString());
            setup.logic.onCustomAction(setup.station, player, PressStation.ACTION_FILL, fill);
            int slot = pressed != null ? Press.FRUIT_SLOT : Press.MASH_SLOT;
            helper.assertTrue(setup.container().getItem(slot).is(fruit.getItem()), bottled.id() + ": " + fruit + " lies in the press");
            helper.assertTrue(setup.container().getItem(Press.BOTTLE_SLOT).isEmpty() && player.getInventory().countItem(BarrelStation.wineBottle()) == 8,
                    bottled.id() + ": no bottle is laid in, got " + setup.container().getItem(Press.BOTTLE_SLOT));
            String making = setup.station.state().data().getString(PressStation.MAKING);
            helper.assertTrue(BuiltInRegistries.ITEM.getKey(bottled.result().getItem()).toString().equals(making),
                    bottled.id() + " is what the station says comes out, got " + making);
            setup.container().clearContent();
        }
        helper.setBlock(AT.above(), Blocks.AIR);
        helper.setBlock(AT, Blocks.AIR);
        helper.succeed();
    }

    /**
     * The press's scene is one the library reads, with every part the station shows things on: both blocks, the two halves of the
     * basket's front, the screw, a place for the fruit, the mash, the tub with its juice and the bottle filled there, what a
     * hopper's bottles were filled with, the badge and the book.
     */
    public static void thePressesSceneIsRead(GameTestHelper helper) {
        StationTests.sceneIsRead(helper, "apple_press", List.of("lower", "upper", "door_a", "door_b", "screw", "basket", "fruit", "mash",
                "tub", "tub_juice", "draught", "served", "lip", "badge", "dial_track", "dial", "preview", "book_open"), PressStation.SCHEMA,
                List.of(PressStation.FRUIT, PressStation.MASH, PressStation.SERVED));
        helper.succeed();
    }
}
