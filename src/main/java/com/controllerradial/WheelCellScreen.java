package com.controllerradial;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Edits a single wheel cell, laid out like MineMenu's menu item editor:
 * a label, an action (key binding or command) with its own options, and an icon.
 * Nothing is written back to the config until "save" is pressed.
 */
public class WheelCellScreen extends Screen {
    private static final int FIELD_WIDTH = 220;

    private final Screen parent;
    private final WheelConfig config;
    private final int presetIndex;
    private final int cellIndex;

    private String pendingText;
    private String pendingType;
    private String pendingKey;
    private boolean pendingToggle;
    private String pendingCommand;
    private boolean pendingClipboard;
    private String pendingIcon;
    private int pendingJump = 1;
    private float pendingHold;

    /** Trigger modes offered by the "trigger" button, in cycles. */
    private static final float[] HOLD_STEPS = {0.0f, 0.5f, 1.0f, 2.0f, 3.0f, 5.0f};

    private EditBox textBox;
    private EditBox commandBox;
    private Button keyButton;
    private Button keyModeButton;
    private Button commandModeButton;
    private Button optionButton;
    private Button iconButton;
    private static boolean renderFailedLogged;

    public WheelCellScreen(Screen parent, int presetIndex, int cellIndex) {
        super(Component.translatable("controllerradial.config.cell.title"));
        this.parent = parent;
        this.config = WheelConfig.get();
        this.presetIndex = presetIndex;
        this.cellIndex = cellIndex;

        WheelSlot slot = slot();
        this.pendingText = slot == null ? "" : slot.text;
        this.pendingKey = slot == null ? "" : slot.key;
        this.pendingToggle = slot != null && slot.toggle;
        this.pendingCommand = slot == null ? "" : slot.command;
        this.pendingClipboard = slot != null && slot.clipboard;
        this.pendingIcon = slot == null ? "" : slot.icon;
        this.pendingHold = slot == null ? 0.0f : slot.hold;
        this.pendingType = slot == null || slot.type.isEmpty() ? WheelSlot.TYPE_KEY : slot.type;
        this.pendingJump = slot == null ? 1 : slot.jump;
    }

    private WheelSlot slot() {
        WheelPreset preset = config.preset(presetIndex);
        return preset != null && cellIndex < preset.slots.size() ? preset.slots.get(cellIndex) : null;
    }

    private boolean keyMode() {
        return WheelSlot.TYPE_KEY.equals(pendingType);
    }

    private boolean commandMode() {
        return WheelSlot.TYPE_COMMAND.equals(pendingType);
    }

    private boolean wheelMode() {
        return WheelSlot.TYPE_WHEEL.equals(pendingType);
    }

    @Override
    protected void init() {
        try {
            buildWidgets();
        } catch (Throwable t) {
            ControllerRadial.LOGGER.error("[ControllerRadial] could not open the cell editor; closing it", t);
            this.minecraft.setScreen(this.parent);
        }
    }

