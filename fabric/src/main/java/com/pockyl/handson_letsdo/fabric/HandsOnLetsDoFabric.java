package com.pockyl.handson_letsdo.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import com.pockyl.handson_letsdo.HandsOnLetsDo;

/** Fabric entry point, on both sides. */
public final class HandsOnLetsDoFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        HandsOnLetsDo.init(FabricLoader.getInstance()::isModLoaded, FabricLoader.getInstance().getConfigDir());
    }
}
