package com.controllerradial;

import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.bind.InputBinding;
import dev.isxander.controlify.api.bind.InputBindingSupplier;
import dev.isxander.controlify.bindings.ControlifyBindings;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.rumble.BasicRumbleEffect;
import dev.isxander.controlify.rumble.RumbleSource;
import dev.isxander.controlify.screenop.ScreenControllerEventListener;
import dev.isxander.controlify.screenop.ScreenProcessor;
import dev.isxander.controlify.screenop.ScreenProcessorProvider;
import dev.isxander.controlify.virtualmouse.VirtualMouseBehaviour;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The wheel itself: rendered by this mod, driven by Controlify input.
 * <p>
 * Hold the trigger to show it, steer the right stick to pick a cell, release to run it.
 * LB / RB switch between the three presets. B (or ESC) cancels.
 */
public class WheelScreen extends Screen implements ScreenControllerEventListener, ScreenProcessorProvider {
    private static final float CELL_RADIUS = 26.0f;
    private static final float CELL_GAP = 6.0f;
    private static final int ICON_SIZE = 12;
    /** How far above the cell centre the icon sits, leaving room for its caption. */
    private static final int ICON_LIFT = 5;
    private static final int CAPTION_HEIGHT = 9;
    /** Padding kept between the text and the edge of the cell. */
    private static final int CONTENT_INSET = 14;

    private final ControllerEntity controller;
    private final InputBinding openBind;
    private final WheelConfig config;
    private final Processor processor = new Processor(this);

    private int presetIndex;
    private int selected = -1;
    /** True once the stick has been pushed far enough to select something this time round. */
    private boolean stickEngaged;
    /** Ticks the edit button has been held; -1 once it fired, until it is released again. */
    private int editHeldTicks;
    private static boolean renderFailedLogged;

    /** How long the edit button has to be held to open the wheel's editor. */
    private static final int EDIT_HOLD_TICKS = 8;

