package com.pockyl.handson_letsdo.station;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.pockyl.handson_letsdo.HandsOnLetsDo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who cooks at a block of Farm &amp; Charm. Its stations remember the player who last opened their menu: a recipe that wants
 * learning cooks only for that player. A cook at the station is that player too, so the block is told the same way, in the field
 * it keeps the player in, found by its type.
 */
public final class BlockOwner {
    /** Where each class of block entity keeps its owner; empty for one that keeps none. */
    private static final Map<Class<?>, Optional<Field>> FIELDS = new ConcurrentHashMap<>();

    private BlockOwner() {
    }

    /** Tells {@code entity} that {@code player} works at it now. */
    public static void set(BlockEntity entity, Player player) {
        Optional<Field> field = FIELDS.computeIfAbsent(entity.getClass(), BlockOwner::find);
        if (field.isPresent()) {
            try {
                field.get().set(entity, player.getUUID());
            } catch (IllegalAccessException | RuntimeException exception) {
                HandsOnLetsDo.LOGGER.debug("The cook of {} could not be set", entity.getClass().getName(), exception);
            }
        }
    }

    /** Who {@code entity} says works at it; null for nobody, or a block that keeps no such player. */
    public static UUID get(BlockEntity entity) {
        Optional<Field> field = FIELDS.computeIfAbsent(entity.getClass(), BlockOwner::find);
        try {
            return field.isPresent() ? (UUID) field.get().get(entity) : null;
        } catch (IllegalAccessException | RuntimeException exception) {
            return null;
        }
    }

    private static Optional<Field> find(Class<?> type) {
        for (Class<?> owner = type; owner != null && owner != BlockEntity.class; owner = owner.getSuperclass()) {
            for (Field field : owner.getDeclaredFields()) {
                if (field.getType() == UUID.class && !Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                    try {
                        field.setAccessible(true);
                        return Optional.of(field);
                    } catch (RuntimeException exception) {
                        return Optional.empty();
                    }
                }
            }
        }
        return Optional.empty();
    }
}
