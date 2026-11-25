package com.pockyl.handson_letsdo.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import com.pockyl.handson_letsdo.HandsOnLetsDo;

/** Forge entry point. */
@Mod(HandsOnLetsDo.MOD_ID)
public final class HandsOnLetsDoForge {
    public HandsOnLetsDoForge() {
        HandsOnLetsDo.init(ModList.get()::isLoaded, FMLPaths.CONFIGDIR.get());
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ForgeClient::init);
    }
}
