package com.pockyl.handson_letsdo.mixin;

import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** The sprite sources an atlas is stitched from, as its definition files gave them: {@link EntitySpritesMixin} adds to them. */
@Mixin(SpriteSourceList.class)
public interface SpriteSourceListAccessor {
    @Accessor("sources")
    List<SpriteSource> handson$sources();
}
