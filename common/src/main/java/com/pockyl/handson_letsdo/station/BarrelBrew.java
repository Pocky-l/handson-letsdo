package com.pockyl.handson_letsdo.station;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.pockyl.handson_letsdo.HandsOnConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * How the fermentation barrel makes its wine while the station is on, as a cellar does: what goes into the barrel ferments there
 * as one, the wine stays in it, and bottles are what it is drawn into:
 *
 * <ul>
 * <li>Everything that lies in it ferments together: as many wines as its juice and every ingredient make, in the time Vinery gives
 * one. No empty bottle is needed before.</li>
 * <li>The wine stays in the barrel, the juice it was made of turned into it, until it is drawn at the bung into empty wine bottles
 * (one each). A wine whose own recipe needs no bottle (its bottle came with what it was made of: honey bottles, a bottle of juice
 * or wine) comes bottled into the barrel's wine slot by itself.</li>
 * <li>Hoppers and players without the mod keep Vinery's way: empty bottles put into the barrel's bottle slot are filled with the
 * wine that waits, into its wine slot, one a tick.</li>
 * <li>A wine of another kind starts only once the last one is drawn: one liquid at a time.</li>
 * <li>It ferments only shut: while a cook has the hatch off, its time waits and nothing is lost; the barrel shuts itself behind
 * the last cook.</li>
 * </ul>
 */
public final class BarrelBrew {
    /** The most bottles of wine that wait in a barrel. */
    public static final int MOST_PORTIONS = 64;

    private BarrelBrew() {
    }

    /** Whether barrels ferment the station's way: while the station is on, on the server it runs on. */
    public static boolean active() {
        return HandsOnConfig.fermentationBarrel();
    }

    /** One tick of a barrel on the server, after Vinery's own (which pours the juice bottles laid in). */
    public static void tick(Level level, BlockEntity entity) {
        if (!(entity instanceof BrewingBarrel brew)) {
            return;
        }
        Barrel barrel = Barrel.of(entity);
        if (barrel == null) {
            return;
        }
        Wine wine = brewing(level, barrel, brew);
        // A barrel ferments shut: while its hatch is off the count waits where it was, and goes on once the hatch is back.
        int time = wine == null ? 0 : brew.handson$isOpen(level.getGameTime()) ? brew.handson$brewTime() : brew.handson$brewTime() + 1;
        if (wine != null && time >= VineryWines.fermentationTime()) {
            ferment(entity, barrel, brew, wine);
            time = 0;
        }
        brew.handson$setBrewTime(time);
        bottleForHoppers(level, entity, barrel, brew);
    }

    /** The wine that waits in the barrel, or null for none (or one whose recipe is gone). */
    public static Wine stored(Level level, BrewingBarrel brew) {
        return brew.handson$portions() > 0 ? VineryWines.byId(level, brew.handson$wine()) : null;
    }

    /**
     * The wine what lies in the barrel makes now, Vinery's reading without the bottle: its juice, enough of it, every ingredient
     * one a slot; the same wine as the one that waits, if one does, and room for more of it. Null while it makes none.
     */
    public static Wine brewing(Level level, Barrel barrel, BrewingBarrel brew) {
        List<ItemStack> lying = ingredients(barrel);
        if (lying.stream().allMatch(ItemStack::isEmpty) || brew.handson$portions() >= MOST_PORTIONS) {
            return null;
        }
        String juice = barrel.juice();
        String waiting = brew.handson$portions() > 0 ? brew.handson$wine() : "";
        for (Wine wine : VineryWines.all(level)) {
            if (wine.juice().equals(juice) && barrel.level() >= wine.juiceAmount() && (waiting.isEmpty() || wine.id().toString().equals(waiting))
                    && StationRecipes.matches(wine.ingredients(), lying)) {
                return wine;
            }
        }
        return null;
    }

