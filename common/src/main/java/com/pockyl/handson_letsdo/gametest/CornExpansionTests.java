package com.pockyl.handson_letsdo.gametest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.pockyl.handson_letsdo.HandsOnLetsDo;
import com.pockyl.handson_letsdo.KitchenTags;
import com.pockyl.handson_letsdo.station.BowlRecipes;
import com.pockyl.handson_letsdo.station.Dish;
import com.pockyl.handson_letsdo.station.FarmCharmDishes;
import com.pockyl.handson_letsdo.station.FarmCharmStove;
import com.pockyl.handson_letsdo.station.KitchenOven;
import com.pockyl.handson_letsdo.station.MincerRecipes;
import com.pockyl.handson_letsdo.station.MincerStation;

import java.util.List;

/**
 * Corn Expansion, a Farm &amp; Charm addon, runs its recipes through our stations: corn dough in the crafting bowl, cornmeal and corn
 * flour at the mincer, polenta and grandma's cornbread on the stove, its dishes laid on the cutting board
 * ({@link BoardTests#cornExpansionsDishesAreLaidOnTheBoard}). Each is at its station, and the mincer mills its kernels instead of
 * mincing them as meat. The every-recipe tests of each station make them; this checks that they are there to make. It passes at
 * once without Corn Expansion.
 */
public final class CornExpansionTests {
    private static final String MOD_ID = "cornexpansion";

    private CornExpansionTests() {
    }

    private static Item item(String namespace, String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    private static boolean makes(List<Dish> recipes, Item result) {
        return recipes.stream().anyMatch(recipe -> recipe.result().is(result));
    }

    public static void cornExpansionsRecipesAreAtOurStations(GameTestHelper helper) {
        if (!HandsOnLetsDo.has(MOD_ID) || !HandsOnLetsDo.has(FarmCharmDishes.MOD_ID)) {
            helper.succeed();
            return;
        }
        Item cornmeal = item(MOD_ID, "cornmeal");
        Item cornFlour = item(MOD_ID, "corn_flour");
        List<Dish> mincing = MincerRecipes.all(helper.getLevel());
        helper.assertTrue(makes(mincing, cornmeal) && makes(mincing, cornFlour), "the mincer makes cornmeal and corn flour");
        helper.assertTrue(makes(BowlRecipes.all(helper.getLevel()), item(MOD_ID, "corn_dough")), "the crafting bowl makes corn dough");
        List<KitchenOven.Baking> baking = FarmCharmStove.recipesOf(helper.getLevel());
        for (String dish : List.of("polenta", "grandmas_cornbread")) {
            Item result = item(MOD_ID, dish);
            helper.assertTrue(baking.stream().anyMatch(recipe -> recipe.result().is(result)), "the stove bakes " + dish);
        }

        // Upstream calls kernels MEAT, its easiest class; the mincer turns them as lightly, but mills them: no juice, no squish.
        helper.assertTrue(MincerStation.MILLED.equals(MincerStation.looks("MEAT", new ItemStack(cornmeal)))
                && MincerStation.MILLED.equals(MincerStation.looks("MEAT", new ItemStack(cornFlour))), "kernels are milled");
        helper.assertTrue("MEAT".equals(MincerStation.looks("MEAT", new ItemStack(item(FarmCharmDishes.MOD_ID, "minced_beef")))),
                "beef is still minced as meat");
        helper.assertTrue(MincerStation.MILLED.equals(MincerStation.looks("STONE", new ItemStack(item(FarmCharmDishes.MOD_ID, "flour"))))
                && "STONE".equals(MincerStation.looks("STONE", new ItemStack(Items.COBBLESTONE))), "wheat is milled, stone is ground");
        helper.assertTrue(MincerStation.heaviness("MEAT", 8) < MincerStation.heaviness("STONE", 8), "how heavy it turns stays the class's");

        ItemStack syrup = new ItemStack(item(MOD_ID, "corn_syrup"));
        helper.assertTrue(KitchenTags.is(syrup, KitchenTags.POURED) && KitchenTags.is(syrup, KitchenTags.DISSOLVES),
                "corn syrup is poured from its bottle, as honey is");
        helper.succeed();
    }
}
