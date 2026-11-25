package com.pockyl.handson_letsdo.client;

import net.minecraft.sounds.SoundEvent;

import com.pockyl.handson_letsdo.HandsOnLetsDo;

/** The barrel's sounds. Sounds played on the client need no registry entry: the sound manager finds them by name in sounds.json. */
final class BarrelSounds {
    static final SoundEvent PLOP = event("barrel.plop");
    static final SoundEvent BURST = event("barrel.burst");
    static final SoundEvent BLUB = event("barrel.blub");
    /** Wine running from the bung into a bottle: one bottle, or one of several drawn quickly one after the other. */
    static final SoundEvent POUR = event("barrel.pour");
    static final SoundEvent POUR_QUICK = event("barrel.pour_quick");

    private BarrelSounds() {
    }

    private static SoundEvent event(String name) {
        return SoundEvent.createVariableRangeEvent(HandsOnLetsDo.id(name));
    }
}
