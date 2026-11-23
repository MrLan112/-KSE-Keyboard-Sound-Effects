package com.mrlan123.kse.sound;

import com.mrlan123.kse.audio.PcmData;
import com.mrlan123.kse.audio.SoundCache;
import com.mrlan123.kse.audio.SoundDecoder;
import com.mrlan123.kse.audio.SoundMixer;
import com.mrlan123.kse.config.KSEConfig;
import com.mrlan123.kse.config.SoundEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Plays keyboard/mouse click sounds. Sounds are played directly through the
 * Java Sound API (independent of Minecraft's own sound system) so that
 * user-imported files can be played without being packed into a resource pack.
 *
 * <p>Every sound is decoded once (see {@link SoundDecoder} for the supported
 * formats), kept in memory and mixed by {@link SoundMixer}, so a key press
 * costs almost nothing.
 */
public class SoundPlayer {
    public static final List<SoundEntry> BUILTIN_SOUNDS = new ArrayList<>();
    private static final Map<String, SoundEntry> BUILTIN_BY_ID = new HashMap<>();

    private static final SoundMixer MIXER = new SoundMixer();
    private static final SoundCache CACHE = new SoundCache();

    /** Decoding can take a moment (a big FLAC, ffmpeg), so it never runs on the game thread. */
    private static final ExecutorService DECODER_THREADS = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "kse-decoder");
        t.setDaemon(true);
        return t;
    });

    static {
        // The order here is the order in the menu.
        BUILTIN_SOUNDS.add(SoundEntry.builtin("creamy_keyboard", "kse.sound.creamy_keyboard", "/assets/kse/sounds/creamy_keyboard.wav"));
        BUILTIN_SOUNDS.add(SoundEntry.builtin("rizz_sound_effect", "kse.sound.rizz_sound_effect", "/assets/kse/sounds/rizz_sound_effect.wav"));
        BUILTIN_SOUNDS.add(SoundEntry.builtin("click1", "kse.sound.click1", "/assets/kse/sounds/click1.wav"));
        BUILTIN_SOUNDS.add(SoundEntry.builtin("click2", "kse.sound.click2", "/assets/kse/sounds/click2.wav"));
        BUILTIN_SOUNDS.add(SoundEntry.builtin("mechanical", "kse.sound.mechanical", "/assets/kse/sounds/mechanical.wav"));
        BUILTIN_SOUNDS.add(SoundEntry.builtin("soft_pop", "kse.sound.soft_pop", "/assets/kse/sounds/soft_pop.wav"));
        BUILTIN_SOUNDS.add(SoundEntry.builtin("asmr_tap1", "kse.sound.asmr_tap1", "/assets/kse/sounds/asmr_tap1.wav"));
        BUILTIN_SOUNDS.add(SoundEntry.builtin("asmr_tap2", "kse.sound.asmr_tap2", "/assets/kse/sounds/asmr_tap2.wav"));
        for (SoundEntry entry : BUILTIN_SOUNDS) {
            BUILTIN_BY_ID.put(entry.id, entry);
        }
    }

    public static List<SoundEntry> getAllSounds() {
        List<SoundEntry> all = new ArrayList<>(BUILTIN_SOUNDS);
        all.addAll(KSEConfig.get().customSounds);
        return all;
    }

    /** Looked up on every key press, so this does not allocate. */
    public static SoundEntry findById(String id) {
        if (id == null) {
            return null;
        }
        SoundEntry builtin = BUILTIN_BY_ID.get(id);
        if (builtin != null) {
            return builtin;
        }
        for (SoundEntry entry : KSEConfig.get().customSounds) {
            if (id.equals(entry.id)) {
                return entry;
            }
        }
        return null;
    }

    /** Called for every keyboard key press. */
    public static void onKeyboard(int keyCode) {
        KSEConfig cfg = KSEConfig.get();
        if (!cfg.playOnKeyboard) {
            return;
        }
        if (cfg.playOnlyOnSpecificKeys && !cfg.hasKey(false, keyCode)) {
            return;
        }
        playSelected();
    }

    /** Called for every mouse button press. */
    public static void onMouse(int button) {
        KSEConfig cfg = KSEConfig.get();
        if (!cfg.playOnMouse) {
            return;
        }
        if (cfg.playOnlyOnSpecificKeys && !cfg.hasKey(true, button)) {
            return;
        }
        playSelected();
    }

    public static void playSelected() {
        KSEConfig cfg = KSEConfig.get();
        // No sound is selected by default: the player has to choose one in the menu.
        if (cfg.selectedSoundId == null || cfg.selectedSoundId.isEmpty()) {
            return;
        }
        SoundEntry entry = findById(cfg.selectedSoundId);
        if (entry != null) {
            play(entry, cfg.volume);
        }
    }

    public static void play(SoundEntry entry, float volume) {
        String key = cacheKey(entry);
        PcmData ready = CACHE.get(key);
        if (ready != null) {
            MIXER.play(ready, volume);
            return;
        }
        if (CACHE.hasFailed(key) || !CACHE.beginLoading(key)) {
            return;
        }
        DECODER_THREADS.execute(() -> {
            try {
                PcmData data = load(entry, key);
                if (data != null) {
                    MIXER.play(data, volume);
                }
            } finally {
                CACHE.endLoading(key);
            }
        });
    }

    /**
     * Decodes the selected sound in the background so that the first key press
     * is as fast as all the others. Call after the game starts and whenever
     * another sound gets selected.
     */
    public static void warmUp() {
        KSEConfig cfg = KSEConfig.get();
        SoundEntry entry = findById(cfg.selectedSoundId);
        if (entry == null) {
            return;
        }
        String key = cacheKey(entry);
        if (CACHE.get(key) != null || CACHE.hasFailed(key) || !CACHE.beginLoading(key)) {
            return;
        }
        DECODER_THREADS.execute(() -> {
            try {
                load(entry, key);
            } finally {
                CACHE.endLoading(key);
            }
        });
    }

    private static String cacheKey(SoundEntry entry) {
        return entry.id + "|" + (entry.builtin ? entry.resourcePath : entry.filePath);
    }

    private static PcmData load(SoundEntry entry, String key) {
        try {
            SoundDecoder.Source source;
            if (entry.builtin) {
                source = SoundDecoder.Source.ofResource(entry.resourcePath);
            } else {
                Path path = Path.of(entry.filePath);
                if (!Files.isRegularFile(path)) {
                    throw new java.io.IOException("File not found: " + path);
                }
                source = SoundDecoder.Source.ofFile(path);
            }
            PcmData data = SoundDecoder.decode(source);
            CACHE.put(key, data);
            return data;
        } catch (Exception | LinkageError e) {
            CACHE.markFailed(key);
            // Never interrupt the game, but do not hide the reason either.
            System.err.println("[KSE] Cannot play sound '" + entry.id + "': " + e.getMessage());
            return null;
        }
    }
}
