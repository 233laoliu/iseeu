package com.iseeu.network;

import com.iseeu.IseeUMod;
import com.iseeu.network.payloads.HandshakeChallengePayload;
import com.iseeu.network.payloads.VerificationPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Central registration of all custom payloads.
 *
 * <p>All payloads are registered for the <strong>configuration phase</strong> — the handshake
 * happens before the player enters the game world, so a rejected client never sees the world.
 *
 * <p>Direction map:
 * <ul>
 *   <li>{@link HandshakeChallengePayload} — S→C (challenge issued by server)</li>
 *   <li>{@link VerificationPayload}        — C→S (signed reply from client)</li>
 * </ul>
 *
 * <p>NeoForge 1.21.1 uses {@code configurationToClient}/{@code configurationToServer} with an
 * inline handler. ClientNetworkHandler is side-safe (no client-only class refs).
 */
public final class ModPayloads {

    public static final String NETWORK_VERSION = "1";

    private ModPayloads() {}

    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(NETWORK_VERSION)
                .executesOn(HandlerThread.NETWORK);

        // Client → Server: signed verification blob.
        registrar.configurationToServer(
                VerificationPayload.TYPE,
                VerificationPayload.STREAM_CODEC,
                ServerNetworkHandler::onVerification);

        // Server → Client: challenge.
        registrar.configurationToClient(
                HandshakeChallengePayload.TYPE,
                HandshakeChallengePayload.STREAM_CODEC,
                ClientNetworkHandler::onChallenge);

        IseeUMod.LOGGER.debug("[IseeU] configuration payloads registered (version={}).", NETWORK_VERSION);
    }
}
