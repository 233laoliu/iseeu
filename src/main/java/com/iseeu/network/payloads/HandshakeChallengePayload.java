package com.iseeu.network.payloads;

import com.iseeu.IseeUMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client. Sent once per login to challenge the client into proving its identity.
 *
 * <p>{@code challengeId} is a single-use random token; {@code serverTime} anchors replay
 * protection — the client must echo both in its reply and the server rejects any reply whose
 * {@code challengeId} it did not issue.
 */
public record HandshakeChallengePayload(
        String challengeId,
        long serverTime,
        boolean requireHardwareFingerprint
) implements CustomPacketPayload {

    public static final Type<HandshakeChallengePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(IseeUMod.MOD_ID, "handshake_challenge"));

    public static final StreamCodec<ByteBuf, HandshakeChallengePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,    HandshakeChallengePayload::challengeId,
                    ByteBufCodecs.VAR_LONG,       HandshakeChallengePayload::serverTime,
                    ByteBufCodecs.BOOL,           HandshakeChallengePayload::requireHardwareFingerprint,
                    HandshakeChallengePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
