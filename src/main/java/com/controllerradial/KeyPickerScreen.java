package com.controllerradial;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Picks the <b>function</b> a wheel cell performs.
 * <p>
 * Functions are the game's key-bound actions — vanilla ones and every mod's — but they are
 * presented as what they do ("跳跃", "打开背包", "切换视角") rather than as keyboard keys, with
 * the bound key and the raw id shown as secondary information.
 * <p>
 * The list (and every function's translated name) is resolved <b>once</b> per open: a big pack can
 * have well over a thousand bindings, and resolving their names on every keystroke is what makes a
 * picker feel frozen.
 */
public class KeyPickerScreen extends Screen {
    private static final int MAX_RESULTS = 400;
    private static boolean renderFailedLogged;

    /** Functions offered at the top, because they are what people actually put on a wheel. */
    private static final List<String> COMMON = List.of(
            "key.jump", "key.sneak", "key.sprint", "key.inventory", "key.drop",
            "key.swapOffhand", "key.togglePerspective", "key.chat", "key.playerlist",
            "key.use", "key.attack", "key.pickItem");

    private final Screen parent;
    private final Consumer<String> onPick;

    private EditBox searchBox;
    private KeyList keyList;
    private String query = "";
    private List<Row> index;
    private List<Row> common = List.of();

    public KeyPickerScreen(Screen parent, Consumer<String> onPick) {
        super(Component.translatable("controllerradial.config.pick_function"));
        this.parent = parent;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        try {
            buildWidgets();
        } catch (Throwable t) {
            ControllerRadial.LOGGER.error("[ControllerRadial] could not open the function picker; closing it", t);
            this.minecraft.setScreen(this.parent);
        }
    }

    private void buildWidgets() {
        ControllerRadial.LOGGER.info("[ControllerRadial] opening the function picker");

        this.searchBox = new EditBox(this.font, this.width / 2 - 110, 30, 260, 20,
                Component.translatable("controllerradial.config.search"));
        this.searchBox.setHint(Component.translatable("controllerradial.config.search"));
        this.searchBox.setMaxLength(48);
        this.searchBox.setResponder(value -> {
            this.query = value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
            refreshList();
        });
        this.addRenderableWidget(this.searchBox);

        this.keyList = this.addRenderableWidget(new KeyList(this.minecraft, this.width, this.height, 56, this.height - 36, 18));
        refreshList();
        this.setFocused(this.searchBox);

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose())
                .bounds(this.width / 2 - 75, this.height - 28, 150, 20).build());
    }

    /** Resolves every binding's translated name once; filtering afterwards is pure string work. */
    private void buildIndex() {
        List<Row> rows = new ArrayList<>();
        Map<String, Row> byKey = new LinkedHashMap<>();
        for (KeyMapping mapping : this.minecraft.options.keyMappings) {
            String name = mapping.getName();
            String bound = mapping.getTranslatedKeyMessage().getString();
            String display = WheelSlot.keyDisplayName(name);
            String group = WheelSlot.keyCategoryName(name);
            String haystack = (name + " " + bound + " " + display + " " + group).toLowerCase(Locale.ROOT);
            Row row = new Row(name, display + "  [" + bound + "]", name, group, haystack, false);
            rows.add(row);
            byKey.putIfAbsent(name, row);
        }
        rows.sort(Comparator
                .comparing((Row row) -> row.group, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(row -> row.label, String.CASE_INSENSITIVE_ORDER));

        // Reorder so the common functions come first, in the order listed above.
        List<Row> common = new ArrayList<>();
        for (String key : COMMON) {
            Row row = byKey.get(key);
            if (row != null) {
                common.add(row);
            }
        }
        this.index = rows;
        this.common = common;
    }

    private void refreshList() {
        if (this.index == null) {
            buildIndex();
        }
        this.keyList.refresh(this.index, this.common, this.query);
    }

    private void pick(String key) {
        this.onPick.accept(key);
        this.onClose();
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
            graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

            // Draw the placeholder ourselves: the vanilla hint is easy to miss.
            if (this.searchBox != null && this.searchBox.getValue().isEmpty()) {
                graphics.drawString(this.font, Component.translatable("controllerradial.config.search"),
                        this.searchBox.getX() + 5, this.searchBox.getY() + 6, 0xFF808080);
            }
        } catch (Throwable t) {
            if (!renderFailedLogged) {
                renderFailedLogged = true;
                ControllerRadial.LOGGER.error("[ControllerRadial] failed to draw the function picker; "
                        + "the game keeps running", t);
            }
        }
    }

    private record Row(String key, String label, String raw, String group, String haystack, boolean header) {
    }

    private class KeyList extends ObjectSelectionList<KeyList.KeyEntry> {
        KeyList(Minecraft minecraft, int width, int height, int y0, int y1, int itemHeight) {
            super(minecraft, width, height, y0, y1, itemHeight);
        }

        void refresh(List<Row> rows, List<Row> common, String query) {
            this.clearEntries();
            this.addEntry(new KeyEntry("", Component.translatable("controllerradial.config.unbound").getString(),
                    "", false));

            if (query.isEmpty() && !common.isEmpty()) {
                this.addEntry(new KeyEntry("", Component.translatable("controllerradial.config.common").getString(),
                        "", true));
                for (Row row : common) {
                    this.addEntry(new KeyEntry(row.key(), row.label(), row.raw(), false));
                }
                this.addEntry(new KeyEntry("", Component.translatable("controllerradial.config.all").getString(),
                        "", true));
            }

            int shown = 0;
            for (Row row : rows) {
                if (!query.isEmpty() && !row.haystack().contains(query)) {
                    continue;
                }
                this.addEntry(new KeyEntry(row.key(), row.label(), row.raw(), false));
                if (++shown >= MAX_RESULTS) {
                    break;
                }
            }
        }

        @Override
        public int getRowWidth() {
            return 300;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.width / 2 + 160;
        }

        private class KeyEntry extends ObjectSelectionList.Entry<KeyEntry> {
            private final String key;
            private final String label;
            private final String raw;
            private final boolean header;

            KeyEntry(String key, String label, String raw, boolean header) {
                this.key = key;
                this.label = label;
                this.raw = raw;
                this.header = header;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int y, int x, int entryWidth, int entryHeight,
                               int mouseX, int mouseY, boolean hovered, float partialTick) {
                if (this.header) {
                    graphics.drawString(font, this.label, x + 4, y + 5, 0xFFF0C060);
                    return;
                }
                int color = this.key.isEmpty() ? 0xFFFF8080 : 0xFFFFFF;
                graphics.drawString(font, this.label, x + 4, y + 5, color);
                if (!this.raw.isEmpty()) {
                    graphics.drawString(font, this.raw,
                            x + 8 + font.width(this.label), y + 5, 0xFF707070);
                }
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (this.header) {
                    return false;
                }
                pick(this.key);
                return true;
            }

            @Override
            public Component getNarration() {
                return Component.literal(this.label);
            }
        }
    }
}
