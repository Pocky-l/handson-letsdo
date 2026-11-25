package com.pockyl.handson_letsdo.mixin;

import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** The sprite sources an atlas is stitched from, as its definition files gave them: {@link EntitySpritesMixin} adds to them. */
@Mixin(SpriteResourceLoader.class)
public interface SpriteResourceLoaderAccessor {
    @Accessor("sources")
    List<SpriteSource> handson$sources();
}
