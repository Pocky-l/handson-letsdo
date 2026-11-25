package com.pockyl.handson_letsdo.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Vinery's apple press, as the station sees it: its own container and the numbers its menu shows. Nothing of Vinery is named
 * here: the slots are a vanilla container's, the numbers the press's own {@link ContainerData} ({@link BlockNumbers}), so the
 * press stays Vinery's in every way and the station only looks into it and lays things in.
 *
 * <p>Slots, as the press has them: the fruit, the mash it is pressed to, the empty bottles, what is bottled. Numbers: ticks
 * pressed and the ticks one fruit takes, ticks bottled and the ticks one bottle takes. The press is two blocks tall and keeps
 * everything in its lower half.
 */
public final class Press {
    public static final int FRUIT_SLOT = 0;
    public static final int MASH_SLOT = 1;
    public static final int BOTTLE_SLOT = 2;
    public static final int RESULT_SLOT = 3;
    public static final int SLOTS = 4;

    public static final int MASHED = 0;
    public static final int MASH_TIME = 1;
    public static final int BOTTLED = 2;
    public static final int BOTTLE_TIME = 3;
    public static final int NUMBERS = 4;

    private final Container container;
    private final ContainerData numbers;
    private final boolean real;
    private final PressBasket basket;

    private Press(Container container, ContainerData numbers, boolean real, PressBasket basket) {
        this.container = container;
        this.numbers = numbers;
        this.real = real;
        this.basket = basket;
    }

    /**
     * The press of a block entity, or null for anything that is not the press this was made for: another build of Vinery whose
     * press has other slots or numbers keeps its own menu.
     */
    public static Press of(BlockEntity entity) {
        if (!(entity instanceof WorldlyContainer container) || container.getContainerSize() != SLOTS) {
            return null;
        }
        var type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType());
        if (type == null || !BarrelStation.MOD_ID.equals(type.getNamespace())) {
            return null;
        }
        ContainerData numbers = BlockNumbers.of(entity);
        if (numbers == null || numbers.getCount() != NUMBERS) {
            return null;
        }
        return new Press(container, numbers, true, entity instanceof PressBasket basket ? basket : null);
    }

    /** {@link #of}, or an empty press that holds nothing and takes nothing where there is none: what shows it never fails. */
    public static Press orEmpty(BlockEntity entity) {
        Press press = of(entity);
        return press != null ? press : new Press(new SimpleContainer(SLOTS), new SimpleContainerData(NUMBERS), false, null);
    }

    /** Whether {@code state} is the upper half of a block two blocks tall, as the press names its halves. */
    public static boolean isUpper(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals("half")) {
                return state.getValue(property).toString().equals("upper");
            }
        }
        return false;
    }

    /** The lower half of the press whose block at {@code pos} is {@code state}: where it keeps everything. */
    public static BlockPos lower(BlockPos pos, BlockState state) {
        return isUpper(state) ? pos.below() : pos;
    }

    /** Whether this is a press in the world, not the stand-in of {@link #orEmpty}. */
    public boolean isReal() {
        return real;
    }

    public Container container() {
        return container;
    }

    /** The basket's front as the station keeps it on the press; null where the press has none (its mixin not applied). */
    public PressBasket basket() {
        return basket;
    }

    /** Portions the tub under the plate's lip holds at the most (a bottle each). */
    public static final int TUB_MOST = 8;
    /** Pressed by hand: fruits pressed together, presses of the screw's cap for them, and ticks a bottle's worth of juice takes. */
    public static final int LOAD = 8;
    public static final int PRESSES_PER_LOAD = 4;
    public static final int JUICE_TICKS = 10;

    /** What the tub under the lip holds, a count of portions; empty for none, and for a press without the tub. */
    public ItemStack tub() {
        return basket == null ? ItemStack.EMPTY : basket.handson$tub();
    }

    /** Whether things go into and out of the basket: its front open, or a press without one. */
    public boolean isOpen() {
        return basket == null || basket.handson$isOpen();
    }

    /** Ticks the fruit under the plate has been pressed; 0 while none is. */
    public int mashed() {
        return Math.max(0, numbers.get(MASHED));
    }

    /** Ticks one fruit takes: Vinery's own setting. */
    public int mashTime() {
        return Math.max(1, numbers.get(MASH_TIME));
    }

    /** Ticks the bottle that fills has been filling; 0 while none does. */
    public int bottled() {
        return Math.max(0, numbers.get(BOTTLED));
    }

    /** Ticks one bottle takes: Vinery's own setting. */
    public int bottleTime() {
        return Math.max(1, numbers.get(BOTTLE_TIME));
    }

    /** Tells the press it has pressed this long: only a test skips the wait so. */
    public void setMashed(int ticks) {
        numbers.set(MASHED, ticks);
    }

    /** Tells the press it has bottled this long: only a test skips the wait so. */
    public void setBottled(int ticks) {
        numbers.set(BOTTLED, ticks);
    }
}
