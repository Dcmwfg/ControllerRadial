package com.controllerradial;

import com.mojang.logging.LogUtils;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Standalone controller radial menu for Forge 1.20.1.
 * <p>
 * The wheel UI and selection logic are implemented in this mod; only the controller input layer
 * comes from Controlify. Controlify itself finds {@link ControlifyIntegration} through
 * {@code META-INF/services}, so this class intentionally contains no Controlify references.
 * <p>
 * The wheel layout editor is exposed through Forge's {@code ConfigScreenHandler} extension point,
 * which both Forge's own mod list and the Mod Menu port read — so it shows up as that mod's
 * "config" button without depending on either of them.
 */
@Mod(ControllerRadial.MOD_ID)
public final class ControllerRadial {
    public static final String MOD_ID = "controllerradial";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ControllerRadial() {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }

        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new WheelConfigScreen(parent)));

        // Releases the keys the wheel holds down for a moment.
        MinecraftForge.EVENT_BUS.register(WheelKeyPresses.class);
        // Adds a wheel layout button to the vanilla controls screen.
        MinecraftForge.EVENT_BUS.register(ControlsScreenHook.class);

        // Load (and write out) the wheel layout. A broken config must never stop the game
        // from starting, so this stays best-effort.
        try {
            LOGGER.info("[ControllerRadial] loaded {}", WheelConfig.get().summary());
        } catch (RuntimeException e) {
            LOGGER.error("[ControllerRadial] could not load the wheel layout; "
                    + "the wheel will be empty until config/controllerradial.json is fixed", e);
        }
    }
}
