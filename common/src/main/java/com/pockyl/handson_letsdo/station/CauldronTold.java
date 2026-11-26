package com.pockyl.handson_letsdo.station;

import net.minecraft.nbt.CompoundTag;

/**
 * What the station adds to HerbalBrews' brewing cauldron ({@code CauldronMixin}): the cauldron tells every client near it what it
 * holds and how far its brew is (HerbalBrews tells them nothing), so that it looks the same in the world as at the station: the
 * potions in their colours, the infusion afloat, the brew mixing.
 */
public interface CauldronTold {
    /** On a client's copy: whether the server told it what the cauldron holds, which a server without this mod never does. */
    boolean handson$told();

    /**
     * On a client's copy: the game time it was last told at, when its count of the brew was as it says. The copy is told before it
     * knows its level, so the first look after a telling marks it: {@code now}, the client's game time.
     */
    long handson$toldAt(long now);

    /** What the clients are sent of the cauldron: everything it saves, and the word that this server has the station. */
    CompoundTag handson$updateTag();
}
