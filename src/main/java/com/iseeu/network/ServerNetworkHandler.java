package com.iseeu.network;

import com.iseeu.IseeUMod;
import com.iseeu.network.payloads.VerificationPayload;
import com.iseeu.server.ServerVerificationManager;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Thin server-side dispatcher for inbound C→S payloads.
 *
 * <p>All actual logic lives in {@link ServerVerificationManager} — this class exists only so the
 * payload registrar has a static handler reference to bind to. It is registered via the payload
 * registrar (not the event bus) so no {@code @EventBusSubscriber} is needed.
 */
public final class ServerNetworkHandler {

    private ServerNetworkHandler() {}

    public static void onVerification(final VerificationPayload payload, final IPayloadContext ctx) {
        // The registrar runs us on the network thread by default; the manager does the heavy
        // crypto/IO here, then uses enqueueWork for any main-thread mutations.
        try {
            ServerVerificationManager.handleVerification(payload, ctx);
        } catch (Throwable t) {
            // Never let an exception in verification crash the netty thread.
            IseeUMod.LOGGER.error("[IseeU] verification threw: {}", t.toString());
            ctx.disconnect(Component.literal("[IseeU] internal verification error"));
        }
    }
}
