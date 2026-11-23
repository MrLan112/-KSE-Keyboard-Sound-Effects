package com.mrlan123.kse.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;
import javax.sound.sampled.spi.FormatConversionProvider;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns (almost) any audio file into {@link PcmData}.
 *
 * <p>The file content is looked at, not the extension, so a wrong or missing
 * extension does not matter. Decoders are tried in this order: the one that
 * matches the file signature first, then all the others, and finally ffmpeg
 * if it happens to be installed.
 *
 * <ul>
 *   <li>JDK: WAV (PCM, float, A-law, u-law), AIFF/AIFC, AU/SND</li>
 *   <li>Bundled libraries: MP3/MP2/MP1, OGG Vorbis, Opus, Speex, FLAC,
 *       AAC/M4A/MP4, Apple Lossless, Monkey's Audio, WavPack, Shorten</li>
 *   <li>ffmpeg (optional): WMA, AC3, AMR, CAF, MKA and everything else it knows</li>
 * </ul>
 *
 * Long files are cut to {@link #MAX_SECONDS} seconds: this is meant for short
 * key sounds, and a whole song would just eat memory.
 */
public final class SoundDecoder {
    /** Sample rate of every decoded sound. */
    public static final int RATE = 44_100;
    public static final int MAX_SECONDS = 10;

    static final int MAX_FRAMES = RATE * MAX_SECONDS;

    /** Where the audio bytes come from; opened again for every decoder that is tried. */
    public interface Source {
        InputStream open() throws IOException;

        /** The file on disk, or null for classpath resources. Only the ffmpeg fallback needs it. */
        Path path();

        String name();

        static Source ofFile(Path file) {
            return new Source() {
                @Override
                public InputStream open() throws IOException {
                    return Files.newInputStream(file);
                }

                @Override
                public Path path() {
                    return file;
                }

                @Override
                public String name() {
                    return file.toString();
                }
            };
        }

        static Source ofResource(String resourcePath) {
            return new Source() {
                @Override
                public InputStream open() throws IOException {
                    InputStream in = SoundDecoder.class.getResourceAsStream(resourcePath);
                    if (in == null) {
                        throw new IOException("Missing resource " + resourcePath);
                    }
                    return in;
                }

                @Override
                public Path path() {
                    return null;
                }

                @Override
                public String name() {
                    return resourcePath;
                }
            };
        }
    }

    private interface Decoder {
        String name();

        PcmData decode(Source source) throws Exception;
    }

    /** What the first bytes of the file say it is. */
    private enum Kind {JDK, MP3, OGG, FLAC, MP4, AAC, APE, WAVPACK, SHORTEN, UNKNOWN}

    /** WAV, AIFF/AIFC and AU, read by {@link PcmContainers}. */
    private static final Decoder JDK = new Decoder() {
        @Override
        public String name() {
            return "wav/aiff/au";
        }

        @Override
        public PcmData decode(Source source) throws Exception {
            try (InputStream raw = new BufferedInputStream(source.open(), 1 << 16);
                 AudioInputStream in = PcmContainers.open(raw)) {
                return PcmReader.read(in, null);
            }
        }
    };

    private static final Decoder OPUS = new Decoder() {
        @Override
        public String name() {
            return "opus";
        }

        @Override
        public PcmData decode(Source source) throws Exception {
            try (InputStream raw = new BufferedInputStream(source.open(), 1 << 16)) {
                return OpusSupport.decode(raw);
            }
        }
    };

    private static final Decoder MP3 = new Spi("mp3",
            "javazoom.spi.mpeg.sampled.file.MpegAudioFileReader",
            "javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider");
    private static final Decoder VORBIS = new Spi("vorbis",
            "javazoom.spi.vorbis.sampled.file.VorbisAudioFileReader",
            "javazoom.spi.vorbis.sampled.convert.VorbisFormatConversionProvider");
    private static final Decoder SPEEX = new Spi("speex",
            "org.xiph.speex.spi.SpeexAudioFileReader",
            "org.xiph.speex.spi.SpeexFormatConvertionProvider");
    private static final Decoder FLAC = new Spi("flac",
            "org.kc7bfi.jflac.sound.spi.FlacAudioFileReader",
            "org.kc7bfi.jflac.sound.spi.FlacFormatConversionProvider");
    private static final Decoder AAC = new Spi("aac",
            "net.sourceforge.jaad.spi.javasound.AACAudioFileReader",
            "net.sourceforge.jaad.spi.javasound.AACFormatConversionProvider");
    private static final Decoder ALAC = new Spi("alac",
            "com.beatofthedrum.alacdecoder.spi.AlacAudioFileReader",
            "com.beatofthedrum.alacdecoder.spi.AlacFormatConversionProvider");
    private static final Decoder APE = new Spi("ape",
            "davaguine.jmac.spi.APEAudioFileReader",
            "davaguine.jmac.spi.APEFormatConversionProvider");
    private static final Decoder WAVPACK = new Spi("wavpack",
            "com.beatofthedrum.wvcodec.spi.WavpackAudioFileReader",
            "com.beatofthedrum.wvcodec.spi.WavpackFormatConversionProvider");
    private static final Decoder SHORTEN = new Spi("shorten",
            "com.beatofthedrum.shortendecoder.spi.ShortenAudioFileReader",
            "com.beatofthedrum.shortendecoder.spi.ShortenFormatConversionProvider");

    /** Every decoder, in the order they are tried when the file type is not obvious. */
    private static final List<Decoder> ALL = List.of(
            JDK, MP3, VORBIS, OPUS, FLAC, AAC, ALAC, SPEEX, WAVPACK, APE, SHORTEN);

    private SoundDecoder() {
    }

    /**
     * @throws IOException when the file is missing, unreadable or in a format nobody here can decode
     */
    public static PcmData decode(Source source) throws IOException {
        List<String> problems = new ArrayList<>();
        Kind kind = sniff(source);

        for (Decoder decoder : orderFor(kind)) {
            try {
                PcmData data = decoder.decode(source);
                if (data != null && data.frames() > 0) {
                    return data;
                }
            } catch (UnsupportedAudioFileException wrongDecoder) {
                // Not this decoder's format, try the next one.
            } catch (Exception | LinkageError e) {
                problems.add(decoder.name() + ": " + describe(e));
            }
        }

        try {
            PcmData data = Ffmpeg.decode(source, problems);
            if (data != null && data.frames() > 0) {
                return data;
            }
        } catch (Exception | LinkageError e) {
            problems.add("ffmpeg: " + describe(e));
        }

        throw new IOException("Unsupported or unreadable audio file"
                + (problems.isEmpty() ? "" : " (" + String.join("; ", problems) + ")"));
    }

    /** @return null if the file can be decoded, otherwise a short reason */
    public static String check(Path file) {
        try {
            decode(Source.ofFile(file));
            return null;
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    private static String describe(Throwable t) {
        String msg = t.getMessage();
        return t.getClass().getSimpleName() + (msg == null || msg.isBlank() ? "" : " - " + msg);
    }

    private static List<Decoder> orderFor(Kind kind) {
        List<Decoder> preferred = switch (kind) {
            case JDK -> List.of(JDK);
            case MP3 -> List.of(MP3);
            case OGG -> List.of(VORBIS, OPUS, SPEEX, FLAC);
            case FLAC -> List.of(FLAC);
            case MP4 -> List.of(AAC, ALAC);
            case AAC -> List.of(AAC);
            case APE -> List.of(APE);
            case WAVPACK -> List.of(WAVPACK);
            case SHORTEN -> List.of(SHORTEN);
            case UNKNOWN -> List.<Decoder>of();
        };
        List<Decoder> order = new ArrayList<>(preferred);
        for (Decoder d : ALL) {
            if (!order.contains(d)) {
                order.add(d);
            }
        }
        return order;
    }

    private static Kind sniff(Source source) {
        byte[] h = new byte[12];
        int n;
        try (InputStream in = source.open()) {
            n = in.readNBytes(h, 0, h.length);
        } catch (IOException e) {
            return Kind.UNKNOWN;
        }
        if (n < 4) {
            return Kind.UNKNOWN;
        }
        if (startsWith(h, "RIFF") || startsWith(h, "RF64") || startsWith(h, "FORM") || startsWith(h, ".snd")) {
            return Kind.JDK;
        }
        if (startsWith(h, "OggS")) {
            return Kind.OGG;
        }
        if (startsWith(h, "fLaC")) {
            return Kind.FLAC;
        }
        if (startsWith(h, "MAC ")) {
            return Kind.APE;
        }
        if (startsWith(h, "wvpk")) {
            return Kind.WAVPACK;
        }
        if (startsWith(h, "ajkg")) {
            return Kind.SHORTEN;
        }
        if (n >= 8 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p') {
            return Kind.MP4;
        }
        if (startsWith(h, "ID3")) {
            return Kind.MP3;
        }
        if ((h[0] & 0xFF) == 0xFF && (h[1] & 0xE0) == 0xE0) {
            // 0xFFF0 with layer bits 00 is an AAC ADTS header, anything else is MPEG audio.
            return (h[1] & 0x06) == 0 ? Kind.AAC : Kind.MP3;
        }
        return Kind.UNKNOWN;
    }

    private static boolean startsWith(byte[] data, String magic) {
        for (int i = 0; i < magic.length(); i++) {
            if (data[i] != (byte) magic.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A decoder built from a Java Sound service provider that is bundled in the
     * mod jar. The classes are created by name and used directly, so this does
     * not depend on how (or whether) the game's class loader exposes
     * META-INF/services, and a missing library only disables its own format.
     */
    private static final class Spi implements Decoder {
        private final String name;
        private final String readerClass;
        private final String converterClass;
        private AudioFileReader reader;
        private FormatConversionProvider converter;
        private boolean initialised;

        Spi(String name, String readerClass, String converterClass) {
            this.name = name;
            this.readerClass = readerClass;
            this.converterClass = converterClass;
        }

        @Override
        public String name() {
            return name;
        }

        private synchronized void init() throws Exception {
            if (initialised) {
                return;
            }
            initialised = true;
            try {
                reader = (AudioFileReader) Class.forName(readerClass).getDeclaredConstructor().newInstance();
                converter = (FormatConversionProvider) Class.forName(converterClass).getDeclaredConstructor().newInstance();
            } catch (ClassNotFoundException | LinkageError e) {
                reader = null;
                converter = null;
            }
        }

        @Override
        public PcmData decode(Source source) throws Exception {
            init();
            if (reader == null) {
                // The library is not in this build: behave as "not my format".
                throw new UnsupportedAudioFileException(name + " support is not bundled");
            }
            try (InputStream raw = new BufferedInputStream(source.open(), 1 << 19);
                 AudioInputStream in = reader.getAudioInputStream(raw)) {
                return PcmReader.read(in, converter);
            }
        }
    }

    /** Shared by every decoder: gets a Java Sound stream down to {@link PcmData}. */
    static final class PcmReader {
        private PcmReader() {
        }

        static PcmData read(AudioInputStream in, FormatConversionProvider converter) throws IOException {
            AudioFormat format = in.getFormat();
            AudioInputStream pcm = in;
            if (!isRawPcm(format)) {
                pcm = toPcm16(in, converter);
                format = pcm.getFormat();
            }
            try {
                return readRawPcm(pcm, format);
            } finally {
                if (pcm != in) {
                    pcm.close();
                }
            }
        }

        private static boolean isRawPcm(AudioFormat f) {
            AudioFormat.Encoding e = f.getEncoding();
            return e.equals(AudioFormat.Encoding.PCM_SIGNED)
                    || e.equals(AudioFormat.Encoding.PCM_UNSIGNED)
                    || e.equals(AudioFormat.Encoding.PCM_FLOAT)
                    || e.equals(AudioFormat.Encoding.ULAW)
                    || e.equals(AudioFormat.Encoding.ALAW);
        }

        private static AudioInputStream toPcm16(AudioInputStream in, FormatConversionProvider converter) throws IOException {
            AudioFormat source = in.getFormat();
            float rate = source.getSampleRate() > 0 ? source.getSampleRate() : RATE;
            int channels = source.getChannels() > 0 ? source.getChannels() : 2;
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    rate, 16, channels, channels * 2, rate, false);
            try {
                if (converter != null && converter.isConversionSupported(target, source)) {
                    return converter.getAudioInputStream(target, in);
                }
                if (converter != null && converter.isConversionSupported(AudioFormat.Encoding.PCM_SIGNED, source)) {
                    return converter.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED, in);
                }
            } catch (IllegalArgumentException e) {
                throw new IOException("Cannot convert " + source + " to PCM: " + e.getMessage(), e);
            }
            throw new IOException("No converter from " + source + " to PCM");
        }

        private static PcmData readRawPcm(AudioInputStream in, AudioFormat f) throws IOException {
            int channels = f.getChannels();
            int bits = f.getSampleSizeInBits();
            if (channels <= 0 || bits <= 0) {
                throw new IOException("Unknown sample layout: " + f);
            }
            int bytesPerSample = (bits + 7) / 8;
            int frameSize = f.getFrameSize() > 0 ? f.getFrameSize() : channels * bytesPerSample;
            float rate = f.getSampleRate() > 0 ? f.getSampleRate() : RATE;

            long maxSourceFrames = (long) Math.ceil(MAX_SECONDS * (double) rate) + 1;
            byte[] data = readFrames(in, frameSize, maxSourceFrames);
            int frames = data.length / frameSize;
            if (frames == 0) {
                return new PcmData(new short[0]);
            }

            float[] left = new float[frames];
            float[] right = new float[frames];
            boolean bigEndian = f.isBigEndian();
            AudioFormat.Encoding enc = f.getEncoding();
            for (int i = 0; i < frames; i++) {
                int base = i * frameSize;
                float l = sample(data, base, bytesPerSample, enc, bigEndian);
                float r = channels > 1 ? sample(data, base + bytesPerSample, bytesPerSample, enc, bigEndian) : l;
                left[i] = l;
                right[i] = r;
            }
            return finish(left, right, frames, rate);
        }

        /** Reads up to maxFrames whole frames; stops early at the end of the stream. */
        private static byte[] readFrames(AudioInputStream in, int frameSize, long maxFrames) throws IOException {
            long maxBytes = Math.min(maxFrames * frameSize, Integer.MAX_VALUE - 64L);
            int chunk = Math.max(frameSize, (1 << 16) / frameSize * frameSize);
            byte[] buf = new byte[(int) Math.min(maxBytes, 1 << 18)];
            int total = 0;
            int zeroReads = 0;
            byte[] tmp = new byte[chunk];
            while (total < maxBytes) {
                int want = (int) Math.min(tmp.length, maxBytes - total);
                want -= want % frameSize;
                if (want <= 0) {
                    break;
                }
                int n = in.read(tmp, 0, want);
                if (n < 0) {
                    break;
                }
                if (n == 0) {
                    if (++zeroReads > 100) {
                        break;
                    }
                    continue;
                }
                zeroReads = 0;
                if (total + n > buf.length) {
                    buf = java.util.Arrays.copyOf(buf, (int) Math.min(maxBytes, Math.max((long) buf.length * 2, total + (long) n)));
                }
                System.arraycopy(tmp, 0, buf, total, n);
                total += n;
            }
            total -= total % frameSize;
            return java.util.Arrays.copyOf(buf, total);
        }

        private static float sample(byte[] b, int off, int bytes, AudioFormat.Encoding enc, boolean bigEndian) {
            if (enc.equals(AudioFormat.Encoding.ULAW)) {
                return ulaw(b[off]) / 32768f;
            }
            if (enc.equals(AudioFormat.Encoding.ALAW)) {
                return alaw(b[off]) / 32768f;
            }
            if (enc.equals(AudioFormat.Encoding.PCM_FLOAT)) {
                if (bytes == 4) {
                    return Float.intBitsToFloat((int) assemble(b, off, 4, bigEndian));
                }
                if (bytes == 8) {
                    return (float) Double.longBitsToDouble(assemble(b, off, 8, bigEndian));
                }
                return 0f;
            }
            long v = assemble(b, off, bytes, bigEndian);
            double full = Math.pow(2, bytes * 8 - 1);
            if (enc.equals(AudioFormat.Encoding.PCM_UNSIGNED)) {
                return (float) ((v - full) / full);
            }
            int shift = 64 - bytes * 8;
            v = (v << shift) >> shift; // sign extend
            return (float) (v / full);
        }

        /** G.711 u-law byte to a 16-bit sample. */
        private static int ulaw(byte b) {
            int u = ~b & 0xFF;
            int t = (((u & 0x0F) << 3) + 0x84) << ((u & 0x70) >> 4);
            t -= 0x84;
            return (u & 0x80) != 0 ? -t : t;
        }

        /** G.711 A-law byte to a 16-bit sample. */
        private static int alaw(byte b) {
            int a = (b ^ 0x55) & 0xFF;
            int t = (a & 0x0F) << 4;
            int segment = (a & 0x70) >> 4;
            switch (segment) {
                case 0 -> t += 8;
                case 1 -> t += 0x108;
                default -> t = (t + 0x108) << (segment - 1);
            }
            return (a & 0x80) != 0 ? t : -t;
        }

        private static long assemble(byte[] b, int off, int n, boolean bigEndian) {
            long v = 0;
            if (bigEndian) {
                for (int i = 0; i < n; i++) {
                    v = (v << 8) | (b[off + i] & 0xFF);
                }
            } else {
                for (int i = n - 1; i >= 0; i--) {
                    v = (v << 8) | (b[off + i] & 0xFF);
                }
            }
            return v;
        }
    }

    /**
     * Last step for every decoder: resample to {@link #RATE}, cut to the length
     * limit and pack as interleaved 16-bit stereo.
     */
    static PcmData finish(float[] left, float[] right, int frames, float sourceRate) {
        float[] l = left;
        float[] r = right;
        int n = frames;
        if (Math.abs(sourceRate - RATE) > 0.5f) {
            double step = sourceRate / (double) RATE;
            int outFrames = (int) Math.min(MAX_FRAMES, Math.floor(frames / step));
            l = resample(left, frames, step, outFrames);
            r = resample(right, frames, step, outFrames);
            n = outFrames;
        }
        n = Math.min(n, MAX_FRAMES);
        short[] out = new short[n * 2];
        for (int i = 0; i < n; i++) {
            out[2 * i] = toShort(l[i]);
            out[2 * i + 1] = toShort(r[i]);
        }
        return new PcmData(out);
    }

    private static float[] resample(float[] in, int inFrames, double step, int outFrames) {
        float[] out = new float[outFrames];
        for (int i = 0; i < outFrames; i++) {
            double pos = i * step;
            int i0 = (int) pos;
            int i1 = Math.min(i0 + 1, inFrames - 1);
            float frac = (float) (pos - i0);
            out[i] = in[Math.min(i0, inFrames - 1)] * (1f - frac) + in[i1] * frac;
        }
        return out;
    }

    private static short toShort(float v) {
        int s = Math.round(v * 32767f);
        return (short) Math.max(-32768, Math.min(32767, s));
    }
}
