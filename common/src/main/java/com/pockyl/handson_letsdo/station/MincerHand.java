package com.pockyl.handson_letsdo.station;

/**
 * What the station adds to Farm &amp; Charm's mincer ({@code MincerMixin}): what was minced at the station lies under the spout
 * until it is taken, instead of being thrown out at once; how far the crank has been turned on the load in the hopper; and the
 * mincer telling every client near it what it holds, so that the hopper and the plate under the spout look the same in the world
 * as at the station. A mincer nobody works at is as Farm &amp; Charm made it.
 */
public interface MincerHand {
    /** Whether what lies in the output slot was minced at the station and waits under the spout to be taken. */
    boolean handson$kept();

    /** Keeps what lies in the output slot under the spout, or lets the mincer throw it out as it does. Server only. */
    void handson$setKept(boolean kept);

    /** How far the crank has been turned on the load now in the hopper, radians either way. */
    float handson$turned();

    /** Sets how far the crank has been turned on the load; kept with the block. */
    void handson$setTurned(float turned);

    /** While the station makes a load through the mincer's own tick: it throws nothing out meanwhile. */
    void handson$setMaking(boolean making);

    /** Tells the clients what the mincer holds now. Server only. */
    void handson$tell();

    /**
     * Whether the mincer was told what the station adds to it (its save carried it): on a client, only where the server has this mod.
     */
    boolean handson$told();
}
