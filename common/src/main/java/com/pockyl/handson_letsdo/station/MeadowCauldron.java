package com.pockyl.handson_letsdo.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;

/**
 * Meadow's cooking cauldron, standing on something hot or hanging in its frame over its own fire, as the station sees it: its own
 * container and the numbers its menu shows ({@link BlockNumbers}). Nothing of Meadow is named here but the ids of its block, its
 * recipes and its tags: the slots are a vanilla container's, the numbers the cauldron's own {@link ContainerData}, so the cauldron
 * stays Meadow's in every way and the station only looks into it and lays things in.
 *
 * <p>Slots, as the cauldron has them: the dish (0), six ingredients (1 to 6; a bowl is one of them), the water poured in (7), which
 * its tick empties into its water (a bucket 50, a bottle 25, at most 100) and leaves what held it there. Numbers: ticks cooked,
 * whether it is hot, its water, the ticks this dish takes. It cooks a dish while something hot stands under it (or it hangs over
 * its fire), its six slots hold exactly one ingredient each of a recipe, it has the water the recipe takes and the dish slot has
 * room: one dish in the recipe's time, one of each ingredient and the recipe's water taken. Its matching also counts the dish slot
 * with the ingredients, so it cooks no second dish until the first one is taken.
 */
public final class MeadowCauldron {
    public static final String MOD_ID = "meadow";
    public static final int OUTPUT_SLOT = 0;
    public static final int FIRST_INGREDIENT = 1;
    public static final int INGREDIENTS = 6;
    public static final int WATER_SLOT = 7;
    public static final int SLOTS = 8;

    public static final int TIME = 0;
    public static final int HEATED = 1;
    public static final int WATER = 2;
    public static final int DURATION = 3;
    public static final int NUMBERS = 4;

    /** The most water the cauldron holds, and what a bucket and a bottle pour in, as Meadow has them. */
    public static final int MOST_WATER = 100;
    public static final int BUCKET = 50;
    public static final int BOTTLE = 25;

    @SuppressWarnings("removal")
    public static final ResourceLocation BLOCK = new ResourceLocation(MOD_ID, "cooking_cauldron");
    @SuppressWarnings("removal")
    public static final ResourceLocation RECIPES = new ResourceLocation(MOD_ID, "cooking");
    /** What a standing cauldron cooks on: Meadow's own tag (its stove tiles, and what other mods add to the common one). */
    @SuppressWarnings("removal")
    public static final TagKey<Block> HEAT = TagKey.create(Registries.BLOCK, new ResourceLocation(MOD_ID, "allows_cooking"));
    @SuppressWarnings("removal")
    private static final TagKey<Item> LARGE_WATER = TagKey.create(Registries.ITEM, new ResourceLocation(MOD_ID, "large_water_fill"));
    @SuppressWarnings("removal")
    private static final TagKey<Item> SMALL_WATER = TagKey.create(Registries.ITEM, new ResourceLocation(MOD_ID, "small_water_fill"));

    private final BlockEntity entity;
    private final Container container;
    private final ContainerData numbers;

    private MeadowCauldron(BlockEntity entity, Container container, ContainerData numbers) {
        this.entity = entity;
        this.container = container;
        this.numbers = numbers;
    }

