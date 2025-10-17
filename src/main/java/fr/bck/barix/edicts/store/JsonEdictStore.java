package fr.bck.barix.edicts.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.bck.barix.BarixConstants;
import fr.bck.barix.lang.Lang;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class JsonEdictStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;

    public JsonEdictStore() {
        Path dir = FMLPaths.CONFIGDIR.get().resolve("barix").resolve("edicts");
        this.file = dir.resolve("edicts.json");
        try {
            Files.createDirectories(dir);
            if (Files.notExists(file)) {
                saveFrom(new HashMap<>(), new HashMap<>(), new HashMap<>());
            }
        } catch (IOException e) {
            BarixConstants.log.error("§aEdicts", Lang.tr("barix.edicts.error.init", null));
            throw new RuntimeException("Failed to init edicts store", e);
        }
    }

    public void loadInto(Map<UUID, Set<String>> userEdicts, Map<String, Set<String>> roleEdicts, Map<UUID, Set<String>> userRoles) {
        try (Reader r = Files.newBufferedReader(file)) {
            Data data = GSON.fromJson(r, Data.class);
            if (data == null) data = new Data();

            userEdicts.clear();
            for (Map.Entry<String, Set<String>> e : data.users.entrySet()) {
                userEdicts.put(UUID.fromString(e.getKey()), new HashSet<>(e.getValue()));
            }

            roleEdicts.clear();
            for (Map.Entry<String, Set<String>> e : data.roles.entrySet()) {
                roleEdicts.put(e.getKey(), new HashSet<>(e.getValue()));
            }

            userRoles.clear();
            for (Map.Entry<String, Set<String>> e : data.userRoles.entrySet()) {
                userRoles.put(UUID.fromString(e.getKey()), new HashSet<>(e.getValue()));
            }
        } catch (NoSuchFileException ignored) {
            // first run
        } catch (IOException ex) {
            BarixConstants.log.error("§aEdicts", Lang.tr("barix.edicts.error.read", null));
            throw new RuntimeException("Failed to read edicts.json", ex);
        }
    }

    public void saveFrom(Map<UUID, Set<String>> userEdicts, Map<String, Set<String>> roleEdicts, Map<UUID, Set<String>> userRoles) {
        Data data = new Data();
        for (Map.Entry<UUID, Set<String>> e : userEdicts.entrySet()) {
            data.users.put(e.getKey().toString(), new TreeSet<>(e.getValue()));
        }
        for (Map.Entry<String, Set<String>> e : roleEdicts.entrySet()) {
            data.roles.put(e.getKey(), new TreeSet<>(e.getValue()));
        }
        for (Map.Entry<UUID, Set<String>> e : userRoles.entrySet()) {
            data.userRoles.put(e.getKey().toString(), new TreeSet<>(e.getValue()));
        }
        try (Writer w = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            GSON.toJson(data, w);
        } catch (IOException ex) {
            BarixConstants.log.error("§aEdicts", Lang.tr("barix.edicts.error.write", null));
            throw new RuntimeException("Failed to write edicts.json", ex);
        }
    }

    static class Data {
        Map<String, Set<String>> users = new HashMap<>();
        Map<String, Set<String>> roles = new HashMap<>();
        Map<String, Set<String>> userRoles = new HashMap<>();
    }
}