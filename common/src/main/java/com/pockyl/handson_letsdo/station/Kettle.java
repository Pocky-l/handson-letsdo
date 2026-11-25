package com.pockyl.handson_letsdo.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * HerbalBrews' tea kettle, as the station sees it: its own container and the numbers its menu shows. Nothing of HerbalBrews is
 * named here: the slots are a vanilla container's, the numbers the kettle's own {@link ContainerData} ({@link BlockNumbers}), so
 * the kettle stays HerbalBrews' in every way and the station only looks into it and lays things in.
 *
 * <p>Slots, as the kettle has them: the drink, four ingredients, the bottles, the water that waits to be poured in, what feeds
 * the heat. Numbers: ticks brewed, whether it stands on something hot, water and heat of a hundred, ticks a bottle takes.
 */
public final class Kettle {
    public static final int DRINK_SLOT = 0;
    public static final int FIRST_INGREDIENT = 1;
    public static final int INGREDIENTS = 4;
    public static final int BOTTLE_SLOT = 5;
    public static final int WATER_SLOT = 6;
    public static final int HEAT_SLOT = 7;
    public static final int SLOTS = 8;

    public static final int TIME = 0;
    /** Only read: setting it sets the kettle's heat too. */
    public static final int HEATED = 1;
    public static final int WATER = 2;
    public static final int HEAT = 3;
    public static final int NEEDED = 4;
    public static final int NUMBERS = 5;
    /** A full kettle, and the hottest it gets. */
    public static final int FULL = 100;

    private final Container container;
    private final ContainerData numbers;
    private final boolean real;
    private final KettleDrinks drinks;

    private Kettle(Container container, ContainerData numbers, boolean real, KettleDrinks drinks) {
        this.container = container;
        this.numbers = numbers;
        this.real = real;
        this.drinks = drinks;
    }

    /**
     * The kettle of a block entity, or null for anything that is not the kettle this was made for: another build of HerbalBrews
     * whose kettle has other slots or numbers keeps its own menu.
     */
    public static Kettle of(BlockEntity entity) {
        if (!(entity instanceof WorldlyContainer container) || container.getContainerSize() != SLOTS) {
            return null;
        }
        var type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType());
        if (type == null || !HerbalTeas.MOD_ID.equals(type.getNamespace())) {
            return null;
        }
        ContainerData numbers = BlockNumbers.of(entity);
        if (numbers == null || numbers.getCount() != NUMBERS) {
            return null;
        }
        return new Kettle(container, numbers, true, entity instanceof KettleDrinks waiting ? waiting : null);
    }

    /** {@link #of}, or an empty kettle that holds nothing and takes nothing where there is none: what shows it never fails. */
    public static Kettle orEmpty(BlockEntity entity) {
        Kettle kettle = of(entity);
        return kettle != null ? kettle : new Kettle(new SimpleContainer(SLOTS), new SimpleContainerData(NUMBERS), false, null);
    }

    /** Whether this is a kettle in the world, not the stand-in of {@link #orEmpty}. */
    public boolean isReal() {
        return real;
    }

    public Container container() {
        return container;
    }

    /** The drinks that wait in the kettle ({@link KettleDrinks}); null where its mixin is not applied. */
    public KettleDrinks drinks() {
        return drinks;
    }

    /** The drink that waits in the kettle, its count how many; empty for none. */
    public ItemStack waiting() {
        return drinks == null ? ItemStack.EMPTY : drinks.handson$waiting();
    }

    /** Ticks the bottle that brews has brewed; 0 while nothing does. */
    public int time() {
        return Math.max(0, numbers.get(TIME));
    }

    /** Tells the kettle it has brewed this long: only a test skips the wait so. */
    public void setTime(int ticks) {
        numbers.set(TIME, ticks);
    }

    /** Ticks the kettle counts to for the bottle that brews: its recipe's, once it has begun. */
    public int needed() {
        return Math.max(0, numbers.get(NEEDED));
    }

    public boolean isHeated() {
        return numbers.get(HEATED) != 0;
    }

    /** Water in it, 0 to {@link #FULL}. */
    public int water() {
        return Math.max(0, Math.min(FULL, numbers.get(WATER)));
    }

    /** How hot it is, 0 to {@link #FULL}. */
    public int heat() {
        return Math.max(0, Math.min(FULL, numbers.get(HEAT)));
    }

    /** Fills the kettle for a game test: what pouring does. */
    public void setWater(int water) {
        numbers.set(WATER, water);
    }

    /** Heats the kettle for a game test: what heat items do. */
    public void setHeat(int heat) {
        numbers.set(HEAT, heat);
    }
}
