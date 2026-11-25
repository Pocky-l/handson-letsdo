package com.pockyl.handson_letsdo.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.satisfy.farm_and_charm.core.block.MincerBlock;
import net.satisfy.farm_and_charm.core.block.entity.MincerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_letsdo.station.MincerHand;

/**
 * Farm &amp; Charm's mincer at the station ({@link MincerHand}): what the cook minced lies under the spout until taken (the mincer
 * throws its output out on every tick), and the mincer tells the clients near it what it holds whenever that changes (it answers
 * them with its save, but sends it only when its block state changes: hoppers and its own use change the slots without one). A
 * player who cranks it the way Farm &amp; Charm does gets what lies there thrown out as that mod does it.
 */
@Mixin(MincerBlockEntity.class)
public abstract class MincerMixin implements MincerHand {
    @Unique
    private static final String HANDSON_TAG = "HandsOnMincer";
    @Unique
    private static final int HANDSON_OUTPUT = 1;

    @Shadow(remap = false)
    private NonNullList<ItemStack> stacks;

    @Unique
    private boolean handson$kept;
    @Unique
    private float handson$turned;
    @Unique
    private boolean handson$making;
    /** What the clients were last told, to tell them again only when it changed; 0 before the first look. */
    @Unique
    private int handson$seen;
    /** Whether what it was loaded from carried the station's part: the server it came from has this mod. */
    @Unique
    private boolean handson$heard;

    /** What lies under the spout stays there while the station keeps it, and while the station makes a load. */
    @Inject(method = "dropItemsInOutputSlot", at = @At("HEAD"), cancellable = true, remap = false)
    private void handson$keepOutput(CallbackInfo ci) {
        if (handson$making || handson$kept && !stacks.get(HANDSON_OUTPUT).isEmpty()) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void handson$tick(CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (handson$making || level == null || level.isClientSide()) {
            return;
        }
        BlockState state = self.getBlockState();
        if (handson$kept && (stacks.get(HANDSON_OUTPUT).isEmpty()
                || state.hasProperty(MincerBlock.CRANK) && state.getValue(MincerBlock.CRANK) > 0)) {
            // Taken, or cranked Farm & Charm's way by a player without the station: the mincer is as that mod made it again.
            handson$kept = false;
            self.setChanged();
        }
        if (handson$look() != handson$seen) {
            handson$tell();
        }
    }

    /** What the clients see of the mincer in one number: the slots and whether the output waits under the spout. */
    @Unique
    private int handson$look() {
        int seen = handson$kept ? 3 : 1;
        for (ItemStack stack : stacks) {
            seen = seen * 31 + (stack.isEmpty() ? 0 : System.identityHashCode(stack.getItem()) * 17 + stack.getCount());
        }
        return seen == 0 ? 1 : seen;
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void handson$load(CompoundTag tag, CallbackInfo ci) {
        CompoundTag own = tag.getCompound(HANDSON_TAG);
        handson$heard |= tag.contains(HANDSON_TAG);
        handson$kept = own.getBoolean("Kept");
        handson$turned = own.getFloat("Turned");
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void handson$save(CompoundTag tag, CallbackInfo ci) {
        CompoundTag own = new CompoundTag();
        if (handson$kept) {
            own.putBoolean("Kept", true);
        }
        if (handson$turned != 0.0F) {
            own.putFloat("Turned", handson$turned);
        }
        tag.put(HANDSON_TAG, own);
    }

    @Override
    public boolean handson$kept() {
        return handson$kept && !stacks.get(HANDSON_OUTPUT).isEmpty();
    }

    @Override
    public void handson$setKept(boolean kept) {
        if (kept != handson$kept) {
            handson$kept = kept;
            ((BlockEntity) (Object) this).setChanged();
        }
    }

    @Override
    public float handson$turned() {
        return handson$turned;
    }

    @Override
    public void handson$setTurned(float turned) {
        if (turned != handson$turned) {
            handson$turned = turned;
            ((BlockEntity) (Object) this).setChanged();
        }
    }

    @Override
    public void handson$setMaking(boolean making) {
        handson$making = making;
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
