package com.terraformersmc.modmenu.api;

/**
 * Compile-time stub of Mod Menu's API (same package, same signature). It is NOT
 * packed into the mod jar; at run time the real class from Mod Menu is used.
 */
public interface ModMenuApi {
    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> null;
    }
}
