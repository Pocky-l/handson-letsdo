package com.pockyl.handson_letsdo.station;

import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.pockyl.handson_letsdo.HandsOnLetsDo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The numbers a block's own menu shows (how far it is, how hot, how full), which every station block of the series keeps in a
 * {@link ContainerData} of its own. It is found in the block entity by its type, so nothing of the block's mod is named and the
 * block stays that mod's in every way: the station only looks into it.
 */
public final class BlockNumbers {
    /** Where each class of block entity keeps its numbers; empty for one that has none. */
    private static final Map<Class<?>, Optional<Field>> FIELDS = new ConcurrentHashMap<>();

    private BlockNumbers() {
    }

    /** The numbers of {@code entity}, or null for a block that keeps none or does not let them be read. */
    public static ContainerData of(BlockEntity entity) {
        Optional<Field> field = FIELDS.computeIfAbsent(entity.getClass(), BlockNumbers::find);
        if (field.isEmpty()) {
            return null;
        }
        try {
            return (ContainerData) field.get().get(entity);
        } catch (IllegalAccessException | RuntimeException exception) {
            return null;
        }
    }

    private static Optional<Field> find(Class<?> type) {
        for (Class<?> owner = type; owner != null && owner != BlockEntity.class; owner = owner.getSuperclass()) {
            for (Field field : owner.getDeclaredFields()) {
                if (ContainerData.class.isAssignableFrom(field.getType()) && !Modifier.isStatic(field.getModifiers())) {
                    try {
                        field.setAccessible(true);
                        return Optional.of(field);
                    } catch (RuntimeException exception) {
                        HandsOnLetsDo.LOGGER.warn("The numbers of {} cannot be read: it keeps its own menu", type.getName(), exception);
                        return Optional.empty();
                    }
                }
            }
        }
        HandsOnLetsDo.LOGGER.warn("{} is not a block this mod knows: it keeps its own menu", type.getName());
        return Optional.empty();
    }
}
