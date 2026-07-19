package com.iseeu.network;

import com.iseeu.IseeUMod;
import com.iseeu.network.payloads.HandshakeChallengePayload;
import com.iseeu.network.payloads.ResultPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.HandlerThread;

/**
 * Registers client-side handlers for S→C payloads.
 *
 * <p>This class is annotated {@code @EventBusSubscriber(value = Dist.CLIENT, bus = MOD)} so it
 * is only loaded on the physical client — a dedicated server never touches
 * {@link ClientNetworkHandler}, which references client-only state.
 */
@EventBusSubscriber(modid = IseeUMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientPayloadRegistration {

    private ClientPayloadRegistration() {}

    @SubscribeEvent
    public static void onRegisterClient(final RegisterClientPayloadHandlersEvent event) {
        // Challenge handler runs on the network thread because it does blocking IO
        // (hardware collection via ProcessBuilder) — running it on main would stall rendering.
        event.register(HandshakeChallengePayload.TYPE, HandlerThread.NETWORK,
                ClientNetworkHandler::onChallenge);
        // Result handler is tiny; main thread is fine.
        event.register(ResultPayload.TYPE, ClientNetworkHandler::onResult);
    }
}
