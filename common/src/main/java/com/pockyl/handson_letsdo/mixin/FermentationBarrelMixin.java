package com.pockyl.handson_letsdo.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.satisfy.vinery.core.block.entity.FermentationBarrelBlockEntity;
import net.satisfy.vinery.core.recipe.FermentationBarrelRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.handson_letsdo.station.BarrelBrew;
import com.pockyl.handson_letsdo.station.BrewingBarrel;

/**
 * Vinery's barrel ferments as the station does: everything that lies in it ferments together in one wine's time, and the wine
 * waits in the barrel until it is drawn into bottles; no bottle is needed before, as no cellar fills its bottles before the wine
 * is made. Vinery's own fermenting,
 * one bottle at a time and only with an empty bottle in the barrel, is left out while the station is on; its pouring of juice
 * bottles, its slots, menu and hoppers stay as they are. {@link BarrelBrew} does the rest. A barrel ferments only shut: while a
 * cook has its hatch off, the count waits ({@link BrewingBarrel#handson$openUntil}).
 */
@Mixin(FermentationBarrelBlockEntity.class)
public abstract class FermentationBarrelMixin implements BrewingBarrel {
    @Unique
    private static final String HANDSON_TAG = "HandsOnBrew";

    @Shadow(remap = false)
    private int fermentationTime;

    @Unique
    private int handson$brewTime;
    @Unique
    private String handson$wine = "";
    @Unique
    private int handson$portions;
    @Unique
    private long handson$openUntil = Long.MIN_VALUE;

    /** Vinery never makes a wine itself while the station is on: the station's fermenting does. */
    @Inject(method = "canCraft", at = @At("HEAD"), cancellable = true, remap = false)
    private void handson$brewsInstead(FermentationBarrelRecipe recipe, RegistryAccess access, CallbackInfoReturnable<Boolean> cir) {
        if (BarrelBrew.active()) {
            cir.setReturnValue(false);
        }
    }

    /** After Vinery's tick (it pours the juice bottles in), the station's: fermenting, and bottling for hoppers and the menu. */
    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private static void handson$brew(Level level, BlockPos pos, FermentationBarrelBlockEntity barrel, CallbackInfo ci) {
        if (BarrelBrew.active()) {
            BarrelBrew.tick(level, barrel);
        }
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void handson$load(CompoundTag tag, CallbackInfo ci) {
        CompoundTag own = tag.getCompound(HANDSON_TAG);
        handson$brewTime = own.getInt("Time");
        handson$wine = own.getString("Wine");
        handson$portions = own.getInt("Portions");
        if (handson$portions <= 0) {
            handson$wine = "";
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void handson$save(CompoundTag tag, CallbackInfo ci) {
        if (handson$brewTime <= 0 && handson$portions <= 0) {
            return;
        }
        CompoundTag own = new CompoundTag();
        own.putInt("Time", handson$brewTime);
        if (handson$portions > 0) {
            own.putString("Wine", handson$wine);
            own.putInt("Portions", handson$portions);
        }
        tag.put(HANDSON_TAG, own);
    }

    @Override
    public int handson$brewTime() {
        return handson$brewTime;
    }

    /** Vinery's menu shows the station's count: its arrow fills as the wine ferments. */
    @Override
    public void handson$setBrewTime(int ticks) {
        handson$brewTime = Math.max(0, ticks);
        fermentationTime = handson$brewTime;
    }

    @Override
    public String handson$wine() {
        return handson$wine;
    }

    @Override
    public int handson$portions() {
        return handson$portions;
    }

    @Override
    public void handson$setWine(String recipe, int portions) {
        handson$portions = Math.max(0, portions);
        handson$wine = handson$portions > 0 && recipe != null ? recipe : "";
    }

    @Override
    public long handson$openUntil() {
        return handson$openUntil;
    }

    @Override
    public void handson$setOpenUntil(long gameTime) {
        handson$openUntil = gameTime;
    }
}