    private void buildWidgets() {
        int left = this.width / 2 - FIELD_WIDTH / 2;

        this.textBox = new EditBox(this.font, left, 32, FIELD_WIDTH, 20, Component.empty());
        this.textBox.setHint(Component.translatable("controllerradial.config.cell.text"));
        this.textBox.setMaxLength(48);
        this.textBox.setValue(pendingText);
        this.addRenderableWidget(this.textBox);

        int third = (FIELD_WIDTH - 8) / 3;
        this.keyModeButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("controllerradial.config.cell.type_key"),
                        button -> switchType(WheelSlot.TYPE_KEY))
                .bounds(left, 60, third, 20).build());
        this.commandModeButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("controllerradial.config.cell.type_command"),
                        button -> switchType(WheelSlot.TYPE_COMMAND))
                .bounds(left + third + 4, 60, third, 20).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("controllerradial.config.cell.type_wheel"),
                        button -> switchType(WheelSlot.TYPE_WHEEL))
                .bounds(left + (third + 4) * 2, 60, FIELD_WIDTH - (third + 4) * 2, 20).build());
        this.keyModeButton.active = !keyMode();
        this.commandModeButton.active = !WheelSlot.TYPE_COMMAND.equals(pendingType);

        if (keyMode()) {
            this.keyButton = this.addRenderableWidget(Button.builder(keyLabel(), button -> {
                stashFields();
                this.minecraft.setScreen(new KeyPickerScreen(this, picked -> pendingKey = picked));
            }).bounds(left, 88, FIELD_WIDTH, 20).build());

            this.optionButton = this.addRenderableWidget(Button.builder(triggerLabel(), button -> {
                cycleTrigger();
                button.setMessage(triggerLabel());
            }).bounds(left, 116, FIELD_WIDTH, 20).build());
        } else if (WheelSlot.TYPE_COMMAND.equals(pendingType)) {
            this.commandBox = new EditBox(this.font, left, 88, FIELD_WIDTH, 20, Component.empty());
            this.commandBox.setHint(Component.translatable("controllerradial.config.cell.command_hint"));
            this.commandBox.setMaxLength(128);
            this.commandBox.setValue(pendingCommand);
            this.addRenderableWidget(this.commandBox);

            this.optionButton = this.addRenderableWidget(Button.builder(clipboardLabel(), button -> {
                this.pendingClipboard = !this.pendingClipboard;
                button.setMessage(clipboardLabel());
            }).bounds(left, 116, FIELD_WIDTH, 20).build());
        }

        if (wheelMode()) {
            this.addRenderableWidget(Button.builder(jumpLabel(), button -> {
                int count = Math.max(1, config.presets.size());
                this.pendingJump = Math.floorMod(this.pendingJump, count) + 1;
                button.setMessage(jumpLabel());
            }).bounds(left, 88, FIELD_WIDTH, 20).build());
        }

        this.iconButton = this.addRenderableWidget(Button.builder(iconLabel(), button -> {
            stashFields();
            this.minecraft.setScreen(new ItemPickerScreen(this, picked -> pendingIcon = picked));
        }).bounds(left, 144, FIELD_WIDTH, 20).build());

        int half2 = (FIELD_WIDTH - 4) / 2;
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
                .bounds(left, this.height - 28, half2, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("controllerradial.config.cell.save"), button -> {
            apply();
            this.onClose();
        }).bounds(left + half2 + 4, this.height - 28, half2, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("controllerradial.config.cell.delete"), button -> {
            config.removeCell(presetIndex, cellIndex);
            this.onClose();
        }).bounds(left, this.height - 52, FIELD_WIDTH, 20).build());
    }

    private void switchType(String type) {
        if (pendingType.equals(type)) {
            return;
        }
        stashFields();
        this.pendingType = type;
        this.rebuildWidgets();
    }

    /** Keeps typed text when another screen opens and this one is rebuilt afterwards. */
    private void stashFields() {
        if (this.textBox != null) {
            this.pendingText = this.textBox.getValue().trim();
        }
        if (this.commandBox != null) {
            this.pendingCommand = this.commandBox.getValue().trim();
        }
    }

    private Component keyLabel() {
        return pendingKey.isEmpty()
                ? Component.translatable("controllerradial.config.cell.pick_key")
                : Component.translatable("controllerradial.config.cell.key", WheelSlot.keyLabel(pendingKey));
    }

    private Component toggleLabel() {
        return Component.translatable("controllerradial.config.cell.toggle",
                Component.translatable(pendingToggle
                        ? "controllerradial.config.on"
                        : "controllerradial.config.off"));
    }

    /** 一下 → 长按 0.5/1/2/3/5 秒 → 切换保持 → 回到一下。 */
    private void cycleTrigger() {
        if (pendingToggle) {
            pendingToggle = false;
            pendingHold = 0.0f;
            return;
        }
        int index = 0;
        for (int i = 0; i < HOLD_STEPS.length; i++) {
            if (Math.abs(HOLD_STEPS[i] - pendingHold) < 0.001f) {
                index = i;
            }
        }
        if (index + 1 < HOLD_STEPS.length) {
            pendingHold = HOLD_STEPS[index + 1];
        } else {
            pendingHold = 0.0f;
            pendingToggle = true;
        }
    }

    private Component triggerLabel() {
        Component mode;
        if (pendingToggle) {
            mode = Component.translatable("controllerradial.config.cell.trigger.toggle");
        } else if (pendingHold <= 0.0f) {
            mode = Component.translatable("controllerradial.config.cell.trigger.tap");
        } else {
            mode = Component.translatable("controllerradial.config.cell.trigger.hold",
                    Component.literal(seconds(pendingHold)));
        }
        return Component.translatable("controllerradial.config.cell.trigger", mode);
    }

    private static String seconds(float value) {
        return value == Math.round(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    private Component clipboardLabel() {
        return Component.translatable("controllerradial.config.cell.clipboard",
                Component.translatable(pendingClipboard
                        ? "controllerradial.config.on"
                        : "controllerradial.config.off"));
    }

    private Component iconLabel() {
        return pendingIcon.isEmpty()
                ? Component.translatable("controllerradial.config.cell.pick_icon")
                : Component.translatable("controllerradial.config.cell.icon", pendingIcon);
    }

    private Component jumpLabel() {
        WheelPreset target = config.preset(pendingJump - 1);
        int count = Math.max(1, config.presets.size());
        String name = target == null
                ? String.valueOf(pendingJump)
                : target.displayName(Math.floorMod(pendingJump - 1, count));
        return Component.translatable("controllerradial.config.cell.jump", name);
    }

    private void apply() {
        WheelSlot slot = slot();
        if (slot == null) {
            return;
        }
        stashFields();

        slot.text = pendingText;
        slot.type = pendingType;
        slot.key = WheelSlot.TYPE_KEY.equals(pendingType) ? pendingKey : "";
        slot.toggle = pendingToggle;
        slot.hold = pendingHold;
        slot.command = WheelSlot.TYPE_COMMAND.equals(pendingType) ? pendingCommand : "";
        slot.clipboard = pendingClipboard;
        slot.jump = pendingJump;
        slot.icon = pendingIcon;
        slot.normalise();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        try {
            this.renderBackground(graphics);
            super.render(graphics, mouseX, mouseY, delta);
            graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
            if (wheelMode()) {
                graphics.drawCenteredString(this.font,
                        Component.translatable("controllerradial.config.cell.jump_hint"),
                        this.width / 2, 120, 0xFF909090);
            }
            if (keyMode()) {
                graphics.drawCenteredString(this.font,
                        Component.translatable("controllerradial.config.cell.trigger_hint"),
                        this.width / 2, 168, 0xFF909090);
            }
        } catch (Throwable t) {
            if (!renderFailedLogged) {
                renderFailedLogged = true;
                ControllerRadial.LOGGER.error("[ControllerRadial] failed to draw the cell editor; "
                        + "the game keeps running", t);
            }
        }
    }

    @Override
    public void onClose() {
        stashFields();
        this.minecraft.setScreen(this.parent);
    }
}
