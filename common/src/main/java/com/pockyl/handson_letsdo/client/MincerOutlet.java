package com.pockyl.handson_letsdo.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.MeshShape;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_letsdo.station.MincerStation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The mincer's spout at work: what a load becomes comes out of the die one after another as the crank turns it through, each piece
 * pushed out of the hole and dropping onto the mincer's foot, where they lie in a heap; minced meat is pressed out first as strands
 * that break off. Crumbs and drops of what is ground come with it, dust for stone and metal, shavings for wood. Each piece of a load
 * shows as it comes out (the station tells how far the load is turned); the heap is the mincer's output that waits under the spout.
 * Scene pixels, as the mincer's model has them.
 */
final class MincerOutlet {
    /** The most pieces the heap shows. */
    static final int PILE = 8;
    /** The die's hole, where things come out, and how far out they are pushed before they drop. */
    private static final Vector3f DIE = new Vector3f(1.1F, 9.4F, 8.0F);
    private static final float PUSHED_OUT = 1.1F;
    /** Where the pieces lie on the foot under the spout: a heap, the later ones on the earlier. */
    private static final float[][] SPOTS = {{3.0F, 1.0F, 5.3F}, {4.3F, 1.0F, 5.7F}, {3.5F, 1.0F, 6.7F}, {2.3F, 1.0F, 6.3F},
            {3.6F, 1.4F, 5.9F}, {2.8F, 1.4F, 6.0F}, {4.0F, 1.4F, 6.4F}, {3.3F, 1.8F, 6.1F}};
    /** A piece: how wide, how thick in icon pixels (meat thicker). */
    private static final float SIZE = 2.6F;
    private static final float THICK = 1.2F;
    private static final float THICK_MEAT = 1.7F;
    /** Coming out: pushed out of the hole, then dropping onto the heap; the least time between two. */
    private static final float PUSH_TIME = 0.12F;
    private static final float DROP_TIME = 0.3F;
    private static final float NEXT_AFTER = 0.09F;
    /** Minced meat is pressed out as strands from the die's holes before a piece breaks off: how long at most, pixels. */
    private static final float STRAND = 1.9F;
    private static final float STRAND_BIT = 0.5F;
    private static final float[] STRAND_Z = {7.2F, 8.8F};
    /** What comes out with it, a second at a full pace of the crank: crumbs, drops, dust. */
    private static final float CRUMB_RATE = 7.0F;
    private static final float DROP_RATE = 2.5F;
    private static final float DUST_RATE = 6.0F;
    @SuppressWarnings("removal")
    private static final ResourceLocation PLAIN = new ResourceLocation("minecraft", "block/white_concrete");

    private final RandomSource random = RandomSource.create();
    private final Map<Item, Integer> perItem = new HashMap<>();
    private final Item[] item = new Item[PILE];
    /** Each piece: since when it is coming out (NaN: it lies in the heap), its turn, how it lies. */
    private final float[] age = new float[PILE];
    private final float[] yaw = new float[PILE];
    private final boolean[] meat = new boolean[PILE];
    private int shown;
    private float nextAt;
    private float clock;
    private float crumbs;
    private float drops;
    private float dust;
    private int strandBits = -1;
    private int strandColor;

    MincerOutlet() {
        java.util.Arrays.fill(age, Float.NaN);
    }

