package com.pockyl.handson_letsdo.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.Map;

/**
 * What differs between the ovens the oven station shows ({@link OvenBehavior}): how the door opens and sounds, how far the tray
 * comes out, where its places lie, where steam and smoke leave it, and the models that follow the block.
 *
 * @param door             how the door opens: one door dropping on its hinge at the bottom, one swinging aside on a hinge at its
 *                         side, or two leaves swinging out like gates
 * @param doorOpen         how far the door turns open, degrees
 * @param doorTime         seconds the door takes to open or shut
 * @param trayOut          how far the tray slides out, pixels
 * @param trayTime         seconds the tray takes
 * @param columnX          the places' columns on the tray, pixels from its middle, column 0 on the cook's left
 * @param rowZ             the places' rows, row 0 at the back
 * @param onTray           how high the places lie above the tray node
 * @param foodScale        how big a thing on a place is drawn, of a block
 * @param mouth            where the shimmer and the steam leave the open oven, scene pixels
 * @param smokeFrom        where smoke rises while it bakes shut
 * @param openSound        played as the door starts to open
 * @param closeSound       played as the door shuts
 * @param blockParts       the scene's nodes the block draws as well, shown only while the station hides the block
 * @param openWhileHot     the door stays open for as long as the oven burns, whatever the cook did with it: an oven whose mouth
 *                         stands open over its fire, with only its tray going in and out
 * @param bakesOpen        what lies on the tray bakes and browns with the door open too
 * @param cold             the tint of the node {@code inside} while nothing burns: the inside of an oven painted glowing
 * @param worldLook        the scene is the oven's look in the world too: it stands in for the block wherever it is drawn, with
 *                         nobody at it as well, so that opening and shutting the oven changes nothing but what moves; an oven
 *                         whose own model shows it otherwise (another door, its mouth open while it burns) would change its
 *                         look the moment a cook came
 */
record OvenLook(Door door, float doorOpen, float doorTime, float trayOut, float trayTime, float[] columnX, float[] rowZ, float onTray,
        float foodScale, Vector3f mouth, Vector3f smokeFrom, SoundEvent openSound, SoundEvent closeSound, String[] blockParts, Models models,
        boolean openWhileHot, boolean bakesOpen, int cold, boolean worldLook) {

    enum Door {
        /** One door hinged at its bottom edge, node {@code door}, dropping towards the cook. */
        DROP,
        /** One door hinged at its left edge (the cook's left), node {@code door}, swinging out aside like a cupboard's. */
        SIDE,
        /** Two leaves hinged at the sides, nodes {@code door_left} and {@code door_right}, swinging out like gates. */
        LEAVES
    }

    /** The models of the scene's nodes that follow the block's state: node id to model, given the block and whether it burns. */
    @FunctionalInterface
    interface Models {
        Models NONE = (state, burning, doorShut) -> Map.of();

        Map<String, ResourceLocation> of(BlockState state, boolean burning, boolean doorShut);
    }

    /**
     * Farm &amp; Charm's stove, a brick oven: the iron plate over its mouth is a door hinged at its left edge that swings aside
     * as a cupboard's does, and its grate slides out 7 px with three places side by side in the middle of what is out, each
     * within the grate's edges (out further, it hid the firebox from the camera). It bakes only shut, so the door stays as the
     * cook leaves it. In the world it is the scene's stove too: its own burning model has no plate over its mouth and shows what
     * bakes lying in it, where the scene's stove keeps it behind the shut door and its fuel burning in the firebox.
     */
    static final OvenLook STOVE = new OvenLook(Door.SIDE, 105.0F, 0.4F, 7.0F, 0.4F, new float[] {3.3F, 0.0F, -3.3F},
            new float[] {0.5F, 0.5F, 0.5F}, 0.3F, 0.21F, new Vector3f(8.0F, 10.0F, -1.0F), new Vector3f(8.0F, 16.4F, 6.5F),
            SoundEvents.IRON_DOOR_OPEN, SoundEvents.IRON_DOOR_CLOSE, new String[] {"oven"}, Models.NONE, false, false, 0xFF4A3C36, true);
}
