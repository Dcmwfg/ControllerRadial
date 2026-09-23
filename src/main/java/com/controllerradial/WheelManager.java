package com.controllerradial;

import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.bind.InputBinding;
import dev.isxander.controlify.controller.ControllerEntity;
import net.minecraft.client.Minecraft;

/**
 * Decides when the wheel opens. The wheel closes itself once the open binding is released.
 */
public final class WheelManager {
    /**
     * Set when the wheel was cancelled (B / ESC) while the open binding is still held, so that
     * the still-held trigger does not immediately reopen it.
     */
    private static boolean suppressed = false;

    /** Remembers which preset was last used, so reopening the wheel returns to it. */
    private static int presetIndex = 0;

    private WheelManager() {
    }

    /**
     * Called from {@code CONTROLLER_STATE_UPDATE}, which Controlify fires for every controller
     * before it handles in-game key binds — that is what lets this wheel take a button before
     * Controlify's own radial menu can.
     */
    public static void onControllerStateUpdate(ControllerEntity controller) {
        if (ControlifyApi.get().getCurrentController().orElse(null) != controller) {
            return;
        }
        onControllerTick(controller);
    }

    public static void onControllerTick(ControllerEntity controller) {
        InputBinding open = WheelBindings.open() == null ? null : WheelBindings.open().onOrNull(controller);
        if (open == null) {
            return;
        }

        if (!open.digitalNow()) {
            suppressed = false;
            return;
        }

        if (suppressed) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.level == null || minecraft.player == null) {
            return;
        }

        minecraft.setScreen(new WheelScreen(controller, open, presetIndex));
        ControllerRadial.LOGGER.info("[ControllerRadial] wheel opened (wheel {} of {})",
                presetIndex + 1, WheelConfig.get().presets.size());
    }

    static int presetIndex() {
        return presetIndex;
    }

    static void rememberPreset(int index, int presetCount) {
        if (presetCount <= 0) {
            presetIndex = 0;
        } else {
            presetIndex = Math.floorMod(index, presetCount);
        }
    }

    static void suppressUntilBindRelease() {
        suppressed = true;
    }
}
