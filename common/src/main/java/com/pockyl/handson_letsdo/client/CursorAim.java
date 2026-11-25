package com.pockyl.handson_letsdo.client;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.StationView;

import java.util.Optional;

/**
 * Where a thing offered to an empty hand is drawn: right under the cursor, over the point of a level target (a floor, a liquid's
 * surface, an opening) that the cursor points at. Taken on a plane higher up instead, the cursor over the front part of a deep
 * target met that plane far behind it, and the thing kept within the opening stood far from the cursor.
 */
final class CursorAim {
    private CursorAim() {
    }

    /**
     * The point of the cursor's line {@code lift} over the level target at {@code floor} (scene pixels), the point of the target
     * under it kept within {@code x0..x1}, {@code z0..z1}: the thing drawn there covers the cursor, over the part of the target it
     * points at, and near an edge it stays inside. Empty when the cursor looks away from the plane.
     */
    static Optional<Vector3f> clamped(StationView view, float floor, float lift, float x0, float x1, float z0, float z1) {
        Optional<Vector3f> hit = view.cursorOn(floor);
        Optional<Vector3f> up = view.cursorOn(floor + lift);
        if (hit.isEmpty() || up.isEmpty()) {
            return Optional.empty();
        }
        Vector3f on = hit.get();
        return Optional.of(new Vector3f(up.get()).add(Mth.clamp(on.x, x0, x1) - on.x, 0.0F, Mth.clamp(on.z, z0, z1) - on.z));
    }

    /**
     * As {@link #clamped}, but empty while the point of the target under the cursor lies outside {@code x0..x1}, {@code z0..z1}:
     * nothing is offered beside the target.
     */
    static Optional<Vector3f> within(StationView view, float floor, float lift, float x0, float x1, float z0, float z1) {
        Optional<Vector3f> hit = view.cursorOn(floor);
        if (hit.isEmpty() || hit.get().x < x0 || hit.get().x > x1 || hit.get().z < z0 || hit.get().z > z1) {
            return Optional.empty();
        }
        return view.cursorOn(floor + lift);
    }
}
