package com.pockyl.handson_letsdo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_letsdo.client.HandsOnLetsDoClient;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.handson_letsdo.station.BowlStation;
import com.pockyl.handson_letsdo.station.CuttingBoardStation;
import com.pockyl.handson_letsdo.station.WokStation;
import com.pockyl.handson_letsdo.station.FarmCharmStove;
import com.pockyl.handson_letsdo.station.MincerStation;

/**
 * Where a scene stands in for a block whose own renderer draws what lies in it, the scene draws that itself, so that it can
 * slide out, brown and be taken; the block's renderer would draw it a second time in the old place. It is stopped here, at the
 * game's dispatcher, and not in the other mod's renderer: any mod can register a renderer of its own for these blocks. Farm
 * &amp; Charm's stove draws its ingredients and its dish in its mouth; its mincer and its crafting bowl are drawn by their renderers
 * alone, the whole of them.
 * Only while the scene is drawn in the block's place, which for each is wherever its scene is drawn: further off, they are as
 * their own renderers draw them.
 */
@Mixin(BlockEntityRenderDispatcher.class)
abstract class StationRenderersMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void handson$drawnByScene(BlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            CallbackInfo callback) {
        boolean drawnByScene = FarmCharmStove.isStove(blockEntity) || MincerStation.isMincer(blockEntity) || BowlStation.isBowl(blockEntity);
        if (drawnByScene && HandsOnLetsDoClient.standsInFor(blockEntity.getBlockPos())) {
            callback.cancel();
        }
        // The board keeps its block; what lies on it and the tool stuck in are the scene's wherever it is drawn.
        if ((CuttingBoardStation.isBoard(blockEntity) || WokStation.isWok(blockEntity)) && DiegesisClient.isSceneDrawn(blockEntity.getBlockPos())) {
            callback.cancel();
        }
    }
}
