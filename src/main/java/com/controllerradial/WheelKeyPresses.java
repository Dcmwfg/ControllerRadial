package com.controllerradial;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * Fires a {@link KeyMapping} the way a real key press would, including for functions that have
 * <b>no keyboard key bound</b> at all.
 * <p>
 * Pressing a key in vanilla does two things: it registers a click ({@code clickCount++}) and it
 * holds the key down ({@code isDown = true}) until release. Most code polls one or the other,
 * so both are emulated here.
 * <p>
 * {@code clickCount} is private and the public {@code KeyMapping.click(key)} looks a mapping up
 * <i>by key</i>, so an unbound mapping can never be clicked. The trick used here is to lend the
 * mapping an unused key code for the duration of one click and immediately give it back — no
 * reflection, no mixins, and nothing about the player's config changes.
 */
public final class WheelKeyPresses {
    /** Keys nobody ever binds, checked again at runtime before being used. */
    private static final int[] SPARE_CANDIDATES = {
            299, 298, 297, 296, 295, 294, 293, 292, 291, 290, // F24 down to F13
            348, 347, 346, 345, 344                           // menu-ish keys, if free
    };

    private static final int DEFAULT_HOLD_TICKS = 3;
    private static final int MAX_HOLD_TICKS = 20 * 30;
    private static final Map<KeyMapping, Integer> HELD = new HashMap<>();

    private WheelKeyPresses() {
    }

    /** Emulates a tap: register the click, hold the key for {@code holdTicks}, then release it. */
    public static void press(KeyMapping mapping, int holdTicks) {
        click(mapping);
        mapping.setDown(true);
        int ticks = Math.min(Math.max(holdTicks, DEFAULT_HOLD_TICKS), MAX_HOLD_TICKS);
        HELD.put(mapping, ticks);
    }

    /** Only registers the click, for toggle mode where the hold state is the point. */
    public static void click(KeyMapping mapping) {
        if (!mapping.isUnbound()) {
            KeyMapping.click(mapping.getKey());
            return;
        }

        InputConstants.Key spare = spareKey();
        if (spare == null) {
            ControllerRadial.LOGGER.warn("[ControllerRadial] no free key available to trigger '{}';"
                    + " bind it to a key to use it from the wheel", mapping.getName());
            return;
        }

        InputConstants.Key original = mapping.getKey();
        try {
            mapping.setKey(spare);
            KeyMapping.resetMapping();
            KeyMapping.click(spare);
        } catch (Throwable t) {
            ControllerRadial.LOGGER.error("[ControllerRadial] could not trigger '{}'", mapping.getName(), t);
        } finally {
            mapping.setKey(original);
            KeyMapping.resetMapping();
        }
    }

    /** Called from the client tick event; releases anything still held. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || HELD.isEmpty()) {
            return;
        }
        List<KeyMapping> release = new ArrayList<>();
        for (Map.Entry<KeyMapping, Integer> entry : HELD.entrySet()) {
            if (entry.getValue() <= 1) {
                release.add(entry.getKey());
            } else {
                entry.setValue(entry.getValue() - 1);
            }
        }
        for (KeyMapping mapping : release) {
            mapping.setDown(false);
            HELD.remove(mapping);
        }
    }

    /** The key code to borrow, chosen so it clashes with nothing the player has bound. */
    static int spareKeyCode(IntPredicate taken) {
        for (int code : SPARE_CANDIDATES) {
            if (!taken.test(code)) {
                return code;
            }
        }
        return -1;
    }

    private static InputConstants.Key spareKey() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) {
            return null;
        }
        KeyMapping[] mappings = minecraft.options.keyMappings;
        int code = spareKeyCode(candidate -> {
            for (KeyMapping mapping : mappings) {
                if (mapping.getKey().getType() == InputConstants.Type.KEYSYM
                        && mapping.getKey().getValue() == candidate) {
                    return true;
                }
            }
            return false;
        });
        return code < 0 ? null : InputConstants.Type.KEYSYM.getOrCreate(code);
    }
}
