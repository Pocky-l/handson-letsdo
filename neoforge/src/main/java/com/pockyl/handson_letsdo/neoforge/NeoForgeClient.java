package com.pockyl.handson_letsdo.neoforge;

import net.minecraft.world.item.ItemDisplayContext;

import com.pockyl.handson_letsdo.client.HandsOnLetsDoClient;

/** NeoForge client entry point, loaded on a client only. */
final class NeoForgeClient {
    private NeoForgeClient() {
    }

    static void init() {
        HandsOnLetsDoClient.init((model, poseStack) -> model.applyTransform(ItemDisplayContext.GUI, poseStack, false));
    }
}
