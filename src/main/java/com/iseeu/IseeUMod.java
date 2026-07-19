package com.iseeu;

import com.iseeu.config.IseeUConfig;
import com.iseeu.network.ModPayloads;
import com.iseeu.server.IseeUConfigurationTask;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
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
 *
 * <p>Handshake runs in the <strong>configuration phase</strong> — before the player enters the
 * game world. A rejected client is disconnected during config and never sees the world.
 */
@Mod(IseeUMod.MOD_ID)
public final class IseeUMod {

    public static final String MOD_ID = "iseeu";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IseeUMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(IseeUMod::commonSetup);
        modEventBus.addListener(ModPayloads::register);
        // Configuration-phase handshake task registration.
        modEventBus.addListener(IseeUMod::onRegisterConfigTasks);

        modContainer.registerConfig(ModConfig.Type.COMMON, IseeUConfig.SPEC);
    }

    private static void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[IseeU] loaded.");
    }

    private static void onRegisterConfigTasks(final RegisterConfigurationTasksEvent event) {
        event.register(new IseeUConfigurationTask(event.getListener()));
    }
}
