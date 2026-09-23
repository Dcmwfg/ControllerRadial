package com.controllerradial;

/**
 * Fits text into a fixed box: the maths behind the auto-shrinking labels on wheel cells.
 * Pure, so it can be checked without a running game.
 */
public final class TextFit {
    private TextFit() {
    }

    /**
     * @param widestLine   width of the widest line, in font pixels
     * @param lineHeight   height of one line, in font pixels
     * @param lines        how many lines will be drawn
     * @param boxWidth     available width in pixels
     * @param boxHeight    available height in pixels
     * @return a scale factor in {@code (0, 1]} that makes the text block fit the box,
     *         or {@code 1} when there is nothing to fit
     */
    public static float scaleFor(int widestLine, int lineHeight, int lines, int boxWidth, int boxHeight) {
        if (widestLine <= 0 || lineHeight <= 0 || lines <= 0 || boxWidth <= 0 || boxHeight <= 0) {
            return 1.0f;
        }
        float byWidth = (float) boxWidth / (float) widestLine;
        float byHeight = (float) boxHeight / (float) (lineHeight * lines);
        return Math.min(1.0f, Math.min(byWidth, byHeight));
    }
}
