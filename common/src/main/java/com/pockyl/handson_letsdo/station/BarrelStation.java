package com.pockyl.handson_letsdo.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.state.TankContents;
import com.pockyl.diegesis.api.state.TankSpec;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_letsdo.HandsOnConfig;
import com.pockyl.handson_letsdo.HandsOnLetsDo;

import java.util.function.Supplier;

/**
 * Vinery's fermentation barrel as a station: the cook takes the hatch in its top off with a click, juice is poured in at the
 * opening and what ferments is thrown in there; everything in it ferments together and the wine waits in the barrel until it is
 * drawn at the bung into empty wine bottles ({@link BarrelBrew}). No slot is seen: everything is put in and taken out by hand.
 */
public final class BarrelStation {
    public static final String MOD_ID = "vinery";
    public static final ResourceLocation ID = HandsOnLetsDo.id("fermentation_barrel");

    /** A juice bottle that waits to be poured in: Vinery pours it as soon as the barrel takes it. Nothing is put here by hand. */
    public static final String WAITING = "waiting";
    public static final String INGREDIENTS = "ingredients";
    /** Empty wine bottles: one is filled for each wine that asks for one. */
    public static final String BOTTLES = "bottles";
    /** The wine that is done. */
    public static final String SERVED = "served";
    public static final SlotRef WAITING_SLOT = SlotRef.of(WAITING, 0);
    public static final SlotRef BOTTLE_SLOT = SlotRef.of(BOTTLES, 0);
    public static final SlotRef SERVED_SLOT = SlotRef.of(SERVED, 0);

    /** The juice in the barrel: its liquid is named after the juice ({@link #liquid}), in thousandths of a full barrel. */
    public static final String JUICE = "juice";
    public static final int FULL = 1000;

    /**
     * What the logic tells the clients in the station's data: how much juice stands in the barrel, how much it holds and how much
     * a bottle is (Vinery's measure and settings); the ticks a wine takes there, and the game time the wine in it began to ferment
     * at, absent while nothing ferments; the wine its contents make, absent for none.
     */
    public static final String LEVEL = "level";
    public static final String CAPACITY = "capacity";
    public static final String PER_BOTTLE = "per_bottle";
    public static final String STARTED = "started";
    public static final String TOTAL = "total";
    /** While the hatch is off: the ticks the wine has fermented so far, standing still, in place of {@link #STARTED}. */
    public static final String HELD = "held";
    public static final String MAKING = "making";
    /** How many wines what lies in the barrel makes in one go, while it makes one. */
    public static final String BATCH = "batch";
    /**
     * The wine that waits in the barrel, absent for none: how many bottles of it, its bottle item, the juice it was made of (its
     * colour), how much of the barrel it fills in Vinery's measure, and whether it needs an empty bottle to be drawn.
     */
    public static final String STORED = "stored";
    public static final String STORED_WINE = "stored_wine";
    public static final String STORED_JUICE = "stored_juice";
    public static final String STORED_VOLUME = "stored_volume";
    public static final String STORED_BOTTLE = "stored_bottle";

    /**
     * Whether the hatch is off (1) or on (0). The cook takes it off and puts it back with a click on it ({@link #ACTION_LID}); only
     * with it off does anything go in at the top. Pouring a bottle or a wine picked in the book takes it off by themselves. The
     * wine ferments only with it on: while it is off the count waits ({@link #HELD}), and the barrel puts it back on behind the
     * last cook.
     */
    public static final String LID = "lid";
    /** A custom action: the hatch is taken off or put back; data {@link #OPEN}. */
    public static final String ACTION_LID = "lid";
    public static final String OPEN = "open";

    /**
     * A custom action: the juice bottle on the cursor is poured in at the opening, one, or with {@link #ALL} as many as the barrel
     * takes; {@link #FROM}: the stack in that inventory slot instead of the cursor's (Shift + click in the inventory).
     */
    public static final String ACTION_POUR = "pour";
    public static final String ALL = "all";
    public static final String FROM = "from";
    /** A custom action: a wine picked in the book; its juice, ingredients and bottles go in from the inventory. Data: {@link #WINE}. */
    public static final String ACTION_FILL = "fill";
    public static final String WINE = "wine";
    /**
     * A custom action at the bung: the wine that waits is drawn, one bottle, or with {@link #ALL} every one, into the inventory; an
     * empty wine bottle on the cursor (or in inventory slot {@link #FROM}) is filled, and with no wine waiting it is set with the
     * others before the head.
     */
    public static final String ACTION_DRAW = "draw";
    /** A custom action at the opening: empty wine bottles on the cursor take the juice back, one bottle's worth each ({@link #ALL}). */
    public static final String ACTION_DRAW_BACK = "draw_back";

