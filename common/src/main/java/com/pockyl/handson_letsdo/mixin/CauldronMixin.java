package com.pockyl.handson_letsdo.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.satisfy.herbalbrews.core.blocks.entity.CauldronBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_letsdo.station.CauldronBrews;
import com.pockyl.handson_letsdo.station.CauldronStation;
import com.pockyl.handson_letsdo.station.CauldronTold;

/**
 * HerbalBrews' brewing cauldron tells the clients near it what it holds and how far its brew is (HerbalBrews tells them nothing),
 * whenever that changes: a potion, the infusion or the flask comes or goes, a brew starts, or stirring hurries it. Not every tick
 * its count goes on: a client counts on by itself from what it was told. Its brewing, its menu and its hoppers stay as they are.
 */
@Pseudo
@Mixin(CauldronBlockEntity.class)
public abstract class CauldronMixin implements CauldronTold {
    /** In what the clients are sent only, never saved: the server that sent it has this mod. */
    @Unique
    private static final String HANDSON_TOLD = "HandsOnTold";

    @Shadow(remap = false)
    private NonNullList<ItemStack> inventory;
    @Shadow(remap = false)
    private int brewingTime;
    @Shadow(remap = false)
    private int totalBrewingTime;

    /** What the clients were last told, to tell them again only when it changed; 0 before the first look. */
    @Unique
    private int handson$shown;
    /** On a client's copy: the server told it what the cauldron holds; when, Long.MIN_VALUE until it is first asked. */
    @Unique
    private boolean handson$told;
    @Unique
    private long handson$toldAt = Long.MIN_VALUE;

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

    /**
     * After HerbalBrews' tick, on the server. By its name only: the tick is the cauldron's own method, named alike on every loader,
     * and its bridge asks the same thing again.
     */
    @Inject(method = "tick", at = @At("RETURN"), remap = false, require = 0)
    private void handson$tell(CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide() || !CauldronStation.isRegistered()) {
            return;
        }
        int seen = 1 + totalBrewingTime * 7;
        if (brewingTime > 0) {
            // When the brew began: the same every tick while it counts by itself, earlier once it was stirred.
            seen = seen * 31 + Long.hashCode(level.getGameTime() - brewingTime);
        }
        for (ItemStack stack : inventory) {
            seen = seen * 31 + (stack.isEmpty() ? 0 : CauldronBrews.hash(stack) * 17 + stack.getCount());
        }
        seen = seen == 0 ? 1 : seen;
        if (seen != handson$shown) {
            handson$shown = seen;
            level.sendBlockUpdated(self.getBlockPos(), self.getBlockState(), self.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** A client's copy notes that it was told, and from when it counts the brew on. The cauldron makes its slots anew as it loads. */
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
