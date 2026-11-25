package com.pockyl.handson_letsdo.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Farm &amp; Charm's mincer, as the station sees it: its own container (the hopper's slot and the output, which the mincer throws
 * out on every tick) and what the station adds to it ({@link MincerHand}). Nothing of Farm &amp; Charm is named here; the mincer's
 * own tick makes every result, run once a result through vanilla's ticker with its crank turned the last tick of a result.
 */
public final class Mincer {
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int SLOTS = 2;
    /** The mincer's crank, ticks it still turns for, and how far one result has got, ticks; one result takes this many. */
    private static final String CRANK = "crank";
    private static final String CRANKED = "cranked";
    private static final int RESULT_TICKS = 20;

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
     * One result of the mincer's own tick: the tick runs once with the crank on the last tick of a result, so the mincer takes one
     * of the input and lays its result into the output slot itself, as its recipe and its rules say. What lay in the output slot
     * is not thrown out meanwhile ({@link MincerHand#handson$setMaking}). Server only.
     */
    @SuppressWarnings("unchecked")
    public void mince() {
        Level level = entity.getLevel();
        BlockState state = entity.getBlockState();
        IntegerProperty crank = number(state, CRANK);
        IntegerProperty cranked = number(state, CRANKED);
        if (level == null || crank == null || cranked == null) {
            return;
        }
        hand.handson$setMaking(true);
        try {
            BlockState turning = state.setValue(crank, 1).setValue(cranked, RESULT_TICKS - 1);
            ((BlockEntityTicker<BlockEntity>) entity).tick(level, entity.getBlockPos(), turning, entity);
        } finally {
            hand.handson$setMaking(false);
        }
    }

    private static IntegerProperty number(BlockState state, String name) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty number && property.getName().equals(name)) {
                return number;
            }
        }
        return null;
    }
}
