package fr.bck.barix.audit;

import fr.bck.barix.BarixConstants;
import fr.bck.barix.config.BarixServerConfig;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.zip.GZIPOutputStream;

public final class AuditRotator {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss").withZone(ZoneOffset.UTC);

    // Nouveau: dossier barix dans le monde courant -> ./<world>/barix/
    private static Path worldBarixDir() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // Racine du monde courant, puis "barix"
            return server.getWorldPath(LevelResource.ROOT).resolve("barix");
        }
        // Fallback si serveur indisponible (ex: très tôt au boot)
        return FMLPaths.GAMEDIR.get().resolve("barix");
    }

    private static Path archiveDir() {
        // ./<world>/barix/archive
        return worldBarixDir().resolve("archive");
    }

    public static void compressNow(Path src) {
        try {
            if (!Files.exists(src)) return;
            Files.createDirectories(archiveDir());
            String base = src.getFileName().toString().replace(".jsonl", "");
            Path out = archiveDir().resolve(base + "-" + TS.format(Instant.now()) + ".jsonl.gz");
            try (var in = Files.newInputStream(src); var gz = new GZIPOutputStream(Files.newOutputStream(out))) {
                in.transferTo(gz);
            }
            Files.deleteIfExists(src);
        } catch (Exception ignored) {
        }
    }

    public static void compressTodayIfConfigured() {
        if (!BarixServerConfig.LOG_COMPRESS_ON_STOP.get()) return;
        compressNow(AuditJsonLog.currentFile());
        BarixConstants.log.info("§5AuditRotator", "§aAudit §elog §7compressed on §cserver stop§7: {}", AuditJsonLog.currentFile().getFileName());
    }
}