package com.controllerradial;

import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.bind.InputBinding;
import dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint;
import dev.isxander.controlify.api.entrypoint.InitContext;
import dev.isxander.controlify.api.entrypoint.PreInitContext;
import dev.isxander.controlify.api.event.ControlifyEvents;
import dev.isxander.controlify.bindings.ControlifyBindings;

/**
 * Bridge into Controlify. Discovered by Controlify through
 * {@code META-INF/services/dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint},
 * so this class is never loaded when Controlify is absent.
 */
public class ControlifyIntegration implements ControlifyEntrypoint {
    @Override
    public void onControlifyPreInit(PreInitContext context) {
    }

    @Override
    public void onControlifyInit(InitContext context) {
        WheelBindings.register(context.bindings());
        // Fires before Controlify handles its in-game key binds, so when this wheel and Controlify's
        // own radial menu share a button, ours opens (theirs then sees a screen already open).
        ControlifyEvents.CONTROLLER_STATE_UPDATE.register(
                event -> WheelManager.onControllerStateUpdate(event.controller()));
        ControllerRadial.LOGGER.info("[ControllerRadial] bindings registered with Controlify");
    }

    @Override
    public void onControllersDiscovered(ControlifyApi controlify) {
        controlify.getCurrentController().ifPresent(controller -> {
            try {
                InputBinding mine = WheelBindings.open().onOrNull(controller);
                InputBinding theirs = ControlifyBindings.RADIAL_MENU.onOrNull(controller);
                if (mine != null && theirs != null && !mine.isUnbound() && !theirs.isUnbound()
                        && mine.boundInput().getRelevantInputs()
                        .equals(theirs.boundInput().getRelevantInputs())) {
                    ControllerRadial.LOGGER.warn("[ControllerRadial] this wheel and Controlify's own radial menu "
                            + "are both bound to {}; ours opens first - rebind one of them to use both",
                            mine.boundInput().getRelevantInputs());
                }
            } catch (Throwable t) {
                ControllerRadial.LOGGER.debug("[ControllerRadial] could not check for a binding clash", t);
            }
        });
    }
}
