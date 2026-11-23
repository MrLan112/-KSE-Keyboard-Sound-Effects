package com.mrlan123.kse.audio;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;

import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sound output through the game's own OpenAL context. Used on Android launchers
 * where Java Sound does not exist. Every OpenAL call runs on one thread; each
 * sound gets a short-lived source that is deleted once it finishes.
 */
final class OpenAlOutput {
    private static final int MAX_SOURCES = 24;
    private static final int MAX_FAILURES = 20;

    private static final ScheduledExecutorService EXEC = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "kse-openal");
        t.setDaemon(true);
        return t;
    });

    /** {source, buffer}; touched only on EXEC. */
    private static final List<int[]> ACTIVE = new ArrayList<>();
    private static boolean reapScheduled = false;
    private static int failures = 0;
    private static volatile boolean broken = false;

    private OpenAlOutput() { }

    /** @return false if OpenAL cannot be used at all (the caller then tries something else) */
    static boolean play(PcmData sound, float gain) {
        if (broken) {
            return false;
        }
        try {
            EXEC.execute(() -> start(sound.samples(), gain));
            return true;
        } catch (Throwable t) {
            broken = true;
            return false;
        }
    }

    private static void start(short[] samples, float gain) {
        try {
            if (ALC10.alcGetCurrentContext() == 0L) {
                fail("no OpenAL context yet");
                return;
            }
            AL10.alGetError();
            while (ACTIVE.size() >= MAX_SOURCES) {
                release(ACTIVE.remove(0));
            }
            int buffer = AL10.alGenBuffers();
            ShortBuffer data = BufferUtils.createShortBuffer(samples.length);
            data.put(samples).flip();
            AL10.alBufferData(buffer, AL10.AL_FORMAT_STEREO16, data, SoundDecoder.RATE);
            int source = AL10.alGenSources();
            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                AL10.alDeleteBuffers(buffer);
                AL10.alDeleteSources(source);
                fail("OpenAL error while creating a source");
                return;
            }
            AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
            AL10.alSourcef(source, AL10.AL_GAIN, gain);
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
            AL10.alSourcePlay(source);
            ACTIVE.add(new int[]{source, buffer});
            failures = 0;
            scheduleReap();
        } catch (Throwable t) {
            fail(t.toString());
        }
    }

    private static void scheduleReap() {
        if (!reapScheduled) {
            reapScheduled = true;
            EXEC.schedule(OpenAlOutput::reap, 250, TimeUnit.MILLISECONDS);
        }
    }

    private static void reap() {
        reapScheduled = false;
        try {
            ACTIVE.removeIf(pair -> {
                if (AL10.alGetSourcei(pair[0], AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) {
                    return false;
                }
                release(pair);
                return true;
            });
        } catch (Throwable t) {
            ACTIVE.clear();
        }
        if (!ACTIVE.isEmpty()) {
            scheduleReap();
        }
    }

    private static void release(int[] pair) {
        try {
            AL10.alSourceStop(pair[0]);
            AL10.alDeleteSources(pair[0]);
            AL10.alDeleteBuffers(pair[1]);
        } catch (Throwable ignored) {
            // nothing to do
        }
    }

    private static void fail(String why) {
        if (++failures == 1 || failures == MAX_FAILURES) {
            System.err.println("[KSE] OpenAL output problem: " + why);
        }
        if (failures >= MAX_FAILURES) {
            broken = true;
        }
    }
}
