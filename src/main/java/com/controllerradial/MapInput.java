package com.controllerradial;

import java.util.Locale;

/**
 * The pure maths behind {@link MapSupport}: what counts as a map screen, how far the stick moves
 * the cursor, and how the cursor is kept inside the window.
 * <p>
 * Deliberately free of Controlify and Minecraft references so it can be checked without a game.
 */
public final class MapInput {
    /** Window pixels per tick at full stick deflection. */
    public static final double STICK_SPEED = 14.0;
    public static final double STICK_DEADZONE = 0.2;

    private MapInput() {
    }

    /** True for Xaero's own screens; their classes all live in the {@code xaero.} package. */
    public static boolean looksLikeMapScreen(String className) {
        return className != null && className.toLowerCase(Locale.ROOT).startsWith("xaero.");
    }

    /** Stick deflection to a cursor delta, in window pixels per tick. */
    public static double stickDelta(float deflection) {
        if (Math.abs(deflection) < STICK_DEADZONE) {
            return 0.0;
        }
        double magnitude = (Math.abs(deflection) - STICK_DEADZONE) / (1.0 - STICK_DEADZONE);
        return Math.signum(deflection) * magnitude * STICK_SPEED;
    }

    public static double clampCursor(double value, int limit) {
        return Math.max(0.0, Math.min(value, Math.max(0, limit - 1)));
    }
}
