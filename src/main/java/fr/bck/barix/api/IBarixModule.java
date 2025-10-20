package fr.bck.barix.api;

import java.util.List;

/**
 * Interface publique pour ajouter des modules tiers à Barix via l'API.
 * Un module est identifié par un id stable, peut déclarer des dépendances (ids d'autres modules),
 * et expose deux callbacks de cycle de vie: start() et stop().
 */
public interface IBarixModule {
    /**
     * Identifiant unique et stable du module. Utilisé pour l'ordre de démarrage et les dépendances.
     */
    String id();

    /**
     * Liste des ids de modules requis. Ceux-ci seront démarrés avant ce module.
     */
    default List<String> dependsOn() { return List.of(); }

    /**
     * Appelé au démarrage des modules (après que toutes les dépendances sont démarrées).
     */
    void start();

    /**
     * Appelé à l'arrêt des modules (avant que les dépendances soient arrêtées).
     */
    void stop();
}

