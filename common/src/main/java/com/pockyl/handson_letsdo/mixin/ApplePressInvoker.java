package com.pockyl.handson_letsdo.mixin;

import net.satisfy.vinery.core.block.entity.ApplePressBlockEntity;
import net.satisfy.vinery.core.recipe.ApplePressFermentingRecipe;
import net.satisfy.vinery.core.recipe.ApplePressMashingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Vinery's own steps of the press, called by {@code ApplePressMixin} when the cook presses by hand: each fruit is pressed and each
 * bottle filled by the press's own code, only sooner than its tick would.
 */
@Mixin(ApplePressBlockEntity.class)
public interface ApplePressInvoker {
    @Invoker(value = "canProcessMashing", remap = false)
    static boolean handson$canMash(ApplePressBlockEntity press, ApplePressMashingRecipe recipe) {
        throw new AssertionError();
    }

    @Invoker(value = "processMashing", remap = false)
    static void handson$mash(ApplePressBlockEntity press, ApplePressMashingRecipe recipe) {
        throw new AssertionError();
    }

    @Invoker(value = "canProcessFermenting", remap = false)
    static boolean handson$canBottle(ApplePressBlockEntity press, ApplePressFermentingRecipe recipe) {
        throw new AssertionError();
    }

    @Invoker(value = "processFermenting", remap = false)
    static void handson$bottle(ApplePressBlockEntity press, ApplePressFermentingRecipe recipe) {
        throw new AssertionError();
    }
}
