package com.iseeu.server;

import com.iseeu.IseeUMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Loads the HWID ban list on server start and persists it on shutdown.
 *
 * <p>Ban mutations at runtime (via {@code /iseeu ban} etc.) write immediately, so this is mainly
 * for the cold-start load and a final flush on exit.
 */
@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
public final class ServerLifecycleHandler {

    private ServerLifecycleHandler() {}

    @SubscribeEvent
    public static void onServerStarted(final ServerStartedEvent event) {
        BanManager.load();
        IseeUMod.LOGGER.info("[IseeU] server started; ban list loaded.");
    }

    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event) {
        BanManager.save();
        IseeUMod.LOGGER.info("[IseeU] server stopping; ban list saved.");
    }
}
