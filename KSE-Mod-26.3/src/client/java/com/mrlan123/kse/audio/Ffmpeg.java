package com.mrlan123.kse.audio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Last-resort decoder: if ffmpeg is installed, it can read practically any
 * audio (and the audio track of any video) file. Nothing is required of the
 * player - without ffmpeg this is simply skipped.
 *
 * ffmpeg is looked up from the KSE_FFMPEG environment variable, then the PATH.
 */
final class Ffmpeg {
    private static final long TIMEOUT_SECONDS = 20;

    private static volatile String executable;
    private static volatile boolean searched;

    private Ffmpeg() {
    }

    static PcmData decode(SoundDecoder.Source source, List<String> problems) throws IOException {
        Path file = source.path();
        if (file == null) {
            return null;
        }
        String exe = locate();
        if (exe == null) {
            problems.add("ffmpeg is not installed");
            return null;
        }

        Process process = new ProcessBuilder(exe,
                "-v", "error", "-nostdin",
                "-i", file.toString(),
                "-vn", "-t", String.valueOf(SoundDecoder.MAX_SECONDS),
                "-f", "s16le", "-ac", "2", "-ar", String.valueOf(SoundDecoder.RATE),
                "pipe:1")
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectInput(ProcessBuilder.Redirect.from(nullDevice()))
                .start();

        byte[] raw;
        try {
            // Reading is done on another thread so a stuck ffmpeg can be killed.
            CompletableFuture<byte[]> reader = CompletableFuture.supplyAsync(() -> {
                try (InputStream out = process.getInputStream()) {
                    return out.readAllBytes();
                } catch (IOException e) {
                    return new byte[0];
                }
            });
            raw = reader.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            process.destroyForcibly();
            throw new IOException("ffmpeg took too long");
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        } catch (java.util.concurrent.ExecutionException e) {
            process.destroyForcibly();
            throw new IOException("ffmpeg failed: " + e.getCause());
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }

        int frames = Math.min(raw.length / 4, SoundDecoder.MAX_FRAMES);
        if (frames == 0) {
            problems.add("ffmpeg could not decode the file");
            return null;
        }
        short[] samples = new short[frames * 2];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) ((raw[2 * i] & 0xFF) | (raw[2 * i + 1] << 8));
        }
        return new PcmData(samples);
    }

    private static java.io.File nullDevice() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return new java.io.File(windows ? "NUL" : "/dev/null");
    }

    private static String locate() {
        if (!searched) {
            synchronized (Ffmpeg.class) {
                if (!searched) {
                    String fromEnv = System.getenv("KSE_FFMPEG");
                    String found = null;
                    for (String candidate : new String[]{fromEnv, "ffmpeg"}) {
                        if (candidate != null && !candidate.isBlank() && works(candidate)) {
                            found = candidate;
                            break;
                        }
                    }
                    executable = found;
                    searched = true;
                }
            }
        }
        return executable;
    }

    private static boolean works(String exe) {
        try {
            Process p = new ProcessBuilder(exe, "-version")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectInput(ProcessBuilder.Redirect.from(nullDevice()))
                    .start();
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
