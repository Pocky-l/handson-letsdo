package com.pockyl.handson_letsdo.station;

import net.minecraft.world.item.ItemStack;

/**
 * What the station adds to Vinery's apple press ({@code ApplePressMixin}): the basket's front, which the cook opens and shuts,
 * and the press telling every client near it what it holds and since when it presses and bottles, so that
 * the press is seen at work in the world too. The press presses and bottles only while its basket is shut. A press with the
 * basket shut is as Vinery made it: so is every press placed before.
 *
 * <p>And the tub under the plate's lip: what the press bottles runs into it instead of into bottles laid by
 * the cook. The press still bottles by its own tick and its own recipe: while its bottle slot is empty and the tub has room for
 * what it makes, it is lent one empty wine bottle for the tick, and what it fills goes into the tub, a portion a bottle. The
 * cook pays the bottle back when they fill one at the tub. Bottles a hopper or the menu laid in are filled the press's own way.
 *
 * <p>And the press worked by hand: while a cook is at it, it presses nothing by itself; every press of the screw's cap counts, and
 * the last of {@link Press#PRESSES_PER_LOAD} presses the whole load of up to {@link Press#LOAD} fruits at once, each by the press's
 * own step. The juice of what was pressed by hand, and of everything while a cook is there, runs into the tub at
 * {@link Press#JUICE_TICKS} a bottle's worth, each bottled by the press's own step.
 */
public interface PressBasket {
    /** Whether the basket's front stands open. */
    boolean handson$isOpen();

    /** Opens or shuts the basket's front, and tells the clients. Server only. */
    void handson$setOpen(boolean open);

    /** The game time the fruit now pressed began to be pressed at, -1 while none is; as the server told it, on a client too. */
    long handson$mashSince();

    /** The game time the bottle now filled began to be filled at, -1 while none is. */
    long handson$bottleSince();

    /** What the tub under the lip holds: the drink the press made, a count of portions (a bottle each); empty for none. */
    ItemStack handson$tub();

    /** Sets what the tub holds, and tells the clients. Server only. */
    void handson$setTub(ItemStack tub);

    /**
     * Whether the press was told what the station adds to it (its save carried it): on a client, only where the server has this mod,
     * so a client with the mod on a server without it draws no tub that nothing fills.
     */
    boolean handson$told();

    /** How many times the screw's cap was pressed on the load in the basket; 0 for none. */
    int handson$presses();

    /**
     * The cook presses the screw's cap once: with the basket shut and fruit the press presses in it, the press counts, and the
     * last press of a load presses it. Returns how much it became (mash), 0 for a press that only counted, -1 for none. Server only.
     */
    int handson$press();

    /** A cook works at the press until {@code until} (game time): it presses only by hand meanwhile. Server only. */
    void handson$attend(long until);

    /** Whether the bottle that fills now runs at the hand's pace ({@link Press#JUICE_TICKS}), not the press's own. */
    boolean handson$quick();
}
