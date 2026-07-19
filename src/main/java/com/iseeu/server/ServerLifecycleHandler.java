package com.iseeu.server;

import com.iseeu.IseeUMod;
import com.iseeu.config.IseeUConfig;
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

        // Warn if server_secret is still the default — the mod is effectively unprotected.
        String secret = IseeUConfig.SERVER_SECRET.get();
        if ("REPLACE_ME_WITH_A_LONG_RANDOM_SECRET".equals(secret)) {
            IseeUMod.LOGGER.error("+===================================================+");
            IseeUMod.LOGGER.error("| IseeU: server_secret is still the DEFAULT VALUE!  |");
            IseeUMod.LOGGER.error("| HMAC signing is completely broken. Generate a     |");
            IseeUMod.LOGGER.error("| random secret and put it in iseeu-common.toml.    |");
            IseeUMod.LOGGER.error("+===================================================+");
        }
        String salt = IseeUConfig.HWID_SALT.get();
        if ("iseeu-v1-salt".equals(salt)) {
            IseeUMod.LOGGER.warn("[IseeU] hwid_salt is the default — consider changing it."
                    + " Changing salt invalidates all existing HWID ban records.");
        }
    }

    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event) {
        BanManager.save();
        ServerVerificationManager.shutdown();
        IseeUMod.LOGGER.info("[IseeU] server stopping; ban list saved.");
    }
}
