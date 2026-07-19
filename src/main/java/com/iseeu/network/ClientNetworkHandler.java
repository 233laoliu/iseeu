package com.iseeu.network;

import com.iseeu.IseeUMod;
import com.iseeu.hardware.HardwareFingerprint;
import com.iseeu.modlist.ModListCollector;
import com.iseeu.network.payloads.HandshakeChallengePayload;
import com.iseeu.network.payloads.VerificationPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side payload handlers.
 *
 * <p>Handlers are registered in {@link ModPayloads} via playToClient. This class is safe to load
 * on a dedicated server — it references no client-only Minecraft types, and its handlers are
 * only invoked on the client (S→C payloads never fire server-side).
 *
 * <p>Handlers run on the network thread (set at registration time) so that the blocking
 * hardware-id collection in {@link #onChallenge} does not stall the client render thread.
 */
public final class ClientNetworkHandler {

    private ClientNetworkHandler() {}

    /**
     * Server challenged us. Collect the required evidence, sign it, and reply.
     */
    public static void onChallenge(final HandshakeChallengePayload payload, final IPayloadContext ctx) {
        try {
            final String challengeId = payload.challengeId();
            final long clientTime = System.currentTimeMillis();
            final String nonce = PacketCrypto.newNonce();
            final String modListHash = ModListCollector.hash();

            final String hwid;
            if (payload.requireHardwareFingerprint()) {
                hwid = HardwareFingerprint.compute();
            } else {
                hwid = "";
            }

            // Canonical message — MUST match VerificationPayload.signedMessage() on the server.
            final String message = challengeId + "|" + clientTime + "|" + nonce + "|" + modListHash + "|" + hwid;
            final String signature = PacketCrypto.signMessage(message);

            PacketDistributor.sendToServer(new VerificationPayload(
                    challengeId, clientTime, nonce, modListHash, hwid, signature));

            IseeUMod.LOGGER.debug("[IseeU] reply sent (cid={}, hwid_len={}).",
                    challengeId.substring(0, Math.min(8, challengeId.length())),
                    hwid.length());
        } catch (Throwable t) {
            IseeUMod.LOGGER.error("[IseeU] failed to build verification reply:", t);
            // Don't try to send an error payload — the connection may already be dead,
            // and PacketDistributor.sendToServer requires a non-null payload which can
            // cause a secondary NPE that crashes the connection thread.
        }
    }
}
