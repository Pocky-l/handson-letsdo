package com.pockyl.handson_letsdo.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.state.TankSpec;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_letsdo.HandsOnConfig;
import com.pockyl.handson_letsdo.HandsOnLetsDo;

import java.util.function.Supplier;

/**
 * Farm &amp; Charm's crafting bowl as a station: the cook throws the ingredients in, takes the bowl's own whisk by its handle and
 * whisks them, round, to and fro or in figures of eight, and once the whisk has gone its way through the bowlful the whole of it
 * becomes its result at once. The result waits in the bowl, shown on the badge beside it, until it is taken from there. Every set
 * is made by the bowl's own tick. The scene is the bowl in the world too, wherever it is drawn; it keeps the world's sides as the
 * bowl and its whisk have them, whichever side the cook comes from.
 */
public final class BowlStation {
    public static final ResourceLocation ID = HandsOnLetsDo.id("crafting_bowl");

    /** The four ingredients, the bowl's own slots 0 to 3. */
    public static final String INGREDIENTS = "ingredients";
    /** What the bowlful became, the bowl's own result slot, waiting in the bowl: shown on the badge and taken from there. */
    public static final String RESULT = "result";
    public static final SlotRef RESULT_SLOT = SlotRef.of(RESULT, 0);

    /**
     * Whisk circles for one set; a bowlful takes this times the root of its sets, at most of sixteen. One set takes about 4.3 s at the
     * fastest a hand whisks, a full bowl of dough some 15 s, sixteen sets 17 s: real work at the bowl, long enough to see it come
     * together from loose to lumpy to smooth.
     */
    public static final float CIRCLES_ONE = 16.0F;
    public static final int SETS_COUNTED = 16;
    /**
     * How fast the whisk's loops go through the mixture at most on the client, pixels a tick: loose, as it was put in, and as a finished
     * batter, which holds them back. Only the look and feel of the whisk: the server counts the hand's way at {@link #COUNTED_PACE}
     * however thick the bowlful is, so that the last circles are no slower to make than the first.
     */
    public static final float PACE_LOOSE = 2.6F;
    public static final float PACE_THICK = 1.25F;
    /** The most of the hand's way the server counts, pixels a tick: the loose pace, from the first circle to the last. */
    public static final float COUNTED_PACE = PACE_LOOSE;
    /**
     * The way the whisk's loops go through the bowl that counts as one circle, pixels: a circle round the middle between the walls.
     * Any figure counts by its way, round, to and fro or in eights.
     */
    public static final float CIRCLE_WAY = 14.0F;
    /** How far from the middle the whisk's loops reach, either way, pixels: the walls stop them there. */
    public static final float WHISK_REACH = 2.6F;
    /** The client's liquid of the mixture, filled by the scene alone: the bowl keeps no liquid of its own. */
    public static final String MIXTURE = "mixture";

    /**
     * What the logic tells the clients in the station's data: how far the bowlful is stirred, 0 to 1; the circles it takes; its sets;
     * the item it becomes, absent for nothing; where the whisk's loops are as the cook stirs, pixels from the middle in the bowl's
     * own sides.
     */
    public static final String STIRRED = "stirred";
    public static final String CIRCLES = "circles";
    public static final String SETS = "sets";
    public static final String MAKING = "making";
    public static final String WHISK_X = "whisk_x";
    public static final String WHISK_Z = "whisk_z";

    /** A custom action: the whisk moved; data: where its loops are now, {@link #X} and {@link #Z}, pixels from the bowl's middle. */
    public static final String ACTION_STIR = "stir";
    public static final String X = "x";
    public static final String Z = "z";
    /** A custom action: the result that waits goes to the cook, taken from the badge. */
    public static final String ACTION_TAKE = "take";
    /**
     * A custom action: an empty container (on the cursor, or in inventory slot {@link #FROM}) draws back a liquid poured into the
     * bowl that came in such a container, one or with {@link #ALL} as many as there are containers.
     */
    public static final String ACTION_DRAW = "draw";
    public static final String FROM = "from";
    /**
     * A custom action: something picked in the book goes into the bowl from the inventory, one set or with {@link #ALL} as many as
     * fit. Data: {@link #RECIPE}, the id of its recipe.
     */
    public static final String ACTION_FILL = "fill";
    public static final String RECIPE = "recipe";
    public static final String ALL = "all";

