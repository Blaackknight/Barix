package fr.bck.barix.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hook de compatibilité Anti-Xray pour les autres mods.
 * Un provider peut:
 * - désactiver complètement l'anti-xray de Barix (ex: si un autre mod fournit déjà un anti-xray réseau).
 * - affiner l'application par joueur.
 * - ajouter des cibles (blocs à masquer) supplémentaires.
 * - empêcher Barix de masquer certaines situations (shouldHide = false).
 */
public interface IAntiXrayCompat {
    /**
     * Identifiant du provider (log/diagnostic).
     */
    String id();

    /**
     * Retourner true pour demander la désactivation complète de Barix Anti-Xray.
     * Exemple: si un autre mod anti-xray est actif et incompatible.
     */
    default boolean disableBarixAntiXrayCompletely() { return false; }

    /**
     * Possibilité de refuser l'application pour un joueur (ex: selon dimension/mod state).
     */
    default boolean shouldApplyFor(ServerPlayer sp) { return true; }

    /**
     * Ajouter des cibles supplémentaires (retourner true si ce blockstate doit être masqué).
     */
    default boolean isTargetBlock(BlockState state) { return false; }

    /**
     * Filtrer le masquage: retourner false pour empêcher Barix de masquer ce bloc précis.
     */
    default boolean shouldHide(ServerPlayer sp, BlockPos pos, BlockState state, Level lvl) { return true; }
}

