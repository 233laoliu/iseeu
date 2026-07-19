package com.iseeu.network;

import com.iseeu.IseeUMod;
import com.iseeu.network.payloads.HandshakeChallengePayload;
import com.iseeu.network.payloads.ResultPayload;
import com.iseeu.network.payloads.VerificationPayload;
import net.neoforged.fml.event.lifecycle.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Central registration of all custom payloads.
 *
 * <p>Direction map:
 * <ul>
 *   <li>{@link HandshakeChallengePayload} — S→C</li>
 *   <li>{@link VerificationPayload}        — C→S (server handler registered here)</li>
 *   <li>{@link ResultPayload}              — S→C</li>
 * </ul>
 *
 * <p>Server handlers for C→S payloads are registered directly. Client handlers for S→C payloads
 * are registered via {@link ClientPayloadRegistration} (which is side-restricted so the server
 * never loads client-only handler classes).
 */
public final class ModPayloads {

    public static final String NETWORK_VERSION = "1";

    private ModPayloads() {}

    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);

        // Client → Server: server-side handler runs on the network thread.
        registrar.playToServer(
                VerificationPayload.TYPE,
                VerificationPayload.STREAM_CODEC,
                ServerNetworkHandler::onVerification);

        // Server → Client: codec only; handlers wired up client-side.
        registrar.playToClient(HandshakeChallengePayload.TYPE, HandshakeChallengePayload.STREAM_CODEC);
        registrar.playToClient(ResultPayload.TYPE, ResultPayload.STREAM_CODEC);

        IseeUMod.LOGGER.debug("[IseeU] payloads registered (version={}).", NETWORK_VERSION);
    }
}
