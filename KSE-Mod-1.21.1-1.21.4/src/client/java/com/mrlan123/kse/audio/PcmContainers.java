package com.mrlan123.kse.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads the headers of the simple uncompressed containers - WAV, AIFF/AIFC and
 * AU/SND - and hands back a stream of raw samples.
 *
 * <p>This replaces {@code AudioSystem.getAudioInputStream}: that asks every
 * Java Sound provider that happens to be visible (other mods may ship some),
 * and a provider that wrongly claims a file produces noise instead of an error.
 * Parsing the few headers here gives the same result on every setup.
 */
final class PcmContainers {
    private static final long UNKNOWN_SIZE = 0xFFFFFFFFL;

    private PcmContainers() {
    }

    /** @throws UnsupportedAudioFileException if this is not a WAV/AIFF/AU file this class can read */
    static AudioInputStream open(InputStream in) throws IOException, UnsupportedAudioFileException {
        byte[] head = in.readNBytes(12);
        if (head.length < 12) {
            throw new UnsupportedAudioFileException("too short");
        }
        String magic = ascii(head, 0, 4);
        switch (magic) {
            case "RIFF" -> {
                if (!ascii(head, 8, 4).equals("WAVE")) {
                    throw new UnsupportedAudioFileException("RIFF but not WAVE");
                }
                return openWav(in);
            }
            case "FORM" -> {
                String type = ascii(head, 8, 4);
                if (!type.equals("AIFF") && !type.equals("AIFC")) {
                    throw new UnsupportedAudioFileException("FORM but not AIFF");
                }
                return openAiff(in, type.equals("AIFC"));
            }
            case ".snd" -> {
                return openAu(in, head);
            }
            default -> throw new UnsupportedAudioFileException("not WAV, AIFF or AU");
        }
    }

    // ---- WAV ---------------------------------------------------------------

    private static AudioInputStream openWav(InputStream in) throws IOException, UnsupportedAudioFileException {
        AudioFormat format = null;
        while (true) {
            byte[] h = in.readNBytes(8);
            if (h.length < 8) {
                throw new UnsupportedAudioFileException("WAV without a data chunk");
            }
            String id = ascii(h, 0, 4);
            long size = u32le(h, 4);
            if (id.equals("fmt ")) {
                if (size < 16 || size > 4096) {
                    throw new UnsupportedAudioFileException("bad WAV fmt chunk");
                }
                byte[] f = readExactly(in, (int) size);
                skipPad(in, size);
                format = wavFormat(f);
            } else if (id.equals("data")) {
                if (format == null) {
                    throw new UnsupportedAudioFileException("WAV data before fmt");
                }
                // A size of 0 or 0xFFFFFFFF means "until the end" (streamed files).
                long frames = (size == 0 || size == UNKNOWN_SIZE) ? AudioSystem.NOT_SPECIFIED : size / format.getFrameSize();
                return new AudioInputStream(in, format, frames);
            } else {
                skipChunk(in, size);
            }
        }
    }

    private static AudioFormat wavFormat(byte[] f) throws UnsupportedAudioFileException {
        int tag = u16le(f, 0);
        int channels = u16le(f, 2);
        float rate = (float) u32le(f, 4);
        int blockAlign = u16le(f, 12);
        int bits = u16le(f, 14);
        if (tag == 0xFFFE && f.length >= 26) {
            tag = u16le(f, 24); // WAVE_FORMAT_EXTENSIBLE: the real tag is in the sub-format GUID
        }
        AudioFormat.Encoding encoding;
        switch (tag) {
            case 1 -> encoding = bits == 8 ? AudioFormat.Encoding.PCM_UNSIGNED : AudioFormat.Encoding.PCM_SIGNED;
            case 3 -> encoding = AudioFormat.Encoding.PCM_FLOAT;
            case 6 -> encoding = AudioFormat.Encoding.ALAW;
            case 7 -> encoding = AudioFormat.Encoding.ULAW;
            default -> throw new UnsupportedAudioFileException("WAV codec 0x" + Integer.toHexString(tag));
        }
        if (channels < 1 || rate < 1 || bits < 1) {
            throw new UnsupportedAudioFileException("bad WAV format");
        }
        // The container size is what counts (20 valid bits are stored in 24).
        int containerBits = blockAlign > 0 && blockAlign % channels == 0 ? blockAlign / channels * 8 : (bits + 7) / 8 * 8;
        return new AudioFormat(encoding, rate, containerBits, channels, containerBits / 8 * channels, rate, false);
    }

