package com.mrlan123.kse.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class KSEConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("kse");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
    public static final Path SOUNDS_DIR = CONFIG_DIR.resolve("sounds");

    public boolean playOnKeyboard = true;
    public boolean playOnMouse = true;
    /** When true, sounds are played only for the keys / mouse buttons listed in specificKeys. */
    public boolean playOnlyOnSpecificKeys = false;
    public List<BoundKey> specificKeys = new ArrayList<>();
    public String selectedSoundId = "";
    public float volume = 1.0f;
    public List<SoundEntry> customSounds = new ArrayList<>();

    private static KSEConfig instance;

    public static KSEConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.createDirectories(SOUNDS_DIR);
        } catch (IOException ignored) {
            // Non-fatal, saving will simply fail later.
        }

        KSEConfig loaded = null;
        if (Files.exists(CONFIG_FILE)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(reader, KSEConfig.class);
            } catch (IOException | com.google.gson.JsonSyntaxException ignored) {
                loaded = null;
            }
        }

        instance = loaded != null ? loaded : new KSEConfig();
        if (instance.customSounds == null) {
            instance.customSounds = new ArrayList<>();
        }
        if (instance.specificKeys == null) {
            instance.specificKeys = new ArrayList<>();
        }
        if (instance.selectedSoundId == null) {
            instance.selectedSoundId = "";
        }
    }

    public static void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(get(), writer);
        } catch (IOException ignored) {
            // Non-fatal.
        }
    }

    /**
     * Copies the given source file into the mod's sound folder and registers it
     * as a new custom sound entry under the provided display name.
     *
     * @return the generated id of the newly added sound
     */
    public String addCustomSound(String name, Path sourceFile) throws IOException {
        String id = "custom_" + UUID.randomUUID().toString().substring(0, 8);
        String ext = getExtension(sourceFile.toString());
        Path dest = SOUNDS_DIR.resolve(id + "." + ext);
        Files.createDirectories(SOUNDS_DIR);
        Files.copy(sourceFile, dest, StandardCopyOption.REPLACE_EXISTING);

        SoundEntry entry = SoundEntry.custom(id, name, dest.toString());
        customSounds.add(entry);
        save();
        return id;
    }

    public void removeCustomSound(String id) {
        customSounds.removeIf(entry -> entry.id.equals(id));
        if (selectedSoundId != null && selectedSoundId.equals(id)) {
            selectedSoundId = "";
        }
        save();
    }

    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "wav";
    }

    public boolean hasKey(boolean mouse, int code) {
        for (BoundKey k : specificKeys) {
            if (k.mouse == mouse && k.code == code) {
                return true;
            }
        }
        return false;
    }

    /** @return false if the key / button was already in the list */
    public boolean addKey(boolean mouse, int code) {
        if (hasKey(mouse, code)) {
            return false;
        }
        specificKeys.add(new BoundKey(mouse, code));
        save();
        return true;
    }

    public void removeKey(BoundKey key) {
        specificKeys.removeIf(k -> k.mouse == key.mouse && k.code == key.code);
        save();
    }

    /** A keyboard key or mouse button chosen in the key editor. */
    public static class BoundKey {
        public boolean mouse;
        public int code;

        public BoundKey() {
        }

        public BoundKey(boolean mouse, int code) {
            this.mouse = mouse;
            this.code = code;
        }
    }
}
