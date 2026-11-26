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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

import com.pockyl.handson_letsdo.HandsOnConfig;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A pot of Farm &amp; Charm (its cooking pot, its roaster, Candlelight's pot and pan and Bakery's small pot, which are built as
 * Farm &amp; Charm's) as the station sees it: its own container and the numbers its menu shows ({@link BlockNumbers}). Nothing of either mod is named here,
 * so the pot stays its mod's in every way and the station only looks into it.
 *
 * <p>Slots, as the pot has them: six ingredients, the vessel a serving is filled into (a bowl), the dish. Numbers: ticks cooked,
 * and whether something hot stands under it.
 */
public final class Pot {
    public static final int INGREDIENTS = 6;
    public static final int VESSEL_SLOT = 6;
    public static final int OUTPUT_SLOT = 7;
    public static final int SLOTS = 8;

    public static final int TIME = 0;
    public static final int HEATED = 1;
    public static final int NUMBERS = 2;

    /** Candlelight's mod id: its cooking pot and its cooking pan are of these pots. */
    public static final String CANDLELIGHT = "candlelight";
    /** Bakery's mod id: its small cooking pot is of these pots. */
    public static final String BAKERY = "bakery";

    /** Ticks Farm &amp; Charm cooks one serving for, in its pot and its roaster: the longest any of these pots takes. */
    public static final int UPSTREAM_TIME = 900;

    /** What a pot stands on to cook: Farm &amp; Charm's own tag. */
    @SuppressWarnings("removal")
    public static final TagKey<Block> HEAT = TagKey.create(Registries.BLOCK, new ResourceLocation(FarmCharmDishes.MOD_ID, "allows_cooking"));

    /**
     * One kind of pot: its block, the recipes it cooks, the block state it shows while it cooks, whether its own tick asks who
     * cooks before it makes a dish that wants learning, the ticks its own tick cooks one serving for, whether its own tick makes
     * one of a dish a serving whatever the recipe gives ({@code oneEach}), and what the config says of its station: whether it is
     * one, and how many times as long as usual it takes under the spoon.
     */
    public record Kind(String name, ResourceLocation block, ResourceLocation recipes, String cookingState, boolean learns,
            int upstreamTime, boolean oneEach, BooleanSupplier on, Supplier<Float> time) {
        @SuppressWarnings("removal")
        static Kind of(String block, String recipes, String cookingState, BooleanSupplier on, Supplier<Float> time) {
            return new Kind(block, new ResourceLocation(FarmCharmDishes.MOD_ID, block), new ResourceLocation(FarmCharmDishes.MOD_ID, recipes),
                    cookingState, true, UPSTREAM_TIME, false, on, time);
        }

        /** How many of {@code dish} the pot's own tick makes a serving: the recipe's count, or one in a pot that makes one each. */
        public int perServing(Dish dish) {
            return oneEach ? 1 : Math.max(1, dish.result().getCount());
        }

        public Supplier<Block> blockSupplier() {
            return () -> BuiltInRegistries.BLOCK.get(block);
        }

        /** Whether this pot is a station here: the config leaves it on. */
        public boolean active() {
            return on.getAsBoolean();
        }

        /** Ticks one serving stirred at the station takes: a Farmer's Delight stew's, as the config makes them. */
        public int handTime() {
            return HandsOnConfig.scaled(HAND_TIME, time.get());
        }

        /** Every dish this pot cooks now. */
        public List<Dish> dishes(Level level) {
            return FarmCharmDishes.all(level, recipes);
        }
    }

    /** Ticks one serving takes while it is stirred, before the config and the size of the batch. */
    public static final int HAND_TIME = 200;

    public static final Kind COOKING_POT = Kind.of("cooking_pot", "pot_cooking", "cooking", HandsOnConfig::cookingPot,
            HandsOnConfig::cookingPotTime);
    /** Farm &amp; Charm's roaster: the pot's slots and numbers, its own recipes; the roast is turned as the soup is stirred. */
    public static final Kind ROASTER = Kind.of("roaster", "roaster", "roasting", HandsOnConfig::roaster, HandsOnConfig::roasterTime);
    /**
     * Candlelight's cooking pot: a copy of Farm &amp; Charm's pot of its own (the same slots, numbers, heat and time) that cooks
     * Farm &amp; Charm's dishes and asks nobody whether a dish was learned. Stirred as Farm &amp; Charm's pot, in its time.
     */
    @SuppressWarnings("removal")
    public static final Kind CANDLELIGHT_POT = new Kind("candlelight_cooking_pot", new ResourceLocation(CANDLELIGHT, "cooking_pot"),
            new ResourceLocation(FarmCharmDishes.MOD_ID, "pot_cooking"), "cooking", false, UPSTREAM_TIME, false,
            HandsOnConfig::candlelightCookingPot, HandsOnConfig::cookingPotTime);
    /**
     * Candlelight's cooking pan: a copy of Farm &amp; Charm's roaster of its own (the same slots, numbers and heat) that roasts Farm
     * &amp; Charm's roasts in a third of the roaster's time and asks nobody whether a roast was learned. Swirled as the roaster, in
     * its time.
     */
    @SuppressWarnings("removal")
    public static final Kind CANDLELIGHT_PAN = new Kind("candlelight_cooking_pan", new ResourceLocation(CANDLELIGHT, "cooking_pan"),
            new ResourceLocation(FarmCharmDishes.MOD_ID, "roaster"), "cooking", false, 300, false, HandsOnConfig::candlelightCookingPan,
            HandsOnConfig::roasterTime);

    /**
     * Bakery's small cooking pot: a copy of Farm &amp; Charm's pot of its own (the same slots, numbers and heat) that cooks Farm
     * &amp; Charm's dishes in a third of the pot's time, one of a dish a serving (twelve yeast in Farm &amp; Charm's pot are one
     * here), and asks nobody whether a dish was learned. Stirred as Farm &amp; Charm's pot, in its time.
     */
    @SuppressWarnings("removal")
    public static final Kind BAKERY_POT = new Kind("bakery_small_cooking_pot", new ResourceLocation(BAKERY, "small_cooking_pot"),
            new ResourceLocation(FarmCharmDishes.MOD_ID, "pot_cooking"), "cooking", false, 300, true,
            HandsOnConfig::bakerySmallCookingPot, HandsOnConfig::cookingPotTime);

    private static final List<Kind> KINDS = List.of(COOKING_POT, ROASTER, CANDLELIGHT_POT, CANDLELIGHT_PAN, BAKERY_POT);

    private final BlockEntity entity;
    private final Container container;
    private final ContainerData numbers;
    private final Kind kind;

    private Pot(BlockEntity entity, Container container, ContainerData numbers, Kind kind) {
        this.entity = entity;
        this.container = container;
        this.numbers = numbers;
        this.kind = kind;
    }

    /**
     * The pot of a block entity, or null for anything that is not a pot this was made for: another build of Farm &amp; Charm whose
     * pot has other slots or numbers keeps its own menu.
     */
    public static Pot of(BlockEntity entity) {
        if (!(entity instanceof WorldlyContainer container) || container.getContainerSize() != SLOTS) {
            return null;
        }
        Kind kind = kindOf(entity.getBlockState());
        if (kind == null) {
            return null;
        }
        ContainerData numbers = BlockNumbers.of(entity);
        return numbers == null || numbers.getCount() != NUMBERS ? null : new Pot(entity, container, numbers, kind);
    }

    /** {@link #of}, or an empty pot of {@code kind} that holds nothing where there is none: what shows it never fails. */
    public static Pot orEmpty(BlockEntity entity, Kind kind) {
        Pot pot = of(entity);
        return pot != null ? pot : new Pot(null, new SimpleContainer(SLOTS), new SimpleContainerData(NUMBERS), kind);
    }

    private static Kind kindOf(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        for (Kind kind : KINDS) {
            if (kind.block().equals(id)) {
                return kind;
            }
        }
        return null;
    }

    /**
     * What the food in {@code food} (a pot's slots, the ingredients first) makes in a pot of {@code kind}, as Farm &amp; Charm's
     * own lookup reads it: the first of its recipes that takes exactly these stacks. Null for nothing.
     */
    @SuppressWarnings("unchecked")
    public static Dish making(Level level, Kind kind, Container food) {
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(kind.recipes());
        if (type == null) {
            return null;
        }
        ResourceLocation id = level.getRecipeManager().getRecipeFor((RecipeType<Recipe<Container>>) type, food, level).map(Recipe::getId)
                .orElse(null);
        for (Dish dish : kind.dishes(level)) {
            if (dish.id().equals(id)) {
                return dish;
            }
        }
        return null;
    }

    /** Whether this is a pot in the world, not the stand-in of {@link #orEmpty}. */
    public boolean isReal() {
        return entity != null;
    }

    public BlockEntity entity() {
        return entity;
    }

    public Container container() {
        return container;
    }

    public Kind kind() {
        return kind;
    }

    /** The station's part of the pot ({@code StirredPotMixin}); null where the mixin is not in it, and the pot is never stirred. */
    public PotCooking cooking() {
        return entity instanceof StirredPot stirred ? stirred.handson$cooking() : null;
    }

    /** Ticks the pot's own tick counts this serving cooked, of its {@link Kind#upstreamTime}. */
    public int time() {
        return numbers.get(TIME);
    }

    public void setTime(int ticks) {
        numbers.set(TIME, ticks);
    }

    /** Tells the pot's own menu whether it stands on something hot: it reads this only when the pot's own tick runs. */
    public void setHeated(boolean heated) {
        numbers.set(HEATED, heated ? 1 : 0);
    }

    /** Whether something hot stands under the pot. */
    public boolean isHeated() {
        Level level = entity == null ? null : entity.getLevel();
        return level != null && isHeated(level, entity.getBlockPos());
    }

    public static boolean isHeated(Level level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(HEAT);
    }

    public ItemStack ingredient(int slot) {
        return container.getItem(slot);
    }

    public ItemStack vessels() {
        return container.getItem(VESSEL_SLOT);
    }

    public ItemStack output() {
        return container.getItem(OUTPUT_SLOT);
    }

    /**
     * Shows on the block itself whether it stands on something hot and whether it cooks, as its own tick does: that tick does not
     * run while a cook's food waits for the spoon.
     */
    public void showState(boolean heated, boolean cooking) {
        if (entity == null || entity.getLevel() == null) {
            return;
        }
        BlockState state = entity.getBlockState();
        BlockState shown = with(with(state, "lit", heated), kind.cookingState(), cooking);
        if (shown != state) {
            entity.getLevel().setBlock(entity.getBlockPos(), shown, Block.UPDATE_ALL);
        }
    }

    private static BlockState with(BlockState state, String name, boolean value) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof BooleanProperty flag && property.getName().equals(name)) {
                return state.getValue(flag) == value ? state : state.setValue(flag, value);
            }
        }
        return state;
    }
}
