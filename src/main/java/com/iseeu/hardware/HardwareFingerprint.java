package com.iseeu.hardware;

import com.iseeu.config.IseeUConfig;
import com.iseeu.util.HashUtils;

/**
 * Builds a stable hardware fingerprint (HWID) from raw identifiers + a configured salt.
 *
 * <p>Output: 64-char lowercase hex SHA-256.
 *
 * <p>Stability: CPU id and disk serial dominate; MAC is a tiebreaker. If any single source
 * returns {@code *-unknown} it still contributes to the hash, so a missing identifier does not
 * collapse the fingerprint to a constant.
 */
public final class HardwareFingerprint {

    private HardwareFingerprint() {}

    /** @return the SHA-256 hex digest of (cpu || disk || mac || salt). */
    public static String compute() {
        return compute(
                HardwareCollector.readCpuId(),
                HardwareCollector.readDiskSerial(),
                HardwareCollector.readPrimaryMac());
    }

    public static String compute(String cpuId, String diskSerial, String mac) {
        String salt = IseeUConfig.HWID_SALT.get();
        String blob = join(cpuId, diskSerial, mac, salt);
        return HashUtils.sha256Hex(blob);
    }

    /** Canonical join — order matters and must match between client and server. */
    static String join(String cpu, String disk, String mac, String salt) {
        return "cpu=" + safe(cpu) + "|disk=" + safe(disk) + "|mac=" + safe(mac) + "|salt=" + safe(salt);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
