package com.pockyl.handson_letsdo.station;

/**
 * What the station adds to Vinery's barrel ({@code FermentationBarrelMixin}): its own count of how long what lies in it has
 * fermented, and the wine that is done and waits in it, still in the barrel, to be drawn into bottles. Kept with the barrel's own
 * data, in the world and in what clients are sent of it.
 */
public interface BrewingBarrel {
    /** Ticks what lies in the barrel has fermented; 0 while it makes nothing. */
    int handson$brewTime();

    void handson$setBrewTime(int ticks);

    /** The recipe of the wine that waits in the barrel; empty for none. */
    String handson$wine();

    /** How many bottles of it wait in the barrel. */
    int handson$portions();

    /** Sets the wine that waits in the barrel and how many bottles of it; none clears it. */
    void handson$setWine(String recipe, int portions);

    /**
     * The game time up to which the barrel stands open, its hatch off at the station: until then what lies in it does not ferment
     * on, and its count waits. The station keeps it a little ahead of the clock for as long as the hatch is off, so a barrel whose
     * station is gone without a word is shut again by itself. Not kept with the barrel: nobody stands at it after a restart.
     */
    long handson$openUntil();

    void handson$setOpenUntil(long gameTime);

    /** Whether the barrel stands open now ({@link #handson$openUntil}). */
    default boolean handson$isOpen(long now) {
        return now <= handson$openUntil();
    }
}
