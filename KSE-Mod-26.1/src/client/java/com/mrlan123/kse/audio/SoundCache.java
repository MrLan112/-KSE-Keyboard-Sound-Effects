package com.mrlan123.kse.audio;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Decoded sounds, kept in memory so a key press never has to touch the disk or
 * run a decoder. The least recently used sounds are dropped when the budget is
 * exceeded. Sounds that failed to decode are remembered too, so a broken file
 * is not retried (and reported) on every key press.
 */
public final class SoundCache {
    private static final long BUDGET_BYTES = 64L * 1024 * 1024;

    private final Map<String, PcmData> sounds = new LinkedHashMap<>(16, 0.75f, true);
    private final Set<String> failed = new HashSet<>();
    private final Set<String> loading = new HashSet<>();
    private long bytes;

    public synchronized PcmData get(String key) {
        return sounds.get(key);
    }

    public synchronized void put(String key, PcmData data) {
        PcmData old = sounds.put(key, data);
        if (old != null) {
            bytes -= old.bytes();
        }
        bytes += data.bytes();
        var it = sounds.entrySet().iterator();
        while (bytes > BUDGET_BYTES && sounds.size() > 1 && it.hasNext()) {
            Map.Entry<String, PcmData> eldest = it.next();
            if (eldest.getKey().equals(key)) {
                continue;
            }
            bytes -= eldest.getValue().bytes();
            it.remove();
        }
    }

    public synchronized boolean hasFailed(String key) {
        return failed.contains(key);
    }

    public synchronized void markFailed(String key) {
        failed.add(key);
    }

    /** @return true if the caller should start loading, false if somebody already is */
    public synchronized boolean beginLoading(String key) {
        return loading.add(key);
    }

    public synchronized void endLoading(String key) {
        loading.remove(key);
    }
}