    /**
     * The cauldron of a block entity, or null for anything that is not the cauldron this was made for: another build of Meadow whose
     * cauldron has other slots or numbers keeps its own menu.
     */
    public static MeadowCauldron of(BlockEntity entity) {
        if (!(entity instanceof WorldlyContainer container) || container.getContainerSize() != SLOTS) {
            return null;
        }
        if (!BLOCK.equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()))) {
            return null;
        }
        ContainerData numbers = BlockNumbers.of(entity);
        if (numbers == null || numbers.getCount() != NUMBERS) {
            return null;
        }
        return new MeadowCauldron(entity, container, numbers);
    }

    /** {@link #of}, or an empty cauldron that holds nothing and takes nothing where there is none: what shows it never fails. */
    public static MeadowCauldron orEmpty(BlockEntity entity) {
        MeadowCauldron cauldron = entity == null ? null : of(entity);
        return cauldron != null ? cauldron : new MeadowCauldron(null, new SimpleContainer(SLOTS), new SimpleContainerData(NUMBERS));
    }

    /** Whether this is a cauldron in the world, not the stand-in of {@link #orEmpty}. */
    public boolean isReal() {
        return entity != null;
    }

    public BlockEntity entity() {
        return entity;
    }

    public Container container() {
        return container;
    }

    public ItemStack output() {
        return container.getItem(OUTPUT_SLOT);
    }

    public ItemStack waterSlot() {
        return container.getItem(WATER_SLOT);
    }

    /** Ticks the dish has cooked; 0 while nothing cooks. */
    public int time() {
        return Math.max(0, numbers.get(TIME));
    }

    /** Tells the cauldron the dish has cooked this long: stirring hurries it so, and a test skips the wait. */
    public void setTime(int ticks) {
        numbers.set(TIME, ticks);
    }

    /** Ticks the dish that cooks takes: the recipe's seconds; 0 while nothing cooks. */
    public int duration() {
        return Math.max(0, numbers.get(DURATION));
    }

    /** Its water, 0 to {@link #MOST_WATER}. */
    public int water() {
        return Math.max(0, numbers.get(WATER));
    }

    /** Sets its water: a test fills it so. */
    public void setWater(int water) {
        numbers.set(WATER, Math.max(0, Math.min(MOST_WATER, water)));
    }

    /** Whether a dish cooks now, as its own tick counts it. */
    public boolean cooks() {
        return duration() > 0;
    }

    /** Whether it cooks where it stands, as its own tick asks: hanging over its own fire, or on something hot. */
    public static boolean isHeated(Level level, BlockPos pos, BlockState state) {
        return hangs(state) || level.getBlockState(pos.below()).is(HEAT);
    }

    /** Whether it hangs in its frame over its own fire: then nothing needs to be under it. */
    public static boolean hangs(BlockState state) {
        return flag(state, "hanging");
    }

    /** Whether its block shows its lid on: it does while a dish cooks. */
    public static boolean lidded(BlockState state) {
        return flag(state, "cooking");
    }

    private static boolean flag(BlockState state, String name) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof BooleanProperty flag && property.getName().equals(name)) {
                return state.getValue(flag);
            }
        }
        return false;
    }

    /** The ingredients in it, its slots 1 to 6 in their order, the empty ones too. */
    public List<ItemStack> ingredients() {
        List<ItemStack> stacks = new ArrayList<>(INGREDIENTS);
        for (int slot = FIRST_INGREDIENT; slot < FIRST_INGREDIENT + INGREDIENTS; slot++) {
            stacks.add(container.getItem(slot));
        }
        return stacks;
    }

    /**
     * How much water {@code stack} pours into the cauldron at the station: a bucket of water (Meadow's large fill: vanilla's and its
     * own wooden one) 50, a bottle of water 25; anything else none. Meadow's tick also takes any potion as a bottle of water: the
     * station never pours a potion away.
     */
    public static int waterOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (stack.is(LARGE_WATER)) {
            return BUCKET;
        }
        return stack.is(SMALL_WATER) && PotionUtils.getPotion(stack) == Potions.WATER && PotionUtils.getCustomEffects(stack).isEmpty()
                ? BOTTLE : 0;
    }

    /** What the cauldron's own tick takes as water in its water slot: Meadow's two tags. */
    public static boolean takesAsWater(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(LARGE_WATER) || stack.is(SMALL_WATER));
    }

    /** One of Meadow's cooking recipes as the station reads it. */
    public record Dish(ResourceLocation id, List<Ingredient> ingredients, ItemStack result, int water, int ticks) {
    }

    /** Every dish the cauldron cooks now, from the live recipe manager, in its order. */
    @SuppressWarnings("unchecked")
    public static List<Dish> dishes(Level level) {
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(RECIPES);
        List<Dish> dishes = new ArrayList<>();
        if (type == null) {
            return dishes;
        }
        for (Recipe<?> recipe : level.getRecipeManager().getAllRecipesFor((RecipeType<Recipe<Container>>) type)) {
            ItemStack result = recipe.getResultItem(level.registryAccess());
            List<Ingredient> ingredients = recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
            if (result.isEmpty() || ingredients.isEmpty() || ingredients.size() > INGREDIENTS) {
                continue;
            }
            dishes.add(new Dish(recipe.getId(), ingredients, result.copy(), number(recipe, "getFluidAmount"),
                    number(recipe, "getCraftingDuration") * 20));
        }
        return dishes;
    }

    /** One of the numbers Meadow's recipe keeps, by its getter's name (its class is not named here); 0 where it has none. */
    private static int number(Recipe<?> recipe, String getter) {
        try {
            return (int) recipe.getClass().getMethod(getter).invoke(recipe);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return 0;
        }
    }

    /** The dish that is exactly what lies in the ingredient slots, as the cauldron's own matching reads it; null for none. */
    public static Dish making(Level level, List<ItemStack> ingredients) {
        List<StationRecipes.Candidate<Dish>> candidates = StationRecipes.candidates(dishes(level), Dish::id, Dish::ingredients);
        StationRecipes.Candidate<Dish> found = StationRecipes.reads(candidates, ingredients);
        return found == null ? null : found.recipe();
    }

    /** Whether some dish takes {@code stack} as an ingredient. */
    public static boolean isIngredient(Level level, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (Dish dish : dishes(level)) {
            for (Ingredient ingredient : dish.ingredients()) {
                if (ingredient.test(stack)) {
                    return true;
                }
            }
        }
        return false;
    }
}
