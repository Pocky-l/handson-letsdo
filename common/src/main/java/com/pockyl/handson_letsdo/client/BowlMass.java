package com.pockyl.handson_letsdo.client;

import net.minecraft.tags.ItemTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.pockyl.diegesis.api.client.Floater;
import com.pockyl.diegesis.api.client.LiquidLook;
import com.pockyl.diegesis.api.scene.MassLook;
import com.pockyl.handson_letsdo.HandsOnLetsDo;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What a bowlful turns into as it is whisked, drawn as that and nothing else: a thick mass with its own texture, firmness and sheen.
 * Whipped butter keeps its swirls, a dough is matte and keeps the whisk's furrows, a porridge holds its oat flakes (and the berries
 * that went in), a salad holds its leaves, tomato, onion and carrot as pieces, minced meat for dog food is grainy and matte, cat food is chunks
 * of its meat and fish in a paste. Whatever no
 * kind fits (another mod's recipe) is a runny batter in the colour of what it makes, which levels soon after the whisk.
 */
enum BowlMass {
    DOUGH("dough", 0xFFEFD6B1, false, 0.75F, 1.0F, 0),
    BUTTER("butter", 0xFFF4D98B, false, 0.9F, 0.85F, 0),
    PORRIDGE("porridge", 0xFFE9DCC3, false, 0.45F, 0.6F, 6),
    PORRIDGE_BERRIES("porridge_berries", 0xFFE7D7C2, false, 0.45F, 0.6F, 6),
    SALAD("salad", 0xFF3F6E25, false, 0.95F, 0.35F, 5),
    MINCE("mince", 0xFF7E362D, false, 0.8F, 0.75F, 0),
    CAT_FOOD("mince", 0xFF6B4632, false, 0.8F, 0.7F, 5),
    BATTER("batter", 0xFFFFFFFF, true, 0.08F, 0.55F, 0);

    /** The finished bowlful's top, which the cook clicks to take the result; the liquid under the colour of what was poured, plain. */
    static final ResourceLocation SHEET = ResourceLocation.fromNamespaceAndPath(HandsOnLetsDo.MOD_ID, "crafting_bowl/mass");
    static final ResourceLocation LIQUID = ResourceLocation.fromNamespaceAndPath(HandsOnLetsDo.MOD_ID, "bowl_mixture");
    /** How big what a porridge or a salad holds is drawn, pixels: a leaf, a ring of onion, a slice, a chunk, an oat flake. */
    private static final float LEAF_SIZE = 2.0F;
    private static final float SHRED_SIZE = 1.5F;
    private static final float SLICE_SIZE = 1.3F;
    private static final float CHUNK_SIZE = 1.2F;
    private static final float MEAT_SIZE = 1.5F;
    private static final float FLAKE_SIZE = 1.0F;
    /** How much of the result's own colour a batter takes; the rest stays as light as a batter is. */
    private static final float BATTER_SHARE = 0.65F;

    private static final Map<Item, BowlMass> KNOWN = new HashMap<>();

    private final ResourceLocation texture;
    /** ARGB, what it looks like from afar: the mixture comes to this colour as it turns into it. */
    private final int color;
    /** A batter takes the colour of what it makes; the other kinds are drawn in their own. */
    private final boolean tinted;
    /** How it keeps the whisk's furrows and is lumpy, 0 to 1 ({@link MassLook}). */
    private final float firmness;
    /** How hard a full bowl of it is to whisk through, 0 to 1: a dough the hardest, a salad's loose leaves light. */
    private final float heaviness;
    /**
     * Of each thing thrown in, how many pieces it holds (porridge, salad), 0 for the kinds that take it in whole; it holds six times
     * that in all. A salad is mostly its pieces, its own colour only the shade between its leaves.
     */
    private final int heldEach;

    BowlMass(String texture, int color, boolean tinted, float firmness, float heaviness, int heldEach) {
        this.texture = ResourceLocation.fromNamespaceAndPath(HandsOnLetsDo.MOD_ID, "block/bowl/" + texture);
        this.color = color;
        this.tinted = tinted;
        this.firmness = firmness;
        this.heaviness = heaviness;
        this.heldEach = heldEach;
    }

    /**
     * The mass it is, for a result of colour {@code result}: its colour alone, plain, so that only the relief shows on it (a painted
     * texture laid its texels slantwise over the relief).
     */
    MassLook look(int result) {
        // No sheen either: its highlights lay over the mass as pale squares.
        return MassLook.of(LiquidLook.LIQUID_SPRITE).firm(firmness).glossy(0.0F).grainy(0.0F).tinted(color(result) & 0xFFFFFF);
    }


    float heaviness() {
        return heaviness;
    }

    /** Whether what it holds includes the meat and fish that went in, as chunks: cat food is chunks in a paste of its own. */
    boolean holdsMeat() {
        return this == CAT_FOOD;
    }

    boolean holdsPieces() {
        return heldEach > 0;
    }

    int heldEach() {
        return heldEach;
    }

    int heldMost() {
        return heldEach * 6;
    }

    ResourceLocation texture() {
        return texture;
    }

    /** The colour the mixture comes to, ARGB, for a result of colour {@code result}. */
    int color(int result) {
        return tinted ? KitchenLooks.mix(0xFFFFFFFF, result, BATTER_SHARE) : color;
    }

    /** The tint its sheet is drawn with (alpha left to the caller), RGB. */
    int tint(int result) {
        return tinted ? color(result) & 0xFFFFFF : 0xFFFFFF;
    }

    /**
     * The kind {@code result} is, by its name first and else by what goes into it. Once known for an item it stays: the last set
     * leaves no ingredients behind, and the bowlful must not change its look as it is finished.
     */
    static BowlMass of(ItemStack result, List<ItemStack> ingredients) {
        if (result.isEmpty()) {
            return BATTER;
        }
        BowlMass known = KNOWN.get(result.getItem());
        if (known != null) {
            return known;
        }
        BowlMass kind = read(result, ingredients);
        if (kind != BATTER || !ingredients.isEmpty()) {
            KNOWN.put(result.getItem(), kind);
        }
        return kind;
    }

    private static BowlMass read(ItemStack result, List<ItemStack> ingredients) {
        String name = path(result);
        boolean berries = has(name, "berr", "cherr") || ingredients.stream().anyMatch(stack -> has(path(stack), "berr", "cherr"));
        if (has(name, "butter") && !has(name, "butterscotch")) {
            return BUTTER;
        }
        if (has(name, "dough", "pastry", "crust")) {
            return DOUGH;
        }
        if (has(name, "oatmeal", "porridge", "muesli", "granola", "cereal")) {
            return berries ? PORRIDGE_BERRIES : PORRIDGE;
        }
        if (has(name, "salad", "slaw")) {
            return SALAD;
        }
        if (has(name, "cat_food")) {
            return CAT_FOOD;
        }
        if (has(name, "dog_food", "pet_food", "mince", "meatball", "patty")) {
            return MINCE;
        }
        long meat = ingredients.stream().filter(BowlMass::meat).count();
        if (!ingredients.isEmpty() && meat * 2 >= ingredients.size()) {
            return MINCE;
        }
        return BATTER;
    }

    /**
     * The pieces of {@code stack} held in a porridge or a salad: leaves in three shades, so that a bowl of them reads as torn leaves; a
     * ring of onion, a slice of carrot, else a chunk of fruit.
     */
    static List<Floater> pieces(ItemStack stack) {
        String name = path(stack);
        int color = FoodColors.main(stack) & 0xFFFFFF;
        if (has(name, "lettuce", "cabbage", "spinach", "kale", "herb", "leaf", "leaves", "basil", "mint", "parsley")) {
            int lighter = FoodColors.lighter(color, 0.18F);
            int darker = KitchenLooks.mix(0xFF000000 | color, 0xFF000000, 0.25F) & 0xFFFFFF;
            return List.of(new Floater(Floater.LEAF, color, LEAF_SIZE), new Floater(Floater.LEAF, lighter, LEAF_SIZE),
                    new Floater(Floater.LEAF, darker, LEAF_SIZE * 0.85F));
        }
        return List.of(piece(name, color));
    }

    private static Floater piece(String name, int color) {
        if (has(name, "beef", "pork", "chicken", "mutton", "rabbit", "fish", "cod", "salmon", "meat", "bacon", "ham")) {
            // A chunk of meat or fish, lighter where it is cut.
            return new Floater(Floater.CHUNK, FoodColors.lighter(color, 0.08F), MEAT_SIZE);
        }
        if (has(name, "onion", "garlic", "leek")) {
            // Cut, it is white inside whatever its skin.
            return new Floater(Floater.SHRED, KitchenLooks.mix(0xFFF2EEE4, 0xFF000000 | color, 0.2F) & 0xFFFFFF, SHRED_SIZE);
        }
        if (has(name, "carrot", "beet", "radish", "cucumber", "zucchini", "potato", "corn", "pepper")) {
            return new Floater(Floater.COIN, color, SLICE_SIZE);
        }
        // Cut fruit shows its lighter inside.
        return new Floater(Floater.CHUNK, FoodColors.lighter(color, 0.12F), CHUNK_SIZE);
    }

    /** A flake of what was shaken in: a rolled oat in a porridge, a flat oval a little lighter than the grain. */
    static Floater flake(ItemStack stack) {
        return new Floater(Floater.LEAF, FoodColors.lighter(FoodColors.main(stack) & 0xFFFFFF, 0.3F), FLAKE_SIZE);
    }

    private static boolean meat(ItemStack stack) {
        return stack.is(ItemTags.MEAT);
    }

    private static String path(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
    }

    private static boolean has(String name, String... parts) {
        for (String part : parts) {
            if (name.contains(part)) {
                return true;
            }
        }
        return false;
    }
}
