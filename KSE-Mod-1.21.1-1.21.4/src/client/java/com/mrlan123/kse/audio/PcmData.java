package com.mrlan123.kse.audio;

/**
 * A decoded sound: 16-bit signed stereo samples (interleaved L, R) at
 * {@link SoundDecoder#RATE} Hz. This is the one format the mixer understands,
 * whatever the original file was.
 */
public record PcmData(short[] samples) {

    public int frames() {
        return samples.length / 2;
    }

    /** Approximate memory used, for the cache budget. */
    public long bytes() {
        return (long) samples.length * 2L;
    }
}
