package com.pockyl.handson_letsdo.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.satisfy.farm_and_charm.core.block.entity.CraftingBowlBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_letsdo.station.BowlHand;

/**
 * Farm &amp; Charm's crafting bowl at the station ({@link BowlHand}): it tells the clients near it what it holds whenever that changes
 * (it answers them with its save, but sends it only when its block state changes: hoppers and its own use change the slots without
 * one), and keeps how far the whisk has gone round on what lies in it.
 */
@Mixin(CraftingBowlBlockEntity.class)
public abstract class CraftingBowlMixin implements BowlHand {
    @Unique
    private static final String HANDSON_TAG = "HandsOnBowl";

    @Shadow(remap = false)
    private NonNullList<ItemStack> stacks;

    @Unique
    private float handson$stirred;
    /** What the clients were last told, to tell them again only when it changed; 0 before the first look. */
    @Unique
    private int handson$seen;
    /** Whether what it was loaded from carried the station's part: the server it came from has this mod. */
    @Unique
    private boolean handson$heard;

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void handson$tick(CallbackInfo ci) {
        Level level = ((BlockEntity) (Object) this).getLevel();
        if (level != null && !level.isClientSide() && handson$look() != handson$seen) {
            handson$tell();
        }
    }

    /** What the clients see of the bowl in one number: its slots. */
    @Unique
    private int handson$look() {
        int seen = 1;
        for (ItemStack stack : stacks) {
            seen = seen * 31 + (stack.isEmpty() ? 0 : System.identityHashCode(stack.getItem()) * 17 + stack.getCount());
        }
        return seen == 0 ? 1 : seen;
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void handson$load(CompoundTag tag, CallbackInfo ci) {
        handson$heard |= tag.contains(HANDSON_TAG);
        handson$stirred = tag.getCompound(HANDSON_TAG).getFloat("Stirred");
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void handson$save(CompoundTag tag, CallbackInfo ci) {
        CompoundTag own = new CompoundTag();
        if (handson$stirred != 0.0F) {
            own.putFloat("Stirred", handson$stirred);
        }
        tag.put(HANDSON_TAG, own);
    }

    @Override
    public float handson$stirred() {
        return handson$stirred;
    }

    @Override
    public void handson$setStirred(float stirred) {
        if (stirred != handson$stirred) {
            handson$stirred = stirred;
            ((BlockEntity) (Object) this).setChanged();
        }
    }

    @Override
    public void handson$tell() {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level != null && !level.isClientSide()) {
            handson$seen = handson$look();
            level.sendBlockUpdated(self.getBlockPos(), self.getBlockState(), self.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public boolean handson$told() {
        return handson$heard;
    }
}
