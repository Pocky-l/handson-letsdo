package com.pockyl.handson_letsdo.neoforge;

import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.station.FurnaceFuel;

/** NeoForge entry point. */
@Mod(HandsOnLetsDo.MOD_ID)
public final class HandsOnLetsDoNeoForge {
    public HandsOnLetsDoNeoForge(Dist dist) {
        FurnaceFuel.use(stack -> stack.getBurnTime(RecipeType.SMELTING));
        HandsOnLetsDo.init(ModList.get()::isLoaded, FMLPaths.CONFIGDIR.get());
        if (dist.isClient()) {
            NeoForgeClient.init();
        }
    }
}
