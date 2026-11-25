package com.pockyl.handson_letsdo.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;

/**
 * Farm &amp; Charm's mincer, as the station sees it: its own container (the hopper's slot and the output, which the mincer throws
 * out on every tick) and what the station adds to it ({@link MincerHand}). Nothing of Farm &amp; Charm is named here; the mincer's
 * own tick makes every result ({@link MincerHand#handson$mince}).
 */
public final class Mincer {
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int SLOTS = 2;

    private final BlockEntity entity;
    private final Container container;
    private final MincerHand hand;

    private Mincer(BlockEntity entity, Container container, MincerHand hand) {
        this.entity = entity;
        this.container = container;
        this.hand = hand;
    }

    /**
     * The mincer of a block entity, or null for anything that is not the mincer this was made for: another build whose mincer has
     * other slots or does not tick itself is left as it is.
     */
    public static Mincer of(BlockEntity entity) {
        if (!(entity instanceof WorldlyContainer container) || container.getContainerSize() != SLOTS || !(entity instanceof BlockEntityTicker<?>)
                || !(entity instanceof MincerHand hand)) {
            return null;
        }
        var type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType());
        if (type == null || !FarmCharmDishes.MOD_ID.equals(type.getNamespace())) {
            return null;
        }
        return new Mincer(entity, container, hand);
    }

    /** {@link #of}, or an empty mincer that holds nothing where there is none: what shows it never fails. */
    public static Mincer orEmpty(BlockEntity entity) {
        Mincer mincer = of(entity);
        return mincer != null ? mincer : new Mincer(null, new SimpleContainer(SLOTS), null);
    }

    public boolean isReal() {
        return entity != null;
    }

    public Container container() {
        return container;
    }

    /** What the station adds to the mincer; null for the stand-in of {@link #orEmpty}. */
    public MincerHand hand() {
        return hand;
    }

    /**
     * One result of the mincer's own tick ({@link MincerHand#handson$mince}): it takes one of the input and lays its result into the
     * output slot itself, as its recipe and its rules say. Server only.
     */
    public void mince() {
        hand.handson$mince();
    }
}
