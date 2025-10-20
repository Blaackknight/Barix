# Barix

[![CI](https://github.com/Blaackknight/Barix/actions/workflows/ci.yml/badge.svg)](https://github.com/Blaackknight/Barix/actions/workflows/ci.yml) [![Mod Version](https://img.shields.io/badge/version-1.0.13--BETA-blue)](https://github.com/Blaackknight/Barix/releases) [![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62B47A?logo=minecraft)](#) [![Forge](https://img.shields.io/badge/Forge-47.4.8-orange)](https://files.minecraftforge.net/net/minecraftforge/forge/) [![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk)](https://adoptium.net/temurin/releases/?version=17) [![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE) [![Platform](https://img.shields.io/badge/Platform-Server%20Only-8A2BE2)](#) [![GitHub](https://img.shields.io/badge/GitHub-Blaackknight%2FBarix-181717?logo=github)](https://github.com/Blaackknight/Barix)

[➡️ Changelog](CHANGELOG.md)

A robust server-side moderation and protection toolkit for Minecraft Forge 1.20.1.  
Barix delivers actionable auditing, region protection, anti-xray obfuscation, Discord alerts, and a lightweight lag monitor—built for clarity, security, and performance.

- Game: Minecraft 1.20.1
- Loader: Forge 47.x
- Mod ID: `barix`
- License: MIT

## Key Features

- Audit & Forensics
  - JSON-based audit logs for player actions and key server events.
  - Queryable audit history (by player, action, block), with automatic log rotation.
  - Clean, server-friendly logs designed for long-term retention.

- Region Protection
  - Create and manage named regions with flexible flags (allow/deny or value-based).
  - Enter/leave tracking and optional admin visualization.
  - Server-side selection particles (no client mod required) for pos1/pos2 and region boxes.
  - Advanced command auto-completion for regions (IDs, flags, UUIDs, coordinates, current name/priority).
  - Configurable selection wand (item-based pos1/pos2).

- Anti-Xray (Packet-Based)
  - Server-side chunk obfuscation (masking) to thwart basic xray clients.
  - Packet transform/rebuild pipeline to protect ores while keeping normal gameplay stable.
  - Fully data-driven ore lists via tags: `data/barix/tags/blocks/anti_xray_ores.json`.
  - New: rich config (bypass by permission/players, dimension whitelist, depth-based camo, section updates masking, per-chunk update caps, extra target blocks, compat auto-disable).

- Discord Alerts
  - Webhook integration for high-signal alerts (e.g., suspicious activity, admin events).
  - Simple one-URL setup; messages are structured and rate-friendly.
  - New: username override, anti-spam cooldown, and in-game admin-only delivery toggle.

- Live Lag Monitor
  - Lightweight, tick-focused monitoring to spot short spikes.
  - Optional tracking API for other mods or scripts.

- Admin UX and API
  - Server commands for all common tasks; optional admin screen on the client if installed.
  - A small public API (`BarixAPI`, `IBarixLagTracker`) to integrate or observe from other mods.

- Pluggable Modules API (New)
  - Register custom modules at runtime via `BarixAPI.registerModule(IBarixModule)`.
  - Dependency-aware start order (`dependsOn()`), safe restarts on registration/unregistration.
  - Per-module lifecycle logs with clear categories: `Modules/<moduleId>`.

- AntiCheat Suite (New)
  - Built-in detectors: Reach, KillAura, Speed, Fly, InstantBreak, Nuker, and Client Detection.
  - Per-detector tuning (windows, thresholds, buffers), whitelists, and permission-level bypass.
  - Cooldowns and action policy per detector (log | alert | kick) with customizable kick messages.
  - Discord alert integration and in-game admin-only notices.

## Installation

Server (required):
1. Download the Barix jar for your Minecraft/Forge version.
2. Place it in the server `mods/` folder.
3. Start the server once to generate configs.

Client (optional):
- Not required for normal play.
- Selection particles and region box previews are server-driven and visible to vanilla clients.
- If installed on client connecting to a Barix server, you may also get optional admin UI.

## Configuration

- Forge config directory: `config/barix/`
  - Audit logs: `config/barix/audit/` (JSON logs with rotation).
  - Discord: set your webhook URL in the Barix config (see `config/barix/*.toml` entries).  
  - Anti-xray ores: edit `data/barix/tags/blocks/anti_xray_ores.json` in a datapack or resource override to adjust which blocks get masked.

General notes:
- Changes to anti-xray targets are tag-driven. Keep lists small and relevant to limit false positives.
- Discord webhooks are optional; leaving the URL empty disables alerts.

### Core settings

- `core.enabled` (bool): master switch for Barix subsystems.
- `core.debug` (bool): verbose global logs (developer-oriented; may be noisy).
- `core.locale` (string, e.g. `fr_fr`, `en_us`): server language for messages and console output.
  - Apply language changes without restart: `/bx lang reload`.

### Regions configuration (new)

- Visuals
  - `regions.selection_particles` (bool, default: true): enable/disable server-side particles for pos1/pos2 and region boxes.
  - `regions.preview_radius` (int 4..128, default: 16): how far the box preview can be seen around players.
  - `regions.debug` (bool, default: false): verbose logs for region operations.
- Selection wand (item-based pos1/pos2)
  - `regions.wand_enable` (bool, default: true): enable the wand.
  - `regions.wand_item` (string ResourceLocation, default: `minecraft:stick`): which item acts as the wand (e.g. `minecraft:blaze_rod`).
  - `regions.wand_sneak_only` (bool, default: false): if true, wand actions require sneaking.
  - `regions.wand_min_permission_level` (int 0..4, default: 3): minimal permission level required to use the wand.

Quick setup examples (in-game):

```mcfunction
/bx config set regions.wand_item minecraft:blaze_rod
/bx config set regions.wand_sneak_only true
/bx config set regions.selection_particles true
/bx config save
```

### Anti-Xray configuration (expanded)

- Core
  - `antixray.enable` (bool): master switch.
  - `antixray.hide_surface` (bool): also hide ores that touch air.
  - `antixray.reveal_radius` (int): reveal radius around players.
- Scope & bypass
  - `antixray.apply_below_y` (int): apply only below this Y.
  - `antixray.dimension_whitelist` (CSV ResourceLocations): limit to listed dimensions.
  - `antixray.bypass_permission_level` (int -1..4): ignore for players at/above level.
  - `antixray.bypass_players` (CSV names): ignore for listed players.
- Camouflage & depth
  - `antixray.camo_block` / `antixray.camo_deepslate_block` (IDs): blocks used to mask targets.
  - `antixray.deepslate_y` (int): switch to deepslate camo below this Y.
- Updates & performance
  - `antixray.mask_on_chunkload` (bool): mask right after sending chunk.
  - `antixray.mask_section_updates` (bool): mask section updates too.
  - `antixray.max_block_updates_per_chunk` (int): per-chunk cap to avoid spam.
  - `antixray.extra_blocks` (CSV block IDs): extra blocks to hide in addition to the ore tag.
  - `antixray.disable_if_mod_present` (CSV modids): automatically disable Barix Anti-Xray if any is loaded.
  - `antixray.debug` (bool): verbose logs.

### Logging & Alerts (expanded)

- Files
  - `logging.jsonl` (bool): write JSONL logs.
  - `logging.roll_daily` (bool): daily rotation.
  - `logging.max_bytes` (long): size-based rotation threshold.
  - `logging.compress_on_stop` (bool): gzip rotation on server stop.
- Discord
  - `logging.discord_webhook` (string): webhook URL; empty to disable.
  - `logging.discord_username` (string): display name used by webhook.
  - `logging.alert_cooldown_seconds` (int): anti-spam cooldown for identical alerts.
  - `logging.alert_ingame_admins_only` (bool): show in-game alerts only to admins.

### Permissions bridge

- `permissions.default_level` (int 0..4, default 3): default required level for `/bx` sensitive ops.
- `permissions.op_is_admin` (bool): treat server operators as admins by default.

### Audit (expanded)

- Master switch: `audit.enable_all`.
- Per-event toggles: `audit.log_open|close|place|break|login|logout|respawn|dim_change|pickup|drop|craft|smelt|explosion|block_interact|anvil|chat`.
- Timing: `audit.click_open_window_ms` window to correlate click→open.

### TopLuck (expanded)

- Enable: `topluck.enabled`.
- Base threshold/cooldown and broadcast/log toggles: `topluck.threshold`, `topluck.cooldown_seconds`, `topluck.notify_admins_only`, `topluck.broadcast`, `topluck.log_console`.
- Event hooks: `topluck.event_fishing|event_looting|event_mining` and tunables `topluck.fishing_min_treasure`, `topluck.looting_min_level`, `topluck.events_scale`.
- Mining ratio model: `topluck.ratio_threshold`, `topluck.ratio_min_base`, `topluck.ratio_window_seconds`, `topluck.ratio_split_dimensions`.
- Debug: `topluck.debug`.

## AntiCheat Suite — detectors and tuning (new)

All detectors support whitelists (CSV names), admin bypass via permission level, cooldowns between alerts, and configurable actions: `log`, `alert`, or `kick` with custom kick message.

- Reach Detection
  - Keys: `reach_detection.enabled`, `max_legal_distance`, `buffer`, `window_seconds`, `infractions_threshold`, `cooldown_seconds`, `notify_admins_only`, `ignore_permission_level`, `whitelist_players`, `debug`.
  - Block interaction reach: `block_check_enabled`, `block_max_distance`, `block_buffer`, `block_ignore_creative`.

- KillAura Detection
  - Keys: `killaura.enabled`, `cps_window_ms`, `max_cps`, `max_fov_degrees`, `require_line_of_sight`, `switch_window_ms`, `max_targets_in_switch_window`, `window_seconds`, `infractions_threshold`, `cooldown_seconds`, `ignore_permission_level`, `whitelist_players`, `action_on_detect`, `kick_message`.

- Speed Detection
  - Keys: `speed_detection.enabled`, `window_seconds`, `infractions_threshold`, `cooldown_seconds`, `base_max_mps`, `sprint_multiplier`, `potion_per_level`, `ice_multiplier`, `buffer_mps`, `max_teleport_delta`, ignores for `creative|flight|elytra|vehicles|riptide`, `action_on_detect`, `kick_message`, `debug`.

- Fly Detection
  - Keys: `fly_detection.enabled`, `max_vertical_speed`, `buffer`, `max_air_time_seconds`, `window_seconds`, `infractions_threshold`, `cooldown_seconds`, ignores for `creative|elytra|vehicle|levitation_potion`, `action_on_detect`, `kick_message`, `debug`.

- Instant Break Detection
  - Keys: `instant_break.enabled`, `window_seconds`, `infractions_threshold`, `cooldown_seconds`, `tolerance_ms`, `tolerance_factor`, `min_expected_ms`, `ignore_permission_level`, `whitelist_players`, `action_on_detect`, `kick_message`, `debug`.

- Nuker Detection
  - Keys: `nuker.enabled`, `window_seconds`, `max_blocks_in_window`, `cluster_ms`, `max_blocks_in_cluster`, `max_reach_distance`, `reach_buffer`, `ignore_permission_level`, `ignore_creative`, `whitelist_players`, `cooldown_seconds`, `action_on_detect`, `kick_message`, `debug`.

- Client Detection
  - Logs brand/version/modlist on login (`client_detection.log_on_login`).
  - Deny lists by exact mod id or name keywords (`deny_mod_ids`, `deny_mod_names`).
  - Action on deny: `log|alert|kick` with `kick_message` and optional Discord alert (`alert_discord`).
  - Admin-only in-game alerts and debug toggles are available.

## Commands overview (/bx)

Barix registers a root command `/bx` (alias `/barix`). Below is a quick overview; in-game help remains authoritative.

- Core
  - `/bx ping`
  - `/bx help`
  - `/bx lang reload`

- Audit
  - `/bx audit status|on|off|toggle`
  - `/bx audit window <ms>`
  - `/bx audit set <open|close|place|break> <on|off>`
  - `/bx audit query uuid <uuid> [limit]`
  - `/bx audit query block <mod:id> [limit]`
  - `/bx audit compress-now`

- Config
  - `/bx config list [prefix]`
  - `/bx config get <key>`
  - `/bx config set <key> <value>`
  - `/bx config save`
  - `/bx config reload`

- Lag
  - `/bx lagscan <seconds>` — run a short lag scan and print top consumers.

- Alerts
  - `/bx alert test` — send a test alert (if webhook configured).

- TopLuck
  - `/bx topluck status|on|off|toggle|reset-cooldowns`
  - `/bx topluck cooldown <seconds>`
  - `/bx topluck threshold <double>`
  - `/bx topluck ratio threshold <double> | minbase <int> | window <seconds> | splitdims <on|off|toggle>`
  - `/bx topluck events list|enable <name>|disable <name>`

- Modules (New)
  - `/bx modules list` — show loaded modules (native + external)
  - `/bx modules status` — show started state and list
  - `/bx modules reload` — rebuild from config + external registry and start
  - `/bx modules restart` — stop + start current stack

- AntiCheat toggle (via Modules API)
  - `/bx anticheat status|on|off|toggle`

### Regions — commands, particles and wand (new)

- Core region commands (with advanced suggestions):
  - `/bx region pos1 [x y z]` — set pos1. Suggests your current coordinates if omitted.
  - `/bx region pos2 [x y z]` — set pos2. Suggests your current coordinates if omitted.
  - `/bx region create <id>` — create a region; suggests a free id like `region_1`.
  - `/bx region remove <id>` — remove by id; suggests existing ids.
  - `/bx region list` — list regions in the current world.
  - `/bx region info <id>` — info line + flags; suggests existing ids.
  - `/bx region at [x y z]` — list regions at a position; suggests your current coordinates.
  - `/bx region priority <id> <value>` — set priority; suggests the current priority.
  - `/bx region name <id> <value>` — set display name; suggests the current name.
  - `/bx region setflag <id> <flag> on|off|clear` — suggests ids and flag names (enum-based).
  - `/bx region addowner|delowner <id> <uuid>` — suggests ids and online players’ UUIDs (with player name hints).
  - `/bx region addmember|delmember <id> <uuid>` — same as above for members.

- Visuals (no client mod required):
  - pos1/pos2 markers and region box edges are drawn using vanilla particles.
  - Toggled by `regions.selection_particles`.

- Selection wand (configurable):
  - Left-click block with the configured item: set pos1 (the block isn’t broken).
  - Right-click block: set pos2 (interaction is cancelled).
  - Right-click air: raytrace forward and set pos2 if a block is targeted.
  - Only main hand is considered; requires `regions.wand_min_permission_level` and (optionally) sneaking.

## Module lifecycle logs (New)

Each module start/stop emits clear logs under categories like `Modules/anticheat` or `Modules/example`:

- Starting: `barix.modules.log.starting`
- Started: `barix.modules.log.started`
- Stopping: `barix.modules.log.stopping`
- Stopped: `barix.modules.log.stopped`

These show colorized in console with Barix’s logging formatter.

## Modules API (New)

Third-party mods can add their own modules to Barix and participate in the lifecycle. Implement `IBarixModule`, then register it through `BarixAPI`.

- Interface (simplified):

```java
public interface IBarixModule {
    String id();                    // unique, stable
    default List<String> dependsOn(){ return List.of(); }
    void start();                   // subscribe/register/start
    void stop();                    // cleanup/unregister
}
```

- Register/unregister at runtime:

```java
// Register (e.g., mod setup or server starting)
fr.bck.barix.api.BarixAPI.registerModule(new MyModule());

// Unregister later if needed
fr.bck.barix.api.BarixAPI.unregisterModule("mymodule");
```

- Start ordering & reloads:
  - `dependsOn()` controls ordering; modules are topo-sorted on start.
  - If you register/unregister after Barix is started, Barix will safely restart its module stack.

### Example included

This repository includes a minimal example module at:

- `src/main/java/fr/bck/barix/example/ExampleExternalModule.java`

It is not auto-registered. To try it, call during your mod init:

```java
fr.bck.barix.api.BarixAPI.registerModule(new fr.bck.barix.example.ExampleExternalModule());
```

Then run `/bx modules status` in game to see it listed. You’ll also see console logs under `Modules/example` on start/stop.

## Anti-Xray Compatibility (New)

Barix Anti-Xray can gracefully coexist with other mods by either disabling itself when specific mods are present or by delegating decisions to compatibility providers.

- Config-based disable
  - Key: `antixray.disable_if_mod_present` (CSV of modids)
  - If any listed mod is loaded, Barix Anti-Xray is automatically disabled (no Netty injection, no masking).
  - Example: `antixray.disable_if_mod_present = otherantixray, some_network_overhaul`

- API-based providers
  - Implement `fr.bck.barix.api.IAntiXrayCompat` and register via `BarixAPI.registerAntiXrayCompat(...)`.
  - A provider can:
    - Disable Barix Anti-Xray entirely (`disableBarixAntiXrayCompletely()`),
    - Decide per-player if Anti-Xray should apply (`shouldApplyFor(ServerPlayer)`),
    - Add extra target blocks to hide (`isTargetBlock(BlockState)`),
    - Veto hiding for specific positions (`shouldHide(...)`).

- Registration snippet:

```java
fr.bck.barix.api.BarixAPI.registerAntiXrayCompat(new IAntiXrayCompat() {
    @Override public String id() { return "my-compat"; }
    @Override public boolean shouldApplyFor(ServerPlayer sp) {
        // Disable Anti-Xray in The End as an example
        return !sp.level().dimension().location().toString().equals("minecraft:the_end");
    }
    @Override public boolean isTargetBlock(BlockState state) {
        // Also hide raw copper blocks (demo)
        return state.is(net.minecraft.world.level.block.Blocks.RAW_COPPER_BLOCK);
    }
    @Override public boolean shouldHide(ServerPlayer sp, BlockPos pos, BlockState state, Level lvl) {
        // Do not hide emerald ores above Y=50 (demo)
        if (state.is(net.minecraft.world.level.block.Blocks.EMERALD_ORE) && pos.getY() > 50) return false;
        return true;
    }
});
```

- Example included:
  - `src/main/java/fr/bck/barix/example/ExampleAntiXrayCompat.java`

## Permissions

Barix includes a permissions bridge to work with common permission providers (e.g., LuckPerms on Forge).  
Typical nodes (examples; adjust to your permission plugin):

- `barix.admin` — Full admin access.
- `barix.region.*` — Region management.
- `barix.audit.*` — Audit querying/rotation.
- `barix.lag.*` — Lag monitor commands.
- `barix.wand` — Selection tool.

If no permission provider is present, Barix defaults to operator checks for sensitive commands.

## Compatibility

- Built for Forge 47.x on Minecraft 1.20.1.
- Server-only by design; clients are optional.
- Known conflicts:
  - Mods that rewrite chunk packets or perform their own anti-xray at the network layer.
  - Any mod that aggressively injects into the same Netty pipeline phases may require manual ordering.

## Performance

- Audit logs are buffered and rotated to reduce IO spikes.
- Anti-xray works at the packet level and aims to be low-overhead.
- Selection and region checks are optimized for common server tick budgets.

## FAQ

- Q: Do players need Barix client-side?
  - A: No. It’s server-side only. Selection particles/boxes are sent by the server and visible to vanilla clients. Installing the client mod enables optional admin UI.

- Q: Can I change which ores are hidden by anti-xray?
  - A: Yes—edit the `anti_xray_ores` block tag via datapack or resource data to suit your server’s rules.

- Q: Where are the logs?
  - A: `config/barix/audit/` with automatic rotation.

- Q: How do I set up Discord alerts?
  - A: Paste a valid webhook URL in the Barix config under `config/barix/`. Leave empty to disable.

## For Developers

- Public API: `fr.bck.barix.api.*` exposes hooks for lag tracking and selected events.
- Modules API: `IBarixModule` + `BarixAPI.registerModule/unregisterModule` to plug additional features.
- Packaged with Sponge Mixin configuration for robust client-side rendering and admin interactions (optional).

---

Barix focuses on clarity and control: actionable logs, sane defaults, and admin tools that don’t get in your way. If you run a survival or semi-vanilla server, Barix helps you keep it fair and fast.

## What's new (1.0.13-BETA)

- New: Pluggable modules API with dependency-ordered startup and safe restarts.
- New: Built-in AntiCheat suite (Reach, KillAura, Speed, Fly, InstantBreak, Nuker, Client Detection) with fine-grained tuning, whitelists, bypass, cooldowns, and action policies (log|alert|kick).
- New: Anti-Xray compatibility (auto-disable by modid, provider API) plus new settings (dimension whitelist, depth-aware camo, update caps, extra blocks, player/permission bypass).
- New: Rich Discord alerts (username, anti-spam cooldown, in-game admins-only display).
- New: Lightweight lag monitor and `/bx lagscan` command.
- New: Region selection particles and box preview; configurable selection wand.
- New: `/bx modules` and `/bx anticheat` to manage the module stack.
- Improved: JSONL audit with rotation/compaction options; query commands and on-demand compression.
- Improved: TopLuck (fishing/looting/mining events, mining ratio model, per-dimension split).
- Improved: Permissions bridge plus server language configurable and reloadable via `/bx lang reload`.

## Update checker

Barix supports Forge’s built‑in update checker.

- Where: `src/main/resources/update.json` (committed in this repo)
- Activation: set a public URL in `src/main/resources/META-INF/mods.toml` via `updateJSONURL`.
  - Configured Raw URL: `https://raw.githubusercontent.com/Blaackknight/Barix/main/src/main/resources/update.json`
  - GitHub Pages (auto‑deployed via Actions): `https://blaackknight.github.io/Barix/update.json`
    - You can switch `updateJSONURL` to the Pages URL if you prefer CDN‑backed delivery.
- Format: this file already contains entries for Minecraft `1.20.1` and version `1.0.13-BETA`, plus `promos`.
- Releasing a new version:
  1) Bump `mod_version` in `gradle.properties`.
  2) Add a new entry under the relevant MC version in `update.json` and update `promos` (`1.20.1-latest`/`recommended`).
  3) Commit and push; the update checker will pick it up from the configured URL.
