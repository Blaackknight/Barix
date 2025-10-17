package fr.bck.barix.edicts.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.bck.barix.BarixConstants;
import fr.bck.barix.edicts.EdictService;
import fr.bck.barix.edicts.Edicts;
import fr.bck.barix.lang.Lang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class EdictsCommand {
    private EdictsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        EdictService svc = Edicts.service();

        dispatcher.register(Commands.literal("edict").requires(src -> src.hasPermission(4)).then(Commands.literal("grant").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("edict", StringArgumentType.word()).executes(ctx -> {
            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
            String ed = StringArgumentType.getString(ctx, "edict");
            svc.grant(p.getUUID(), ed);
            ctx.getSource().sendSuccess(() -> msg("barix.edicts.grant.user", ctx.getSource(), ed, p.getGameProfile().getName()), true);
            BarixConstants.log.info("§aEdicts", Lang.tr("barix.edicts.grant.user", null, ed, p.getGameProfile().getName()));
            return 1;
        })))).then(Commands.literal("revoke").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("edict", StringArgumentType.word()).executes(ctx -> {
            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
            String ed = StringArgumentType.getString(ctx, "edict");
            svc.revoke(p.getUUID(), ed);
            ctx.getSource().sendSuccess(() -> msg("barix.edicts.revoke.user", ctx.getSource(), ed, p.getGameProfile().getName()), true);
            BarixConstants.log.info("§aEdicts", Lang.tr("barix.edicts.revoke.user", null, ed, p.getGameProfile().getName()));
            return 1;
        })))).then(Commands.literal("check").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("edict", StringArgumentType.word()).executes(ctx -> {
            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
            String ed = StringArgumentType.getString(ctx, "edict");
            boolean ok = svc.allows(p.getUUID(), ed);
            ctx.getSource().sendSuccess(() -> msg("barix.edicts.check.user", ctx.getSource(), ed, p.getGameProfile().getName(), ok), false);
            return ok ? 1 : 0;
        })))).then(Commands.literal("role").then(Commands.literal("grant").then(Commands.argument("role", StringArgumentType.word()).then(Commands.argument("edict", StringArgumentType.word()).executes(ctx -> {
            String role = StringArgumentType.getString(ctx, "role");
            String ed = StringArgumentType.getString(ctx, "edict");
            svc.grantRole(role, ed);
            ctx.getSource().sendSuccess(() -> msg("barix.edicts.role.grant", ctx.getSource(), ed, role), true);
            BarixConstants.log.info("§aEdicts", Lang.tr("barix.edicts.role.grant", null, ed, role));
            return 1;
        })))).then(Commands.literal("revoke").then(Commands.argument("role", StringArgumentType.word()).then(Commands.argument("edict", StringArgumentType.word()).executes(ctx -> {
            String role = StringArgumentType.getString(ctx, "role");
            String ed = StringArgumentType.getString(ctx, "edict");
            svc.revokeRole(role, ed);
            ctx.getSource().sendSuccess(() -> msg("barix.edicts.role.revoke", ctx.getSource(), ed, role), true);
            BarixConstants.log.info("§aEdicts", Lang.tr("barix.edicts.role.revoke", null, ed, role));
            return 1;
        })))).then(Commands.literal("add").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("role", StringArgumentType.word()).executes(ctx -> {
            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
            String role = StringArgumentType.getString(ctx, "role");
            svc.addRole(p.getUUID(), role);
            ctx.getSource().sendSuccess(() -> msg("barix.edicts.role.add", ctx.getSource(), role, p.getGameProfile().getName()), true);
            BarixConstants.log.info("§aEdicts", Lang.tr("barix.edicts.role.add", null, role, p.getGameProfile().getName()));
            return 1;
        })))).then(Commands.literal("remove").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("role", StringArgumentType.word()).executes(ctx -> {
            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
            String role = StringArgumentType.getString(ctx, "role");
            svc.removeRole(p.getUUID(), role);
            ctx.getSource().sendSuccess(() -> msg("barix.edicts.role.remove", ctx.getSource(), role, p.getGameProfile().getName()), true);
            BarixConstants.log.info("§aEdicts", Lang.tr("barix.edicts.role.remove", null, role, p.getGameProfile().getName()));
            return 1;
        }))))).then(Commands.literal("reload").executes(ctx -> {
            svc.reload();
            ctx.getSource().sendSuccess(() -> msg("barix.edicts.reloaded", ctx.getSource()), true);
            BarixConstants.log.info("§aEdicts", Lang.tr("barix.edicts.reloaded", null));
            return 1;
        })).then(Commands.literal("save").executes(ctx -> {
            svc.save();
            ctx.getSource().sendSuccess(() -> msg("barix.edicts.saved", ctx.getSource()), true);
            BarixConstants.log.info("§aEdicts", Lang.tr("barix.edicts.saved", null));
            return 1;
        })));
    }

    private static Component msg(String key, CommandSourceStack src, Object... args) {
        if (src.getEntity() instanceof ServerPlayer p) {
            return Lang.c(key, p, args);
        }
        return Component.literal(Lang.tr(key, null, args));
    }
}