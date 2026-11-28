package com.pockyl.handson_letsdo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.MeshShape;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.scene.Visual;

import java.util.function.Predicate;

/**
 * The badge every station shows beside its block to the cook at it, the same at all of them, as Hands-On Delight's: a dark plate with
 * cut corners and, on it, the icon of what this step needs from the cook (the bowl a soup waits for, the fuel a cold stove lacks, the
 * crank a load waits to be turned by), or of what it makes while it makes itself or can simply be taken; under it a bar for how far
 * the step is; under the bar how much is ready (gold, at the left) and how much is still to be made (pale, at the right); a time left
 * for a step that takes long by itself. What the cook lacks (a container, fuel, heat) blinks. No text: the names and times are in
 * the icon's tooltip. Drawn in the pixels of the icon, 16 of them wide and centred on the badge, x to the right and z down as the
 * cook sees it.
 */
final class StepBadge {
    /** The badge's nodes in every scene: the plate, the track, what is lit, the pale count, the icon. */
    static final String[] PARTS = {"badge", "dial_track", "dial", "dial_more", "preview"};
    static final int PLATE_COLOR = 0x8C1E140C;
    static final int DIAL_COLOR = 0xFFFFCB6B;
    static final int TRACK_COLOR = 0x50FFF2DC;
    /** What is still to come, and the time: pale, unlike what is ready. */
    static final int MORE_COLOR = 0xC8FFF2DC;
    /** A step that waits for something besides the cook's hand (a hatch, a door) stands still, greyed. */
    static final int WAITING_COLOR = 0xFFA8A49C;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "block/white_concrete");
    private static final Vector3f ICON_MIDDLE = new Vector3f(8.0F, 8.0F, 8.0F);
    private static final int BAR_LEFT = -7;
    private static final int BAR_TOP = 10;
    private static final int BAR_LENGTH = 14;
    private static final int COUNT_TOP = 12;
    private static final int TIME_TOP = 18;
    private static final int MOST = 99;
    /** What is lacking blinks, as Hands-On Delight's bowl blinks the container it waits for. */
    private static final float BLINK_RATE = 9.0F;
    private static final float BLINK_SHOWN = -0.3F;

    /**
     * What a step needs that is not an item: the gesture's tool, drawn in the badge's pixels where the icon goes, and heat.
     */
    enum Glyph {
        /** The pot's own stick, taken to stir. */
        SPOON(new String[] {
                "...........##.",
                "..........##..",
                ".........##...",
                "........##....",
                ".......##.....",
                "......##......",
                ".....##.......",
                "..####........",
                ".######.......",
                "########......",
                "########......",
                ".######.......",
                "..####........"}),
        /** The roaster, taken by a handle and swirled round. */
        PAN(new String[] {
                "....######....",
                "..##......##..",
                ".#..........#.",
                ".#.........###",
                "............#.",
                "..............",
                "##############",
                ".############.",
                ".############.",
                "..##########..",
                "...########..."}),
        /** The mincer's crank, turned round. */
        CRANK(new String[] {
                "....######....",
                "..##......##..",
                ".#..........#.",
                ".#..........#.",
                "#.....##.....#",
                "#.....###....#",
                "#......###...#",
                "#.......###..#",
                ".#.......#####",
                ".#........####",
                "..##......####",
                "....######...."}),
        /** The bowl's whisk, its loops over its handle. */
        WHISK(new String[] {
                ".....####.....",
                "...##.##.##...",
                "..#..#..#..#..",
                "..#..#..#..#..",
                "..#..#..#..#..",
                "...#.#..#.#...",
                "....##..##....",
                ".....####.....",
                "......##......",
                "......##......",
                "......##......",
                "......##......",
                "......##......"}),
        /** The press's screw, its cap borne down on. */
        PRESS(new String[] {
                "##############",
                "##############",
                "......##......",
                ".....####.....",
                "......##......",
                ".....####.....",
                "......##......",
                "..##########..",
                "..##########..",
                "..............",
                "....######....",
                ".....####.....",
                "......##......"}),
        /** Heat under the block. */
        FLAME(new String[] {
                "......#.......",
                ".....##.......",
                ".....###......",
                "....####..#...",
                "....#####.##..",
                "...#########..",
                "..###########.",
                "..####..#####.",
                ".####....####.",
                ".####....####.",
                "..###...####..",
                "...########...",
                ".....####....."}),
        /** A hatch or a lid, put back on. */
        LID(new String[] {
                "......##......",
                "......##......",
                "....######....",
                "..##########..",
                ".############.",
                "..............",
                "......##......",
                "......##......",
                "....######....",
                ".....####.....",
                "......##......",
                "..............",
                "##############"}),
        /** The wok tossed: what lies in it flies up over its rim. */
        WOK(new String[] {
                "......##......",
                "..#........#..",
                ".....#..#.....",
                "..............",
                "..............",
                "#............#",
                "##..........##",
                ".############.",
                ".############.",
                "..##########..",
                "...########...",
                "..............",
                ".............."}),
        /** A door, shut. */
        DOOR(new String[] {
                "..##########..",
                "..#........#..",
                "..#........#..",
                "..#........#..",
                "..#.....##.#..",
                "..#.....##.#..",
                "..#........#..",
                "..#........#..",
                "..#........#..",
                "..##########..",
                "..............",
                "#####....#####",
                "....######...."});

        private final String[] rows;

        Glyph(String[] rows) {
            this.rows = rows;
        }
    }

    /**
     * One look of the badge. {@code item} or {@code glyph} is the icon (the glyph when both); {@code lacking}: the cook has it not, so it
     * blinks; {@code ready} and {@code more}: how much is ready and how much is still to be made, in what comes out; {@code progress}: how
     * far the step is, 0 to 1, NaN for no bar; {@code seconds}: the time it still takes by itself, 0 for none; {@code waiting}: the step
     * stands still for something besides the hand, greyed; {@code tooltip}: what the icon says under the pointer.
     */
    record Step(ItemStack item, Glyph glyph, boolean lacking, int ready, int more, float progress, int seconds, boolean waiting,
            Component tooltip) {
    }

    /** The station's own marks on the badge besides the shared ones (stars over the icon, gauges beside it); {@code key} says when they change. */
    interface Extras {
        Extras NONE = new Extras() {
            @Override
            public String key() {
                return "";
            }
        };

        String key();

        default void track(MeshShape.Builder track) {
        }

        default void lit(MeshShape.Builder lit) {
        }
    }

    private final int halfWidth;
    private final int top;
    private final int bottom;
    private String drawnLit;
    private String drawnTrack;
    private String drawnMore;
    private Item shownItem;
    private boolean drawnPlate;

    /**
     * A badge {@code halfWidth} pixels each side of the middle (10, or wider for gauges), from {@code top} (the icon's top is -8; stars
     * over it need more), with a row for the time under the counts when {@code timed}.
     */
    StepBadge(int halfWidth, int top, boolean timed) {
        this.halfWidth = halfWidth;
        this.top = top;
        this.bottom = timed ? TIME_TOP + 5 : COUNT_TOP + 5;
    }

    /** The scene is new: its badge nodes get their colours, are drawn over everything for the cook, and are drawn anew. */
    void ready(StationView view) {
        drawnLit = null;
        drawnTrack = null;
        drawnMore = null;
        shownItem = null;
        drawnPlate = false;
        view.node("badge").ifPresent(node -> node.setTint(PLATE_COLOR));
        view.node("dial").ifPresent(node -> node.setTint(DIAL_COLOR));
        view.node("dial_track").ifPresent(node -> node.setTint(TRACK_COLOR));
        view.node("dial_more").ifPresent(node -> node.setTint(MORE_COLOR));
        // The badge stays in sight over the block and whatever stands beside it, as the book does.
        for (String part : PARTS) {
            view.node(part).ifPresent(node -> node.setOnTop(true));
        }
    }

    /** The badge as {@code step} says, shown or not; {@code time}: the station's clock, seconds. */
    void show(StationView view, boolean shown, Step step, Extras extras, float time) {
        view.node("badge").ifPresent(node -> {
            node.setVisible(shown);
            if (!drawnPlate) {
                drawnPlate = true;
                node.setShape(plate(), TEXTURE, true);
            }
        });
        for (String part : new String[] {"dial_track", "dial", "dial_more"}) {
            view.node(part).ifPresent(node -> node.setVisible(shown));
        }
        view.node("preview").ifPresent(node -> node.setVisible(shown));
        if (!shown || step == null) {
            return;
        }
        boolean blinkShown = !step.lacking() || Mth.sin(time * BLINK_RATE) > BLINK_SHOWN;
        boolean bar = !Float.isNaN(step.progress());
        int lit = bar ? Math.round(Mth.clamp(step.progress(), 0.0F, 1.0F) * BAR_LENGTH) : 0;
        int ready = Math.min(step.ready(), MOST);
        int more = Math.min(step.more(), MOST);
        boolean glyph = step.glyph() != null;

        String trackKey = bar + "/" + extras.key();
        if (!trackKey.equals(drawnTrack)) {
            drawnTrack = trackKey;
            MeshShape.Builder track = MeshShape.builder();
            if (bar) {
                Badge.pixels(track, BAR_LEFT, BAR_TOP, BAR_LENGTH, 1);
            }
            extras.track(track);
            view.node("dial_track").ifPresent(node -> node.setShape(track.build(), TEXTURE, true));
        }
        String litKey = lit + "/" + ready + "/" + (glyph && blinkShown ? step.glyph().name() : "") + "/" + extras.key();
        if (!litKey.equals(drawnLit)) {
            drawnLit = litKey;
            MeshShape.Builder dial = MeshShape.builder();
            if (lit > 0) {
                Badge.pixels(dial, BAR_LEFT, BAR_TOP, lit, 1);
            }
            if (ready > 0) {
                Badge.number(dial, Integer.toString(ready), BAR_LEFT, COUNT_TOP);
            }
            if (glyph && blinkShown) {
                glyph(dial, step.glyph().rows);
            }
            extras.lit(dial);
            view.node("dial").ifPresent(node -> node.setShape(dial.build(), TEXTURE, true));
        }
        view.node("dial").ifPresent(node -> node.setTint(step.waiting() ? WAITING_COLOR : DIAL_COLOR));
        String left = step.seconds() <= 0 ? "" : step.seconds() >= 60
                ? step.seconds() / 60 + ":" + String.format("%02d", step.seconds() % 60) : Integer.toString(step.seconds());
        String moreKey = more + "/" + left;
        if (!moreKey.equals(drawnMore)) {
            drawnMore = moreKey;
            MeshShape.Builder pale = MeshShape.builder();
            if (more > 0) {
                String digits = Integer.toString(more);
                Badge.number(pale, digits, BAR_LEFT + BAR_LENGTH - Badge.width(digits), COUNT_TOP);
            }
            boolean timed = !left.isEmpty() && bottom > TIME_TOP;
            if (timed) {
                Badge.text(pale, left, TIME_TOP);
            }
            view.node("dial_more").ifPresent(node -> node.setShape(more > 0 || timed ? pale.build() : null, TEXTURE, true));
        }
        view.node("preview").ifPresent(node -> {
            Item item = glyph || step.item().isEmpty() ? null : step.item().getItem();
            if (item != shownItem) {
                shownItem = item;
                node.setVisual(item == null ? null : Visual.ofItem(item).withAnchor(ICON_MIDDLE));
            }
            // A glyph is drawn by the dial; the icon's node stays, unseen, for its tooltip.
            node.setTint(glyph || blinkShown ? 0xFFFFFFFF : 0x00FFFFFF);
            node.setTooltip(step.tooltip());
        });
    }

    /** Whether the local cook has nothing {@code wanted} on the cursor or in the inventory: what the step needs then blinks. */
    static boolean lacks(Predicate<ItemStack> wanted) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        if (wanted.test(player.inventoryMenu.getCarried())) {
            return false;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (wanted.test(inventory.getItem(slot))) {
                return false;
            }
        }
        return true;
    }

    /** Every part of the badge drawn {@code factor} times its size: coming with the camera, swelling when something is done. */
    void scale(StationView view, float factor) {
        for (String part : PARTS) {
            view.node(part).ifPresent(node -> node.scaleTo(factor, 0.0F));
        }
    }

    /** As {@link #scale}, with the icon {@code icon} times bigger again: what waits breathes. */
    void scale(StationView view, float factor, float icon) {
        for (String part : PARTS) {
            float by = "preview".equals(part) ? factor * icon : factor;
            view.node(part).ifPresent(node -> node.scaleTo(by, 0.0F));
        }
    }

    /** The dark plate with cut corners, as wide and as tall as this badge is. */
    private MeshShape plate() {
        MeshShape.Builder shape = MeshShape.builder();
        Badge.pixels(shape, -halfWidth, top, 2 * halfWidth, bottom - top);
        Badge.pixels(shape, -halfWidth + 1, top - 1, 2 * halfWidth - 2, 1);
        Badge.pixels(shape, -halfWidth + 1, bottom, 2 * halfWidth - 2, 1);
        return shape.build();
    }

    /** A glyph in the middle of where the icon goes. */
    private static void glyph(MeshShape.Builder shape, String[] rows) {
        int left = -rows[0].length() / 2;
        int top = -rows.length / 2;
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].length(); column++) {
                if (rows[row].charAt(column) == '#') {
                    Badge.pixels(shape, left + column, top + row, 1, 1);
                }
            }
        }
    }
}