    public WheelScreen(ControllerEntity controller, InputBinding openBind, int presetIndex) {
        super(Component.translatable("controllerradial.wheel.title"));
        this.controller = controller;
        this.openBind = openBind;
        this.config = WheelConfig.get();
        this.presetIndex = config.presets.isEmpty() ? 0 : Math.floorMod(presetIndex, config.presets.size());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public ScreenProcessor<?> screenProcessor() {
        return processor;
    }

    private List<WheelSlot> slots() {
        if (config.presets.isEmpty()) {
            return List.of();
        }
        WheelPreset preset = config.presets.get(presetIndex);
        return preset.slots == null ? List.of() : preset.slots;
    }

    /** LB / RB switch wheels straight away. */
    private boolean handlePageSwitch(ControllerEntity controller) {
        InputBinding prev = binding(WheelBindings.previousPreset(), controller);
        InputBinding next = binding(WheelBindings.nextPreset(), controller);
        if (prev != null && prev.justPressed()) {
            return switchPreset(-1);
        }
        if (next != null && next.justPressed()) {
            return switchPreset(1);
        }
        return false;
    }

    /**
     * Holding the edit button opens this wheel's settings in game, so a controller player never
     * has to reach for a mouse. Controlify cannot bind a combination, hence the dedicated button.
     *
     * @return true when the editor opened, so the caller skips selection this tick
     */
    private boolean handleEditRequest(ControllerEntity controller) {
        InputBinding edit = binding(WheelBindings.editWheel(), controller);
        if (edit == null) {
            return false;
        }

        if (!edit.digitalNow()) {
            editHeldTicks = 0;
            return false;
        }
        if (editHeldTicks < 0) {
            return true; // already fired, waiting for the button to come back up
        }
        if (++editHeldTicks < EDIT_HOLD_TICKS) {
            return false;
        }

        editHeldTicks = -1;
        selected = -1;
        stickEngaged = false;
        ControllerRadial.LOGGER.info("[ControllerRadial] opening the wheel editor in game (wheel {})",
                presetIndex + 1);
        playClick();
        // The editor's parent is this screen, so finishing it comes back to the open wheel.
        Minecraft.getInstance().setScreen(new WheelConfigScreen(this, presetIndex));
        return true;
    }

    private static InputBinding binding(InputBindingSupplier supplier, ControllerEntity controller) {
        return supplier == null ? null : supplier.onOrNull(controller);
    }

    /** Applies a wheel change, remembering it for next time. */
    private boolean switchPreset(int delta) {
        if (delta == 0 || config.presets.isEmpty()) {
            return false;
        }

        presetIndex = Math.floorMod(presetIndex + delta, config.presets.size());
        WheelManager.rememberPreset(presetIndex, config.presets.size());
        selected = -1;
        stickEngaged = false;
        playSelectFeedback();
        return true;
    }

    @Override
    public void onControllerInput(ControllerEntity controller) {
        try {
            handleControllerInput(controller);
        } catch (Throwable t) {
            // A bug in here must never take the game down; log it and close the wheel.
            ControllerRadial.LOGGER.error("[ControllerRadial] failed to handle wheel input; closing the wheel", t);
            selected = -1;
            stickEngaged = false;
            onClose();
        }
    }

    private void handleControllerInput(ControllerEntity controller) {
        if (controller != this.controller) {
            return;
        }

        // Letting go of the trigger only closes the wheel: confirmation is the stick coming back
        // to the middle (see below), so a quick trigger tap is a cancel.
        if (!openBind.digitalNow()) {
            selected = -1;
            stickEngaged = false;
            onClose();
            return;
        }

        if (handlePageSwitch(controller)) {
            return;
        }

        if (handleEditRequest(controller)) {
            return;
        }

        int count = slots().size();
        if (count == 0) {
            return;
        }

        float x = axis(WheelBindings.axisRight(), controller) - axis(WheelBindings.axisLeft(), controller);
        float y = axis(WheelBindings.axisDown(), controller) - axis(WheelBindings.axisUp(), controller);

        int newSelected = WheelSelection.selectionFor(x, y, count, config.activationThreshold);

        if (WheelSelection.confirmsOnRelease(stickEngaged, selected, newSelected)) {
            int choice = selected;
            selected = -1;
            stickEngaged = false;

            WheelSlot chosen = choice >= 0 && choice < slots().size() ? slots().get(choice) : null;
            if (chosen != null && chosen.jumpsWheel() && !config.presets.isEmpty()) {
                // MineMenu's CATEGORY: swap wheels and keep the menu open.
                presetIndex = Math.floorMod(chosen.jump - 1, config.presets.size());
                WheelManager.rememberPreset(presetIndex, config.presets.size());
                ControllerRadial.LOGGER.info("[ControllerRadial] jumped to wheel {}", presetIndex + 1);
                playSelectFeedback();
                return;
            }

            onClose();
            if (chosen != null) {
                ControllerRadial.LOGGER.info("[ControllerRadial] running cell {} of wheel {}", choice, presetIndex);
                playClick();
                chosen.invoke(Minecraft.getInstance());
            }
            return;
        }

        if (newSelected >= 0) {
            stickEngaged = true;
            if (newSelected != selected) {
                selected = newSelected;
                playSelectFeedback();
            }
        }
        // The highlight is deliberately sticky: letting the stick drift back below the threshold
        // must not clear it, otherwise releasing could not confirm anything.
    }

    @Override
    public void onClose() {
        if (openBind.digitalNow()) {
            // Cancelled while still holding the trigger: wait for the trigger to come up
            // before the wheel may be opened again.
            WheelManager.suppressUntilBindRelease();
        }
        super.onClose();
    }

    private static boolean justPressed(InputBindingSupplier supplier, ControllerEntity controller) {
        if (supplier == null) {
            return false;
        }
        InputBinding binding = supplier.onOrNull(controller);
        return binding != null && binding.justPressed();
    }

    private static float axis(InputBindingSupplier supplier, ControllerEntity controller) {
        if (supplier == null) {
            return 0.0f;
        }
        InputBinding binding = supplier.onOrNull(controller);
        return binding == null ? 0.0f : binding.analogueNow();
    }

    private void playSelectFeedback() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        if (config.haptics) {
            ControlifyApi.get().playRumbleEffect(RumbleSource.GUI, BasicRumbleEffect.constant(0.0f, 0.45f, 3));
        }
    }

