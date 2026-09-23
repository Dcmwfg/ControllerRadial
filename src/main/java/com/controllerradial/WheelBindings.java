package com.controllerradial;

import dev.isxander.controlify.api.bind.ControlifyBindApi;
import dev.isxander.controlify.api.bind.InputBindingSupplier;
import dev.isxander.controlify.bindings.BindContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The bindings this mod registers with Controlify.
 * <p>
 * {@code wheel_open} stays live in game (so the wheel can be opened) and inside the wheel
 * (so the release can be detected); everything else only exists while the wheel is open.
 */
public final class WheelBindings {
    public static final Component CATEGORY = Component.translatable("controllerradial.binding.category");

    /** Active only while our own wheel screen is displayed. */
    public static final BindContext WHEEL_CONTEXT = new BindContext(
            ResourceLocation.tryParse(ControllerRadial.MOD_ID + ":wheel"),
            minecraft -> minecraft.screen instanceof WheelScreen
    );

    private static InputBindingSupplier open;
    private static InputBindingSupplier axisUp;
    private static InputBindingSupplier axisDown;
    private static InputBindingSupplier axisLeft;
    private static InputBindingSupplier axisRight;
    private static InputBindingSupplier previousPreset;
    private static InputBindingSupplier nextPreset;
    private static InputBindingSupplier editWheel;

    private WheelBindings() {
    }

    public static void register(ControlifyBindApi api) {
        api.registerBindContext(WHEEL_CONTEXT);

        open = api.registerBinding(builder -> builder
                .id(ControllerRadial.MOD_ID, "wheel_open")
                .category(CATEGORY)
                .allowedContexts(BindContext.IN_GAME, WHEEL_CONTEXT));

        axisUp = select(api, "wheel_axis_up");
        axisDown = select(api, "wheel_axis_down");
        axisLeft = select(api, "wheel_axis_left");
        axisRight = select(api, "wheel_axis_right");

        previousPreset = select(api, "wheel_prev");
        nextPreset = select(api, "wheel_next");
        editWheel = select(api, "wheel_edit");
    }

    private static InputBindingSupplier select(ControlifyBindApi api, String path) {
        return api.registerBinding(builder -> builder
                .id(ControllerRadial.MOD_ID, path)
                .category(CATEGORY)
                .allowedContexts(WHEEL_CONTEXT));
    }

    public static InputBindingSupplier open() {
        return open;
    }

    public static InputBindingSupplier axisUp() {
        return axisUp;
    }

    public static InputBindingSupplier axisDown() {
        return axisDown;
    }

    public static InputBindingSupplier axisLeft() {
        return axisLeft;
    }

    public static InputBindingSupplier axisRight() {
        return axisRight;
    }

    public static InputBindingSupplier previousPreset() {
        return previousPreset;
    }

    public static InputBindingSupplier nextPreset() {
        return nextPreset;
    }

    public static InputBindingSupplier editWheel() {
        return editWheel;
    }
}
