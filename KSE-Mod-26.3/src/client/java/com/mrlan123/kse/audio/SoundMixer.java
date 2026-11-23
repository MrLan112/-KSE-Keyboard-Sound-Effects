package com.mrlan123.kse.audio;

import com.mrlan123.kse.Platform;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Plays decoded sounds through ONE audio line that stays open while sounds are
 * playing, instead of opening a new line for every key press. Any number of
 * sounds can overlap: they are mixed together here.
 *
 * <p>The audio thread starts with the first sound and sleeps when nothing is
 * playing; the line is released after a few seconds of silence. If the line
 * cannot be opened (no sound device, exotic driver) it falls back to one
 * {@link Clip} per sound for a while before trying the line again.
 */
public final class SoundMixer {
    /** Created on first use so that classes are not touched where Java Sound is missing (Android). */
    private static final class Fmt {
        static final AudioFormat F = new AudioFormat(SoundDecoder.RATE, 16, 2, true, false);
    }

    /** Mixed and written in pieces this long (~5.8 ms). */
    private static final int CHUNK_FRAMES = 256;
    /** Size of the line's own buffer: this is the delay between a key press and its sound (~35 ms). */
    private static final int BUFFER_FRAMES = 1_536;
    /** Silence written after the last sound so that its tail is not cut off. */
    private static final int TAIL_CHUNKS = BUFFER_FRAMES / CHUNK_FRAMES + 1;
    private static final int MAX_VOICES = 32;
    private static final long IDLE_CLOSE_MS = 8_000;
    private static final long RETRY_LINE_MS = 5_000;

    private static final class Voice {
        final short[] data;
        final float gain;
        int pos;

        Voice(short[] data, float gain) {
            this.data = data;
            this.gain = gain;
        }
    }

    private final Object lock = new Object();
    private final List<Voice> voices = new ArrayList<>();
    private Thread thread;

    private volatile long lineFailedAt = 0;
    private volatile boolean lineFailed = false;
    /** Set when Java Sound cannot be used at all (missing classes or native library). */
    private volatile boolean openAlOnly = false;

