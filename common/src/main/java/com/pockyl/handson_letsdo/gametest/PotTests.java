package com.pockyl.handson_letsdo.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
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
import com.pockyl.handson_letsdo.Cookable;
import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.station.BlockOwner;
import com.pockyl.handson_letsdo.station.CookingPotStation;
import com.pockyl.handson_letsdo.station.Dish;
import com.pockyl.handson_letsdo.station.FarmCharmDishes;
import com.pockyl.handson_letsdo.station.Pot;
import com.pockyl.handson_letsdo.station.PotCooking;
import com.pockyl.handson_letsdo.station.PotLogic;
import com.pockyl.handson_letsdo.station.StirredPot;

import java.util.List;
import java.util.UUID;

/**
 * The cooking pot's rules against a real pot of Farm &amp; Charm, the same on every loader; each loader's holder class calls them.
 * They pass at once without Farm &amp; Charm.
 */
public final class PotTests {
    /** Longer than Farm &amp; Charm cooks one serving for: one test waits for a pot fed by hoppers. */
    public static final int OWN_TIMEOUT = Pot.UPSTREAM_TIME + 200;

    private PotTests() {
    }

    @SuppressWarnings("removal")
    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(new ResourceLocation(id));
    }

    private static Item farm(String path) {
        return item(FarmCharmDishes.MOD_ID + ":" + path);
    }

    private record Setup(TestStation station, PotLogic logic, Pot pot, PotCooking cooking) {
        Container container() {
            return pot.container();
        }
    }

    /** A pot at {@code at}, on a magma block or on stone, with the logic the station would make for it. */
    private static Setup pot(GameTestHelper helper, BlockPos at, boolean heated) {
        helper.setBlock(at.below(), heated ? Blocks.MAGMA_BLOCK : Blocks.STONE);
        helper.setBlock(at, Pot.COOKING_POT.blockSupplier().get());
        BlockPos pos = helper.absolutePos(at);
        TestStation station = new TestStation(helper.getLevel(), pos, CookingPotStation.TYPE);
        Pot pot = Pot.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(pot != null, "Farm & Charm's pot is the pot the station was made for");
        helper.assertTrue(pot.cooking() != null, "the pot waits for the spoon: the stirring is in it");
        return new Setup(station, new PotLogic(station, Pot.COOKING_POT), pot, pot.cooking());
    }

    private static Setup pot(GameTestHelper helper) {
        return pot(helper, new BlockPos(1, 2, 1), true);
    }

    private static boolean withoutFarmAndCharm(GameTestHelper helper) {
        if (HandsOnLetsDo.has(FarmCharmDishes.MOD_ID)) {
            return false;
        }
        helper.succeed();
        return true;
    }

    /** A cook comes to the pot and stays: the station ticks for them every tick from now on. */
    private static void attend(GameTestHelper helper, Setup setup, Player player) {
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        helper.onEachTick(() -> {
            if (!setup.station.users.isEmpty()) {
                setup.logic.tick(setup.station);
            }
        });
    }

    /** Puts {@code stack} into the pot as a cook at the station does. */
    private static void putIn(Setup setup, int slot, ItemStack stack) {
        SlotRef ref = SlotRef.of(CookingPotStation.INGREDIENTS, slot);
        setup.station.state().setItem(ref, stack);
        setup.logic.onSlotChanged(setup.station, ref);
    }

    /** One stroke of the spoon, at {@code angle} round the pot, most of the way out. */
    private static void stir(Setup setup, Player player, double angle) {
        CompoundTag where = new CompoundTag();
        where.putFloat(CookingPotStation.STIR_X, (float) (Math.cos(angle) * 0.9));
        where.putFloat(CookingPotStation.STIR_Z, (float) (Math.sin(angle) * 0.9));
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_STIR, where);
    }

    /** The spoon goes round steadily for {@code ticks} ticks from now: wide rounds one way, a stroke every other tick. */
    private static void stirFor(GameTestHelper helper, Setup setup, Player player, int ticks) {
        for (int tick = 0; tick < ticks; tick += 2) {
            double angle = tick * Math.PI / 10.0;
            helper.runAfterDelay(tick, () -> stir(setup, player, angle));
        }
    }

    private static void scoop(Setup setup, Player player, boolean all, boolean fromInventory) {
        CompoundTag how = new CompoundTag();
        how.putBoolean(CookingPotStation.SCOOP_ALL, all);
        how.putBoolean(CookingPotStation.FROM_INVENTORY, fromInventory);
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_SCOOP, how);
    }

    private static int count(Container container, int slot) {
        return container.getItem(slot).getCount();
    }

    /**
     * The pot is a station exactly when Farm &amp; Charm is installed, and then its block entity is what the station expects: eight
     * slots, two numbers, the stirring in it, and its dishes read from the game's recipes.
     */
    public static void thePotIsAStationOnlyWithFarmAndCharm(GameTestHelper helper) {
        boolean installed = HandsOnLetsDo.has(FarmCharmDishes.MOD_ID);
        helper.assertTrue(Diegesis.stationType(CookingPotStation.ID).isPresent() == (installed && Pot.COOKING_POT.active()),
                "the pot's station exists with Farm & Charm and only with it");
        if (!installed) {
            helper.succeed();
            return;
        }
        Setup setup = pot(helper);
        BlockState state = helper.getLevel().getBlockState(setup.station.pos());
        helper.assertTrue(Diegesis.stationFor(state).filter(type -> type == CookingPotStation.TYPE).isPresent(), "the pot opens its station");
        helper.assertTrue(setup.container().getContainerSize() == Pot.SLOTS, "eight slots");
        helper.assertTrue(Pot.of(helper.getLevel().getBlockEntity(setup.station.pos().below())) == null, "a magma block is no pot");
        int dishes = Pot.COOKING_POT.dishes(helper.getLevel()).size();
        helper.assertTrue(dishes >= 10, "Farm & Charm's dishes are read, got " + dishes);
        helper.assertTrue(setup.pot.isHeated(), "a magma block under the pot heats it, as Farm & Charm's tag says");
        Player player = helper.makeMockPlayer();
        setup.logic.onOpen(setup.station, player);
        helper.assertTrue(player.getUUID().equals(BlockOwner.get(setup.pot.entity())), "the pot is told who cooks at it");
        setup.logic.tick(setup.station);
        helper.assertTrue(setup.station.state().getControl(CookingPotStation.HEATED) > 0.5F, "the station shows the heat");
        helper.assertTrue(setup.station.state().getTank(CookingPotStation.BROTH).amount() > 0, "there is water in an empty pot");
        Setup cold = pot(helper, new BlockPos(3, 2, 1), false);
        cold.logic.tick(cold.station);
        helper.assertTrue(cold.station.state().getControl(CookingPotStation.HEATED) < 0.5F, "a pot on stone is cold");
        helper.succeed();
    }

    /**
     * What is put in through the station lies in the pot's own container, and what comes into the pot is seen at the station. Only
     * what some dish is made of goes into the water; bowls do not.
     */
    public static void thePotsSlotsAreItsOwn(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer();
        SlotRef first = SlotRef.of(CookingPotStation.INGREDIENTS, 0);
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, first, new ItemStack(Items.POTATO)), "a potato is in a soup: it goes in");
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, first, new ItemStack(Items.COBBLESTONE)), "no dish takes cobblestone");
        helper.assertFalse(CookingPotStation.SCHEMA.slotGroups().get(CookingPotStation.INGREDIENTS).rules().accepts().test(new ItemStack(Items.BOWL)),
                "a bowl does not go into the water");
        putIn(setup, 0, new ItemStack(Items.POTATO, 3));
        helper.assertTrue(setup.container().getItem(0).is(Items.POTATO) && count(setup.container(), 0) == 3,
                "the potatoes lie in the pot's first slot");
        setup.container().setItem(Pot.OUTPUT_SLOT, new ItemStack(farm("potato_soup"), 2));
        setup.container().setItem(Pot.VESSEL_SLOT, new ItemStack(Items.BOWL, 5));
        setup.station.state().pollStorages();
        helper.assertTrue(setup.station.state().getItem(CookingPotStation.SERVED_SLOT).is(farm("potato_soup")), "the pot's dish is the station's");
        helper.assertTrue(setup.station.state().getItem(CookingPotStation.VESSEL_SLOT).getCount() == 5, "and a hopper's bowls");
        helper.assertFalse(setup.logic.mayTake(setup.station, player, CookingPotStation.MEAL_SLOT), "the soup is not taken out of a slot");
        helper.succeed();
    }

    /**
     * Food a cook put in at the station waits for the spoon: with a bowl lying in the pot, where Farm &amp; Charm alone would cook,
     * nothing moves. Stirred, it cooks, and when it is done the bowl that lay in the pot is filled.
     */
    public static void aStirredPotCooksAndAnUnstirredOneWaits(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer();
        attend(helper, setup, player);
        setup.container().setItem(Pot.VESSEL_SLOT, new ItemStack(Items.BOWL));
        putIn(setup, 0, new ItemStack(Items.POTATO));
        putIn(setup, 1, new ItemStack(farm("onion")));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(setup.cooking.isByHand(), "the cook's food is the cook's");
            helper.assertTrue(setup.cooking.progress(setup.pot) == 0.0F && setup.pot.time() == 0, "left alone it does not cook, got "
                    + setup.pot.time() + " ticks");
            helper.assertTrue(setup.pot.output().isEmpty(), "and nothing is made");
            stirFor(helper, setup, player, 40);
        });
        helper.runAfterDelay(70, () -> {
            float progress = setup.cooking.progress(setup.pot);
            helper.assertTrue(progress > 0.1F, "stirred it cooks, got " + progress);
            helper.assertTrue(setup.station.state().getControl(CookingPotStation.STIRRING) > 0.5F, "everybody sees the spoon go");
            setup.cooking.setProgress(setup.pot, 0.99F);
        });
        helper.runAfterDelay(76, () -> {
            helper.assertTrue(setup.pot.output().is(farm("potato_soup")) && setup.pot.output().getCount() == 1,
                    "the soup is done and the bowl that lay in the pot is filled, got " + setup.pot.output());
            helper.assertTrue(setup.container().getItem(0).isEmpty() && setup.container().getItem(1).isEmpty() && setup.pot.vessels().isEmpty(),
                    "out of the potato, the onion and the bowl");
            helper.assertTrue(setup.cooking.servings() == 0, "no soup is left waiting");
            helper.succeed();
        });
    }

    /** A soup of {@code sets} potatoes and onions waits in the pot of {@code setup}, cooked under the spoon of {@code player}. */
    private static void cookPotatoSoup(GameTestHelper helper, Setup setup, Player player, int potatoes, int onions) {
        attend(helper, setup, player);
        putIn(setup, 0, new ItemStack(Items.POTATO, potatoes));
        putIn(setup, 1, new ItemStack(farm("onion"), onions));
        helper.runAfterDelay(2, () -> {
            setup.cooking.setProgress(setup.pot, 0.995F);
            stir(setup, player, 0.0);
        });
    }

    /**
     * The soup that is done waits in the pot and is filled into bowls: a bowl on the cursor takes one serving, the last of a stack
     * stays in the hand filled, an empty hand scoops with the bowls of the inventory, with shift all there are. A bottle takes no
     * soup, and no bowl, no soup. Each serving uses one of each ingredient and one bowl.
     */
    public static void theSoupIsFilledIntoBowls(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer();
        cookPotatoSoup(helper, setup, player, 4, 4);
        Item soup = farm("potato_soup");
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(setup.cooking.servings() == 4, "four servings wait in the pot, got " + setup.cooking.servings());
            helper.assertTrue(setup.pot.output().isEmpty(), "none is in a bowl yet");
            setup.station.state().pollStorages();
            helper.assertTrue(setup.station.state().getItem(CookingPotStation.MEAL_SLOT).is(soup), "the station shows the soup");
            helper.assertTrue(setup.station.state().getItem(SlotRef.of(CookingPotStation.INGREDIENTS, 0)).isEmpty(),
                    "what is soup no longer floats as food");
            helper.assertFalse(setup.logic.mayPlace(setup.station, player, SlotRef.of(CookingPotStation.INGREDIENTS, 2), new ItemStack(Items.POTATO)),
                    "a pot of finished soup takes nothing more");
            scoop(setup, player, false, true);
            helper.assertTrue(setup.cooking.servings() == 4, "no bowl, no soup");
            player.inventoryMenu.setCarried(new ItemStack(Items.GLASS_BOTTLE));
            scoop(setup, player, false, false);
            helper.assertTrue(setup.cooking.servings() == 4 && player.inventoryMenu.getCarried().is(Items.GLASS_BOTTLE), "a bottle takes no soup");
            player.inventoryMenu.setCarried(new ItemStack(Items.BOWL, 2));
            scoop(setup, player, false, false);
            helper.assertTrue(setup.cooking.servings() == 3 && player.inventoryMenu.getCarried().getCount() == 1
                    && player.getInventory().countItem(soup) == 1, "a bowl on the cursor takes one serving, into the inventory");
            helper.assertTrue(setup.station.effects.contains(CookingPotStation.EFFECT_SCOOPED), "everybody sees it scooped");
            scoop(setup, player, false, false);
            helper.assertTrue(setup.cooking.servings() == 2 && player.inventoryMenu.getCarried().is(soup), "the last bowl held stays in the hand, filled");
            player.inventoryMenu.setCarried(ItemStack.EMPTY);
            player.getInventory().add(new ItemStack(Items.BOWL, 5));
            scoop(setup, player, true, true);
            helper.assertTrue(setup.cooking.servings() == 0 && player.getInventory().countItem(soup) == 3
                    && player.getInventory().countItem(Items.BOWL) == 3, "an empty hand scoops the rest with bowls from the inventory, got "
                    + player.getInventory().countItem(soup) + " soups");
            helper.assertTrue(setup.container().getItem(0).isEmpty() && setup.container().getItem(1).isEmpty(),
                    "four servings used the four potatoes and the four onions");
            scoop(setup, player, true, true);
            helper.assertTrue(player.getInventory().countItem(soup) == 3, "an empty pot gives nothing");
            helper.succeed();
        });
    }

    /**
     * Uneven stacks make only what they hold: five potatoes and three onions are three soups, the two potatoes over float on and
     * can be taken back, and after the three servings nothing is left to make a fourth.
     */
    public static void unevenStacksMakeNoServingsFromNothing(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer();
        cookPotatoSoup(helper, setup, player, 5, 3);
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(setup.cooking.servings() == 3, "three onions make three soups, got " + setup.cooking.servings());
            SlotRef potatoes = SlotRef.of(CookingPotStation.INGREDIENTS, 0);
            setup.station.state().pollStorages();
            helper.assertTrue(setup.station.state().getItem(potatoes).getCount() == 2, "the two potatoes over float on");
            setup.station.state().setItem(potatoes, ItemStack.EMPTY);
            helper.assertTrue(count(setup.container(), 0) == 3, "taken out, the soup's own three stay in the pot");
            player.inventoryMenu.setCarried(new ItemStack(Items.BOWL, 8));
            scoop(setup, player, true, false);
            helper.assertTrue(player.getInventory().countItem(farm("potato_soup")) == 3 && player.inventoryMenu.getCarried().getCount() == 5,
                    "three servings into three bowls, got " + player.getInventory().countItem(farm("potato_soup")));
            helper.assertTrue(setup.container().getItem(0).isEmpty() && setup.container().getItem(1).isEmpty(), "and the pot is empty");
            helper.succeed();
        });
    }

    /**
     * What an ingredient leaves goes to the cook: the bucket of the water yeast is made with comes back with the yeast, and none
     * stays in the pot.
     */
    public static void whatAnIngredientLeavesGoesToTheCook(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer();
        attend(helper, setup, player);
        putIn(setup, 0, new ItemStack(Items.SUGAR));
        putIn(setup, 1, new ItemStack(Items.WATER_BUCKET));
        putIn(setup, 2, new ItemStack(Items.WHEAT));
        helper.runAfterDelay(2, () -> {
            setup.cooking.setProgress(setup.pot, 0.995F);
            stir(setup, player, 0.0);
        });
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(setup.cooking.servings() == 1, "the yeast is done, got " + setup.cooking.servings());
            player.inventoryMenu.setCarried(new ItemStack(Items.BOWL));
            scoop(setup, player, false, false);
            Item yeast = farm("yeast");
            helper.assertTrue(player.inventoryMenu.getCarried().is(yeast) && player.inventoryMenu.getCarried().getCount() == 12,
                    "one bowl takes the whole serving, twelve yeast, got " + player.inventoryMenu.getCarried());
            helper.assertTrue(player.getInventory().countItem(Items.BUCKET) == 1, "the bucket the water came in goes to the cook");
            helper.assertTrue(setup.container().isEmpty(), "and nothing stays in the pot");
            helper.succeed();
        });
    }

    /**
     * Steady wide rounds by a cook who knows the dish earn three stars, and three stars make three servings of two sets: the third
     * uses a bowl like the others and no food.
     */
    public static void starsGiveBonusServings(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        Setup setup = pot(helper);
        Item soup = farm("potato_soup");
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "cook"));
        player.awardStat(Stats.ITEM_CRAFTED.get(soup), 40);
        attend(helper, setup, player);
        putIn(setup, 0, new ItemStack(Items.POTATO, 2));
        putIn(setup, 1, new ItemStack(farm("onion"), 2));
        // Two servings take the square root of two times as long as one, less the pace of the spoon.
        int ticks = (int) (Pot.COOKING_POT.handTime() * Math.sqrt(2.0) / 1.5) + 12;
        stirFor(helper, setup, player, ticks);
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(setup.station.state().getControl(CookingPotStation.STARS) > 2.99F, "steady wide rounds earn three stars, got "
                    + setup.station.state().getControl(CookingPotStation.STARS));
            helper.assertTrue(setup.station.state().data().getInt(CookingPotStation.OPEN_STARS) == 3, "a cook who made forty has all three open");
        });
        helper.runAfterDelay(ticks + 4, () -> {
            helper.assertTrue(setup.cooking.servings() == 3, "two servings come out as three, got " + setup.cooking.servings());
            helper.assertTrue(setup.station.state().data().getInt(CookingPotStation.BONUS) == 1 && setup.station.state().data()
                    .getInt(CookingPotStation.GRADE) == 3, "the station tells the stars and what they added");
            player.inventoryMenu.setCarried(new ItemStack(Items.BOWL, 5));
            scoop(setup, player, true, false);
            helper.assertTrue(player.getInventory().countItem(soup) == 3 && player.inventoryMenu.getCarried().getCount() == 2,
                    "three soups in three bowls, got " + player.getInventory().countItem(soup));
            helper.assertTrue(setup.container().getItem(0).isEmpty() && setup.container().getItem(1).isEmpty() && setup.cooking.servings() == 0,
                    "out of the two potatoes and the two onions, and no fourth");
            helper.succeed();
        });
    }

    /**
     * Food hoppers bring, bowls too, cooks Farm &amp; Charm's way with nobody at the pot: one serving, in Farm &amp; Charm's own
     * time, into its dish slot.
     */
    public static void aHopperFedPotCooksUpstreamsWay(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        Setup setup = pot(helper, at, true);
        helper.setBlock(at.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        helper.setBlock(at.east(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
        Container above = (Container) helper.getBlockEntity(at.above());
        above.setItem(0, new ItemStack(Items.POTATO));
        above.setItem(1, new ItemStack(farm("onion")));
        ((Container) helper.getBlockEntity(at.east())).setItem(0, new ItemStack(Items.BOWL));
        long[] began = {-1};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (began[0] < 0 && setup.pot.time() > 0) {
                began[0] = now - setup.pot.time();
                helper.assertFalse(setup.cooking.isByHand(), "a hopper's food is nobody's");
            }
            if (!setup.pot.output().isEmpty()) {
                helper.assertTrue(setup.pot.output().is(farm("potato_soup")) && setup.pot.output().getCount() == 1, "one soup, got "
                        + setup.pot.output());
                long took = now - began[0];
                helper.assertTrue(began[0] >= 0 && Math.abs(took - Pot.UPSTREAM_TIME) <= 2, "in Farm & Charm's own time ("
                        + Pot.UPSTREAM_TIME + "), took " + took);
                helper.assertTrue(setup.cooking.servings() == 0, "and no soup waits for a cook");
                helper.succeed();
            }
        });
    }

    /** A hopper feeding the pot while a cook stands at it still feeds it by itself: its food does not wait for the spoon. */
    public static void aHopperFeedsThePotByItselfBesideACook(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        Setup setup = pot(helper, at, true);
        attend(helper, setup, helper.makeMockPlayer());
        setup.container().setItem(Pot.VESSEL_SLOT, new ItemStack(Items.BOWL));
        helper.setBlock(at.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        Container above = (Container) helper.getBlockEntity(at.above());
        above.setItem(0, new ItemStack(Items.POTATO));
        above.setItem(1, new ItemStack(farm("onion")));
        helper.runAfterDelay(60, () -> {
            helper.assertFalse(setup.cooking.isByHand(), "a hopper's food is nobody's, also beside a cook");
            helper.assertTrue(setup.pot.time() > 20, "it cooks by itself with the cook beside it and nobody stirring, got " + setup.pot.time());
            setup.container().clearContent();
            helper.succeed();
        });
    }

    /**
     * A cook's food cooks Farm &amp; Charm's way while somebody works at the pot in its own menu, which has no spoon, and waits for
     * the spoon again once the menu is shut.
     */
    public static void aCookInTheMenuCooksUpstreamsWay(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        Setup setup = pot(helper);
        attend(helper, setup, helper.makeMockPlayer());
        setup.container().setItem(Pot.VESSEL_SLOT, new ItemStack(Items.BOWL));
        putIn(setup, 0, new ItemStack(Items.POTATO));
        putIn(setup, 1, new ItemStack(farm("onion")));
        ServerPlayer guest = TestPlayers.join(helper);
        // Beside the pot: its menu shuts for whoever is too far from it.
        BlockPos beside = setup.station.pos().above();
        guest.setPos(beside.getX() + 0.5, beside.getY(), beside.getZ() + 0.5);
        int[] atShut = {0};
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(setup.pot.time() == 0, "the cook's food waits for the spoon");
            guest.containerMenu = ((MenuProvider) setup.pot.entity()).createMenu(1, guest.getInventory(), guest);
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(setup.pot.time() > 20, "with the pot's own menu open it cooks Farm & Charm's way, got " + setup.pot.time());
            guest.containerMenu = guest.inventoryMenu;
        });
        helper.runAfterDelay(44, () -> atShut[0] = setup.pot.time());
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(setup.pot.time() <= atShut[0], "the menu shut, it waits for the spoon again, got " + setup.pot.time());
            TestPlayers.leave(helper, guest);
            setup.container().clearContent();
            helper.succeed();
        });
    }

    /**
     * A cook who walks away leaves the pot to Farm &amp; Charm: a little after the station's last tick its own count runs again, with
     * the bowl that lies in it, and what was cooked under the spoon is not lost.
     */
    public static void aPotLeftMidStirGoesBackToUpstreamsTick(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer();
        attend(helper, setup, player);
        setup.container().setItem(Pot.VESSEL_SLOT, new ItemStack(Items.BOWL));
        putIn(setup, 0, new ItemStack(Items.POTATO));
        putIn(setup, 1, new ItemStack(farm("onion")));
        stirFor(helper, setup, player, 20);
        float[] stirred = {0.0F};
        helper.runAfterDelay(30, () -> {
            stirred[0] = setup.cooking.progress(setup.pot);
            helper.assertTrue(stirred[0] > 0.05F, "it cooked under the spoon, got " + stirred[0]);
            // The cook goes: the station ticks no more.
            setup.station.users.clear();
        });
        helper.runAfterDelay(60, () -> helper.assertTrue(setup.pot.output().isEmpty(), "nothing is made the moment the cook turns away"));
        helper.runAfterDelay(140, () -> {
            helper.assertTrue(setup.pot.time() > 40, "the pot's own count runs again, got " + setup.pot.time());
            helper.assertTrue(setup.cooking.progress(setup.pot) >= stirred[0], "and what was cooked under the spoon is kept");
            setup.container().clearContent();
            helper.succeed();
        });
    }

    /** A dish that needs no bowl is made the moment it is done, into the pot's dish slot, and waits there to be taken. */
    public static void aDishThatNeedsNoBowlComesOutWhole(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        Dish goulash = Pot.COOKING_POT.dishes(helper.getLevel()).stream().filter(dish -> dish.vessel().isEmpty()).findFirst().orElse(null);
        if (goulash == null) {
            helper.succeed();
            return;
        }
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer();
        attend(helper, setup, player);
        int slot = 0;
        for (Ingredient ingredient : goulash.ingredients()) {
            if (ingredient.getItems().length > 0) {
                putIn(setup, slot++, ingredient.getItems()[0].copyWithCount(1));
            }
        }
        helper.runAfterDelay(2, () -> {
            setup.cooking.setProgress(setup.pot, 0.995F);
            stir(setup, player, 0.0);
        });
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(ItemStack.isSameItem(setup.pot.output(), goulash.result()), goulash.id() + " comes out by itself, got "
                    + setup.pot.output());
            helper.assertTrue(setup.cooking.servings() == 0, "and no soup waits for a bowl");
            setup.station.state().pollStorages();
            helper.assertTrue(ItemStack.isSameItem(setup.station.state().getItem(CookingPotStation.SERVED_SLOT), goulash.result()),
                    "the station shows it to be taken");
            setup.container().clearContent();
            helper.succeed();
        });
    }

    /**
     * Food thrown in as a cook throws it, alike things together, is laid out the way the pot reads the dish it makes: two tomatoes
     * and sugar are the tomato soup, which wants the tomatoes in two slots. What went in without floating comes back out whole.
     */
    public static void twoAlikeThrownInMakeTheirDish(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer();
        attend(helper, setup, player);
        putIn(setup, 0, new ItemStack(farm("tomato"), 2));
        putIn(setup, 1, new ItemStack(Items.SUGAR));
        helper.runAfterDelay(3, () -> {
            Dish making = setup.cooking.making(helper.getLevel(), setup.pot);
            helper.assertTrue(making != null && making.result().is(farm("simple_tomato_soup")), "two tomatoes and sugar make the tomato soup, got "
                    + (making == null ? "nothing" : making.id()));
            int tomatoSlots = 0;
            for (int slot = 0; slot < Pot.INGREDIENTS; slot++) {
                tomatoSlots += setup.container().getItem(slot).is(farm("tomato")) ? 1 : 0;
            }
            helper.assertTrue(tomatoSlots == 2, "the tomatoes lie in two slots, as the pot reads them, got " + tomatoSlots);
            helper.assertTrue(PotLogic.sinks(new ItemStack(Items.SUGAR)), "sugar dissolves: it does not float");
            setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_TAKE_BACK, new CompoundTag());
            helper.assertTrue(player.getInventory().countItem(Items.SUGAR) == 1, "what went in without floating comes back out");
            setup.container().clearContent();
            helper.succeed();
        });
    }

    /** Puts one set of what {@code dish} takes into the player's inventory, and what it is served in. */
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
     * Every dish the pot has, with whatever mods add theirs, picked in the book: its ingredients go in from the inventory as the
     * pot reads them, the pot makes that dish of them, and under the spoon it is done and served: in its vessel, or by itself. The
     * stirring is skipped.
     */
    public static void everyDishIsLaidFromTheBookAndCooks(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        // Not what only a player who learned it cooks, nor what nobody can make: a tag of a mod that is not here.
        List<Dish> dishes = Pot.COOKING_POT.dishes(helper.getLevel()).stream().filter(dish -> !dish.learned() && Cookable.all(dish.ingredients()))
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
                    // A new pot for each dish.
                    if (setup[0] != null) {
                        setup[0].container().clearContent();
                    }
                    helper.setBlock(at, Blocks.AIR);
                    setup[0] = pot(helper, at, true);
                    setup[0].station.users.add(player);
                    setup[0].logic.onOpen(setup[0].station, player);
                    give(player, dish);
                    CompoundTag fill = new CompoundTag();
                    fill.putString(CookingPotStation.DISH, dish.id().toString());
                    setup[0].logic.onCustomAction(setup[0].station, player, CookingPotStation.ACTION_FILL, fill);
                }
                case 2 -> {
                    Dish making = setup[0].cooking.making(helper.getLevel(), setup[0].pot);
                    helper.assertTrue(making != null && making.id().equals(dish.id()), dish.id() + " is what the pot makes of what the book laid in, got "
                            + (making == null ? "nothing" : making.id()));
                    setup[0].cooking.setProgress(setup[0].pot, 0.995F);
                    stir(setup[0], player, 0.0);
                }
                case 5 -> {
                    if (dish.vessel().isEmpty()) {
                        helper.assertTrue(ItemStack.isSameItem(setup[0].pot.output(), dish.result()), dish.id() + " is made, got "
                                + setup[0].pot.output());
                    } else {
                        helper.assertTrue(setup[0].cooking.servings() >= 1, dish.id() + " is done and waits for its vessel");
                        scoop(setup[0], player, false, true);
                        helper.assertTrue(player.getInventory().countItem(dish.result().getItem()) >= dish.result().getCount(),
                                dish.id() + " is served in " + dish.vessel());
                    }
                }
                default -> {
                }
            }
        });
    }

    /**
     * A recipe that wants learning cooks at the station for the cook who learned it, and for nobody else: the pot is told who
     * cooks, as its own menu tells it.
     */
    public static void aLearnedDishCooksForTheCookWhoLearnedIt(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        ResourceLocation id = HandsOnLetsDo.id("test_learned_soup");
        FarmCharmTestRecipes.addLearnedPotRecipe(helper.getLevel(), id, new ItemStack(Items.MUSHROOM_STEW), Items.BROWN_MUSHROOM, Items.RED_MUSHROOM);
        Setup setup = pot(helper);
        ServerPlayer cook = TestPlayers.join(helper);
        attend(helper, setup, cook);
        putIn(setup, 0, new ItemStack(Items.BROWN_MUSHROOM));
        putIn(setup, 1, new ItemStack(Items.RED_MUSHROOM));
        helper.runAfterDelay(2, () -> {
            setup.cooking.setProgress(setup.pot, 0.995F);
            stir(setup, cook, 0.0);
        });
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(setup.cooking.servings() == 0, "a cook who has not learned the recipe makes nothing of it");
            FarmCharmTestRecipes.teach(cook, helper.getLevel(), id);
            setup.cooking.setProgress(setup.pot, 0.995F);
            stir(setup, cook, 0.0);
        });
        helper.runAfterDelay(14, () -> {
            helper.assertTrue(setup.cooking.servings() == 1, "once learned it cooks under their spoon, got " + setup.cooking.servings());
            cook.inventoryMenu.setCarried(new ItemStack(Items.BOWL));
            scoop(setup, cook, false, false);
            helper.assertTrue(cook.inventoryMenu.getCarried().is(Items.MUSHROOM_STEW), "and the pot serves it to them, got "
                    + cook.inventoryMenu.getCarried());
            FarmCharmTestRecipes.remove(helper.getLevel(), id);
            TestPlayers.leave(helper, cook);
            helper.succeed();
        });
    }

    /**
     * The pot tells its clients what it holds, so that each shows it as its station does with nobody at it: a client's copy made
     * from what the pot sends knows it came from a server with the station, what floats in it and the soup that waits, and holds
     * nothing once the pot is emptied. What the pot saves to the world does not carry that word.
     */
    public static void thePotTellsItsClientsWhatItHolds(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer();
        cookPotatoSoup(helper, setup, player, 3, 2);
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(setup.cooking.servings() == 2, "two servings wait in the pot, got " + setup.cooking.servings());
            BlockState state = helper.getLevel().getBlockState(setup.station.pos());
            BlockEntity copy = ((EntityBlock) state.getBlock()).newBlockEntity(setup.station.pos(), state);
            helper.assertTrue(copy instanceof StirredPot, "the pot's block entity has the station's part");
            StirredPot seen = (StirredPot) copy;
            copy.load(setup.pot.entity().saveWithoutMetadata());
            helper.assertFalse(seen.handson$told(), "what the pot saves does not say it was told");
            copy.load(setup.pot.entity().getUpdateTag());
            helper.assertTrue(seen.handson$told(), "what it sends says so");
            Container held = (Container) copy;
            helper.assertTrue(held.getItem(0).is(Items.POTATO) && held.getItem(0).getCount() == 3 && held.getItem(1).getCount() == 2,
                    "a client's copy holds what lies in the pot");
            helper.assertTrue(seen.handson$cooking().servings() == 2 && seen.handson$cooking().reserved() == 2,
                    "and the soup that waits in it");
            setup.container().clearContent();
            setup.cooking.load(new CompoundTag());
            copy.load(setup.pot.entity().getUpdateTag());
            helper.assertTrue(held.isEmpty() && seen.handson$cooking().servings() == 0, "and nothing once it is emptied");
            helper.succeed();
        });
    }

    /** All the station tells a client who comes to a full pot is far below what one packet may hold. */
    public static void thePotsDataStaysSmall(GameTestHelper helper) {
        if (withoutFarmAndCharm(helper)) {
            return;
        }
        Setup setup = pot(helper);
        Container container = setup.container();
        Item[] food = {Items.POTATO, Items.CARROT, Items.BEETROOT, Items.SUGAR, Items.WHEAT, Items.APPLE};
        for (int slot = 0; slot < Pot.INGREDIENTS; slot++) {
            container.setItem(slot, new ItemStack(food[slot], 64));
        }
        container.setItem(Pot.VESSEL_SLOT, new ItemStack(Items.BOWL, 64));
        container.setItem(Pot.OUTPUT_SLOT, new ItemStack(farm("potato_soup"), 64));
        setup.logic.tick(setup.station);
        setup.station.state().pollStorages();
        setup.station.state().markAllDirty();
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        setup.station.state().writeChanges(buffer, true);
        int bytes = buffer.readableBytes();
        helper.assertTrue(bytes > 0 && bytes < 32 * 1024, "the station's whole data is " + bytes + " bytes");
        container.clearContent();
        helper.succeed();
    }

    /**
     * The pot's scene is one the library reads, with every part the station shows things on: a place for each ingredient and for
     * the dish that is done, and no other slot (the soup is in the pot, bowls are dipped), the water, the spoon, the lid, the
     * hands, the badge and the book.
     */
    public static void thePotsSceneIsRead(GameTestHelper helper) {
        List<String> nodes = new java.util.ArrayList<>(List.of("pot", "support", "lid", "ladle", "broth", "mouth", "served", "scoop", "soup", "sunk",
                "badge", "dial_track", "dial", "preview", "book_open"));
        for (int i = 0; i < Pot.INGREDIENTS; i++) {
            nodes.addAll(List.of("item_" + i, "melt_" + i, "hand_" + i, "spill_" + i));
        }
        StationTests.sceneIsRead(helper, "cooking_pot", nodes, CookingPotStation.SCHEMA,
                List.of(CookingPotStation.INGREDIENTS, CookingPotStation.SERVED));
        helper.succeed();
    }
}
