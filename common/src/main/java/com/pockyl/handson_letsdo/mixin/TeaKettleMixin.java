package com.pockyl.handson_letsdo.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.satisfy.herbalbrews.core.blocks.entity.TeaKettleBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.handson_letsdo.station.Kettle;
import com.pockyl.handson_letsdo.station.KettleDrinks;
import com.pockyl.handson_letsdo.station.KettleStation;

import java.util.Optional;

/**
 * HerbalBrews' kettle brews into itself while the station is on ({@link KettleDrinks}): with no bottle in its bottle slot, its
 * recipe is looked up as if one stood there (every drink is brewed into a bottle), the bottle is not taken, and the drink it makes
 * goes from its drink slot into the kettle, where it waits to be poured. It brews no other drink while one waits, and no more
 * than {@link KettleDrinks#MOST}. With bottles in it, it fills them its own way. Its menu, hoppers, heat and water stay as they are.
 * And the kettle tells the clients near it what it keeps (HerbalBrews tells them nothing), whenever its water, heat, brewing or
 * contents change, so that every client shows it steaming, boiling and whistling with nobody at it.
 */
@Mixin(TeaKettleBlockEntity.class)
public abstract class TeaKettleMixin implements KettleDrinks {
    @Unique
    private static final String HANDSON_TAG = "HandsOnKettle";
    /** Its heat reaches the clients in steps of this much (of 100): enough for how hard it steams. */
    @Unique
    private static final int HEAT_STEP = 5;

    @Shadow(remap = false)
    @Final
    private NonNullList<ItemStack> inventory;
    @Shadow(remap = false)
    private int cookingTime;
    @Shadow(remap = false)
    private int requiredDuration;
    @Shadow(remap = false)
    private boolean isBeingBurned;
    @Shadow(remap = false)
    private int waterLevel;
    @Shadow(remap = false)
    private int heatLevel;

