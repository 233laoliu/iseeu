package com.iseeu.server;

import com.iseeu.IseeUMod;
import com.iseeu.config.IseeUConfig;
import com.iseeu.config.IseeUConfig.EnforceMode;
import com.iseeu.network.PacketCrypto;
import com.iseeu.network.ReplayProtection;
import com.iseeu.network.payloads.HandshakeChallengePayload;
import com.iseeu.network.payloads.ResultPayload;
import com.iseeu.network.payloads.VerificationPayload;
import com.iseeu.util.HashUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Server-side authority for the anti-cheat handshake.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>On login, issue a one-time challenge to the client.</li>
 *   <li>On receipt of {@link VerificationPayload}, run the full verification chain.</li>
 *   <li>On timeout, kick the player if still PENDING.</li>
 *   <li>On disconnect, purge player state.</li>
 * </ol>
 */
public final class ServerVerificationManager {

    /** Single-thread scheduler for timeout checks. Daemon so it never blocks JVM shutdown. */
    private static final ScheduledExecutorService TIMEOUT =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "iseeu-timeout");
                t.setDaemon(true);
                return t;
            });

    private ServerVerificationManager() {}

    // ==================================================================
    //  login / logout
    // ==================================================================

    @SubscribeEvent
    public static void onPlayerLogin(final PlayerEvent.PlayerLoggedInEvent event) {
        if (IseeUConfig.ENFORCE_MODE.get() == EnforceMode.DISABLED) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Op bypass — only when explicitly enabled.
        if (IseeUConfig.ALLOW_OP_BYPASS.get() && player.hasPermissions(2)) {
            VerificationState.Entry e = VerificationState.issueChallenge(player.getUUID(), "op-bypass");
            e.status = VerificationState.Status.VERIFIED;
            return;
        }

        String challengeId = PacketCrypto.randomToken();
        VerificationState.issueChallenge(player.getUUID(), challengeId);

        boolean requireHwid = IseeUConfig.REQUIRE_HARDWARE_FINGERPRINT.get();
        PacketDistributor.sendToPlayer(player,
                new HandshakeChallengePayload(challengeId, System.currentTimeMillis(), requireHwid));

        IseeUMod.LOGGER.debug("[IseeU] challenge sent to {} (cid={}).",
                player.getName().getString(), challengeId.substring(0, 8));
        scheduleTimeout(player.getUUID(), challengeId);
    }

    @SubscribeEvent
    public static void onPlayerLogout(final PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        VerificationState.remove(uuid);
        ReplayProtection.reset(uuid.toString());
    }

    private static void scheduleTimeout(UUID uuid, String challengeId) {
        int seconds = IseeUConfig.VERIFICATION_TIMEOUT_SECONDS.get();
        TIMEOUT.schedule(() -> {
            VerificationState.Entry e = VerificationState.get(uuid);
            if (e == null || e.status != VerificationState.Status.PENDING) return;
            if (!e.challengeId.equals(challengeId)) return; // superseded by a re-issue
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                if (p == null) return;
                if (VerificationState.isPending(uuid)) {
                    IseeUMod.LOGGER.warn("[IseeU] {} timed out verification.", uuid);
                    p.connection.disconnect(Component.literal("[IseeU] ")
                            .append(Component.translatable("iseeu.kick.timeout")));
                }
            });
        }, seconds, TimeUnit.SECONDS);
    }

    // ==================================================================
    //  verification chain
    // ==================================================================

    public static void handleVerification(final VerificationPayload payload, final IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();

        // (1) challenge must match what we issued.
        VerificationState.Entry entry = VerificationState.get(uuid);
        if (entry == null || !entry.challengeId.equals(payload.challengeId())) {
            reject(player, ctx, "challenge mismatch — please rejoin");
            return;
        }
        if (entry.status == VerificationState.Status.VERIFIED) {
            // Already verified; ignore duplicate (could be a retry).
            return;
        }

        // (2) signature — proves the client holds the shared secret.
        if (!PacketCrypto.verifyMessage(payload.signedMessage(), payload.signature())) {
            reject(player, ctx, "invalid handshake signature");
            return;
        }

        // (3) replay / clock skew.
        long now = System.currentTimeMillis();
        if (!ReplayProtection.checkAndRecord(uuid.toString(), payload.nonce(), payload.clientTime(), now)) {
            reject(player, ctx, "replay detected or clock skew too large");
            return;
        }

        // (4) mod list.
        String modError = verifyModList(payload);
        if (modError != null) {
            reject(player, ctx, modError);
            return;
        }

        // (5) hardware fingerprint / ban check.
        String hwidError = verifyHwid(payload, player);
        if (hwidError != null) {
            reject(player, ctx, hwidError);
            return;
        }

        // (6) success.
        VerificationState.setStatus(uuid, VerificationState.Status.VERIFIED);
        VerificationState.setHwid(uuid, payload.hwid());
        PacketDistributor.sendToPlayer(player, new ResultPayload(true, "verified"));
        IseeUMod.LOGGER.info("[IseeU] {} verified (hwid={}).",
                player.getName().getString(), shortHwid(payload.hwid()));
    }

    // ==================================================================
    //  individual checks
    // ==================================================================

    private static String verifyModList(VerificationPayload payload) {
        // (a) required hash — strictest check, when set.
        String required = IseeUConfig.MOD_LIST_HASH.get();
        if (required != null && !required.isBlank()) {
            if (!HashUtils.constantTimeEquals(payload.modListHash(), required.trim())) {
                return "mod list hash mismatch — pack version differs from server";
            }
        }

        // (b) blacklist takes precedence over whitelist.
        List<? extends String> blacklist = IseeUConfig.MOD_BLACKLIST.get();
        for (String entry : payload.modList()) {
            String modId = modIdOf(entry);
            for (String bad : blacklist) {
                if (bad.equalsIgnoreCase(modId)) {
                    return "disallowed mod detected: " + modId;
                }
            }
        }

        // (c) whitelist (if non-empty, every mod must be in it).
        List<? extends String> whitelist = IseeUConfig.MOD_WHITELIST.get();
        if (whitelist != null && !whitelist.isEmpty()) {
            for (String entry : payload.modList()) {
                String modId = modIdOf(entry);
                boolean ok = false;
                for (String allowed : whitelist) {
                    if (allowed.equalsIgnoreCase(modId)) { ok = true; break; }
                }
                if (!ok) return "mod not on whitelist: " + modId;
            }
        }
        return null;
    }

    private static String verifyHwid(VerificationPayload payload, ServerPlayer player) {
        if (!IseeUConfig.REQUIRE_HARDWARE_FINGERPRINT.get()) return null;
        String hwid = payload.hwid();
        if (hwid == null || hwid.isBlank()) {
            return "missing hardware fingerprint";
        }
        if (BanManager.isBanned(hwid)) {
            BanManager.BanRecord rec = BanManager.get(hwid);
            String reason = rec != null ? rec.reason() : "unspecified";
            IseeUMod.LOGGER.warn("[IseeU] BANNED join attempt: {} hwid={} reason={}",
                    player.getName().getString(), shortHwid(hwid), reason);
            return "this machine is banned (" + reason + ")";
        }
        return null;
    }

    // ==================================================================
    //  verdict helpers
    // ==================================================================

    private static void reject(ServerPlayer player, IPayloadContext ctx, String reason) {
        IseeUMod.LOGGER.warn("[IseeU] REJECT {} : {}",
                player.getName().getString(), reason);
        VerificationState.setStatus(player.getUUID(), VerificationState.Status.FAILED);

        EnforceMode mode = IseeUConfig.ENFORCE_MODE.get();
        Component reasonComponent = Component.literal("[IseeU] ")
                .withStyle(ChatFormatting.RED)
                .append(Component.literal(reason));

        // Tell the client why, then (if enforcing) disconnect.
        PacketDistributor.sendToPlayer(player, new ResultPayload(false, reason));

        if (mode == EnforceMode.ENFORCE) {
            ctx.enqueueWork(() -> player.connection.disconnect(reasonComponent));
        }
    }

    // ==================================================================
    //  utils
    // ==================================================================

    private static String modIdOf(String entry) {
        int at = entry.indexOf('@');
        return at < 0 ? entry : entry.substring(0, at);
    }

    private static String shortHwid(String hwid) {
        if (hwid == null || hwid.length() < 10) return "n/a";
        return hwid.substring(0, 8) + "…" + hwid.substring(hwid.length() - 4);
    }
}
