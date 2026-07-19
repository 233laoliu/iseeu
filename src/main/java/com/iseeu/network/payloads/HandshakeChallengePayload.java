package com.iseeu.network.payloads;

import com.iseeu.IseeUMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client. Sent once per connection to challenge the client into proving its identity.
 */
public record HandshakeChallengePayload(
        String challengeId,
        boolean requireHardwareFingerprint
) implements CustomPacketPayload {

    public static final Type<HandshakeChallengePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(IseeUMod.MOD_ID, "handshake_challenge"));

    public static final StreamCodec<ByteBuf, HandshakeChallengePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,    HandshakeChallengePayload::challengeId,
                    ByteBufCodecs.BOOL,           HandshakeChallengePayload::requireHardwareFingerprint,
                    HandshakeChallengePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
