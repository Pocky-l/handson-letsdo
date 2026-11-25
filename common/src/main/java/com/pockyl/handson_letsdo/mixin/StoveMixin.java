package com.pockyl.handson_letsdo.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.satisfy.farm_and_charm.core.block.entity.StoveBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_letsdo.HandsOnConfig;
import com.pockyl.handson_letsdo.station.FarmCharmStove;

/**
 * Farm &amp; Charm 1.1.x's stove stops with two alike on the tray before the last set: before each of its ticks, what lies there alike
 * is laid the way it bakes on ({@link FarmCharmStove#layAlike(BlockEntity)}). Its baking, fuel and menu stay as they are.
 */
@Mixin(StoveBlockEntity.class)
abstract class StoveMixin {
    /** By its name only: the stove's tick is its own method, named alike on every loader, and its bridge asks the same thing again. */
    @Inject(method = "tick", at = @At("HEAD"), remap = false, require = 0)
    private void handson$layAlike(CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level != null && !level.isClientSide() && HandsOnConfig.stove()) {
            FarmCharmStove.layAlike(self);
        }
    }
}
