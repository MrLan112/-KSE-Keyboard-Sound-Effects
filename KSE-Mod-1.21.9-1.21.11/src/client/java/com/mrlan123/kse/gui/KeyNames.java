package com.mrlan123.kse.gui;

import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

/** Human readable names for keyboard keys and mouse buttons. */
public final class KeyNames {
    private KeyNames() {
    }

    public static Text name(boolean mouse, int code) {
        InputUtil.Type type = mouse ? InputUtil.Type.MOUSE : InputUtil.Type.KEYSYM;
        return type.createFromCode(code).getLocalizedText();
    }
}
