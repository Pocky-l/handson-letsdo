package com.pockyl.handson_letsdo.station;

import net.minecraft.world.item.ItemStack;

import com.pockyl.diegesis.api.state.SlotStorage;

/**
 * A slot group a station works out from its block (a pot's soup, its food less the soup's sets) on a client's copy of the block.
 * While a cook is at the station the library reads the slots from the storage and lays there what the server says; a group
 * worked out from the copy would ignore it and keep showing what the copy last held until the block's own update came, and
 * nothing would tell the scene when it did. So what the server says is shown until the copy itself changes, then the copy is
 * the truth again. On the server the group is the storage itself.
 */
public final class ToldSlots implements SlotStorage {
    private final SlotStorage own;
    private final ItemStack[] told;
    /** What the copy showed when the server spoke: once it shows something else, the copy has heard of it too. */
    private final ItemStack[] ownWhenTold;

    private ToldSlots(SlotStorage own) {
        this.own = own;
        told = new ItemStack[own.size()];
        ownWhenTold = new ItemStack[own.size()];
    }

    /** {@code own} on a server; on a client, {@code own} showing what the server says until it changes by itself. */
    public static SlotStorage of(boolean client, SlotStorage own) {
        return client ? new ToldSlots(own) : own;
    }

    @Override
    public int size() {
        return own.size();
    }

    @Override
    public ItemStack get(int index) {
        ItemStack now = own.get(index);
        if (told[index] != null) {
            if (ItemStack.matches(now, ownWhenTold[index])) {
                return told[index];
            }
            told[index] = null;
            ownWhenTold[index] = null;
        }
        return now;
    }

    @Override
    public void set(int index, ItemStack stack) {
        own.set(index, stack);
        ItemStack now = own.get(index);
        if (ItemStack.matches(now, stack)) {
            told[index] = null;
            ownWhenTold[index] = null;
        } else {
            told[index] = stack.copy();
            ownWhenTold[index] = now.copy();
        }
    }
}
