package com.pockyl.handson_letsdo.station;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.satisfy.wok_and_bowl.core.block.WokBlock;
import net.satisfy.wok_and_bowl.core.block.entity.WokBlockEntity;
import net.satisfy.wok_and_bowl.core.recipe.WokRecipe;

import java.util.List;
import java.util.UUID;

/**
 * Wok &amp; Bowl's wok (1.21.1), as the station sees it: every step is the wok's own (what it takes, the toss, the sauce, the flambé,
 * serving), the station only hands it what the cook does. Only loaded with Wok &amp; Bowl.
 */
public final class WokAndBowlWok {
    public static final String MOD_ID = "wok_and_bowl";
    public static final int MOST_INGREDIENTS = WokBlockEntity.MAX_INGREDIENTS;
    public static final int DISHES = WokBlockEntity.MAX_DISHES;
    private static final TagKey<Item> SAUCE = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "fermented_sauce"));
    private static final TagKey<Item> REJECTS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "wok_rejects"));
    private static final ResourceLocation WOK_TYPE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "wok");

    private final WokBlockEntity wok;

    private WokAndBowlWok(WokBlockEntity wok) {
        this.wok = wok;
    }

    /** The wok of a block entity, or null for anything else. */
    public static WokAndBowlWok of(BlockEntity entity) {
        return entity instanceof WokBlockEntity wok ? new WokAndBowlWok(wok) : null;
    }

    /** Whether the bamboo steamer stands on it: then it steams, and is used by hand as Wok &amp; Bowl has it. */
    public static boolean hasSteamer(BlockState state) {
        return state.hasProperty(WokBlock.HAS_STEAMER) && state.getValue(WokBlock.HAS_STEAMER);
    }

    public boolean hasSteamer() {
        return hasSteamer(wok.getBlockState());
    }

    /**
     * One of {@code stack} goes in as the wok takes it: an ingredient, the sauce, or a flambé with flint and steel. The wok takes it
     * from the stack itself. False when it takes nothing (cold, not its cook, not in a recipe's order, full, sauced).
     */
    public boolean add(ItemStack stack, Player player) {
        return wok.addIngredient(stack, player).consumesAction();
    }

    /** The wok's empty hand: dump burnt rice, toss when called, serve when ready, else a dish out. */
    public boolean emptyHand(Player player) {
        return wok.useEmptyHand(player) == InteractionResult.CONSUME;
    }

    public List<ItemStack> ingredients() {
        return wok.getIngredients();
    }

    public List<ItemStack> dishes() {
        return wok.getDishes();
    }

    public boolean isCooking() {
        return wok.isCooking();
    }

    public boolean hasDishes() {
        return wok.hasDishes();
    }

    public boolean isAwaitingToss() {
        return wok.isAwaitingToss();
    }

    public boolean isSauced() {
        return wok.isSauced();
    }

    public boolean isFlambeed() {
        return wok.isFlambeed();
    }

    public boolean hasBurntRice() {
        return wok.hasBurntRice();
    }

    /** Whether there is heat under it: on the server the wok's own look, on a client what the server told it. */
    public boolean isHeated() {
        Level level = wok.getLevel();
        return level != null && level.isClientSide() ? wok.isHeatedForDisplay() : wok.isHeated();
    }

    public int timer() {
        return wok.getTimer();
    }

    public int cookingTicks() {
        return wok.getCookingTicks();
    }

    /** When the dish is ready to be served: on a client what the server told it, on the server worked out. */
    public int readyTicks() {
        Level level = wok.getLevel();
        return level != null && level.isClientSide() ? wok.getReadyTime() : Math.max(0, wok.getCookingTicks() - 40);
    }

    public static int tossWindow() {
        return WokBlockEntity.getTossWindow();
    }

    public long lastToss() {
        return wok.getLastToss();
    }

    public long tossCalled() {
        return wok.getTossCalled();
    }

    public long lastAdded() {
        return wok.getLastAdded();
    }

    public long lastDump() {
        return wok.getLastDump();
    }

    public long lastEmptied() {
        return wok.getLastEmptied();
    }

    public UUID owner() {
        return wok.getOwner();
    }

    /** What the ingredients so far make: the recipe's dish, the mixed stir-fry when improvised, empty for nothing yet. */
    public ItemStack makes() {
        return wok.isCooking() ? wok.getCurrentResult() : ItemStack.EMPTY;
    }

    /** Whether it may still be served: cooking and not sauced, with a dish to make. */
    public boolean canFinish() {
        return wok.canFinish();
    }

    /**
     * How many flames the dish has now, 1 to 3, as the wok counts them when it serves: tosses missed and two late ones each cost
     * one, the flambé adds one. Serving off time costs one more then.
     */
    public int flames(HolderLookup.Provider registries) {
        CompoundTag saved = wok.saveWithoutMetadata(registries);
        int flames = 3 - saved.getInt("MissedTosses") - saved.getInt("LateTosses") / 2 + (wok.isFlambeed() ? 1 : 0);
        return Math.max(1, Math.min(3, flames));
    }

    /** How many dishes a serving of {@code flames} gives, by Wok &amp; Bowl's config: more over soul fire. */
    public int dishCount(int flames) {
        Level level = wok.getLevel();
        boolean soul = level != null && WokBlock.isSoulFire(level, wok.getBlockPos());
        return net.satisfy.wok_and_bowl.platform.PlatformHelper.getWokDishCount(flames, soul);
    }

    /** Whether the dish's flames count at all (Wok &amp; Bowl's quality setting). */
    public static boolean hasQuality() {
        return net.satisfy.wok_and_bowl.platform.PlatformHelper.isWokQualityEnabled();
    }

    public static boolean isSauce(ItemStack stack) {
        return stack.is(SAUCE);
    }

    public static boolean isFlint(ItemStack stack) {
        return stack.is(Items.FLINT_AND_STEEL);
    }

    public static boolean isRejected(ItemStack stack) {
        return stack.is(REJECTS);
    }

    /** The wok's recipes as the recipe manager has them now, each its ingredients in order. */
    @SuppressWarnings("unchecked")
    public static List<RecipeHolder<WokRecipe>> recipes(Level level) {
        RecipeType<WokRecipe> type = (RecipeType<WokRecipe>) BuiltInRegistries.RECIPE_TYPE.get(WOK_TYPE);
        return type == null ? List.of() : level.getRecipeManager().getAllRecipesFor(type);
    }

    public static List<Ingredient> ingredients(WokRecipe recipe) {
        return recipe.getIngredients();
    }

    public static ItemStack result(WokRecipe recipe) {
        return recipe.getResult();
    }

    /** Whether {@code stack} could go into the wok as it is now: in some recipe at the next place, or any food while cooking. */
    public boolean takes(Level level, ItemStack stack) {
        if (stack.isEmpty() || isRejected(stack)) {
            return false;
        }
        if (isSauce(stack) || isFlint(stack)) {
            return wok.isCooking();
        }
        int next = wok.getIngredients().size();
        if (next >= MOST_INGREDIENTS || wok.isSauced()) {
            return false;
        }
        if (wok.isCooking() && stack.has(net.minecraft.core.component.DataComponents.FOOD)) {
            return true;
        }
        for (RecipeHolder<WokRecipe> recipe : recipes(level)) {
            List<Ingredient> wanted = recipe.value().getIngredients();
            if (wanted.size() > next && recipe.value().startsWith(wok.getIngredients()) && wanted.get(next).test(stack)) {
                return true;
            }
        }
        return false;
    }
}
