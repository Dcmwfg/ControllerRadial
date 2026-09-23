package com.controllerradial;

import java.util.ArrayList;
import java.util.List;

/**
 * One of the wheel pages. Hold the trigger and tap LB / RB to cycle between them.
 */
public class WheelPreset {
    /** Shown at the top of the wheel. */
    public String name = "";
    /** Slots, starting at the top of the wheel and running clockwise. */
    public List<WheelSlot> slots = new ArrayList<>();

    public WheelPreset() {
    }

    public WheelPreset(String name) {
        this.name = name;
    }

    public String displayName(int index) {
        return name == null || name.isBlank() ? "轮盘 " + (index + 1) : name.trim();
    }

    public WheelSlot slot(int index) {
        return index >= 0 && index < slots.size() ? slots.get(index) : null;
    }
}
