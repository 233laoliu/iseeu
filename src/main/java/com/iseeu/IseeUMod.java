package com.iseeu;

import com.iseeu.config.IseeUConfig;
import com.iseeu.network.ModPayloads;
import com.iseeu.server.ServerVerificationManager;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * IseeU Guard — main mod entry.
 *
 * <p>Architectural rules:
 * <ul>
 *   <li>Server  = authority. Allowing/kicking decisions live here.</li>
 *   <li>Client  = collector + reporter. Never trusts its own state, only forwards signed blobs.</li>
 *   <li>Both    = required. A client without the mod cannot join a server that enforces it.</li>
 * </ul>
 */
@Mod(IseeUMod.MOD_ID)
public final class IseeUMod {

    public static final String MOD_ID = "iseeu";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IseeUMod(IEventBus modEventBus, ModContainer modContainer) {
        // ---- common mod-load events ----
        modEventBus.addListener(IseeUMod::commonSetup);
        modEventBus.addListener(ModPayloads::register);

        // ---- runtime events ----
        // Server side: handshake kick-off, verification state, ban checks.
        // Client handlers are wired up via playToClient in ModPayloads — no separate event bus
        // registration needed (NeoForge 1.21.1 registers them inline with the payload).
        NeoForge.EVENT_BUS.register(ServerVerificationManager.class);

        // ---- config (server file: config/iseeu-server.toml) ----
        modContainer.registerConfig(ModConfig.Type.SERVER, IseeUConfig.SPEC);
    }

    private static void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[IseeU] loaded. mode = {}", IseeUConfig.ENFORCE_MODE.get());
    }
}
