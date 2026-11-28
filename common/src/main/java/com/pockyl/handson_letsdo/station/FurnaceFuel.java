package com.pockyl.handson_letsdo.station;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * How long a furnace burns a stack, asked the loader's way, the way its furnace asks. Never through
 * {@code AbstractFurnaceBlockEntity.getFuel()}: Forge's furnace asks {@code ForgeHooks} instead, so that list misses the items
 * that give their own burn time, and other mods' mixins into it may break when someone calls it (Decorative Blocks crashed the
 * client on NeoForge 1.21.1 when a stove was opened).
 */
public final class FurnaceFuel {
    private static ToIntFunction<ItemStack> burnTime = stack -> 0;

    private FurnaceFuel() {
    }

    /** Set by the loader project during mod setup. */
    public static void use(ToIntFunction<ItemStack> loaderBurnTime) {
        burnTime = Objects.requireNonNull(loaderBurnTime);
    }

    /** Ticks a furnace burns this stack for; 0 when it does not burn. */
    public static int burnTime(ItemStack stack) {
        return stack.isEmpty() ? 0 : burnTime.applyAsInt(stack);
    }
}
