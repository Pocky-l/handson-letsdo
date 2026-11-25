package com.pockyl.handson_letsdo.gametest;

import net.minecraft.world.level.GameType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_letsdo.Cookable;
import com.pockyl.handson_letsdo.HandsOnConfig;
import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.station.Bowl;
import com.pockyl.handson_letsdo.station.BowlLogic;
import com.pockyl.handson_letsdo.station.BowlRecipes;
import com.pockyl.handson_letsdo.station.BowlStation;
import com.pockyl.handson_letsdo.station.Dish;
import com.pockyl.handson_letsdo.station.FarmCharmDishes;
import com.pockyl.handson_letsdo.station.HandPath;

import java.util.ArrayList;
import java.util.List;

/**
 * The crafting bowl's rules against a real bowl of Farm &amp; Charm: a whole bowlful is mixed at once by the bowl's own tick once the
 * whisk has gone its circles' way through it, in any figure, and only while the cook whisks; the result is taken from the badge, and a
 * liquid poured in is drawn back with its container. They pass at once without Farm &amp; Charm.
 */
public final class BowlTests {
    private static final BlockPos AT = new BlockPos(1, 2, 1);
    /** The hand's steps along its way, pixels. */
    private static final float STEP = 0.5F;
    /** Reported once a tick, the hand goes this far at most each time: slower than the thickest batter lets it. */
    private static final float TICK_STEP = 1.2F;
    /** How far to each side figures of eight go. */
    private static final float WHISK_EIGHT = 2.4F;

    private BowlTests() {
    }

    /** A bowl with the logic the station makes for it. */
    private record Setup(TestStation station, BowlLogic logic, Bowl bowl) {
        Container container() {
            return bowl.container();
        }
    }

