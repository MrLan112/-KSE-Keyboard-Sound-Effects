package com.terraformersmc.modmenu.api;

import net.minecraft.client.gui.screens.Screen;

/**
 * Compile-time stub of Mod Menu's API (same package, same signature). It is NOT
 * packed into the mod jar; at run time the real class from Mod Menu is used.
 */
@FunctionalInterface
public interface ConfigScreenFactory<S extends Screen> {
    S create(Screen parent);
}
