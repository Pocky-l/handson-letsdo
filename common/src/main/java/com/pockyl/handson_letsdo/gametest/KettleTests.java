package com.pockyl.handson_letsdo.gametest;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.GameType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_letsdo.Cookable;
import com.pockyl.handson_letsdo.HandsOnConfig;
import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.station.HerbalTeas;
import com.pockyl.handson_letsdo.station.Kettle;
import com.pockyl.handson_letsdo.station.KettleLogic;
import com.pockyl.handson_letsdo.station.KettleStation;
import com.pockyl.handson_letsdo.station.Tea;

import java.util.ArrayList;
import java.util.List;

/**
 * The tea kettle's rules against a real kettle of HerbalBrews: the station lays things into the kettle's own slots and the
 * kettle brews. They pass at once without HerbalBrews.
 */
public final class KettleTests {
    private static final BlockPos AT = new BlockPos(1, 2, 1);

    private KettleTests() {
    }

    private static Item herbal(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(HerbalTeas.MOD_ID, path));
    }

    private record Setup(TestStation station, KettleLogic logic, Kettle kettle, BlockEntity entity) {
        Container container() {
            return kettle.container();
        }
    }

    /** An iron kettle at {@code at}, on HerbalBrews' stove when {@code heated}, with the logic the station would make for it. */
    private static Setup kettle(GameTestHelper helper, BlockPos at, boolean heated) {
        helper.setBlock(at.below(), heated ? BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(HerbalTeas.MOD_ID, "stove")) : Blocks.STONE);
        helper.setBlock(at, KettleStation.IRON.get());
        BlockPos pos = helper.absolutePos(at);
        TestStation station = new TestStation(helper.getLevel(), pos, KettleStation.TYPE);
        BlockEntity entity = helper.getLevel().getBlockEntity(pos);
        Kettle kettle = Kettle.of(entity);
        helper.assertTrue(kettle != null, "HerbalBrews' kettle is the kettle the station was made for");
        return new Setup(station, new KettleLogic(station), kettle, entity);
    }

    private static boolean withoutHerbalBrews(GameTestHelper helper) {
        if (HandsOnLetsDo.has(HerbalTeas.MOD_ID)) {
            return false;
        }
        helper.succeed();
        return true;
    }

    /** {@code player} comes to the kettle, and its logic runs every tick from now, as it does while somebody is at a station. */
    private static void attend(GameTestHelper helper, Setup setup, Player player) {
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        helper.onEachTick(() -> setup.logic.tick(setup.station));
    }

    private static void put(Setup setup, SlotRef slot, ItemStack stack) {
        setup.station.state().setItem(slot, stack);
        setup.logic.onSlotChanged(setup.station, slot);
    }

    private static void pour(Setup setup, Player player) {
        setup.logic.onCustomAction(setup.station, player, KettleStation.ACTION_POUR, new CompoundTag());
    }

    private static Tea teaOf(GameTestHelper helper, Item drink) {
        Tea tea = HerbalTeas.all(helper.getLevel()).stream().filter(it -> it.result().is(drink)).findFirst().orElse(null);
        helper.assertTrue(tea != null, "HerbalBrews brews " + drink);
        return tea;
    }

    /**
     * The kettle is a station exactly when HerbalBrews is installed, on both of its kettles, and then its block entity is what
     * the station expects: eight slots and five numbers, its drinks read, a stove under it heating it. It tells its clients what it
     * keeps, so that it steams and whistles for them with nobody at it: a client's copy made from what it sends holds what lies in
     * it, its water and its heat, and holds nothing once the kettle is emptied.
     */
    public static void theKettleIsAStationOnlyWithHerbalBrews(GameTestHelper helper) {
        boolean installed = HandsOnLetsDo.has(HerbalTeas.MOD_ID);
        helper.assertTrue(Diegesis.stationType(KettleStation.ID).isPresent() == (installed && HandsOnConfig.teaKettle()),
                "the kettle's station exists with HerbalBrews and only with it");
        if (!installed) {
            helper.succeed();
            return;
        }
        Setup setup = kettle(helper, AT, true);
        BlockState iron = helper.getLevel().getBlockState(setup.station.pos());
        helper.assertTrue(Diegesis.stationFor(iron).filter(type -> type == KettleStation.TYPE).isPresent(), "the iron kettle opens its station");
        helper.assertTrue(Diegesis.stationFor(KettleStation.COPPER.get().defaultBlockState()).filter(type -> type == KettleStation.TYPE).isPresent(),
                "the copper kettle opens it too");
        helper.assertTrue(setup.container().getContainerSize() == Kettle.SLOTS, "eight slots");
        int teas = HerbalTeas.all(helper.getLevel()).size();
        helper.assertTrue(teas >= 9, "HerbalBrews' drinks are read, got " + teas);
        setup.container().setItem(Kettle.FIRST_INGREDIENT, new ItemStack(Items.SUGAR));
        setup.kettle.setWater(50);
        BlockEntity copy = ((EntityBlock) iron.getBlock()).newBlockEntity(setup.station.pos(), iron);
        helper.assertTrue(copy != null, "the kettle has a block entity");
        copy.loadWithComponents(setup.entity.getUpdateTag(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        Kettle seen = Kettle.of(copy);
        helper.assertTrue(seen != null && seen.container().getItem(Kettle.FIRST_INGREDIENT).is(Items.SUGAR) && seen.water() == 50,
                "a client's copy shows what lies in the kettle and its water");
        setup.container().clearContent();
        setup.kettle.setWater(0);
        copy.loadWithComponents(setup.entity.getUpdateTag(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(seen.container().isEmpty() && seen.water() == 0, "and nothing once it is emptied");
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(setup.kettle.isHeated() && setup.kettle.heat() == HerbalTeas.HEAT_OF_A_FIRE,
                    "a stove under the kettle heats it to " + HerbalTeas.HEAT_OF_A_FIRE + ", got " + setup.kettle.heat());
            helper.succeed();
        });
    }

    /**
     * The station's slots are the kettle's own: what is laid at the station lies in the kettle, where hoppers and its menu reach
     * it, and what lies in the kettle is what the station shows.
     */
    public static void theKettlesSlotsAreItsOwn(GameTestHelper helper) {
        if (withoutHerbalBrews(helper)) {
            return;
        }
        Setup setup = kettle(helper, AT, false);
        put(setup, SlotRef.of(KettleStation.INGREDIENTS, 2), new ItemStack(Items.SUGAR, 3));
        helper.assertTrue(setup.container().getItem(Kettle.FIRST_INGREDIENT + 2).is(Items.SUGAR), "an ingredient lies in the kettle's own slot");
        put(setup, KettleStation.BOTTLE_SLOT, new ItemStack(Items.GLASS_BOTTLE, 4));
        helper.assertTrue(setup.container().getItem(Kettle.BOTTLE_SLOT).getCount() == 4, "the bottles stand in its bottle slot");
        put(setup, KettleStation.EMBER_SLOT, new ItemStack(Items.BLAZE_POWDER, 2));
        helper.assertTrue(setup.container().getItem(Kettle.HEAT_SLOT).is(Items.BLAZE_POWDER), "what feeds the heat lies in its heat slot");
        Item tea = herbal("green_tea");
        setup.container().setItem(Kettle.DRINK_SLOT, new ItemStack(tea, 2));
        helper.assertTrue(setup.station.state().getItem(KettleStation.SERVED_SLOT).is(tea), "the drink in the kettle is the drink at the station");
        setup.container().setItem(Kettle.WATER_SLOT, new ItemStack(Items.BUCKET));
        helper.assertTrue(setup.station.state().getItem(KettleStation.WAITING_SLOT).is(Items.BUCKET), "and what lies in its water slot is seen");
        setup.container().clearContent();
        helper.succeed();
    }

    /**
     * Water is poured by the kettle itself: the station lays the bucket into the kettle's water slot, the kettle fills by half and
     * the empty bucket comes back to the hand it was poured from. A bottle of water is a quarter, and the kettle leaves nothing of
     * it. A full kettle takes no more: the bucket stays in the hand.
     */
    public static void waterIsPouredByTheKettleAndTheBucketComesBack(GameTestHelper helper) {
        if (withoutHerbalBrews(helper)) {
            return;
        }
        Setup setup = kettle(helper, AT, false);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        attend(helper, setup, player);
        player.inventoryMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        pour(setup, player);
        helper.assertTrue(player.inventoryMenu.getCarried().isEmpty() && setup.container().getItem(Kettle.WATER_SLOT).is(Items.WATER_BUCKET),
                "the bucket lies in the kettle's water slot");
        helper.assertTrue(setup.station.effects.contains(KettleStation.EFFECT_POURED), "and everybody sees it poured");
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(setup.kettle.water() == HerbalTeas.LARGE_AMOUNT, "a bucket fills it by half, got " + setup.kettle.water());
            helper.assertTrue(player.inventoryMenu.getCarried().is(Items.BUCKET) && setup.container().getItem(Kettle.WATER_SLOT).isEmpty(),
                    "the empty bucket is back in the hand, got " + player.inventoryMenu.getCarried());
            player.inventoryMenu.setCarried(PotionContents.createItemStack(Items.POTION, Potions.WATER));
            pour(setup, player);
        });
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(setup.kettle.water() == HerbalTeas.LARGE_AMOUNT + HerbalTeas.SMALL_AMOUNT, "a bottle is a quarter, got " + setup.kettle.water());
            helper.assertTrue(player.inventoryMenu.getCarried().isEmpty() && setup.container().getItem(Kettle.WATER_SLOT).isEmpty(),
                    "the kettle leaves nothing of a bottle");
            player.inventoryMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
            pour(setup, player);
        });
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(setup.kettle.water() == Kettle.FULL, "the kettle is full, got " + setup.kettle.water());
            player.inventoryMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
            pour(setup, player);
            helper.assertTrue(player.inventoryMenu.getCarried().is(Items.WATER_BUCKET) && setup.container().getItem(Kettle.WATER_SLOT).isEmpty(),
                    "a full kettle takes no more water");
            helper.succeed();
        });
    }

    /**
     * An empty bucket a hopper or the kettle's menu left in its water slot would stop the kettle taking water for good: it goes
     * to whoever pours next, and their water is poured.
     */
    public static void whatWasLeftInTheWaterSlotGoesToWhoeverPoursNext(GameTestHelper helper) {
        if (withoutHerbalBrews(helper)) {
            return;
        }
        Setup setup = kettle(helper, AT, false);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        attend(helper, setup, player);
        setup.container().setItem(Kettle.WATER_SLOT, new ItemStack(Items.BUCKET));
        player.inventoryMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        pour(setup, player);
        helper.assertTrue(player.getInventory().countItem(Items.BUCKET) == 1, "the bucket that lay there goes to the cook");
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(setup.kettle.water() == HerbalTeas.LARGE_AMOUNT, "and the cook's water is poured, got " + setup.kettle.water());
            helper.succeed();
        });
    }

    /**
     * Leaves and bottles laid in (as a hopper lays them) brew by the kettle's own rules: green tea wants the kettle hotter than a stove alone
     * makes it, so it waits, and the station tells what it lacks; blaze powder laid at its foot is burned by the kettle, and then a
     * bottle brews in the kettle's time: one tea with its effect, one bottle, one leaf and its water gone.
     */
    public static void aTeaBrewsOnlyWithWaterAndHeatEnough(GameTestHelper helper) {
        if (withoutHerbalBrews(helper)) {
            return;
        }
        Setup setup = kettle(helper, AT, true);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        attend(helper, setup, player);
        Item drink = herbal("green_tea");
        Tea tea = teaOf(helper, drink);
        helper.assertTrue(tea.heat() > HerbalTeas.HEAT_OF_A_FIRE && tea.water() > 0 && tea.bottles().size() == 1,
                "green tea wants more heat than a fire gives, water and a bottle");
        setup.kettle.setWater(HerbalTeas.LARGE_AMOUNT);
        put(setup, SlotRef.of(KettleStation.INGREDIENTS, 0), new ItemStack(herbal("dried_green_tea"), 2));
        put(setup, KettleStation.BOTTLE_SLOT, new ItemStack(Items.GLASS_BOTTLE, 2));
        helper.runAfterDelay(10, () -> {
            CompoundTag data = setup.station.state().data();
            helper.assertTrue(setup.kettle.time() == 0 && setup.container().getItem(Kettle.DRINK_SLOT).isEmpty(), "too cool, the kettle waits");
            helper.assertTrue(tea.id().toString().equals(data.getString(KettleStation.MAKING)) && data.getInt(KettleStation.NEEDS_HEAT) == tea.heat()
                    && data.getInt(KettleStation.HEAT) < tea.heat(), "and the station tells which drink waits and for how much heat");
            put(setup, KettleStation.EMBER_SLOT, new ItemStack(Items.BLAZE_POWDER));
        });
        helper.runAfterDelay(16, () -> {
            helper.assertTrue(setup.kettle.heat() >= tea.heat() && setup.container().getItem(Kettle.HEAT_SLOT).isEmpty(),
                    "the kettle burned the blaze powder, heat " + setup.kettle.heat());
            helper.assertTrue(setup.station.effects.contains(KettleStation.EFFECT_FLARED), "and everybody saw it flare");
            helper.assertTrue(setup.kettle.time() > 0 && setup.kettle.needed() == tea.time(), "now a bottle brews in the kettle's own time");
            setup.kettle.setTime(setup.kettle.needed() - 2);
        });
        helper.runAfterDelay(22, () -> {
            ItemStack made = setup.container().getItem(Kettle.DRINK_SLOT);
            helper.assertTrue(made.is(drink) && made.getCount() == 1 && !made.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).customEffects().isEmpty(),
                    "one green tea with its effect, got " + made);
            helper.assertTrue(setup.kettle.water() == HerbalTeas.LARGE_AMOUNT - tea.water(), "its water is gone, left " + setup.kettle.water());
            helper.assertTrue(setup.container().getItem(Kettle.BOTTLE_SLOT).getCount() == 1 && setup.container().getItem(Kettle.FIRST_INGREDIENT).getCount() == 1,
                    "and one bottle and one leaf");
            helper.assertTrue(setup.station.effects.contains(KettleStation.EFFECT_BREWED), "everybody saw the bottle filled");
            setup.container().clearContent();
            helper.succeed();
        });
    }

    /**
     * The kettle brews into itself, as Hands-On Delight's kettle does: nothing goes in with the lid on; with it off,
     * leaves brew with no bottle in the kettle, the drink waits in it (the bottle place stays empty) and the station tells it;
     * a glass bottle clicked on the spout is filled with it, the drink back on the cursor, and everybody sees it poured.
     */
    public static void aKettleBrewsIntoItselfAndIsPouredAtTheSpout(GameTestHelper helper) {
        if (withoutHerbalBrews(helper)) {
            return;
        }
        Setup setup = kettle(helper, AT, true);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        attend(helper, setup, player);
        Item drink = herbal("green_tea");
        Tea tea = teaOf(helper, drink);
        ItemStack leaves = new ItemStack(herbal("dried_green_tea"), 2);
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, SlotRef.of(KettleStation.INGREDIENTS, 0), leaves),
                "nothing goes in with the lid on");
        CompoundTag lid = new CompoundTag();
        lid.putBoolean(KettleStation.OPEN, true);
        setup.logic.onCustomAction(setup.station, player, KettleStation.ACTION_LID, lid);
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, SlotRef.of(KettleStation.INGREDIENTS, 0), leaves),
                "with the lid off the leaves go in");
        setup.kettle.setWater(HerbalTeas.LARGE_AMOUNT);
        setup.kettle.setHeat(Kettle.FULL);
        put(setup, SlotRef.of(KettleStation.INGREDIENTS, 0), leaves);
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(setup.kettle.time() > 0, "it brews with no bottle in it");
            setup.kettle.setTime(setup.kettle.needed() - 2);
        });
        helper.runAfterDelay(12, () -> {
            ItemStack waiting = setup.kettle.waiting();
            helper.assertTrue(waiting.is(drink) && waiting.getCount() == 1 && !waiting.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).customEffects().isEmpty(),
                    "one green tea waits in the kettle, with its effect, got " + waiting);
            helper.assertTrue(setup.container().getItem(Kettle.DRINK_SLOT).isEmpty() && setup.container().getItem(Kettle.BOTTLE_SLOT).isEmpty(),
                    "nothing in its drink and bottle slots");
            helper.assertTrue(setup.kettle.water() == HerbalTeas.LARGE_AMOUNT - tea.water(), "its water is gone, left " + setup.kettle.water());
            CompoundTag data = setup.station.state().data();
            helper.assertTrue(data.getInt(KettleStation.DRINKS) == 1 && data.getString(KettleStation.DRINK).endsWith("green_tea"),
                    "the station tells what waits");
            helper.assertTrue(setup.station.effects.contains(KettleStation.EFFECT_BREWED), "everybody saw it brewed");
            setup.kettle.setHeat(0);
            player.inventoryMenu.setCarried(new ItemStack(Items.GLASS_BOTTLE, 3));
            setup.logic.onCustomAction(setup.station, player, KettleStation.ACTION_DRAW, new CompoundTag());
            ItemStack carried = player.inventoryMenu.getCarried();
            helper.assertTrue(carried.is(Items.GLASS_BOTTLE) && carried.getCount() == 2, "one bottle of the three is filled, left " + carried);
            helper.assertTrue(player.getInventory().countItem(drink) == 1, "and the tea is in the inventory");
            helper.assertTrue(setup.kettle.waiting().isEmpty(), "nothing waits any more");
            helper.assertTrue(setup.station.effects.contains(KettleStation.EFFECT_DRAWN), "everybody saw it poured");
            player.inventoryMenu.setCarried(ItemStack.EMPTY);
            setup.container().clearContent();
            helper.succeed();
        });
    }

    private static void give(Player player, Tea tea) {
        player.getInventory().clearContent();
        List<Ingredient> wanted = new ArrayList<>(tea.ingredients());
        for (Ingredient ingredient : wanted) {
            if (ingredient.getItems().length > 0) {
                player.getInventory().add(ingredient.getItems()[0].copyWithCount(1));
            }
        }
        if (tea.water() > 0) {
            player.getInventory().add(new ItemStack(Items.WATER_BUCKET));
        }
    }

    /**
     * Every drink the kettle has, picked in the book: the lid comes off, what it is made of goes in from the inventory as the
     * kettle reads it, its water is poured and the bucket comes back (no bottle: the drink waits in the kettle); hot enough, the
     * kettle makes that drink of it.
     * The wait is skipped.
     */
    public static void everyTeaIsLaidFromTheBookAndBrews(GameTestHelper helper) {
        if (withoutHerbalBrews(helper)) {
            return;
        }
        List<Tea> teas = HerbalTeas.all(helper.getLevel()).stream().filter(tea -> Cookable.all(tea.ingredients())).toList();
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        Setup[] setup = new Setup[1];
        int step = 12;
        helper.onEachTick(() -> {
            int tick = (int) helper.getTick();
            int index = tick / step;
            if (index >= teas.size()) {
                helper.succeed();
                return;
            }
            Tea tea = teas.get(index);
            if (setup[0] != null && tick % step != 0) {
                setup[0].logic.tick(setup[0].station);
            }
            switch (tick % step) {
                case 0 -> {
                    if (setup[0] != null) {
                        setup[0].container().clearContent();
                    }
                    helper.setBlock(AT, Blocks.AIR);
                    setup[0] = kettle(helper, AT, false);
                    setup[0].station.users.add(player);
                    setup[0].logic.onOpen(setup[0].station, player);
                    give(player, tea);
                    CompoundTag fill = new CompoundTag();
                    fill.putString(KettleStation.TEA, tea.id().toString());
                    setup[0].logic.onCustomAction(setup[0].station, player, KettleStation.ACTION_FILL, fill);
                }
                case 4 -> {
                    String making = setup[0].station.state().data().getString(KettleStation.MAKING);
                    helper.assertTrue(tea.id().toString().equals(making), tea.id() + " is what the kettle holds after the book laid it in, got " + making);
                    helper.assertTrue(KettleStation.isOpen(setup[0].station.state()), tea.id() + ": the book took the lid off");
                    helper.assertTrue(setup[0].container().getItem(Kettle.BOTTLE_SLOT).isEmpty(), tea.id() + ": no bottle is laid in");
                    helper.assertTrue(setup[0].kettle.water() >= tea.water(), tea.id() + " has its water, got " + setup[0].kettle.water());
                    helper.assertTrue(tea.water() == 0 || player.getInventory().countItem(Items.BUCKET) == 1, tea.id() + ": the bucket came back");
                    setup[0].kettle.setHeat(Kettle.FULL);
                }
                case 7 -> {
                    helper.assertTrue(setup[0].kettle.time() > 0, tea.id() + " brews once the kettle is hot");
                    setup[0].kettle.setTime(setup[0].kettle.needed() - 1);
                }
                case 10 -> helper.assertTrue(setup[0].kettle.waiting().is(tea.result().getItem()),
                        tea.id() + " is made and waits in the kettle, got " + setup[0].kettle.waiting());
                default -> {
                }
            }
        });
    }

    /**
     * The kettle's scene is one the library reads, with every part the station shows things on: the kettle and its lid, the water,
     * a place for each ingredient, the spout and the bottle filled there, the drinks a hopper's bottles were filled with and what
     * feeds the heat, and no other slot; the badge with its gauges and the book.
     */
    public static void theKettlesSceneIsRead(GameTestHelper helper) {
        List<String> nodes = new ArrayList<>(List.of("kettle", "body", "lid", "water", "mouth", "dip", "spout", "cup", "served", "embers", "fire",
                "offer", "pour_hand", "badge", "dial_track", "dial", "gauge_water", "gauge_heat", "gauge_marks", "preview", "book_open"));
        for (int i = 0; i < Kettle.INGREDIENTS; i++) {
            nodes.add("item_" + i);
        }
        StationTests.sceneIsRead(helper, "tea_kettle", nodes, KettleStation.SCHEMA,
                List.of(KettleStation.INGREDIENTS, KettleStation.SERVED, KettleStation.EMBERS));
        helper.succeed();
    }
}
