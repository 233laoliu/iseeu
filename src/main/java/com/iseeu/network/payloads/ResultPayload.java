package com.iseeu.network.payloads;

import com.iseeu.IseeUMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client. Final verdict after the handshake.
 *
 * <p>If {@code success} is false, the client can show {@code message} to the user before the
 * server-side disconnect lands. If true, {@code message} is a short confirmation.
 */
public record ResultPayload(
        boolean success,
        String message
) implements CustomPacketPayload {

    public static final Type<ResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(IseeUMod.MOD_ID, "result"));

    public static final StreamCodec<ByteBuf, ResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,           ResultPayload::success,
                    ByteBufCodecs.STRING_UTF8,    ResultPayload::message,
                    ResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
