package com.iseeu.server;

import com.iseeu.IseeUMod;
import com.iseeu.config.IseeUConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * Admin command tree: {@code /iseeu ...}
 *
 * <ul>
 *   <li>{@code /iseeu reload}               — reload ban list + config</li>
 *   <li>{@code /iseeu ban <player> [reason]} — ban the online player's HWID</li>
 *   <li>{@code /iseeu banhwid <hwid> [reason]} — ban a raw HWID string</li>
 *   <li>{@code /iseeu unban <hwid>}         — lift a HWID ban</li>
 *   <li>{@code /iseeu list [page]}          — paged ban list</li>
 *   <li>{@code /iseeu check <player>}       — show a player's verification state + HWID</li>
 *   <li>{@code /iseeu status}               — show this server's enforcement mode (no op required)</li>
 * </ul>
 */
@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
public final class IseeUCommands {

    private static final int PER_PAGE = 8;

    private IseeUCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(Commands.literal("iseeu")
                // ---- op-only subtree ----
                .then(Commands.literal("reload")
                        .requires(s -> s.hasPermission(2))
                        .executes(IseeUCommands::cmdReload))
                .then(Commands.literal("ban")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> cmdBan(ctx, ""))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> cmdBan(ctx, StringArgumentType.getString(ctx, "reason"))))))
                .then(Commands.literal("banhwid")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("hwid", StringArgumentType.string())
                                .executes(ctx -> cmdBanHwid(ctx, ""))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> cmdBanHwid(ctx, StringArgumentType.getString(ctx, "reason"))))))
                .then(Commands.literal("unban")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("hwid", StringArgumentType.string())
                                .executes(IseeUCommands::cmdUnban)))
                .then(Commands.literal("list")
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> cmdList(ctx, 1))
                        .then(Commands.argument("page", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                .executes(ctx -> cmdList(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "page")))))
                .then(Commands.literal("check")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(IseeUCommands::cmdCheck)))
                // ---- everyone ----
                .then(Commands.literal("status")
                        .executes(IseeUCommands::cmdStatus))
        );
    }

    // ==================================================================
    //  impl
    // ==================================================================

    private static int cmdReload(CommandContext<CommandSourceStack> ctx) {
        BanManager.load();
        ctx.getSource().sendSuccess(() ->
                Component.literal("[IseeU] reloaded. bans=" + BanManager.all().size()
                        + " mode=" + IseeUConfig.ENFORCE_MODE.get()), false);
        return 1;
    }

    private static int cmdBan(CommandContext<CommandSourceStack> ctx, String reason) {
        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(ctx, "player");
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("player not found"));
            return 0;
        }
        String hwid = VerificationState.getHwid(target.getUUID());
        if (hwid == null || hwid.isBlank()) {
            ctx.getSource().sendFailure(Component.literal(target.getName().getString()
                    + " has not completed verification yet (no HWID on record)."));
            return 0;
        }
        String name = target.getName().getString();
        String uuid = target.getUUID().toString();
        BanManager.ban(hwid, name, uuid, reason);
        // Kick them now.
        target.connection.disconnect(Component.literal("[IseeU] banned: ")
                .append(Component.literal(reason.isEmpty() ? "unspecified" : reason)));
        final String fHwid = hwid;
        ctx.getSource().sendSuccess(() -> Component.literal("[IseeU] banned " + name
                + " hwid=" + shortHwid(fHwid)), true);
        return 1;
    }

    private static int cmdBanHwid(CommandContext<CommandSourceStack> ctx, String reason) {
        String hwid = StringArgumentType.getString(ctx, "hwid");
        if (BanManager.isBanned(hwid)) {
            ctx.getSource().sendFailure(Component.literal("already banned: " + shortHwid(hwid)));
            return 0;
        }
        BanManager.ban(hwid, "?", "", reason);
        // If that player is online, kick them.
        var server = ctx.getSource().getServer();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String pHwid = VerificationState.getHwid(p.getUUID());
            if (pHwid != null && pHwid.equalsIgnoreCase(hwid)) {
                p.connection.disconnect(Component.literal("[IseeU] banned: ")
                        .append(Component.literal(reason.isEmpty() ? "unspecified" : reason)));
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal("[IseeU] banned hwid=" + shortHwid(hwid)), true);
        return 1;
    }

    private static int cmdUnban(CommandContext<CommandSourceStack> ctx) {
        String hwid = StringArgumentType.getString(ctx, "hwid");
        boolean ok = BanManager.unban(hwid);
        if (ok) {
            ctx.getSource().sendSuccess(() -> Component.literal("[IseeU] unbanned " + shortHwid(hwid)), true);
        } else {
            ctx.getSource().sendFailure(Component.literal("no ban matched " + shortHwid(hwid)));
        }
        return ok ? 1 : 0;
    }

    private static int cmdList(CommandContext<CommandSourceStack> ctx, int page) {
        List<BanManager.BanRecord> all = BanManager.all();
        if (all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[IseeU] ban list is empty."), false);
            return 1;
        }
        int pages = (all.size() + PER_PAGE - 1) / PER_PAGE;
        if (page > pages) page = pages;
        int from = (page - 1) * PER_PAGE;
        int to = Math.min(from + PER_PAGE, all.size());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[IseeU] bans " + all.size() + " (page " + page + "/" + pages + ")"), false);
        for (int i = from; i < to; i++) {
            BanManager.BanRecord r = all.get(i);
            ctx.getSource().sendSuccess(() -> Component.literal(" - ")
                    .append(Component.literal(r.name()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" " + shortHwid(r.hwid())))
                    .append(Component.literal(" [" + r.reason() + "]").withStyle(ChatFormatting.GRAY)), false);
        }
        return 1;
    }

    private static int cmdCheck(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(ctx, "player");
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("player not found"));
            return 0;
        }
        VerificationState.Entry e = VerificationState.get(target.getUUID());
        if (e == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(target.getName().getString()
                    + ": no verification record (not yet challenged)."), false);
            return 1;
        }
        String hwid = e.hwid != null ? e.hwid : "(none)";
        ctx.getSource().sendSuccess(() -> Component.literal(target.getName().getString()
                + " status=" + e.status + " hwid=" + shortHwid(hwid)
                + (BanManager.isBanned(hwid) ? " [BANNED]" : "")), false);
        return 1;
    }

    private static int cmdStatus(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[IseeU] mode=" + IseeUConfig.ENFORCE_MODE.get()
                + " requireHwid=" + IseeUConfig.REQUIRE_HARDWARE_FINGERPRINT.get()
                + " bans=" + BanManager.all().size()
                + " pending=" + VerificationState.pendingCount()), false);
        return 1;
    }

    private static String shortHwid(String hwid) {
        if (hwid == null || hwid.length() < 12) return hwid == null ? "n/a" : hwid;
        return hwid.substring(0, 8) + "…" + hwid.substring(hwid.length() - 4);
    }
}
