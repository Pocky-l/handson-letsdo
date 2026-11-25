package com.pockyl.handson_letsdo.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

import com.pockyl.handson_letsdo.HandsOnLetsDo;

/** NeoForge entry point. */
@Mod(HandsOnLetsDo.MOD_ID)
public final class HandsOnLetsDoNeoForge {
    public HandsOnLetsDoNeoForge(Dist dist) {
        HandsOnLetsDo.init(ModList.get()::isLoaded, FMLPaths.CONFIGDIR.get());
        if (dist.isClient()) {
            NeoForgeClient.init();
        }
    }
}
