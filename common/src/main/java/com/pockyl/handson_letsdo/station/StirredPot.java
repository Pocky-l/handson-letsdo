package com.pockyl.handson_letsdo.station;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * What the station adds to a pot of Farm &amp; Charm ({@code StirredPotMixin}): its stirring, the batch it cooks and the soup that
 * waits in it to be served. Kept with the pot's own data. The pot also tells the clients near it what it holds, so that each
 * shows it as its station does with nobody at it.
 */
public interface StirredPot {
    PotCooking handson$cooking();

    /** On a client's copy: whether the server told it what the pot holds, which a server without this mod never does. */
    boolean handson$told();

    /** What the clients are sent of the pot: everything it saves, and the word that this server has the station. */
    CompoundTag handson$updateTag(HolderLookup.Provider registries);
}
