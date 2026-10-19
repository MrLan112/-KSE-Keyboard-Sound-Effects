package com.mrlan123.kse;

import com.mrlan123.kse.config.KSEConfig;
import com.mrlan123.kse.gui.KSEConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class KSEModClient implements ClientModInitializer {
    public static final String MOD_ID = "kse";

    public static KeyBinding openConfigKey;

    @Override
    public void onInitializeClient() {
        KSEConfig.load();

        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.kse.open_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B, // default key: B (can be changed in Options -> Controls)
                "category.kse.general"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new KSEConfigScreen(null));
                }
            }
        });
    }
}
