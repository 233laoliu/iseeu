package com.iseeu.network.payloads;

import com.iseeu.IseeUMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → Server. The signed verification blob.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code challengeId} — echoed from {@link HandshakeChallengePayload}.</li>
 *   <li>{@code clientTime} — client wall clock (ms). Server checks skew vs. tolerance.</li>
 *   <li>{@code nonce} — client-generated random token; tracked server-side to block replay.</li>
 *   <li>{@code modListHash} — SHA-256 of the client's sorted mod list.</li>
 *   <li>{@code hwid} — SHA-256 hardware fingerprint, or empty if disabled.</li>
 *   <li>{@code signature} — HMAC-SHA256 over the canonical message, keyed by server_secret.</li>
 * </ul>
 *
 * <p>The signed message is constructed by {@link com.iseeu.network.PacketCrypto#signMessage}.
 */
public record VerificationPayload(
        String challengeId,
        long clientTime,
        String nonce,
        String modListHash,
        String hwid,
        String signature
) implements CustomPacketPayload {

    public static final Type<VerificationPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(IseeUMod.MOD_ID, "verification"));

    public static final StreamCodec<ByteBuf, VerificationPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,   VerificationPayload::challengeId,
                    ByteBufCodecs.VAR_LONG,      VerificationPayload::clientTime,
                    ByteBufCodecs.STRING_UTF8,   VerificationPayload::nonce,
                    ByteBufCodecs.STRING_UTF8,   VerificationPayload::modListHash,
                    ByteBufCodecs.STRING_UTF8,   VerificationPayload::hwid,
                    ByteBufCodecs.STRING_UTF8,   VerificationPayload::signature,
                    VerificationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Canonical string that gets HMAC-signed — order MUST match {@code PacketCrypto.signMessage}. */
    public String signedMessage() {
        return challengeId + "|" + clientTime + "|" + nonce + "|" + modListHash + "|" + hwid;
    }
}
