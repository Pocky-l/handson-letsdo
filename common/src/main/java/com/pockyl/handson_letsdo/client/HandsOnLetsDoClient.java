package com.pockyl.handson_letsdo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.LiquidLook;
import com.pockyl.diegesis.api.client.ParticleStyle;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.station.BarrelStation;
import com.pockyl.handson_letsdo.station.BowlStation;
import com.pockyl.handson_letsdo.station.CookingPotStation;
import com.pockyl.handson_letsdo.station.KettleStation;
import com.pockyl.handson_letsdo.station.MincerStation;
import com.pockyl.handson_letsdo.station.OvenStation;
import com.pockyl.handson_letsdo.station.PressStation;

import java.util.ArrayList;
import java.util.List;

/** Client setup: how each station looks and moves while it is open. */
public final class HandsOnLetsDoClient {
    /** Sugar, flour and the like shaken out over a pot: fine grains that fall straight and are gone in the soup. */
    public static final ResourceLocation GRAIN = HandsOnLetsDo.id("grain");
    /** Thin smoke off an oven that bakes shut, and the shimmer of hot air out of its open mouth. */
    public static final ResourceLocation PAN_SMOKE = HandsOnLetsDo.id("pan_smoke");
    public static final ResourceLocation HEAT = HandsOnLetsDo.id("heat");
    /** Fat spitting off what roasts in a pan. */
    public static final ResourceLocation FAT = HandsOnLetsDo.id("fat");
    /** Hot fat seething round what roasts in a pan: small amber bubbles that pop. */
    public static final ResourceLocation OIL_POP = HandsOnLetsDo.id("oil_pop");
    /**
     * Fine drops of juice raining off the press's plate into its tub, falling at the real pull (pixels a second squared): each style
     * lives as long as one fall takes, the shortest to the longest, so that a drop ends where it meets the juice.
     */
    public static final float JUICE_DROP_GRAVITY = 157.0F;
    private static final int JUICE_DROPS = 8;
    private static final float JUICE_DROP_SHORTEST = 0.19F;
    private static final float JUICE_DROP_STEP = 0.0125F;

    private static GuiModels guiModels = (model, poseStack) -> model;

    private HandsOnLetsDoClient() {
    }

    /**
     * How a loader turns an item model to the way it shows in a slot: applies the GUI transform of {@code model} to
     * {@code poseStack} and gives the model to draw after it (on Forge a model can give another one for the GUI).
     */
    @FunctionalInterface
    public interface GuiModels {
        BakedModel guiModel(BakedModel model, PoseStack poseStack);
    }

    static BakedModel guiModel(BakedModel model, PoseStack poseStack) {
        return guiModels.guiModel(model, poseStack);
    }

    /** The juice drop style {@code index} ({@link #JUICE_DROP_GRAVITY}). */
    static ResourceLocation juiceDrop(int index) {
        return HandsOnLetsDo.id("juice_drop_" + index);
    }

    /** How long a drop of style {@code index} falls, seconds: its whole life. */
    static float juiceDropFall(int index) {
        return JUICE_DROP_SHORTEST + JUICE_DROP_STEP * index;
    }

    /** The juice drop style whose fall from rest is nearest to falling {@code pixels}. */
    static int juiceDropFor(float pixels) {
        float fall = (float) Math.sqrt(2.0F * pixels / JUICE_DROP_GRAVITY);
        return Math.max(0, Math.min(JUICE_DROPS - 1, Math.round((fall - JUICE_DROP_SHORTEST) / JUICE_DROP_STEP)));
    }

    /** Whether the station at {@code pos} leaves its block out of the world now, its scene drawn in its place this frame. */
    public static boolean standsInFor(BlockPos pos) {
        return DiegesisClient.isSceneDrawn(pos) && DiegesisClient.station(pos).map(StationView::isBlockHidden).orElse(false);
    }

