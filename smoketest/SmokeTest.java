import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Headless verification for ControllerRadial.
 * <p>
 * Deliberately avoids touching Controlify's concrete classes beyond the entrypoint interface:
 * the published Controlify jar is SRG-remapped, so its class bodies cannot be executed inside a
 * dev (official-named) classpath. Both mods are SRG-remapped in production, so that mismatch
 * only affects this harness.
 */
public class SmokeTest {
    private static int passed = 0;
    private static int failed = 0;
    private static final Path RESOURCES = Path.of(System.getProperty("cr.resources", "src/main/resources"));
    private static final Path JAR = resolveJar();

    /** Bindings that ship with a default input; the map extras deliberately start unbound. */
    private static final List<String> BINDING_DEFAULTS = List.of(
            "controllerradial:wheel_open",
            "controllerradial:wheel_axis_up",
            "controllerradial:wheel_axis_down",
            "controllerradial:wheel_axis_left",
            "controllerradial:wheel_axis_right",
            "controllerradial:wheel_prev",
            "controllerradial:wheel_next",
            "controllerradial:wheel_edit",
            "controllerradial:gui_drag",
            "controllerradial:map_right_click",
            "controllerradial:map_nudge_up",
            "controllerradial:map_nudge_down",
            "controllerradial:map_nudge_left",
            "controllerradial:map_nudge_right");

    private static final List<String> BINDING_IDS = List.of(
            "controllerradial:wheel_open",
            "controllerradial:wheel_axis_up",
            "controllerradial:wheel_axis_down",
            "controllerradial:wheel_axis_left",
            "controllerradial:wheel_axis_right",
            "controllerradial:wheel_prev",
            "controllerradial:wheel_next",
            "controllerradial:wheel_edit",
            "controllerradial:map_axis_up",
            "controllerradial:map_axis_down",
            "controllerradial:map_axis_left",
            "controllerradial:map_axis_right",
            "controllerradial:map_nudge_up",
            "controllerradial:map_nudge_down",
            "controllerradial:map_nudge_left",
            "controllerradial:map_nudge_right",
            "controllerradial:map_zoom_in",
            "controllerradial:map_zoom_out",
            "controllerradial:map_right_click",
            "controllerradial:gui_drag");

