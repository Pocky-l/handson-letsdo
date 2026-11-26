package com.pockyl.handson_letsdo.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the cauldron's potions are to the eye and to its brew: their colours, and the effects HerbalBrews puts into the flask. The one
 * class that reads potions the way this line of the game keeps them.
 */
public final class CauldronBrews {
    /** Vanilla's water colour, for a potion with no effect and for the cauldron's own brew of such potions. */
    public static final int WATER = 0x385DC6;

    private CauldronBrews() {
    }

    /**
     * The potion's own colour, RGB: its custom colour, else vanilla's colour of its effects (water's for none). Not vanilla's colour of
     * the stack: HerbalBrews' flask names no potion, only its effects, and vanilla draws that as an unbrewable potion.
     */
    public static int color(ItemStack potion) {
        CompoundTag tag = potion.getTag();
        if (tag != null && tag.contains("CustomPotionColor", Tag.TAG_ANY_NUMERIC)) {
            return tag.getInt("CustomPotionColor") & 0xFFFFFF;
        }
        return PotionUtils.getColor(PotionUtils.getMobEffects(potion)) & 0xFFFFFF;
    }

    /**
     * The colour of the flask the potions brew into, RGB: vanilla's colour of the effects HerbalBrews gives it (every effect of the
     * three, the strongest of each kind), water's for none.
     */
    public static int brewColor(List<ItemStack> potions) {
        List<MobEffectInstance> effects = effects(potions);
        return effects.isEmpty() ? WATER : PotionUtils.getColor(effects) & 0xFFFFFF;
    }

    /**
     * Whether HerbalBrews makes a flask of these potions: on 1.20.1 only when one of them has an effect (it keeps counting and never
     * brews otherwise).
     */
    public static boolean makesSomething(List<ItemStack> potions) {
        return !effects(potions).isEmpty();
    }

    /** A bottle of water, for the badge when the cook has no potion to show. */
    public static ItemStack waterBottle() {
        return PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
    }

    /** A number that tells one kind of stack from another, its potion too. */
    public static int hash(ItemStack stack) {
        return System.identityHashCode(stack.getItem()) * 31 + Objects.hashCode(stack.getTag());
    }

    private static List<MobEffectInstance> effects(List<ItemStack> potions) {
        Map<MobEffect, MobEffectInstance> strongest = new LinkedHashMap<>();
        for (ItemStack potion : potions) {
            for (MobEffectInstance effect : PotionUtils.getMobEffects(potion)) {
                MobEffectInstance known = strongest.get(effect.getEffect());
                if (known == null || effect.getAmplifier() > known.getAmplifier()) {
                    strongest.put(effect.getEffect(), effect);
                }
            }
        }
        return List.copyOf(strongest.values());
    }
}
