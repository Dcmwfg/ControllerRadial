package com.controllerradial;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Renames a wheel (a preset). Leaving the name empty makes it fall back to "wheel N".
 */
public class WheelPresetNameScreen extends Screen {
    private static final int FIELD_WIDTH = 220;

    private final Screen parent;
    private final int presetIndex;
    private final WheelConfig config;

    private EditBox nameBox;
    private static boolean renderFailedLogged;

    public WheelPresetNameScreen(Screen parent, int presetIndex) {
        super(Component.translatable("controllerradial.config.rename.title"));
        this.parent = parent;
        this.presetIndex = presetIndex;
        this.config = WheelConfig.get();
    }

    @Override
    protected void init() {
        try {
            buildWidgets();
        } catch (Throwable t) {
            ControllerRadial.LOGGER.error("[ControllerRadial] could not open the rename screen; closing it", t);
            this.minecraft.setScreen(this.parent);
        }
    }

    private void buildWidgets() {
        int left = this.width / 2 - FIELD_WIDTH / 2;

        this.nameBox = new EditBox(this.font, left, 60, FIELD_WIDTH, 20, Component.empty());
        this.nameBox.setHint(Component.translatable("controllerradial.config.rename.hint"));
        this.nameBox.setMaxLength(24);
        WheelPreset preset = config.preset(presetIndex);
        this.nameBox.setValue(preset == null ? "" : preset.name);
        this.addRenderableWidget(this.nameBox);
        this.setFocused(this.nameBox);

        int half = FIELD_WIDTH / 2 - 2;
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
                .bounds(left, this.height - 28, half, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("controllerradial.config.cell.save"), button -> {
            apply();
            this.onClose();
        }).bounds(left + half + 4, this.height - 28, half, 20).build());
    }

    private void apply() {
        WheelPreset preset = config.preset(presetIndex);
        if (preset == null) {
            return;
        }
        preset.name = this.nameBox == null ? "" : this.nameBox.getValue().trim();
        ControllerRadial.LOGGER.info("[ControllerRadial] wheel {} renamed to '{}'", presetIndex + 1, preset.name);
        config.save();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            apply();
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        try {
            this.renderBackground(graphics);
            super.render(graphics, mouseX, mouseY, delta);
            graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
            WheelPreset preset = config.preset(presetIndex);
            graphics.drawCenteredString(this.font,
                    Component.translatable("controllerradial.config.rename.current",
                            preset == null ? "" : preset.displayName(Math.max(0, presetIndex))),
                    this.width / 2, 90, 0xFF909090);
        } catch (Throwable t) {
            if (!renderFailedLogged) {
                renderFailedLogged = true;
                ControllerRadial.LOGGER.error("[ControllerRadial] failed to draw the rename screen; "
                        + "the game keeps running", t);
            }
        }
    }
}
