package com.pockyl.handson_letsdo.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;

/**
 * Farm &amp; Charm's crafting bowl, as the station sees it: its own container (four ingredients and the result) and what the station
 * adds to it ({@link BowlHand}). Nothing of Farm &amp; Charm is named here; the bowl's own tick makes every set, run once a set through
 * vanilla's ticker with its stirring on the last tick of a set.
 */
public final class Bowl {
    public static final int INGREDIENTS = 4;
    public static final int RESULT_SLOT = 4;
    public static final int SLOTS = 5;
    /** The bowl's stirring, ticks it still goes on, and how far a set has got; a set takes this many ticks of stirring. */
    private static final String STIRRING = "stirring";
    private static final String STIRRED = "stirred";
    private static final int SET_TICKS = 50;
    @SuppressWarnings("removal")
    private static final ResourceLocation BLOCK = new ResourceLocation(FarmCharmDishes.MOD_ID, "crafting_bowl");

    private final BlockEntity entity;
    private final Container container;
    private final BowlHand hand;

    private Bowl(BlockEntity entity, Container container, BowlHand hand) {
        this.entity = entity;
        this.container = container;
        this.hand = hand;
    }

    /** The bowl of a block entity, or null for anything that is not the bowl this was made for. */
    public static Bowl of(BlockEntity entity) {
        if (!(entity instanceof WorldlyContainer container) || container.getContainerSize() != SLOTS || !(entity instanceof BlockEntityTicker<?>)
                || !(entity instanceof BowlHand hand)) {
            return null;
        }
        var type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType());
        if (type == null || !FarmCharmDishes.MOD_ID.equals(type.getNamespace())) {
            return null;
        }
        return new Bowl(entity, container, hand);
    }

    /** {@link #of}, or an empty bowl that holds nothing where there is none: what shows it never fails. */
    public static Bowl orEmpty(BlockEntity entity) {
        Bowl bowl = of(entity);
        return bowl != null ? bowl : new Bowl(null, new SimpleContainer(SLOTS), null);
    }

    /** Whether {@code block} is Farm &amp; Charm's crafting bowl. */
    public static boolean isBowlBlock(Block block) {
        return BLOCK.equals(BuiltInRegistries.BLOCK.getKey(block));
    }

    public boolean isReal() {
        return entity != null;
    }

    public Container container() {
        return container;
    }

    /** What the station adds to the bowl; null for the stand-in of {@link #orEmpty}. */
    public BowlHand hand() {
        return hand;
    }

    /** The four ingredient stacks as they lie. */
    public List<ItemStack> ingredients() {
        List<ItemStack> stacks = new ArrayList<>(INGREDIENTS);
        for (int i = 0; i < INGREDIENTS; i++) {
            stacks.add(container.getItem(i));
        }
        return stacks;
    }

    /**
     * One set of the bowl's own tick: the tick runs once with the stirring on the last tick of a set, so the bowl takes one of each
     * ingredient and lays its result into the result slot itself, as its recipe and its rules say. Server only.
     */
    @SuppressWarnings("unchecked")
    public void stirOnce() {
        Level level = entity.getLevel();
        BlockState state = entity.getBlockState();
        IntegerProperty stirring = number(state, STIRRING);
        IntegerProperty stirred = number(state, STIRRED);
        if (level == null || stirring == null || stirred == null) {
            return;
        }
        BlockState going = state.setValue(stirring, 1).setValue(stirred, SET_TICKS - 1);
        ((BlockEntityTicker<BlockEntity>) entity).tick(level, entity.getBlockPos(), going, entity);
    }

    /**
     * Sets the bowl's own count of a set back to nothing once its result was taken at the station, as its own use does when a player
     * takes it: its look and its next stirring go on from an empty bowl. Server only.
     */
    public void resetWhenTaken() {
        Level level = entity == null ? null : entity.getLevel();
        if (level == null || level.isClientSide() || !container.getItem(RESULT_SLOT).isEmpty()) {
            return;
        }
        BlockState state = entity.getBlockState();
        IntegerProperty stirred = number(state, STIRRED);
        if (stirred != null && state.getValue(stirred) >= SET_TICKS) {
            level.setBlock(entity.getBlockPos(), state.setValue(stirred, 0), Block.UPDATE_ALL);
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
