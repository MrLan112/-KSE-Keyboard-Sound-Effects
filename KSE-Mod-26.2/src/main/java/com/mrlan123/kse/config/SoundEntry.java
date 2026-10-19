package com.mrlan123.kse.config;

/**
 * Represents a single selectable sound - either bundled with the mod (builtin)
 * or imported by the user (custom).
 */
public class SoundEntry {
    public String id;
    public String displayNameKey; // translation key, used for builtin sounds
    public String customName;     // raw display name, used for custom sounds
    public boolean builtin;
    public String resourcePath;   // classpath resource path, used for builtin sounds
    public String filePath;       // absolute file path on disk, used for custom sounds

    public SoundEntry() {
    }

    public static SoundEntry builtin(String id, String displayNameKey, String resourcePath) {
        SoundEntry entry = new SoundEntry();
        entry.id = id;
        entry.displayNameKey = displayNameKey;
        entry.builtin = true;
        entry.resourcePath = resourcePath;
        return entry;
    }

    public static SoundEntry custom(String id, String name, String filePath) {
        SoundEntry entry = new SoundEntry();
        entry.id = id;
        entry.customName = name;
        entry.builtin = false;
        entry.filePath = filePath;
        return entry;
    }
}
