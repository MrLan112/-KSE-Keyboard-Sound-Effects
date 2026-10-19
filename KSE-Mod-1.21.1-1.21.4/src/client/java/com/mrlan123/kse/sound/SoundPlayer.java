package com.mrlan123.kse.sound;

import com.mrlan123.kse.config.KSEConfig;
import com.mrlan123.kse.config.SoundEntry;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Plays keyboard/mouse click sounds. Sounds are played directly through the
 * Java Sound API (independent of Minecraft's own sound system) so that
 * user-imported files can be played without being packed into a resource pack.
 *
 * Supported file formats: WAV, AIFF, AU (native Java Sound), plus MP3 and OGG
 * Vorbis via bundled SPI providers (mp3spi, vorbis-java).
 */
public class SoundPlayer {
    public static final List<SoundEntry> BUILTIN_SOUNDS = new ArrayList<>();

    /** Decoding and opening a line can take a few milliseconds, so never do it on the game thread. */
    private static final ExecutorService AUDIO_THREADS = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "kse-audio");
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
    }

    public static List<SoundEntry> getAllSounds() {
        List<SoundEntry> all = new ArrayList<>(BUILTIN_SOUNDS);
        all.addAll(KSEConfig.get().customSounds);
        return all;
    }

    public static SoundEntry findById(String id) {
        for (SoundEntry entry : getAllSounds()) {
            if (entry.id.equals(id)) {
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
        AUDIO_THREADS.execute(() -> playNow(entry, volume));
    }

    private static void playNow(SoundEntry entry, float volume) {
        try {
            InputStream rawStream;
            if (entry.builtin) {
                rawStream = SoundPlayer.class.getResourceAsStream(entry.resourcePath);
                if (rawStream == null) {
                    return;
                }
            } else {
                Path path = Path.of(entry.filePath);
                if (!Files.exists(path)) {
                    return;
                }
                rawStream = Files.newInputStream(path);
            }

            AudioInputStream rawAudioStream = AudioSystem.getAudioInputStream(new BufferedInputStream(rawStream));
            AudioInputStream audioStream = toPlayablePcm(rawAudioStream);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            audioStream.close();
            applyVolume(clip, volume);
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            });
            clip.start();
        } catch (Exception ignored) {
            // Playback errors should never interrupt gameplay.
        }
    }

    /**
     * Clip only accepts uncompressed PCM audio. WAV/AIFF/AU are already PCM, but
     * compressed formats decoded via SPI providers (MP3 via mp3spi, OGG Vorbis via
     * vorbis-java) need one extra conversion step to a playable PCM_SIGNED stream.
     */
    private static AudioInputStream toPlayablePcm(AudioInputStream sourceStream) {
        AudioFormat sourceFormat = sourceStream.getFormat();
        if (sourceFormat.getEncoding() == AudioFormat.Encoding.PCM_SIGNED) {
            return sourceStream;
        }
        float rate = sourceFormat.getSampleRate() > 0 ? sourceFormat.getSampleRate() : 44100;
        int channels = sourceFormat.getChannels() > 0 ? sourceFormat.getChannels() : 2;
        AudioFormat pcmFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED, rate, 16, channels, channels * 2, rate, false);
        if (AudioSystem.isConversionSupported(pcmFormat, sourceFormat)) {
            return AudioSystem.getAudioInputStream(pcmFormat, sourceStream);
        }
        return sourceStream;
    }

    private static void applyVolume(Clip clip, float volume) {
        try {
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float clamped = Math.max(0.0001f, Math.min(1f, volume));
                float dB = (float) (Math.log10(clamped) * 20.0);
                control.setValue(Math.max(control.getMinimum(), Math.min(control.getMaximum(), dB)));
            }
        } catch (Exception ignored) {
            // Volume control is optional, ignore if unavailable.
        }
    }
}
