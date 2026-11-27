package com.pockyl.handson_letsdo.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_letsdo.HandsOnConfig;
import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.station.CauldronTold;
import com.pockyl.handson_letsdo.station.MeadowCauldron;
import com.pockyl.handson_letsdo.station.MeadowCauldronLogic;
import com.pockyl.handson_letsdo.station.MeadowCauldronStation;

import java.util.ArrayList;
import java.util.List;

/**
 * The cooking cauldron's rules against a real cauldron of Meadow: the station pours water and lays food into the cauldron's own
 * slots and the cauldron cooks them into its dish in its own tick, which the ladle hurries. They pass at once without Meadow.
 */
public final class MeadowCauldronTests {
    private static final BlockPos AT = new BlockPos(1, 2, 1);
    @SuppressWarnings("removal")
    private static final ResourceLocation SOUP = new ResourceLocation("meadow", "cooking_pot/beetroot_soup");

    private MeadowCauldronTests() {
    }

    private record Setup(TestStation station, MeadowCauldronLogic logic, MeadowCauldron cauldron, BlockEntity entity) {
        Container container() {
            return cauldron.container();
        }
    }

    /** A cauldron at {@code at}, hanging in its frame over its own fire (it is hot) or standing on the floor (it is cold). */
    private static Setup cauldron(GameTestHelper helper, BlockPos at, boolean hanging) {
        BlockState state = with(MeadowCauldronStation.CAULDRON.get().defaultBlockState(), "hanging", hanging);
        helper.setBlock(at, state);
        BlockPos pos = helper.absolutePos(at);
        TestStation station = new TestStation(helper.getLevel(), pos,
                hanging ? MeadowCauldronStation.HANGING_TYPE : MeadowCauldronStation.TYPE);
        BlockEntity entity = helper.getLevel().getBlockEntity(pos);
        MeadowCauldron cauldron = MeadowCauldron.of(entity);
        helper.assertTrue(cauldron != null, "Meadow's cauldron is the cauldron the station was made for");
        return new Setup(station, new MeadowCauldronLogic(station), cauldron, entity);
    }

