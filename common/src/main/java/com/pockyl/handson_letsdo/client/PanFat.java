package com.pockyl.handson_letsdo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.pockyl.diegesis.api.client.NodePainter;
import com.pockyl.handson_letsdo.HandsOnLetsDo;

/**
 * The fat a roast lets out where it lies, painted at the node {@code fat} on the roaster's floor, under the oil that comes later: a
 * flat amber puddle under each thing, as on Hands-On Delight's skillet, running out while it roasts, left behind where a piece slid
 * off, shimmering a little as hot fat does. Pan pixels from the floor's middle.
 */
final class PanFat implements NodePainter {
    /** A block-atlas sprite: the scene keeps everything see-through on the block sheet, in one sorted batch. */
    private static final ResourceLocation SPRITE = HandsOnLetsDo.id("block/oil_film");
    /** The film's own pale amber deepened to golden: on the roaster's red enamel the pale one reads as pink. */
    private static final int GOLDEN = 0xFFBE50;
    /** How much the light on a puddle breathes. */
    private static final float SHIMMER = 0.12F;
    /** Each puddle a hair over the one before: see-through quads in one plane would flicker. */
    private static final float LAYER = 0.004F;

    private float[][] puddles = new float[0][];
    private float clock;

    /** The puddles now: x, z, size across and how far each has run out (0 to 1). */
    void puddles(float[][] puddles) {
        this.puddles = puddles;
    }

    void frame(float dt) {
        clock += dt;
    }

    @Override
    public void paint(PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (puddles.length == 0) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(Sheets.translucentCullBlockSheet());
        TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(SPRITE);
        for (int i = 0; i < puddles.length; i++) {
            float[] puddle = puddles[i];
            float shimmer = 1.0F - SHIMMER + SHIMMER * Mth.sin(clock * 2.6F + i * 1.9F);
            int alpha = Mth.clamp(Math.round(255.0F * puddle[3] * shimmer), 0, 255);
            if (alpha == 0) {
                continue;
            }
            float half = puddle[2] * (0.55F + 0.45F * Mth.sqrt(puddle[3])) / 2.0F;
            poseStack.pushPose();
            poseStack.translate(puddle[0], LAYER * i, puddle[1]);
            // Quarter turns only: pixels turned by any other angle break the grid they share with the pan.
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F * (i & 3)));
            Matrix4f pose = poseStack.last().pose();
            Matrix3f normal = poseStack.last().normal();
            vertex(consumer, pose, normal, -half, -half, sprite.getU0(), sprite.getV0(), alpha, light);
            vertex(consumer, pose, normal, -half, half, sprite.getU0(), sprite.getV1(), alpha, light);
            vertex(consumer, pose, normal, half, half, sprite.getU1(), sprite.getV1(), alpha, light);
            vertex(consumer, pose, normal, half, -half, sprite.getU1(), sprite.getV0(), alpha, light);
            poseStack.popPose();
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, float x, float z, float u, float v, int alpha, int light) {
        consumer.addVertex(pose, x, 0.0F, z).setColor(GOLDEN >> 16 & 0xFF, GOLDEN >> 8 & 0xFF, GOLDEN & 0xFF, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(normal.m10(), normal.m11(), normal.m12());
    }
}
