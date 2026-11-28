package com.pockyl.handson_letsdo.mixin;

import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import net.minecraft.client.renderer.texture.atlas.sources.SingleFile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

/**
 * Farm &amp; Charm draws its mincer and its crafting bowl with entity models whose textures live outside the block atlas; the scenes
 * draw the same parts as block models, from the block atlas. Their textures are added to it here, each only while a resource pack
 * has it: an atlas file would name them for every player, and the game would log a missing sprite for each where Farm &amp; Charm
 * is not installed.
 */
@Mixin(SpriteSourceList.class)
abstract class EntitySpritesMixin {
    @Unique
    private static final ResourceLocation HANDSON_BLOCKS = ResourceLocation.fromNamespaceAndPath("minecraft", "blocks");
    @Unique
    private static final List<ResourceLocation> HANDSON_SPRITES = List.of(ResourceLocation.fromNamespaceAndPath("farm_and_charm", "entity/mincer"),
            ResourceLocation.fromNamespaceAndPath("farm_and_charm", "entity/crafting_bowl"), ResourceLocation.fromNamespaceAndPath("farm_and_charm", "entity/crafting_bowl_full"),
            ResourceLocation.fromNamespaceAndPath("wok_and_bowl", "entity/wok"));

    @Inject(method = "load", at = @At("RETURN"))
    private static void handson$addEntitySprites(ResourceManager resources, ResourceLocation atlas,
            CallbackInfoReturnable<SpriteSourceList> info) {
        if (!HANDSON_BLOCKS.equals(atlas)) {
            return;
        }
        for (ResourceLocation sprite : HANDSON_SPRITES) {
            if (resources.getResource(sprite.withPrefix("textures/").withSuffix(".png")).isPresent()) {
                ((SpriteSourceListAccessor) info.getReturnValue()).handson$sources().add(new SingleFile(sprite, Optional.empty()));
            }
        }
    }
}
