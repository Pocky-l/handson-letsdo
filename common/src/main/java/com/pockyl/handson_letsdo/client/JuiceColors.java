package com.pockyl.handson_letsdo.client;

import java.util.Map;

/**
 * How each of Vinery's juices looks standing in a barrel, by the word wines name it with: the reds from ruby to dark violet, the
 * whites from straw to green, apple juice amber, the Nether's two their vines' own colours. Opaque but for a little: a must is
 * cloudy.
 */
final class JuiceColors {
    private static final int ALPHA = 0xEB000000;
    private static final int UNKNOWN = 0x8A5A6E;
    private static final Map<String, Integer> COLORS = Map.ofEntries(
            Map.entry("red_general", 0x6C2A5E),
            Map.entry("white_general", 0xC6CC84),
            Map.entry("red_savanna", 0x8A2F45),
            Map.entry("white_savanna", 0xD9C27A),
            Map.entry("red_taiga", 0x4A1F52),
            Map.entry("white_taiga", 0xB4D1A0),
            Map.entry("red_jungle", 0x8C2C62),
            Map.entry("white_jungle", 0x9CC878),
            Map.entry("apple", 0xD9A441),
            Map.entry("red_crimson", 0x8E1F2B),
            Map.entry("white_warped", 0x2FA69A));

    private JuiceColors() {
    }

    /** The colour of a juice, with its alpha; a juice this mod has no word for is a dull rose. */
    static int of(String juice) {
        return ALPHA | COLORS.getOrDefault(juice, UNKNOWN);
    }
}