    private static Setup bowl(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at.below(), Blocks.STONE);
        helper.setBlock(at, BowlStation.BOWL.get().defaultBlockState());
        BlockPos pos = helper.absolutePos(at);
        TestStation station = new TestStation(helper.getLevel(), pos, BowlStation.TYPE);
        Bowl bowl = Bowl.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(bowl != null, "Farm & Charm's crafting bowl is the bowl the station was made for, with what the station adds to it");
        return new Setup(station, new BowlLogic(station), bowl);
    }

    private static boolean withoutFarmCharm(GameTestHelper helper) {
        if (HandsOnLetsDo.has(FarmCharmDishes.MOD_ID)) {
            return false;
        }
        helper.succeed();
        return true;
    }

    private static Player cook(GameTestHelper helper, Setup setup) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        return player;
    }

    /**
     * The cook's whisk goes {@code circles} circles' way through the bowlful, in small steps, as the hand's reports count it: what a
     * bowlful takes, apart from how fast the hand may go.
     */
    private static void stir(Setup setup, Player player, float circles) {
        int steps = Math.round(circles * BowlStation.CIRCLE_WAY / STEP);
        for (int i = 0; i < steps; i++) {
            setup.logic.stir(setup.station, player, STEP);
        }
    }

    private static void reportAt(Setup setup, Player player, float x, float z) {
        CompoundTag data = new CompoundTag();
        data.putFloat(BowlStation.X, x);
        data.putFloat(BowlStation.Z, z);
        setup.logic.onCustomAction(setup.station, player, BowlStation.ACTION_STIR, data);
    }

    /**
     * Places along figures of eight through the middle, from {@code t} on, a hand's report a tick apart (no further than
     * {@link #TICK_STEP}), until they have gone {@code way} pixels; {@code t} is left where they end.
     */
    private static List<float[]> eights(float[] t, float way) {
        List<float[]> places = new ArrayList<>();
        float gone = 0.0F;
        float lastX = WHISK_EIGHT * Mth.sin(t[0]);
        float lastZ = WHISK_EIGHT * Mth.sin(t[0]) * Mth.cos(t[0]);
        while (gone < way) {
            float x = lastX;
            float z = lastZ;
            float step = 0.0F;
            while (step < TICK_STEP * 0.95F) {
                t[0] += 0.005F;
                float nx = WHISK_EIGHT * Mth.sin(t[0]);
                float nz = WHISK_EIGHT * Mth.sin(t[0]) * Mth.cos(t[0]);
                step += (float) Math.hypot(nx - x, nz - z);
                x = nx;
                z = nz;
            }
            gone += (float) Math.hypot(x - lastX, z - lastZ);
            lastX = x;
            lastZ = z;
            places.add(new float[] {x, z});
        }
        return places;
    }

    /** The cook's hand reports {@code places} one a tick, from the next tick on; then {@code then}. */
    private static void whisk(GameTestHelper helper, Setup setup, Player player, List<float[]> places, Runnable then) {
        for (int i = 0; i < places.size(); i++) {
            float[] place = places.get(i);
            helper.runAfterDelay(i + 1, () -> reportAt(setup, player, place[0], place[1]));
        }
        helper.runAfterDelay(places.size() + 1, then);
    }

    /** Whether the bowl's own count of a set is back at nothing, as Farm &amp; Charm's own empty hand leaves it. */
    private static boolean countIsBack(GameTestHelper helper, Setup setup) {
        BlockState state = helper.getLevel().getBlockState(setup.station.pos());
        for (var property : state.getProperties()) {
            if (property instanceof IntegerProperty number && property.getName().equals("stirred") && state.getValue(number) != 0) {
                return false;
            }
        }
        return true;
    }

    /** {@code sets} of each of the recipe's ingredients into the bowl's slots, as a cook lays them. */
    private static void lay(Setup setup, Dish recipe, int sets) {
        List<Ingredient> ingredients = recipe.ingredients();
        for (int i = 0; i < ingredients.size(); i++) {
            ItemStack item = ingredients.get(i).getItems()[0];
            setup.station.state().setItem(SlotRef.of(BowlStation.INGREDIENTS, i), item.copyWithCount(Math.min(sets, item.getMaxStackSize())));
            setup.logic.onSlotChanged(setup.station, SlotRef.of(BowlStation.INGREDIENTS, i));
        }
    }

    /** A recipe of several ingredients that stack, every one of them with an item: the bowl's dog food or farmer's salad. */
    private static Dish several(GameTestHelper helper) {
        for (Dish recipe : BowlRecipes.all(helper.getLevel())) {
            if (recipe.ingredients().size() >= 2 && Cookable.all(recipe.ingredients())
                    && recipe.ingredients().stream().allMatch(ingredient -> ingredient.getItems()[0].getMaxStackSize() >= 16)) {
                return recipe;
            }
        }
        return null;
    }

    public static void theBowlIsAStationOnlyWithFarmCharm(GameTestHelper helper) {
        boolean installed = HandsOnLetsDo.has(FarmCharmDishes.MOD_ID);
        helper.assertTrue(Diegesis.stationType(BowlStation.ID).isPresent() == (installed && HandsOnConfig.craftingBowl()),
                "the bowl's station exists with Farm & Charm and only with it");
        if (!installed) {
            helper.succeed();
            return;
        }
        Setup setup = bowl(helper, AT);
        BlockState state = helper.getLevel().getBlockState(setup.station.pos());
        helper.assertTrue(Diegesis.stationFor(state).filter(type -> type == BowlStation.TYPE).isPresent(), "the bowl opens its station");
        helper.assertTrue(setup.container().getContainerSize() == Bowl.SLOTS, "five slots");
        helper.assertTrue(setup.bowl.hand() != null, "the station's part is in the bowl (its mixin applied)");
        helper.assertTrue(!BowlRecipes.all(helper.getLevel()).isEmpty(), "Farm & Charm's bowl recipes are read");
        helper.succeed();
    }

    /**
     * A bowlful is mixed at once by its circles: one set takes sixteen, four sets thirty-two, and every set comes out together. Before its
     * circles are done nothing comes out.
     */
    public static void aBowlfulIsMixedByItsCircles(GameTestHelper helper) {
        if (withoutFarmCharm(helper)) {
            return;
        }
        Dish recipe = several(helper);
        helper.assertTrue(recipe != null, "a recipe of several ingredients to mix");
        helper.assertTrue(BowlStation.circles(1) == 16 && BowlStation.circles(4) == 32 && BowlStation.circles(16) == 64
                && BowlStation.circles(64) == 64, "sixteen circles a set, a bowlful by the root of its sets");
        Setup one = bowl(helper, AT);
        Player player = cook(helper, one);
        lay(one, recipe, 1);
        stir(one, player, 15.8F);
        helper.assertTrue(one.container().getItem(Bowl.RESULT_SLOT).isEmpty(), "one set: nothing before sixteen circles");
        stir(one, player, 0.3F);
        ItemStack made = one.container().getItem(Bowl.RESULT_SLOT);
        helper.assertTrue(made.is(recipe.result().getItem()) && made.getCount() == recipe.result().getCount(),
                "one set after sixteen circles, got " + made);
        helper.assertTrue(one.bowl.ingredients().stream().allMatch(ItemStack::isEmpty), "its ingredients are used");
        helper.assertTrue(one.station.effects.contains(BowlStation.EFFECT_MIXED), "everybody saw it mixed");

        Setup four = bowl(helper, AT.east(2));
        cook(helper, four);
        lay(four, recipe, 4);
        stir(four, player, 31.8F);
        helper.assertTrue(four.container().getItem(Bowl.RESULT_SLOT).isEmpty(), "four sets: nothing before thirty-two circles");
        stir(four, player, 0.3F);
        made = four.container().getItem(Bowl.RESULT_SLOT);
        helper.assertTrue(made.getCount() == 4 * recipe.result().getCount(), "four sets together after thirty-two circles, got " + made);
        helper.assertTrue(four.bowl.ingredients().stream().allMatch(ItemStack::isEmpty), "all of them used");
        helper.succeed();
    }

    /**
     * Nothing is mixed without the hand, and the result that waits blocks the next bowlful, as the bowl's own rule: stirring then
     * counts nothing until it is taken, and taking it sets the bowl's own count back. A hand that holds the whisk still counts nothing,
     * and one that jumps across the bowl counts no more than a hand moves in a tick.
     */
    public static void nothingIsMixedWithoutTheHand(GameTestHelper helper) {
        if (withoutFarmCharm(helper)) {
            return;
        }
        Dish recipe = several(helper);
        Setup setup = bowl(helper, AT);
        Player player = cook(helper, setup);
        helper.onEachTick(() -> setup.logic.tick(setup.station));
        lay(setup, recipe, 2);
        for (int i = 0; i < 40; i++) {
            reportAt(setup, player, 1.0F, -0.5F);
        }
        Setup jumps = bowl(helper, AT.east(2));
        cook(helper, jumps);
        lay(jumps, recipe, 1);
        int across = (int) (BowlStation.circles(1) * BowlStation.CIRCLE_WAY / (2.0F * BowlStation.WHISK_REACH)) + 2;
        for (int i = 0; i <= across; i++) {
            reportAt(jumps, player, i % 2 == 0 ? -BowlStation.WHISK_REACH : BowlStation.WHISK_REACH, 0.0F);
        }
        helper.assertTrue(jumps.container().getItem(Bowl.RESULT_SLOT).isEmpty(),
                "jumps across the bowl count no more than a hand moves in a tick: " + across + " of them mix nothing");
        jumps.container().clearContent();
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(setup.container().getItem(Bowl.RESULT_SLOT).isEmpty() && setup.bowl.ingredients().get(0).getCount() == 2,
                    "a bowl whose whisk is held still mixes nothing");
            stir(setup, player, BowlStation.circles(2) + 0.1F);
            helper.assertTrue(!setup.container().getItem(Bowl.RESULT_SLOT).isEmpty(), "stirred round, it mixes");
            lay(setup, recipe, 1);
            stir(setup, player, 4.0F);
            helper.assertTrue(setup.bowl.ingredients().get(0).getCount() == 1, "the result that waits blocks the next bowlful");
            setup.container().setItem(Bowl.RESULT_SLOT, ItemStack.EMPTY);
            setup.logic.onTaken(setup.station, player, BowlStation.RESULT_SLOT, recipe.result().copy());
            helper.assertTrue(countIsBack(helper, setup), "taken, the bowl's own count is back at nothing");
            setup.container().clearContent();
            helper.succeed();
        });
    }

    /**
     * The whisk is not tied to a circle: figures of eight through the middle, which go round it as much one way as the other, mix the
     * bowlful as soon as their way is its circles' worth, and not before.
     */
    public static void aFigureOfEightMixesAsCirclesDo(GameTestHelper helper) {
        if (withoutFarmCharm(helper)) {
            return;
        }
        Dish recipe = several(helper);
        Setup setup = bowl(helper, AT);
        Player player = cook(helper, setup);
        lay(setup, recipe, 4);
        float needed = BowlStation.circles(4) * BowlStation.CIRCLE_WAY;
        float[] t = {0.0F};
        reportAt(setup, player, 0.0F, 0.0F);
        whisk(helper, setup, player, eights(t, needed * 0.95F), () -> {
            helper.assertTrue(setup.container().getItem(Bowl.RESULT_SLOT).isEmpty(), "nothing before its circles' way");
            whisk(helper, setup, player, eights(t, needed * 0.1F), () -> {
                ItemStack made = setup.container().getItem(Bowl.RESULT_SLOT);
                helper.assertTrue(made.getCount() == 4 * recipe.result().getCount(), "figures of eight mixed the bowlful, got " + made);
                setup.container().clearContent();
                helper.succeed();
            });
        });
    }

    /**
     * The batter resists the whisk only on the client; the server counts the hand's way alike from the first circle to the last. A steady hand going back and forth across the bowl at nine tenths of the counted pace mixes one set in
     * just the ticks its way takes, however thick the bowlful has become on the way.
     */
    public static void aThickBowlfulCountsAsFastAsAThinOne(GameTestHelper helper) {
        if (withoutFarmCharm(helper)) {
            return;
        }
        Dish recipe = several(helper);
        Setup setup = bowl(helper, AT);
        Player player = cook(helper, setup);
        lay(setup, recipe, 1);
        float needed = BowlStation.circles(1) * BowlStation.CIRCLE_WAY;
        float step = BowlStation.COUNTED_PACE * 0.9F;
        float reach = BowlStation.WHISK_REACH;
        List<float[]> places = new ArrayList<>();
        float x = -reach;
        float side = 1.0F;
        places.add(new float[] {x, 0.0F});
        for (float gone = 0.0F; gone < needed + step; gone += step) {
            if (Math.abs(x + side * step) > reach) {
                side = -side;
            }
            x += side * step;
            places.add(new float[] {x, 0.0F});
        }
        whisk(helper, setup, player, places, () -> {
            ItemStack made = setup.container().getItem(Bowl.RESULT_SLOT);
            helper.assertTrue(made.getCount() == recipe.result().getCount(), "mixed in the ticks its way takes at the counted pace, got " + made);
            setup.container().clearContent();
            helper.succeed();
        });
    }

    /**
     * However the hand jerks or shakes the whisk, and however often it reports, the whisk goes through the bowlful no faster than the
     * pace it is counted at: no further than a steady hand at that pace. A steady hand counts in full. (The client's whisk also slows in
     * a thick batter, {@link BowlStation#pace}: only its look.)
     */
    public static void theWhiskGoesNoFasterThanTheMixtureLets(GameTestHelper helper) {
        float loose = BowlStation.pace(0.0F);
        float thick = BowlStation.pace(1.0F);
        helper.assertTrue(thick < loose * 0.5F, "a finished batter holds the whisk back to under half its pace, " + thick + " of " + loose);
        float reach = BowlStation.WHISK_REACH;
        for (float pace : new float[] {loose, thick}) {
            HandPath shaken = new HandPath(reach);
            shaken.report(0, -reach, 0.0F, pace);
            float way = 0.0F;
            for (int tick = 1; tick <= 20; tick++) {
                way += shaken.report(tick, tick % 2 == 0 ? -reach : reach, 0.0F, pace);
            }
            helper.assertTrue(way <= 20 * pace + 1.0e-3F, "shaken across the bowl every tick, it goes no further than " + 20 * pace
                    + ", went " + way);
            HandPath steady = new HandPath(reach);
            steady.report(0, -reach, 0.0F, pace);
            float went = 0.0F;
            int ticks = (int) (2.0F * reach / pace);
            for (int tick = 1; tick <= ticks; tick++) {
                went += steady.report(tick, -reach + tick * pace, 0.0F, pace);
            }
            helper.assertTrue(Math.abs(went - ticks * pace) < 1.0e-3F, "a steady hand counts in full, " + went + " of " + ticks * pace);
        }
        HandPath often = new HandPath(reach);
        often.report(100, 0.0F, 0.0F, loose);
        float burst = 0.0F;
        for (int i = 0; i < 10; i++) {
            burst += often.report(101, i % 2 == 0 ? reach : -reach, 0.0F, loose);
        }
        helper.assertTrue(burst <= HandPath.SAVED_TICKS * loose + 1.0e-3F,
                "ten reports in one tick go no further than the pace saved up, went " + burst);
        helper.succeed();
    }

    /** The result that waits is taken from the badge: it goes to the cook, the bowl is empty and its own count back at nothing. */
    public static void theResultIsTakenFromTheBadge(GameTestHelper helper) {
        if (withoutFarmCharm(helper)) {
            return;
        }
        Dish recipe = several(helper);
        Setup setup = bowl(helper, AT);
        Player player = cook(helper, setup);
        lay(setup, recipe, 2);
        stir(setup, player, BowlStation.circles(2) + 0.1F);
        ItemStack made = setup.container().getItem(Bowl.RESULT_SLOT).copy();
        helper.assertTrue(!made.isEmpty(), "the bowlful was mixed");
        player.getInventory().clearContent();
        setup.logic.onCustomAction(setup.station, player, BowlStation.ACTION_TAKE, new CompoundTag());
        helper.assertTrue(setup.container().getItem(Bowl.RESULT_SLOT).isEmpty(), "taken, nothing waits in the bowl");
        helper.assertTrue(player.getInventory().countItem(made.getItem()) == made.getCount(), "the cook has all " + made);
        helper.assertTrue(countIsBack(helper, setup), "the bowl's own count is back at nothing");
        helper.assertTrue(setup.station.effects.contains(BowlStation.EFFECT_TAKEN), "everybody saw it taken");
        setup.logic.onCustomAction(setup.station, player, BowlStation.ACTION_TAKE, new CompoundTag());
        helper.assertTrue(player.getInventory().countItem(made.getItem()) == made.getCount(), "an empty bowl gives nothing");
        setup.container().clearContent();
        helper.succeed();
    }

    /**
     * A liquid poured in is drawn back by an empty container of its kind on the cursor, and only by that: another container draws
     * nothing.
     */
    public static void aPouredLiquidIsDrawnBackByItsContainer(GameTestHelper helper) {
        if (withoutFarmCharm(helper)) {
            return;
        }
        ItemStack liquid = ItemStack.EMPTY;
        for (Dish recipe : BowlRecipes.all(helper.getLevel())) {
            for (Ingredient ingredient : recipe.ingredients()) {
                for (ItemStack item : ingredient.getItems()) {
                    if (liquid.isEmpty() && !BowlStation.containerOf(item).isEmpty()) {
                        liquid = item.copyWithCount(1);
                    }
                }
            }
        }
        if (liquid.isEmpty()) {
            helper.succeed();
            return;
        }
        Setup setup = bowl(helper, AT);
        Player player = cook(helper, setup);
        ItemStack container = BowlStation.containerOf(liquid);
        setup.station.state().setItem(SlotRef.of(BowlStation.INGREDIENTS, 0), liquid.copy());
        ItemStack other = container.is(Items.BUCKET) ? new ItemStack(Items.GLASS_BOTTLE) : new ItemStack(Items.BUCKET);
        player.inventoryMenu.setCarried(other.copy());
        setup.logic.onCustomAction(setup.station, player, BowlStation.ACTION_DRAW, new CompoundTag());
        helper.assertTrue(ItemStack.isSameItem(setup.container().getItem(0), liquid), "another container draws nothing back");
        player.inventoryMenu.setCarried(container.copy());
        setup.logic.onCustomAction(setup.station, player, BowlStation.ACTION_DRAW, new CompoundTag());
        helper.assertTrue(setup.container().getItem(0).isEmpty(), "its container drew the " + liquid + " back");
        helper.assertTrue(ItemStack.isSameItem(player.inventoryMenu.getCarried(), liquid), "it is on the cursor, got "
                + player.inventoryMenu.getCarried());
        helper.assertTrue(setup.station.effects.contains(BowlStation.EFFECT_DRAWN), "everybody saw it drawn");
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        setup.container().clearContent();
        helper.succeed();
    }

    /** What an ingredient leaves (a milk bucket's bucket) goes to the cook, never beside the bowl. */
    public static void whatTheBowlsIngredientsLeaveGoesToTheCook(GameTestHelper helper) {
        if (withoutFarmCharm(helper)) {
            return;
        }
        Dish recipe = BowlRecipes.all(helper.getLevel()).stream().filter(dish -> dish.ingredients().size() == 1
                && dish.ingredients().get(0).getItems().length > 0 && dish.ingredients().get(0).getItems()[0].getItem().hasCraftingRemainingItem())
                .findFirst().orElse(null);
        if (recipe == null) {
            helper.succeed();
            return;
        }
        Setup setup = bowl(helper, AT);
        Player player = cook(helper, setup);
        ItemStack ingredient = recipe.ingredients().get(0).getItems()[0];
        lay(setup, recipe, 1);
        stir(setup, player, BowlStation.circles(1) + 0.1F);
        ItemStack left = new ItemStack(ingredient.getItem().getCraftingRemainingItem());
        helper.assertTrue(!setup.container().getItem(Bowl.RESULT_SLOT).isEmpty(), recipe.id() + " was mixed");
        helper.assertTrue(player.getInventory().countItem(left.getItem()) == 1, "the cook has the " + left + " it left");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(setup.station.pos()).inflate(2.0)).isEmpty(),
                "nothing lies beside the bowl");
        setup.container().clearContent();
        helper.succeed();
    }

    /** Every recipe of the bowl, picked in the book: its ingredients go in from the inventory and its circles mix them. */
    public static void everythingTheBowlMakesIsLaidFromTheBook(GameTestHelper helper) {
        if (withoutFarmCharm(helper)) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        for (Dish recipe : BowlRecipes.all(helper.getLevel())) {
            if (!Cookable.all(recipe.ingredients())) {
                continue;
            }
            helper.setBlock(AT, Blocks.AIR);
            Setup setup = bowl(helper, AT);
            setup.station.users.add(player);
            setup.logic.onOpen(setup.station, player);
            player.getInventory().clearContent();
            for (Ingredient ingredient : recipe.ingredients()) {
                player.getInventory().add(ingredient.getItems()[0].copyWithCount(1));
            }
            CompoundTag fill = new CompoundTag();
            fill.putString(BowlStation.RECIPE, recipe.id().toString());
            setup.logic.onCustomAction(setup.station, player, BowlStation.ACTION_FILL, fill);
            long laid = setup.bowl.ingredients().stream().filter(stack -> !stack.isEmpty()).count();
            helper.assertTrue(laid == recipe.ingredients().size(), recipe.id() + ": its ingredients lie in the bowl, got " + setup.bowl.ingredients());
            Dish read = BowlRecipes.reads(BowlRecipes.all(helper.getLevel()), setup.bowl.ingredients());
            if (read == null || !read.id().equals(recipe.id())) {
                // Another recipe reads the same ingredients first, as the bowl itself looks them up.
                setup.container().clearContent();
                continue;
            }
            stir(setup, player, BowlStation.circles(1) + 0.1F);
            ItemStack made = setup.container().getItem(Bowl.RESULT_SLOT);
            helper.assertTrue(made.is(recipe.result().getItem()) && made.getCount() == recipe.result().getCount(),
                    recipe.id() + ": its circles make it, got " + made);
            setup.container().clearContent();
        }
        helper.setBlock(AT, Blocks.AIR);
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(AT)).inflate(3.0)).forEach(ItemEntity::discard);
        helper.succeed();
    }

    /** The bowl's scene is one the library reads, with every part the station shows things on. */
    public static void theBowlsSceneIsRead(GameTestHelper helper) {
        StationTests.sceneIsRead(helper, "crafting_bowl", List.of("bowl", "whisk_pivot", "whisk", "dough", "mixture", "mouth", "item_0", "item_1",
                "item_2", "item_3", "badge", "dial_track", "dial", "preview", "book_open"), BowlStation.SCHEMA,
                List.of(BowlStation.INGREDIENTS));
        helper.succeed();
    }
}
