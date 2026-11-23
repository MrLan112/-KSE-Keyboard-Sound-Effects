package com.mrlan123.kse.audio;

import java.util.ArrayList;
import java.util.List;

/**
 * Audio file extensions the mod knows about. They are only used to build the
 * "open file" dialog filters: decoding looks at the file content, so a wrong
 * or missing extension does not matter.
 */
public final class SoundFormats {
    /** Decoded by the JDK or by libraries bundled inside the mod jar. */
    public static final List<String> BUNDLED = List.of(
            "wav", "wave", "aif", "aiff", "aifc", "au", "snd",
            "mp3", "mp2", "mp1", "mpga",
            "ogg", "oga", "ogx", "opus", "spx",
            "flac", "fla",
            "aac", "adts", "m4a", "m4b", "m4r", "mp4", "3gp",
            "ape", "wv", "shn");

    /** Decoded only when ffmpeg is installed on the computer (used as a last resort). */
    public static final List<String> VIA_FFMPEG = List.of(
            "wma", "asf", "ac3", "eac3", "dts", "amr", "awb", "caf", "mka",
            "webm", "mkv", "mov", "avi", "tta", "mpc", "w64", "voc", "gsm");

    private SoundFormats() {
    }

    public static List<String> all() {
        List<String> all = new ArrayList<>(BUNDLED);
        all.addAll(VIA_FFMPEG);
        return all;
    }

    /** "*.wav", "*.mp3", ... */
    public static List<String> globPatterns() {
        List<String> out = new ArrayList<>();
        for (String ext : all()) {
            out.add("*." + ext);
        }
        return out;
    }
}
