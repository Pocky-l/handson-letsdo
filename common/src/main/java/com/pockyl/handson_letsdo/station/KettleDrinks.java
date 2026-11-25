package com.pockyl.handson_letsdo.station;

import net.minecraft.world.item.ItemStack;

/**
 * What the station adds to HerbalBrews' tea kettle ({@code TeaKettleMixin}), as Hands-On Delight's kettle does it: the kettle
 * brews into itself, and the drink waits in it until the cook pours it into bottles at the spout. No
 * bottle stands in the kettle before: a brew takes what it is made of and its water, as the kettle's own does, and adds one
 * drink to the kettle. A kettle that holds bottles (laid in by a hopper or in its menu) fills them as HerbalBrews made it.
 */
public interface KettleDrinks {
    /** The most drinks that wait in a kettle: it brews no more until some are poured. */
    int MOST = 16;

    /** The drink that waits in the kettle, its count the bottles it fills; empty for none. Not to be changed. */
    ItemStack handson$waiting();

    /** One drink poured out of the kettle, or empty when none waits. */
    ItemStack handson$pour();

    /** Sets what waits in the kettle: only a test does. */
    void handson$setWaiting(ItemStack drinks);
}
