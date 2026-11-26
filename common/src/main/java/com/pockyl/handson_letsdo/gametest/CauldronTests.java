package com.pockyl.handson_letsdo.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_letsdo.HandsOnConfig;
import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.station.Cauldron;
import com.pockyl.handson_letsdo.station.CauldronLogic;
import com.pockyl.handson_letsdo.station.CauldronStation;
import com.pockyl.handson_letsdo.station.CauldronTold;

import java.util.ArrayList;
import java.util.List;

/**
 * The brewing cauldron's rules against a real cauldron of HerbalBrews: the station pours potions into the cauldron's own slots and
 * the cauldron brews them into its flask in its own tick, which the stick hurries. They pass at once without HerbalBrews.
 */
public final class CauldronTests {
    private static final BlockPos AT = new BlockPos(1, 2, 1);

    private CauldronTests() {
    }

    private record Setup(TestStation station, CauldronLogic logic, Cauldron cauldron, BlockEntity entity) {
        Container container() {
            return cauldron.container();
        }
    }

    private static Setup cauldron(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, CauldronStation.CAULDRON.get());
        BlockPos pos = helper.absolutePos(at);
        TestStation station = new TestStation(helper.getLevel(), pos, CauldronStation.TYPE);
        BlockEntity entity = helper.getLevel().getBlockEntity(pos);
        Cauldron cauldron = Cauldron.of(entity);
        helper.assertTrue(cauldron != null, "HerbalBrews' cauldron is the cauldron the station was made for");
        return new Setup(station, new CauldronLogic(station), cauldron, entity);
    }

    private static boolean withoutHerbalBrews(GameTestHelper helper) {
        if (HandsOnLetsDo.has(Cauldron.MOD_ID)) {
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

    private static ItemStack potion(Potion potion) {
        return PotionUtils.setPotion(new ItemStack(Items.POTION), potion);
    }

    private static Item infusion() {
        return BuiltInRegistries.ITEM.get(Cauldron.INFUSION);
    }

    private static void act(Setup setup, Player player, String action) {
        setup.logic.onCustomAction(setup.station, player, action, new CompoundTag());
    }

    /** The stick's foot goes round the cauldron by {@code turns} turns, in steps a hand makes within one tick. */
    private static void stir(Setup setup, Player player, float turns) {
        int steps = Mth.ceil(turns * Mth.TWO_PI / 0.6F);
        for (int i = 0; i <= steps; i++) {
            CompoundTag where = new CompoundTag();
            where.putFloat(CauldronStation.X, 2.5F * Mth.cos(i * 0.6F));
            where.putFloat(CauldronStation.Z, 2.5F * Mth.sin(i * 0.6F));
            setup.logic.onCustomAction(setup.station, player, CauldronStation.ACTION_STIR, where);
        }
    }

    /**
     * The cauldron is a station exactly when HerbalBrews is installed, and then its block entity is what the station expects: five
     * slots and two numbers. It tells its clients what it holds, so that it looks the same in the world as at the station: a
     * client's copy made from what it sends holds its potions and knows it was told, and holds nothing once it is emptied.
     */
    public static void theCauldronIsAStationOnlyWithHerbalBrews(GameTestHelper helper) {
        boolean installed = HandsOnLetsDo.has(Cauldron.MOD_ID);
        helper.assertTrue(Diegesis.stationType(CauldronStation.ID).isPresent() == (installed && HandsOnConfig.brewingCauldron()),
                "the cauldron's station exists with HerbalBrews and only with it");
        if (!installed) {
            helper.succeed();
            return;
        }
        Setup setup = cauldron(helper, AT);
        BlockState state = helper.getLevel().getBlockState(setup.station.pos());
        helper.assertTrue(Diegesis.stationFor(state).filter(type -> type == CauldronStation.TYPE).isPresent(), "the cauldron opens its station");
        helper.assertTrue(setup.container().getContainerSize() == Cauldron.SLOTS, "five slots");
        setup.container().setItem(0, potion(Potions.HEALING));
        setup.container().setItem(Cauldron.CATALYST_SLOT, new ItemStack(infusion()));
        BlockEntity copy = ((EntityBlock) state.getBlock()).newBlockEntity(setup.station.pos(), state);
        helper.assertTrue(copy instanceof CauldronTold, "the cauldron tells its clients what it holds");
        copy.load(setup.entity.getUpdateTag());
        Cauldron seen = Cauldron.of(copy);
        helper.assertTrue(seen != null && PotionUtils.getPotion(seen.container().getItem(0)) == Potions.HEALING
                && Cauldron.isCatalyst(seen.catalyst()) && ((CauldronTold) copy).handson$told(),
                "a client's copy shows the potion and the infusion, and that it was told");
        setup.container().clearContent();
        copy.load(setup.entity.getUpdateTag());
        helper.assertTrue(seen.container().isEmpty(), "and nothing once it is emptied");
        CompoundTag saved = setup.entity.saveWithoutMetadata();
        helper.assertFalse(saved.contains("HandsOnTold"), "the word that the server told it is not saved with the block");
        helper.succeed();
    }

    /**
     * The station's slots are the cauldron's own: what is laid at the station lies in the cauldron, where hoppers and its menu reach
     * it, and what lies in the cauldron is what the station shows.
     */
    public static void theCauldronsSlotsAreItsOwn(GameTestHelper helper) {
        if (withoutHerbalBrews(helper)) {
            return;
        }
        Setup setup = cauldron(helper, AT);
        setup.station.state().setItem(SlotRef.of(CauldronStation.POTIONS, 1), potion(Potions.SWIFTNESS));
        helper.assertTrue(PotionUtils.getPotion(setup.container().getItem(1)) == Potions.SWIFTNESS, "a potion lies in the cauldron's own slot");
        setup.station.state().setItem(CauldronStation.CATALYST_SLOT, new ItemStack(infusion(), 3));
        helper.assertTrue(setup.container().getItem(Cauldron.CATALYST_SLOT).getCount() == 3, "the infusion lies in its infusion slot");
        setup.container().setItem(Cauldron.FLASK_SLOT, new ItemStack(BuiltInRegistries.ITEM.get(Cauldron.FLASK)));
        helper.assertTrue(Cauldron.isFlask(setup.station.state().getItem(CauldronStation.FLASK_SLOT)), "the flask in it is the flask at the station");
        setup.container().clearContent();
        helper.succeed();
    }

    /**
     * A potion is poured from the cursor into the first empty potion slot, everybody sees it poured, and the cauldron keeps its bottle
     * as its menu does. A glass bottle draws the last potion back out. While a flask waits, no potion goes in. The infusion is thrown
     * in from the cursor.
     */
    public static void potionsArePouredAndDrawnBack(GameTestHelper helper) {
        if (withoutHerbalBrews(helper)) {
            return;
        }
        Setup setup = cauldron(helper, AT);
        Player player = helper.makeMockPlayer();
        attend(helper, setup, player);
        player.inventoryMenu.setCarried(potion(Potions.HEALING));
        act(setup, player, CauldronStation.ACTION_POUR);
        helper.assertTrue(player.inventoryMenu.getCarried().isEmpty() && PotionUtils.getPotion(setup.container().getItem(0)) == Potions.HEALING,
                "the potion is in the cauldron's first slot, nothing left in the hand");
        helper.assertTrue(setup.station.effects.contains(CauldronStation.EFFECT_POURED), "and everybody sees it poured");
        player.inventoryMenu.setCarried(potion(Potions.SWIFTNESS));
        act(setup, player, CauldronStation.ACTION_POUR);
        helper.assertTrue(PotionUtils.getPotion(setup.container().getItem(1)) == Potions.SWIFTNESS, "the next goes into the next slot");
        player.inventoryMenu.setCarried(new ItemStack(Items.GLASS_BOTTLE, 2));
        act(setup, player, CauldronStation.ACTION_DRAW);
        helper.assertTrue(setup.container().getItem(1).isEmpty() && player.getInventory().countItem(Items.POTION) == 1
                && player.inventoryMenu.getCarried().getCount() == 1, "a bottle draws the last potion back out");
        helper.assertTrue(setup.station.effects.contains(CauldronStation.EFFECT_DRAWN), "and everybody sees it drawn");
        setup.container().setItem(Cauldron.FLASK_SLOT, new ItemStack(BuiltInRegistries.ITEM.get(Cauldron.FLASK)));
        player.inventoryMenu.setCarried(potion(Potions.STRENGTH));
        act(setup, player, CauldronStation.ACTION_POUR);
        helper.assertTrue(player.inventoryMenu.getCarried().is(Items.POTION) && setup.container().getItem(1).isEmpty(),
                "no potion goes in while a flask waits");
        player.inventoryMenu.setCarried(new ItemStack(infusion(), 3));
        act(setup, player, CauldronStation.ACTION_THROW);
        helper.assertTrue(setup.cauldron.catalyst().getCount() == 1 && player.inventoryMenu.getCarried().getCount() == 2,
                "one infusion is thrown in from the cursor");
        setup.container().clearContent();
        helper.succeed();
    }

    /**
     * Three potions poured and the infusion laid in brew by the cauldron's own tick: by itself it counts on (never slower than its
     * menu); eight turns of the stick bring the brew the rest of the way, and the cauldron brews a flask with the potions' effects,
     * taking the potions and one infusion. The flask is filled at the cauldron for the cook.
     */
    public static void theStickHurriesTheBrew(GameTestHelper helper) {
        if (withoutHerbalBrews(helper)) {
            return;
        }
        Setup setup = cauldron(helper, AT);
        Player player = helper.makeMockPlayer();
        attend(helper, setup, player);
        for (Potion kind : List.of(Potions.HEALING, Potions.SWIFTNESS, Potions.WATER)) {
            player.inventoryMenu.setCarried(potion(kind));
            act(setup, player, CauldronStation.ACTION_POUR);
        }
        player.getInventory().setItem(3, new ItemStack(infusion(), 2));
        CompoundTag from = new CompoundTag();
        from.putInt(CauldronStation.FROM, 3);
        setup.logic.onCustomAction(setup.station, player, CauldronStation.ACTION_THROW, from);
        helper.assertTrue(setup.cauldron.catalyst().getCount() == 1 && player.getInventory().getItem(3).getCount() == 1,
                "one infusion is thrown in");
        helper.assertTrue(setup.cauldron.brews(), "three potions and the infusion brew");
        int total = setup.cauldron.total();
        helper.assertTrue(total > 0, "the brew takes HerbalBrews' time, got " + total);
        helper.runAfterDelay(5, () -> {
            int before = setup.cauldron.time();
            helper.assertTrue(before >= 3, "left alone it counts on by itself, got " + before);
            stir(setup, player, CauldronStation.TURNS * 0.5F);
            int half = setup.cauldron.time() - before;
            helper.assertTrue(Math.abs(half - total / 2) <= total / 20, "four turns are half the brew, got " + half + " of " + total);
            helper.assertTrue(setup.station.state().data().getBoolean(CauldronStation.STIRRING), "the station tells that the cook stirs");
            stir(setup, player, CauldronStation.TURNS * 0.5F + 0.2F);
            helper.assertTrue(setup.cauldron.time() == total - 1 && setup.cauldron.flask().isEmpty(),
                    "eight turns bring it to its last tick, which is the cauldron's own");
        });
        helper.runAfterDelay(8, () -> {
            ItemStack flask = setup.cauldron.flask();
            helper.assertTrue(Cauldron.isFlask(flask), "the cauldron brewed its flask, got " + flask);
            List<MobEffect> effects = new ArrayList<>();
            PotionUtils.getMobEffects(flask).forEach(effect -> effects.add(effect.getEffect()));
            helper.assertTrue(effects.contains(MobEffects.HEAL) && effects.contains(MobEffects.MOVEMENT_SPEED),
                    "with the potions' effects, got " + effects);
            helper.assertTrue(setup.cauldron.potions().isEmpty() && setup.cauldron.catalyst().isEmpty(), "taking the potions and the infusion");
            helper.assertTrue(setup.station.effects.contains(CauldronStation.EFFECT_BREWED), "everybody sees it done");
            Item made = flask.getItem();
            act(setup, player, CauldronStation.ACTION_TAKE);
            helper.assertTrue(setup.cauldron.flask().isEmpty() && player.getInventory().countItem(made) == 1,
                    "the flask is filled for the cook");
            helper.assertTrue(setup.station.effects.contains(CauldronStation.EFFECT_TAKEN), "and everybody sees it filled");
            helper.succeed();
        });
    }

    /**
     * Three potions without any effect brew as HerbalBrews brews them: on 1.20.1 into nothing (it counts and never brews). The station
     * says so, and the stick does not pretend to hurry it.
     */
    public static void potionsWithoutEffectsBrewAsHerbalBrewsDoes(GameTestHelper helper) {
        if (withoutHerbalBrews(helper)) {
            return;
        }
        Setup setup = cauldron(helper, AT);
        Player player = helper.makeMockPlayer();
        attend(helper, setup, player);
        for (int slot = 0; slot < Cauldron.POTIONS; slot++) {
            setup.container().setItem(slot, potion(Potions.WATER));
        }
        setup.container().setItem(Cauldron.CATALYST_SLOT, new ItemStack(infusion()));
        helper.runAfterDelay(3, () -> {
            helper.assertFalse(setup.station.state().data().getBoolean(CauldronStation.MAKES), "the station tells that they make nothing");
            helper.assertFalse(setup.station.state().data().getBoolean(CauldronStation.BREWING), "and that nothing brews");
            int before = setup.cauldron.time();
            stir(setup, player, CauldronStation.TURNS);
            helper.assertTrue(setup.cauldron.time() == before, "stirring hurries nothing");
            setup.container().clearContent();
            helper.succeed();
        });
    }

    /**
     * Hoppers still feed the cauldron by its faces with nobody at it: potions from above, a potion and the infusion from the side; it
     * brews in its own tick, and a hopper under it takes the flask.
     */
    public static void aHopperStillFeedsTheCauldron(GameTestHelper helper) {
        if (withoutHerbalBrews(helper)) {
            return;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        Setup setup = cauldron(helper, at);
        helper.setBlock(at.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        helper.setBlock(at.east(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
        helper.setBlock(at.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        Container above = (Container) helper.getBlockEntity(at.above());
        above.setItem(0, potion(Potions.HEALING));
        above.setItem(1, potion(Potions.FIRE_RESISTANCE));
        Container side = (Container) helper.getBlockEntity(at.east());
        side.setItem(0, potion(Potions.SWIFTNESS));
        side.setItem(1, new ItemStack(infusion()));
        Container below = (Container) helper.getBlockEntity(at.below());
        boolean[] hurried = {false};
        helper.onEachTick(() -> {
            if (!hurried[0] && setup.cauldron.brews() && setup.cauldron.time() > 0) {
                hurried[0] = true;
                // Its own sixty seconds are its own: the test only skips them.
                setup.cauldron.setTime(setup.cauldron.total() - 2);
            }
            for (int slot = 0; slot < below.getContainerSize(); slot++) {
                if (Cauldron.isFlask(below.getItem(slot))) {
                    helper.assertTrue(setup.cauldron.potions().isEmpty() && setup.cauldron.catalyst().isEmpty(),
                            "the cauldron brewed what the hoppers brought");
                    helper.succeed();
                    return;
                }
            }
        });
    }

    /**
     * The cauldron's scene is one the library reads, with every part the station shows things on: the cauldron, its brew and its
     * stick, the infusion afloat and no other slot, the opening and its offer, the bottle poured, the vessel dipped, the infusion
     * thrown, the badge and the book.
     */
    public static void theCauldronsSceneIsRead(GameTestHelper helper) {
        List<String> nodes = List.of("cauldron", "brew", "stick", "sachet", "mouth", "dip", "offer", "pour_hand", "dip_hand", "throw",
                "badge", "dial_track", "dial", "dial_more", "preview", "book_open");
        StationTests.sceneIsRead(helper, "brewing_cauldron", nodes, CauldronStation.SCHEMA, List.of(CauldronStation.CATALYST));
        helper.succeed();
    }
}
