package com.pockyl.handson_letsdo.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.world.item.ItemDisplayContext;

import com.pockyl.handson_letsdo.client.HandsOnLetsDoClient;

/** Fabric client entry point. */
public final class HandsOnLetsDoFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HandsOnLetsDoClient.init((model, poseStack) -> {
            model.getTransforms().getTransform(ItemDisplayContext.GUI).apply(false, poseStack);
            return model;
        });
    }
}
