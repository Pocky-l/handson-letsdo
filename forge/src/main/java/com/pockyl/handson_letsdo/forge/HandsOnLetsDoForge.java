package com.pockyl.handson_letsdo.forge;

import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.station.FurnaceFuel;

/** Forge entry point. */
@Mod(HandsOnLetsDo.MOD_ID)
public final class HandsOnLetsDoForge {
    public HandsOnLetsDoForge() {
        FurnaceFuel.use(stack -> ForgeHooks.getBurnTime(stack, RecipeType.SMELTING));
        HandsOnLetsDo.init(ModList.get()::isLoaded, FMLPaths.CONFIGDIR.get());
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ForgeClient::init);
    }
}
