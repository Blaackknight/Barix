package fr.bck.barix.edicts;

import java.util.UUID;

public interface EdictService {
    boolean allows(UUID playerId, String edict);

    void grant(UUID playerId, String edict);

    void revoke(UUID playerId, String edict);

    void addRole(UUID playerId, String role);

    void removeRole(UUID playerId, String role);

    void grantRole(String role, String edict);

    void revokeRole(String role, String edict);

    void reload();

    void save();
}