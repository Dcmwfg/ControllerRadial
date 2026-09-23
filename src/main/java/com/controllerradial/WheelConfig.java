package com.controllerradial;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The wheel layout: three presets, switched with LB / RB while the trigger is held.
 * Stored as {@code config/controllerradial.json} and rewritten on every load, so the file
 * always shows the full set of defaults.
 * <p>
 * Not thread safe; only touched from the client thread.
 */
public final class WheelConfig {
    /** How many wheels a fresh config starts with. */
    public static final int DEFAULT_PRESET_COUNT = 3;
    /** Upper bound of wheels; LB / RB cycle through whatever exists. */
    public static final int MAX_PRESETS = 12;
    /** Upper bound of cells per wheel. */
    public static final int MAX_SLOTS = 12;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final String FILE_NAME = "controllerradial.json";

    private static WheelConfig instance;

    public List<WheelPreset> presets = new ArrayList<>();

    /** Stick deflection needed before a direction counts as selected. */
    public float activationThreshold = 0.5f;

    /** Play a short rumble when the selection or the wheel changes. */
    public boolean haptics = true;

    /** Ticks without stick input before the selection is cleared. */
    public int focusTimeoutTicks = 10;

    /** Legacy single-wheel layout, migrated into {@link #presets} on load. */
    public List<WheelSlot> slots;

    public static WheelConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    private static WheelConfig load() {
        Path path = file();
        WheelConfig config = null;

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, WheelConfig.class);
            } catch (IOException | JsonSyntaxException e) {
                ControllerRadial.LOGGER.error("Could not read {}, falling back to defaults", path, e);
            }
        }

        if (config == null) {
            config = defaults();
        }
        config.normalise();
        config.save();
        return config;
    }

    static WheelConfig defaults() {
        WheelConfig config = new WheelConfig();

        WheelPreset common = new WheelPreset("常用");
        common.slots.add(WheelSlot.key("聊天", "key.chat", "minecraft:paper"));
        common.slots.add(WheelSlot.key("玩家列表", "key.playerlist", "minecraft:player_head"));
        common.slots.add(WheelSlot.key("进度", "key.advancements", "minecraft:book"));
        common.slots.add(WheelSlot.key("社交", "key.socialInteractions", "minecraft:bell"));
        common.slots.add(WheelSlot.key("旁观高亮", "key.spectatorOutlines", "minecraft:spectral_arrow"));
        common.slots.add(WheelSlot.key("切换视角", "key.togglePerspective", "minecraft:ender_eye"));
        common.slots.add(WheelSlot.key("切换副手", "key.swapOffhand", "minecraft:shield"));
        common.slots.add(WheelSlot.key("选取方块", "key.pickItem", "minecraft:item_frame"));
        config.presets.add(common);

        WheelPreset hotbar = new WheelPreset("快捷栏");
        for (int i = 1; i <= 9; i++) {
            hotbar.slots.add(WheelSlot.key("栏" + i, "key.hotbar." + i));
        }
        config.presets.add(hotbar);

        WheelPreset commands = new WheelPreset("命令");
        commands.slots.add(WheelSlot.command("生存", "gamemode survival", "minecraft:iron_sword"));
        commands.slots.add(WheelSlot.command("创造", "gamemode creative", "minecraft:grass_block"));
        commands.slots.add(WheelSlot.command("旁观", "gamemode spectator", "minecraft:ender_pearl"));
        commands.slots.add(WheelSlot.command("白天", "time set day", "minecraft:sunflower"));
        commands.slots.add(WheelSlot.command("夜晚", "time set night", "minecraft:torch"));
        commands.slots.add(WheelSlot.command("晴天", "weather clear", "minecraft:glass"));
        commands.slots.add(WheelSlot.command("雨天", "weather rain", "minecraft:water_bucket"));
        commands.slots.add(WheelSlot.command("清背包", "clear", "minecraft:lava_bucket"));
        config.presets.add(commands);

        return config;
    }

    void normalise() {
        List<WheelPreset> migrated = new ArrayList<>();

        if (presets != null) {
            for (WheelPreset preset : presets) {
                if (preset != null) {
                    migrated.add(preset);
                }
            }
        }
        // Older configs had a flat "slots" list.
        if (migrated.isEmpty() && slots != null && !slots.isEmpty()) {
            WheelPreset legacy = new WheelPreset("轮盘 1");
            legacy.slots.addAll(slots);
            migrated.add(legacy);
        }
        if (migrated.isEmpty()) {
            migrated.addAll(defaults().presets);
        }
        slots = null;

        // The player decides how many wheels they want; only the cap and a minimum of one apply.
        while (migrated.size() > MAX_PRESETS) {
            migrated.remove(migrated.size() - 1);
        }
        if (migrated.isEmpty()) {
            migrated.add(new WheelPreset("轮盘 1"));
        }

        for (WheelPreset preset : migrated) {
            if (preset.slots == null) {
                preset.slots = new ArrayList<>();
            }
            while (preset.slots.size() > MAX_SLOTS) {
                preset.slots.remove(preset.slots.size() - 1);
            }
            for (WheelSlot slot : preset.slots) {
                if (slot != null) {
                    slot.normalise();
                }
            }
        }

        presets = migrated;
        activationThreshold = Math.min(0.95f, Math.max(0.15f, activationThreshold));
        focusTimeoutTicks = Math.max(0, Math.min(200, focusTimeoutTicks));
    }

    /** Short human-readable description of the layout, for the log. */
    public String summary() {
        int cells = 0;
        for (WheelPreset preset : presets) {
            cells += preset.slots == null ? 0 : preset.slots.size();
        }
        return presets.size() + " wheels, " + cells + " cells";
    }

    /** The preset at {@code index}, wrapping around, or {@code null} when there are none. */
    public WheelPreset preset(int index) {
        if (presets == null || presets.isEmpty()) {
            return null;
        }
        return presets.get(Math.floorMod(index, presets.size()));
    }

    /** Appends an empty cell, unless the preset is already full. */
    public WheelSlot addCell(int presetIndex) {
        WheelPreset preset = preset(presetIndex);
        if (preset == null || preset.slots.size() >= MAX_SLOTS) {
            return null;
        }
        WheelSlot slot = new WheelSlot();
        preset.slots.add(slot);
        return slot;
    }

    public void removeCell(int presetIndex, int cellIndex) {
        WheelPreset preset = preset(presetIndex);
        if (preset != null && cellIndex >= 0 && cellIndex < preset.slots.size()) {
            preset.slots.remove(cellIndex);
        }
    }

    /** Appends an empty wheel, unless the cap is reached. */
    public WheelPreset addPreset() {
        if (presets == null || presets.size() >= MAX_PRESETS) {
            return null;
        }
        WheelPreset preset = new WheelPreset("轮盘 " + (presets.size() + 1));
        presets.add(preset);
        return preset;
    }

    /** Removes a wheel, keeping at least one. */
    public boolean removePreset(int index) {
        if (presets == null || presets.size() <= 1 || index < 0 || index >= presets.size()) {
            return false;
        }
        presets.remove(index);
        return true;
    }

    public void save() {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            ControllerRadial.LOGGER.error("Could not write {}", path, e);
        }
    }
}
