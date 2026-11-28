package com.pockyl.handson_letsdo.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.satisfy.farm_and_charm.core.block.CuttingBoardBlock;
import net.satisfy.farm_and_charm.core.block.entity.CuttingBoardBlockEntity;
import net.satisfy.farm_and_charm.core.recipe.CuttingBoardAssemblyRecipe;
import net.satisfy.farm_and_charm.core.recipe.CuttingBoardRecipe;
import net.satisfy.farm_and_charm.core.util.Strippables;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Farm &amp; Charm's cutting board (1.21.1), as the station sees it: what lies on it (a list, bottom first), the tool stuck into it and
 * what that tool is cutting, and the board's recipes, read from the live recipe manager every time. Only loaded with Farm &amp; Charm.
 */
public final class FarmCharmBoard {
    /** As many things as the board holds. */
    public static final int MOST = CuttingBoardBlockEntity.MAX_ITEMS;
    /** How many strokes an axe takes to strip a log, as the board's own stuck axe does. */
    public static final int STRIP_CHOPS = Strippables.CHOPS;
    /** The experience of a stripped log, as the board gives it. */
    public static final float STRIP_EXPERIENCE = 0.05F;
    private static final ResourceLocation CUT_TYPE = ResourceLocation.fromNamespaceAndPath(FarmCharmDishes.MOD_ID, "cutting_board");
    private static final ResourceLocation ASSEMBLY_TYPE = ResourceLocation.fromNamespaceAndPath(FarmCharmDishes.MOD_ID,
            "cutting_board_assembly");

    private final CuttingBoardBlockEntity board;

    private FarmCharmBoard(CuttingBoardBlockEntity board) {
        this.board = board;
    }

    /** The board of a block entity, or null for anything else (a build without the list the station writes). */
    public static FarmCharmBoard of(BlockEntity entity) {
        return entity instanceof CuttingBoardBlockEntity board && entity instanceof BoardItems ? new FarmCharmBoard(board) : null;
    }

    public BlockEntity blockEntity() {
        return board;
    }

    /** What lies on the board, bottom first: the board's own list. Written through {@link #setItems}. */
    public List<ItemStack> items() {
        return board.getItems();
    }

