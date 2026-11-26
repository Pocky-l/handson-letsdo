package com.pockyl.handson_letsdo.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.satisfy.candlelight.core.block.entity.LargeCookingPotBlockEntity;
import net.satisfy.farm_and_charm.core.block.entity.CookingPotBlockEntity;
import net.satisfy.farm_and_charm.core.block.entity.RoasterBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_letsdo.station.PotCooking;
import com.pockyl.handson_letsdo.station.StirredPot;

/**
 * Farm &amp; Charm's pot, its roaster and Candlelight's pot, which tick alike, wait for the spoon: their own tick is held back while
 * a cook's batch waits to be stirred or cooks under the spoon, and while a finished dish waits for its bowls ({@link
 * PotCooking#holds}). Nothing inside their cooking is touched: every serving is still made by that tick, and with nobody at the
 * station they are their own mod's. And they tell the clients near them what they hold (their mods tell them nothing), whenever it
 * changes, so that every client shows the pot as its station does with nobody at it ({@code BlockEntityUpdateMixin} sends it).
 */
@Pseudo
@Mixin({CookingPotBlockEntity.class, RoasterBlockEntity.class, LargeCookingPotBlockEntity.class})
public abstract class StirredPotMixin implements StirredPot {
    /** In what the clients are sent only, never saved: the server that sent it has this mod. */
    @Unique
    private static final String HANDSON_TOLD = "HandsOnTold";

    @Shadow(remap = false)
    @Final
    private NonNullList<ItemStack> inventory;

    @Unique
    private PotCooking handson$cooking;
    /** What the clients were last told, to tell them again only when it changed; 0 before the first look. */
    @Unique
    private int handson$shown;
    /** On a client's copy: the server told it what the pot holds. */
    @Unique
    private boolean handson$told;

    @Override
    public PotCooking handson$cooking() {
        if (handson$cooking == null) {
            handson$cooking = new PotCooking((BlockEntity) (Object) this);
        }
        return handson$cooking;
    }

    @Override
    public boolean handson$told() {
        return handson$told;
    }

    /** By its name only: the pot's tick is its own method, named alike on every loader, and its bridge asks the same thing again. */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void handson$waitForTheSpoon(CallbackInfo ci) {
        boolean holds = handson$cooking().holds();
        handson$tell();
        if (holds) {
            ci.cancel();
        }
    }

    /** On the server: the clients near are told when what they show of the pot changed, what lies in it and the soup that waits. */
    @Unique
    private void handson$tell() {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        int seen = handson$cooking().shown();
        if (seen == 0) {
            return;
        }
        for (ItemStack stack : inventory) {
            seen = seen * 31 + (stack.isEmpty() ? 0 : System.identityHashCode(stack.getItem()) * 17 + stack.getCount());
        }
        seen = seen == 0 ? 1 : seen;
        if (seen != handson$shown) {
            handson$shown = seen;
            level.sendBlockUpdated(self.getBlockPos(), self.getBlockState(), self.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** The pot reads only the slots that hold something: an emptied one must not keep what it held on a client's copy. */
    @Inject(method = "load", at = @At("HEAD"), require = 0)
    private void handson$clear(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("Items", Tag.TAG_LIST)) {
            for (int i = 0; i < inventory.size(); i++) {
                inventory.set(i, ItemStack.EMPTY);
            }
        }
    }

    @Inject(method = "load", at = @At("TAIL"), require = 0)
    private void handson$load(CompoundTag tag, CallbackInfo ci) {
        handson$cooking().load(tag);
        handson$told |= tag.getBoolean(HANDSON_TOLD);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), require = 0)
    private void handson$save(CompoundTag tag, CallbackInfo ci) {
        handson$cooking().save(tag);
    }

    /** What the clients are sent of the pot: everything it saves, and that this server has the station. */
    @Override
    public CompoundTag handson$updateTag() {
        CompoundTag tag = ((BlockEntity) (Object) this).saveWithoutMetadata();
        tag.putBoolean(HANDSON_TOLD, true);
        return tag;
    }
}
