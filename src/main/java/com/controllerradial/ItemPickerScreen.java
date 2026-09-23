package com.controllerradial;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Picks the item drawn on a wheel cell, MineMenu style: a searchable list of every registered item
 * showing its icon, its name and its id.
 * <p>
 * Like the key picker, the item index (and every item's display name) is built <b>once</b> per
 * open — a big pack has thousands of items and re-resolving their names per keystroke is what
 * makes these lists feel frozen.
 */
public class ItemPickerScreen extends Screen {
    private static final String NONE = "";
    private static final int MAX_RESULTS = 600;
    private static boolean renderFailedLogged;

    private final Screen parent;
    private final Consumer<String> onPick;

    private EditBox searchBox;
    private ItemList itemList;
    private String query = "";
    private List<Row> index;

    public ItemPickerScreen(Screen parent, Consumer<String> onPick) {
        super(Component.translatable("controllerradial.config.pick_icon"));
        this.parent = parent;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        try {
            buildWidgets();
        } catch (Throwable t) {
            ControllerRadial.LOGGER.error("[ControllerRadial] could not open the icon picker; closing it", t);
            this.minecraft.setScreen(this.parent);
        }
    }

    private void buildWidgets() {
        ControllerRadial.LOGGER.info("[ControllerRadial] opening the icon picker");

        this.searchBox = new EditBox(this.font, this.width / 2 - 110, 30, 260, 20,
                Component.translatable("controllerradial.config.search_item"));
        this.searchBox.setHint(Component.translatable("controllerradial.config.search_item"));
        this.searchBox.setMaxLength(48);
        this.searchBox.setResponder(value -> {
            this.query = value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
            refreshList();
        });
        this.addRenderableWidget(this.searchBox);

        this.itemList = this.addRenderableWidget(new ItemList(this.minecraft, this.width, this.height, 56, this.height - 36, 20));
        refreshList();
        this.setFocused(this.searchBox);

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose())
                .bounds(this.width / 2 - 75, this.height - 28, 150, 20).build());
    }

    private void buildIndex() {
        List<Row> rows = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            String name;
            try {
                name = new ItemStack(item).getHoverName().getString();
            } catch (Throwable t) {
                // A broken translation from some mod must not take the picker down.
                name = id.getPath();
            }
            rows.add(new Row(id.toString(), item, name + "  (" + id + ")",
                    (id + " " + name).toLowerCase(Locale.ROOT)));
        }
        rows.sort(Comparator.comparing(row -> row.label, String.CASE_INSENSITIVE_ORDER));
        this.index = rows;
    }

    private void refreshList() {
        if (this.index == null) {
            buildIndex();
        }
        this.itemList.refresh(this.index, this.query);
    }

    private void pick(String icon) {
        this.onPick.accept(icon);
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
                graphics.drawString(this.font, Component.translatable("controllerradial.config.search_item"),
                        this.searchBox.getX() + 5, this.searchBox.getY() + 6, 0xFF808080);
            }
        } catch (Throwable t) {
            if (!renderFailedLogged) {
                renderFailedLogged = true;
                ControllerRadial.LOGGER.error("[ControllerRadial] failed to draw the icon picker; "
                        + "the game keeps running", t);
            }
        }
    }

    private record Row(String icon, Item item, String label, String haystack) {
    }

    private class ItemList extends ObjectSelectionList<ItemList.ItemEntry> {
        ItemList(Minecraft minecraft, int width, int height, int y0, int y1, int itemHeight) {
            super(minecraft, width, height, y0, y1, itemHeight);
        }

        void refresh(List<Row> rows, String query) {
            this.clearEntries();
            this.addEntry(new ItemEntry(NONE, null,
                    Component.translatable("controllerradial.config.no_icon").getString()));

            int shown = 0;
            for (Row row : rows) {
                if (!query.isEmpty() && !row.haystack().contains(query)) {
                    continue;
                }
                this.addEntry(new ItemEntry(row.icon(), row.item(), row.label()));
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

        private class ItemEntry extends ObjectSelectionList.Entry<ItemEntry> {
            private final String icon;
            private final Item item;
            private final String label;

            ItemEntry(String icon, Item item, String label) {
                this.icon = icon;
                this.item = item;
                this.label = label;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int y, int x, int entryWidth, int entryHeight,
                               int mouseX, int mouseY, boolean hovered, float partialTick) {
                if (this.item != null) {
                    graphics.renderItem(new ItemStack(this.item), x + 4, y + 2);
                }
                graphics.drawString(font, this.label, x + 24, y + 6,
                        this.icon.isEmpty() ? 0xFFFF8080 : 0xFFFFFF);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                pick(this.icon);
                return true;
            }

            @Override
            public Component getNarration() {
                return Component.literal(this.label);
            }
        }
    }
}
