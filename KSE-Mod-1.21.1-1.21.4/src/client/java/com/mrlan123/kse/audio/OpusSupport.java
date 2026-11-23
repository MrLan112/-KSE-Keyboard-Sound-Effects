package com.mrlan123.kse.audio;

import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusException;
import org.gagravarr.ogg.OggFile;
import org.gagravarr.opus.OpusAudioData;
import org.gagravarr.opus.OpusFile;
import org.gagravarr.opus.OpusInfo;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Ogg Opus (.opus, and Opus inside .ogg). Java Sound has no Opus support, so
 * the Ogg container is read with vorbis-java and the packets are decoded with
 * Concentus (a pure Java port of libopus). Kept in its own class so that a
 * missing library only disables Opus.
 */
final class OpusSupport {
    /** Opus always decodes at 48 kHz; this is the longest packet it can hold, per channel. */
    private static final int OPUS_RATE = 48_000;
    private static final int MAX_PACKET_FRAMES = 5_760;

    private OpusSupport() {
    }

    static PcmData decode(InputStream in) throws IOException, UnsupportedAudioFileException {
        OpusFile file;
        try {
            file = new OpusFile(new OggFile(in));
        } catch (IllegalArgumentException | IOException notOpus) {
            throw new UnsupportedAudioFileException("not an Ogg Opus stream");
        }
        OpusInfo info = file.getInfo();
        if (info == null) {
            throw new UnsupportedAudioFileException("not an Ogg Opus stream");
        }
        int channels = info.getNumChannels();
        if (channels < 1 || channels > 2) {
            throw new IOException("Opus with " + channels + " channels is not supported");
        }

        OpusDecoder decoder;
        try {
            decoder = new OpusDecoder(OPUS_RATE, channels);
        } catch (OpusException e) {
            throw new IOException("Cannot start the Opus decoder: " + e.getMessage(), e);
        }

        int maxFrames = OPUS_RATE * SoundDecoder.MAX_SECONDS;
        float[] left = new float[maxFrames];
        float[] right = new float[maxFrames];
        int frames = 0;
        int skip = Math.max(0, info.getPreSkip()); // encoder delay, not part of the sound
        short[] pcm = new short[MAX_PACKET_FRAMES * channels];

        OpusAudioData packet;
        while (frames < maxFrames && (packet = file.getNextAudioPacket()) != null) {
            byte[] data = packet.getData();
            if (data == null || data.length == 0) {
                continue;
            }
            int decoded;
            try {
                decoded = decoder.decode(data, 0, data.length, pcm, 0, MAX_PACKET_FRAMES, false);
            } catch (OpusException e) {
                continue; // a damaged packet is skipped, the rest still plays
            }
            for (int i = 0; i < decoded && frames < maxFrames; i++) {
                if (skip > 0) {
                    skip--;
                    continue;
                }
                float l = pcm[i * channels] / 32768f;
                float r = channels == 2 ? pcm[i * channels + 1] / 32768f : l;
                left[frames] = l;
                right[frames] = r;
                frames++;
            }
        }
        if (frames == 0) {
            throw new IOException("The Opus stream has no audio");
        }
        return SoundDecoder.finish(left, right, frames, OPUS_RATE);
    }
}
