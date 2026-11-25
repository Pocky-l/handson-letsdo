package com.pockyl.handson_letsdo.gametest;

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
import net.minecraft.world.inventory.ContainerData;
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
import com.pockyl.handson_letsdo.Cookable;
import com.pockyl.handson_letsdo.HandsOnConfig;
import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.station.BlockNumbers;
import com.pockyl.handson_letsdo.station.BlockOwner;
import com.pockyl.handson_letsdo.station.FarmCharmDishes;
import com.pockyl.handson_letsdo.station.FarmCharmStove;
import com.pockyl.handson_letsdo.station.KitchenOven;
import com.pockyl.handson_letsdo.station.OvenLogic;
import com.pockyl.handson_letsdo.station.OvenStation;

import java.util.List;
import java.util.Optional;

/**
 * The stove's rules against a real stove of Farm &amp; Charm, the same on every loader; each loader's holder class calls them.
 * They pass at once without Farm &amp; Charm.
 */
public final class StoveTests {
    /** Longer than three dishes take the stove: one test waits for them with nobody near. */
    public static final int THREE_BAKES = 3 * FarmCharmStove.BAKE_TIME + 200;

    private StoveTests() {
    }

    private static Item farm(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(FarmCharmDishes.MOD_ID, path));
    }

    private record Setup(TestStation station, OvenLogic logic, FarmCharmStove stove, ContainerData numbers) {
        Container container() {
            return stove.container();
        }
    }

    /** A stove at {@code at} with the logic the station would make for it. */
    private static Setup stove(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, FarmCharmStove.BLOCK.get());
        BlockPos pos = helper.absolutePos(at);
        TestStation station = new TestStation(helper.getLevel(), pos, OvenStation.STOVE_TYPE);
        BlockEntity entity = helper.getLevel().getBlockEntity(pos);
        FarmCharmStove stove = FarmCharmStove.of(entity);
        helper.assertTrue(stove != null, "Farm & Charm's stove is the stove the station was made for");
        return new Setup(station, new OvenLogic(station), stove, BlockNumbers.of(entity));
    }

    private static Setup stove(GameTestHelper helper) {
        return stove(helper, new BlockPos(1, 2, 1));
    }

    private static boolean withoutFarmAndCharm(GameTestHelper helper) {
        if (HandsOnLetsDo.has(FarmCharmDishes.MOD_ID)) {
            return false;
        }
        helper.succeed();
        return true;
    }

    /** A cook comes to the stove and stays: the station ticks for them every tick from now on. */
    private static void attend(GameTestHelper helper, Setup setup, Player player) {
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        helper.onEachTick(() -> {
            if (!setup.station.users.isEmpty()) {
                setup.logic.tick(setup.station);
            }
        });
    }

    private static CompoundTag door(boolean open) {
        CompoundTag data = new CompoundTag();
        data.putBoolean(OvenStation.OPEN, open);
        return data;
    }

    private static void fill(Setup setup, Player player, ResourceLocation recipe, boolean all) {
        CompoundTag data = new CompoundTag();
        data.putString(OvenStation.DISH, recipe.toString());
        data.putBoolean(OvenStation.ALL, all);
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_FILL, data);
    }

    private static void fuelFromInventory(Setup setup, Player player) {
        CompoundTag data = new CompoundTag();
        data.putBoolean(OvenStation.FROM_INVENTORY, true);
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_FUEL, data);
    }

    private static KitchenOven.Baking baking(GameTestHelper helper, Setup setup, String dish) {
        KitchenOven.Baking found = setup.stove.recipes(helper.getLevel()).stream().filter(recipe -> recipe.result().is(farm(dish))).findFirst()
                .orElse(null);
        helper.assertTrue(found != null, "the stove bakes " + dish);
        return found;
    }

    private static ItemStack[] tray(Setup setup) {
        ItemStack[] tray = new ItemStack[FarmCharmStove.PLACES];
        for (int place = 0; place < tray.length; place++) {
            tray[place] = setup.stove.place(place);
        }
        return tray;
    }

    /** What is on the tray is as good as baked: the stove finishes it on its next ticks. */
    private static void nearlyBaked(Setup setup) {
        setup.numbers.set(FarmCharmStove.COOK, setup.numbers.get(FarmCharmStove.COOK_TOTAL) - 2);
    }

    /**
     * The stove is a station exactly when Farm &amp; Charm is installed, and then its block entity is what the station expects:
     * five slots, four numbers, its recipes read from the game's, and it is told who cooks at it.
     */
    public static void theStoveIsAStationOnlyWithFarmAndCharm(GameTestHelper helper) {
        boolean installed = HandsOnLetsDo.has(FarmCharmDishes.MOD_ID);
        helper.assertTrue(Diegesis.stationType(OvenStation.STOVE_ID).isPresent() == (installed && HandsOnConfig.stove()),
                "the stove's station exists with Farm & Charm and only with it");
        if (!installed) {
            helper.succeed();
            return;
        }
        Setup setup = stove(helper);
        BlockState state = helper.getLevel().getBlockState(setup.station.pos());
        helper.assertTrue(Diegesis.stationFor(state).filter(type -> type == OvenStation.STOVE_TYPE).isPresent(), "the stove opens its station");
        helper.assertTrue(setup.container().getContainerSize() == FarmCharmStove.SLOTS && setup.numbers.getCount() == FarmCharmStove.NUMBERS,
                "five slots and four numbers");
        int recipes = setup.stove.recipes(helper.getLevel()).size();
        helper.assertTrue(recipes >= 9, "Farm & Charm's bakings are read, got " + recipes);
        helper.assertTrue(setup.stove.isFuel(new ItemStack(Items.COAL)) && !setup.stove.isFuel(new ItemStack(Items.COBBLESTONE)),
                "it burns what a furnace burns");
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        setup.logic.onOpen(setup.station, player);
        helper.assertTrue(player.getUUID().equals(BlockOwner.get(setup.stove.blockEntity())), "the stove is told who cooks at it");
        helper.assertTrue(OvenLogic.isOpen(setup.station), "the mouth opens for a cook who comes to a stove where nothing bakes");
        helper.succeed();
    }

    /**
     * What is laid on the tray lies in the stove's own slots, and what comes into the stove is seen at the station. Only what some
     * dish is made of goes on the tray, only fuel to the fire, and nothing at all with the mouth shut. The mouth is shut behind the
     * last cook.
     */
    public static void theStovesSlotsAreItsOwn(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        Setup setup = stove(helper);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        SlotRef first = SlotRef.of(OvenStation.INGREDIENTS, 0);
        ItemStack dough = new ItemStack(farm("dough"), 2);
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, first, dough), "dough is in a bread: it goes on the tray");
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, first, new ItemStack(Items.COBBLESTONE)), "no dish takes cobblestone");
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, OvenStation.FUEL_SLOT, new ItemStack(Items.COAL)), "coal goes to the fire");
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, OvenStation.FUEL_SLOT, dough), "dough does not");
        setup.station.state().setItem(first, dough);
        helper.assertTrue(setup.container().getItem(FarmCharmStove.FIRST_PLACE).is(farm("dough")), "the dough lies in the stove's first ingredient slot");
        setup.container().setItem(FarmCharmStove.OUTPUT_SLOT, new ItemStack(farm("farmers_bread"), 3));
        setup.station.state().pollStorages();
        helper.assertTrue(setup.station.state().getItem(SlotRef.of(OvenStation.SERVED, 0)).is(farm("farmers_bread")), "the stove's dish is the station's");
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_DOOR, door(false));
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, first, dough), "nothing goes in with the mouth shut");
        helper.assertFalse(setup.logic.mayTake(setup.station, player, first), "and nothing comes out");
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_DOOR, door(true));
        setup.logic.onClose(setup.station, player);
        helper.assertFalse(OvenLogic.isOpen(setup.station), "the mouth is shut behind the last cook");
        setup.container().clearContent();
        helper.succeed();
    }

    /**
     * The same bread from the same things, once laid in by hoppers with nobody near and once through the station: as many loaves,
     * after as many ticks of baking, the stove's own.
     */
    public static void breadThroughTheStationIsBreadByHoppers(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        BlockPos alone = new BlockPos(1, 3, 1);
        helper.setBlock(alone, FarmCharmStove.BLOCK.get());
        helper.setBlock(alone.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        helper.setBlock(alone.east(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
        Container above = (Container) helper.getBlockEntity(alone.above());
        above.setItem(0, new ItemStack(farm("dough")));
        above.setItem(1, new ItemStack(Items.WATER_BUCKET));
        ((Container) helper.getBlockEntity(alone.east())).setItem(0, new ItemStack(Items.COAL));
        BlockEntity aloneEntity = helper.getBlockEntity(alone);
        FarmCharmStove byHoppers = FarmCharmStove.of(aloneEntity);
        ContainerData aloneNumbers = BlockNumbers.of(aloneEntity);

        Setup setup = stove(helper, new BlockPos(5, 2, 1));
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        attend(helper, setup, player);
        player.getInventory().add(new ItemStack(farm("dough")));
        player.getInventory().add(new ItemStack(Items.WATER_BUCKET));
        player.getInventory().add(new ItemStack(Items.COAL));
        fill(setup, player, baking(helper, setup, "farmers_bread").id(), false);
        fuelFromInventory(setup, player);
        helper.assertTrue(player.getInventory().countItem(farm("dough")) == 0 && setup.stove.fuel().is(Items.COAL),
                "the book lays the dough and the water on the tray, the coal goes to the fire");
        // The cook shuts the door: the stove bakes only shut.
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_DOOR, door(false));

        long[] began = {-1, -1};
        long[] done = {-1, -1};
        int[] loaves = {0, 0};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            FarmCharmStove[] stoves = {byHoppers, setup.stove};
            ContainerData[] numbers = {aloneNumbers, setup.numbers};
            for (int i = 0; i < 2; i++) {
                if (began[i] < 0 && numbers[i].get(FarmCharmStove.COOK) > 0) {
                    began[i] = now - numbers[i].get(FarmCharmStove.COOK);
                }
                if (done[i] < 0 && !stoves[i].output(0).isEmpty()) {
                    done[i] = now;
                    loaves[i] = stoves[i].output(0).getCount();
                    helper.assertTrue(stoves[i].output(0).is(farm("farmers_bread")), "bread, got " + stoves[i].output(0));
                }
            }
            if (done[0] >= 0 && done[1] >= 0) {
                helper.assertTrue(loaves[0] == loaves[1] && loaves[0] > 0, "as many loaves: " + loaves[0] + " by hoppers, " + loaves[1] + " at the station");
                long ticksAlone = done[0] - began[0];
                long ticksAtStation = done[1] - began[1];
                helper.assertTrue(Math.abs(ticksAlone - ticksAtStation) <= 2 && Math.abs(ticksAlone - FarmCharmStove.BAKE_TIME) <= 2,
                        "as long as the stove takes (" + FarmCharmStove.BAKE_TIME + "): " + ticksAlone + " by hoppers, " + ticksAtStation
                                + " at the station");
                helper.assertTrue(setup.station.state().data().getBoolean(OvenStation.HOT), "the station shows the fire");
                byHoppers.container().clearContent();
                setup.container().clearContent();
                helper.succeed();
            }
        });
    }

    /**
     * The dish taken off the tray, and fuel laid to the fire, leave what bakes as far as it was: the stove alone would start it
     * over at every such change.
     */
    public static void takingTheDishKeepsWhatBakes(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        Setup setup = stove(helper);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        attend(helper, setup, player);
        setup.stove.setPlace(0, new ItemStack(farm("corn"), 2));
        setup.stove.setPlace(1, new ItemStack(farm("corn"), 2));
        setup.stove.setPlace(2, new ItemStack(farm("butter"), 2));
        setup.container().setItem(FarmCharmStove.FUEL_SLOT, new ItemStack(Items.COAL));
        setup.container().setItem(FarmCharmStove.OUTPUT_SLOT, new ItemStack(farm("roasted_corn")));
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_DOOR, door(false));
        helper.runAfterDelay(30, () -> {
            int baked = setup.numbers.get(FarmCharmStove.COOK);
            helper.assertTrue(baked > 10, "it bakes, got " + baked);
            setup.station.state().setItem(SlotRef.of(OvenStation.SERVED, 0), ItemStack.EMPTY);
            helper.assertTrue(setup.stove.output(0).isEmpty(), "the dish is taken");
            helper.assertTrue(setup.numbers.get(FarmCharmStove.COOK) == baked, "what bakes is as far as it was, got "
                    + setup.numbers.get(FarmCharmStove.COOK) + " of " + baked);
            setup.station.state().setItem(OvenStation.FUEL_SLOT, new ItemStack(Items.COAL, 4));
            helper.assertTrue(setup.numbers.get(FarmCharmStove.COOK) == baked, "and so with fuel laid to the fire");
            setup.container().clearContent();
            helper.succeed();
        });
    }

    /**
     * Two alike on the tray bake to the last set with nobody there: three sets of corn, corn and butter, laid from the book, are
     * three roasted corns. The stove alone takes both corns of a set from one place and stops with the other place still full.
     */
    public static void twoAlikeBakeToTheLastSet(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        Setup setup = stove(helper);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        player.getInventory().add(new ItemStack(farm("corn"), 6));
        player.getInventory().add(new ItemStack(farm("butter"), 3));
        fill(setup, player, baking(helper, setup, "roasted_corn").id(), true);
        setup.logic.tick(setup.station);
        int corn = 0;
        for (ItemStack stack : tray(setup)) {
            corn += stack.is(farm("corn")) ? stack.getCount() : 0;
        }
        helper.assertTrue(corn == 6 && player.getInventory().countItem(farm("butter")) == 0, "three sets lie on the tray, got " + corn + " corn");
        setup.container().setItem(FarmCharmStove.FUEL_SLOT, new ItemStack(Items.COAL, 2));
        // The cook goes; the stove bakes on by itself.
        setup.logic.onClose(setup.station, player);
        setup.station.users.clear();
        helper.onEachTick(() -> {
            if (setup.stove.output(0).getCount() == 3) {
                helper.assertTrue(setup.stove.output(0).is(farm("roasted_corn")), "roasted corn, got " + setup.stove.output(0));
                for (ItemStack stack : tray(setup)) {
                    helper.assertTrue(stack.isEmpty(), "nothing is left on the tray, got " + stack);
                }
                setup.container().clearContent();
                helper.succeed();
            }
        });
    }

    /** Puts one set of what {@code recipe} takes into the player's inventory. */
    private static void give(Player player, KitchenOven.Baking recipe) {
        player.getInventory().clearContent();
        for (Ingredient ingredient : recipe.needed()) {
            if (ingredient.getItems().length > 0) {
                player.getInventory().add(ingredient.getItems()[0].copyWithCount(1));
            }
        }
    }

    /**
     * Every dish the stove has, with whatever mods add theirs, picked in the book: its ingredients go on the tray from the
     * inventory as the stove reads them, and with a fire under them the stove bakes that dish. The wait is skipped.
     */
    public static void everyBakingIsLaidFromTheBookAndBakes(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        Setup[] setup = {stove(helper, at)};
        List<KitchenOven.Baking> recipes = setup[0].stove.recipes(helper.getLevel()).stream()
                .filter(recipe -> Cookable.all(recipe.ingredients())).toList();
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        int step = 8;
        helper.onEachTick(() -> {
            int tick = (int) helper.getTick();
            int index = tick / step;
            if (index >= recipes.size()) {
                setup[0].container().clearContent();
                helper.succeed();
                return;
            }
            KitchenOven.Baking recipe = recipes.get(index);
            switch (tick % step) {
                case 0 -> {
                    // A new stove for each dish.
                    setup[0].container().clearContent();
                    helper.setBlock(at, Blocks.AIR);
                    setup[0] = stove(helper, at);
                    setup[0].station.users.add(player);
                    setup[0].logic.onOpen(setup[0].station, player);
                    give(player, recipe);
                    fill(setup[0], player, recipe.id(), false);
                    setup[0].logic.tick(setup[0].station);
                    Optional<KitchenOven.Baking> lying = KitchenOven.matching(recipes, tray(setup[0]));
                    helper.assertTrue(lying.isPresent() && lying.get().id().equals(recipe.id()), recipe.id() + " is what lies on the tray, got "
                            + lying.map(KitchenOven.Baking::id).orElse(null));
                    setup[0].container().setItem(FarmCharmStove.FUEL_SLOT, new ItemStack(Items.COAL));
                }
                case 3 -> {
                    helper.assertTrue(setup[0].numbers.get(FarmCharmStove.COOK) > 0, recipe.id() + " bakes once the book laid it and a fire burns");
                    nearlyBaked(setup[0]);
                }
                case 6 -> helper.assertTrue(ItemStack.isSameItem(setup[0].stove.output(0), recipe.result()), recipe.id() + " is baked, got "
                        + setup[0].stove.output(0));
                default -> {
                }
            }
        });
    }

    /**
     * What the stove sends its clients after the last dish is taken from an emptied stove holds nothing: a client's copy that had
     * shown what baked is empty again.
     */
    public static void aClientsCopyOfAnEmptiedStoveIsEmpty(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        Setup setup = stove(helper);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        attend(helper, setup, player);
        setup.stove.setPlace(0, new ItemStack(farm("corn")));
        setup.stove.setPlace(1, new ItemStack(farm("corn")));
        setup.stove.setPlace(2, new ItemStack(farm("butter")));
        setup.container().setItem(FarmCharmStove.FUEL_SLOT, new ItemStack(Items.COAL));
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_DOOR, door(false));
        BlockEntity real = setup.stove.blockEntity();
        BlockEntity copy = ((EntityBlock) real.getBlockState().getBlock()).newBlockEntity(setup.station.pos(), real.getBlockState());
        helper.runAfterDelay(3, () -> {
            copy.loadWithComponents(real.getUpdateTag(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            helper.assertTrue(copy instanceof Container seen && !seen.isEmpty(), "the client's copy shows what lies on the tray");
            nearlyBaked(setup);
        });
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(setup.stove.output(0).is(farm("roasted_corn")), "the corn is roasted, got " + setup.stove.output(0));
            setup.station.state().setItem(SlotRef.of(OvenStation.SERVED, 0), ItemStack.EMPTY);
            copy.loadWithComponents(real.getUpdateTag(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            helper.assertTrue(copy instanceof Container seen && seen.isEmpty(), "nothing lies in the client's copy");
            helper.succeed();
        });
    }

    /**
     * The stove bakes only with its door shut: open, nothing bakes and the fire waits where it was, so nothing
     * is lost by looking in; shut, both go on.
     */
    public static void theStoveBakesOnlyShut(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        Setup setup = stove(helper);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        attend(helper, setup, player);
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_DOOR, door(true));
        setup.stove.setPlace(0, new ItemStack(farm("corn")));
        setup.stove.setPlace(1, new ItemStack(farm("corn")));
        setup.stove.setPlace(2, new ItemStack(farm("butter")));
        setup.container().setItem(FarmCharmStove.FUEL_SLOT, new ItemStack(Items.COAL));
        int[] fire = {0};
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(setup.numbers.get(FarmCharmStove.COOK) <= 1, "nothing bakes behind an open door, got "
                    + setup.numbers.get(FarmCharmStove.COOK));
            fire[0] = setup.numbers.get(FarmCharmStove.BURN);
            helper.assertTrue(fire[0] > 0, "the fire is lit");
        });
        helper.runAfterDelay(40, () -> {
            // Read between the stove's tick and the station's, it may be the tick the stove has just burnt.
            helper.assertTrue(Math.abs(setup.numbers.get(FarmCharmStove.BURN) - fire[0]) <= 1, "the fire waits too, got " + setup.numbers.get(FarmCharmStove.BURN)
                    + " of " + fire[0]);
            setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_DOOR, door(false));
        });
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(setup.numbers.get(FarmCharmStove.COOK) > 20, "shut, it bakes, got " + setup.numbers.get(FarmCharmStove.COOK));
            helper.assertTrue(setup.numbers.get(FarmCharmStove.BURN) < fire[0], "and the fire burns down");
            setup.container().clearContent();
            helper.succeed();
        });
    }

    /**
     * The firebox is an opening of its own under the door: fuel goes onto the fire and comes off it with the door shut, one with a
     * right click and the stack with a left one, while the tray still takes nothing.
     */
    public static void fuelGoesOnTheFireWithTheDoorShut(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        Setup setup = stove(helper);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_DOOR, door(false));
        helper.assertFalse(OvenLogic.isOpen(setup.station), "the door is shut");
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, OvenStation.FUEL_SLOT, new ItemStack(Items.COAL)),
                "coal goes onto the fire with the door shut");
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, SlotRef.of(OvenStation.INGREDIENTS, 0), new ItemStack(farm("dough"))),
                "the tray takes nothing with the door shut");
        player.inventoryMenu.setCarried(new ItemStack(Items.COAL, 16));
        CompoundTag one = new CompoundTag();
        one.putBoolean(OvenStation.ONE, true);
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_FUEL, one);
        helper.assertTrue(setup.stove.fuel().is(Items.COAL) && setup.stove.fuel().getCount() == 1, "a right click lays one coal, got "
                + setup.stove.fuel());
        helper.assertTrue(player.inventoryMenu.getCarried().getCount() == 15, "the rest stays in the hand");
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_FUEL, new CompoundTag());
        helper.assertTrue(setup.stove.fuel().getCount() == 16 && player.inventoryMenu.getCarried().isEmpty(), "a left click lays the stack");
        helper.assertTrue(setup.logic.mayTake(setup.station, player, OvenStation.FUEL_SLOT), "and the fuel can be taken back with the door shut");
        helper.assertFalse(setup.logic.mayTake(setup.station, player, SlotRef.of(OvenStation.SERVED, 0)), "the dish cannot");
        setup.container().clearContent();
        helper.succeed();
    }

    /** All the station tells a client who comes to a full stove is far below what one packet may hold. */
    public static void theStovesDataStaysSmall(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        Setup setup = stove(helper);
        Container container = setup.container();
        container.setItem(FarmCharmStove.FIRST_PLACE, new ItemStack(farm("dough"), 64));
        container.setItem(FarmCharmStove.FIRST_PLACE + 1, new ItemStack(Items.SUGAR, 64));
        container.setItem(FarmCharmStove.FIRST_PLACE + 2, new ItemStack(Items.CARROT, 64));
        container.setItem(FarmCharmStove.FUEL_SLOT, new ItemStack(Items.COAL, 64));
        container.setItem(FarmCharmStove.OUTPUT_SLOT, new ItemStack(farm("farmers_bread"), 64));
        setup.logic.tick(setup.station);
        setup.station.state().pollStorages();
        setup.station.state().markAllDirty();
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        setup.station.state().writeChanges(buffer, true);
        int bytes = buffer.readableBytes();
        helper.assertTrue(bytes > 0 && bytes < 32 * 1024, "the station's whole data is " + bytes + " bytes");
        container.clearContent();
        helper.succeed();
    }

    /**
     * The stove's scene is one the library reads, with every part the station shows things on: the stove and its mouth's plate,
     * the tray with a place for each ingredient and for the dish, the fire and the fuel, the badge and the book.
     */
    public static void theStovesSceneIsRead(GameTestHelper helper) {
        StationTests.sceneIsRead(helper, "stove", List.of("oven", "body", "inside", "glow", "fuel", "panel", "door", "tray",
                "rack_model", "place_0", "place_1", "place_2", "food_0", "food_1", "food_2", "served", "dish", "rack_zone", "badge", "dial_track",
                "dial", "flame", "flame_lit", "preview", "book_open"), OvenStation.STOVE_SCHEMA,
                List.of(OvenStation.INGREDIENTS, OvenStation.SERVED, OvenStation.FUEL));
        helper.succeed();
    }
}
