package com.pockyl.handson_letsdo.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import com.pockyl.handson_letsdo.HandsOnLetsDo;

/**
 * The pot's sounds. Sounds played on the client need no registry entry: the sound manager finds them by name in sounds.json.
 * The boiling and the sizzle are Farm &amp; Charm's own clips, by their ids.
 */
final class PotSounds {
    static final SoundEvent BODY = event("pot.body");
    static final SoundEvent STIR = event("pot.stir");
    static final SoundEvent PLOP = event("pot.plop");
    static final SoundEvent BURST = event("pot.burst");
    static final SoundEvent BLUB = event("pot.blub");
    static final SoundEvent SCOOP = event("pot.scoop");
    static final SoundEvent BOIL = farmCharm("cooking_pot_boiling");
    static final SoundEvent SIZZLE = farmCharm("roaster_cooking");

    private PotSounds() {
    }

    private static SoundEvent event(String name) {
        return SoundEvent.createVariableRangeEvent(HandsOnLetsDo.id(name));
    }

    private static SoundEvent farmCharm(String name) {
        return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("farm_and_charm", name));
    }
}