    private final ExecutorService clipThreads = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "kse-clip");
        t.setDaemon(true);
        return t;
    });

    /**
     * Starts a sound. Cheap enough to call from the game thread: it only queues the sound.
     *
     * @param volume 0..1, a plain amplitude factor (0.5 is -6 dB)
     */
    public void play(PcmData sound, float volume) {
        float gain = Math.max(0f, Math.min(1f, volume));
        if (sound == null || sound.frames() == 0 || gain < 0.0001f) {
            return;
        }
        // Android launchers have no Java Sound: play through the game's OpenAL instead.
        if ((Platform.isAndroid() || openAlOnly) && OpenAlOutput.play(sound, gain)) {
            return;
        }
        if (lineFailed && System.currentTimeMillis() - lineFailedAt < RETRY_LINE_MS) {
            clipThreads.execute(() -> playOnClip(sound, gain));
            return;
        }
        synchronized (lock) {
            if (voices.size() >= MAX_VOICES) {
                voices.remove(0); // drop the oldest rather than pile up
            }
            voices.add(new Voice(sound.samples(), gain));
            if (thread == null || !thread.isAlive()) {
                thread = new Thread(this::run, "kse-audio-mixer");
                thread.setDaemon(true);
                thread.setPriority(Thread.MAX_PRIORITY);
                thread.start();
            }
            lock.notifyAll();
        }
    }

    private void run() {
        SourceDataLine line = null;
        boolean running = false;
        int tail = 0;
        long idleSince = 0;

        Voice[] active = new Voice[MAX_VOICES];
        float[] mix = new float[CHUNK_FRAMES * 2];
        byte[] out = new byte[CHUNK_FRAMES * 4];

        try {
            while (true) {
                int count;
                synchronized (lock) {
                    voices.removeIf(v -> v.pos >= v.data.length);
                    count = voices.size();
                    for (int i = 0; i < count; i++) {
                        active[i] = voices.get(i);
                    }
                }

                if (count == 0) {
                    if (tail > 0 && line != null) {
                        tail--;
                        java.util.Arrays.fill(out, (byte) 0);
                        line.write(out, 0, out.length);
                        continue;
                    }
                    if (running) {
                        line.stop();
                        line.flush();
                        running = false;
                        idleSince = System.currentTimeMillis();
                    }
                    synchronized (lock) {
                        if (voices.isEmpty()) {
                            if (line == null) {
                                lock.wait();
                            } else {
                                long left = IDLE_CLOSE_MS - (System.currentTimeMillis() - idleSince);
                                if (left > 0) {
                                    lock.wait(left);
                                } else {
                                    line.close();
                                    line = null;
                                }
                            }
                        }
                    }
                    continue;
                }

                if (line == null) {
                    line = openLine();
                    if (line == null) {
                        // No usable line: hand the waiting sounds to Clips and let the line rest.
                        lineFailed = true;
                        if (NO_JAVA_SOUND) {
                            openAlOnly = true;
                        }
                        lineFailedAt = System.currentTimeMillis();
                        List<Voice> pending;
                        synchronized (lock) {
                            pending = new ArrayList<>(voices);
                            voices.clear();
                        }
                        for (Voice v : pending) {
                            if (v.pos == 0) {
                                clipThreads.execute(() -> playOnClip(new PcmData(v.data), v.gain));
                            }
                        }
                        continue;
                    }
                    lineFailed = false;
                }
                if (!running) {
                    line.start();
                    running = true;
                }
                tail = TAIL_CHUNKS;

                java.util.Arrays.fill(mix, 0f);
                for (int i = 0; i < count; i++) {
                    Voice v = active[i];
                    int n = Math.min(mix.length, v.data.length - v.pos);
                    short[] d = v.data;
                    int p = v.pos;
                    float g = v.gain;
                    for (int s = 0; s < n; s++) {
                        mix[s] += d[p + s] * g;
                    }
                    v.pos = p + n;
                    active[i] = null;
                }
                for (int s = 0; s < mix.length; s++) {
                    float x = mix[s];
                    int v = x > 32767f ? 32767 : (x < -32768f ? -32768 : (int) x);
                    out[2 * s] = (byte) v;
                    out[2 * s + 1] = (byte) (v >> 8);
                }
                line.write(out, 0, out.length);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            System.err.println("[KSE] Audio mixer stopped: " + e);
        } finally {
            if (line != null) {
                try {
                    line.close();
                } catch (RuntimeException ignored) {
                    // nothing to do
                }
            }
            synchronized (lock) {
                voices.clear();
                thread = null;
            }
        }
    }

    private static volatile boolean NO_JAVA_SOUND = false;

    private static SourceDataLine openLine() {
        try {
            SourceDataLine line = AudioSystem.getSourceDataLine(Fmt.F);
            line.open(Fmt.F, BUFFER_FRAMES * Fmt.F.getFrameSize());
            return line;
        } catch (Exception | LinkageError e) {
            if (e instanceof LinkageError) {
                NO_JAVA_SOUND = true;
            }
            System.err.println("[KSE] Cannot open an audio line, using the slower fallback: " + e);
            return null;
        }
    }

    private static void playOnClip(PcmData sound, float gain) {
        try {
            short[] s = sound.samples();
            byte[] bytes = new byte[s.length * 2];
            for (int i = 0; i < s.length; i++) {
                bytes[2 * i] = (byte) s[i];
                bytes[2 * i + 1] = (byte) (s[i] >> 8);
            }
            Clip clip = AudioSystem.getClip();
            clip.open(Fmt.F, bytes, 0, bytes.length);
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float dB = (float) (Math.log10(Math.max(gain, 0.0001f)) * 20.0);
                control.setValue(Math.max(control.getMinimum(), Math.min(control.getMaximum(), dB)));
            }
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            });
            clip.start();
        } catch (Exception | LinkageError ignored) {
            // Playback problems must never reach the game.
        }
    }
}
