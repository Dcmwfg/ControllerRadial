package com.controllerradial;

/**
 * Where the wheel's cells go, worked out from the number of cells and the screen size.
 * <p>
 * Pure maths, so the "the wheel must not cover the text above and below it" rule can be checked
 * without a running game.
 */
public final class WheelLayout {
    /** Room kept at the top for the wheel name, page dots and the LB/RB hint. */
    public static final int HEADER_HEIGHT = 46;
    /** Room kept at the bottom for the selected cell's name / the hint line. */
    public static final int FOOTER_HEIGHT = 50;

    public static final float MAX_CELL_RADIUS = 26.0f;
    public static final float MIN_CELL_RADIUS = 9.0f;
    public static final float CELL_GAP = 6.0f;

    private static final float EDGE_MARGIN = 6.0f;

    private WheelLayout() {
    }

    public record Geometry(float ringRadius, float cellRadius) {
    }

    /** Bottom button rows: both rows span exactly this width, with the same gap. */
    static final int BOTTOM_ROW_WIDTH = 306;
    static final int BOTTOM_ROW_GAP = 6;

    /** Width of one button in a row of {@code count} that spans {@code total} pixels. */
    static int rowButtonWidth(int total, int count, int gap) {
        if (count <= 0) {
            return 0;
        }
        return (total - (count - 1) * gap) / count;
    }

    /** Left edge of the {@code index}-th button centred in a row that starts at {@code left}. */
    static int rowButtonX(int left, int buttonWidth, int gap, int index) {
        return left + index * (buttonWidth + gap);
    }

    public static Geometry compute(int count, int width, int height) {
        if (count <= 0) {
            return new Geometry(0.0f, MAX_CELL_RADIUS);
        }

        // Room left for the ring once the header and footer keep their space.
        float halfHeight = height / 2.0f - Math.max(HEADER_HEIGHT, FOOTER_HEIGHT);
        float halfWidth = width / 2.0f - EDGE_MARGIN;
        float available = Math.min(halfWidth, halfHeight);

        // Cells not touching each other.
        float ring = count * (MAX_CELL_RADIUS * 2.0f + CELL_GAP) / (float) (Math.PI * 2.0);
        ring = Math.max(ring, MAX_CELL_RADIUS + 16.0f);

        float maxRing = available - MAX_CELL_RADIUS;
        if (maxRing >= MIN_CELL_RADIUS) {
            ring = Math.min(ring, maxRing);
        } else {
            // Screen far too small for a full wheel: keep it on screen and let the text shrink.
            ring = Math.max(MIN_CELL_RADIUS, available - MIN_CELL_RADIUS);
        }

        float cellRadius = MAX_CELL_RADIUS;
        if (count > 1) {
            float spacing = ring * (float) (Math.PI * 2.0) / count;
            cellRadius = Math.min(MAX_CELL_RADIUS, Math.max(MIN_CELL_RADIUS, spacing / 2.0f - 3.0f));
        }

        // Last word: never let the wheel reach into the header or footer.
        float limit = available - cellRadius;
        if (ring > limit && limit > 0.0f) {
            ring = limit;
        }

        return new Geometry(ring, cellRadius);
    }
}