    // ---- AIFF / AIFC ---------------------------------------------------------

    private static AudioInputStream openAiff(InputStream in, boolean aifc) throws IOException, UnsupportedAudioFileException {
        AudioFormat format = null;
        long numFrames = 0;
        while (true) {
            byte[] h = in.readNBytes(8);
            if (h.length < 8) {
                throw new UnsupportedAudioFileException("AIFF without a sound chunk");
            }
            String id = ascii(h, 0, 4);
            long size = u32be(h, 4);
            if (id.equals("COMM")) {
                if (size < 18 || size > 4096) {
                    throw new UnsupportedAudioFileException("bad AIFF COMM chunk");
                }
                byte[] c = readExactly(in, (int) size);
                skipPad(in, size);
                int channels = u16be(c, 0);
                numFrames = u32be(c, 2);
                int bits = (u16be(c, 6) + 7) / 8 * 8;
                float rate = (float) extended80(c, 8);
                String compression = aifc && size >= 22 ? ascii(c, 18, 4) : "NONE";
                format = aiffFormat(compression, channels, bits, rate);
            } else if (id.equals("SSND")) {
                if (format == null) {
                    throw new UnsupportedAudioFileException("AIFF sound data before COMM");
                }
                byte[] ssnd = readExactly(in, 8);
                skipFully(in, u32be(ssnd, 0)); // offset to the first sample
                return new AudioInputStream(in, format, numFrames);
            } else {
                skipChunk(in, size);
            }
        }
    }

    private static AudioFormat aiffFormat(String compression, int channels, int bits, float rate) throws UnsupportedAudioFileException {
        if (channels < 1 || bits < 8 || rate < 1) {
            throw new UnsupportedAudioFileException("bad AIFF format");
        }
        AudioFormat.Encoding encoding;
        boolean bigEndian = true;
        switch (compression) {
            case "NONE", "twos", "in24", "in32" -> encoding = AudioFormat.Encoding.PCM_SIGNED;
            case "sowt" -> {
                encoding = AudioFormat.Encoding.PCM_SIGNED;
                bigEndian = false;
            }
            case "fl32", "FL32", "fl64", "FL64" -> encoding = AudioFormat.Encoding.PCM_FLOAT;
            case "ulaw", "ULAW" -> {
                encoding = AudioFormat.Encoding.ULAW;
                bits = 8;
            }
            case "alaw", "ALAW" -> {
                encoding = AudioFormat.Encoding.ALAW;
                bits = 8;
            }
            default -> throw new UnsupportedAudioFileException("AIFF-C codec " + compression);
        }
        if (encoding.equals(AudioFormat.Encoding.PCM_FLOAT)) {
            bits = compression.endsWith("64") ? 64 : 32;
        }
        return new AudioFormat(encoding, rate, bits, channels, bits / 8 * channels, rate, bigEndian);
    }

    // ---- AU / SND ------------------------------------------------------------

