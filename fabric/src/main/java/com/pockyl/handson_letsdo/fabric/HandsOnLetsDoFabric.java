package com.pockyl.handson_letsdo.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.loader.api.FabricLoader;

import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.station.FurnaceFuel;

/** Fabric entry point, on both sides. */
public final class HandsOnLetsDoFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FurnaceFuel.use(stack -> {
            Integer ticks = FuelRegistry.INSTANCE.get(stack.getItem());
            return ticks == null ? 0 : ticks;
        });
        HandsOnLetsDo.init(FabricLoader.getInstance()::isModLoaded, FabricLoader.getInstance().getConfigDir());
    }
}
