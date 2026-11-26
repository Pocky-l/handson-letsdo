package com.pockyl.handson_letsdo.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.state.TankSpec;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_letsdo.HandsOnConfig;
import com.pockyl.handson_letsdo.HandsOnLetsDo;

import java.util.function.Supplier;

/**
 * HerbalBrews' brewing cauldron as a station, as this mod's Farm &amp; Charm pot: the cook pours three potions in from their bottles,
 * each in its colour, throws the herbal infusion in after them, and the cauldron brews them over its own fire into one flask with
 * every effect of the three; the cook stirs the brew with the cauldron's own stick to hurry it and fills the flask at the cauldron.
 * Its slots are windows on the cauldron's own inventory: hoppers reach it and it keeps its items whoever is at it, and the flask is
 * made by HerbalBrews' own tick. The block is hidden wherever the scene is drawn and the scene shows the same cauldron, open: every
 * client near it runs the logic on its copy of the cauldron (the cauldron tells them what it holds), so that the potions, the
 * infusion and the brew show in the world too.
 */
public final class CauldronStation {
    public static final ResourceLocation ID = HandsOnLetsDo.id("brewing_cauldron");

    /** The three potions, the cauldron's own slots 0 to 2: poured in, shown as the brew, never as items. */
    public static final String POTIONS = "potions";
    /** The flask the brew became, the cauldron's own slot 3: it waits as the brew in the cauldron and is filled there. */
    public static final String FLASK = "flask";
    /** The herbal infusion, the cauldron's own slot 4: afloat on the brew. */
    public static final String CATALYST = "catalyst";
    public static final SlotRef FLASK_SLOT = SlotRef.of(FLASK, 0);
    public static final SlotRef CATALYST_SLOT = SlotRef.of(CATALYST, 0);
    /** The brew, a liquid the scene fills by itself from what the cauldron holds: the station's tank only names the node. */
    public static final String BREW = "brew";

    /**
     * Stick turns that brew a whole brew, besides the cauldron's own count: at a hand's steady pace (about a turn a second) some
     * eight seconds, against HerbalBrews' sixty for the brew left alone. Each turn hurries it by an eighth of its time.
     */
    public static final int TURNS = 8;
    /** The fastest the server counts the stick going round: a whole turn in this many ticks. */
    public static final float FASTEST_TURN_TICKS = 8.0F;
    /** Where the stick's end may go in the brew, pixels from the middle either way: the opening is 10 px, the stick 2 px thick. */
    public static final float STICK_REACH = 3.0F;
    /** A stick not reported for this many ticks has been let go. */
    public static final int STIR_GAP = 10;

    /**
     * What the logic tells the clients in the station's data: whether a brew is on; the game time it began at (earlier as it is
     * stirred) and the ticks it takes; whether the cauldron makes anything of its potions (always on 1.21.1);
     * the colour the brew becomes (RGB); whether a cook stirs now, and where the stick's end is, pixels from the middle in the
     * cauldron's own axes.
     */
    public static final String BREWING = "brewing";
    public static final String STARTED = "started";
    public static final String TOTAL = "total";
    public static final String MAKES = "makes";
    public static final String COLOR = "color";
    public static final String STIRRING = "stirring";
    public static final String STICK_X = "stick_x";
    public static final String STICK_Z = "stick_z";

    /**
     * A custom action: the potion on the cursor ({@link #FROM}: in that inventory slot instead) is poured into the cauldron, into
     * its first empty potion slot.
     */
    public static final String ACTION_POUR = "pour";
    /**
     * A custom action: the glass bottle on the cursor (or in inventory slot {@link #FROM}) draws the last potion poured back out of
     * the cauldron, before it is brewed.
     */
    public static final String ACTION_DRAW = "draw";
    /** A custom action: the stick moved; data: where its end is now, {@link #X} and {@link #Z}, pixels from the middle. */
    public static final String ACTION_STIR = "stir";
    public static final String X = "x";
    public static final String Z = "z";
    /** A custom action: the flask that waits is filled at the cauldron and goes to the cook. */
    public static final String ACTION_TAKE = "take";
    /**
     * A custom action: the infusion on the cursor ({@link #FROM}: in that inventory slot instead) is thrown in, one, or with {@link #ALL}
     * the stack: what the cauldron takes into its infusion slot as its menu does.
     */
    public static final String ACTION_THROW = "throw";
    /**
     * A custom action: the brew picked in the book; the potions the cauldron lacks are poured in from the inventory, and an infusion
     * thrown in when it has none, with {@link #ALL} the stack.
     */
    public static final String ACTION_FILL = "fill";
    public static final String ALL = "all";
    public static final String FROM = "from";

    /** Played for everybody who sees it: a potion poured in. Data: {@link #ITEM}, the potion's item; {@link #COLOR}, its colour. */
    public static final ResourceLocation EFFECT_POURED = HandsOnLetsDo.id("cauldron_poured");
    /** Played for everybody who sees it: a potion drawn back out into a glass bottle. Data: as {@link #EFFECT_POURED}. */
    public static final ResourceLocation EFFECT_DRAWN = HandsOnLetsDo.id("cauldron_drawn");
    /** Played for everybody who sees it: the brew is done, the flask waits in the cauldron. Data: {@link #COLOR}. */
    public static final ResourceLocation EFFECT_BREWED = HandsOnLetsDo.id("cauldron_brewed");
    /** Played for everybody who sees it: the flask was filled at the cauldron. Data: {@link #COLOR}. */
    public static final ResourceLocation EFFECT_TAKEN = HandsOnLetsDo.id("cauldron_taken");
    public static final String ITEM = "item";
    public static final String MOUTH = "mouth";

    public static final Supplier<Block> CAULDRON = () -> BuiltInRegistries.BLOCK.get(Cauldron.BLOCK);

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(POTIONS, Cauldron.POTIONS, SlotRules.DEFAULT.kept().accepts(Cauldron::isPotion))
            .slots(FLASK, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .slots(CATALYST, 1, SlotRules.DEFAULT.kept().accepts(Cauldron::isCatalyst))
            .tank(BREW, TankSpec.of(1))
            .build();

    /** The cauldron faces the cook, whichever side they come from; the code turns it back to the way the block stands. */
    public static final StationType TYPE = Diegesis.station(ID)
            .block(CAULDRON)
            .schema(SCHEMA)
            .logic(CauldronLogic::new)
            .orientation(StationType.Orientation.PLAYER)
            .openWhen(CauldronStation::opens)
            .hidesBlock()
            .idleLogic()
            .build();

    private static boolean registered;

    private CauldronStation() {
    }

    /** With HerbalBrews installed and the station left on in the config; otherwise the cauldron keeps its own menu. */
    public static void register() {
        if (HandsOnLetsDo.has(Cauldron.MOD_ID) && HandsOnConfig.brewingCauldron()) {
            Diegesis.register(TYPE);
            registered = true;
        }
    }

    /** Whether the station is on: only then does the cauldron tell its clients what it holds. */
    public static boolean isRegistered() {
        return registered;
    }

    /** Sneaking keeps the cauldron's own use, its menu. A cauldron that is not the one this mod knows keeps its menu too. */
    private static boolean opens(Player player, BlockPos pos, BlockState state) {
        return !player.isSecondaryUseActive() && Cauldron.of(player.level().getBlockEntity(pos)) != null;
    }
}
