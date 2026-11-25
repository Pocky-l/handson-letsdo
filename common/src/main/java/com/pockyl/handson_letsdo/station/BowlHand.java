package com.pockyl.handson_letsdo.station;

/**
 * What the station adds to Farm &amp; Charm's crafting bowl ({@code CraftingBowlMixin}): how far the whisk has gone round the bowl on
 * what lies in it, kept with the block, and the bowl telling every client near it what it holds, so that it looks the same in the
 * world as at the station. A bowl nobody works at is as Farm &amp; Charm made it.
 */
public interface BowlHand {
    /** How far the whisk has gone round the bowl on what lies in it now, radians either way. */
    float handson$stirred();

    /** Sets how far the whisk has gone round; kept with the block. */
    void handson$setStirred(float stirred);

    /** Tells the clients what the bowl holds now. Server only. */
    void handson$tell();

    /** Whether the bowl was told what the station adds to it (its save carried it): on a client, only where the server has this mod. */
    boolean handson$told();
}
