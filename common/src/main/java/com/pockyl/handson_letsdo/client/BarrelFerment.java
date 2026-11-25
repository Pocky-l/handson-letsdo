package com.pockyl.handson_letsdo.client;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.StationView;

/**
 * A wine working under the hatch: now and then the gas lifts it a little, foam beads along its seam and a soft blub comes from
 * inside. Seen and heard by whoever passes a barrel that ferments, and by the cook while the hatch is on.
 */
final class BarrelFerment {
    /** Seconds between two breaths at full vigour: at least this long, and up to this much longer. */
    private static final float REST = 2.2F;
    private static final float REST_SPREAD = 4.5F;
    /** How high the hatch knocks up as the gas lifts it, pixels, and for how long, seconds. */
    private static final float BUMP = 0.35F;
    private static final float BUMP_TIME = 0.28F;
    private static final int GAS = 0x80FFFFFF;

    private float rest = 1.5F;
    private float bumpAt = -10.0F;
    private float time;

    /**
     * @param vigour how hard the wine works now, 0 (it does not) to 1
     * @param shut   the scene's own barrel shows with its hatch on, which can knock
     * @param foam   the colour of the foam
     */
    void frame(StationView view, float dt, float vigour, boolean shut, int foam) {
        time += dt;
        if (vigour <= 0.0F) {
            rest = Math.max(rest, 1.0F);
            return;
        }
        rest -= dt * vigour;
        if (rest > 0.0F) {
            return;
        }
        RandomSource random = view.level().random;
        rest = REST + random.nextFloat() * REST_SPREAD;
        int pops = 3 + random.nextInt(4);
        for (int i = 0; i < pops; i++) {
            view.spawnParticles(DiegesisParticles.BUBBLE_POP, seam(random), new Vector3f(0.0F, 0.4F, 0.0F), 0.15F, 1, foam);
        }
        view.spawnParticles(DiegesisParticles.STEAM, seam(random), new Vector3f(0.0F, 2.2F, 0.0F), 0.5F, 1, GAS);
        // A deep, soft blub from inside, a little pop of the foam at the seam, and the hatch settling back with a light wooden tap.
        view.playSound(BarrelSounds.BLUB, "lid", 0.28F, 0.7F + random.nextFloat() * 0.15F);
        view.playSound(SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, "lid", 0.18F, 0.85F + random.nextFloat() * 0.25F);
        if (shut) {
            bumpAt = time;
            view.playSound(SoundEvents.WOODEN_TRAPDOOR_CLOSE, "lid", 0.06F, 1.7F + random.nextFloat() * 0.2F);
        }
    }

    /** The hatch comes off a working wine: a puff of gas and foam out of the opening. */
    void opened(StationView view, int foam) {
        RandomSource random = view.level().random;
        Vector3f middle = new Vector3f(8.0F, BarrelBehavior.SEAM + 0.5F, 8.0F);
        view.spawnParticles(DiegesisParticles.STEAM, middle, new Vector3f(0.0F, 3.0F, 0.0F), 2.0F, 4, GAS);
        for (int i = 0; i < 6; i++) {
            view.spawnParticles(DiegesisParticles.BUBBLE_POP, seam(random), new Vector3f(0.0F, 0.6F, 0.0F), 0.3F, 1, foam);
        }
        view.playSound(BarrelSounds.BURST, "lid", 0.3F, 0.9F + random.nextFloat() * 0.1F);
        rest = Math.max(rest, REST);
    }

    /** How far the gas lifts the hatch now, pixels. */
    float bump() {
        float k = (time - bumpAt) / BUMP_TIME;
        return k >= 0.0F && k < 1.0F ? BUMP * Mth.sin(Mth.PI * k) : 0.0F;
    }

    /** A random point of the seam round the hatch, scene pixels. */
    private static Vector3f seam(RandomSource random) {
        float width = BarrelBehavior.RIM_X1 - BarrelBehavior.RIM_X0;
        float depth = BarrelBehavior.RIM_Z1 - BarrelBehavior.RIM_Z0;
        float along = random.nextFloat() * 2.0F * (width + depth);
        float x;
        float z;
        if (along < width) {
            x = BarrelBehavior.RIM_X0 + along;
            z = BarrelBehavior.RIM_Z0;
        } else if (along < width + depth) {
            x = BarrelBehavior.RIM_X1;
            z = BarrelBehavior.RIM_Z0 + along - width;
        } else if (along < 2.0F * width + depth) {
            x = BarrelBehavior.RIM_X1 - (along - width - depth);
            z = BarrelBehavior.RIM_Z1;
        } else {
            x = BarrelBehavior.RIM_X0;
            z = BarrelBehavior.RIM_Z1 - (along - 2.0F * width - depth);
        }
        return new Vector3f(x, BarrelBehavior.SEAM, z);
    }
}
