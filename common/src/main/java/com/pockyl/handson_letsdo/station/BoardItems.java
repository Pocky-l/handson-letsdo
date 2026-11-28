package com.pockyl.handson_letsdo.station;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/** What the station reaches in Farm &amp; Charm's cutting board, added to it by {@code CuttingBoardItemsAccessor}. */
public interface BoardItems {
    /** The board's own list of what lies on it, bottom first: written in place. */
    List<ItemStack> handson$items();

    /** Saves the board and tells the clients, as the board does after each of its own changes. */
    void handson$markUpdated();
}