    /**
     * One frame: {@code lying} waits under the spout, {@code making} is what the load on the hopper becomes ({@code perInput} of it
     * for every item), {@code load} items of it turned through by {@code turned} (0 to 1); the crank goes at {@code pace} (0 to 1),
     * grinding what is of the class {@code kind}.
     */
    void frame(StationView view, float dt, ItemStack lying, ItemStack making, int perInput, int load, float turned, float pace, String kind) {
        clock += dt;
        if (!making.isEmpty()) {
            perItem.put(making.getItem(), Math.max(1, perInput));
        }
        // What lies there is as many pieces as items of a load it came of: what one item becomes, as last seen made.
        int per = lying.isEmpty() ? 1 : perItem.getOrDefault(lying.getItem(), 1);
        int fromLying = lying.isEmpty() ? 0 : Mth.ceil(lying.getCount() / (float) per);
        int pending = making.isEmpty() ? 0 : Math.min(load, Mth.floor(turned * load + 1.0e-3F));
        int want = Math.min(PILE, fromLying + pending);
        while (shown > want) {
            shown--;
            hide(view, shown);
        }
        while (shown < want && clock >= nextAt) {
            Item next = shown < fromLying && !lying.isEmpty() ? lying.getItem() : making.isEmpty() ? lying.getItem() : making.getItem();
            boolean quiet = shown < fromLying && lying.getCount() > 0 && pending == 0 && turned <= 0.0F && clock < 0.5F;
            comeOut(view, shown, next, quiet);
            shown++;
            nextAt = quiet ? clock : clock + NEXT_AFTER;
        }
        for (int i = 0; i < shown; i++) {
            move(view, i, dt, kind);
        }
        strand(view, making, load, turned, kind);
        grinding(view, dt, making, pace, kind);
    }

    /** Piece {@code index} comes out of the die; {@code quiet}: it lies there already. */
    private void comeOut(StationView view, int index, Item what, boolean quiet) {
        item[index] = what;
        ItemStack stack = new ItemStack(what);
        FoodProperties food = what.getFoodProperties();
        meat[index] = food != null && food.isMeat();
        yaw[index] = random.nextFloat() * 360.0F;
        age[index] = quiet ? Float.NaN : 0.0F;
        Optional<ItemVoxels.Icon> icon = ItemVoxels.icon(stack);
        float thick = meat[index] ? THICK_MEAT : THICK;
        view.node(node(index)).ifPresent(node -> {
            node.setVisible(true);
            if (icon.isPresent()) {
                node.setVisual(null);
                node.setShape(ItemVoxels.build(icon.get(), SIZE, List.of(), 0.0F, thick, true), icon.get().sprite());
            } else {
                node.setShape(null, null);
                node.setVisual(com.pockyl.diegesis.api.scene.Visual.ofItem(what).withAnchor(new Vector3f(8.0F, 8.0F, 8.0F)));
            }
            node.scaleTo(quiet ? 1.0F : 0.3F, 0.0F);
        });
        if (!quiet && meat[index]) {
            view.playSound(SoundEvents.SLIME_SQUISH_SMALL, "die", 0.06F, 1.6F + random.nextFloat() * 0.3F);
        }
    }

    private void hide(StationView view, int index) {
        item[index] = null;
        age[index] = Float.NaN;
        view.node(node(index)).ifPresent(node -> {
            node.setShape(null, null);
            node.setVisual(null);
            node.setVisible(false);
        });
    }

    private static String node(int index) {
        return "out_" + index;
    }

    /** Puts piece {@code index} where it is: pushed out of the hole, dropping, or lying in the heap. */
    private void move(StationView view, int index, float dt, String kind) {
        NodeView node = view.node(node(index)).orElse(null);
        if (node == null) {
            return;
        }
        float[] spot = SPOTS[index];
        float x = spot[0];
        float y = spot[1];
        float z = spot[2];
        float pitch = 0.0F;
        float scale = 1.0F;
        if (!Float.isNaN(age[index])) {
            age[index] += dt;
            float t = age[index];
            if (t < PUSH_TIME) {
                float k = t / PUSH_TIME;
                x = DIE.x - PUSHED_OUT * k;
                y = DIE.y;
                z = DIE.z + (spot[2] - DIE.z) * 0.15F * k;
                pitch = 90.0F;
                scale = Mth.lerp(k, 0.3F, 1.0F);
            } else if (t < PUSH_TIME + DROP_TIME) {
                float k = (t - PUSH_TIME) / DROP_TIME;
                float startX = DIE.x - PUSHED_OUT;
                float startZ = DIE.z + (spot[2] - DIE.z) * 0.15F;
                x = Mth.lerp(k, startX, spot[0]);
                z = Mth.lerp(k, startZ, spot[2]);
                // It drops as things drop, leaving the die with a little push up.
                y = Mth.lerp(k * k, DIE.y, spot[1]) + 0.6F * Mth.sin(k * Mth.PI) * (1.0F - k);
                pitch = 90.0F + 270.0F * k;
            } else {
                age[index] = Float.NaN;
                landed(view, index, kind);
            }
        }
        node.moveTo(x, y, z, 0.0F);
        node.rotateTo(pitch, yaw[index], 0.0F, 0.0F);
        node.scaleTo(scale, 0.0F);
    }

