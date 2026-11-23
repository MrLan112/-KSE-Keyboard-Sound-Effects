package com.mrlan123.kse;

import java.util.Locale;

/**
 * Detects Android launchers (PojavLauncher, Zalith, FCL, MobileGlues based ones...).
 * They run a normal desktop-class JRE, but there is no Java Sound mixer, no AWT
 * file dialogs and no zenity/kdialog, so the mod uses OpenAL and typed paths there.
 *
 * <p>Override with {@code -Dkse.android=true} or {@code -Dkse.android=false}.
 */
public final class Platform {
    private static final boolean ANDROID = detect();

    private Platform() { }

    public static boolean isAndroid() {
        return ANDROID;
    }

    private static boolean detect() {
        String forced = System.getProperty("kse.android");
        if (forced != null) {
            return Boolean.parseBoolean(forced.trim());
        }
        try {
            String[] vars = {"ANDROID_ROOT", "ANDROID_DATA", "ANDROID_STORAGE", "POJAV_ENVIRON",
                    "POJAV_RENDERER", "POJAV_NATIVEDIR", "POJAV_HOME", "FCL_NATIVEDIR", "ZALITH_HOME"};
            for (String v : vars) {
                String value = System.getenv(v);
                if (value != null && !value.isEmpty()) {
                    return true;
                }
            }
            for (String key : new String[]{"os.name", "java.vendor", "java.vm.vendor", "java.vm.name", "user.home"}) {
                String p = System.getProperty(key, "").toLowerCase(Locale.ROOT);
                if (p.contains("android") || p.contains("pojav")) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            // treat as desktop
        }
        return false;
    }
}
