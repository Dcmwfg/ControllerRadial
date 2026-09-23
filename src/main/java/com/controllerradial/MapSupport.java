package com.controllerradial;

import com.mojang.blaze3d.platform.Window;
import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.api.bind.ControlifyBindApi;
import dev.isxander.controlify.api.bind.InputBinding;
import dev.isxander.controlify.api.bind.InputBindingSupplier;
import dev.isxander.controlify.api.event.ControlifyEvents;
import dev.isxander.controlify.bindings.BindContext;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.screenop.ScreenProcessorProvider;
import dev.isxander.controlify.virtualmouse.VirtualMouseBehaviour;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

/**
 * Controller support for Xaero's map screens, which are mouse-only: pan/hover with the mouse,
 * zoom with the wheel and open their context menu with the right button.
 * <p>
 * Nothing about Xaero is patched or reflected on — the cursor is moved by warping the real GLFW
 * cursor, and zoom/right-click are delivered as ordinary mouse events to whatever screen is open.
 * That also means it works for any similarly mouse-driven map, not just Xaero's.
 * <p>
 * Active only while a Xaero screen is open (their classes all live under {@code xaero.}).
 */
public final class MapSupport {
    /** Window pixels per tick at full stick deflection. */
    private static final int NUDGE_STEP = 16;
    private static final int NUDGE_REPEAT_TICKS = 4;
    private static final int ZOOM_REPEAT_TICKS = 3;

    public static final BindContext MAP_CONTEXT = new BindContext(
            ResourceLocation.tryParse(ControllerRadial.MOD_ID + ":xaero_map"),
            minecraft -> looksLikeMapScreen(minecraft.screen));

    private static InputBindingSupplier axisUp;
    private static InputBindingSupplier axisDown;
    private static InputBindingSupplier axisLeft;
    private static InputBindingSupplier axisRight;
    private static InputBindingSupplier nudgeUp;
    private static InputBindingSupplier nudgeDown;
    private static InputBindingSupplier nudgeLeft;
    private static InputBindingSupplier nudgeRight;
    private static InputBindingSupplier zoomIn;
    private static InputBindingSupplier zoomOut;
    private static InputBindingSupplier rightClick;
    /** Generic (any screen) "hold the left mouse button" for dragging. */
    private static InputBindingSupplier drag;

    private static boolean dragging;
    private static double lastGuiX;
    private static double lastGuiY;
    private static final Set<String> SEEN_SCREENS = new HashSet<>();

    private static int nudgeCooldown;
    private static int zoomCooldown;
    private static boolean hintLogged;

    private MapSupport() {
    }

    public static void register(ControlifyBindApi api) {
        api.registerBindContext(MAP_CONTEXT);

        axisUp = axis(api, "map_axis_up");
        axisDown = axis(api, "map_axis_down");
        axisLeft = axis(api, "map_axis_left");
        axisRight = axis(api, "map_axis_right");

        nudgeUp = button(api, "map_nudge_up");
        nudgeDown = button(api, "map_nudge_down");
        nudgeLeft = button(api, "map_nudge_left");
        nudgeRight = button(api, "map_nudge_right");

        zoomIn = axis(api, "map_zoom_in");
        zoomOut = axis(api, "map_zoom_out");
        rightClick = button(api, "map_right_click");

        drag = api.registerBinding(builder -> builder
                .id(ControllerRadial.MOD_ID, "gui_drag")
                .category(WheelBindings.CATEGORY)
                .allowedContexts(BindContext.ANY_SCREEN));

        ControlifyEvents.ACTIVE_CONTROLLER_TICKED.register(event -> apply(event.controller()));
    }

    private static InputBindingSupplier axis(ControlifyBindApi api, String path) {
        return api.registerBinding(builder -> builder
                .id(ControllerRadial.MOD_ID, path)
                .category(WheelBindings.CATEGORY)
                .allowedContexts(MAP_CONTEXT));
    }

    private static InputBindingSupplier button(ControlifyBindApi api, String path) {
        return axis(api, path);
    }

    /** Runs every tick while the active controller is being used. */
    static void apply(ControllerEntity controller) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (screen == null) {
            dragging = false;
            return;
        }

        logVirtualMouseState(screen);

        // Generic for every screen: hold the left button while this is held (dragging).
        applyDrag(minecraft, screen, controller);

        if (!looksLikeMapScreen(screen)) {
            nudgeCooldown = 0;
            zoomCooldown = 0;
            return;
        }

        if (!hintLogged) {
            hintLogged = true;
            ControllerRadial.LOGGER.info("[ControllerRadial] map extras available on {} (they start unbound; "
                    + "Controlify's own virtual mouse already moves the cursor with the left stick, "
                    + "scrolls with the right stick and right-clicks with X)",
                    screen.getClass().getName());
        }

        double dx = stickDelta(analogue(axisRight, controller) - analogue(axisLeft, controller));
        double dy = stickDelta(analogue(axisDown, controller) - analogue(axisUp, controller));

