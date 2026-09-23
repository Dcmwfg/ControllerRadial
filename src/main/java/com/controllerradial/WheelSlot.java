package com.controllerradial;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * One cell of the wheel, modelled on MineMenu's menu items: a label, an icon and one action.
 * <p>
 * Display: {@code text} (falling back to {@code name}) is drawn in the cell and shrunk to fit.
 * If {@code text} is empty the {@code icon} item is drawn instead, and if that is empty too the
 * bound key's glyph is drawn.
 * <p>
 * Action, chosen by {@link #type} ({@code key}, {@code command} or {@code wheel}):
 * <ul>
 *     <li>{@code key} — taps a key mapping; with {@code toggle} it stays held until used again</li>
 *     <li>{@code command} — runs a chat command; with {@code clipboard} it only copies it</li>
 *     <li>{@code wheel} — MineMenu's CATEGORY: swaps to {@link #jump} and keeps the wheel open</li>
 * </ul>
 */
public class WheelSlot {
    public static final String TYPE_KEY = "key";
    public static final String TYPE_COMMAND = "command";
    public static final String TYPE_WHEEL = "wheel";

    /** Text drawn on the cell. */
    public String text = "";
    /** Legacy alias for {@link #text}. */
    public String name = "";
    /** {@code key}, {@code command} or {@code wheel}; inferred when empty. */
    public String type = "";

    /** Key mapping name to tap, e.g. {@code key.togglePerspective}. */
    public String key = "";
    /** Hold the key instead of tapping it, until the cell is picked again. */
    public boolean toggle = false;
    /** Seconds to hold the key for before releasing it; 0 means a single tap. */
    public float hold = 0.0f;

    /** Chat command, e.g. {@code gamemode creative}. */
    public String command = "";
    /** Copy the command to the clipboard instead of running it. */
    public boolean clipboard = false;

    /** Target wheel for {@link #TYPE_WHEEL}, 1-based. */
    public int jump = 1;

    /** Optional item id drawn as the cell icon, e.g. {@code minecraft:iron_sword}. */
    public String icon = "";

    public WheelSlot() {
    }

    private WheelSlot(String text, String key, String command, String icon) {
        this.text = text;
        this.key = key;
        this.command = command;
        this.icon = icon;
    }

    public static WheelSlot key(String text, String key) {
        return new WheelSlot(text, key, "", "");
    }

    public static WheelSlot key(String text, String key, String icon) {
        return new WheelSlot(text, key, "", icon);
    }

    public static WheelSlot command(String text, String command, String icon) {
        return new WheelSlot(text, "", command, icon);
    }

    void normalise() {
        text = clean(text);
        name = clean(name);
        type = clean(type);
        key = clean(key);
        command = clean(command);
        icon = clean(icon);

        if (text.isEmpty()) {
            text = name;
        }
        name = "";

        if (command.startsWith("/")) {
            command = command.substring(1).trim();
        }

        if (!type.equals(TYPE_KEY) && !type.equals(TYPE_COMMAND) && !type.equals(TYPE_WHEEL)) {
            type = !key.isEmpty() ? TYPE_KEY : (!command.isEmpty() ? TYPE_COMMAND : "");
        }
        jump = Math.min(Math.max(jump, 1), WheelConfig.MAX_PRESETS);
        hold = Math.min(Math.max(hold, 0.0f), 30.0f);
    }

    /** How long to hold the key down, in ticks. */
    public int holdTicks() {
        return Math.round(hold * 20.0f);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public boolean isEmpty() {
        return actionName().isEmpty();
    }

    /** The action this cell performs, or an empty string when nothing is bound. */
    public String actionName() {
        if (TYPE_KEY.equals(type)) {
            return key;
        }
        if (TYPE_COMMAND.equals(type)) {
            return command;
        }
        if (TYPE_WHEEL.equals(type)) {
            return "wheel " + jump;
        }
        return "";
    }

    /** True when picking this cell swaps wheels instead of running something. */
    public boolean jumpsWheel() {
        return TYPE_WHEEL.equals(type);
    }

    public String displayName() {
        if (!text.isEmpty()) {
            return text;
        }
        if (TYPE_WHEEL.equals(type)) {
            return "→ " + jump;
        }
        String action = actionName();
        if (!action.isEmpty()) {
            return TYPE_COMMAND.equals(type) ? "/" + action : action;
        }
        return "";
    }

    /**
     * What to draw on the wheel cell: the label, or the key's translated name when the label is
     * empty ("跳跃 [空格]" rather than "key.jump").
     */
    public String renderLabel() {
        if (!text.isEmpty()) {
            return text;
        }
        if (TYPE_KEY.equals(type) && !key.isEmpty()) {
            return keyLabel(key);
        }
        return displayName();
    }

    /**
     * Runs this cell's action. The wheel screen is already closed by the time this is called.
     */
    public void invoke(Minecraft minecraft) {
        if (TYPE_KEY.equals(type)) {
            invokeKey(minecraft);
            return;
        }
        if (TYPE_COMMAND.equals(type)) {
            invokeCommand(minecraft);
        }
    }

    private void invokeKey(Minecraft minecraft) {
        KeyMapping mapping = findKeyMapping(minecraft, key);
        if (mapping == null) {
            ControllerRadial.LOGGER.warn("No key mapping named '{}'", key);
            return;
        }
        if (!toggle) {
            // Works for functions with no keyboard key bound, too.
            WheelKeyPresses.press(mapping, holdTicks());
            return;
        }
        // Toggle mode: keep the key held (or let it go), registering one click when turning on.
        boolean nowDown = !mapping.isDown();
        mapping.setDown(nowDown);
        if (nowDown) {
            WheelKeyPresses.click(mapping);
        }
    }

    private void invokeCommand(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        String name = minecraft.player.getName().getString();
        String resolved = resolveCommand(command, name);

        if (clipboard) {
            minecraft.keyboardHandler.setClipboard(resolved);
            return;
        }
        if (minecraft.player.connection == null) {
            ControllerRadial.LOGGER.warn("Cannot run '{}' without a connection", resolved);
            return;
        }
        minecraft.player.connection.sendCommand(resolved);
    }

    /**
     * Turns a configured command into the text to send or copy.
     * <p>
     * Mirrors MineMenu: {@code @p} becomes the player's name, and {@code //...} (WorldEdit)
     * keeps one slash while anything else loses all leading slashes.
     */
    public static String resolveCommand(String raw, String playerName) {
        String command = raw == null ? "" : raw.trim();
        command = command.replace("@p", playerName == null ? "" : playerName);

        if (command.startsWith("//")) {
            // WorldEdit style: keep one slash, like MineMenu does.
            return command.replace("//", "/");
        }
        return command.replaceAll("^/+", "");
    }

    public static KeyMapping findKeyMapping(Minecraft minecraft, String name) {
        if (minecraft == null || name == null || name.isEmpty()) {
            return null;
        }
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (mapping.getName().equals(name)) {
                return mapping;
            }
        }
        return null;
    }

    /**
     * The key mapping's name in the player's language, e.g. "跳跃" for {@code key.jump}.
     * Falls back to the raw id when the mod supplying it has no translation.
     */
    public static String keyDisplayName(String keyName) {
        if (keyName == null || keyName.isEmpty()) {
            return "";
        }
        KeyMapping mapping = findKeyMapping(clientOrNull(), keyName);
        if (mapping == null) {
            return keyName;
        }
        return translateOrRaw(mapping.getName());
    }

    /** The group a key mapping belongs to in the player's language, e.g. "移动". */
    public static String keyCategoryName(String keyName) {
        KeyMapping mapping = findKeyMapping(clientOrNull(), keyName);
        return mapping == null ? "" : translateOrRaw(mapping.getCategory());
    }

    /** The key actually bound to a mapping, e.g. "空格". */
    public static String keyGlyph(String keyName) {
        KeyMapping mapping = findKeyMapping(clientOrNull(), keyName);
        return mapping == null ? "" : mapping.getTranslatedKeyMessage().getString();
    }

    /** "跳跃 [空格]" — how the wheel and the editor show a key binding. */
    public static String keyLabel(String keyName) {
        if (keyName == null || keyName.isEmpty()) {
            return "";
        }
        String name = keyDisplayName(keyName);
        String glyph = keyGlyph(keyName);
        return glyph.isEmpty() ? name : name + " [" + glyph + "]";
    }

    private static String translateOrRaw(String translationKey) {
        if (translationKey == null || translationKey.isEmpty()) {
            return "";
        }
        String translated = Component.translatable(translationKey).getString();
        return translated.isEmpty() || translated.equals(translationKey) ? translationKey : translated;
    }

    /**
     * The client, or {@code null} when there is none — the editor and the wheel run in game, but
     * keeping this safe lets the helpers be used without a running client.
     */
    private static Minecraft clientOrNull() {
        try {
            return Minecraft.getInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
