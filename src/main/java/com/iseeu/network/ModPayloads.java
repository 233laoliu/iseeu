package com.iseeu.network;

import com.iseeu.IseeUMod;
import com.iseeu.network.payloads.HandshakeChallengePayload;
import com.iseeu.network.payloads.ResultPayload;
import com.iseeu.network.payloads.VerificationPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Central registration of all custom payloads.
 *
 * <p>Direction map:
 * <ul>
 *   <li>{@link HandshakeChallengePayload} — S→C (client handler: {@link ClientNetworkHandler#onChallenge})</li>
 *   <li>{@link VerificationPayload}        — C→S (server handler: {@link ServerNetworkHandler#onVerification})</li>
 *   <li>{@link ResultPayload}              — S→C (client handler: {@link ClientNetworkHandler#onResult})</li>
 * </ul>
 *
 * <p>NeoForge 1.21.1 uses {@code playToClient}/{@code playToServer} with an inline handler —
 * there is no separate {@code RegisterClientPayloadHandlersEvent} (that was introduced in 1.21.2+).
 * ClientNetworkHandler is side-safe (no client-only class refs), so loading it on a dedicated
 * server is harmless.
 */
public final class ModPayloads {

    public static final String NETWORK_VERSION = "1";

    private ModPayloads() {}

    public static void register(final RegisterPayloadHandlersEvent event) {
        // Challenge handler does blocking IO (hardware collection) — run on network thread.
        final PayloadRegistrar registrar = event.registrar(NETWORK_VERSION)
                .executesOn(HandlerThread.NETWORK);

        // Client → Server: server-side handler.
        registrar.playToServer(
                VerificationPayload.TYPE,
                VerificationPayload.STREAM_CODEC,
                ServerNetworkHandler::onVerification);

        // Server → Client: client-side handlers wired up directly (1.21.1 API).
        registrar.playToClient(
                HandshakeChallengePayload.TYPE,
                HandshakeChallengePayload.STREAM_CODEC,
                ClientNetworkHandler::onChallenge);
        registrar.playToClient(
                ResultPayload.TYPE,
                ResultPayload.STREAM_CODEC,
                ClientNetworkHandler::onResult);

        IseeUMod.LOGGER.debug("[IseeU] payloads registered (version={}).", NETWORK_VERSION);
    }
}
