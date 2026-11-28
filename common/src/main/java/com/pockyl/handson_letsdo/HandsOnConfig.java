package com.pockyl.handson_letsdo;

import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * What a modpack can turn off or tune, read once at startup from {@code config/handson_letsdo.properties} (the same file on every
 * loader). On a server it decides for everybody: a station turned off there opens on no client, and its block keeps the menu
 * of its own mod.
 */
public final class HandsOnConfig {
    private static final String FILE = HandsOnLetsDo.MOD_ID + ".properties";
    /** The shortest and the longest a cooking time can be made, as a multiple of the usual one. */
    private static final float SHORTEST_TIME = 0.1F;
    private static final float LONGEST_TIME = 10.0F;
    private static final String HEADER = String.join("\n",
            "Hands-On Let's Do. Restart the game or the server after a change.",
            "fermentation_barrel: Vinery's fermentation barrel as a station, when that mod is there. Off, it keeps its own",
            "  menu. The wine takes the time Vinery's own config gives it either way.",
            "cooking_pot: Farm & Charm's cooking pot as a station, when that mod is there. Off, it keeps its own menu.",
            "candlelight_cooking_pot: Candlelight's cooking pot as a station, when that mod is there. Off, it keeps its own",
            "  menu. It is stirred as Farm & Charm's pot is, in the time cooking_pot_time gives.",
            "roaster: Farm & Charm's roaster as a station, when that mod is there. Off, it keeps its own menu.",
            "candlelight_cooking_pan: Candlelight's cooking pan as a station, when that mod is there. Off, it keeps its own",
            "  menu. It is swirled as Farm & Charm's roaster is, in the time roaster_time gives.",
            "stove: Farm & Charm's stove as a station, when that mod is there. Off, it keeps its own menu. It bakes in its own",
            "  time with its own fuel either way.",
            "candlelight_stoves: Candlelight's eleven stoves as stations, when that mod is there. Off, they keep their own menu.",
            "  They bake as Farm & Charm's stove does, in its time with their own fuel either way.",
            "tea_kettle: HerbalBrews' tea kettles as a station, when that mod is there. Off, they keep their own menu. They brew",
            "  in their own time either way.",
            "brewing_cauldron: HerbalBrews' brewing cauldron as a station, when that mod is there. Off, it keeps its own menu.",
            "  Left alone it brews in its own time; stirred at the station, faster.",
            "apple_press: Vinery's apple press as a station, when that mod is there. Off, it keeps its own menu. At the station it",
            "  presses as the cook presses the screw's cap; left alone, it presses and bottles in the times Vinery's own config",
            "  gives it.",
            "mincer: Farm & Charm's mincer as a station, when that mod is there. Off, it is cranked by right clicks, Farm & Charm's",
            "  way.",
            "crafting_bowl: Farm & Charm's crafting bowl as a station, when that mod is there. Off, it is stirred by right clicks,",
            "  Farm & Charm's way.",
            "cutting_board: Farm & Charm's cutting board as a station, when that mod is there (1.21.1). Off, things are laid",
            "  and cut by right clicks, Farm & Charm's way.",
            "stirring_required: food a cook puts into the pot at the station waits for the spoon while the cook is at it. Off,",
            "  it also cooks unstirred, Farm & Charm's way; stirring still cooks the whole pot at once and earns stars.",
            "recipe_book: the recipe book beside the stations. Each player's own: read on their client.",
            "Those are true or false. The cooking times are numbers from 0.1 to 10:",
            "cooking_pot_time: how long a pot stirred at the station takes (Farm & Charm's and Candlelight's), as a multiple of",
            "  the usual time: 1.0 as it is, 2.0 twice as long, 0.5 half. Food left alone, fed by a hopper or cooked in the pot's",
            "  own menu keeps its own mod's time.",
            "roaster_time: the same for a roaster worked at the station (Farm & Charm's roaster and Candlelight's pan).");

    private static boolean fermentationBarrel = true;
    private static boolean cookingPot = true;
    private static boolean candlelightCookingPot = true;
    private static boolean stove = true;
    private static boolean candlelightStoves = true;
    private static boolean roaster = true;
    private static boolean candlelightCookingPan = true;
    private static boolean teaKettle = true;
    private static boolean brewingCauldron = true;
    private static boolean applePress = true;
    private static boolean mincer = true;
    private static boolean craftingBowl = true;
    private static boolean cuttingBoard = true;
    private static boolean stirringRequired = true;
    private static boolean recipeBook = true;
    private static float cookingPotTime = 1.0F;
    private static float roasterTime = 1.0F;

    private HandsOnConfig() {
    }

