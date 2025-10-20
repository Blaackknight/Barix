package fr.bck.barix.region;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Flags de protection supportés par les régions.
 * Tous les flags sont booléens (true = autorisé, false = interdit) sauf précision ultérieure.
 */
public enum Flags {
    BUILD,          // placer des blocs
    BREAK,          // casser des blocs
    INTERACT,       // clic droit sur blocs
    USE_ITEM,       // clic droit avec item en l'air
    CONTAINERS,     // ouvrir coffres/containers
    PVP,            // attaquer les joueurs
    EXPLOSIONS,     // dégâts d'explosions dans la région
    MOB_GRIEF,      // grief des mobs (enderman, wither...)
    FIRE_SPREAD,    // propagation du feu
    FLUID_FLOW,     // écoulement des fluides
    ENTRY,          // entrée des joueurs (si false, on bloque l'interaction mais pas de tp forcé)
    FALLBACK_ALLOW; // valeur par défaut si aucun flag spécifique ne correspond

    public static Optional<Flags> byName(String s) {
        if (s == null) return Optional.empty();
        String n = s.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (Flags f : values()) {
            if (f.name().equals(n)) return Optional.of(f);
        }
        return Optional.empty();
    }

    public static Map<String, String> allToExampleValues() {
        return Arrays.stream(values())
                .collect(Collectors.toMap(e -> e.name().toLowerCase(Locale.ROOT), e -> "true|false"));
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static String requireValidName(String name) {
        Objects.requireNonNull(name, "flag name");
        if (byName(name).isEmpty()) throw new IllegalArgumentException("Flag inconnu: " + name);
        return name;
    }
}
