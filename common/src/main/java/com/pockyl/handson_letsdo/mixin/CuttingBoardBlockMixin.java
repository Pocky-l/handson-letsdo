package com.pockyl.handson_letsdo.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.satisfy.farm_and_charm.core.block.CuttingBoardBlock;
import net.satisfy.farm_and_charm.core.block.entity.CuttingBoardBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.handson_letsdo.station.FarmCharmBoard;

import java.util.ArrayList;
import java.util.List;

/**
 * The station lays batches on the board (a stack of each layer); the board's own empty hand makes one dish of what lies there and
 * clears the rest away. Used without the station (sneaking, or a client without this mod), a board with a batch on it makes every
 * round of its dish, the dishes and what is left launched as the board launches them, or gives the top layer back whole.
 */
@Mixin(CuttingBoardBlock.class)
abstract class CuttingBoardBlockMixin {
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void handson$batch(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> callback) {
        if (level.isClientSide()) {
            return;
        }
        FarmCharmBoard board = FarmCharmBoard.of(level.getBlockEntity(pos));
        if (board == null || !board.knife().isEmpty() || board.items().stream().allMatch(stack -> stack.getCount() <= 1)) {
            return;
        }
        List<ItemStack> layers = new ArrayList<>();
        board.items().forEach(stack -> layers.add(stack.copy()));
        var dish = player.isShiftKeyDown() ? java.util.Optional.<net.minecraft.world.item.crafting.RecipeHolder<
                net.satisfy.farm_and_charm.core.recipe.CuttingBoardAssemblyRecipe>>empty() : FarmCharmBoard.dish(level, layers);
        if (dish.isPresent()) {
            int rounds = layers.stream().mapToInt(ItemStack::getCount).min().orElse(0);
            for (int round = 0; round < rounds; round++) {
                CuttingBoardBlockEntity.output(level, pos, FarmCharmBoard.make(level, dish.get().value(), layers));
            }
            board.setItems(List.of());
            for (ItemStack layer : layers) {
                if (layer.getCount() > rounds) {
                    CuttingBoardBlockEntity.output(level, pos, layer.copyWithCount(layer.getCount() - rounds));
                }
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
        } else {
            ItemStack top = layers.remove(layers.size() - 1);
            board.setItems(layers);
            if (!player.getInventory().add(top)) {
                player.drop(top, false);
            }
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 1.0F);
        }
        callback.setReturnValue(InteractionResult.CONSUME);
    }
}
