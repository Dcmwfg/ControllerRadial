package com.controllerradial;

import net.minecraft.util.Mth;

/**
 * Pure stick-to-slot maths, kept separate from the screen so it can be reasoned about
 * (and tested) without a running game.
 * <p>
 * Slot 0 sits at the top of the wheel and indices run clockwise; the returned index is
 * {@code -1} while the stick is centred. Stick coordinates follow Minecraft's screen space,
 * i.e. positive {@code y} is down, which is also how Controlify reports the radial axes.
 */
public final class WheelSelection {
    private WheelSelection() {
    }

    public static int selectionFor(float x, float y, int count, float threshold) {
        if (count <= 0 || (Math.abs(x) < threshold && Math.abs(y) < threshold)) {
            return -1;
        }
        float angle = Mth.wrapDegrees(Mth.RAD_TO_DEG * (float) Mth.atan2(y, x) - 90.0f) + 180.0f;
        float slice = 360.0f / count;
        return Mth.floor((angle + slice / 2.0f) / slice) % count;
    }

    /**
     * Whether the stick coming back to the middle should confirm the current highlight.
     * Only true when the stick had actually been pushed this time round and something is
     * highlighted — so opening the wheel and letting go of the trigger is still a cancel.
     */
    public static boolean confirmsOnRelease(boolean stickWasEngaged, int selected, int newSelected) {
        return newSelected < 0 && stickWasEngaged && selected >= 0;
    }
}
