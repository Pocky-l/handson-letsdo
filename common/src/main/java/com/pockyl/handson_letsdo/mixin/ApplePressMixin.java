package com.pockyl.handson_letsdo.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.satisfy.vinery.core.block.entity.ApplePressBlockEntity;
import net.satisfy.vinery.core.recipe.ApplePressFermentingRecipe;
import net.satisfy.vinery.core.recipe.ApplePressMashingRecipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_letsdo.station.BarrelStation;
import com.pockyl.handson_letsdo.station.Press;
import com.pockyl.handson_letsdo.station.PressBasket;
import com.pockyl.handson_letsdo.station.VineryPressing;

/**
 * Vinery's press with a basket whose front the cook opens and shuts at the station: while it stands open the press neither
 * presses nor bottles (its tick waits, what it had done stays). And the press tells the clients near it what it holds and since
 * when it presses and bottles (Vinery tells them nothing), so that every client shows the press at work, the screw turning and
 * the juice dripping, with nobody at it ({@code BlockEntityUpdateMixin} sends it). The open front is kept with the block.
 * And the tub under the plate's lip ({@link PressBasket}): the press is lent an empty wine bottle for each tick it may bottle into
 * the tub, and what it fills runs into the tub. And the press by hand: while a cook works at it, its own tick waits; the cook's
 * presses of the screw's cap press a load at once and the juice runs quickly, each fruit and each bottle made by Vinery's own steps
 * ({@link ApplePressInvoker}).
 */
@Mixin(ApplePressBlockEntity.class)
public abstract class ApplePressMixin implements PressBasket {
    @Unique
    private static final String HANDSON_TAG = "HandsOnPress";
    @Unique
    private static final int HANDSON_BOTTLE_SLOT = 2;
    @Unique
    private static final int HANDSON_RESULT_SLOT = 3;

    @Shadow(remap = false)
    @Final
    private NonNullList<ItemStack> inventory;
    @Shadow(remap = false)
    private int progress1;
    @Shadow(remap = false)
    private int progress2;
    @Shadow(remap = false)
    private int maxProgress1;
    @Shadow(remap = false)
    private int maxProgress2;

    @Unique
    private boolean handson$open;
    @Unique
    private long handson$mashSince = -1L;
    @Unique
    private long handson$bottleSince = -1L;
    /** What the clients were last told, to tell them again only when it changed; 0 before the first look. */
    @Unique
    private int handson$told;
    @Unique
    private ItemStack handson$tub = ItemStack.EMPTY;
    /** Whether what it was loaded from carried the station's part: the server it came from has this mod. */
    @Unique
    private boolean handson$heard;
    /** Whether the press holds the bottle lent to it for this tick, and its result slot before the tick. */
    @Unique
    private boolean handson$lent;
    @Unique
    private ItemStack handson$resultBefore = ItemStack.EMPTY;
    /** Presses of the screw's cap on the load in the basket. */
    @Unique
    private int handson$presses;
    /** Until when (game time) a cook works at the press, and how much mash pressed by hand still runs out quickly. */
    @Unique
    private long handson$attended;
    @Unique
    private int handson$squeezed;
    /** Ticks the quick bottle's worth has run, 0 while none runs. */
    @Unique
    private int handson$run;