    /** A piece lands on the heap: soft for meat, a tap for anything hard, a little of its colour flying off. */
    private void landed(StationView view, int index, String kind) {
        SoundEvent sound = switch (kind) {
            case "STONE", "METAL" -> SoundEvents.STONE_HIT;
            case "WOOD" -> SoundEvents.WOOD_HIT;
            case MincerStation.MILLED -> SoundEvents.SAND_STEP;
            default -> SoundEvents.SLIME_SQUISH_SMALL;
        };
        view.playSound(sound, "minced", 0.12F, 1.2F + random.nextFloat() * 0.3F);
        float[] spot = SPOTS[index];
        int color = 0xFF000000 | FoodColors.main(new ItemStack(item[index]));
        view.spawnParticles(DiegesisParticles.DUST, new Vector3f(spot[0], spot[1] + 0.3F, spot[2]), new Vector3f(0.0F, 1.0F, 0.0F), 0.5F, 2,
                color);
    }

    /**
     * Minced meat is pressed out of the die's holes as strands as the crank turns it through, the longer the further the piece that
     * comes out next is turned; they break off when it comes out.
     */
    private void strand(StationView view, ItemStack making, int load, float turned, String kind) {
        boolean pressed = !making.isEmpty() && load > 0 && "MEAT".equals(kind) && making.getItem().getFoodProperties() != null
                && making.getItem().getFoodProperties().isMeat();
        float within = pressed ? turned * load - Mth.floor(turned * load) : 0.0F;
        int bits = pressed ? Mth.floor(within * STRAND / STRAND_BIT) : 0;
        if (bits == strandBits) {
            return;
        }
        strandBits = bits;
        if (pressed) {
            strandColor = 0xFF000000 | FoodColors.main(making);
        }
        MeshShape shape = null;
        if (bits > 0) {
            MeshShape.Builder builder = MeshShape.builder();
            for (float strandZ : STRAND_Z) {
                for (int i = 0; i < bits; i++) {
                    float x = DIE.x - STRAND_BIT * (i + 0.5F);
                    float y = DIE.y - 0.07F * i * i;
                    float wobble = (i % 2 == 0 ? 0.08F : -0.08F);
                    box(builder, x - 0.28F, y - 0.28F, strandZ + wobble - 0.28F, x + 0.28F, y + 0.28F, strandZ + wobble + 0.28F);
                }
            }
            shape = builder.build();
        }
        MeshShape built = shape;
        int color = strandColor;
        view.node("strand").ifPresent(node -> {
            node.setVisible(built != null);
            node.setShape(built, PLAIN);
            node.setTint(color);
        });
    }

