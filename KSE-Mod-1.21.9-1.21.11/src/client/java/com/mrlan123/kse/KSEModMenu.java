package com.mrlan123.kse;

import com.mrlan123.kse.gui.KSEConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Adds a "configure" button for the mod in Mod Menu (Mod Menu itself is optional). */
public class KSEModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return KSEConfigScreen::new;
    }
}