    /**
     * Before Vinery's tick: the clients are told when the press starts or stops a step, or its slots changed (hoppers and its menu
     * change them too). With the basket open, the tick waits. While a cook works at the press, and while what the cook pressed
     * still runs out, it waits too, and the juice runs at the hand's pace instead.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void handson$tick(CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        long now = level.getGameTime();
        boolean byHand = !handson$open && (now < handson$attended || handson$squeezed > 0 && handson$canRun(level));
        if (byHand) {
            handson$juice(level);
        } else {
            handson$run = 0;
        }
        // As long as a step runs its start stays put; while it waits (the basket open, a cook's hand) its start moves on with the time.
        handson$mashSince = !byHand && progress1 > 0 ? now - progress1 : -1L;
        handson$bottleSince = handson$run > 0 ? now - handson$run : !byHand && progress2 > 0 ? now - progress2 : -1L;
        int seen = handson$seen();
        if (seen != handson$told) {
            handson$told = seen;
            level.sendBlockUpdated(self.getBlockPos(), self.getBlockState(), self.getBlockState(), Block.UPDATE_CLIENTS);
        }
        if (handson$open || byHand) {
            ci.cancel();
            return;
        }
        handson$lend(level);
    }

    /** The recipe of {@code type} with the id of {@code step}, as the press's own code takes it; null for none. */
    @Unique
    private static <T> T handson$recipe(Level level, VineryPressing.Step step, Class<T> type) {
        if (step == null) {
            return null;
        }
        Recipe<?> recipe = level.getRecipeManager().byKey(step.id()).map(RecipeHolder::value).orElse(null);
        return type.isInstance(recipe) ? type.cast(recipe) : null;
    }

    /** Whether the mash before the plate can run into a bottle now: a bottle stands in the press, or the tub has room for it. */
    @Unique
    private boolean handson$canRun(Level level) {
        ItemStack mash = inventory.get(1);
        VineryPressing.Step step = mash.isEmpty() ? null : VineryPressing.taking(VineryPressing.bottling(level), mash);
        if (step == null) {
            return false;
        }
        if (!step.bottle() || !inventory.get(HANDSON_BOTTLE_SLOT).isEmpty()) {
            return true;
        }
        return handson$tub.getCount() < Press.TUB_MOST && (handson$tub.isEmpty() || ItemStack.isSameItemSameComponents(handson$tub, step.result()));
    }

    /**
     * The juice by hand: a bottle's worth every {@link Press#JUICE_TICKS}, bottled by the press's own step, into the tub (a bottle
     * lent for it) or into the bottles a hopper laid in.
     */
    @Unique
    private void handson$juice(Level level) {
        if (!handson$canRun(level)) {
            handson$run = 0;
            return;
        }
        if (++handson$run < Press.JUICE_TICKS) {
            return;
        }
        handson$run = 0;
        ApplePressBlockEntity self = (ApplePressBlockEntity) (Object) this;
        ApplePressFermentingRecipe recipe = handson$recipe(level, VineryPressing.taking(VineryPressing.bottling(level), inventory.get(1)),
                ApplePressFermentingRecipe.class);
        if (recipe == null) {
            return;
        }
        boolean lend = recipe.requiresBottle() && inventory.get(HANDSON_BOTTLE_SLOT).isEmpty();
        if (lend) {
            handson$resultBefore = inventory.get(HANDSON_RESULT_SLOT).copy();
            inventory.set(HANDSON_BOTTLE_SLOT, new ItemStack(BarrelStation.wineBottle()));
        }
        if (ApplePressInvoker.handson$canBottle(self, recipe)) {
            ApplePressInvoker.handson$bottle(self, recipe);
            handson$squeezed = Math.max(0, handson$squeezed - 1);
        } else {
            // The press's own step does not take it (another drink stands in its result slot): it goes on the press's own way.
            handson$squeezed = 0;
        }
        progress2 = 0;
        if (lend) {
            handson$lent = true;
            handson$poured();
        }
        self.setChanged();
    }

