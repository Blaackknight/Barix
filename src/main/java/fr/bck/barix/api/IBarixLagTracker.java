package fr.bck.barix.api;


import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;

public interface IBarixLagTracker {

    /**
     * Enregistre manuellement un temps d'exécution pour un élément spécifique.
     */
    void record(ResourceLocation id, long nanos, ServerLevel world, String type);

    /**
     * Retourne la moyenne d'exécution (en ms) d'un élément.
     */
    double getAverageMs(ResourceLocation id);

    /**
     * Retourne la map des stats internes.
     * Clé = ResourceLocation identifiant la source (monde / block / entity / ...).
     * Valeur = objet interne contenant au moins les champs `totalNanos` (long) et `count` (int)
     * (ou une structure compatible).
     */
    Map<ResourceLocation, ?> getStatsMap();

    /**
     * Réinitialise les statistiques.
     */
    void reset();
}