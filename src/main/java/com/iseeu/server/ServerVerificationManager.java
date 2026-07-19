package com.iseeu.server;

import com.iseeu.IseeUMod;
import com.iseeu.config.IseeUConfig;
import com.iseeu.config.IseeUConfig.EnforceMode;
import com.iseeu.network.PacketCrypto;
import com.iseeu.network.ReplayProtection;
import com.iseeu.network.payloads.VerificationPayload;
import com.iseeu.util.HashUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Server-side authority for the anti-cheat handshake.
 *
 * <p>Runs in the <strong>configuration phase</strong> — before the player enters the game world.
 * A rejected client is disconnected immediately and never sees the world.
 *
 * <p>Flow:
 * <ol>
 *   <li>{@link IseeUConfigurationTask} issues a challenge.</li>
 *   <li>Client replies with {@link VerificationPayload}.</li>
 *   <li>{@link #handleVerification} runs the six-step verification chain.</li>
 *   <li>Pass → {@code finishCurrentTask} (player proceeds to game). Fail → {@code disconnect}.</li>
 * </ol>
 */
public final class ServerVerificationManager {

    private static final ScheduledExecutorService TIMEOUT =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "iseeu-timeout");
                t.setDaemon(true);
                return t;
            });

    private ServerVerificationManager() {}

    // ==================================================================
    //  configuration-phase timeout
    // ==================================================================

    static void scheduleConfigTimeout(ServerConfigurationPacketListener listener,
                                       UUID playerUuid, String challengeId) {
        int seconds = IseeUConfig.VERIFICATION_TIMEOUT_SECONDS.get();
        TIMEOUT.schedule(() -> {
            VerificationState.Entry e = VerificationState.get(playerUuid);
            if (e == null || e.status != VerificationState.Status.PENDING) return;
            if (!e.challengeId.equals(challengeId)) return; // superseded
            IseeUMod.LOGGER.warn("[IseeU] {} timed out verification (config phase).", playerUuid);
            listener.disconnect(Component.literal("[IseeU] ")
                    .append(Component.translatable("iseeu.kick.timeout")));
        }, seconds, TimeUnit.SECONDS);
    }

    // ==================================================================
    //  verification chain
    // ==================================================================

    public static void handleVerification(final VerificationPayload payload, final IPayloadContext ctx) {
        // (1) challenge must match what we issued.
        VerificationState.Entry entry = VerificationState.getByChallenge(payload.challengeId());
        if (entry == null) {
            reject(ctx, "challenge mismatch — please rejoin");
            return;
        }
        if (entry.status == VerificationState.Status.VERIFIED) {
            // Already verified; acknowledge and let them through.
            ctx.finishCurrentTask(IseeUConfigurationTask.TYPE);
            return;
        }

        UUID uuid = entry.playerId;

        // (2) signature — proves the client holds the shared secret.
        if (!PacketCrypto.verifyMessage(payload.signedMessage(), payload.signature())) {
            reject(ctx, "invalid handshake signature");
            return;
        }

        // (3) replay / clock skew.
        long now = System.currentTimeMillis();
        if (!ReplayProtection.checkAndRecord(uuid.toString(), payload.nonce(), payload.clientTime(), now)) {
            reject(ctx, "replay detected or clock skew too large");
            return;
        }

        // (4) mod list hash.
        String modError = verifyModList(payload);
        if (modError != null) {
            reject(ctx, modError);
            return;
        }

        // (5) hardware fingerprint / ban check.
        String hwidError = verifyHwid(payload);
        if (hwidError != null) {
            reject(ctx, hwidError);
            return;
        }

        // (6) success — let the player in.
        VerificationState.setStatus(uuid, VerificationState.Status.VERIFIED);
        VerificationState.setHwid(uuid, payload.hwid());
        IseeUMod.LOGGER.info("[IseeU] {} verified (hwid={}).",
                uuid, shortHwid(payload.hwid()));
        ctx.finishCurrentTask(IseeUConfigurationTask.TYPE);
    }

    // ==================================================================
    //  individual checks
    // ==================================================================

    private static String verifyModList(VerificationPayload payload) {
        String required = IseeUConfig.MOD_LIST_HASH.get();
        if (required != null && !required.isBlank()) {
            if (!HashUtils.constantTimeEquals(payload.modListHash(), required.trim())) {
                return "mod list hash mismatch — pack version differs from server";
            }
        }
        return null;
    }

    private static String verifyHwid(VerificationPayload payload) {
        if (!IseeUConfig.REQUIRE_HARDWARE_FINGERPRINT.get()) return null;
        String hwid = payload.hwid();
        if (hwid == null || hwid.isBlank()) {
            return "missing hardware fingerprint";
        }
        if (BanManager.isBanned(hwid)) {
            BanManager.BanRecord rec = BanManager.get(hwid);
            String reason = rec != null ? rec.reason() : "unspecified";
            IseeUMod.LOGGER.warn("[IseeU] BANNED join attempt: hwid={} reason={}",
                    shortHwid(hwid), reason);
            return "this machine is banned (" + reason + ")";
        }
        return null;
    }

    // ==================================================================
    //  verdict helper
    // ==================================================================

    private static void reject(IPayloadContext ctx, String reason) {
        IseeUMod.LOGGER.warn("[IseeU] REJECT: {}", reason);
        // Disconnect always — in config phase there is no "log only" mode, because
        // letting an unverified client into the game defeats the purpose.
        EnforceMode mode = IseeUConfig.ENFORCE_MODE.get();
        Component reasonComponent = Component.literal("[IseeU] ")
                .withStyle(ChatFormatting.RED)
                .append(Component.literal(reason));
        if (mode == EnforceMode.LOG_ONLY) {
            // Log-only: let them through but record the failure.
            IseeUMod.LOGGER.warn("[IseeU] LOG_ONLY mode — allowing unverified client despite: {}", reason);
            ctx.finishCurrentTask(IseeUConfigurationTask.TYPE);
        } else {
            ctx.disconnect(reasonComponent);
        }
    }

    // ==================================================================
    //  utils
    // ==================================================================

    private static String shortHwid(String hwid) {
        if (hwid == null || hwid.length() < 10) return "n/a";
        return hwid.substring(0, 8) + "…" + hwid.substring(hwid.length() - 4);
    }
}
