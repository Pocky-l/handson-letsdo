package com.pockyl.handson_letsdo.station;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the cauldron's potions are to the eye and to its brew: their colours, and the effects HerbalBrews puts into the flask. The one
 * class that reads potions the way this line of the game keeps them.
 */
public final class CauldronBrews {
    /** Vanilla's water colour, for a potion with no effect and for the cauldron's own brew of such potions. */
    public static final int WATER = 0x385DC6;

    private CauldronBrews() {
    }

    /** The potion's own colour, as vanilla draws its liquid, RGB. */
    public static int color(ItemStack potion) {
        return potion.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getColor() & 0xFFFFFF;
    }

    /**
     * The colour of the flask the potions brew into, RGB: vanilla's colour of the effects HerbalBrews gives it (every effect of the
     * three, the strongest of each kind), water's for none.
     */
    public static int brewColor(List<ItemStack> potions) {
        List<MobEffectInstance> effects = effects(potions);
        return effects.isEmpty() ? WATER : PotionContents.getColor(effects) & 0xFFFFFF;
    }

    /** Whether HerbalBrews makes a flask of these potions: on 1.21.1 always, a plain flask of potions without any effect. */
    public static boolean makesSomething(List<ItemStack> potions) {
        return true;
    }

    /** A bottle of water, for the badge when the cook has no potion to show. */
    public static ItemStack waterBottle() {
        return PotionContents.createItemStack(Items.POTION, Potions.WATER);
    }

    /** A number that tells one kind of stack from another, its potion too. */
    public static int hash(ItemStack stack) {
        return ItemStack.hashItemAndComponents(stack);
    }

    private static List<MobEffectInstance> effects(List<ItemStack> potions) {
        Map<Holder<MobEffect>, MobEffectInstance> strongest = new LinkedHashMap<>();
        for (ItemStack potion : potions) {
            for (MobEffectInstance effect : potion.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getAllEffects()) {
                MobEffectInstance known = strongest.get(effect.getEffect());
                if (known == null || effect.getAmplifier() > known.getAmplifier()) {
                    strongest.put(effect.getEffect(), effect);
                }
            }
        }
        return List.copyOf(strongest.values());
    }
}
