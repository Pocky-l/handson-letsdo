package com.pockyl.handson_letsdo.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.handson_letsdo.station.CauldronStation;
import com.pockyl.handson_letsdo.station.CauldronTold;
import com.pockyl.handson_letsdo.station.PressBasket;
import com.pockyl.handson_letsdo.station.StirredPot;

/**
 * The block entities this mod makes tell their clients send what they save to the clients, on loading the chunk and on every
 * {@code sendBlockUpdated}: Vinery's press ({@link PressBasket}), Farm &amp; Charm's pot and roaster ({@link StirredPot}) and HerbalBrews'
 * cauldron while its station is on ({@link CauldronTold}), which declare neither method, so they are answered here for them. Every
 * other block entity is left as it is.
 */
@Mixin(BlockEntity.class)
abstract class BlockEntityUpdateMixin {
    @Inject(method = "getUpdateTag", at = @At("HEAD"), cancellable = true)
    private void handson$tag(HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> info) {
        if (this instanceof PressBasket) {
            info.setReturnValue(((BlockEntity) (Object) this).saveWithoutMetadata(registries));
        } else if (this instanceof StirredPot pot) {
            info.setReturnValue(pot.handson$updateTag(registries));
        } else if (this instanceof CauldronTold cauldron && CauldronStation.isRegistered()) {
            info.setReturnValue(cauldron.handson$updateTag(registries));
        }
    }

    @Inject(method = "getUpdatePacket", at = @At("HEAD"), cancellable = true)
    private void handson$packet(CallbackInfoReturnable<Packet<ClientGamePacketListener>> info) {
        if (this instanceof PressBasket || this instanceof StirredPot || this instanceof CauldronTold && CauldronStation.isRegistered()) {
            info.setReturnValue(ClientboundBlockEntityDataPacket.create((BlockEntity) (Object) this));
        }
    }
}