    public static void main(String[] args) throws Exception {
        section("Controlify entrypoint wiring");
        List<ControlifyEntrypoint> providers = new ArrayList<>();
        ServiceLoader.load(ControlifyEntrypoint.class).forEach(providers::add);
        System.out.println("  providers = " + providers.stream().map(p -> p.getClass().getName()).toList());
        check("exactly one ControlifyEntrypoint provider on the classpath", providers.size() == 1);
        check("provider is com.controllerradial.ControlifyIntegration",
                providers.size() == 1 && providers.get(0).getClass().getName().equals("com.controllerradial.ControlifyIntegration"));
        Class<?> entrypoint = providers.isEmpty() ? null : providers.get(0).getClass();
        check("entrypoint implements the Controlify lifecycle",
                hasMethod(entrypoint, "onControlifyPreInit")
                        && hasMethod(entrypoint, "onControlifyInit")
                        && hasMethod(entrypoint, "onControllersDiscovered"));

        section("service registration file");
        Path serviceFile = RESOURCES.resolve("META-INF/services/dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint");
        check("service file exists in resources", Files.isRegularFile(serviceFile));
        if (Files.isRegularFile(serviceFile)) {
            check("service file names ControlifyIntegration",
                    Files.readString(serviceFile, StandardCharsets.UTF_8).trim()
                            .equals("com.controllerradial.ControlifyIntegration"));
        }

        section("default controller binds");
        Path binds = RESOURCES.resolve("assets/controlify/controllers/default_bind/default.json");
        check("default.json exists", Files.isRegularFile(binds));
        if (Files.isRegularFile(binds)) {
            JsonObject defaults = JsonParser.parseReader(new FileReader(binds.toFile()))
                    .getAsJsonObject().getAsJsonObject("defaults");
            check("defaults cover exactly our bound-by-default bindings", defaults.keySet().equals(new java.util.TreeSet<>(BINDING_DEFAULTS)));
            check("hold-to-open uses a button, not a trigger (no clash with 'use')",
                    "controlify:button/dpad_right".equals(inputOf(defaults, "controllerradial:wheel_open", "button")));
            check("selection uses the RIGHT stick up", "controlify:axis/right_stick_up".equals(inputOf(defaults, "controllerradial:wheel_axis_up", "axis")));
            check("selection uses the RIGHT stick down", "controlify:axis/right_stick_down".equals(inputOf(defaults, "controllerradial:wheel_axis_down", "axis")));
            check("selection uses the RIGHT stick left", "controlify:axis/right_stick_left".equals(inputOf(defaults, "controllerradial:wheel_axis_left", "axis")));
            check("selection uses the RIGHT stick right", "controlify:axis/right_stick_right".equals(inputOf(defaults, "controllerradial:wheel_axis_right", "axis")));
            check("previous preset uses LB", "controlify:button/left_shoulder".equals(inputOf(defaults, "controllerradial:wheel_prev", "button")));
            check("next preset uses RB", "controlify:button/right_shoulder".equals(inputOf(defaults, "controllerradial:wheel_next", "button")));
            check("edit-in-place uses a free face button",
                    "controlify:button/north".equals(inputOf(defaults, "controllerradial:wheel_edit", "button")));
            check("cursor and zoom bindings stay unbound, because Controlify already covers them",
                    !defaults.has("controllerradial:map_axis_up")
                            && !defaults.has("controllerradial:map_zoom_in")
                            && !defaults.has("controllerradial:map_zoom_out"));
            check("right click is bound to the right trigger",
                    "controlify:axis/right_trigger".equals(inputOf(defaults, "controllerradial:map_right_click", "axis")));
            check("menu nudging is bound to the dpad",
                    "controlify:button/dpad_up".equals(inputOf(defaults, "controllerradial:map_nudge_up", "button"))
                            && "controlify:button/dpad_right".equals(inputOf(defaults, "controllerradial:map_nudge_right", "button")));
            check("drag is bound to a button Controlify leaves free in screens",
                    "controlify:button/right_stick".equals(inputOf(defaults, "controllerradial:gui_drag", "button")));
        }

        section("language keys");
        for (String lang : List.of("en_us", "zh_cn")) {
            Path file = RESOURCES.resolve("assets/controllerradial/lang/" + lang + ".json");
            check(lang + ".json exists", Files.isRegularFile(file));
            if (Files.isRegularFile(file)) {
                JsonObject json = JsonParser.parseReader(new FileReader(file.toFile())).getAsJsonObject();
                List<String> keys = new ArrayList<>(List.of(
                        "controllerradial.binding.category",
                        "controlify.binding.controllerradial.wheel_open",
                        "controlify.binding.controllerradial.wheel_open.desc",
                        "controlify.binding.controllerradial.wheel_axis_up",
                        "controlify.binding.controllerradial.wheel_axis_down",
                        "controlify.binding.controllerradial.wheel_axis_left",
                        "controlify.binding.controllerradial.wheel_axis_right",
                        "controlify.binding.controllerradial.wheel_prev",
                        "controlify.binding.controllerradial.wheel_next",
                        "controlify.binding.controllerradial.wheel_edit",
                        "controlify.binding.controllerradial.wheel_edit.desc",
                        "controlify.binding.controllerradial.map_right_click",
                        "controlify.binding.controllerradial.map_right_click.desc",
                        "controlify.binding.controllerradial.gui_drag",
                        "controlify.binding.controllerradial.gui_drag.desc",
                        "controlify.binding.controllerradial.map_zoom_in",
                        "controlify.binding.controllerradial.map_nudge_down",
                        "controllerradial.wheel.title",
                        "controllerradial.wheel.hint",
                        "controllerradial.wheel.pages_hint",
                        "controllerradial.wheel.group_hint",
                        "controllerradial.wheel.empty",
                        "controllerradial.config.title",
                        "controllerradial.controls.button",
                        "controllerradial.config.add",
                        "controllerradial.config.add_preset",
                        "controllerradial.config.remove_preset",
                        "controllerradial.config.rename_preset",
                        "controllerradial.config.rename.title",
                        "controllerradial.config.rename.hint",
                        "controllerradial.config.rename.current",
                        "controllerradial.config.empty",
                        "controllerradial.config.on",
                        "controllerradial.config.off",
                        "controllerradial.config.action.key",
                        "controllerradial.config.action.key_toggle",
                        "controllerradial.config.action.key_hold",
                        "controllerradial.config.action.wheel",
                        "controllerradial.config.action.command",
                        "controllerradial.config.action.command_clipboard",
                        "controllerradial.config.action.none",
                        "controllerradial.config.cell.title",
                        "controllerradial.config.cell.text",
                        "controllerradial.config.cell.type_key",
                        "controllerradial.config.cell.type_command",
                        "controllerradial.config.cell.type_wheel",
                        "controllerradial.config.cell.jump",
                        "controllerradial.config.cell.jump_hint",
                        "controllerradial.config.cell.key",
                        "controllerradial.config.cell.command",
                        "controllerradial.config.cell.command_hint",
                        "controllerradial.config.cell.toggle",
                        "controllerradial.config.cell.toggle_hint",
                        "controllerradial.config.cell.trigger",
                        "controllerradial.config.cell.trigger.tap",
                        "controllerradial.config.cell.trigger.hold",
                        "controllerradial.config.cell.trigger.toggle",
                        "controllerradial.config.cell.trigger_hint",
                        "controllerradial.config.cell.clipboard",
                        "controllerradial.config.cell.icon",
                        "controllerradial.config.cell.pick_key",
                        "controllerradial.config.cell.pick_icon",
                        "controllerradial.config.cell.save",
                        "controllerradial.config.cell.delete",
                        "controllerradial.config.pick_key",
                        "controllerradial.config.pick_function",
                        "controllerradial.config.common",
                        "controllerradial.config.all",
                        "controllerradial.config.pick_icon",
                        "controllerradial.config.search",
                        "controllerradial.config.search_item",
                        "controllerradial.config.unbound",
                        "controllerradial.config.no_icon"));
                for (String key : keys) {
                    check(lang + " has " + key, json.has(key) && !json.get(key).getAsString().isBlank());
                }
            }
        }

        section("default wheels");
        Class<?> configClass = Class.forName("com.controllerradial.WheelConfig");
        Method defaults = configClass.getDeclaredMethod("defaults");
        defaults.setAccessible(true);
        Method normaliseConfig = configClass.getDeclaredMethod("normalise");
        normaliseConfig.setAccessible(true);
        Method summary = configClass.getMethod("summary");

        Object config = defaults.invoke(null);
        normaliseConfig.invoke(config);
        check("summary of the defaults reads well",
                "3 wheels, 25 cells".equals(summary.invoke(config)),
                "got " + summary.invoke(config));

        Object presets = configClass.getField("presets").get(config);
        check("three presets by default", ((List<?>) presets).size() == 3);
        if (((List<?>) presets).size() == 3) {
            List<Integer> sizes = new ArrayList<>();
            List<String> names = new ArrayList<>();
            boolean actionable = true;
            boolean labelled = true;
            for (Object preset : (List<?>) presets) {
                Class<?> presetClass = preset.getClass();
                names.add((String) presetClass.getField("name").get(preset));
                List<?> slots = (List<?>) presetClass.getField("slots").get(preset);
                sizes.add(slots.size());
                Method normaliseSlot = Class.forName("com.controllerradial.WheelSlot").getDeclaredMethod("normalise");
                normaliseSlot.setAccessible(true);
                for (Object slot : slots) {
                    normaliseSlot.invoke(slot);
                    Class<?> slotClass = slot.getClass();
                    String text = (String) slotClass.getField("text").get(slot);
                    String key = (String) slotClass.getField("key").get(slot);
                    String command = (String) slotClass.getField("command").get(slot);
                    labelled &= !text.isBlank();
                    actionable &= !key.isBlank() || !command.isBlank();
                }
            }
            System.out.println("  preset names  = " + names);
            System.out.println("  preset sizes  = " + sizes);
            check("every preset has a usable name", names.stream().noneMatch(n -> n == null || n.isBlank()));
            check("every cell has display text", labelled);
            check("every cell has an action (key or command)", actionable);
        }

        section("config migration and shape");
        Object legacyConfig = new com.google.gson.Gson().fromJson(
                "{\"slots\":[{\"name\":\"旧格\",\"command\":\"/time set day\"}],\"activationThreshold\":9}",
                configClass);
        normaliseConfig.invoke(legacyConfig);
        List<?> migratedPresets = (List<?>) configClass.getField("presets").get(legacyConfig);
        check("legacy single-wheel config migrates to one wheel", migratedPresets.size() == 1);
        check("a fresh config still starts with three wheels",
                ((List<?>) configClass.getField("presets").get(config)).size() == 3);
        Object migratedFirst = migratedPresets.get(0);
        List<?> migratedSlots = (List<?>) migratedFirst.getClass().getField("slots").get(migratedFirst);
        check("legacy slot survives the migration", migratedSlots.size() == 1);
        if (migratedSlots.size() == 1) {
            Object migratedSlot = migratedSlots.get(0);
            check("legacy name becomes the cell text",
                    "旧格".equals(migratedSlot.getClass().getField("text").get(migratedSlot)));
            check("legacy command keeps working, slash stripped",
                    "time set day".equals(migratedSlot.getClass().getField("command").get(migratedSlot)));
        }
        check("legacy slots field is cleared so nothing reads it later",
                configClass.getField("slots").get(legacyConfig) == null);
        check("threshold is clamped into range",
                (float) configClass.getField("activationThreshold").get(legacyConfig) <= 0.95f);

        for (Object preset : migratedPresets) {
            check("preset has a non-null slot list", preset.getClass().getField("slots").get(preset) != null);
        }

        section("config editing helpers");
        Object editable = defaults.invoke(null);
        normaliseConfig.invoke(editable);
        Method preset = configClass.getMethod("preset", int.class);
        Method addCell = configClass.getMethod("addCell", int.class);
        Method removeCell = configClass.getMethod("removeCell", int.class, int.class);

        check("preset(0) is available", preset.invoke(editable, 0) != null);
        check("preset(-1) wraps to the last wheel",
                preset.invoke(editable, -1) == preset.invoke(editable, 2));
        Object firstPreset = preset.invoke(editable, 0);
        List<?> firstSlots = (List<?>) firstPreset.getClass().getField("slots").get(firstPreset);
        int before = firstSlots.size();
        check("addCell appends an empty cell", addCell.invoke(editable, 0) != null && firstSlots.size() == before + 1);
        removeCell.invoke(editable, 0, before);
        check("removeCell takes it back out", firstSlots.size() == before);
        removeCell.invoke(editable, 0, 999);
        check("removeCell ignores an out of range index", firstSlots.size() == before);

        // A separate config object, so the cell tests above keep their own object intact.
        Object presetConfig = defaults.invoke(null);
        normaliseConfig.invoke(presetConfig);
        Method addPreset = configClass.getMethod("addPreset");
        Method removePreset = configClass.getMethod("removePreset", int.class);
        List<?> presetsList = (List<?>) configClass.getField("presets").get(presetConfig);
        int presetsBefore = presetsList.size();
        check("a config keeps three wheels by default", presetsBefore == 3);

        Object added = addPreset.invoke(presetConfig);
        check("addPreset appends a wheel", added != null && presetsList.size() == presetsBefore + 1);
        check("the appended wheel is the one at the end", presetsList.get(presetsList.size() - 1) == added);
        check("the appended wheel has a name",
                !((String) added.getClass().getField("name").get(added)).isBlank());

        for (int i = 0; i < 20 && presetsList.size() < 12; i++) {
            addPreset.invoke(presetConfig);
        }
        check("addPreset refuses to go past 12 wheels", addPreset.invoke(presetConfig) == null);
        check("removePreset drops one wheel",
                (boolean) removePreset.invoke(presetConfig, 0) && presetsList.size() == 11);
        check("removePreset ignores an out of range index",
                !(boolean) removePreset.invoke(presetConfig, 99));
        for (int i = 0; i < 20 && presetsList.size() > 1; i++) {
            removePreset.invoke(presetConfig, 0);
        }
        check("the last wheel cannot be removed",
                presetsList.size() == 1 && !(boolean) removePreset.invoke(presetConfig, 0));

        Object fiveWheels = new com.google.gson.Gson().fromJson(
                "{\"presets\":[{\"name\":\"a\"},{\"name\":\"b\"},{\"name\":\"c\"},{\"name\":\"d\"},{\"name\":\"e\"}]}",
                configClass);
        normaliseConfig.invoke(fiveWheels);
        check("a config with five wheels keeps all five",
                ((List<?>) configClass.getField("presets").get(fiveWheels)).size() == 5);
        for (int i = 0; i < 20 && firstSlots.size() < 12; i++) {
            addCell.invoke(editable, 0);
        }
        check("cells can be filled up to the cap", firstSlots.size() == 12, "got " + firstSlots.size());
        check("addCell refuses to go past 12 cells", addCell.invoke(editable, 0) == null);

        section("cell actions (MineMenu style)");
        Class<?> slotClass2 = Class.forName("com.controllerradial.WheelSlot");
        Method normalise2 = slotClass2.getDeclaredMethod("normalise");
        normalise2.setAccessible(true);
        Method actionName = slotClass2.getDeclaredMethod("actionName");
        actionName.setAccessible(true);
        Method resolveCommand = slotClass2.getDeclaredMethod("resolveCommand", String.class, String.class);

        Object keyCell = slotClass2.getDeclaredConstructor().newInstance();
        slotClass2.getField("text").set(keyCell, "跳跃");
        slotClass2.getField("key").set(keyCell, "key.jump");
        normalise2.invoke(keyCell);
        check("a key cell infers type=key", "key".equals(slotClass2.getField("type").get(keyCell)));
        check("a key cell reports its action", "key.jump".equals(actionName.invoke(keyCell)));
        check("toggle defaults to off", !(boolean) slotClass2.getField("toggle").get(keyCell));
        slotClass2.getField("hold").setFloat(keyCell, 99.0f);
        normalise2.invoke(keyCell);
        check("a hold longer than 30 s is clamped",
                (float) slotClass2.getField("hold").getFloat(keyCell) == 30.0f);
        check("30 s becomes 600 ticks",
                (int) slotClass2.getDeclaredMethod("holdTicks").invoke(keyCell) == 600);
        slotClass2.getField("hold").setFloat(keyCell, 1.0f);
        normalise2.invoke(keyCell);
        check("one second becomes 20 ticks",
                (int) slotClass2.getDeclaredMethod("holdTicks").invoke(keyCell) == 20);
        slotClass2.getField("hold").setFloat(keyCell, -5.0f);
        normalise2.invoke(keyCell);
        check("a negative hold becomes a plain tap",
                (int) slotClass2.getDeclaredMethod("holdTicks").invoke(keyCell) == 0);

        Object commandCell = slotClass2.getDeclaredConstructor().newInstance();
        slotClass2.getField("command").set(commandCell, "/gamemode creative");
        normalise2.invoke(commandCell);
        check("a command cell infers type=command", "command".equals(slotClass2.getField("type").get(commandCell)));
        check("a command cell strips its slash", "gamemode creative".equals(actionName.invoke(commandCell)));
        check("a command cell labels itself with a slash",
                "/gamemode creative".equals(slotClass2.getDeclaredMethod("displayName").invoke(commandCell)));

        Object blankCell = slotClass2.getDeclaredConstructor().newInstance();
        normalise2.invoke(blankCell);
        check("an unbound cell has no type", "".equals(slotClass2.getField("type").get(blankCell)));
        check("an unbound cell is empty", (boolean) slotClass2.getDeclaredMethod("isEmpty").invoke(blankCell));

        Object jumpCell = slotClass2.getDeclaredConstructor().newInstance();
        slotClass2.getField("type").set(jumpCell, "wheel");
        slotClass2.getField("jump").setInt(jumpCell, 0);
        normalise2.invoke(jumpCell);
        check("a jump cell keeps type=wheel", "wheel".equals(slotClass2.getField("type").get(jumpCell)));
        check("a jump target below one is clamped", (int) slotClass2.getField("jump").getInt(jumpCell) == 1);
        check("a jump cell is not empty", !(boolean) slotClass2.getDeclaredMethod("isEmpty").invoke(jumpCell));
        check("a jump cell reports it swaps wheels",
                (boolean) slotClass2.getDeclaredMethod("jumpsWheel").invoke(jumpCell));
        check("a label-less jump cell shows an arrow and the target",
                "→ 1".equals(slotClass2.getDeclaredMethod("displayName").invoke(jumpCell)));
        slotClass2.getField("jump").setInt(jumpCell, 99);
        normalise2.invoke(jumpCell);
        check("a jump target past the cap is clamped",
                (int) slotClass2.getField("jump").getInt(jumpCell)
                        == Class.forName("com.controllerradial.WheelConfig").getField("MAX_PRESETS").getInt(null));
        check("a normal cell does not swap wheels",
                !(boolean) slotClass2.getDeclaredMethod("jumpsWheel").invoke(keyCell));

        check("plain commands lose every leading slash",
                "time set day".equals(resolveCommand.invoke(null, "/time set day", "Steve")));
        check("WorldEdit style collapses the first slash pair",
                "//time set day".equals(resolveCommand.invoke(null, "///time set day", "Steve")));
        check("WorldEdit keeps one slash",
                "/wand".equals(resolveCommand.invoke(null, "//wand", "Steve")));
        check("@p becomes the player name",
                "tp @s Steve".equals(resolveCommand.invoke(null, "tp @s @p", "Steve")));
        check("@p substitution works anywhere in the command",
                "say hi Steve".equals(resolveCommand.invoke(null, "say hi @p", "Steve")));
        check("no leading slash is fine too",
                "gamemode survival".equals(resolveCommand.invoke(null, "gamemode survival", "Steve")));

        section("key naming (no client)");
        Method keyDisplayName = slotClass2.getDeclaredMethod("keyDisplayName", String.class);
        Method keyLabel = slotClass2.getDeclaredMethod("keyLabel", String.class);
        Method keyGlyph = slotClass2.getDeclaredMethod("keyGlyph", String.class);
        Method keyCategoryName = slotClass2.getDeclaredMethod("keyCategoryName", String.class);
        Method findKeyMappingNull = slotClass2.getDeclaredMethod("findKeyMapping", Minecraft.class, String.class);

        check("findKeyMapping tolerates a missing client",
                findKeyMappingNull.invoke(null, null, "key.jump") == null);
        check("keyDisplayName falls back to the raw id without a client",
                "key.jump".equals(keyDisplayName.invoke(null, "key.jump")));
        check("keyGlyph is empty without a client", "".equals(keyGlyph.invoke(null, "key.jump")));
        check("keyCategoryName is empty without a client", "".equals(keyCategoryName.invoke(null, "key.jump")));
        check("keyLabel still shows something without a client",
                "key.jump".equals(keyLabel.invoke(null, "key.jump")));
        check("keyLabel of an empty key is empty", "".equals(keyLabel.invoke(null, "")));

        Object bareKeyCell = slotClass2.getDeclaredConstructor().newInstance();
        slotClass2.getField("key").set(bareKeyCell, "key.jump");
        normalise2.invoke(bareKeyCell);
        Method renderLabel = slotClass2.getDeclaredMethod("renderLabel");
        check("a label-less key cell still renders something, without a client",
                "key.jump".equals(renderLabel.invoke(bareKeyCell)));
        check("a labelled cell renders its label",
                "跳跃".equals(renderLabel.invoke(keyCell)));

        section("unbound functions");
        Class<?> presses = Class.forName("com.controllerradial.WheelKeyPresses");
        Method spareKeyCode = presses.getDeclaredMethod("spareKeyCode", java.util.function.IntPredicate.class);
        spareKeyCode.setAccessible(true);
        check("a free key is offered when nothing is bound",
                (int) spareKeyCode.invoke(null, (java.util.function.IntPredicate) code -> false) == 299);
        check("already-used keys are skipped",
                (int) spareKeyCode.invoke(null, (java.util.function.IntPredicate) code -> code == 299) == 298);
        check("a busy keyboard yields no spare key",
                (int) spareKeyCode.invoke(null, (java.util.function.IntPredicate) code -> true) == -1);

        section("wheel layout");
        Class<?> layoutClass = Class.forName("com.controllerradial.WheelLayout");
        Method compute = layoutClass.getDeclaredMethod("compute", int.class, int.class, int.class);
        compute.setAccessible(true);
        Class<?> geometryClass = Class.forName("com.controllerradial.WheelLayout$Geometry");
        Method ringRadius = geometryClass.getDeclaredMethod("ringRadius");
        Method cellRadius = geometryClass.getDeclaredMethod("cellRadius");
        ringRadius.setAccessible(true);
        cellRadius.setAccessible(true);

        int[][] screens = {{640, 360}, {854, 480}, {480, 270}, {320, 180}, {1920, 1080}};
        int[] counts = {4, 6, 8, 9, 12};
        boolean neverOverlapsText = true;
        boolean cellsNeverTouch = true;
        for (int[] screen : screens) {
            for (int count : counts) {
                Object geometry = compute.invoke(null, count, screen[0], screen[1]);
                float ring = (float) ringRadius.invoke(geometry);
                float cell = (float) cellRadius.invoke(geometry);
                float halfHeight = screen[1] / 2.0f - 50.0f;
                float halfWidth = screen[0] / 2.0f - 6.0f;
                if (ring + cell > Math.min(halfHeight, halfWidth) + 0.01f) {
                    neverOverlapsText = false;
                    System.out.println("      too big: " + screen[0] + "x" + screen[1] + " count=" + count
                            + " ring=" + ring + " cell=" + cell);
                }
                float spacing = ring * (float) (Math.PI * 2.0) / count;
                if (count > 1 && spacing + 0.01f < cell * 2.0f && ring > 60.0f) {
                    cellsNeverTouch = false;
                    System.out.println("      overlapping cells: " + screen[0] + "x" + screen[1]
                            + " count=" + count + " spacing=" + spacing + " cell=" + cell);
                }
            }
        }
        check("the wheel never reaches into the header or footer", neverOverlapsText);
        check("cells never touch on a normal screen", cellsNeverTouch);
        Object empty = compute.invoke(null, 0, 640, 360);
        check("no cells means no ring", (float) ringRadius.invoke(empty) == 0.0f);

        Method rowButtonWidth = layoutClass.getDeclaredMethod("rowButtonWidth", int.class, int.class, int.class);
        Method rowButtonX = layoutClass.getDeclaredMethod("rowButtonX", int.class, int.class, int.class, int.class);
        rowButtonWidth.setAccessible(true);
        rowButtonX.setAccessible(true);
        java.lang.reflect.Field widthField = layoutClass.getDeclaredField("BOTTOM_ROW_WIDTH");
        java.lang.reflect.Field gapField = layoutClass.getDeclaredField("BOTTOM_ROW_GAP");
        widthField.setAccessible(true);
        gapField.setAccessible(true);
        int total = (int) widthField.get(null);
        int gap = (int) gapField.get(null);

        int three = (int) rowButtonWidth.invoke(null, total, 3, gap);
        int two = (int) rowButtonWidth.invoke(null, total, 2, gap);
        check("a three-button row keeps the full width",
                (int) rowButtonX.invoke(null, 0, three, gap, 2) + three <= total);
        check("a two-button row keeps the same full width",
                (int) rowButtonX.invoke(null, 0, two, gap, 1) + two <= total);
        check("both bottom rows end on the same edge",
                Math.abs(((int) rowButtonX.invoke(null, 0, three, gap, 2) + three)
                        - ((int) rowButtonX.invoke(null, 0, two, gap, 1) + two)) <= 2);
        check("buttons in a row never overlap",
                (int) rowButtonX.invoke(null, 0, three, gap, 1) >= three);
        check("an empty row has no width", (int) rowButtonWidth.invoke(null, total, 0, gap) == 0);

        section("controls screen button placement");
        Class<?> hook = Class.forName("com.controllerradial.ControlsScreenHook");
        Method findFreeCell = hook.getDeclaredMethod("findFreeCell", int.class, int.class,
                java.util.function.Predicate.class);
        findFreeCell.setAccessible(true);

        java.util.function.Predicate<int[]> nothingTaken = cell -> false;
        int[] firstCell = (int[]) findFreeCell.invoke(null, 640, 360, nothingTaken);
        check("an empty grid gets the top-left cell",
                firstCell != null && firstCell[0] == 640 / 2 - 155 && firstCell[1] == 360 / 6 - 12);

        // Rows are filled two at a time, left then right: the 6th cell is row 3's right column.
        final int[] counter = {0};
        java.util.function.Predicate<int[]> sixTaken = cell -> counter[0]++ < 6;
        int[] seventh = (int[]) findFreeCell.invoke(null, 640, 360, sixTaken);
        check("three full rows are skipped and the next row is used",
                seventh != null && seventh[0] == 640 / 2 - 155 && seventh[1] == 360 / 6 - 12 + 72);

        int[] leftCell = (int[]) findFreeCell.invoke(null, 640, 360,
                (java.util.function.Predicate<int[]>) cell -> cell[0] == 640 / 2 - 155 && cell[1] == 360 / 6 - 12);
        check("only the left column is skipped when just it is taken",
                leftCell != null && leftCell[0] == 640 / 2 - 155 + 160 && leftCell[1] == 360 / 6 - 12);

        check("a completely full grid reports no room",
                findFreeCell.invoke(null, 640, 360, (java.util.function.Predicate<int[]>) cell -> true) == null);

        int[] small = (int[]) findFreeCell.invoke(null, 320, 240,
                (java.util.function.Predicate<int[]>) cell -> cell[1] >= 240 - 20);
        check("a cell is never placed off the bottom", small == null || small[1] + 20 <= 240 - 8);

        section("map controls");
        Class<?> mapClass = Class.forName("com.controllerradial.MapInput");
        Method looksLikeMap = mapClass.getDeclaredMethod("looksLikeMapScreen", String.class);
        looksLikeMap.setAccessible(true);
        Method stickDelta = mapClass.getDeclaredMethod("stickDelta", float.class);
        stickDelta.setAccessible(true);
        Method clampCursor = mapClass.getDeclaredMethod("clampCursor", double.class, int.class);
        clampCursor.setAccessible(true);

        check("the fullscreen map is recognised",
                (boolean) looksLikeMap.invoke(null, "xaero.map.gui.GuiMap"));
        check("xaero's other screens are recognised too",
                (boolean) looksLikeMap.invoke(null, "xaero.common.gui.GuiMinimapSettings"));
        check("vanilla screens are not",
                !(boolean) looksLikeMap.invoke(null, "net.minecraft.client.gui.screens.TitleScreen"));
        check("a missing screen is not", !(boolean) looksLikeMap.invoke(null, (Object) null));
        check("a look-alike package is not matched by accident",
                !(boolean) looksLikeMap.invoke(null, "com.example.xaero.map.GuiMap"));

        check("a centred stick does not move the cursor",
                Math.abs((double) stickDelta.invoke(null, 0.0f)) < 0.001);
        check("a slightly off-centre stick is ignored (deadzone)",
                Math.abs((double) stickDelta.invoke(null, 0.15f)) < 0.001);
        check("full deflection gives the full speed",
                Math.abs((double) stickDelta.invoke(null, 1.0f) - 14.0) < 0.001);
        check("the direction follows the stick",
                (double) stickDelta.invoke(null, -1.0f) < 0.0
                        && (double) stickDelta.invoke(null, 1.0f) > 0.0);
        check("partial deflection is slower than full",
                (double) stickDelta.invoke(null, 0.6f) < (double) stickDelta.invoke(null, 1.0f));

        check("the cursor cannot leave the window (top left)",
                (double) clampCursor.invoke(null, -50.0, 800) == 0.0);
        check("the cursor cannot leave the window (bottom right)",
                (double) clampCursor.invoke(null, 5000.0, 800) == 799.0);

        section("wheel naming");
        Class<?> presetClass2 = Class.forName("com.controllerradial.WheelPreset");
        Object namedPreset = presetClass2.getDeclaredConstructor(String.class).newInstance("战斗");
        check("a named wheel shows its name",
                "战斗".equals(presetClass2.getDeclaredMethod("displayName", int.class).invoke(namedPreset, 0)));
        Object unnamedPreset = presetClass2.getDeclaredConstructor().newInstance();
        check("an unnamed wheel falls back to wheel N",
                "轮盘 3".equals(presetClass2.getDeclaredMethod("displayName", int.class).invoke(unnamedPreset, 2)));
        presetClass2.getField("name").set(unnamedPreset, "   ");
        check("a whitespace-only name also falls back",
                "轮盘 1".equals(presetClass2.getDeclaredMethod("displayName", int.class).invoke(unnamedPreset, 0)));

        section("text auto-fit");
        Method scaleFor = Class.forName("com.controllerradial.TextFit")
                .getDeclaredMethod("scaleFor", int.class, int.class, int.class, int.class, int.class);
        expect(scaleFor, "text smaller than the box is not scaled up", 1.0f, 10, 9, 1, 40, 20);
        expect(scaleFor, "text exactly the box width fits", 1.0f, 40, 9, 1, 40, 20);
        expect(scaleFor, "too-wide text shrinks to the box", 0.5f, 80, 9, 1, 40, 20);
        expect(scaleFor, "tall text shrinks by height", 0.5f, 10, 20, 2, 40, 20);
        expect(scaleFor, "long CJK label shrinks", 0.25f, 160, 9, 1, 40, 30);
        expect(scaleFor, "empty box is left alone", 1.0f, 40, 9, 1, 0, 20);
        expect(scaleFor, "no text is left alone", 1.0f, 0, 9, 1, 40, 20);

        section("stick to slot mapping");
        Method selectionFor = Class.forName("com.controllerradial.WheelSelection")
                .getDeclaredMethod("selectionFor", float.class, float.class, int.class, float.class);
        expect(selectionFor, "8 slots, stick right", 2, 1f, 0f, 8, 0.5f);
        expect(selectionFor, "8 slots, stick down", 4, 0f, 1f, 8, 0.5f);
        expect(selectionFor, "8 slots, stick left", 6, -1f, 0f, 8, 0.5f);
        expect(selectionFor, "8 slots, stick up", 0, 0f, -1f, 8, 0.5f);
        expect(selectionFor, "8 slots, stick up-right", 1, 0.7071f, -0.7071f, 8, 0.5f);
        expect(selectionFor, "8 slots, stick down-left", 5, -0.7071f, 0.7071f, 8, 0.5f);
        expect(selectionFor, "8 slots, centred stick selects nothing", -1, 0.2f, 0.2f, 8, 0.5f);
        expect(selectionFor, "8 slots, exactly on threshold still counts", 2, 0.5f, 0f, 8, 0.5f);
        expect(selectionFor, "4 slots, stick right", 1, 1f, 0f, 4, 0.5f);
        expect(selectionFor, "4 slots, stick down", 2, 0f, 1f, 4, 0.5f);
        expect(selectionFor, "4 slots, stick left", 3, -1f, 0f, 4, 0.5f);
        expect(selectionFor, "4 slots, stick up", 0, 0f, -1f, 4, 0.5f);
        expect(selectionFor, "9 slots (hotbar preset), stick right", 2, 1f, 0f, 9, 0.5f);
        expect(selectionFor, "9 slots, stick up", 0, 0f, -1f, 9, 0.5f);
        expect(selectionFor, "12 slots, stick right", 3, 1f, 0f, 12, 0.5f);
        expect(selectionFor, "no slots selects nothing", -1, 1f, 0f, 0, 0.5f);

        section("confirm on stick release");
        Method confirmsOnRelease = Class.forName("com.controllerradial.WheelSelection")
                .getDeclaredMethod("confirmsOnRelease", boolean.class, int.class, int.class);
        check("stick back to centre confirms the highlighted cell",
                (boolean) confirmsOnRelease.invoke(null, true, 2, -1));
        check("a stick that was never pushed cannot confirm",
                !(boolean) confirmsOnRelease.invoke(null, false, 2, -1));
        check("nothing highlighted means nothing to confirm",
                !(boolean) confirmsOnRelease.invoke(null, true, -1, -1));
        check("a still-engaged stick does not confirm",
                !(boolean) confirmsOnRelease.invoke(null, true, 2, 3));

        section("preset cycling");
        Class<?> managerClass = Class.forName("com.controllerradial.WheelManager");
        Method remember = managerClass.getDeclaredMethod("rememberPreset", int.class, int.class);
        Method current = managerClass.getDeclaredMethod("presetIndex");
        remember.setAccessible(true);
        current.setAccessible(true);
        remember.invoke(null, 1, 3);
        check("remembers a valid page", (int) current.invoke(null) == 1);
        remember.invoke(null, 3, 3);
        check("wraps past the last page", (int) current.invoke(null) == 0);
        remember.invoke(null, -1, 3);
        check("wraps before the first page", (int) current.invoke(null) == 2);
        remember.invoke(null, 5, 0);
        check("falls back to the first page when there are none", (int) current.invoke(null) == 0);
        remember.invoke(null, 0, 3);

        section("slot normalisation");
        Class<?> slotClass = Class.forName("com.controllerradial.WheelSlot");
        Method normalise = slotClass.getDeclaredMethod("normalise");
        normalise.setAccessible(true);
        Method displayName = slotClass.getDeclaredMethod("displayName");
        displayName.setAccessible(true);
        Method isEmpty = slotClass.getDeclaredMethod("isEmpty");
        isEmpty.setAccessible(true);
        Method keyFactory = slotClass.getDeclaredMethod("key", String.class, String.class);

        Object keySlot = keyFactory.invoke(null, "  跳跃  ", " key.jump ");
        normalise.invoke(keySlot);
        check("text is trimmed", "跳跃".equals(field(keySlot, "text")));
        check("key name is trimmed", "key.jump".equals(field(keySlot, "key")));
        check("display uses the custom text", "跳跃".equals(displayName.invoke(keySlot)));
        check("key cell is not empty", !(boolean) isEmpty.invoke(keySlot));

        Object legacySlot = slotClass.getDeclaredConstructor().newInstance();
        slotClass.getField("name").set(legacySlot, "旧字段");
        normalise.invoke(legacySlot);
        check("legacy name field migrates into text", "旧字段".equals(displayName.invoke(legacySlot)));

        Object commandSlot = slotClass.getDeclaredMethod("command", String.class, String.class, String.class)
                .invoke(null, "创造", "  /gamemode creative  ", "minecraft:grass_block");
        normalise.invoke(commandSlot);
        check("leading slash and padding stripped from command",
                "gamemode creative".equals(field(commandSlot, "command")));
        check("command cell is not empty", !(boolean) isEmpty.invoke(commandSlot));

        Object blank = slotClass.getDeclaredConstructor().newInstance();
        normalise.invoke(blank);
        check("blank cell reports empty", (boolean) isEmpty.invoke(blank));

        section("packaged jar");
        File jar = JAR.toFile();
        check("production jar exists at " + JAR, jar.isFile());
        if (jar.isFile()) {
            try (var zip = new java.util.zip.ZipFile(jar)) {
                for (String entry : List.of(
                        "META-INF/mods.toml",
                        "META-INF/services/dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint",
                        "assets/controlify/controllers/default_bind/default.json",
                        "assets/controllerradial/lang/en_us.json",
                        "assets/controllerradial/lang/zh_cn.json",
                        "pack.mcmeta",
                        "com/controllerradial/ControlifyIntegration.class",
                        "com/controllerradial/WheelScreen.class",
                        "com/controllerradial/WheelScreen$Processor.class",
                        "com/controllerradial/WheelPreset.class",
                        "com/controllerradial/WheelSelection.class",
                        "com/controllerradial/TextFit.class",
                        "com/controllerradial/WheelConfigScreen.class",
                        "com/controllerradial/WheelCellScreen.class",
                        "com/controllerradial/KeyPickerScreen.class",
                        "com/controllerradial/ItemPickerScreen.class",
                        "com/controllerradial/WheelKeyPresses.class",
                        "com/controllerradial/ControlsScreenHook.class",
                        "com/controllerradial/WheelLayout.class",
                        "com/controllerradial/MapSupport.class",
                        "com/controllerradial/MapInput.class",
                        "com/controllerradial/MapSupport.class",
                        "com/controllerradial/WheelPresetNameScreen.class")) {
                    check("jar contains " + entry, zip.getEntry(entry) != null);
                }
                check("jar does not bundle Controlify itself",
                        zip.getEntry("dev/isxander/controlify/Controlify.class") == null);
            }
        }

        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static Path resolveJar() {
        String explicit = System.getProperty("cr.jar");
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit);
        }
        Path libs = Path.of("build/libs");
        if (Files.isDirectory(libs)) {
            try (var stream = Files.list(libs)) {
                return stream
                        .filter(p -> p.toString().endsWith(".jar"))
                        .max((a, b) -> {
                            try {
                                return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                            } catch (Exception e) {
                                return a.toString().compareTo(b.toString());
                            }
                        })
                        .orElse(libs.resolve("missing.jar"));
            } catch (Exception ignored) {
                // fall through
            }
        }
        return libs.resolve("missing.jar");
    }

    private static String inputOf(JsonObject defaults, String id, String kind) {
        JsonObject entry = defaults.getAsJsonObject(id);
        return entry != null && entry.has(kind) ? entry.get(kind).getAsString() : "";
    }

    private static boolean hasMethod(Class<?> type, String name) {
        if (type == null) {
            return false;
        }
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void expect(Method method, String what, float expected, Object... args) throws Exception {
        float actual = (float) method.invoke(null, args);
        check(what + " -> " + expected, Math.abs(actual - expected) < 0.0001f, "got " + actual);
    }

    private static void expect(Method method, String what, int expected, Object... args) throws Exception {
        int actual = (int) method.invoke(null, args);
        check(what + " -> " + expected, actual == expected, "got " + actual);
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("== " + title + " ==");
    }

    private static void check(String what, boolean ok) {
        check(what, ok, "");
    }

    private static void check(String what, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("  PASS  " + what);
        } else {
            failed++;
            System.out.println("  FAIL  " + what + (detail.isEmpty() ? "" : " (" + detail + ")"));
        }
    }
}