    /** Played for everybody who sees it: the bowlful is mixed. Data: {@link #ITEM}, what it became. */
    public static final ResourceLocation EFFECT_MIXED = HandsOnLetsDo.id("bowl_mixed");
    public static final String ITEM = "item";
    /** Played for everybody who sees it: the result was taken out of the bowl. Data: {@link #ITEM}. */
    public static final ResourceLocation EFFECT_TAKEN = HandsOnLetsDo.id("bowl_taken");
    /** Played for everybody who sees it: a poured liquid was drawn back. Data: {@link #ITEM}, the liquid in its container. */
    public static final ResourceLocation EFFECT_DRAWN = HandsOnLetsDo.id("bowl_drawn");

    @SuppressWarnings("removal")
    public static final Supplier<Block> BOWL = () -> BuiltInRegistries.BLOCK.get(new ResourceLocation(FarmCharmDishes.MOD_ID, "crafting_bowl"));

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(INGREDIENTS, Bowl.INGREDIENTS, SlotRules.DEFAULT.kept())
            .slots(RESULT, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .tank(MIXTURE, TankSpec.of(1))
            .build();

    /** The bowl has no front: the scene faces the cook, and turns the bowl back to the world's sides. */
    public static final StationType TYPE = Diegesis.station(ID)
            .block(BOWL)
            .schema(SCHEMA)
            .logic(BowlLogic::new)
            .orientation(StationType.Orientation.PLAYER)
            .openWhen(BowlStation::opens)
            .hidesBlock()
            .idleLogic()
            .build();

    private BowlStation() {
    }

    /** With Farm &amp; Charm installed and the station left on in the config; otherwise the bowl is stirred Farm &amp; Charm's way. */
    public static void register() {
        if (HandsOnLetsDo.has(FarmCharmDishes.MOD_ID) && HandsOnConfig.craftingBowl()) {
            Diegesis.register(TYPE);
        }
    }

    /** Whether the cook's own use reaches the bowl: sneaking empties it Farm &amp; Charm's way; a bowl this mod does not know too. */
    private static boolean opens(Player player, BlockPos pos, BlockState state) {
        return !player.isSecondaryUseActive() && Bowl.of(player.level().getBlockEntity(pos)) != null;
    }

    /** Whether {@code entity} is a bowl the station knows: its block entity renderer draws what the scene draws. */
    public static boolean isBowl(BlockEntity entity) {
        return entity instanceof BowlHand && Bowl.isBowlBlock(entity.getBlockState().getBlock());
    }

    /**
     * The container a liquid ingredient came in and is drawn back with: what the item leaves when it is used, a glass bottle for a
     * potion (a water bottle); empty for anything that came in none.
     */
    public static ItemStack containerOf(ItemStack stack) {
        Item left = stack.getItem().getCraftingRemainingItem();
        if (left != null && left != Items.AIR) {
            return new ItemStack(left);
        }
        return stack.getItem() instanceof PotionItem ? new ItemStack(Items.GLASS_BOTTLE) : ItemStack.EMPTY;
    }

    /**
     * How fast the client's whisk goes through a bowlful stirred this far, 0 to 1, pixels a tick: the mixture thickens as it comes
     * together. The server does not use it.
     */
    public static float pace(float stirred) {
        float t = Mth.clamp(stirred, 0.0F, 1.0F);
        return Mth.lerp(t * t * (3.0F - 2.0F * t), PACE_LOOSE, PACE_THICK);
    }

    /** The whisk circles a bowlful of {@code sets} takes. */
    public static int circles(int sets) {
        return Mth.ceil(CIRCLES_ONE * Math.sqrt(Math.max(1, Math.min(sets, SETS_COUNTED))) - 1.0e-4F);
    }
}