    private void playClick() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        try {
            renderWheel(graphics);
        } catch (Throwable t) {
            if (!renderFailedLogged) {
                renderFailedLogged = true;
                ControllerRadial.LOGGER.error("[ControllerRadial] failed to draw the wheel; "
                        + "drawing is skipped from now on (the game keeps running)", t);
            }
        }
    }

    private void renderWheel(GuiGraphics graphics) {
        int centreX = this.width / 2;
        int centreY = this.height / 2;
        List<WheelSlot> slots = slots();
        int count = slots.size();
        WheelLayout.Geometry geometry = WheelLayout.compute(count, this.width, this.height);

        graphics.fill(0, 0, this.width, this.height, 0x50000000);

        if (count > 0) {
            float bandInner = Math.max(geometry.ringRadius() - geometry.cellRadius() - 3.0f, 16.0f);
            float bandOuter = geometry.ringRadius() + geometry.cellRadius() + 3.0f;

            fillRing(graphics, centreX, centreY, bandInner, bandOuter, 0xC0141414);
            fillCircle(graphics, centreX, centreY, bandInner, 0x66000000);

            for (int i = 0; i < count; i++) {
                double boundary = Math.toRadians(-90.0 + i * (360.0 / count) + 180.0 / count);
                drawRadialLine(graphics, centreX, centreY, bandInner, bandOuter, boundary, 0x60FFFFFF);
            }

            for (int i = 0; i < count; i++) {
                double angle = Math.toRadians(-90.0 + i * (360.0 / count));
                int cellX = Math.round(centreX + (float) (Math.cos(angle) * geometry.ringRadius()));
                int cellY = Math.round(centreY + (float) (Math.sin(angle) * geometry.ringRadius()));
                drawCell(graphics, slots.get(i), cellX, cellY, geometry.cellRadius(), i == selected);
            }
        } else {
            graphics.drawCenteredString(this.font, Component.translatable("controllerradial.wheel.empty"),
                    centreX, centreY - 4, 0xFFFFFFFF);
        }

        drawHeader(graphics, centreX);
        drawFooter(graphics, centreX, slots);
    }

    private void drawHeader(GuiGraphics graphics, int centreX) {
        List<WheelPreset> presets = config.presets;
        if (presets.isEmpty()) {
            return;
        }

        String title = presets.get(presetIndex).displayName(presetIndex);
        graphics.drawCenteredString(this.font, Component.literal(title), centreX, 12, 0xFFFFFFFF);

        int dotY = 12 + this.font.lineHeight + 3;
        if (presets.size() <= 8) {
            int dotWidth = 6;
            int dotGap = 4;
            int total = presets.size() * dotWidth + (presets.size() - 1) * dotGap;
            int x = centreX - total / 2;
            for (int i = 0; i < presets.size(); i++) {
                int color = i == presetIndex ? 0xFFF0C060 : 0x60FFFFFF;
                graphics.fill(x, dotY, x + dotWidth, dotY + 3, color);
                x += dotWidth + dotGap;
            }
        } else {
            // Too many wheels for dots; show which one we are on instead.
            graphics.drawCenteredString(this.font,
                    Component.literal((presetIndex + 1) + " / " + presets.size()), centreX, dotY - 2, 0xFFF0C060);
        }

        graphics.drawCenteredString(this.font, Component.translatable("controllerradial.wheel.pages_hint"),
                centreX, dotY + 8, 0xFF909090);
    }

    private void drawFooter(GuiGraphics graphics, int centreX, List<WheelSlot> slots) {
        WheelSlot hovered = selected >= 0 && selected < slots.size() ? slots.get(selected) : null;
        Component status = hovered == null
                ? Component.translatable("controllerradial.wheel.hint")
                : Component.literal(hovered.displayName());
        graphics.drawCenteredString(this.font, status, centreX, this.height - 39,
                hovered == null ? 0xFFA0A0A0 : 0xFFFFFFFF);

        InputBinding edit = binding(WheelBindings.editWheel(), this.controller);
        if (edit != null) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("controllerradial.wheel.group_hint", edit.inputIcon()),
                    centreX, this.height - 27, 0xFF909090);
        }
    }

    private void drawCell(GuiGraphics graphics, WheelSlot slot, int centreX, int centreY,
                          float radius, boolean highlighted) {
        fillCircle(graphics, centreX, centreY, radius,
                highlighted ? 0x80F0C060 : 0x60101010);
        fillRing(graphics, centreX, centreY, Math.max(radius - 1.5f, 0.0f), radius,
                highlighted ? 0xFFFFD080 : 0x40FFFFFF);

        ItemStack stack = iconStack(slot);
        String text = slot.renderLabel();

        if (!stack.isEmpty()) {
            // Icon in the cell, caption tucked underneath it.
            float scale = (float) ICON_SIZE / 16.0f;
            graphics.pose().pushPose();
            graphics.pose().translate(centreX - ICON_SIZE / 2.0f - 0.5f,
                    centreY - ICON_SIZE / 2.0f - ICON_LIFT, 0.0f);
            graphics.pose().scale(scale, scale, 1.0f);
            graphics.renderItem(stack, 0, 0);
            graphics.pose().popPose();

            if (!text.isEmpty()) {
                int captionWidth = Math.round(radius * 1.3f);
                drawFittedText(graphics, text,
                        Math.round(centreX - captionWidth / 2.0f),
                        Math.round(centreY + 2.0f),
                        captionWidth, CAPTION_HEIGHT,
                        highlighted ? 0xFFFFFFFF : 0xFFD8D8D8);
            }
            return;
        }

        if (text.isEmpty()) {
            return;
        }

        int box = Math.round(radius * 2.0f - CONTENT_INSET);
        drawFittedText(graphics, text, centreX - box / 2, centreY - box / 2, box, box,
                highlighted ? 0xFFFFFFFF : 0xFFD8D8D8);
    }

    /** Draws text centred in a box, shrunk until it fits. */
    private void drawFittedText(GuiGraphics graphics, String text, int x, int y,
                                int boxWidth, int boxHeight, int color) {
        if (text.isEmpty() || boxWidth <= 0 || boxHeight <= 0) {
            return;
        }

        Font font = this.font;
        String[] lines = text.split("\\n", -1);
        int widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, font.width(line));
        }

        float scale = TextFit.scaleFor(widest, font.lineHeight, lines.length, boxWidth, boxHeight);
        float blockHeight = font.lineHeight * lines.length * scale;

        graphics.pose().pushPose();
        graphics.pose().translate(x, y + (boxHeight - blockHeight) / 2.0f, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineX = Math.round((boxWidth / scale - font.width(line)) / 2.0f);
            graphics.drawString(font, line, lineX, i * font.lineHeight, color, true);
        }
        graphics.pose().popPose();
    }

    private static ItemStack iconStack(WheelSlot slot) {
        if (slot.icon.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(slot.icon);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(id)
                .filter(item -> item != Items.AIR)
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
    }

    private static void fillCircle(GuiGraphics graphics, int cx, int cy, float radius, int color) {
        int r = Math.round(radius);
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    private static void fillRing(GuiGraphics graphics, int cx, int cy, float innerRadius,
                                 float outerRadius, int color) {
        int ro = Math.max(1, Math.round(outerRadius));
        int ri = Math.max(0, Math.round(innerRadius));
        for (int dy = -ro; dy <= ro; dy++) {
            int outer = (int) Math.sqrt(Math.max(0, ro * ro - dy * dy));
            int inner = Math.abs(dy) < ri ? (int) Math.sqrt(Math.max(0, ri * ri - dy * dy)) : 0;
            graphics.fill(cx - outer, cy + dy, cx - inner + 1, cy + dy + 1, color);
            graphics.fill(cx + inner, cy + dy, cx + outer + 1, cy + dy + 1, color);
        }
    }

    private static void drawRadialLine(GuiGraphics graphics, int cx, int cy, float from, float to,
                                       double angle, int color) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        for (double r = from; r <= to; r += 0.5) {
            int x = (int) Math.round(cx + cos * r);
            int y = (int) Math.round(cy + sin * r);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private record Geometry(float ringRadius, float cellRadius) {
    }


    /**
     * Keeps Controlify's screen defaults out of the way: no virtual cursor, no GUI navigation,
     * and B closes the wheel.
     */
    public static class Processor extends ScreenProcessor<WheelScreen> {
        public Processor(WheelScreen screen) {
            super(screen);
        }

        @Override
        public VirtualMouseBehaviour virtualMouseBehaviour() {
            return VirtualMouseBehaviour.DISABLED;
        }

        @Override
        protected void handleButtons(ControllerEntity controller) {
            if (controller == screen.controller && ControlifyBindings.GUI_BACK.on(controller).justPressed()) {
                screen.onClose();
            }
        }
    }
}
