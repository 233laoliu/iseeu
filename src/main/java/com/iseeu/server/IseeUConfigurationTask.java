package com.iseeu.server;

import com.iseeu.IseeUMod;
import com.iseeu.config.IseeUConfig;
import com.iseeu.network.PacketCrypto;
import com.iseeu.network.payloads.HandshakeChallengePayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Configuration-phase handshake task.
 *
 * <p>Runs before the player enters the game world. Sends a challenge to the client and arms a
 * timeout. The client's reply ({@link com.iseeu.network.payloads.VerificationPayload}) is handled
 * by {@link ServerVerificationManager#handleVerification}, which either calls
 * {@code finishCurrentTask} (let the player in) or {@code disconnect} (reject).
 *
 * <p>If the mod is disabled, the task completes immediately — no handshake, no block.
 */
public final class IseeUConfigurationTask implements ICustomConfigurationTask {

    public static final ConfigurationTask.Type TYPE =
            new ConfigurationTask.Type(
                    ResourceLocation.fromNamespaceAndPath(IseeUMod.MOD_ID, "handshake"));

    private final ServerConfigurationPacketListener listener;

    public IseeUConfigurationTask(ServerConfigurationPacketListener listener) {
        this.listener = listener;
    }

    @Override
    public void run(Consumer<CustomPacketPayload> sender) {
        // Disabled? Skip the handshake entirely.
        if (IseeUConfig.ENFORCE_MODE.get() == IseeUConfig.EnforceMode.DISABLED) {
            listener.finishCurrentTask(TYPE);
            return;
        }

        // ServerConfigurationPacketListener has no getOwner() in MC 1.21.1.
        // We use a random UUID as placeholder — the real identifier is the challengeId.
        UUID placeholderUuid = UUID.randomUUID();
        String challengeId = PacketCrypto.randomToken();
        VerificationState.issueChallenge(placeholderUuid, challengeId);

        boolean requireHwid = IseeUConfig.REQUIRE_HARDWARE_FINGERPRINT.get();
        sender.accept(new HandshakeChallengePayload(
                challengeId, System.currentTimeMillis(), requireHwid));

        IseeUMod.LOGGER.debug("[IseeU] config-phase challenge sent (cid={}).",
                challengeId.substring(0, 8));

        ServerVerificationManager.scheduleConfigTimeout(listener, placeholderUuid, challengeId);
    }

    @Override
    public ConfigurationTask.Type type() {
        return TYPE;
    }
}
