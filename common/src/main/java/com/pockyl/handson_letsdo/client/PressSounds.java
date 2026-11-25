package com.pockyl.handson_letsdo.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import com.pockyl.handson_letsdo.station.BarrelStation;

/**
 * The apple press's sounds: Vinery's own squeeze of fruit (its grapevine pot's, the press exists only with Vinery), vanilla's
 * wooden creak for the screw and a drop falling into liquid for the juice raining into the tub, and the barrel's clip for a
 * bottle filling under it. Played on the client by name: no registry entry is needed.
 */
final class PressSounds {
    static final SoundEvent SQUEEZE = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(BarrelStation.MOD_ID,
            "block.grapevine_pot.squeeze"));
    static final SoundEvent CREAK = SoundEvents.CROSSBOW_LOADING_MIDDLE.value();
    static final SoundEvent RELEASE = SoundEvents.WOODEN_TRAPDOOR_OPEN;
    static final SoundEvent DROP = SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON;
    static final SoundEvent DROP_ON_WOOD = SoundEvents.POINTED_DRIPSTONE_DRIP_WATER;
    static final SoundEvent GLUG = BarrelSounds.BLUB;

    private PressSounds() {
    }
}
