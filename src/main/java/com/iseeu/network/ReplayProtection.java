package com.iseeu.network;

import com.iseeu.IseeUMod;
import com.iseeu.config.IseeUConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Replay protection — tracks recently-seen nonces per player UUID.
 *
 * <p>Two layers of defence:
 * <ol>
 *   <li><b>Timestamp skew</b> — replies older than {@code tolerance} seconds are dropped.</li>
 *   <li><b>Nonce cache</b> — the last N nonces per player are remembered; a duplicate is dropped.</li>
 * </ol>
 *
 * <p>The cache is a bounded LRU per player. If it overflows, the oldest nonce is evicted; this is
 * acceptable because the timestamp check already bounds how far back an attacker can reach.
 */
public final class ReplayProtection {

    /** player UUID hex → LRU nonce set. */
    private static final Map<String, LinkedHashMap<String, Long>> BY_PLAYER = new LinkedHashMap<>();

    private ReplayProtection() {}

    public static synchronized void reset(String playerUuid) {
        BY_PLAYER.remove(playerUuid);
    }

    /**
     * @return true if the nonce is fresh (accepted), false if it was a replay (rejected).
     */
    public static synchronized boolean checkAndRecord(String playerUuid, String nonce, long clientTimeMs, long nowMs) {
        int toleranceSec = IseeUConfig.TIMESTAMP_TOLERANCE_SECONDS.get();
        long skew = Math.abs(clientTimeMs - nowMs);
        if (skew > toleranceSec * 1000L) {
            IseeUMod.LOGGER.debug("[IseeU] replay-protect: skew {}ms > tolerance for {}",
                    skew, playerUuid);
            return false;
        }

        int capacity = Math.max(16, IseeUConfig.NONCE_CACHE_SIZE.get());
        LinkedHashMap<String, Long> set = BY_PLAYER.computeIfAbsent(playerUuid, k -> new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > capacity;
            }
        });

        if (set.containsKey(nonce)) {
            IseeUMod.LOGGER.debug("[IseeU] replay-protect: duplicate nonce from {}", playerUuid);
            return false;
        }
        set.put(nonce, clientTimeMs);
        return true;
    }
}
