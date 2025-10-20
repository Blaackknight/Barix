package fr.bck.barix.topluck;

import fr.bck.barix.BarixConstants;
import fr.bck.barix.config.BarixServerConfig;
import fr.bck.barix.lang.Lang;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class TopLuck {

    private static boolean dbg() {
        return Boolean.TRUE.equals(BarixServerConfig.LUCK_DEBUG.get()) || Boolean.TRUE.equals(BarixServerConfig.CORE_DEBUG.get());
    }

    // key = playerName + '\u0000' + category
    private static final Map<String, Long> LAST_ALERT_MS = new ConcurrentHashMap<>();

    // Suivi minage: compteurs par (player + cat)
    private static final Map<String, MiningCounter> MINING = new ConcurrentHashMap<>();
    private static final Set<Block> BASE_BLOCKS = Set.of(Blocks.STONE, Blocks.DEEPSLATE, Blocks.TUFF, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.NETHERRACK, Blocks.BLACKSTONE, Blocks.BASALT, Blocks.END_STONE);

    private static final class MiningCounter {
        long base;
        long ores;
        long windowStartMs;
    }

    /**
     * Évalue un évènement de "luck".
     *
     * @param playerName pseudo du joueur
     * @param luck       valeur observée (double)
     * @param category   sous-catégorie libre (ex: "Alerts", "DungeonLoot") ; peut contenir '/'
     * @return true si une alerte a été émise
     */
    public static boolean handle(String playerName, double luck, String category) {
        if (!Boolean.TRUE.equals(BarixServerConfig.LUCK_ENABLED.get())) {
            if (dbg()) BarixConstants.log.debug("§5TopLuck", Lang.tr("barix.topluck.disabled", BarixServerConfig.CORE_LOCALE.get()));
            return false;
        }

        double threshold = BarixServerConfig.LUCK_THRESHOLD.get();
        if (luck < threshold) {
            if (dbg()) BarixConstants.log.debug("§5TopLuck", Lang.tr("barix.topluck.below_threshold", BarixServerConfig.CORE_LOCALE.get(), String.format("%.3f", luck), String.format("%.3f", threshold)));
            return false;
        }

        String cat = (category == null || category.isBlank()) ? "default" : category;
        String mapKey = (playerName == null ? "§5unknown" : playerName) + '\u0000' + cat;

        int cooldownSec = BarixServerConfig.LUCK_COOLDOWN_SECONDS.get();
        long now = System.currentTimeMillis();
        Long last = LAST_ALERT_MS.get(mapKey);
        if (last != null && (now - last) < TimeUnit.SECONDS.toMillis(cooldownSec)) {
            if (dbg()) BarixConstants.log.debug("§5TopLuck§6/§b" + cat, Lang.tr("barix.topluck.cooldown.active", BarixServerConfig.CORE_LOCALE.get(), cooldownSec));
            return false;
        }
        LAST_ALERT_MS.put(mapKey, now);

        // Message principal (affiche "ratio" pour Mining, "luck" sinon)
        String name = (playerName == null ? "unknown" : playerName);
        if (Boolean.TRUE.equals(BarixServerConfig.LUCK_LOG_CONSOLE.get())) {
            BarixConstants.log.info("§5TopLuck/" + cat, Lang.tr("barix.topluck.detected", BarixServerConfig.CORE_LOCALE.get(), name, String.format("%.3f", luck), cat));
        }

        if (Boolean.TRUE.equals(BarixServerConfig.LUCK_BROADCAST.get())) {
            BarixConstants.log.info("§5TopLuck§6/§aBroadcast", Lang.tr("barix.topluck.broadcast", BarixServerConfig.CORE_LOCALE.get(), name, String.format("%.3f", luck)));
        }

        if (Boolean.TRUE.equals(BarixServerConfig.LUCK_NOTIFY_ADMINS_ONLY.get())) {
            BarixConstants.log.warn("TopLuck/Admin", Lang.tr("barix.topluck.admin_notify", BarixServerConfig.CORE_LOCALE.get(), name, String.format("%.3f", luck), cat));
        }

        return true;
    }

    /**
     * Overload pratique: catégorie par défaut.
     */
    public static boolean handle(String playerName, double luck) {
        return handle(playerName, luck, "default");
    }

    /**
     * Overload pratique pour passer un Player.
     */
    public static boolean handle(Player player, double luck, String category) {
        String name = (player != null && player.getGameProfile() != null) ? player.getGameProfile().getName() : "unknown";
        return handle(name, luck, category);
    }

    /**
     * Appelé à chaque cassage de bloc pour mettre à jour le ratio minage et déclencher si suspect.
     */
    public static boolean onBlockMined(Player player, BlockState state) {
        if (player == null || state == null) return false;
        if (!BarixServerConfig.LUCK_ENABLED.get() || !BarixServerConfig.LUCK_EVENT_MINING.get()) return false;

        boolean isOre = state.is(Tags.Blocks.ORES);
        boolean isBase = BASE_BLOCKS.contains(state.getBlock());
        if (!isOre && !isBase) return false; // on ne compte que ce qui nous intéresse

        String cat = miningCategory(player.level());
        String key = safeName(player) + '\u0000' + cat;
        long now = System.currentTimeMillis();
        int windowSec = BarixServerConfig.LUCK_RATIO_WINDOW_SECONDS.get();
        int minBase = BarixServerConfig.LUCK_RATIO_MIN_BASE.get();
        double threshold = BarixServerConfig.LUCK_RATIO_THRESHOLD.get();

        MiningCounter c = MINING.computeIfAbsent(key, k -> {
            MiningCounter mc = new MiningCounter();
            mc.windowStartMs = now;
            return mc;
        });

        // reset fenêtre si expirée
        if (now - c.windowStartMs > TimeUnit.SECONDS.toMillis(windowSec)) {
            c.base = 0;
            c.ores = 0;
            c.windowStartMs = now;
        }

        if (isOre) c.ores++;
        if (isBase) c.base++;

        if (c.base >= minBase && c.ores > 0) {
            double ratio = c.ores / (double) c.base; // 0.05 = 5%
            if (ratio >= threshold) {
                // Déclenche via handle() en utilisant le ratio comme "luck"
                boolean fired = handle(safeName(player), ratio, cat);
                if (fired && dbg()) {
                    BarixConstants.log.debug("TopLuck/" + cat, Lang.tr("barix.topluck.mining.window.detail", BarixServerConfig.CORE_LOCALE.get(), c.base, c.ores, String.format("%.4f", ratio), String.format("%.4f", threshold)));
                }
                return fired;
            }
        }
        return false;
    }

    private static String miningCategory(Level level) {
        String dim;
        if (level == null) {
            dim = "Unknown";
        } else if (level.dimension() == Level.NETHER) {
            dim = "Nether";
        } else if (level.dimension() == Level.END) {
            dim = "End";
        } else {
            dim = "Overworld";
        }
        return BarixServerConfig.LUCK_RATIO_SPLIT_DIMENSIONS.get() ? "Mining/" + dim : "Mining";
    }

    private static String safeName(Player p) {
        return (p != null && p.getGameProfile() != null) ? p.getGameProfile().getName() : "unknown";
    }

    public static void resetCooldowns() {
        LAST_ALERT_MS.clear();
    }
}
