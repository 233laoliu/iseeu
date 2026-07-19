package com.iseeu.server;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-player verification state, kept on the server.
 *
 * <p>Lifecycle:
 * <pre>
 *   issueChallenge()  → PENDING  (player just logged in, challenge sent)
 *   handleVerification() → VERIFIED | FAILED  (after the signed reply arrives)
 *   onDisconnect()    → removed  (so rejoin starts clean)
 * </pre>
 *
 * <p>While PENDING, {@link ServerVerificationManager} arms a timeout task that will kick the
 * player if they don't complete the handshake within the configured window.
 */
public final class VerificationState {

    public enum Status { PENDING, VERIFIED, FAILED }

    public static final class Entry {
        public final UUID playerId;
        public final String challengeId;
        public final long issuedAtMs;
        public volatile Status status;
        /** Set on successful verification — used by {@code /iseeu ban <player>}. */
        public volatile String hwid;

        Entry(UUID playerId, String challengeId, long issuedAtMs) {
            this.playerId = playerId;
            this.challengeId = challengeId;
            this.issuedAtMs = issuedAtMs;
            this.status = Status.PENDING;
            this.hwid = null;
        }
    }

    private static final ConcurrentMap<UUID, Entry> BY_PLAYER = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Entry> BY_CHALLENGE = new ConcurrentHashMap<>();

    private VerificationState() {}

    public static Entry issueChallenge(UUID playerUuid, String challengeId) {
        Entry e = new Entry(playerUuid, challengeId, System.currentTimeMillis());
        BY_PLAYER.put(playerUuid, e);
        BY_CHALLENGE.put(challengeId, e);
        return e;
    }

    public static Entry get(UUID playerUuid) {
        return BY_PLAYER.get(playerUuid);
    }

    /** Look up by challenge id — used by the configuration-phase handler. */
    public static Entry getByChallenge(String challengeId) {
        return BY_CHALLENGE.get(challengeId);
    }

    public static boolean isVerified(UUID playerUuid) {
        Entry e = BY_PLAYER.get(playerUuid);
        return e != null && e.status == Status.VERIFIED;
    }

    public static boolean isPending(UUID playerUuid) {
        Entry e = BY_PLAYER.get(playerUuid);
        return e != null && e.status == Status.PENDING;
    }

    public static void setStatus(UUID playerUuid, Status status) {
        Entry e = BY_PLAYER.get(playerUuid);
        if (e != null) e.status = status;
    }

    /** Record the verified HWID so admins can ban the online player by name. */
    public static void setHwid(UUID playerUuid, String hwid) {
        Entry e = BY_PLAYER.get(playerUuid);
        if (e != null) e.hwid = hwid;
    }

    public static String getHwid(UUID playerUuid) {
        Entry e = BY_PLAYER.get(playerUuid);
        return e != null ? e.hwid : null;
    }

    public static void remove(UUID playerUuid) {
        Entry e = BY_PLAYER.remove(playerUuid);
        if (e != null) BY_CHALLENGE.remove(e.challengeId);
    }

    public static int pendingCount() {
        return BY_PLAYER.size();
    }
}
