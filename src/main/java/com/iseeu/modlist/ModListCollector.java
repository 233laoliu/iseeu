package com.iseeu.modlist;

import com.iseeu.util.HashUtils;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.moddiscovery.ModInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Collects the running mod list on the client and produces:
 * <ul>
 *   <li>{@code entries()} — the sorted (modId@version) pairs sent to the server.</li>
 *   <li>{@code hash()} — SHA-256 of those pairs, for cheap comparison.</li>
 * </ul>
 *
 * <p>Sorting is lexicographic on modId so the hash is stable regardless of load order.
 */
public final class ModListCollector {

    private ModListCollector() {}

    /** @return sorted list of "modId@version" strings for every loaded mod. */
    public static List<String> entries() {
        List<ModInfo> mods = ModList.get().getMods();
        List<String> out = new ArrayList<>(mods.size());
        for (ModInfo info : mods) {
            // Skip built-in / invisible system mods (neoforge itself is usually wanted though).
            String id = info.getModId();
            String ver = info.getVersion().toString();
            out.add(id + "@" + ver);
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    public static List<String> modIds() {
        List<ModInfo> mods = ModList.get().getMods();
        List<String> out = new ArrayList<>(mods.size());
        for (ModInfo info : mods) out.add(info.getModId());
        out.sort(Comparator.naturalOrder());
        return out;
    }

    /** @return SHA-256 of {@code "id1@v1;id2@v2;..."} over the sorted list. */
    public static String hash() {
        return HashUtils.sha256Hex(String.join(";", entries()));
    }

    /**
     * When the integration pack changes its mod set, the operator can run
     * {@code /iseeu hashtest} server-side (after joining with a known-good client) to capture
     * the current hash and paste it into {@code mod_list.required_hash}.
     */
    public static String describe() {
        StringBuilder sb = new StringBuilder();
        for (String e : entries()) sb.append(e).append('\n');
        sb.append("hash = ").append(hash());
        return sb.toString();
    }
}
