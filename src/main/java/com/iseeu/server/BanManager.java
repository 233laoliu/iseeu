package com.iseeu.server;

import com.iseeu.IseeUMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Persistent HWID ban list. Stored as {@code config/iseeu-bans.json}.
 *
 * <p>Format:
 * <pre>
 * {
 *   "bans": [
 *     { "hwid": "abc...", "name": "Notch", "uuid": "...", "reason": "xray", "at": "2024-..." }
 *   ]
 * }</pre>
 *
 * <p>Ban records include the player name/uuid at ban time so that admins can recognise entries
 * later, but the <em>match key</em> is always the HWID hash — names can change.
 */
public final class BanManager {

    private static final Path CONFIG_DIR = Path.of("config");
    private static final Path BAN_FILE = CONFIG_DIR.resolve("iseeu-bans.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Volatile reference — load() atomically swaps, ban/unban operate on latest.
    private static volatile ConcurrentMap<String, BanRecord> BANNED = new ConcurrentHashMap<>();

    private BanManager() {}

    // ------------------------------------------------------------------
    //  load / save
    // ------------------------------------------------------------------

    public static synchronized void load() {
        var newMap = new ConcurrentHashMap<String, BanRecord>();
        if (!Files.exists(BAN_FILE)) {
            BANNED = newMap;
            IseeUMod.LOGGER.info("[IseeU] no existing ban file; starting empty.");
            return;
        }
        try {
            String raw = Files.readString(BAN_FILE, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            JsonArray arr = root.has("bans") ? root.getAsJsonArray("bans") : new JsonArray();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                String hwid = o.get("hwid").getAsString();
                String name = o.has("name") ? o.get("name").getAsString() : "?";
                String uuid = o.has("uuid") ? o.get("uuid").getAsString() : "";
                String reason = o.has("reason") ? o.get("reason").getAsString() : "";
                String at = o.has("at") ? o.get("at").getAsString() : Instant.now().toString();
                newMap.put(hwid.toLowerCase(Locale.ROOT), new BanRecord(hwid, name, uuid, reason, at));
            }
            BANNED = newMap;  // atomic swap — no window where isBanned() sees half-loaded data
            IseeUMod.LOGGER.info("[IseeU] loaded {} HWID bans.", newMap.size());
        } catch (JsonSyntaxException | JsonIOException e) {
            IseeUMod.LOGGER.error("[IseeU] ban file is corrupt JSON — ignored: {}", e.toString());
        } catch (IOException e) {
            IseeUMod.LOGGER.error("[IseeU] failed to read ban file: {}", e.toString());
        }
    }

    public static synchronized void save() {
        var current = BANNED;
        Path tmpFile = CONFIG_DIR.resolve("iseeu-bans.json.tmp");
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (BanRecord r : current.values()) {
                JsonObject o = new JsonObject();
                o.addProperty("hwid", r.hwid);
                o.addProperty("name", r.name);
                o.addProperty("uuid", r.uuid);
                o.addProperty("reason", r.reason);
                o.addProperty("at", r.at);
                arr.add(o);
            }
            root.add("bans", arr);
            Files.writeString(tmpFile, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmpFile, BAN_FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            IseeUMod.LOGGER.error("[IseeU] failed to write ban file: {}", e.toString());
            try { Files.deleteIfExists(tmpFile); } catch (IOException ignored) {}
        }
    }

    // ------------------------------------------------------------------
    //  query / mutate
    // ------------------------------------------------------------------

    public static boolean isBanned(String hwid) {
        if (hwid == null || hwid.isBlank()) return false;
        return BANNED.containsKey(hwid.toLowerCase(Locale.ROOT));
    }

    public static BanRecord get(String hwid) {
        if (hwid == null) return null;
        return BANNED.get(hwid.toLowerCase(Locale.ROOT));
    }

    public static void ban(String hwid, String name, String uuid, String reason) {
        if (hwid == null || hwid.isBlank()) return;
        BanRecord rec = new BanRecord(hwid, name == null ? "?" : name,
                uuid == null ? "" : uuid,
                reason == null ? "" : reason,
                Instant.now().toString());
        BANNED.put(hwid.toLowerCase(Locale.ROOT), rec);
        save();
    }

    public static boolean unban(String hwid) {
        if (hwid == null) return false;
        boolean removed = BANNED.remove(hwid.toLowerCase(Locale.ROOT)) != null;
        if (removed) save();
        return removed;
    }

    public static List<BanRecord> all() {
        return new ArrayList<>(BANNED.values());
    }

    public static Set<String> allHwids() {
        return BANNED.keySet();
    }

    /** Convenience: find the player name for a uuid, if currently online. */
    public static String nameForUuid(String uuidStr) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return "?";
            UUID uuid = UUID.fromString(uuidStr);
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) return p.getGameProfile().getName();
            GameProfile profile = server.getProfileCache().get(uuid).orElse(null);
            return profile != null ? profile.getName() : "?";
        } catch (Exception e) {
            return "?";
        }
    }

    public record BanRecord(String hwid, String name, String uuid, String reason, String at) {}
}
