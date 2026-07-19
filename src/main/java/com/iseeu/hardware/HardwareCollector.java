package com.iseeu.hardware;

import com.iseeu.IseeUMod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Collects raw hardware identifiers on the client side.
 *
 * <p>Side safety: this class never references any Minecraft / client-only type, so it is safe
 * to load on a dedicated server (it simply won't be called there — see
 * {@link com.iseeu.network.ClientNetworkHandler}).
 *
 * <p>Privacy note: collected identifiers are NEVER sent in plaintext. They are concatenated,
 * salted, and reduced to a single SHA-256 hex digest by {@link HardwareFingerprint} before
 * any bytes leave the client.
 */
public final class HardwareCollector {

    /** Vendor prefixes of common virtual NICs — skipped when picking a MAC. */
    private static final String[] VIRTUAL_NIC_PREFIXES = {
            "docker", "veth", "virbr", "vboxnet", "vmnet", "tap", "tun",
            "isatap", "teredo", "lo", "bond", "br-", "lxc"
    };

    private static final Pattern NON_HEX = Pattern.compile("[^0-9A-Fa-f]");

    private HardwareCollector() {}

    // ------------------------------------------------------------------
    //  CPU
    // ------------------------------------------------------------------

    public static String readCpuId() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                // wmic is deprecated on very new Windows; fall back to PowerShell CIM.
                String wmic = runOnce("wmic", "cpu", "get", "ProcessorId");
                String cleaned = firstHexishLine(wmic);
                if (cleaned != null) return cleaned;
                String ps = runOnce("powershell",
                        "-NoProfile", "-Command",
                        "(Get-CimInstance Win32_Processor).ProcessorId");
                return firstHexishLine(ps);
            }
            if (os.contains("mac") || os.contains("darwin")) {
                String out = runOnce("sh", "-c",
                        "ioreg -rd1 -c IOPlatformDevice | grep -i 'cpu-id\\|IOPlatformUUID' | head -n2");
                return firstHexishLine(out);
            }
            // Linux — /proc/cpuinfo doesn't contain a serial number, fall back to mainboard UUID.
            String cpuLine = firstHexishLine(readFirstLine("/proc/cpuinfo"));
            if (cpuLine != null && !cpuLine.equals("cpu-unknown")) return cpuLine;
            // Fall back to DMI product UUID (mainboard-level unique ID).
            String dmi = readFirstLine("/sys/class/dmi/id/product_uuid");
            return dmi != null ? dmi.trim() : "cpu-unknown";
        } catch (Exception e) {
            IseeUMod.LOGGER.debug("[IseeU] CPU id read failed: {}", e.toString());
            return "cpu-unknown";
        }
    }

    // ------------------------------------------------------------------
    //  Disk
    // ------------------------------------------------------------------

    public static String readDiskSerial() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                String wmic = runOnce("wmic", "diskdrive", "get", "SerialNumber");
                String cleaned = firstNonEmptyLine(wmic);
                if (cleaned != null) return cleaned.trim().toUpperCase(Locale.ROOT);
                String ps = runOnce("powershell",
                        "-NoProfile", "-Command",
                        "(Get-CimInstance Win32_DiskDrive | Select-Object -First 1).SerialNumber");
                return orUnknown(firstNonEmptyLine(ps)).trim().toUpperCase(Locale.ROOT);
            }
            if (os.contains("mac") || os.contains("darwin")) {
                String out = runOnce("sh", "-c",
                        "diskutil info /dev/disk0 | awk '/Volume UUID/ || /Serial/ {print $NF}'");
                return orUnknown(firstNonEmptyLine(out)).trim().toUpperCase(Locale.ROOT);
            }
            // Linux: try /sys/block/{dev}/device/serial for the first non-loop device.
            return orUnknown(firstLinuxDiskSerial()).toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            IseeUMod.LOGGER.debug("[IseeU] disk serial read failed: {}", e.toString());
            return "disk-unknown";
        }
    }

    // ------------------------------------------------------------------
    //  MAC
    // ------------------------------------------------------------------

    /** @return the first physical, non-virtual MAC address, upper-cased without separators. */
    public static String readPrimaryMac() {
        try {
            List<String> all = new ArrayList<>();
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                if (ni == null) continue;
                if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) continue;
                String name = ni.getName();
                if (name == null) continue;
                String lower = name.toLowerCase(Locale.ROOT);
                if (isVirtualNic(lower)) continue;
                byte[] mac = ni.getHardwareAddress();
                if (mac == null || mac.length == 0) continue;
                all.add(bytesToMac(mac));
            }
            if (all.isEmpty()) return "mac-unknown";
            Collections.sort(all); // deterministic
            return all.get(0);
        } catch (Exception e) {
            IseeUMod.LOGGER.debug("[IseeU] MAC read failed: {}", e.toString());
            return "mac-unknown";
        }
    }

    // ------------------------------------------------------------------
    //  helpers
    // ------------------------------------------------------------------

    private static String runOnce(String... cmd) throws IOException {
        Process p = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        // Timeout — subprocess must NOT hang the network thread indefinitely.
        try {
            if (!p.waitFor(15, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("subprocess timed out: " + String.join(" ", cmd));
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new IOException("subprocess interrupted: " + String.join(" ", cmd));
        }
        // Non-zero exit code means the command failed — output may be garbage.
        if (p.exitValue() != 0) {
            throw new IOException("subprocess exited " + p.exitValue() + ": " + String.join(" ", cmd));
        }
        return sb.toString();
    }

    private static String readFirstLine(String path) throws IOException {
        Path p = Paths.get(path);
        if (!Files.exists(p)) return "";
        try (var lines = Files.lines(p, StandardCharsets.UTF_8)) {
            return lines.findFirst().orElse("");
        }
    }

    private static String firstLinuxDiskSerial() throws IOException {
        Path blockDir = Paths.get("/sys/block");
        if (!Files.exists(blockDir)) return "";
        try (var ds = Files.list(blockDir)) {
            var it = ds.iterator();
            while (it.hasNext()) {
                Path dev = it.next();
                String name = dev.getFileName().toString();
                if (name.startsWith("loop") || name.startsWith("ram")
                        || name.startsWith("sr") || name.startsWith("md")) continue;
                Path serialFile = dev.resolve("device").resolve("serial");
                if (Files.exists(serialFile)) {
                    String s = Files.readString(serialFile).trim();
                    if (!s.isEmpty()) return s;
                }
            }
        }
        return "";
    }

    private static boolean isVirtualNic(String lowerName) {
        for (String p : VIRTUAL_NIC_PREFIXES) {
            if (lowerName.startsWith(p)) return true;
        }
        return false;
    }

    private static String bytesToMac(byte[] mac) {
        StringBuilder sb = new StringBuilder(mac.length * 2);
        for (byte b : mac) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /** @return the first line that looks like a hex / serial token, after stripping spaces. */
    private static String firstHexishLine(String out) {
        return firstNonEmptyLineFiltered(out, true);
    }

    private static String firstNonEmptyLine(String out) {
        return firstNonEmptyLineFiltered(out, false);
    }

    private static String firstNonEmptyLineFiltered(String out, boolean hexOnly) {
        if (out == null) return null;
        for (String raw : out.split("\\r?\\n")) {
            if (raw == null) continue;
            // Drop column headers like "ProcessorId" that wmic prints.
            if (raw.equalsIgnoreCase("ProcessorId")) continue;
            if (raw.equalsIgnoreCase("SerialNumber")) continue;
            String t = raw.trim();
            if (t.isEmpty()) continue;
            if (hexOnly) {
                String stripped = NON_HEX.matcher(t).replaceAll("");
                if (stripped.length() >= 6) return stripped.toUpperCase(Locale.ROOT);
            } else {
                return t;
            }
        }
        return null;
    }

    private static String orUnknown(String s) {
        return s == null ? "disk-unknown" : s;
    }
}