    /** What lies on the board from now on, bottom first; empty stacks are left out. Saved and sent to the clients. */
    public void setItems(List<ItemStack> items) {
        List<ItemStack> list = ((BoardItems) board).handson$items();
        list.clear();
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && list.size() < MOST) {
                list.add(stack);
            }
        }
        ((BoardItems) board).handson$markUpdated();
    }

    /** Tells the clients after a stack lying there changed in place (a cut took some of it). */
    public void changed() {
        ((BoardItems) board).handson$markUpdated();
    }

    /** The tool stuck into the board, empty for none. */
    public ItemStack knife() {
        return board.getKnife();
    }

    public void setKnife(ItemStack knife) {
        board.setKnife(knife);
    }

    /** What the stuck tool is cutting, the board's own way, empty for nothing. */
    public ItemStack pending() {
        return board.getPending();
    }

    public void clearPending() {
        board.startCut(ItemStack.EMPTY);
    }

    /** A knife the board cuts with: Farm &amp; Charm's cleavers and every knife of the common tag. */
    public static boolean isKnife(ItemStack stack) {
        return !stack.isEmpty() && stack.is(CuttingBoardBlock.KNIVES);
    }

    /** An axe the board strips logs with: any axe but a cleaver. */
    public static boolean isAxe(ItemStack stack) {
        return !stack.isEmpty() && Strippables.isAxe(stack);
    }

    @SuppressWarnings("unchecked")
    private static <T extends net.minecraft.world.item.crafting.Recipe<?>> RecipeType<T> type(ResourceLocation id) {
        return (RecipeType<T>) BuiltInRegistries.RECIPE_TYPE.get(id);
    }

    /** Every cut of the board, as the recipe manager has them now. */
    public static List<RecipeHolder<CuttingBoardRecipe>> cuts(Level level) {
        RecipeType<CuttingBoardRecipe> type = type(CUT_TYPE);
        return type == null ? List.of() : level.getRecipeManager().getAllRecipesFor(type);
    }

    /** Every dish put together on the board. */
    public static List<RecipeHolder<CuttingBoardAssemblyRecipe>> assemblies(Level level) {
        RecipeType<CuttingBoardAssemblyRecipe> type = type(ASSEMBLY_TYPE);
        return type == null ? List.of() : level.getRecipeManager().getAllRecipesFor(type);
    }

    /** The cut the board makes of {@code stack}, as its own lookup finds it. */
    public static Optional<CuttingBoardRecipe> cut(Level level, ItemStack stack) {
        RecipeType<CuttingBoardRecipe> type = type(CUT_TYPE);
        if (type == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return level.getRecipeManager().getRecipeFor(type, new SingleRecipeInput(stack.copyWithCount(1)), level).map(RecipeHolder::value);
    }

    /** What an axe strips {@code stack} into, one of it; empty when it strips nothing. */
    public static Optional<ItemStack> stripped(ItemStack stack) {
        return stack.isEmpty() ? Optional.empty() : Strippables.strip(stack.copyWithCount(1));
    }

    /** What works {@code stack} on the board: a knife's cut, an axe's strip, or nothing. */
    public enum Work {
        CUT, STRIP, NONE
    }

    public static Work work(Level level, ItemStack stack) {
        if (cut(level, stack).isPresent()) {
            return Work.CUT;
        }
        return stripped(stack).isPresent() ? Work.STRIP : Work.NONE;
    }

    /** Whether {@code tool} works {@code stack}: a knife what is cut, an axe what is stripped. */
    public static boolean worksWith(Level level, ItemStack stack, ItemStack tool) {
        return switch (work(level, stack)) {
            case CUT -> isKnife(tool);
            case STRIP -> isAxe(tool);
            case NONE -> false;
        };
    }

    /** How many strokes {@code tool} needs on {@code stack}, as the board counts them (a cleaver its own number); 0 for no work. */
    public static int chops(Level level, ItemStack stack, ItemStack tool) {
        return switch (work(level, stack)) {
            case CUT -> cut(level, stack).map(recipe -> CuttingBoardBlockEntity.requiredChops(tool, recipe)).orElse(0);
            case STRIP -> STRIP_CHOPS;
            case NONE -> 0;
        };
    }

    /** What one item of {@code stack} is cut into: the result, then the byproducts. */
    public static List<ItemStack> pieces(Level level, CuttingBoardRecipe recipe, ItemStack stack) {
        List<ItemStack> pieces = new ArrayList<>();
        pieces.add(recipe.assemble(new SingleRecipeInput(stack.copyWithCount(1)), level.registryAccess()));
        recipe.getByproducts().forEach(byproduct -> pieces.add(byproduct.copy()));
        return pieces;
    }

    public static float experience(CuttingBoardRecipe recipe) {
        return recipe.getExperience();
    }

    /** Whether some dish of the board starts with {@code items}, in their order where the dish has one. */
    public static boolean accepts(Level level, List<ItemStack> items) {
        if (items.isEmpty() || items.size() > MOST) {
            return false;
        }
        List<ItemStack> ones = ones(items);
        return assemblies(level).stream().anyMatch(recipe -> recipe.value().accepts(ones));
    }

    /** Whether some dish of the board is more than {@code items} and starts with them: laid so far, it could still grow. */
    public static boolean canGrow(Level level, List<ItemStack> items) {
        List<ItemStack> ones = ones(items);
        return assemblies(level).stream().anyMatch(recipe -> recipe.value().getIngredients().size() > ones.size() && recipe.value().accepts(ones));
    }

    /** The dish {@code items} make as they lie, as the board's own lookup finds it. */
    public static Optional<RecipeHolder<CuttingBoardAssemblyRecipe>> dish(Level level, List<ItemStack> items) {
        RecipeType<CuttingBoardAssemblyRecipe> type = type(ASSEMBLY_TYPE);
        if (type == null || items.isEmpty()) {
            return Optional.empty();
        }
        return level.getRecipeManager().getRecipeFor(type, new CuttingBoardAssemblyRecipe.Input(ones(items)), level);
    }

    /** The same dish by its id; with {@code preferred} first when it is made of the same. */
    public static Optional<RecipeHolder<CuttingBoardAssemblyRecipe>> dish(Level level, List<ItemStack> items, ResourceLocation preferred) {
        if (preferred != null) {
            List<ItemStack> ones = ones(items);
            Optional<RecipeHolder<CuttingBoardAssemblyRecipe>> picked = assemblies(level).stream()
                    .filter(recipe -> recipe.id().equals(preferred) && recipe.value().matches(new CuttingBoardAssemblyRecipe.Input(ones), level))
                    .findFirst();
            if (picked.isPresent()) {
                return picked;
            }
        }
        return dish(level, items);
    }

    /** One dish of {@code items}: what it is, as many as one round makes. */
    public static ItemStack make(Level level, CuttingBoardAssemblyRecipe recipe, List<ItemStack> items) {
        return recipe.assemble(new CuttingBoardAssemblyRecipe.Input(ones(items)), level.registryAccess());
    }

    /** What a dish takes, in the order it is laid: its ingredients, then the finisher. */
    public static List<Ingredient> laid(CuttingBoardAssemblyRecipe recipe) {
        return recipe.getIngredients();
    }

    public static boolean isOrdered(CuttingBoardAssemblyRecipe recipe) {
        return recipe.isOrdered();
    }

    public static boolean hasFinisher(CuttingBoardAssemblyRecipe recipe) {
        return recipe.getFinisher().isPresent();
    }

    public static ItemStack result(CuttingBoardAssemblyRecipe recipe) {
        return recipe.getResult();
    }

    /** One of each stack, in their order: a dish is matched by kinds, not by how many lie there. */
    public static List<ItemStack> ones(List<ItemStack> items) {
        List<ItemStack> ones = new ArrayList<>(items.size());
        items.forEach(stack -> ones.add(stack.copyWithCount(1)));
        return ones;
    }
}
