package com.mrlan123.kse;

import com.mojang.blaze3d.platform.InputConstants;
import com.mrlan123.kse.config.KSEConfig;
import com.mrlan123.kse.gui.KSEConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class KSEModClient implements ClientModInitializer {
    public static final String MOD_ID = "kse";

    public static KeyMapping openConfigKey;

    @Override
    public void onInitializeClient() {
        KSEConfig.load();

        // Translation key of the category: key.category.kse.general
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "general"));

        openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.kse.open_config",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_B, // default key: B (can be changed in Options -> Controls)
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new KSEConfigScreen(null));
                }
            }
        });
    }
}
