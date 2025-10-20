package fr.bck.barix.region;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.bck.barix.selection.SelectionManager;
import fr.bck.barix.util.Particles;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class RegionCommands {
    private RegionCommands() {}

    // ---- Suggestion providers ----
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_REGION_IDS = (ctx, builder) -> {
        ServerLevel lvl = ctx.getSource().getLevel();
        String rem = builder.getRemaining().toLowerCase(Locale.ROOT);
        Collection<Region> all = RegionManager.of(lvl).all();
        for (Region r : all) {
            String id = r.getId();
            if (rem.isEmpty() || id.toLowerCase(Locale.ROOT).startsWith(rem)) builder.suggest(id);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_NEW_REGION_ID = (ctx, builder) -> {
        ServerLevel lvl = ctx.getSource().getLevel();
        var manager = RegionManager.of(lvl);
        List<String> props = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String id = "region_" + i;
            if (!manager.exists(id)) props.add(id);
        }
        if (props.isEmpty()) props.add("region_new");
        String rem = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String s : props) if (rem.isEmpty() || s.startsWith(rem)) builder.suggest(s);
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_FLAGS = (ctx, builder) -> {
        String rem = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Flags f : Flags.values()) {
            String n = f.name().toLowerCase(Locale.ROOT);
            if (rem.isEmpty() || n.startsWith(rem)) builder.suggest(n);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_UUIDS = (ctx, builder) -> {
        MinecraftServer srv = ctx.getSource().getServer();
        String rem = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (ServerPlayer sp : srv.getPlayerList().getPlayers()) {
            String s = sp.getUUID().toString();
            if (rem.isEmpty() || s.toLowerCase(Locale.ROOT).startsWith(rem)) builder.suggest(s, Component.literal(sp.getGameProfile().getName()));
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_COORD_X = (ctx, builder) -> {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p != null) builder.suggest(Integer.toString(p.blockPosition().getX()));
        return builder.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_COORD_Y = (ctx, builder) -> {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p != null) builder.suggest(Integer.toString(p.blockPosition().getY()));
        return builder.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_COORD_Z = (ctx, builder) -> {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p != null) builder.suggest(Integer.toString(p.blockPosition().getZ()));
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_REGION_NAME_VALUE = (ctx, builder) -> {
        try {
            String id = ctx.getArgument("id", String.class);
            ServerLevel lvl = ctx.getSource().getLevel();
            Region r = RegionManager.of(lvl).get(id);
            if (r != null) builder.suggest(r.getName());
        } catch (IllegalArgumentException ignored) {}
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PRIORITY_VALUE = (ctx, builder) -> {
        try {
            String id = ctx.getArgument("id", String.class);
            ServerLevel lvl = ctx.getSource().getLevel();
            Region r = RegionManager.of(lvl).get(id);
            if (r != null) builder.suggest(Integer.toString(r.getPriority()));
        } catch (IllegalArgumentException ignored) {}
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("region").requires(src -> src.hasPermission(3))
                .then(Commands.literal("pos1").executes(ctx -> setPos(ctx.getSource(), 1, null))
                        .then(Commands.argument("x", IntegerArgumentType.integer()).suggests(SUGGEST_COORD_X)
                                .then(Commands.argument("y", IntegerArgumentType.integer()).suggests(SUGGEST_COORD_Y)
                                        .then(Commands.argument("z", IntegerArgumentType.integer()).suggests(SUGGEST_COORD_Z)
                                                .executes(ctx -> setPos(ctx.getSource(), 1, new BlockPos(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z"))))))))
                .then(Commands.literal("pos2").executes(ctx -> setPos(ctx.getSource(), 2, null))
                        .then(Commands.argument("x", IntegerArgumentType.integer()).suggests(SUGGEST_COORD_X)
                                .then(Commands.argument("y", IntegerArgumentType.integer()).suggests(SUGGEST_COORD_Y)
                                        .then(Commands.argument("z", IntegerArgumentType.integer()).suggests(SUGGEST_COORD_Z)
                                                .executes(ctx -> setPos(ctx.getSource(), 2, new BlockPos(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z"))))))))
                .then(Commands.literal("create").then(Commands.argument("id", StringArgumentType.word()).suggests(SUGGEST_NEW_REGION_ID).executes(ctx -> create(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("remove").then(Commands.argument("id", StringArgumentType.word()).suggests(SUGGEST_REGION_IDS).executes(ctx -> remove(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("info").then(Commands.argument("id", StringArgumentType.word()).suggests(SUGGEST_REGION_IDS).executes(ctx -> info(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("at").executes(ctx -> at(ctx.getSource(), null))
                        .then(Commands.argument("x", IntegerArgumentType.integer()).suggests(SUGGEST_COORD_X)
                                .then(Commands.argument("y", IntegerArgumentType.integer()).suggests(SUGGEST_COORD_Y)
                                        .then(Commands.argument("z", IntegerArgumentType.integer()).suggests(SUGGEST_COORD_Z)
                                                .executes(ctx -> at(ctx.getSource(), new BlockPos(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z"))))))))
                .then(Commands.literal("priority").then(Commands.argument("id", StringArgumentType.word()).suggests(SUGGEST_REGION_IDS).then(Commands.argument("value", IntegerArgumentType.integer(-1000, 1000)).suggests(SUGGEST_PRIORITY_VALUE).executes(ctx -> prio(ctx.getSource(), StringArgumentType.getString(ctx, "id"), IntegerArgumentType.getInteger(ctx, "value"))))))
                .then(Commands.literal("name").then(Commands.argument("id", StringArgumentType.word()).suggests(SUGGEST_REGION_IDS).then(Commands.argument("value", StringArgumentType.greedyString()).suggests(SUGGEST_REGION_NAME_VALUE).executes(ctx -> name(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "value"))))))
                .then(Commands.literal("setflag").then(Commands.argument("id", StringArgumentType.word()).suggests(SUGGEST_REGION_IDS).then(Commands.argument("flag", StringArgumentType.word()).suggests(SUGGEST_FLAGS).then(Commands.literal("on").executes(ctx -> setFlag(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "flag"), Boolean.TRUE))).then(Commands.literal("off").executes(ctx -> setFlag(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "flag"), Boolean.FALSE))).then(Commands.literal("clear").executes(ctx -> setFlag(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "flag"), null))))))
                .then(Commands.literal("addowner").then(Commands.argument("id", StringArgumentType.word()).suggests(SUGGEST_REGION_IDS).then(Commands.argument("uuid", UuidArgument.uuid()).suggests(SUGGEST_UUIDS).executes(ctx -> addOwner(ctx.getSource(), StringArgumentType.getString(ctx, "id"), UuidArgument.getUuid(ctx, "uuid"))))))
                .then(Commands.literal("delowner").then(Commands.argument("id", StringArgumentType.word()).suggests(SUGGEST_REGION_IDS).then(Commands.argument("uuid", UuidArgument.uuid()).suggests(SUGGEST_UUIDS).executes(ctx -> delOwner(ctx.getSource(), StringArgumentType.getString(ctx, "id"), UuidArgument.getUuid(ctx, "uuid"))))))
                .then(Commands.literal("addmember").then(Commands.argument("id", StringArgumentType.word()).suggests(SUGGEST_REGION_IDS).then(Commands.argument("uuid", UuidArgument.uuid()).suggests(SUGGEST_UUIDS).executes(ctx -> addMember(ctx.getSource(), StringArgumentType.getString(ctx, "id"), UuidArgument.getUuid(ctx, "uuid"))))))
                .then(Commands.literal("delmember").then(Commands.argument("id", StringArgumentType.word()).suggests(SUGGEST_REGION_IDS).then(Commands.argument("uuid", UuidArgument.uuid()).suggests(SUGGEST_UUIDS).executes(ctx -> delMember(ctx.getSource(), StringArgumentType.getString(ctx, "id"), UuidArgument.getUuid(ctx, "uuid"))))));
    }

    private static int setPos(CommandSourceStack src, int which, BlockPos pos) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Commande joueur uniquement")); return 0; }
        // Résoudre la position dans une variable locale, puis construire un message final pour éviter la capture de variables non-finales dans la lambda
        BlockPos resolved = pos;
        if (resolved == null) resolved = SelectionManager.raytraceBlock(p, 120.0);
        if (resolved == null) { src.sendFailure(Component.literal("Aucun bloc visé (regardez un bloc ou fournissez x y z)")); return 0; }
        if (which == 1) SelectionManager.setPos1(p, resolved); else SelectionManager.setPos2(p, resolved);
        final String msg = (which == 1 ? "pos1" : "pos2") + " = " + resolved.getX()+","+resolved.getY()+","+resolved.getZ();
        src.sendSuccess(() -> Component.literal(msg), false);
        // Particules: point et boîte si sélection complète
        Particles.showPos(p, resolved);
        BlockPos a = SelectionManager.getPos1(p), b = SelectionManager.getPos2(p);
        if (a != null && b != null) Particles.showBox(p, a, b);
        return 1;
    }

    private static int create(CommandSourceStack src, String id) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Commande joueur uniquement")); return 0; }
        BlockPos a = SelectionManager.getPos1(p), b = SelectionManager.getPos2(p);
        if (a == null || b == null) { src.sendFailure(Component.literal("Sélection incomplète: utilisez /bx region pos1 et pos2")); return 0; }
        ServerLevel lvl = src.getLevel();
        try {
            Region r = RegionManager.of(lvl).create(id, a, b);
            r.getOwners().add(p.getUUID());
            src.sendSuccess(() -> Component.literal("Région créée: " + id), true);
            // Visualiser la région créée
            Particles.showBox(p, r.getMin(), r.getMax());
            return 1;
        } catch (IllegalArgumentException ex) {
            src.sendFailure(Component.literal(ex.getMessage()));
            return 0;
        }
    }

    private static int remove(CommandSourceStack src, String id) {
        ServerLevel lvl = src.getLevel();
        boolean ok = RegionManager.of(lvl).remove(id);
        if (ok) src.sendSuccess(() -> Component.literal("Région supprimée: " + id), true);
        else src.sendFailure(Component.literal("Région inconnue: " + id));
        return ok ? 1 : 0;
    }

    private static int list(CommandSourceStack src) {
        ServerLevel lvl = src.getLevel();
        var all = RegionManager.of(lvl).all();
        src.sendSuccess(() -> Component.literal("Régions (" + all.size() + "):"), false);
        for (Region r : all) {
            BlockPos mi = r.getMin(), ma = r.getMax();
            src.sendSuccess(() -> Component.literal("- " + r.getId() + " ["+mi.getX()+","+mi.getY()+","+mi.getZ()+"] -> ["+ma.getX()+","+ma.getY()+","+ma.getZ()+"], prio=" + r.getPriority()), false);
        }
        return all.size();
    }

    private static int info(CommandSourceStack src, String id) {
        ServerLevel lvl = src.getLevel();
        Region r = RegionManager.of(lvl).get(id);
        if (r == null) { src.sendFailure(Component.literal("Région inconnue: " + id)); return 0; }
        src.sendSuccess(() -> Component.literal("Info région " + r.getId() + " (\"" + r.getName() + "\"): prio=" + r.getPriority()), false);
        src.sendSuccess(() -> Component.literal("Owners: " + r.getOwners()), false);
        src.sendSuccess(() -> Component.literal("Members: " + r.getMembers()), false);
        StringBuilder sb = new StringBuilder();
        sb.append("Flags:");
        for (Flags f : Flags.values()) {
            if (r.getAllFlags().containsKey(f)) sb.append(" ").append(f.name()).append("=").append(r.getAllFlags().get(f));
        }
        src.sendSuccess(() -> Component.literal(sb.toString()), false);
        // Visualiser la boîte
        ServerPlayer p = src.getPlayer();
        if (p != null) Particles.showBox(p, r.getMin(), r.getMax());
        return 1;
    }

    private static int at(CommandSourceStack src, BlockPos pos) {
        ServerPlayer p = src.getPlayer();
        ServerLevel lvl = src.getLevel();
        // Résoudre la position dans une variable locale, puis construire un message final pour éviter la capture de variables non-finales dans la lambda
        BlockPos resolved = pos;
        if (resolved == null) resolved = p != null ? p.blockPosition() : BlockPos.ZERO;
        var regs = RegionManager.of(lvl).at(resolved);
        final int count = regs.size();
        final String header = "Régions @" + resolved.getX()+","+resolved.getY()+","+resolved.getZ()+": " + count;
        src.sendSuccess(() -> Component.literal(header), false);
        int shown = 0;
        for (Region r : regs) {
            src.sendSuccess(() -> Component.literal("- " + r.getId() + " (prio=" + r.getPriority() + ")"), false);
            if (p != null && shown < 5) { Particles.showBox(p, r.getMin(), r.getMax()); shown++; }
        }
        return count;
    }

    private static int prio(CommandSourceStack src, String id, int v) {
        ServerLevel lvl = src.getLevel();
        try {
            RegionManager.of(lvl).setPriority(id, v);
            src.sendSuccess(() -> Component.literal("Priorité maj pour " + id + ": " + v), true);
            return 1;
        } catch (IllegalArgumentException ex) {
            src.sendFailure(Component.literal(ex.getMessage()));
            return 0;
        }
    }

    private static int name(CommandSourceStack src, String id, String name) {
        ServerLevel lvl = src.getLevel();
        try {
            RegionManager.of(lvl).setName(id, name);
            src.sendSuccess(() -> Component.literal("Nom maj pour " + id + ": \"" + name + "\""), true);
            return 1;
        } catch (IllegalArgumentException ex) {
            src.sendFailure(Component.literal(ex.getMessage()));
            return 0;
        }
    }

    private static int setFlag(CommandSourceStack src, String id, String flagName, Boolean v) {
        ServerLevel lvl = src.getLevel();
        try {
            Flags f = Flags.valueOf(flagName.trim().toUpperCase(Locale.ROOT));
            RegionManager.of(lvl).setFlag(id, f, v);
            src.sendSuccess(() -> Component.literal("Flag " + f + "=" + (v == null ? "<cleared>" : v) + " pour " + id), true);
            return 1;
        } catch (IllegalArgumentException ex) {
            src.sendFailure(Component.literal(ex.getMessage()));
            return 0;
        }
    }

    private static int addOwner(CommandSourceStack src, String id, UUID u) {
        ServerLevel lvl = src.getLevel();
        try {
            boolean ok = RegionManager.of(lvl).addOwner(id, u);
            if (ok) src.sendSuccess(() -> Component.literal("Owner ajouté pour " + id + ": " + u), true);
            else src.sendFailure(Component.literal("Déjà owner: " + u));
            return ok ? 1 : 0;
        } catch (IllegalArgumentException ex) {
            src.sendFailure(Component.literal(ex.getMessage()));
            return 0;
        }
    }

    private static int delOwner(CommandSourceStack src, String id, UUID u) {
        ServerLevel lvl = src.getLevel();
        try {
            boolean ok = RegionManager.of(lvl).removeOwner(id, u);
            if (ok) src.sendSuccess(() -> Component.literal("Owner retiré pour " + id + ": " + u), true);
            else src.sendFailure(Component.literal("Pas owner: " + u));
            return ok ? 1 : 0;
        } catch (IllegalArgumentException ex) {
            src.sendFailure(Component.literal(ex.getMessage()));
            return 0;
        }
    }

    private static int addMember(CommandSourceStack src, String id, UUID u) {
        ServerLevel lvl = src.getLevel();
        try {
            boolean ok = RegionManager.of(lvl).addMember(id, u);
            if (ok) src.sendSuccess(() -> Component.literal("Membre ajouté pour " + id + ": " + u), true);
            else src.sendFailure(Component.literal("Déjà membre: " + u));
            return ok ? 1 : 0;
        } catch (IllegalArgumentException ex) {
            src.sendFailure(Component.literal(ex.getMessage()));
            return 0;
        }
    }

    private static int delMember(CommandSourceStack src, String id, UUID u) {
        ServerLevel lvl = src.getLevel();
        try {
            boolean ok = RegionManager.of(lvl).removeMember(id, u);
            if (ok) src.sendSuccess(() -> Component.literal("Membre retiré pour " + id + ": " + u), true);
            else src.sendFailure(Component.literal("Pas membre: " + u));
            return ok ? 1 : 0;
        } catch (IllegalArgumentException ex) {
            src.sendFailure(Component.literal(ex.getMessage()));
            return 0;
        }
    }
}
