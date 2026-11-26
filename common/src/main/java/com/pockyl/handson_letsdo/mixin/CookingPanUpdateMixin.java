package com.pockyl.handson_letsdo.mixin;

import net.minecraft.nbt.CompoundTag;
import net.satisfy.candlelight.core.block.entity.CookingPanBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.handson_letsdo.station.StirredPot;

/**
 * Candlelight's cooking pan sends its clients what it saves by a method of its own, past {@code BlockEntityUpdateMixin}: it is
 * answered here as the other pots are there, with everything it saves and the word that this server has the station, so that every
 * client shows the pan as its station does with nobody at it ({@link StirredPot}).
 */
@Pseudo
@Mixin(CookingPanBlockEntity.class)
abstract class CookingPanUpdateMixin {
    @Inject(method = "getUpdateTag", at = @At("HEAD"), cancellable = true, require = 0)
    private void handson$tag(CallbackInfoReturnable<CompoundTag> info) {
        if ((Object) this instanceof StirredPot pot) {
            info.setReturnValue(pot.handson$updateTag());
        }
    }
}