    /** Played for everybody who sees it: juice poured in at the opening; a wine done and bottled. */
    public static final ResourceLocation EFFECT_POURED = HandsOnLetsDo.id("poured");
    public static final ResourceLocation EFFECT_BOTTLED = HandsOnLetsDo.id("bottled");
    /** Played for everybody who sees it: wine drawn at the bung. Data: the wine ({@link #VESSEL}) and how many. */
    public static final ResourceLocation EFFECT_DRAWN = HandsOnLetsDo.id("drawn");
    /** Played for everybody who sees it: juice taken back into bottles at the opening. Data: the juice bottle, its juice, how many. */
    public static final ResourceLocation EFFECT_DRAWN_BACK = HandsOnLetsDo.id("drawn_back");
    /** In a poured effect: the bottle poured, its juice and how many. In a bottled effect: the wine and how many. */
    public static final String VESSEL = "vessel";
    public static final String COUNT = "count";
    public static final String MOUTH = "mouth";
    public static final String TAP = "tap";

    public static final Supplier<Block> BARREL = () -> BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(MOD_ID, "fermentation_barrel"));
    private static final ResourceLocation WINE_BOTTLE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "wine_bottle");

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(WAITING, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .slots(BOTTLES, 1, SlotRules.DEFAULT.kept().accepts(BarrelStation::isWineBottle))
            .slots(INGREDIENTS, Barrel.INGREDIENTS, SlotRules.DEFAULT.kept())
            .slots(SERVED, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .tank(JUICE, TankSpec.of(FULL))
            .control(LID, ControlSpec.stepped(0.0F, 1.0F, 1, 0.0F).locked())
            .build();

    /**
     * The barrel lies on its side and its head with the bung looks at whoever placed it, while its {@code facing} names the way
     * that player looked: the scene's front is the head. The block is hidden while somebody works at it, the scene shows it open.
     */
    public static final StationType TYPE = Diegesis.station(ID)
            .block(BARREL)
            .schema(SCHEMA)
            .logic(BarrelLogic::new)
            .orientation(StationType.Orientation.BLOCK_OPPOSITE)
            .openWhen(BarrelStation::opens)
            .hidesBlock()
            .idleLogic()
            .build();

    private BarrelStation() {
    }

    /** With Vinery installed and the station left on in the config; otherwise the barrel keeps Vinery's menu. */
    public static void register() {
        if (HandsOnLetsDo.has(MOD_ID) && HandsOnConfig.fermentationBarrel()) {
            Diegesis.register(TYPE);
        }
    }

    public static Item wineBottle() {
        return BuiltInRegistries.ITEM.get(WINE_BOTTLE);
    }

    public static boolean isWineBottle(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(WINE_BOTTLE);
    }

    /** Whether the hatch is off. */
    public static boolean isOpen(StationState state) {
        return state.getControl(LID) > 0.5F;
    }

    /** The liquid a juice is in the station's tank; {@link #juiceOf} reads it back. */
    public static ResourceLocation liquid(String juice) {
        ResourceLocation id = ResourceLocation.tryBuild(HandsOnLetsDo.MOD_ID, juice);
        return id != null ? id : HandsOnLetsDo.id("unknown");
    }

    /** Which juice stands in the barrel, as wines name it; empty for none. */
    public static String juiceOf(StationState state) {
        TankContents tank = state.getTank(JUICE);
        return tank.isEmpty() ? "" : tank.main().getPath();
    }

    /**
     * How many bottles of {@code stack} can be poured in now: none unless it is juice, of the kind that stands in the barrel or
     * into an empty one, and of the kind the wine that waits in it was made of; with room for a whole bottle beside the juice and
     * the wine (no part of one is poured), counting the bottles a hopper left waiting.
     */
    public static int pourable(StationState state, ItemStack stack) {
        String juice = VineryWines.juiceOf(stack);
        if (juice.isEmpty()) {
            return 0;
        }
        ItemStack waiting = state.getItem(WAITING_SLOT);
        if (!waiting.isEmpty() && !ItemStack.isSameItemSameComponents(waiting, stack)) {
            return 0;
        }
        var data = state.data();
        int level = data.getInt(LEVEL);
        String standing = juiceOf(state);
        if (level > 0 && !standing.equals(juice) || data.contains(STORED) && !data.getString(STORED_JUICE).equals(juice)) {
            return 0;
        }
        int room = (Math.max(1, data.getInt(CAPACITY)) - level - data.getInt(STORED_VOLUME)) / Math.max(1, data.getInt(PER_BOTTLE))
                - waiting.getCount();
        return Math.max(0, Math.min(room, stack.getMaxStackSize() - waiting.getCount()));
    }

    /**
     * Sneaking keeps Vinery's own use: it pours the juice away. A barrel that is not the one this mod knows keeps its menu.
     */
    private static boolean opens(Player player, BlockPos pos, BlockState state) {
        return !player.isSecondaryUseActive() && Barrel.of(player.level().getBlockEntity(pos)) != null;
    }
}
