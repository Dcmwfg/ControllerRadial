package com.controllerradial;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The wheel layout editor. Reached from the mod list's config button
 * (Forge's {@code ConfigScreenHandler} extension point, which the Mod Menu port also reads).
 */
public class WheelConfigScreen extends Screen {
    private final Screen parent;
    private final WheelConfig config;
    private static boolean renderFailedLogged;

    private int presetIndex;
    private CellList cellList;
    private Button[] presetButtons;

    public WheelConfigScreen(Screen parent) {
        this(parent, 0);
    }

    public WheelConfigScreen(Screen parent, int presetIndex) {
        super(Component.translatable("controllerradial.config.title"));
        this.parent = parent;
        this.config = WheelConfig.get();
        int count = Math.max(1, this.config.presets.size());
        this.presetIndex = Math.floorMod(presetIndex, count);
    }

    @Override
    protected void init() {
        try {
            buildWidgets();
        } catch (Throwable t) {
            ControllerRadial.LOGGER.error("[ControllerRadial] could not open the wheel layout editor; closing it", t);
            this.minecraft.setScreen(this.parent);
        }
    }

    private void buildWidgets() {
        ControllerRadial.LOGGER.info("[ControllerRadial] opening the wheel layout editor");
        int presetCount = Math.max(1, config.presets.size());
        this.presetButtons = new Button[presetCount];

        // Wheels are tabs along the top; they wrap onto more rows as the player adds them.
        int maxWidth = this.width - 16;
        int buttonWidth = Math.max(56, Math.min(96, maxWidth / presetCount - 4));
        int perRow = Math.max(1, (maxWidth + 4) / (buttonWidth + 4));
        int rows = (presetCount + perRow - 1) / perRow;
        int inRow = Math.min(presetCount, perRow);
        int totalWidth = inRow * buttonWidth + (inRow - 1) * 4;
        int startX = this.width / 2 - totalWidth / 2;

        for (int i = 0; i < presetCount; i++) {
            final int index = i;
            int row = i / perRow;
            int column = i % perRow;
            String label = this.font.plainSubstrByWidth(config.presets.get(i).displayName(i), buttonWidth - 8);
            this.presetButtons[i] = this.addRenderableWidget(Button.builder(
                            Component.literal(label),
                            button -> {
                                this.presetIndex = index;
                                this.refreshList();
                                this.updatePresetButtons();
                            })
                    .bounds(startX + column * (buttonWidth + 4), 28 + row * 24, buttonWidth, 20)
                    .build());
        }
        this.updatePresetButtons();

        int listTop = 28 + rows * 24 + 8;
        this.cellList = this.addRenderableWidget(new CellList(this.minecraft, this.width, this.height, listTop, this.height - 62, 30));
        this.refreshList();

        int left = this.width / 2 - WheelLayout.BOTTOM_ROW_WIDTH / 2;
        int rowOfThree = WheelLayout.rowButtonWidth(WheelLayout.BOTTOM_ROW_WIDTH, 3, WheelLayout.BOTTOM_ROW_GAP);
        int rowOfTwo = WheelLayout.rowButtonWidth(WheelLayout.BOTTOM_ROW_WIDTH, 2, WheelLayout.BOTTOM_ROW_GAP);
        int gap = WheelLayout.BOTTOM_ROW_GAP;

        this.addRenderableWidget(Button.builder(Component.translatable("controllerradial.config.add"), button -> {
            if (config.addCell(this.presetIndex) != null) {
                this.refreshList();
            }
        }).bounds(WheelLayout.rowButtonX(left, rowOfThree, gap, 0), this.height - 52, rowOfThree, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("controllerradial.config.add_preset"), button -> {
            WheelPreset added = config.addPreset();
            if (added != null) {
                this.presetIndex = config.presets.size() - 1;
                this.rebuildWidgets();
            }
        }).bounds(WheelLayout.rowButtonX(left, rowOfThree, gap, 1), this.height - 52, rowOfThree, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("controllerradial.config.rename_preset"), button ->
                this.minecraft.setScreen(new WheelPresetNameScreen(this, this.presetIndex))
        ).bounds(WheelLayout.rowButtonX(left, rowOfThree, gap, 2), this.height - 52, rowOfThree, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("controllerradial.config.remove_preset"), button -> {
            if (config.removePreset(this.presetIndex)) {
                this.presetIndex = Math.floorMod(this.presetIndex, config.presets.size());
                this.rebuildWidgets();
            }
        }).bounds(WheelLayout.rowButtonX(left, rowOfTwo, gap, 0), this.height - 28, rowOfTwo, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(WheelLayout.rowButtonX(left, rowOfTwo, gap, 1), this.height - 28, rowOfTwo, 20).build());
    }

    private void updatePresetButtons() {
        for (int i = 0; i < this.presetButtons.length; i++) {
            this.presetButtons[i].active = i != this.presetIndex;
        }
    }

    private void refreshList() {
        this.cellList.refresh(this.presetIndex);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        try {
            renderEditor(graphics, mouseX, mouseY, delta);
        } catch (Throwable t) {
            if (!renderFailedLogged) {
                renderFailedLogged = true;
                ControllerRadial.LOGGER.error("[ControllerRadial] failed to draw the wheel layout editor; "
                        + "the game keeps running", t);
            }
        }
    }

    private void renderEditor(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, delta);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        WheelPreset preset = config.preset(this.presetIndex);
        if (preset == null || preset.slots.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("controllerradial.config.empty"),
                    this.width / 2, this.height / 2, 0xFFA0A0A0);
        }
    }

    @Override
    public void onClose() {
        this.config.save();
        this.minecraft.setScreen(this.parent);
    }

    static String actionLabel(WheelSlot slot) {
        if (WheelSlot.TYPE_KEY.equals(slot.type) && !slot.key.isEmpty()) {
            String label = WheelSlot.keyLabel(slot.key);
            if (slot.toggle) {
                return Component.translatable("controllerradial.config.action.key_toggle", label).getString();
            }
            if (slot.hold > 0.0f) {
                String seconds = slot.hold == Math.round(slot.hold)
                        ? String.valueOf((int) slot.hold)
                        : String.valueOf(slot.hold);
                return Component.translatable("controllerradial.config.action.key_hold", label, seconds).getString();
            }
            return Component.translatable("controllerradial.config.action.key", label).getString();
        }
        if (WheelSlot.TYPE_WHEEL.equals(slot.type)) {
            WheelPreset target = WheelConfig.get().preset(slot.jump - 1);
            String name = target == null ? String.valueOf(slot.jump)
                    : target.displayName(Math.max(0, slot.jump - 1));
            return Component.translatable("controllerradial.config.action.wheel", name).getString();
        }
        if (WheelSlot.TYPE_COMMAND.equals(slot.type) && !slot.command.isEmpty()) {
            return Component.translatable(slot.clipboard
                    ? "controllerradial.config.action.command_clipboard"
                    : "controllerradial.config.action.command", slot.command).getString();
        }
        return Component.translatable("controllerradial.config.action.none").getString();
    }

    private class CellList extends ObjectSelectionList<CellList.CellEntry> {
        CellList(Minecraft minecraft, int width, int height, int y0, int y1, int itemHeight) {
            super(minecraft, width, height, y0, y1, itemHeight);
        }

        void refresh(int presetIndex) {
            this.clearEntries();
            WheelPreset preset = config.preset(presetIndex);
            if (preset == null) {
                return;
            }
            for (int i = 0; i < preset.slots.size(); i++) {
                this.addEntry(new CellEntry(i));
            }
        }

        @Override
        public int getRowWidth() {
            return 280;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.width / 2 + 150;
        }

        private class CellEntry extends ObjectSelectionList.Entry<CellEntry> {
            private final int index;

            CellEntry(int index) {
                this.index = index;
            }

            private WheelSlot slot() {
                WheelPreset preset = config.preset(presetIndex);
                return preset != null && this.index < preset.slots.size() ? preset.slots.get(this.index) : null;
            }

            @Override
            public void render(GuiGraphics graphics, int entryIndex, int y, int x, int entryWidth, int entryHeight,
                               int mouseX, int mouseY, boolean hovered, float partialTick) {
                WheelSlot slot = slot();
                if (slot == null) {
                    return;
                }
                graphics.drawString(font, (this.index + 1) + ". " + slot.displayName(), x + 4, y + 4, 0xFFFFFF);
                graphics.drawString(font, actionLabel(slot), x + 4, y + 16, 0xFFA0A0A0);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                minecraft.setScreen(new WheelCellScreen(WheelConfigScreen.this, presetIndex, this.index));
                return true;
            }

            @Override
            public Component getNarration() {
                WheelSlot slot = slot();
                return Component.literal(slot == null ? "" : slot.displayName());
            }
        }
    }
}
