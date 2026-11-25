package com.pockyl.handson_letsdo.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.station.HerbalTeas;

/**
 * The tea kettle's sounds, as Hands-On Delight's kettle has them: the boil is HerbalBrews' own clip by its id (its kettle's
 * boiling), the whistle of a brew done is Hands-On Delight's own, ours (HerbalBrews has none). Played on the client by name: no
 * registry entry is needed.
 */
final class KettleSounds {
    @SuppressWarnings("removal")
    static final SoundEvent BOIL = SoundEvent.createVariableRangeEvent(new ResourceLocation(HerbalTeas.MOD_ID, "tea_kettle_boiling"));
    static final SoundEvent WHISTLE = SoundEvent.createVariableRangeEvent(HandsOnLetsDo.id("kettle.whistle"));

    private KettleSounds() {
    }
}
