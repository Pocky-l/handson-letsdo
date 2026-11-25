package com.pockyl.handson_letsdo.station;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Vinery's fermentation barrel, as the station sees it: its own container and the numbers its menu shows. Nothing of Vinery is
 * named here: the slots are a vanilla container's, the numbers the barrel's own {@link ContainerData}
 * ({@link BlockNumbers}), so the barrel stays Vinery's in every way and the station only looks into it.
 *
 * <p>Slots, as the barrel has them: the juice bottle that is poured in, three ingredients, the empty wine bottles, the wine.
 * Numbers: ticks fermented, ticks a wine takes, how much juice stands in it, and which juice.
 */
public final class Barrel {
    public static final int JUICE_SLOT = 0;
    public static final int FIRST_INGREDIENT = 1;
    public static final int INGREDIENTS = 3;
    public static final int BOTTLE_SLOT = 4;
    public static final int WINE_SLOT = 5;
    public static final int SLOTS = 6;

    public static final int TIME = 0;
    public static final int TOTAL = 1;
    public static final int LEVEL = 2;
    public static final int JUICE = 3;
    public static final int NUMBERS = 4;

    /** The juices by the number the barrel gives them; a wine names its juice by these words. */
    private static final String[] JUICES = {"white_general", "red_general", "white_savanna", "red_savanna", "white_taiga", "red_taiga",
            "white_jungle", "red_jungle", "apple", "red_crimson", "white_warped"};

    private final Container container;
    private final ContainerData numbers;
    private final boolean real;
    /** The station's part of the barrel ({@code FermentationBarrelMixin}); null where the mixin is not in it. */
    private final BrewingBarrel brew;

    private Barrel(Container container, ContainerData numbers, boolean real, BrewingBarrel brew) {
        this.container = container;
        this.numbers = numbers;
        this.real = real;
        this.brew = brew;
    }

    /**
     * The barrel of a block entity, or null for anything that is not the barrel this was made for: another build of Vinery whose
     * barrel has other slots or numbers keeps its own menu.
     */
    public static Barrel of(BlockEntity entity) {
        if (!(entity instanceof WorldlyContainer container) || container.getContainerSize() != SLOTS) {
            return null;
        }
        ContainerData numbers = BlockNumbers.of(entity);
        return numbers == null || numbers.getCount() != NUMBERS ? null
                : new Barrel(container, numbers, true, entity instanceof BrewingBarrel brewing ? brewing : null);
    }

    /** {@link #of}, or an empty barrel that holds nothing and takes nothing where there is none: what shows it never fails. */
    public static Barrel orEmpty(BlockEntity entity) {
        Barrel barrel = of(entity);
        return barrel != null ? barrel : new Barrel(new SimpleContainer(SLOTS), new SimpleContainerData(NUMBERS), false, null);
    }

    /** Whether this is a barrel in the world, not the stand-in of {@link #orEmpty}. */
    public boolean isReal() {
        return real;
    }

    public Container container() {
        return container;
    }

    /** The station's part of the barrel: its fermenting and the wine that waits in it; null where there is none. */
    public BrewingBarrel brew() {
        return brew;
    }

    /** Ticks what lies in it has fermented. */
    public int time() {
        return brew != null ? brew.handson$brewTime() : numbers.get(TIME);
    }

    /** Tells the barrel it has fermented this long: only a test skips the wait so. */
    public void setTime(int ticks) {
        if (brew != null) {
            brew.handson$setBrewTime(ticks);
        } else {
            numbers.set(TIME, ticks);
        }
    }

    /** How many bottles of wine wait in it to be drawn, not counting those already in its wine slot. */
    public int portions() {
        return brew != null ? brew.handson$portions() : 0;
    }

    /** Ticks a wine takes: Vinery's own setting. */
    public int total() {
        return numbers.get(TOTAL);
    }

    /** How much juice stands in it, in Vinery's measure (a bottle is {@link VineryWines#perBottle} of it). */
    public int level() {
        return Math.max(0, numbers.get(LEVEL));
    }

    /** Which juice stands in it, as wines name it; empty for none, {@code "unknown"} for one this mod has no word for. */
    public String juice() {
        if (level() <= 0) {
            return "";
        }
        int index = numbers.get(JUICE);
        return index >= 0 && index < JUICES.length ? JUICES[index] : "unknown";
    }
}
