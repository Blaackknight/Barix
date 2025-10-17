package fr.bck.barix.audit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;

public final class AuditQuery {
    private static final Gson G = new Gson();

    public static List<JsonObject> find(String uuidOrNull, String blockIdOrNull, String eventOrNull, Instant fromOrNull, Instant toOrNull, int limitNewest) {
        try {
            Path cfg = FMLPaths.CONFIGDIR.get().resolve("barix");
            Path auditDir = cfg.resolve("audit");
            Path archiveDir = cfg.resolve("archive");
            List<Path> files = new ArrayList<>();

            if (Files.isDirectory(auditDir)) try (var s = Files.list(auditDir)) {
                s.forEach(files::add);
            }
            if (Files.isDirectory(archiveDir)) try (var s = Files.list(archiveDir)) {
                s.forEach(files::add);
            }

            // derniers fichiers d'abord
            files.sort(Comparator.comparing(Path::getFileName).reversed());

            Predicate<JsonObject> pred = o -> {
                if (uuidOrNull != null) {
                    var pl = o.getAsJsonObject("player");
                    if (pl == null || !uuidOrNull.equalsIgnoreCase(pl.get("uuid").getAsString())) return false;
                }
                if (blockIdOrNull != null) {
                    var b = o.get("block");
                    if (b == null || !blockIdOrNull.equalsIgnoreCase(b.getAsString())) return false;
                }
                if (eventOrNull != null) {
                    var ev = o.get("event");
                    if (ev == null || !eventOrNull.equalsIgnoreCase(ev.getAsString())) return false;
                }
                if (fromOrNull != null || toOrNull != null) {
                    Instant ts = Instant.parse(o.get("ts").getAsString());
                    if (fromOrNull != null && ts.isBefore(fromOrNull)) return false;
                    if (toOrNull != null && ts.isAfter(toOrNull)) return false;
                }
                return true;
            };

            List<JsonObject> out = new ArrayList<>(Math.max(16, limitNewest));
            for (Path p : files) {
                if (out.size() >= limitNewest) break;
                readFile(p, line -> {
                    JsonObject o = G.fromJson(line, JsonObject.class);
                    if (pred.test(o)) {
                        out.add(o);
                        return out.size() < limitNewest;
                    }
                    return true;
                });
            }
            // tri du plus récent au plus ancien déjà garanti par ordre parcours + append
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    // Lecture ligne à ligne; stopReader.accept(line) retourne false pour stopper
    private static void readFile(Path p, java.util.function.Function<String, Boolean> stopReader) throws IOException {
        boolean gz = p.getFileName().toString().endsWith(".gz");
        InputStream in = gz ? new GZIPInputStream(Files.newInputStream(p)) : Files.newInputStream(p);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!stopReader.apply(line)) break;
            }
        }
    }
}