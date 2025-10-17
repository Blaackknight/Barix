package fr.bck.barix.edicts;

import fr.bck.barix.edicts.store.JsonEdictStore;

import java.util.*;

public class InMemoryEdictService implements EdictService {
    private final Map<UUID, Set<String>> userEdicts = new HashMap<>();
    private final Map<String, Set<String>> roleEdicts = new HashMap<>();
    private final Map<UUID, Set<String>> userRoles = new HashMap<>();
    private final JsonEdictStore store;

    public InMemoryEdictService(JsonEdictStore store) {
        this.store = store;
        store.loadInto(userEdicts, roleEdicts, userRoles);
    }

    @Override
    public boolean allows(UUID playerId, String edict) {
        Set<String> up = userEdicts.getOrDefault(playerId, Collections.emptySet());
        if (up.contains(edict) || up.contains("*")) return true;
        for (String role : userRoles.getOrDefault(playerId, Collections.emptySet())) {
            Set<String> rp = roleEdicts.getOrDefault(role, Collections.emptySet());
            if (rp.contains(edict) || rp.contains("*")) return true;
        }
        return false;
    }

    @Override
    public void grant(UUID playerId, String edict) {
        userEdicts.computeIfAbsent(playerId, k -> new HashSet<>()).add(edict);
    }

    @Override
    public void revoke(UUID playerId, String edict) {
        userEdicts.computeIfAbsent(playerId, k -> new HashSet<>()).remove(edict);
    }

    @Override
    public void addRole(UUID playerId, String role) {
        userRoles.computeIfAbsent(playerId, k -> new HashSet<>()).add(role.toLowerCase(Locale.ROOT));
    }

    @Override
    public void removeRole(UUID playerId, String role) {
        userRoles.computeIfAbsent(playerId, k -> new HashSet<>()).remove(role.toLowerCase(Locale.ROOT));
    }

    @Override
    public void grantRole(String role, String edict) {
        roleEdicts.computeIfAbsent(role.toLowerCase(Locale.ROOT), k -> new HashSet<>()).add(edict);
    }

    @Override
    public void revokeRole(String role, String edict) {
        roleEdicts.computeIfAbsent(role.toLowerCase(Locale.ROOT), k -> new HashSet<>()).remove(edict);
    }

    @Override
    public void reload() {
        userEdicts.clear();
        roleEdicts.clear();
        userRoles.clear();
        store.loadInto(userEdicts, roleEdicts, userRoles);
    }

    @Override
    public void save() {
        store.saveFrom(userEdicts, roleEdicts, userRoles);
    }
}