package com.pockyl.handson_letsdo.client;

import com.pockyl.diegesis.api.client.MeshShape;

/**
 * The pixels a station's badge is drawn in: flat squares of an eighth of a scene pixel, in the pixels of the icon the badge
 * shows (16 of them, centred), and the small digits of its numbers.
 */
final class Badge {
    private static final float PIXEL = 0.125F;
    private static final String[][] DIGITS = {
            {"###", "#.#", "#.#", "#.#", "###"}, {".#.", "##.", ".#.", ".#.", "###"}, {"###", "..#", "###", "#..", "###"},
            {"###", "..#", ".##", "..#", "###"}, {"#.#", "#.#", "###", "..#", "..#"}, {"###", "#..", "###", "..#", "###"},
            {"###", "#..", "###", "#.#", "###"}, {"###", "..#", ".#.", ".#.", ".#."}, {"###", "#.#", "###", "#.#", "###"},
            {"###", "#.#", "###", "..#", "###"}};
    private static final String[] COLON = {".", "#", ".", "#", "."};
    private static final String[] TIMES = {"...", "#.#", ".#.", "#.#", "..."};

    private Badge() {
    }

    /** A rectangle of badge pixels, {@code x} to the right and {@code z} down from the middle of the icon. */
    static void pixels(MeshShape.Builder shape, float x, float z, float width, float height) {
        float left = -(x + width) * PIXEL;
        float right = -x * PIXEL;
        float top = -z * PIXEL;
        float bottom = -(z + height) * PIXEL;
        shape.corner(left, 0.0F, top, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F)
                .corner(right, 0.0F, top, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F)
                .corner(right, 0.0F, bottom, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F)
                .corner(left, 0.0F, bottom, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F);
    }

    /** A line of digits, colons and "x" centred under the icon with its top at {@code top}, a pixel between characters. */
    static void text(MeshShape.Builder shape, String text, int top) {
        int width = width(text);
        number(shape, text, -width / 2 - width % 2, top);
    }

    /** How many pixels wide {@code text} is drawn. */
    static int width(String text) {
        int width = -1;
        for (char c : text.toCharArray()) {
            width += (c == ':' ? 1 : 3) + 1;
        }
        return width;
    }

    /** A line of digits, colons and "x" from {@code left} to the right, with its top at {@code top}. */
    static void number(MeshShape.Builder shape, String text, int left, int top) {
        int x = left;
        for (char c : text.toCharArray()) {
            String[] glyph = c == ':' ? COLON : c == 'x' ? TIMES : DIGITS[c - '0'];
            for (int row = 0; row < glyph.length; row++) {
                for (int column = 0; column < glyph[row].length(); column++) {
                    if (glyph[row].charAt(column) == '#') {
                        pixels(shape, x + column, top + row, 1, 1);
                    }
                }
            }
            x += glyph[0].length() + 1;
        }
    }
}
