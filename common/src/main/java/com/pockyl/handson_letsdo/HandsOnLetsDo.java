package com.pockyl.handson_letsdo;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import com.pockyl.handson_letsdo.station.BarrelStation;
import com.pockyl.handson_letsdo.station.BowlStation;
import com.pockyl.handson_letsdo.station.CauldronStation;
import com.pockyl.handson_letsdo.station.CookingPotStation;
import com.pockyl.handson_letsdo.station.KettleStation;
import com.pockyl.handson_letsdo.station.MincerStation;
import com.pockyl.handson_letsdo.station.OvenStation;
import com.pockyl.handson_letsdo.station.PressStation;

import java.nio.file.Path;
import java.util.function.Predicate;

/** Constants and setup of the mod, the same on the client and the server. */
public final class HandsOnLetsDo {
    public static final String MOD_ID = "handson_letsdo";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static Predicate<String> modLoaded = id -> false;
    private static Path configDir = Path.of("config");

    private HandsOnLetsDo() {
    }

    @SuppressWarnings("removal")
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    /** Whether the mod with this id is installed: every mod of the series is optional and brings its stations with it. */
    public static boolean has(String modId) {
        return modLoaded.test(modId);
    }

    /** Where the game keeps its config files; the recipe book keeps what the player made of it there too. */
    public static Path configDir() {
        return configDir;
    }

    /**
     * Called during mod setup on both sides, with the loader's answer to "is this mod installed" and its config folder. The
     * stations are made here, the same on the server and the client.
     */
    public static void init(Predicate<String> isModLoaded, Path configDirectory) {
        modLoaded = isModLoaded;
        configDir = configDirectory;
        HandsOnConfig.load(configDirectory);
        BarrelStation.register();
        CookingPotStation.register();
        OvenStation.register();
        KettleStation.register();
        CauldronStation.register();
        PressStation.register();
        MincerStation.register();
        BowlStation.register();
    }
}
