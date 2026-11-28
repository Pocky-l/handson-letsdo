package com.pockyl.handson_letsdo.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.handson_letsdo.HandsOnConfig;
import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.station.BoardLogic;
import com.pockyl.handson_letsdo.station.CuttingBoardStation;
import com.pockyl.handson_letsdo.station.FarmCharmBoard;
import com.pockyl.handson_letsdo.station.FarmCharmDishes;

import java.util.List;

/**
 * The board's rules against a real cutting board of Farm &amp; Charm (1.21.1): food is cut in batches of sixteen after the strokes the
 * board counts for the tool, logs stripped by an axe, dishes made as many times as their layers allow, and the board's own use keeps
 * every round of a batch. They pass at once without Farm &amp; Charm or its board.
 */
public final class BoardTests {
    private static final BlockPos AT = new BlockPos(1, 2, 1);

    private BoardTests() {
    }

    private static Item farmCharm(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(FarmCharmDishes.MOD_ID, path));
    }

    private record Setup(TestStation station, BoardLogic logic, FarmCharmBoard board, Player cook) {
    }

    private static boolean withoutBoard(GameTestHelper helper) {
        if (HandsOnLetsDo.has(FarmCharmDishes.MOD_ID) && Diegesis.stationType(CuttingBoardStation.ID).isPresent()) {
            return false;
        }
        helper.succeed();
        return true;
    }

    private static Setup board(GameTestHelper helper) {
        helper.setBlock(AT.below(), Blocks.STONE);
        helper.setBlock(AT, CuttingBoardStation.CUTTING_BOARD.get().defaultBlockState());
        BlockPos pos = helper.absolutePos(AT);
        TestStation station = new TestStation(helper.getLevel(), pos, CuttingBoardStation.TYPE);
        FarmCharmBoard board = FarmCharmBoard.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(board != null, "Farm & Charm's board is the board the station was made for, its list reached by the accessor");
        BoardLogic logic = new BoardLogic(station);
        Player cook = helper.makeMockPlayer(GameType.SURVIVAL);
        cook.getInventory().clearContent();
        station.users.add(cook);
        logic.onOpen(station, cook);
        return new Setup(station, logic, board, cook);
    }

    private static void place(Setup setup, ItemStack stack) {
        setup.cook.inventoryMenu.setCarried(stack);
        setup.logic.onCustomAction(setup.station, setup.cook, CuttingBoardStation.ACTION_PLACE, new CompoundTag());
    }

    /** Every stroke the next mark asks for, each a few ticks after the last as a hand makes them. */
    private static void chopAll(GameTestHelper helper, Setup setup, int strokes, Runnable then) {
        chopFrom(helper, setup, 0, strokes, then);
    }

    private static void chopFrom(GameTestHelper helper, Setup setup, int mark, int strokes, Runnable then) {
        if (mark == strokes) {
            then.run();
            return;
        }
        CompoundTag at = new CompoundTag();
        at.putInt(CuttingBoardStation.MARK, mark);
        at.putFloat(CuttingBoardStation.AT, 8.0F);
        setup.logic.onCustomAction(setup.station, setup.cook, CuttingBoardStation.ACTION_CHOP, at);
        helper.runAfterDelay(4, () -> chopFrom(helper, setup, mark + 1, strokes, then));
    }

    private static int count(Player player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** The board is a station exactly when Farm &amp; Charm brings it, and then its block opens it. */
    public static void theBoardIsAStationOnlyWithFarmCharm(GameTestHelper helper) {
        boolean installed = HandsOnLetsDo.has(FarmCharmDishes.MOD_ID) && HandsOnConfig.cuttingBoard();
        if (!installed) {
            helper.assertTrue(Diegesis.stationType(CuttingBoardStation.ID).isEmpty(), "no board station without Farm & Charm");
            helper.succeed();
            return;
        }
        helper.assertTrue(Diegesis.stationType(CuttingBoardStation.ID).isPresent(), "Farm & Charm's board is a station");
        Setup setup = board(helper);
        helper.assertTrue(Diegesis.stationFor(helper.getLevel().getBlockState(setup.station.pos())).filter(type -> type == CuttingBoardStation.TYPE)
                .isPresent(), "the board opens its station");
        helper.succeed();
    }

    /**
     * Twenty beef laid with a cleaver on the cursor: after the strokes the board counts for the cleaver, sixteen are cut into minced
     * beef that goes to the cook, the cleaver worn once for each, and four still lie there with new marks.
     */
    public static void aBatchOfSixteenIsCutAfterTheToolsStrokes(GameTestHelper helper) {
        if (withoutBoard(helper)) {
            return;
        }
        Setup setup = board(helper);
        place(setup, new ItemStack(Items.BEEF, 20));
        helper.assertTrue(setup.board.items().size() == 1 && setup.board.items().get(0).getCount() == 20, "the twenty beef lie on the board as one stack");
        ItemStack cleaver = new ItemStack(farmCharm("iron_cleaver"));
        setup.cook.inventoryMenu.setCarried(cleaver);
        int strokes = Math.clamp(FarmCharmBoard.chops(helper.getLevel(), new ItemStack(Items.BEEF), cleaver), 1, BoardLogic.MOST_MARKS);
        // The marks are laid for the tool on the cursor at the next tick.
        setup.logic.tick(setup.station);
        chopAll(helper, setup, strokes, () -> {
            helper.assertTrue(count(setup.cook, farmCharm("minced_beef")) == 16, "sixteen minced beef went to the cook, got "
                    + count(setup.cook, farmCharm("minced_beef")));
            helper.assertTrue(setup.board.items().size() == 1 && setup.board.items().get(0).getCount() == 4, "four beef still lie there");
            ItemStack worn = setup.cook.inventoryMenu.getCarried();
            helper.assertTrue(worn.is(cleaver.getItem()) && worn.getDamageValue() == 16, "the cleaver was worn once for each cut, "
                    + worn.getDamageValue());
            helper.succeed();
        });
    }

    /** One stroke short of the count cuts nothing: the batch waits for the last mark. */
    public static void nothingIsCutBeforeTheLastStroke(GameTestHelper helper) {
        if (withoutBoard(helper)) {
            return;
        }
        Setup setup = board(helper);
        place(setup, new ItemStack(Items.BEEF, 4));
        ItemStack knife = new ItemStack(farmCharm("iron_cleaver"));
        setup.cook.inventoryMenu.setCarried(knife);
        setup.logic.tick(setup.station);
        int strokes = Math.clamp(FarmCharmBoard.chops(helper.getLevel(), new ItemStack(Items.BEEF), knife), 1, BoardLogic.MOST_MARKS);
        if (strokes < 2) {
            helper.succeed();
            return;
        }
        chopAll(helper, setup, strokes - 1, () -> {
            helper.assertTrue(count(setup.cook, farmCharm("minced_beef")) == 0, "nothing was cut yet");
            helper.assertTrue(setup.board.items().get(0).getCount() == 4, "the beef still lies there whole");
            helper.succeed();
        });
    }

    /** A log laid with an axe in the inventory and an empty hand: two strokes strip it, the stripped log goes to the cook. */
    public static void anAxeStripsALog(GameTestHelper helper) {
        if (withoutBoard(helper)) {
            return;
        }
        Setup setup = board(helper);
        place(setup, new ItemStack(Items.OAK_LOG, 3));
        helper.assertTrue(setup.board.items().size() == 1, "the logs lie on the board to be stripped");
        setup.cook.getInventory().setItem(0, new ItemStack(Items.IRON_AXE));
        setup.cook.inventoryMenu.setCarried(ItemStack.EMPTY);
        setup.logic.tick(setup.station);
        chopAll(helper, setup, FarmCharmBoard.STRIP_CHOPS, () -> {
            helper.assertTrue(count(setup.cook, Items.STRIPPED_OAK_LOG) == 3, "the three logs were stripped");
            helper.assertTrue(setup.board.items().isEmpty(), "nothing is left on the board");
            helper.succeed();
        });
    }

    /** Three layers of wheat, a batch each: they make the chicken nest by themselves, as many times as they lie, to the cook. */
    public static void layersMakeTheirDishRoundAfterRound(GameTestHelper helper) {
        if (withoutBoard(helper)) {
            return;
        }
        Setup setup = board(helper);
        for (int i = 0; i < 3; i++) {
            place(setup, new ItemStack(Items.WHEAT, 5));
        }
        Item nest = farmCharm("chicken_nest");
        helper.assertTrue(count(setup.cook, nest) == 10, "five rounds of two nests each went to the cook, got " + count(setup.cook, nest));
        helper.assertTrue(setup.board.items().isEmpty(), "the layers were used up");
        helper.succeed();
    }

    /** Rope is laid in its order: barley first is refused, string, barley, string makes it. */
    public static void anOrderedDishTakesItsOrder(GameTestHelper helper) {
        if (withoutBoard(helper)) {
            return;
        }
        Setup setup = board(helper);
        place(setup, new ItemStack(farmCharm("barley"), 2));
        helper.assertTrue(setup.board.items().isEmpty(), "barley begins no dish: it stays in the hand");
        setup.cook.inventoryMenu.setCarried(ItemStack.EMPTY);
        place(setup, new ItemStack(Items.STRING, 2));
        place(setup, new ItemStack(farmCharm("barley"), 2));
        place(setup, new ItemStack(Items.STRING, 2));
        helper.assertTrue(count(setup.cook, farmCharm("rope")) == 12, "two rounds of six rope, got " + count(setup.cook, farmCharm("rope")));
        helper.succeed();
    }

    /** The board's own empty hand on a batch (a sneaking cook): every round of the dish is made, nothing is wiped away. */
    public static void theBoardsOwnUseMakesEveryRound(GameTestHelper helper) {
        if (withoutBoard(helper)) {
            return;
        }
        Setup setup = board(helper);
        setup.board.setItems(List.of(new ItemStack(Items.WHEAT, 4), new ItemStack(Items.CARROT, 4), new ItemStack(Items.WHEAT_SEEDS, 4)));
        if (FarmCharmBoard.dish(helper.getLevel(), setup.board.items()).isEmpty()) {
            helper.fail("wheat, carrot and seeds make chicken feed");
            return;
        }
        BlockPos pos = setup.station.pos();
        Player sneaking = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), sneaking,
                new BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
        helper.assertTrue(setup.board.items().isEmpty(), "the board was used up");
        helper.runAfterDelay(2, () -> {
            int feed = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(3.0)).stream()
                    .filter(entity -> entity.getItem().is(farmCharm("chicken_feed"))).mapToInt(entity -> entity.getItem().getCount()).sum();
            helper.assertTrue(feed == 12, "four rounds of three chicken feed came off the board, got " + feed);
            helper.succeed();
        });
    }

    /**
     * Corn Expansion's dishes are laid on the board as its recipes say: polenta and butter make buttery polenta; four popcorn, each a
     * layer of its own, and paper to finish make a tin; minced beef, an egg and cornmeal finished with a stick make a raw corn dog.
     */
    public static void cornExpansionsDishesAreLaidOnTheBoard(GameTestHelper helper) {
        if (!HandsOnLetsDo.has("cornexpansion")) {
            helper.succeed();
            return;
        }
        if (withoutBoard(helper)) {
            return;
        }
        record Laid(String dish, List<ItemStack> layers) {
        }
        List<Laid> dishes = List.of(
                new Laid("buttery_polenta", List.of(new ItemStack(corn("polenta")), new ItemStack(farmCharm("butter")))),
                new Laid("popcorn_tin", List.of(new ItemStack(corn("popcorn")), new ItemStack(corn("popcorn")), new ItemStack(corn("popcorn")),
                        new ItemStack(corn("popcorn")), new ItemStack(Items.PAPER))),
                new Laid("raw_corn_dog", List.of(new ItemStack(farmCharm("minced_beef")), new ItemStack(Items.EGG), new ItemStack(corn("cornmeal")),
                        new ItemStack(Items.STICK))));
        for (Laid laid : dishes) {
            helper.setBlock(AT, Blocks.AIR);
            Setup setup = board(helper);
            for (ItemStack layer : laid.layers()) {
                place(setup, layer.copy());
                setup.cook.inventoryMenu.setCarried(ItemStack.EMPTY);
            }
            Item dish = corn(laid.dish());
            helper.assertTrue(count(setup.cook, dish) == 1, laid.dish() + " is made and goes to the cook, got " + count(setup.cook, dish)
                    + ", the board holds " + setup.board.items());
            helper.assertTrue(setup.board.items().isEmpty(), laid.dish() + ": the layers were used up");
        }
        helper.setBlock(AT, Blocks.AIR);
        helper.succeed();
    }

    private static Item corn(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cornexpansion", path));
    }

    /** A cleaver on the cursor stuck into the bare board stays there, and an empty-hand take pulls it out to the cook. */
    public static void aToolIsStuckInAndPulledOut(GameTestHelper helper) {
        if (withoutBoard(helper)) {
            return;
        }
        Setup setup = board(helper);
        setup.cook.inventoryMenu.setCarried(new ItemStack(farmCharm("iron_cleaver")));
        setup.logic.onCustomAction(setup.station, setup.cook, CuttingBoardStation.ACTION_CARVE, new CompoundTag());
        helper.assertTrue(!setup.board.knife().isEmpty() && setup.cook.inventoryMenu.getCarried().isEmpty(), "the cleaver is stuck in");
        setup.logic.onCustomAction(setup.station, setup.cook, CuttingBoardStation.ACTION_TAKE, new CompoundTag());
        helper.assertTrue(setup.board.knife().isEmpty() && count(setup.cook, farmCharm("iron_cleaver")) == 1, "it came out to the cook");
        helper.succeed();
    }

    public static void theBoardsSceneIsRead(GameTestHelper helper) {
        StationTests.sceneIsRead(helper, "cutting_board", List.of("mouth", "surface", "work", "item", "item_block", "marks", "stuck",
                "knife", "tool", "badge", "dial_track", "dial", "preview", "book_open"), CuttingBoardStation.SCHEMA, List.of());
        helper.succeed();
    }
}
