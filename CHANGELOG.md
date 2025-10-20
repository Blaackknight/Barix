# Journal des modifications

Toutes les modifications notables de ce projet sont documentées ici.

Le format s’inspire de Keep a Changelog et nous suivons SemVer autant que possible.

## [1.0.13-BETA] - 2025-10-20

Cette version apporte une architecture modulaire, une suite AntiCheat intégrée, des réglages plus riches, des alertes Discord améliorées, un moniteur de lag en jeu et l’update checker Forge.

### Ajouté
- API de modules pluggables pour enregistrer des modules externes au runtime (`IBarixModule` / `BarixAPI.registerModule`), avec ordre de démarrage selon les dépendances et redémarrages sûrs.
- Nouvelles commandes de gestion des modules:
  - `/bx modules list|status|reload|restart`
  - `/bx anticheat status|on|off|toggle` (contrôle le module AntiCheat via `modules.anticheat_enabled`).
- Suite AntiCheat avec réglages par détecteur et actions (log|alert|kick):
  - Reach, KillAura, Speed, Fly, InstantBreak, Nuker et Client Detection.
  - Whitelists, bypass par niveau de permission, cooldowns, messages de kick personnalisables.
- Anti‑Xray — configuration élargie:
  - `apply_below_y`, `dimension_whitelist`, `bypass_permission_level`, `bypass_players`.
  - Camouflage: `camo_block`, `camo_deepslate_block`, `deepslate_y`.
  - Masquage/mises à jour: `mask_on_chunkload`, `mask_section_updates`, `max_block_updates_per_chunk`, `extra_blocks`.
  - Compatibilité: `disable_if_mod_present`.
  - Journalisation: `debug`.
- API de compatibilité Anti‑Xray (`IAntiXrayCompat`) avec enregistrement via `BarixAPI.registerAntiXrayCompat`.
- Regions — améliorations confort:
  - Particules de sélection côté serveur et aperçu de boîte (sans mod client), rayon configurable.
  - Baguette de sélection configurable: `regions.wand_enable`, `regions.wand_item`, `regions.wand_sneak_only`, `regions.wand_min_permission_level`.
  - Commandes `/bx region ...` avec suggestions avancées (IDs, flags, UUIDs, coordonnées, valeurs actuelles).
- Moniteur de lag: nouvelle commande `/bx lagscan <seconds>` pour identifier les tâches lourdes par temps moyen.
- Alertes Discord:
  - `logging.discord_username`, `logging.alert_cooldown_seconds`, `logging.alert_ingame_admins_only`.
- Update checker intégré:
  - Ajout de `src/main/resources/update.json`.
  - `updateJSONURL` configuré dans `mods.toml` vers GitHub Raw; workflow GitHub Pages optionnel fourni.
- Métadonnées & docs:
  - Badges ajoutés au README (Version, Minecraft, Forge, Java, License, Platform, CI, GitHub).
  - Nouveau workflow CI (build + validation update.json) et déploiement GitHub Pages pour `update.json`.

### Modifié
- Refactor: les détecteurs AntiCheat sont maintenant gérés par `AntiCheatModule` via `ModuleManager`, pour des cycles start/stop/reload propres et des logs de cycle clairs.
- Logs: catégories colorisées et contexte MDC pour une console plus lisible.
- Expérience config:
  - Les clés lisibles sont listées et proposées par `/bx config list|get|set`.
  - Rechargement ciblé des sous‑systèmes lors des mises à jour (langue et alertes Discord).
- TopLuck: ajout d’évènements (pêche/looting/minage) et modèle de ratio minage (threshold/minBase/window) avec option de séparation par dimension.
- Audit: davantage d’interrupteurs et fenêtre de corrélation clic→ouverture.

### Notes de migration
- Les bascules AntiCheat sont centralisées via `modules.anticheat_enabled` et les clés par détecteur (`reach_detection.*`, `killaura.*`, `speed_detection.*`, `fly_detection.*`, `instant_break.*`, `nuker.*`, `client_detection.*`).
- Si vous utilisiez d’anciens réglages Anti‑Xray, reportez‑vous aux nouvelles clés `antixray.*` décrites dans le README.
- Pour activer la notification d’update, assurez‑vous que `updateJSONURL` dans `mods.toml` pointe vers une URL publique et maintenez `update.json` (buckets + `promos`) synchronisé avec `gradle.properties:mod_version`.

### Obsolète/Supprimé
- Néant.

[1.0.13-BETA]: https://github.com/Blaackknight/Barix/releases
