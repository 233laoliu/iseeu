package com.iseeu.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Server-side configuration. Lives in {@code config/iseeu-server.toml}.
 *
 * <p>All security-relevant knobs live here. The {@code server_secret} in particular
 * <strong>must</strong> be regenerated per deployment — it is the HMAC signing key shared
 * with clients that join this server.
 */
public final class IseeUConfig {

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    // ---------- general ----------
    public static final ModConfigSpec.EnumValue<EnforceMode> ENFORCE_MODE =
            B.comment("enforce = kick on failure; log_only = record but allow; disabled = do nothing.")
             .defineEnum("general.enforce_mode", EnforceMode.ENFORCE);

    public static final ModConfigSpec.BooleanValue REQUIRE_HARDWARE_FINGERPRINT =
            B.comment("If true, clients must send a hardware fingerprint. Disable for headless clients.")
             .define("general.require_hardware_fingerprint", true);

    // ---------- security ----------
    public static final ModConfigSpec.ConfigValue<String> SERVER_SECRET =
            B.comment("Shared HMAC secret. Generate a long random string per server,",
                      "e.g. `openssl rand -hex 32`. NEVER ship the default in production.")
             .define("security.server_secret", "REPLACE_ME_WITH_A_LONG_RANDOM_SECRET");

    public static final ModConfigSpec.ConfigValue<String> HWID_SALT =
            B.comment("Extra salt mixed into hardware fingerprint before hashing.",
                      "Different salt => different HWID space => old ban lists become useless.")
             .define("security.hwid_salt", "iseeu-v1-salt");

    public static final ModConfigSpec.IntValue TIMESTAMP_TOLERANCE_SECONDS =
            B.comment("Max acceptable clock skew between client and server, in seconds.")
             .defineInRange("security.timestamp_tolerance_seconds", 60, 5, 600);

    public static final ModConfigSpec.IntValue NONCE_CACHE_SIZE =
            B.comment("How many recent nonces to remember per player for replay protection.")
             .defineInRange("security.nonce_cache_size", 256, 16, 4096);

    public static final ModConfigSpec.IntValue VERIFICATION_TIMEOUT_SECONDS =
            B.comment("How long after login a client has to complete the handshake before kick.")
             .defineInRange("security.verification_timeout_seconds", 15, 5, 120);

    public static final ModConfigSpec.IntValue CHALLENGE_MAX_AGE_SECONDS =
            B.comment("Maximum age of a challenge (seconds). Rejects replayed challenges captured after this window.")
             .defineInRange("security.challenge_max_age_seconds", 30, 10, 300);

    // ---------- mod list ----------
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MOD_WHITELIST =
            B.comment("Allowed mod ids. Empty list = no whitelist enforcement (use MOD_LIST_HASH instead).")
             .defineListAllowEmpty(Collections.singletonList("mod_list.allowed_mods"),
                     ArrayList::new,
                     o -> o instanceof String s && !s.isBlank());

    public static final ModConfigSpec.ConfigValue<List<? extends String>> MOD_BLACKLIST =
            B.comment("Disallowed mod ids. Takes precedence over the whitelist.")
             .defineListAllowEmpty(Collections.singletonList("mod_list.disallowed_mods"),
                     ArrayList::new,
                     o -> o instanceof String s && !s.isBlank());

    public static final ModConfigSpec.ConfigValue<String> MOD_LIST_HASH =
            B.comment("Expected SHA-256 of the sorted mod list (modId@version joined by ';').",
                      "Empty = not enforced. Use /iseeu hashtest to print the current value.")
             .define("mod_list.required_hash", "");

    public static final ModConfigSpec.BooleanValue ALLOW_OP_BYPASS =
            B.comment("If true, server operators (op level >= 2) skip verification. Use for admins only.")
             .define("mod_list.allow_op_bypass", false);

    public static final ModConfigSpec SPEC = B.build();

    public enum EnforceMode {
        /** Kick the player on any verification failure. */
        ENFORCE,
        /** Log failures but allow the player to stay. Useful for dry-runs. */
        LOG_ONLY,
        /** Effectively turn the mod off. */
        DISABLED
    }

    private IseeUConfig() {}
}