    /**
     * While a load is turned: crumbs of it out of the die, drops of meat's juice falling off, dust of stone and metal at the die and the
     * hopper's mouth, shavings of wood; the more the faster the crank goes.
     */
    private void grinding(StationView view, float dt, ItemStack making, float pace, String kind) {
        if (making.isEmpty() || pace < 0.05F) {
            return;
        }
        int color = 0xFF000000 | FoodColors.main(making);
        Vector3f out = new Vector3f(DIE.x - 0.4F, DIE.y - 0.4F, DIE.z);
        crumbs += dt * CRUMB_RATE * pace;
        while (crumbs >= 1.0F) {
            crumbs -= 1.0F;
            view.spawnParticles(DiegesisParticles.DUST, out, new Vector3f(-1.5F, -0.6F, (random.nextFloat() - 0.5F) * 1.2F), 0.5F, 1, color);
        }
        switch (kind) {
            case "MEAT" -> {
                drops += dt * DROP_RATE * pace;
                while (drops >= 1.0F) {
                    drops -= 1.0F;
                    int juice = KitchenLooks.mix(color, 0xFF7A1010, 0.5F);
                    view.spawnParticles(DiegesisParticles.DROPLET, new Vector3f(DIE.x - 0.2F, DIE.y - 1.2F, DIE.z + (random.nextFloat() - 0.5F) * 2.0F),
                            new Vector3f(-0.3F, -1.0F, 0.0F), 0.1F, 1, juice);
                }
            }
            case "STONE", "METAL" -> {
                dust += dt * DUST_RATE * pace;
                while (dust >= 1.0F) {
                    dust -= 1.0F;
                    int grey = "METAL".equals(kind) ? 0xFFA8A8B0 : 0xFF9A9690;
                    view.spawnParticles(DiegesisParticles.DUST, out, new Vector3f(-1.2F, 0.8F, 0.0F), 0.7F, 1, grey);
                    view.spawnParticles(DiegesisParticles.DUST, new Vector3f(9.5F, 16.4F, 8.0F), new Vector3f(0.0F, 1.0F, 0.0F), 1.0F, 1, grey);
                }
            }
            case "WOOD", MincerStation.MILLED -> {
                dust += dt * DUST_RATE * pace;
                while (dust >= 1.0F) {
                    dust -= 1.0F;
                    view.spawnParticles(HandsOnLetsDoClient.GRAIN, out, new Vector3f(-2.0F, 1.0F, (random.nextFloat() - 0.5F) * 2.0F), 0.4F, 1,
                            color);
                }
            }
            default -> {
            }
        }
    }

    /** A box's sides and top and bottom. */
    private static void box(MeshShape.Builder shape, float x0, float y0, float z0, float x1, float y1, float z1) {
        shape.corner(x0, y1, z0, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F).corner(x0, y1, z1, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F)
                .corner(x1, y1, z1, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F).corner(x1, y1, z0, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F);
        shape.corner(x0, y0, z0, 0.0F, 0.0F, 0.0F, -1.0F, 0.0F).corner(x1, y0, z0, 1.0F, 0.0F, 0.0F, -1.0F, 0.0F)
                .corner(x1, y0, z1, 1.0F, 1.0F, 0.0F, -1.0F, 0.0F).corner(x0, y0, z1, 0.0F, 1.0F, 0.0F, -1.0F, 0.0F);
        shape.corner(x0, y0, z0, 0.0F, 1.0F, 0.0F, 0.0F, -1.0F).corner(x0, y1, z0, 0.0F, 0.0F, 0.0F, 0.0F, -1.0F)
                .corner(x1, y1, z0, 1.0F, 0.0F, 0.0F, 0.0F, -1.0F).corner(x1, y0, z0, 1.0F, 1.0F, 0.0F, 0.0F, -1.0F);
        shape.corner(x1, y0, z1, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F).corner(x1, y1, z1, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F)
                .corner(x0, y1, z1, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F).corner(x0, y0, z1, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F);
        shape.corner(x0, y0, z1, 0.0F, 1.0F, -1.0F, 0.0F, 0.0F).corner(x0, y1, z1, 0.0F, 0.0F, -1.0F, 0.0F, 0.0F)
                .corner(x0, y1, z0, 1.0F, 0.0F, -1.0F, 0.0F, 0.0F).corner(x0, y0, z0, 1.0F, 1.0F, -1.0F, 0.0F, 0.0F);
        shape.corner(x1, y0, z0, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F).corner(x1, y1, z0, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F)
                .corner(x1, y1, z1, 1.0F, 0.0F, 1.0F, 0.0F, 0.0F).corner(x1, y0, z1, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F);
    }
}