    private static BlockState with(BlockState state, String name, boolean value) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof BooleanProperty flag && property.getName().equals(name)) {
                return state.setValue(flag, value);
            }
        }
        return state;
    }

    private static boolean withoutMeadow(GameTestHelper helper) {
        if (HandsOnLetsDo.has(MeadowCauldron.MOD_ID)) {
            return false;
        }
        helper.succeed();
        return true;
    }

    /** {@code player} comes to the cauldron, and its logic runs every tick from now, as it does while somebody is at a station. */
    private static void attend(GameTestHelper helper, Setup setup, Player player) {
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        helper.onEachTick(() -> setup.logic.tick(setup.station));
    }

    @SuppressWarnings("removal")
    private static Item meadow(String name) {
        return BuiltInRegistries.ITEM.get(new ResourceLocation(MeadowCauldron.MOD_ID, name));
    }

    private static void act(Setup setup, Player player, String action) {
        setup.logic.onCustomAction(setup.station, player, action, new CompoundTag());
    }

    /** The ladle's foot goes round the cauldron by {@code turns} turns, in steps a hand makes within one tick. */
    private static void stir(Setup setup, Player player, float turns) {
        int steps = Mth.ceil(turns * Mth.TWO_PI / 0.6F);
        for (int i = 0; i <= steps; i++) {
            CompoundTag where = new CompoundTag();
            where.putFloat(MeadowCauldronStation.X, 2.0F * Mth.cos(i * 0.6F));
            where.putFloat(MeadowCauldronStation.Z, 2.0F * Mth.sin(i * 0.6F));
            setup.logic.onCustomAction(setup.station, player, MeadowCauldronStation.ACTION_STIR, where);
        }
    }

    /** Food dropped into the cauldron at the station, as the library lays a stack into a slot of the group. */
    private static void drop(Setup setup, int slot, ItemStack food) {
        SlotRef ref = SlotRef.of(MeadowCauldronStation.INGREDIENTS, slot);
        setup.station.state().setItem(ref, food);
        setup.logic.onSlotChanged(setup.station, ref);
    }

    /**
     * The cauldron is a station exactly when Meadow is installed, standing or hanging, and then its block entity is what the station
     * expects: eight slots and four numbers. It tells its clients what it holds, so that it looks the same in the world as at the
     * station: a client's copy made from what it sends holds its food and water and knows it was told, and holds nothing once it is
     * emptied (Meadow's own loading would keep a slot the server emptied).
     */
    public static void theCauldronIsAStationOnlyWithMeadow(GameTestHelper helper) {
        boolean installed = HandsOnLetsDo.has(MeadowCauldron.MOD_ID);
        boolean on = installed && HandsOnConfig.meadowCookingCauldron();
        helper.assertTrue(Diegesis.stationType(MeadowCauldronStation.ID).isPresent() == on
                && Diegesis.stationType(MeadowCauldronStation.HANGING_ID).isPresent() == on,
                "the cauldron's stations exist with Meadow and only with it");
        if (!installed) {
            helper.succeed();
            return;
        }
        Setup setup = cauldron(helper, AT, false);
        BlockState state = helper.getLevel().getBlockState(setup.station.pos());
        helper.assertTrue(Diegesis.stationFor(state).filter(type -> type == MeadowCauldronStation.TYPE).isPresent(),
                "a standing cauldron opens the standing station");
        BlockState hanging = with(state, "hanging", true);
        helper.assertTrue(Diegesis.stationFor(hanging).filter(type -> type == MeadowCauldronStation.HANGING_TYPE).isPresent(),
                "a hanging one the hanging station");
        helper.assertTrue(setup.container().getContainerSize() == MeadowCauldron.SLOTS, "eight slots");
        setup.container().setItem(1, new ItemStack(Items.POTATO));
        setup.cauldron.setWater(40);
        BlockEntity copy = ((EntityBlock) state.getBlock()).newBlockEntity(setup.station.pos(), state);
        helper.assertTrue(copy instanceof CauldronTold, "the cauldron tells its clients what it holds");
        copy.load(setup.entity.getUpdateTag());
        MeadowCauldron seen = MeadowCauldron.of(copy);
        helper.assertTrue(seen != null && seen.container().getItem(1).is(Items.POTATO) && seen.water() == 40
                && ((CauldronTold) copy).handson$told(), "a client's copy shows the food and the water, and that it was told");
        setup.container().clearContent();
        copy.load(setup.entity.getUpdateTag());
        helper.assertTrue(seen.container().isEmpty(), "and nothing once it is emptied");
        CompoundTag saved = setup.entity.saveWithoutMetadata();
        helper.assertFalse(saved.contains("HandsOnTold"), "the word that the server told it is not saved with the block");
        helper.succeed();
    }

    /**
     * The station's slots are the cauldron's own: food laid at the station lies in slots 1 to 6, the dish of slot 0 is the station's,
     * where hoppers and its menu reach them. Only what some dish is made of goes in.
     */
    public static void theCauldronsSlotsAreItsOwn(GameTestHelper helper) {
        if (withoutMeadow(helper)) {
            return;
        }
        Setup setup = cauldron(helper, AT, false);
        Player player = helper.makeMockPlayer();
        setup.station.state().setItem(SlotRef.of(MeadowCauldronStation.INGREDIENTS, 2), new ItemStack(Items.POTATO, 3));
        helper.assertTrue(setup.container().getItem(3).is(Items.POTATO), "food laid at the station lies in the cauldron's own slot");
        setup.container().setItem(MeadowCauldron.OUTPUT_SLOT, new ItemStack(Items.BAKED_POTATO, 4));
        helper.assertTrue(setup.station.state().getItem(MeadowCauldronStation.SERVED_SLOT).is(Items.BAKED_POTATO),
                "the dish in the cauldron is the station's");
        SlotRef slot = SlotRef.of(MeadowCauldronStation.INGREDIENTS, 0);
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, slot, new ItemStack(Items.BEETROOT)), "beetroot goes into the water");
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, slot, new ItemStack(Items.DIAMOND)), "a diamond does not");
        helper.succeed();
    }

    /**
     * Water is poured from a bucket into the cauldron's water slot; its own tick empties it into its water (50 a bucket, 25 a bottle)
     * and the empty bucket comes back to the cook. What would not fit is not poured, nor a potion that is not water.
     */
    public static void waterIsPouredAndTheBucketComesBack(GameTestHelper helper) {
        if (withoutMeadow(helper)) {
            return;
        }
        Setup setup = cauldron(helper, AT, false);
        Player player = helper.makeMockPlayer();
        attend(helper, setup, player);
        player.inventoryMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        act(setup, player, MeadowCauldronStation.ACTION_POUR);
        helper.assertTrue(player.inventoryMenu.getCarried().isEmpty() && setup.cauldron.waterSlot().is(Items.WATER_BUCKET),
                "the bucket goes into the cauldron's water slot");
        helper.assertTrue(setup.station.effects.contains(MeadowCauldronStation.EFFECT_POURED), "everybody sees it poured");
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(setup.cauldron.water() == MeadowCauldron.BUCKET, "the cauldron poured it in, got " + setup.cauldron.water());
            helper.assertTrue(player.inventoryMenu.getCarried().is(Items.BUCKET) && setup.cauldron.waterSlot().isEmpty(),
                    "and the empty bucket came back to the cook's hand");
            player.inventoryMenu.setCarried(PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.HEALING));
            act(setup, player, MeadowCauldronStation.ACTION_POUR);
            helper.assertTrue(player.inventoryMenu.getCarried().is(Items.POTION), "a potion of healing is not water: it stays");
            player.inventoryMenu.setCarried(PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER));
            act(setup, player, MeadowCauldronStation.ACTION_POUR);
        });
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(setup.cauldron.water() == MeadowCauldron.BUCKET + MeadowCauldron.BOTTLE,
                    "a bottle of water pours a quarter, got " + setup.cauldron.water());
            player.inventoryMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
            act(setup, player, MeadowCauldronStation.ACTION_POUR);
            helper.assertTrue(player.inventoryMenu.getCarried().is(Items.WATER_BUCKET), "a bucket that would spill over is not poured");
            helper.succeed();
        });
    }

    /**
     * Food dropped in lies together, and the cauldron reads a dish one ingredient a slot: five beetroots in one stack and a bowl are
     * laid out over six slots, the way Meadow's beetroot soup wants them, and the station names the dish they make.
     */
    public static void foodIsLaidOutForItsDish(GameTestHelper helper) {
        if (withoutMeadow(helper)) {
            return;
        }
        Setup setup = cauldron(helper, AT, false);
        Player player = helper.makeMockPlayer();
        attend(helper, setup, player);
        drop(setup, 0, new ItemStack(Items.BEETROOT, 5));
        drop(setup, 1, new ItemStack(Items.BOWL));
        helper.runAfterDelay(2, () -> {
            int beetroots = 0;
            for (ItemStack stack : setup.cauldron.ingredients()) {
                beetroots += stack.is(Items.BEETROOT) && stack.getCount() == 1 ? 1 : 0;
            }
            helper.assertTrue(beetroots == 5, "one beetroot a slot, got " + setup.cauldron.ingredients());
            helper.assertTrue(SOUP.toString().equals(setup.station.state().data().getString(MeadowCauldronStation.MAKING)),
                    "the station tells the dish they make, got " + setup.station.state().data().getString(MeadowCauldronStation.MAKING));
            helper.succeed();
        });
    }

    /**
     * Over its fire, with water and the food of a dish, the cauldron cooks by itself in Meadow's time; the ladle hurries it: four
     * turns are half the dish, eight bring it to its last tick, and Meadow's own tick makes the dish, taking the food and the water.
     * The next dish waits until this one is taken, as Meadow's cauldron does; then it cooks.
     */
    public static void theLadleHurriesTheDish(GameTestHelper helper) {
        if (withoutMeadow(helper)) {
            return;
        }
        Setup setup = cauldron(helper, AT, true);
        Player player = helper.makeMockPlayer();
        attend(helper, setup, player);
        setup.cauldron.setWater(50);
        drop(setup, 0, new ItemStack(Items.POTATO, 2));
        drop(setup, 1, new ItemStack(meadow("alpine_salt"), 2));
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(setup.cauldron.cooks(), "the potato and the salt cook over its fire");
            int total = setup.cauldron.duration();
            helper.assertTrue(total == 600, "in Meadow's thirty seconds, got " + total);
            int before = setup.cauldron.time();
            stir(setup, player, MeadowCauldronStation.TURNS * 0.5F);
            int half = setup.cauldron.time() - before;
            helper.assertTrue(Math.abs(half - total / 2) <= total / 20, "four turns are half the dish, got " + half + " of " + total);
            helper.assertTrue(setup.station.state().data().getBoolean(MeadowCauldronStation.STIRRING),
                    "the station tells that the cook stirs");
            stir(setup, player, MeadowCauldronStation.TURNS * 0.5F + 0.2F);
            helper.assertTrue(setup.cauldron.time() == total - 1 && setup.cauldron.output().isEmpty(),
                    "eight turns bring it to its last tick, which is the cauldron's own");
        });
        helper.runAfterDelay(8, () -> {
            ItemStack dish = setup.cauldron.output();
            helper.assertTrue(dish.is(Items.BAKED_POTATO) && dish.getCount() == 4, "the cauldron cooked four baked potatoes, got " + dish);
            helper.assertTrue(setup.cauldron.water() == 40, "taking ten of its water, got " + setup.cauldron.water());
            helper.assertTrue(setup.station.effects.contains(MeadowCauldronStation.EFFECT_COOKED), "everybody sees it done");
            helper.assertFalse(setup.cauldron.cooks(), "the next one waits until the dish is taken");
            act(setup, player, MeadowCauldronStation.ACTION_TAKE);
            helper.assertTrue(setup.cauldron.output().isEmpty() && player.getInventory().countItem(Items.BAKED_POTATO) == 4,
                    "the dish goes into the cook's inventory");
        });
        helper.runAfterDelay(11, () -> {
            helper.assertTrue(setup.cauldron.cooks(), "once it is taken, the next one cooks");
            helper.succeed();
        });
    }

    /**
     * With nobody at it, hoppers still work the cauldron Meadow's way: food from above goes into its ingredient slots, and the dish it
     * cooks goes into the hopper below.
     */
    public static void aHopperStillFeedsTheCauldron(GameTestHelper helper) {
        if (withoutMeadow(helper)) {
            return;
        }
        Setup setup = cauldron(helper, AT, true);
        setup.cauldron.setWater(50);
        helper.setBlock(AT.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        helper.setBlock(AT.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        Container above = (Container) helper.getBlockEntity(AT.above());
        above.setItem(0, new ItemStack(Items.POTATO));
        above.setItem(1, new ItemStack(meadow("alpine_salt")));
        Container below = (Container) helper.getBlockEntity(AT.below());
        boolean[] hurried = {false};
        helper.onEachTick(() -> {
            if (!hurried[0] && setup.cauldron.cooks() && setup.cauldron.time() > 0) {
                hurried[0] = true;
                // Its own thirty seconds are its own: the test only skips them.
                setup.cauldron.setTime(setup.cauldron.duration() - 2);
            }
            for (int slot = 0; slot < below.getContainerSize(); slot++) {
                if (below.getItem(slot).is(Items.BAKED_POTATO)) {
                    helper.succeed();
                    return;
                }
            }
        });
    }

    /**
     * Both scenes are ones the library reads, with every part the station shows things on: the cauldron, its lid, its ladle and its
     * water, the food afloat and no other slot, the dish, the inside and its offer, the vessel poured, the badge and the book; the
     * hanging one its frame too.
     */
    public static void theCauldronsScenesAreRead(GameTestHelper helper) {
        List<String> nodes = List.of("cauldron", "lid", "ladle", "water", "item_0", "item_5", "dish", "mouth", "dip", "offer",
                "pour_hand", "badge", "dial_track", "dial", "dial_more", "preview", "book_open");
        List<String> shown = List.of(MeadowCauldronStation.INGREDIENTS);
        StationTests.sceneIsRead(helper, "meadow_cooking_cauldron", nodes, MeadowCauldronStation.SCHEMA, shown);
        List<String> hanging = new ArrayList<>(nodes);
        hanging.add("frame");
        StationTests.sceneIsRead(helper, "meadow_cooking_cauldron_hanging", hanging, MeadowCauldronStation.SCHEMA, shown);
        helper.succeed();
    }
}