    /** How many wines of {@code wine} what lies in the barrel makes in one go: its juice and every ingredient, and room for them. */
    public static int batch(Barrel barrel, BrewingBarrel brew, Wine wine) {
        int batch = barrel.level() / Math.max(1, wine.juiceAmount());
        for (ItemStack stack : ingredients(barrel)) {
            if (!stack.isEmpty()) {
                batch = Math.min(batch, stack.getCount());
            }
        }
        return Math.max(0, Math.min(batch, MOST_PORTIONS - brew.handson$portions()));
    }

    /** How much of the barrel the wine that waits in it fills, in Vinery's measure of juice: the juice it was made of. */
    public static int wineVolume(Level level, BrewingBarrel brew) {
        Wine wine = stored(level, brew);
        return wine == null ? 0 : brew.handson$portions() * wine.juiceAmount();
    }

    private static List<ItemStack> ingredients(Barrel barrel) {
        List<ItemStack> lying = new ArrayList<>();
        for (int i = 0; i < Barrel.INGREDIENTS; i++) {
            lying.add(barrel.container().getItem(Barrel.FIRST_INGREDIENT + i));
        }
        return lying;
    }

    /** The wine is done: the juice and the ingredients of every wine in the batch become that many bottles of it in the barrel. */
    private static void ferment(BlockEntity entity, Barrel barrel, BrewingBarrel brew, Wine wine) {
        int batch = batch(barrel, brew, wine);
        if (batch <= 0) {
            return;
        }
        Container container = barrel.container();
        for (int i = 0; i < Barrel.INGREDIENTS; i++) {
            int slot = Barrel.FIRST_INGREDIENT + i;
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                ItemStack left = stack.copy();
                left.shrink(batch);
                container.setItem(slot, left.isEmpty() ? ItemStack.EMPTY : left);
            }
        }
        brew.handson$setWine(wine.id().toString(), brew.handson$portions() + batch);
        // Vinery's own setter tells the clients around: the barrel looks the same to them as to the server.
        VineryWines.setJuice(entity, barrel.juice(), barrel.level() - batch * wine.juiceAmount());
        entity.setChanged();
    }

    /**
     * For hoppers and Vinery's menu: the wine that waits goes into the barrel's wine slot, one bottle a tick, each into an empty
     * bottle from its bottle slot, or as it is when its recipe needs none.
     */
    private static void bottleForHoppers(Level level, BlockEntity entity, Barrel barrel, BrewingBarrel brew) {
        Wine wine = stored(level, brew);
        if (wine == null) {
            return;
        }
        Container container = barrel.container();
        ItemStack out = container.getItem(Barrel.WINE_SLOT);
        ItemStack bottle = container.getItem(Barrel.BOTTLE_SLOT);
        boolean room = out.isEmpty() || out.is(wine.result().getItem()) && out.getCount() < out.getMaxStackSize();
        if (!room || wine.bottle() && !BarrelStation.isWineBottle(bottle)) {
            return;
        }
        if (wine.bottle()) {
            ItemStack left = bottle.copy();
            left.shrink(1);
            container.setItem(Barrel.BOTTLE_SLOT, left.isEmpty() ? ItemStack.EMPTY : left);
        }
        if (out.isEmpty()) {
            container.setItem(Barrel.WINE_SLOT, VineryWines.bottled(wine, level));
        } else {
            ItemStack more = out.copy();
            more.grow(1);
            container.setItem(Barrel.WINE_SLOT, more);
        }
        take(entity, brew, 1);
    }

    /** {@code count} bottles of the wine that waits are drawn: the barrel tells the clients around once it is empty of it. */
    public static void take(BlockEntity entity, BrewingBarrel brew, int count) {
        int left = brew.handson$portions() - count;
        brew.handson$setWine(brew.handson$wine(), left);
        entity.setChanged();
        if (entity.getLevel() != null) {
            entity.getLevel().sendBlockUpdated(entity.getBlockPos(), entity.getBlockState(), entity.getBlockState(), 3);
        }
    }
}