    @Unique
    private ItemStack handson$drink = ItemStack.EMPTY;
    /** This tick's recipe was found with no bottle in the kettle: what it brews stays in it. */
    @Unique
    private boolean handson$intoItself;
    /** How many drinks lay in its drink slot before it brewed. */
    @Unique
    private int handson$before;
    /** What the clients were last told, to tell them again only when it changed; 0 before the first look. */
    @Unique
    private int handson$told;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Redirect(method = "tick", remap = false, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;"
                    + "Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"))
    private Optional handson$find(RecipeManager manager, RecipeType type, Container container, Level level) {
        handson$intoItself = false;
        if (!KettleStation.brewsIntoItself() || !inventory.get(Kettle.BOTTLE_SLOT).isEmpty()) {
            return manager.getRecipeFor(type, container, level);
        }
        inventory.set(Kettle.BOTTLE_SLOT, new ItemStack(Items.GLASS_BOTTLE));
        Optional found;
        try {
            found = manager.getRecipeFor(type, container, level);
        } finally {
            inventory.set(Kettle.BOTTLE_SLOT, ItemStack.EMPTY);
        }
        if (found.isEmpty()) {
            return found;
        }
        ItemStack made = ((Recipe<?>) found.get()).getResultItem(level.registryAccess());
        if (!handson$drink.isEmpty() && (!ItemStack.isSameItem(handson$drink, made) || handson$drink.getCount() >= MOST)) {
            // Another drink waits in the kettle, or it is full: it brews again once they are poured.
            return Optional.empty();
        }
        handson$intoItself = true;
        return found;
    }

    /**
     * Brewing takes one of each of the recipe's ingredients, the bottle too, and stops where one is missing: with none in the
     * kettle, the bottle it was looked up with stands there while it brews, and is taken by it.
     */
    @Inject(method = "craft", at = @At("HEAD"), remap = false)
    private void handson$beforeBrew(CallbackInfo ci) {
        handson$before = inventory.get(Kettle.DRINK_SLOT).getCount();
        if (handson$intoItself) {
            inventory.set(Kettle.BOTTLE_SLOT, new ItemStack(Items.GLASS_BOTTLE));
        }
    }

    /** What it brewed into its drink slot with no bottle goes into the kettle; the bottle it brewed with is gone either way. */
    @Inject(method = "craft", at = @At("RETURN"), remap = false)
    private void handson$brewed(CallbackInfo ci) {
        if (!handson$intoItself) {
            return;
        }
        handson$intoItself = false;
        inventory.set(Kettle.BOTTLE_SLOT, ItemStack.EMPTY);
        ItemStack slot = inventory.get(Kettle.DRINK_SLOT);
        int made = slot.getCount() - handson$before;
        if (made <= 0) {
            return;
        }
        if (handson$drink.isEmpty()) {
            handson$drink = slot.copyWithCount(made);
        } else {
            handson$drink.grow(made);
        }
        slot.shrink(made);
        if (slot.isEmpty()) {
            inventory.set(Kettle.DRINK_SLOT, ItemStack.EMPTY);
        }
        ((BlockEntity) (Object) this).setChanged();
    }

    /**
     * After HerbalBrews' tick: the clients near are told when what they show of the kettle changed (its water, its heat in steps,
     * whether it stands on heat and brews, what lies in it, the drink that waits), not every tick its count goes on.
     */
    @Inject(method = "tick", at = @At("RETURN"), remap = false)
    private void handson$tell(Level level, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (level.isClientSide() || !KettleStation.brewsIntoItself()) {
            return;
        }
        int seen = handson$seen();
        if (seen != handson$told) {
            handson$told = seen;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** What the clients see of the kettle in one number. */
    @Unique
    private int handson$seen() {
        int seen = 1 + waterLevel * 7 + heatLevel / HEAT_STEP * 131 + (isBeingBurned ? 3 : 0) + (cookingTime > 0 ? 5 : 0) + requiredDuration * 17;
        seen = seen * 31 + (handson$drink.isEmpty() ? 0 : System.identityHashCode(handson$drink.getItem()) * 17 + handson$drink.getCount());
        for (ItemStack stack : inventory) {
            seen = seen * 31 + (stack.isEmpty() ? 0 : System.identityHashCode(stack.getItem()) * 17 + stack.getCount());
        }
        return seen == 0 ? 1 : seen;
    }

    /**
     * What clients are sent of the kettle, on loading its chunk and on every update: everything it keeps, as it saves it, besides
     * HerbalBrews' own flag.
     */
    @Inject(method = "getUpdateTag", at = @At("RETURN"))
    private void handson$updateTag(CallbackInfoReturnable<CompoundTag> info) {
        if (KettleStation.brewsIntoItself()) {
            info.getReturnValue().merge(((BlockEntity) (Object) this).saveWithoutMetadata());
        }
    }

    /** The slots it reads are only those that hold something: an emptied one must not keep what it held on a client's copy. */
    @Inject(method = "load", at = @At("HEAD"))
    private void handson$clear(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("Items", Tag.TAG_LIST)) {
            for (int i = 0; i < inventory.size(); i++) {
                inventory.set(i, ItemStack.EMPTY);
            }
        }
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void handson$load(CompoundTag tag, CallbackInfo ci) {
        handson$drink = tag.contains(HANDSON_TAG) ? ItemStack.of(tag.getCompound(HANDSON_TAG).getCompound("Drink")) : ItemStack.EMPTY;
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void handson$save(CompoundTag tag, CallbackInfo ci) {
        if (!handson$drink.isEmpty()) {
            CompoundTag own = new CompoundTag();
            own.put("Drink", handson$drink.save(new CompoundTag()));
            tag.put(HANDSON_TAG, own);
        }
    }

    @Override
    public ItemStack handson$waiting() {
        return handson$drink;
    }

    @Override
    public ItemStack handson$pour() {
        if (handson$drink.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack one = handson$drink.split(1);
        ((BlockEntity) (Object) this).setChanged();
        return one;
    }

    @Override
    public void handson$setWaiting(ItemStack drinks) {
        handson$drink = drinks.copy();
        ((BlockEntity) (Object) this).setChanged();
    }
}