    /**
     * The cook presses the screw's cap. With the basket shut and fruit the press presses, the press counts; the last press of a load
     * presses up to {@link Press#LOAD} fruits by the press's own step, as many as the mash before the plate has room for.
     */
    @Override
    public int handson$press() {
        ApplePressBlockEntity self = (ApplePressBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide() || handson$open) {
            return -1;
        }
        ItemStack fruit = inventory.get(0);
        ApplePressMashingRecipe recipe = handson$recipe(level, VineryPressing.taking(VineryPressing.mashing(level), fruit),
                ApplePressMashingRecipe.class);
        if (recipe == null || !ApplePressInvoker.handson$canMash(self, recipe)) {
            return -1;
        }
        ItemStack mash = inventory.get(1);
        int each = Math.max(1, recipe.getResultItem(level.registryAccess()).getCount());
        int room = mash.isEmpty() ? Integer.MAX_VALUE : (mash.getMaxStackSize() - mash.getCount()) / each;
        if (room <= 0) {
            return -1;
        }
        handson$presses++;
        int made = 0;
        if (handson$presses >= Press.PRESSES_PER_LOAD) {
            handson$presses = 0;
            int load = Math.min(Press.LOAD, Math.min(fruit.getCount(), room));
            for (int i = 0; i < load && ApplePressInvoker.handson$canMash(self, recipe); i++) {
                int before = inventory.get(1).getCount();
                ApplePressInvoker.handson$mash(self, recipe);
                made += inventory.get(1).getCount() - before;
            }
            progress1 = 0;
            // What runs out quickly is the mash pressed by hand, never more than lies before the plate (a full tub keeps it there).
            handson$squeezed = Math.min(handson$squeezed + made, inventory.get(1).getCount());
        }
        self.setChanged();
        handson$told = handson$seen();
        level.sendBlockUpdated(self.getBlockPos(), self.getBlockState(), self.getBlockState(), Block.UPDATE_CLIENTS);
        return made;
    }

    @Override
    public int handson$presses() {
        return handson$presses;
    }

    @Override
    public void handson$attend(long until) {
        handson$attended = Math.max(handson$attended, until);
    }

    @Override
    public boolean handson$quick() {
        return handson$run > 0;
    }

    /**
     * The press may bottle into the tub: its bottle slot is empty, what lies in its mash slot is bottled in a bottle, and the tub
     * is empty or holds the same drink with room for one more. It is lent the bottle for this tick.
     */
    @Unique
    private void handson$lend(Level level) {
        if (!inventory.get(HANDSON_BOTTLE_SLOT).isEmpty() || handson$tub.getCount() >= Press.TUB_MOST) {
            return;
        }
        ItemStack mash = inventory.get(1);
        VineryPressing.Step step = mash.isEmpty() ? null : VineryPressing.taking(VineryPressing.bottling(level), mash);
        if (step == null || !step.bottle() || !handson$tub.isEmpty() && !ItemStack.isSameItemSameComponents(handson$tub, step.result())) {
            return;
        }
        handson$lent = true;
        handson$resultBefore = inventory.get(HANDSON_RESULT_SLOT).copy();
        inventory.set(HANDSON_BOTTLE_SLOT, new ItemStack(BarrelStation.wineBottle()));
    }

    /** After Vinery's tick: the lent bottle, if it was not filled, is taken back; what it was filled with runs into the tub. */
    @Inject(method = "tick", at = @At("RETURN"), remap = false)
    private void handson$afterTick(CallbackInfo ci) {
        handson$poured();
    }

    /** The lent bottle, if it was not filled, is taken back; what it was filled with runs into the tub. */
    @Unique
    private void handson$poured() {
        if (!handson$lent) {
            return;
        }
        handson$lent = false;
        if (!inventory.get(HANDSON_BOTTLE_SLOT).isEmpty()) {
            inventory.set(HANDSON_BOTTLE_SLOT, ItemStack.EMPTY);
            return;
        }
        ItemStack result = inventory.get(HANDSON_RESULT_SLOT);
        int made = result.getCount() - (ItemStack.isSameItemSameComponents(result, handson$resultBefore) ? handson$resultBefore.getCount() : 0);
        if (made <= 0) {
            return;
        }
        ItemStack poured = result.copyWithCount(made);
        inventory.set(HANDSON_RESULT_SLOT, handson$resultBefore.isEmpty() || made == result.getCount() ? ItemStack.EMPTY
                : result.copyWithCount(result.getCount() - made));
        handson$tub = handson$tub.isEmpty() ? poured : handson$tub.copyWithCount(handson$tub.getCount() + made);
        ((BlockEntity) (Object) this).setChanged();
    }

