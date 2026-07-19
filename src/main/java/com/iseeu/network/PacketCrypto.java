package com.iseeu.network;

import com.iseeu.IseeUMod;
import com.iseeu.config.IseeUConfig;
import com.iseeu.util.HashUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * HMAC-SHA256 sign / verify for {@link com.iseeu.network.payloads.VerificationPayload}.
 *
 * <p>The signing key is the {@code server_secret} from config. Both client and server read the
 * same value (the client receives it implicitly via the fact that the server accepts the signed
 * reply — there is no secret exchange over the wire).
 *
 * <p><strong>Threat model</strong>: a passive MITM cannot forge a valid reply without the secret.
 * An active MITM who has the secret can forge anything — protect the server config file.
 */
public final class PacketCrypto {

    private static final SecureRandom RNG = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private PacketCrypto() {}

    private static volatile boolean secretChecked;

    /** @return HMAC-SHA256(secret, message) as lowercase hex. */
    public static String signMessage(String message) {
        String secret = IseeUConfig.SERVER_SECRET.get();
        if (!secretChecked && "REPLACE_ME_WITH_A_LONG_RANDOM_SECRET".equals(secret)) {
            secretChecked = true;
            IseeUMod.LOGGER.warn("[IseeU] server_secret is the default — signatures provide no real security!");
        }
        return HashUtils.hmacSha256Hex(secret, message);
    }

    /** Constant-time signature verification. */
    public static boolean verifyMessage(String message, String expectedSignature) {
        String actual = signMessage(message);
        return HashUtils.constantTimeEquals(actual, expectedSignature);
    }

    /** @return a fresh 128-bit hex token. Used for challenge ids and nonces. */
    public static String randomToken() {
        byte[] buf = new byte[16];
        RNG.nextBytes(buf);
        char[] out = new char[32];
        for (int i = 0; i < buf.length; i++) {
            int v = buf[i] & 0xFF;
            out[i * 2]     = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }

    /** @return a fresh nonce scoped to one verification round. */
    public static String newNonce() {
        return randomToken();
    }
}