    private static AudioInputStream openAu(InputStream in, byte[] head) throws IOException, UnsupportedAudioFileException {
        // head holds: magic(4) dataOffset(4) dataSize(4); the rest of the header follows.
        long dataOffset = u32be(head, 4);
        long dataSize = u32be(head, 8);
        byte[] rest = readExactly(in, 12);
        long encodingId = u32be(rest, 0);
        float rate = (float) u32be(rest, 4);
        int channels = (int) u32be(rest, 8);
        if (dataOffset < 24 || channels < 1 || rate < 1) {
            throw new UnsupportedAudioFileException("bad AU header");
        }
        skipFully(in, dataOffset - 24);

        AudioFormat.Encoding encoding;
        int bits;
        switch ((int) encodingId) {
            case 1 -> {
                encoding = AudioFormat.Encoding.ULAW;
                bits = 8;
            }
            case 2 -> {
                encoding = AudioFormat.Encoding.PCM_SIGNED;
                bits = 8;
            }
            case 3 -> {
                encoding = AudioFormat.Encoding.PCM_SIGNED;
                bits = 16;
            }
            case 4 -> {
                encoding = AudioFormat.Encoding.PCM_SIGNED;
                bits = 24;
            }
            case 5 -> {
                encoding = AudioFormat.Encoding.PCM_SIGNED;
                bits = 32;
            }
            case 6 -> {
                encoding = AudioFormat.Encoding.PCM_FLOAT;
                bits = 32;
            }
            case 7 -> {
                encoding = AudioFormat.Encoding.PCM_FLOAT;
                bits = 64;
            }
            case 27 -> {
                encoding = AudioFormat.Encoding.ALAW;
                bits = 8;
            }
            default -> throw new UnsupportedAudioFileException("AU encoding " + encodingId);
        }
        int frameSize = bits / 8 * channels;
        AudioFormat format = new AudioFormat(encoding, rate, bits, channels, frameSize, rate, true);
        long frames = dataSize == UNKNOWN_SIZE ? AudioSystem.NOT_SPECIFIED : dataSize / frameSize;
        return new AudioInputStream(in, format, frames);
    }

    // ---- small helpers -------------------------------------------------------

    private static String ascii(byte[] b, int off, int len) {
        return new String(b, off, len, StandardCharsets.ISO_8859_1);
    }

    private static int u16le(byte[] b, int o) {
        return (b[o] & 0xFF) | (b[o + 1] & 0xFF) << 8;
    }

    private static long u32le(byte[] b, int o) {
        return (b[o] & 0xFFL) | (b[o + 1] & 0xFFL) << 8 | (b[o + 2] & 0xFFL) << 16 | (b[o + 3] & 0xFFL) << 24;
    }

    private static int u16be(byte[] b, int o) {
        return (b[o] & 0xFF) << 8 | (b[o + 1] & 0xFF);
    }

    private static long u32be(byte[] b, int o) {
        return (b[o] & 0xFFL) << 24 | (b[o + 1] & 0xFFL) << 16 | (b[o + 2] & 0xFFL) << 8 | (b[o + 3] & 0xFFL);
    }

    /** AIFF stores the sample rate as an 80-bit IEEE extended float. */
    private static double extended80(byte[] b, int o) {
        int exponent = ((b[o] & 0x7F) << 8) | (b[o + 1] & 0xFF);
        long mantissa = 0;
        for (int i = 0; i < 8; i++) {
            mantissa = (mantissa << 8) | (b[o + 2 + i] & 0xFFL);
        }
        if (exponent == 0 && mantissa == 0) {
            return 0;
        }
        double value = (mantissa >>> 11) * Math.pow(2, exponent - 16383 - 52);
        return (b[o] & 0x80) != 0 ? -value : value;
    }

    private static byte[] readExactly(InputStream in, int n) throws IOException {
        byte[] data = in.readNBytes(n);
        if (data.length < n) {
            throw new EOFException();
        }
        return data;
    }

    private static void skipChunk(InputStream in, long size) throws IOException {
        skipFully(in, size + (size & 1));
    }

    private static void skipPad(InputStream in, long size) throws IOException {
        if ((size & 1) == 1) {
            skipFully(in, 1);
        }
    }

    private static void skipFully(InputStream in, long n) throws IOException {
        while (n > 0) {
            long skipped = in.skip(n);
            if (skipped <= 0) {
                if (in.read() < 0) {
                    throw new EOFException();
                }
                skipped = 1;
            }
            n -= skipped;
        }
    }
}