    /** What the clients see of the press in one number: the open front, whether each step runs, and the slots. */
    @Unique
    private int handson$seen() {
        int seen = 1 + (handson$open ? 2 : 0) + (handson$mashSince >= 0 ? 4 : 0) + (handson$bottleSince >= 0 ? 8 : 0)
                + handson$tub.getCount() * 16 + handson$presses * 512 + (handson$run > 0 ? 4096 : 0);
        for (ItemStack stack : inventory) {
            seen = seen * 31 + (stack.isEmpty() ? 0 : System.identityHashCode(stack.getItem()) * 17 + stack.getCount());
        }
        return seen == 0 ? 1 : seen;
    }

    /** The slots it reads are only those that hold something: an emptied one must not keep what it held on a client's copy. */
    @Inject(method = "loadAdditional", at = @At("HEAD"))
    private void handson$clear(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (tag.contains("Items", Tag.TAG_LIST)) {
            for (int i = 0; i < inventory.size(); i++) {
                inventory.set(i, ItemStack.EMPTY);
            }
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void handson$load(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        CompoundTag own = tag.getCompound(HANDSON_TAG);
        handson$heard |= tag.contains(HANDSON_TAG);
        handson$open = own.getBoolean("Open");
        handson$mashSince = own.contains("MashSince") ? own.getLong("MashSince") : -1L;
        handson$bottleSince = own.contains("BottleSince") ? own.getLong("BottleSince") : -1L;
        handson$tub = own.contains("Tub") ? ItemStack.parseOptional(registries, own.getCompound("Tub")) : ItemStack.EMPTY;
        handson$presses = own.getInt("Presses");
        handson$squeezed = Math.min(own.getInt("Squeezed"), inventory.get(1).getCount());
        handson$run = own.getInt("Quick");
        // Its times as the server has them (Vinery's config there), for a client's copy.
        if (own.getInt("MashTime") > 0) {
            maxProgress1 = own.getInt("MashTime");
        }
        if (own.getInt("BottleTime") > 0) {
            maxProgress2 = own.getInt("BottleTime");
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void handson$save(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        CompoundTag own = new CompoundTag();
        if (handson$open) {
            own.putBoolean("Open", true);
        }
        if (handson$mashSince >= 0) {
            own.putLong("MashSince", handson$mashSince);
        }
        if (handson$bottleSince >= 0) {
            own.putLong("BottleSince", handson$bottleSince);
        }
        if (!handson$tub.isEmpty()) {
            own.put("Tub", handson$tub.save(registries));
        }
        if (handson$presses > 0) {
            own.putInt("Presses", handson$presses);
        }
        if (handson$squeezed > 0) {
            own.putInt("Squeezed", handson$squeezed);
        }
        if (handson$run > 0) {
            own.putInt("Quick", handson$run);
        }
        own.putInt("MashTime", maxProgress1);
        own.putInt("BottleTime", maxProgress2);
        tag.put(HANDSON_TAG, own);
    }

    @Override
    public boolean handson$isOpen() {
        return handson$open;
    }

    @Override
    public void handson$setOpen(boolean open) {
        if (open == handson$open) {
            return;
        }
        handson$open = open;
        BlockEntity self = (BlockEntity) (Object) this;
        self.setChanged();
        Level level = self.getLevel();
        if (level != null && !level.isClientSide()) {
            handson$told = handson$seen();
            level.sendBlockUpdated(self.getBlockPos(), self.getBlockState(), self.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public boolean handson$told() {
        return handson$heard;
    }

    @Override
    public long handson$mashSince() {
        return handson$mashSince;
    }

    @Override
    public long handson$bottleSince() {
        return handson$bottleSince;
    }

    @Override
    public ItemStack handson$tub() {
        return handson$tub;
    }

    @Override
    public void handson$setTub(ItemStack tub) {
        handson$tub = tub.isEmpty() ? ItemStack.EMPTY : tub.copy();
        BlockEntity self = (BlockEntity) (Object) this;
        self.setChanged();
        Level level = self.getLevel();
        if (level != null && !level.isClientSide()) {
            handson$told = handson$seen();
            level.sendBlockUpdated(self.getBlockPos(), self.getBlockState(), self.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
