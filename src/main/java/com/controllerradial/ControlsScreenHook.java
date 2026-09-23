package com.controllerradial;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.ControlsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Puts a "wheel layout" button on the vanilla Options → Controls screen, so the wheel can be
 * configured without installing a mod menu.
 * <p>
 * Done with Forge's own {@link ScreenEvent.Init.Post} instead of a mixin: the screen stays vanilla,
 * we only append one widget.
 * <p>
 * The button fills the <b>first empty cell</b> of the screen's two-column grid (top to bottom,
 * left before right), so it lines up with the vanilla buttons instead of being appended at random.
 */
public final class ControlsScreenHook {
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_SPACING = 24;
    /** The controls screen lays its buttons out from this x, two columns 160px apart. */
    private static final int COLUMN_OFFSET = 155;
    private static final int COLUMN_SPACING = 160;

    private ControlsScreenHook() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof ControlsScreen screen)) {
            return;
        }
        try {
            int[] cell = findFreeCell(screen);
            if (cell == null) {
                ControllerRadial.LOGGER.info("[ControllerRadial] no free spot on the controls screen; "
                        + "open the wheel layout from the mod list instead");
                return;
            }

            event.addListener(Button.builder(Component.translatable("controllerradial.controls.button"),
                            button -> screen.getMinecraft().setScreen(new WheelConfigScreen(screen)))
                    .bounds(cell[0], cell[1], BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
            ControllerRadial.LOGGER.info("[ControllerRadial] wheel layout button placed at x={} y={}",
                    cell[0], cell[1]);
        } catch (Throwable t) {
            // Never let a convenience button break the options screen.
            ControllerRadial.LOGGER.error("[ControllerRadial] could not add the wheel layout button", t);
        }
    }

    /**
     * The first cell of the vanilla grid that nothing occupies, scanned row by row from the top,
     * left column before right. Vanilla's first row sits at {@code height / 6 - 12}.
     *
     * @return {@code {x, y}} of the cell, or {@code null} when the whole grid is taken
     */
    static int[] findFreeCell(Screen screen) {
        int[] cell = findFreeCell(screen.width, screen.height,
                candidate -> !isFree(screen, candidate[0], candidate[1]));
        if (cell != null) {
            return cell;
        }

        // Nothing free on the vanilla grid (another mod filled it): try just below everything.
        int lowestY = Integer.MIN_VALUE;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget) {
                lowestY = Math.max(lowestY, widget.getY() + widget.getHeight());
            }
        }
        if (lowestY != Integer.MIN_VALUE) {
            int y = lowestY + 4;
            if (y + BUTTON_HEIGHT <= screen.height - 8 && isFree(screen, screen.width / 2 - COLUMN_OFFSET, y)) {
                return new int[]{screen.width / 2 - COLUMN_OFFSET, y};
            }
        }
        return null;
    }

    /**
     * Pure version of the grid scan, so the "fill the first gap" rule can be checked on its own.
     *
     * @param occupied tells whether a candidate cell is already taken
     * @return {@code {x, y}} of the first free cell, or {@code null} when the grid is full
     */
    static int[] findFreeCell(int width, int height, java.util.function.Predicate<int[]> occupied) {
        int left = width / 2 - COLUMN_OFFSET;
        int right = left + COLUMN_SPACING;
        int startY = height / 6 - 12;
        int bottom = height - 8;

        for (int y = startY; y + BUTTON_HEIGHT <= bottom; y += ROW_SPACING) {
            int[] leftCell = {left, y};
            if (!occupied.test(leftCell)) {
                return leftCell;
            }
            int[] rightCell = {right, y};
            if (!occupied.test(rightCell)) {
                return rightCell;
            }
        }
        return null;
    }

    static boolean isFree(Screen screen, int x, int y) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget
                    && widget.getX() < x + BUTTON_WIDTH && widget.getX() + widget.getWidth() > x
                    && widget.getY() < y + BUTTON_HEIGHT && widget.getY() + widget.getHeight() > y) {
                return false;
            }
        }
        return true;
    }
}