        if (nudgeCooldown > 0) {
            nudgeCooldown--;
        }
        if (nudgeCooldown == 0) {
            int stepX = 0;
            int stepY = 0;
            if (down(nudgeLeft, controller)) {
                stepX -= NUDGE_STEP;
            }
            if (down(nudgeRight, controller)) {
                stepX += NUDGE_STEP;
            }
            if (down(nudgeUp, controller)) {
                stepY -= NUDGE_STEP;
            }
            if (down(nudgeDown, controller)) {
                stepY += NUDGE_STEP;
            }
            if (stepX != 0 || stepY != 0) {
                dx += stepX;
                dy += stepY;
                nudgeCooldown = NUDGE_REPEAT_TICKS;
            }
        }

        moveCursor(minecraft, dx, dy);

        float zoom = analogue(zoomIn, controller) - analogue(zoomOut, controller);
        if (Math.abs(zoom) < 0.5f) {
            zoomCooldown = 0;
        } else if (zoomCooldown == 0) {
            // 1.20.1 takes three doubles here (x, y, delta); one notch per step.
            screen.mouseScrolled(guiX(minecraft), guiY(minecraft), Math.signum(zoom));
            zoomCooldown = ZOOM_REPEAT_TICKS;
        } else {
            zoomCooldown--;
        }

        InputBinding click = binding(rightClick, controller);
        if (click != null) {
            if (click.justPressed()) {
                // The right trigger is also Controlify's "page down"; cancel that scroll for this
                // tick so opening the map's menu does not zoom at the same time.
                stopControlifyScroll();
                screen.mouseClicked(guiX(minecraft), guiY(minecraft), 1);
            }
            if (click.justReleased()) {
                screen.mouseReleased(guiX(minecraft), guiY(minecraft), 1);
            }
        }
    }

    private static void stopControlifyScroll() {
        try {
            Controlify.instance().virtualMouseHandler().preventScrollingThisTick();
        } catch (Throwable t) {
            ControllerRadial.LOGGER.debug("[ControllerRadial] could not cancel this tick's scroll", t);
        }
    }

    /** Moves the real cursor, which is what these screens read (and what makes hover work). */
    private static void moveCursor(Minecraft minecraft, double dx, double dy) {
        if (dx == 0.0 && dy == 0.0) {
            return;
        }
        Window window = minecraft.getWindow();
        double x = clampCursor(minecraft.mouseHandler.xpos() + dx, window.getWidth());
        double y = clampCursor(minecraft.mouseHandler.ypos() + dy, window.getHeight());
        GLFW.glfwSetCursorPos(window.getWindow(), x, y);
    }

    /**
     * Drives a drag through the screen's own mouse callbacks: press, a drag event per tick while
     * held, then release. Controlify's virtual mouse has no way to hold a button, which is what
     * map panning and drag-based UIs need.
     */
    private static void applyDrag(Minecraft minecraft, Screen screen, ControllerEntity controller) {
        InputBinding held = binding(drag, controller);
        if (held == null) {
            return;
        }

        double x = guiX(minecraft);
        double y = guiY(minecraft);

        if (!held.digitalNow()) {
            if (dragging) {
                dragging = false;
                screen.mouseReleased(x, y, 0);
            }
            lastGuiX = x;
            lastGuiY = y;
            return;
        }

        if (!dragging) {
            dragging = true;
            lastGuiX = x;
            lastGuiY = y;
            screen.mouseClicked(x, y, 0);
            return;
        }

        screen.mouseDragged(x, y, 0, x - lastGuiX, y - lastGuiY);
        lastGuiX = x;
        lastGuiY = y;
    }

    /** Notes once per screen class whether Controlify's virtual mouse is even active there. */
    private static void logVirtualMouseState(Screen screen) {
        String name = screen.getClass().getName();
        if (!SEEN_SCREENS.add(name)) {
            return;
        }
        try {
            VirtualMouseBehaviour behaviour = ScreenProcessorProvider.provide(screen).virtualMouseBehaviour();
            ControllerRadial.LOGGER.info("[ControllerRadial] screen {} -> Controlify virtual mouse: {}",
                    name, behaviour);
        } catch (Throwable t) {
            ControllerRadial.LOGGER.debug("[ControllerRadial] could not read the virtual mouse state", t);
        }
    }

    private static double guiX(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        return minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getWidth();
    }

    private static double guiY(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        return minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getHeight();
    }

    /** Deadzone plus speed, in window pixels per tick. */
    static double stickDelta(float deflection) {
        return MapInput.stickDelta(deflection);
    }

    static double clampCursor(double value, int limit) {
        return MapInput.clampCursor(value, limit);
    }

    /** True for Xaero's own screens; they all live in the {@code xaero.} package. */
    static boolean looksLikeMapScreen(Screen screen) {
        return screen != null && MapInput.looksLikeMapScreen(screen.getClass().getName());
    }

    static boolean looksLikeMapScreen(String className) {
        return MapInput.looksLikeMapScreen(className);
    }

    private static boolean down(InputBindingSupplier supplier, ControllerEntity controller) {
        InputBinding input = binding(supplier, controller);
        return input != null && input.digitalNow();
    }

    private static float analogue(InputBindingSupplier supplier, ControllerEntity controller) {
        InputBinding input = binding(supplier, controller);
        return input == null ? 0.0f : input.analogueNow();
    }

    private static InputBinding binding(InputBindingSupplier supplier, ControllerEntity controller) {
        return supplier == null ? null : supplier.onOrNull(controller);
    }
}
