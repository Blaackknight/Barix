package fr.bck.barix.anti;

import fr.bck.barix.BarixConstants;
import fr.bck.barix.config.BarixServerConfig;
import fr.bck.barix.lang.Lang;
import fr.bck.barix.tags.BarixBlockTags;
import fr.bck.barix.util.ServerScheduler;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class AntiXrayManager {
    private AntiXrayManager() {
    }

    // Cache des blocs supplémentaires (config: antixray.extra_blocks)
    private static String extraRawCache = null;
    private static final Set<Block> extraBlocksCache = new ObjectOpenHashSet<>();

    private static boolean dbg() {
        return Boolean.TRUE.equals(BarixServerConfig.ANTIXRAY_DEBUG.get()) || Boolean.TRUE.equals(BarixServerConfig.CORE_DEBUG.get());
    }

    private static boolean isCompatDisabledCompletely() {
        // Config: disable_if_mod_present
        String csv = BarixServerConfig.ANTIXRAY_DISABLE_IF_MOD_PRESENT.get();
        if (csv != null && !csv.isBlank()) {
            for (String s : csv.split(",")) {
                String id = s.trim();
                if (!id.isEmpty() && ModList.get().isLoaded(id)) return true;
            }
        }
        // Providers
        for (var p : fr.bck.barix.api.BarixAPI.getAntiXrayCompatProviders()) {
            try { if (p.disableBarixAntiXrayCompletely()) return true; } catch (Throwable ignored) {}
        }
        return false;
    }

    // --- Hooks côté réseau ---

    public static void onChunkSent(ServerPlayer sp, ClientboundLevelChunkWithLightPacket pkt) {
        boolean applicable = shouldApplyFor(sp);
        boolean maskOnLoad = Boolean.TRUE.equals(BarixServerConfig.ANTIXRAY_MASK_ON_CHUNKLOAD.get());
        if (dbg())
            BarixConstants.log.debug("§1AntiXray§6/§eHook", Lang.tr("barix.antixray.hook.chunk_sent", BarixServerConfig.CORE_LOCALE.get(), pkt.getX(), pkt.getZ(), sp.getGameProfile().getName(), applicable, maskOnLoad));
        if (!applicable) return;
        if (!maskOnLoad) return;
        ServerLevel lvl = sp.serverLevel();
        var cp = new ChunkPos(pkt.getX(), pkt.getZ());
        // Délai léger (2 ticks) pour garantir que le client a bien appliqué le chunk avant les updates
        ServerScheduler.runLater(lvl, 2, () -> maskChunkToPlayer(sp, lvl, cp));
    }

    public static void onSectionUpdateSent(ServerPlayer sp, net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket pkt) {
        // Pour l'instant, on ne masque pas les updates de section, mais on log pour debug
        if (dbg())
            BarixConstants.log.debug("§1AntiXray§6/§eHook", Lang.tr("barix.antixray.hook.section_update_sent", BarixServerConfig.CORE_LOCALE.get(), sp.getGameProfile().getName(), pkt.getClass().getSimpleName()));
    }

    // --- Cœur logique ---

    private static void maskChunkToPlayer(ServerPlayer sp, ServerLevel lvl, ChunkPos cp) {
        final int minY = lvl.getMinBuildHeight();
        final int maxY = lvl.getMaxBuildHeight(); // exclusif pour nos boucles +1
        final int limit = BarixServerConfig.ANTIXRAY_MAX_BLOCK_UPDATES_PER_CHUNK.get();
        int sent = 0;

        // Préparer sets pour filtres
        final int revealRadius = BarixServerConfig.ANTIXRAY_REVEAL_RADIUS.get();
        final boolean hideSurface = BarixServerConfig.ANTIXRAY_HIDE_SURFACE.get();
        final int onlyBelowY = BarixServerConfig.ANTIXRAY_ONLY_BELOW_Y.get();

        // Rafraîchir le cache de blocs supplémentaires si la config a changé
        updateExtraBlocksCache();

        if (dbg())
            BarixConstants.log.debug("§1AntiXray§6/§7Mask", Lang.tr("barix.antixray.mask.start", BarixServerConfig.CORE_LOCALE.get(), sp.getGameProfile().getName(), cp.x, cp.z, limit, revealRadius, onlyBelowY, extraBlocksCache.size()));

        // Position base du chunk
        int baseX = cp.getMinBlockX();
        int baseZ = cp.getMinBlockZ();

        BlockState stone = resolveBlockState(BarixServerConfig.ANTIXRAY_CAMO_BLOCK.get(), Blocks.STONE.defaultBlockState());
        BlockState deep = resolveBlockState(BarixServerConfig.ANTIXRAY_CAMO_DEEPSLATE_BLOCK.get(), Blocks.DEEPSLATE.defaultBlockState());
        int deepY = BarixServerConfig.ANTIXRAY_DEEPSLATE_Y.get();

        // Scan simple 16x(heigth)x16 limité par max updates
        outer:
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                // Itérer du bas vers le haut pour prioriser profondeur
                for (int y = minY; y < maxY; y++) {
                    if (y >= onlyBelowY) continue; // appliquer seulement sous Y
                    BlockPos pos = new BlockPos(baseX + dx, y, baseZ + dz);
                    BlockState state = lvl.getBlockState(pos);
                    if (!isTargetBlock(state)) continue; // tag + extra + compat
                    if (!hideSurface && touchesAir(lvl, pos)) continue;
                    if (sp.blockPosition().closerThan(pos, revealRadius)) continue;

                    BlockState camo = (y < deepY) ? deep : stone;
                    if (state.equals(camo)) continue; // déjà camouflé

                    // Compat: laisser les providers veto le masquage pour ce bloc précis
                    if (!compatShouldHide(sp, pos, state, lvl)) continue;

                    sp.connection.send(new ClientboundBlockUpdatePacket(pos, camo));
                    if (++sent >= limit) break outer;
                }
            }
        }
        if (sent > 0) {
            if (dbg())
                BarixConstants.log.debug("§1AntiXray§6/§7Mask", Lang.tr("barix.antixray.mask.sent", BarixServerConfig.CORE_LOCALE.get(), sent, cp.x, cp.z, sp.getGameProfile().getName()));
        } else {
            if (dbg())
                BarixConstants.log.debug("§1AntiXray§6/§7Mask", Lang.tr("barix.antixray.mask.none", BarixServerConfig.CORE_LOCALE.get(), cp.x, cp.z, sp.getGameProfile().getName()));
        }
    }

    private static boolean compatShouldHide(ServerPlayer sp, BlockPos pos, BlockState state, Level lvl) {
        for (var p : fr.bck.barix.api.BarixAPI.getAntiXrayCompatProviders()) {
            try { if (!p.shouldHide(sp, pos, state, lvl)) return false; } catch (Throwable ignored) {}
        }
        return true;
    }

    private static boolean isTargetBlock(BlockState state) {
        // Combine le tag Barix + la liste custom config
        if (state.is(BarixBlockTags.ANTIXRAY_ORES)) return true;
        if (extraBlocksCache.contains(state.getBlock())) return true;
        // Compat: cibles supplémentaires
        for (var p : fr.bck.barix.api.BarixAPI.getAntiXrayCompatProviders()) {
            try { if (p.isTargetBlock(state)) return true; } catch (Throwable ignored) {}
        }
        return false;
    }

    private static void updateExtraBlocksCache() {
        String raw = BarixServerConfig.ANTIXRAY_EXTRA_BLOCKS.get();
        if (Objects.equals(raw, extraRawCache)) return; // pas de changement
        extraRawCache = raw;
        extraBlocksCache.clear();
        if (raw == null || raw.isBlank()) {
            if (dbg())
                BarixConstants.log.debug("§1AntiXray§6/§88Config", Lang.tr("barix.antixray.config.extra_blocks_empty", BarixServerConfig.CORE_LOCALE.get()));
            return;
        }
        int added = 0;
        for (String s : raw.split(",")) {
            String t = s.trim();
            if (t.isEmpty()) continue;
            ResourceLocation rl = ResourceLocation.tryParse(t);
            if (rl == null) continue;
            Block b = ForgeRegistries.BLOCKS.getValue(rl);
            if (b != null && b != Blocks.AIR) {
                extraBlocksCache.add(b);
                added++;
            }
        }
        BarixConstants.log.info("§1AntiXray§6/§88Config", Lang.tr("barix.antixray.config.extra_blocks_loaded", BarixServerConfig.CORE_LOCALE.get(), added));
    }

    private static boolean shouldApplyFor(ServerPlayer sp) {
        if (isCompatDisabledCompletely()) return false;
        if (!Boolean.TRUE.equals(BarixServerConfig.CORE_ENABLED.get())) {
            if (dbg())
                BarixConstants.log.debug("§1AntiXray§6/§aApply", Lang.tr("barix.antixray.apply.core_disabled", BarixServerConfig.CORE_LOCALE.get(), sp.getGameProfile().getName()));
            return false;
        }
        if (!Boolean.TRUE.equals(BarixServerConfig.ANTIXRAY_ENABLE.get())) {
            if (dbg())
                BarixConstants.log.debug("§1AntiXray§6/§aApply", Lang.tr("barix.antixray.apply.disabled", BarixServerConfig.CORE_LOCALE.get(), sp.getGameProfile().getName()));
            return false;
        }
        // Providers: possibilité de refuser application pour ce joueur
        for (var p : fr.bck.barix.api.BarixAPI.getAntiXrayCompatProviders()) {
            try { if (!p.shouldApplyFor(sp)) return false; } catch (Throwable ignored) {}
        }
        // Bypass permission (désactivable avec -1)
        int bypassLevel = BarixServerConfig.ANTIXRAY_BYPASS_PERMISSION_LEVEL.get();
        if (bypassLevel >= 0) {
            try {
                if (sp.hasPermissions(bypassLevel)) {
                    if (dbg())
                        BarixConstants.log.debug("§1AntiXray§6/§aApply", Lang.tr("barix.antixray.apply.bypass_perm", BarixServerConfig.CORE_LOCALE.get(), bypassLevel, sp.getGameProfile().getName()));
                    return false;
                }
            } catch (Throwable ignored) {
            }
        }
        // Bypass joueurs
        String bypassCsv = BarixServerConfig.ANTIXRAY_BYPASS_PLAYERS.get();
        if (bypassCsv != null && !bypassCsv.isBlank()) {
            Set<String> names = csvToSetLower(bypassCsv);
            String name = sp.getGameProfile().getName().toLowerCase(Locale.ROOT);
            if (names.contains(name)) {
                if (dbg())
                    BarixConstants.log.debug("§1AntiXray§6/§aApply", Lang.tr("barix.antixray.apply.bypass_player", BarixServerConfig.CORE_LOCALE.get(), sp.getGameProfile().getName()));
                return false;
            }
        }
        // Dimensions (whitelist)
        String dimsCsv = BarixServerConfig.ANTIXRAY_DIM_WHITELIST.get();
        if (dimsCsv != null && !dimsCsv.isBlank()) {
            Set<String> dims = csvToSetLower(dimsCsv);
            String cur = sp.level().dimension().location().toString().toLowerCase(Locale.ROOT);
            if (!dims.contains(cur)) {
                if (dbg())
                    BarixConstants.log.debug("§1AntiXray§6/§aApply", Lang.tr("barix.antixray.apply.dim_not_whitelisted", BarixServerConfig.CORE_LOCALE.get(), cur, sp.getGameProfile().getName()));
                return false;
            }
        }
        return true;
    }

    public static boolean shouldInject() {
        if (isCompatDisabledCompletely()) return false;
        if (!Boolean.TRUE.equals(BarixServerConfig.CORE_ENABLED.get())) return false;
        if (!Boolean.TRUE.equals(BarixServerConfig.ANTIXRAY_ENABLE.get())) return false;
        return true;
    }

    private static Set<String> csvToSetLower(String csv) {
        var set = new ObjectOpenHashSet<String>();
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) set.add(t.toLowerCase(Locale.ROOT));
        }
        return set;
    }

    private static BlockState resolveBlockState(String id, BlockState def) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) {
                Block b = Objects.requireNonNull(ForgeRegistries.BLOCKS.getValue(rl)).defaultBlockState().getBlock();
                if (b != Blocks.AIR) return b.defaultBlockState();
            }
        } catch (Throwable ignored) {
        }
        return def;
    }

    static boolean shouldHide(ServerPlayer sp, BlockPos pos, BlockState state, Level lvl) {
        if (!isTargetBlock(state)) return false;
        if (!BarixServerConfig.ANTIXRAY_HIDE_SURFACE.get() && touchesAir(lvl, pos)) return false;
        if (sp.blockPosition().closerThan(pos, BarixServerConfig.ANTIXRAY_REVEAL_RADIUS.get())) return false;
        if (!(pos.getY() < BarixServerConfig.ANTIXRAY_ONLY_BELOW_Y.get())) return false;
        // Compat veto
        return compatShouldHide(sp, pos, state, lvl);
    }

    // Vérifie si le bloc touche l’air (6 directions)
    private static boolean touchesAir(Level lvl, BlockPos pos) {
        return lvl.isEmptyBlock(pos.above()) || lvl.isEmptyBlock(pos.below()) || lvl.isEmptyBlock(pos.north()) || lvl.isEmptyBlock(pos.south()) || lvl.isEmptyBlock(pos.east()) || lvl.isEmptyBlock(pos.west());
    }
}
