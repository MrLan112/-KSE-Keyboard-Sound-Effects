package com.mrlan123.kse.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;

/** Human readable names for keyboard keys and mouse buttons. */
public final class KeyNames {
    private KeyNames() {
    }

    public static Component name(boolean mouse, int code) {
        InputConstants.Type type = mouse ? InputConstants.Type.MOUSE : InputConstants.Type.KEYBOARD;
        return type.getOrCreate(code).getDisplayName();
    }
}
