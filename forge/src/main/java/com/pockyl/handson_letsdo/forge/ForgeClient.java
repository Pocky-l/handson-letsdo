package com.pockyl.handson_letsdo.forge;

import net.minecraft.world.item.ItemDisplayContext;

import com.pockyl.handson_letsdo.client.HandsOnLetsDoClient;

/** Forge client entry point, loaded on a client only. */
final class ForgeClient {
    private ForgeClient() {
    }

    static void init() {
        HandsOnLetsDoClient.init((model, poseStack) -> model.applyTransform(ItemDisplayContext.GUI, poseStack, false));
    }
}