    /** Called by each loader after the common setup: only the stations that were made get their looks. */
    @SuppressWarnings("removal")
    public static void init(GuiModels models) {
        guiModels = models;
        // The crafting bowl's liquid: our own still surface, coloured by what was poured in.
        DiegesisClient.registerLiquid(BowlMass.LIQUID, new LiquidLook(0xFFFFFFFF, LiquidLook.LIQUID_SPRITE, false, 0.0F));
        DiegesisClient.registerParticle(GRAIN, ParticleStyle.builder(new ResourceLocation("minecraft", "generic_0")).color(0xFFFFFFFF)
                .size(0.28F, 0.42F).life(0.35F, 0.6F).gravity(55.0F).drag(0.2F).pop().build());
        DiegesisClient.registerParticle(FAT, ParticleStyle.builder(new ResourceLocation("minecraft", "drip_fall")).color(0xFFFFE7A8)
                .size(0.3F, 0.45F).life(0.3F, 0.6F).gravity(75.0F).drag(0.05F).stretch().pop().build());
        for (int i = 0; i < JUICE_DROPS; i++) {
            float fall = juiceDropFall(i);
            DiegesisClient.registerParticle(juiceDrop(i), ParticleStyle.builder(new ResourceLocation("minecraft", "drip_fall"))
                    .color(0xFFD9A441).size(0.3F, 0.45F).life(fall, fall).gravity(JUICE_DROP_GRAVITY).drag(0.0F).stretch().pop().build());
        }
        List<ResourceLocation> popping = new ArrayList<>(List.of(new ResourceLocation("diegesis", "bubble"), new ResourceLocation("diegesis", "bubble")));
        for (int i = 0; i < 5; i++) {
            popping.add(new ResourceLocation("diegesis", "bubble_pop_" + i));
        }
        DiegesisClient.registerParticle(OIL_POP, ParticleStyle.builder(popping).color(0xE6F7C27A).size(0.8F, 1.3F).life(0.25F, 0.5F)
                .drag(1.0F).grow(0.4F).pop().build());
        List<ResourceLocation> steam = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            steam.add(new ResourceLocation("diegesis", "steam_" + i));
        }
        DiegesisClient.registerParticle(PAN_SMOKE, ParticleStyle.builder(steam).color(0x58C8C6C2).size(1.8F, 2.6F).life(1.6F, 2.4F)
                .gravity(-4.0F).drag(0.75F).grow(3.0F).wobble(1.0F).fadeIn(0.3F).build());
        List<ResourceLocation> heat = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            heat.add(HandsOnLetsDo.id("heat_" + i));
        }
        DiegesisClient.registerParticle(HEAT, ParticleStyle.builder(heat).color(0x48FFFFFF)
                .size(3.0F, 4.0F).life(0.8F, 1.3F).gravity(-5.0F).drag(0.6F).grow(0.4F).wobble(0.3F).fadeIn(0.4F).build());
        if (Diegesis.stationType(BarrelStation.ID).isPresent()) {
            DiegesisClient.registerBehavior(BarrelStation.ID, BarrelBehavior::new);
        }
        if (Diegesis.stationType(CookingPotStation.ID).isPresent()) {
            DiegesisClient.registerBehavior(CookingPotStation.ID, PotBehavior::new);
        }
        if (Diegesis.stationType(CookingPotStation.ROASTER_ID).isPresent()) {
            DiegesisClient.registerBehavior(CookingPotStation.ROASTER_ID, view -> new PotBehavior(view, PotBehavior.Shape.ROASTER));
        }
        if (Diegesis.stationType(CookingPotStation.CANDLELIGHT_ID).isPresent()) {
            DiegesisClient.registerBehavior(CookingPotStation.CANDLELIGHT_ID,
                    view -> new PotBehavior(view, PotBehavior.Shape.CANDLELIGHT_POT));
        }
        if (Diegesis.stationType(OvenStation.STOVE_ID).isPresent()) {
            DiegesisClient.registerBehavior(OvenStation.STOVE_ID, OvenBehavior::stove);
        }
        if (Diegesis.stationType(KettleStation.ID).isPresent()) {
            DiegesisClient.registerBehavior(KettleStation.ID, KettleBehavior::new);
        }
        if (Diegesis.stationType(PressStation.ID).isPresent()) {
            DiegesisClient.registerBehavior(PressStation.ID, PressBehavior::new);
        }
        if (Diegesis.stationType(MincerStation.ID).isPresent()) {
            DiegesisClient.registerBehavior(MincerStation.ID, MincerBehavior::new);
        }
        if (Diegesis.stationType(BowlStation.ID).isPresent()) {
            DiegesisClient.registerBehavior(BowlStation.ID, BowlBehavior::new);
        }
    }
}