    static void load(Path directory) {
        Path path = directory.resolve(FILE);
        Properties values = new Properties();
        boolean unread = false;
        if (Files.exists(path)) {
            // Read leniently: a file saved in another encoding (a comment in Cyrillic in Windows-1251) keeps its values.
            try {
                values.load(new StringReader(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)));
            } catch (IOException | IllegalArgumentException exception) {
                HandsOnLetsDo.LOGGER.warn("Could not read {}, using the defaults", path, exception);
                unread = true;
            }
        }
        fermentationBarrel = read(values, "fermentation_barrel", fermentationBarrel);
        cookingPot = read(values, "cooking_pot", cookingPot);
        candlelightCookingPot = read(values, "candlelight_cooking_pot", candlelightCookingPot);
        stove = read(values, "stove", stove);
        candlelightStoves = read(values, "candlelight_stoves", candlelightStoves);
        roaster = read(values, "roaster", roaster);
        candlelightCookingPan = read(values, "candlelight_cooking_pan", candlelightCookingPan);
        teaKettle = read(values, "tea_kettle", teaKettle);
        brewingCauldron = read(values, "brewing_cauldron", brewingCauldron);
        applePress = read(values, "apple_press", applePress);
        mincer = read(values, "mincer", mincer);
        craftingBowl = read(values, "crafting_bowl", craftingBowl);
        cuttingBoard = read(values, "cutting_board", cuttingBoard);
        stirringRequired = read(values, "stirring_required", stirringRequired);
        recipeBook = read(values, "recipe_book", recipeBook);
        cookingPotTime = readTime(values, "cooking_pot_time", cookingPotTime);
        roasterTime = readTime(values, "roaster_time", roasterTime);
        if (unread) {
            // Never write the defaults over a file the player wrote and the game could not read.
            return;
        }
        // Written back every start, so a file from an older version gets the new keys.
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                values.store(writer, HEADER);
            }
        } catch (IOException exception) {
            HandsOnLetsDo.LOGGER.warn("Could not write {}", path, exception);
        }
    }

    private static boolean read(Properties values, String key, boolean fallback) {
        String value = values.getProperty(key, String.valueOf(fallback)).trim().toLowerCase(java.util.Locale.ROOT);
        boolean read = switch (value) {
            case "true", "yes", "on", "1" -> true;
            case "false", "no", "off", "0" -> false;
            default -> fallback;
        };
        values.setProperty(key, String.valueOf(read));
        return read;
    }

    private static float readTime(Properties values, String key, float fallback) {
        float read = fallback;
        try {
            float value = Float.parseFloat(values.getProperty(key, String.valueOf(fallback)).trim().replace(',', '.'));
            if (Float.isFinite(value)) {
                read = Math.max(SHORTEST_TIME, Math.min(LONGEST_TIME, value));
            }
        } catch (NumberFormatException exception) {
            HandsOnLetsDo.LOGGER.warn("{} in {} is no number, using {}", key, FILE, fallback);
        }
        values.setProperty(key, String.valueOf(read));
        return read;
    }

    public static boolean fermentationBarrel() {
        return fermentationBarrel;
    }

    public static boolean cookingPot() {
        return cookingPot;
    }

    public static boolean candlelightCookingPot() {
        return candlelightCookingPot;
    }

    public static boolean stove() {
        return stove;
    }

    public static boolean candlelightStoves() {
        return candlelightStoves;
    }

    public static boolean roaster() {
        return roaster;
    }

    public static boolean candlelightCookingPan() {
        return candlelightCookingPan;
    }

    public static boolean teaKettle() {
        return teaKettle;
    }

    public static boolean brewingCauldron() {
        return brewingCauldron;
    }

    public static boolean applePress() {
        return applePress;
    }

    public static boolean mincer() {
        return mincer;
    }

    public static boolean craftingBowl() {
        return craftingBowl;
    }

    public static boolean cuttingBoard() {
        return cuttingBoard;
    }

    /** How many times as long as usual a roaster worked at the station takes. */
    public static float roasterTime() {
        return roasterTime;
    }

    /** Whether a cook's food waits for the spoon while the cook is at the pot. */
    public static boolean stirringRequired() {
        return stirringRequired;
    }

    /** How many times as long as usual a pot stirred at the station takes. */
    public static float cookingPotTime() {
        return cookingPotTime;
    }

    /** Sets the stirred pot's time for a game test: what a restart with another file does. */
    public static void setCookingPotTime(float times) {
        cookingPotTime = times;
    }

    /** Sets whether stirring is required for a game test. */
    public static void setStirringRequired(boolean required) {
        stirringRequired = required;
    }

    /** {@code ticks} as long as {@code times} makes them, never less than a tick. */
    public static int scaled(int ticks, float times) {
        return Math.max(1, Math.round(ticks * times));
    }

    /** Whether this player's stations show their recipe book. */
    public static boolean recipeBook() {
        return recipeBook;
    }
}
