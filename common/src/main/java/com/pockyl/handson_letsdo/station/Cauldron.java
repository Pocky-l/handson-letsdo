package com.pockyl.handson_letsdo.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * HerbalBrews' brewing cauldron, as the station sees it: its own container and the numbers its menu shows. Nothing of HerbalBrews
 * is named here but the ids of its block and items: the slots are a vanilla container's, the numbers the cauldron's own
 * {@link ContainerData} ({@link BlockNumbers}), so the cauldron stays HerbalBrews' in every way and the station only looks into it
 * and lays things in.
 *
 * <p>Slots, as the cauldron has them: three potions, the flask it brews them into, the herbal infusion that sets them off.
 * Numbers: ticks brewed, ticks a brew takes. It brews with three potions, an infusion and its flask slot empty, over its own fire,
 * in the time HerbalBrews' config gives (1200 ticks): one flask with every effect of the three, the strongest of each kind.
 */
public final class Cauldron {
    public static final String MOD_ID = "herbalbrews";
    public static final int POTIONS = 3;
    public static final int FLASK_SLOT = 3;
    public static final int CATALYST_SLOT = 4;
    public static final int SLOTS = 5;

    public static final int TIME = 0;
    public static final int TOTAL = 1;
    public static final int NUMBERS = 2;

    @SuppressWarnings("removal")
    public static final ResourceLocation BLOCK = new ResourceLocation(MOD_ID, "cauldron");
    @SuppressWarnings("removal")
    public static final ResourceLocation INFUSION = new ResourceLocation(MOD_ID, "herbal_infusion");
    @SuppressWarnings("removal")
    public static final ResourceLocation FLASK = new ResourceLocation(MOD_ID, "flask");

    private final Container container;
    private final ContainerData numbers;
    private final boolean real;

    private Cauldron(Container container, ContainerData numbers, boolean real) {
        this.container = container;
        this.numbers = numbers;
        this.real = real;
    }

    /**
     * The cauldron of a block entity, or null for anything that is not the cauldron this was made for: another build of HerbalBrews
     * whose cauldron has other slots or numbers keeps its own menu.
     */
    public static Cauldron of(BlockEntity entity) {
        if (!(entity instanceof WorldlyContainer container) || container.getContainerSize() != SLOTS) {
            return null;
        }
        if (!BLOCK.equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()))) {
            return null;
        }
        ContainerData numbers = BlockNumbers.of(entity);
        if (numbers == null || numbers.getCount() != NUMBERS) {
            return null;
        }
        return new Cauldron(container, numbers, true);
    }

    /** {@link #of}, or an empty cauldron that holds nothing and takes nothing where there is none: what shows it never fails. */
    public static Cauldron orEmpty(BlockEntity entity) {
        Cauldron cauldron = entity == null ? null : of(entity);
        return cauldron != null ? cauldron : new Cauldron(new SimpleContainer(SLOTS), new SimpleContainerData(NUMBERS), false);
    }

    /** Whether this is a cauldron in the world, not the stand-in of {@link #orEmpty}. */
    public boolean isReal() {
        return real;
    }

    public Container container() {
        return container;
    }

    /** The potions in it, in their slots' order, the empty slots left out. */
    public List<ItemStack> potions() {
        List<ItemStack> potions = new ArrayList<>(POTIONS);
        for (int slot = 0; slot < POTIONS; slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                potions.add(stack);
            }
        }
        return potions;
    }

    public ItemStack flask() {
        return container.getItem(FLASK_SLOT);
    }

    public ItemStack catalyst() {
        return container.getItem(CATALYST_SLOT);
    }

    /** Ticks the brew has brewed; 0 while nothing brews. */
    public int time() {
        return Math.max(0, numbers.get(TIME));
    }

    /** Tells the cauldron it has brewed this long: stirring hurries it so, and a test skips the wait. */
    public void setTime(int ticks) {
        numbers.set(TIME, ticks);
    }

    /** Ticks a brew takes: HerbalBrews' config, set whenever a potion is laid in. */
    public int total() {
        return Math.max(0, numbers.get(TOTAL));
    }

    /**
     * Whether the cauldron brews now, as its own tick asks: three potions, an infusion and an empty flask slot. On 1.20.1
     * HerbalBrews also makes nothing of three potions that have no effect at all: it counts and counts and never brews.
     */
    public boolean brews() {
        for (int slot = 0; slot < POTIONS; slot++) {
            if (!isPotion(container.getItem(slot))) {
                return false;
            }
        }
        return isCatalyst(catalyst()) && flask().isEmpty() && CauldronBrews.makesSomething(potions());
    }

    /** Whether the cauldron counts this as a potion: what its tick takes into a brew. */
    public static boolean isPotion(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof PotionItem;
    }

    /** HerbalBrews' herbal infusion, what sets a brew off. */
    public static boolean isCatalyst(ItemStack stack) {
        return !stack.isEmpty() && INFUSION.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static boolean isFlask(ItemStack stack) {
        return !stack.isEmpty() && FLASK.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
}
