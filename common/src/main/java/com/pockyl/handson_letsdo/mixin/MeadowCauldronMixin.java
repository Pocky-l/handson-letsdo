package com.pockyl.handson_letsdo.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.satisfy.meadow.core.block.entity.CookingCauldronBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_letsdo.station.CauldronTold;
import com.pockyl.handson_letsdo.station.MeadowCauldronStation;

/**
 * Meadow's cooking cauldron tells the clients near it what it holds, how much water and how far its dish is (Meadow tells them
 * nothing), whenever that changes: food, water or the dish comes or goes, a dish starts, or stirring hurries it. Not every tick
 * its count goes on: a client counts on by itself from what it was told. Its cooking, its menu and its hoppers stay as they are.
 */
@Pseudo
@Mixin(CookingCauldronBlockEntity.class)
public abstract class MeadowCauldronMixin implements CauldronTold {
    /** In what the clients are sent only, never saved: the server that sent it has this mod. */
    @Unique
    private static final String HANDSON_TOLD = "HandsOnTold";

    @Shadow(remap = false)
    private NonNullList<ItemStack> inventory;
    @Shadow(remap = false)
    private int cookingTime;
    @Shadow(remap = false)
    private int fluidLevel;
    @Shadow(remap = false)
    private int currentCraftingDuration;

    /** What the clients were last told, to tell them again only when it changed; 0 before the first look. */
    @Unique
    private int handson$shown;
    /** On a client's copy: the server told it what the cauldron holds; when, Long.MIN_VALUE until it is first asked. */
    @Unique
    private boolean handson$told;
    @Unique
    private long handson$toldAt = Long.MIN_VALUE;

    @Override
    public boolean handson$tells() {
        return MeadowCauldronStation.isRegistered();
    }

    @Override
    public boolean handson$told() {
        return handson$told;
    }

    @Override
    public long handson$toldAt(long now) {
        if (handson$toldAt == Long.MIN_VALUE || handson$toldAt > now) {
            handson$toldAt = now;
        }
        return handson$toldAt;
    }

    /** After Meadow's tick, on the server. By its name only: the tick is the cauldron's own method, named alike on every loader. */
    @Inject(method = "tick", at = @At("RETURN"), remap = false, require = 0)
    private void handson$tell(CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide() || !MeadowCauldronStation.isRegistered()) {
            return;
        }
        int seen = 1 + currentCraftingDuration * 7 + fluidLevel * 131;
        if (currentCraftingDuration > 0) {
            // When the dish began: the same every tick while it counts by itself, earlier once it was stirred.
            seen = seen * 31 + Long.hashCode(level.getGameTime() - cookingTime);
        }
        for (ItemStack stack : inventory) {
            seen = seen * 31 + (stack.isEmpty() ? 0
                    : (stack.getItem().hashCode() * 31 + (stack.getTag() == null ? 0 : stack.getTag().hashCode())) * 17 + stack.getCount());
        }
        seen = seen == 0 ? 1 : seen;
        if (seen != handson$shown) {
            handson$shown = seen;
            level.sendBlockUpdated(self.getBlockPos(), self.getBlockState(), self.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * What the server tells a client's copy is all it holds: Meadow's loading fills the slots the tag names and leaves the rest as
     * they were, so a slot emptied on the server would stay full on the client. Only for what the server sent, never for a save.
     */
    @Inject(method = "load", at = @At("HEAD"), require = 0)
    private void handson$clear(CompoundTag tag, CallbackInfo ci) {
        if (tag.getBoolean(HANDSON_TOLD)) {
            for (int slot = 0; slot < inventory.size(); slot++) {
                inventory.set(slot, ItemStack.EMPTY);
            }
        }
    }

    /** A client's copy notes that it was told, and from when it counts the dish on. */
    @Inject(method = "load", at = @At("TAIL"), require = 0)
    private void handson$load(CompoundTag tag, CallbackInfo ci) {
        if (tag.getBoolean(HANDSON_TOLD)) {
            handson$told = true;
            handson$toldAt = Long.MIN_VALUE;
        }
    }

    @Override
    public CompoundTag handson$updateTag() {
        CompoundTag tag = ((BlockEntity) (Object) this).saveWithoutMetadata();
        tag.putBoolean(HANDSON_TOLD, true);
        return tag;
    }
}
