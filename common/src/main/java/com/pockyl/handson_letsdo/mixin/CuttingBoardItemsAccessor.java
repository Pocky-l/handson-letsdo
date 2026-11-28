package com.pockyl.handson_letsdo.mixin;

import net.minecraft.world.item.ItemStack;
import net.satisfy.farm_and_charm.core.block.entity.CuttingBoardBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.pockyl.handson_letsdo.station.BoardItems;

import java.util.List;

/**
 * Farm &amp; Charm's cutting board keeps what lies on it in a list it adds to one item at a time; the station lays a batch on it (a stack
 * to be cut, layers of a dish), so it writes that list itself and then saves and syncs the board as the board does.
 */
@Mixin(CuttingBoardBlockEntity.class)
public interface CuttingBoardItemsAccessor extends BoardItems {
    @Override
    @Accessor(value = "items", remap = false)
    List<ItemStack> handson$items();

    @Override
    @Invoker(value = "markUpdated", remap = false)
    void handson$markUpdated();
}
