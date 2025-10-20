package fr.bck.barix.topluck;

import fr.bck.barix.BarixConstants;
import fr.bck.barix.config.BarixServerConfig;
import fr.bck.barix.lang.Lang;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BarixConstants.MODID) // bus=FORGE par défaut
public final class TopLuckEvents {

    private TopLuckEvents() {
    }

    private static boolean dbg() {
        return Boolean.TRUE.equals(BarixServerConfig.LUCK_DEBUG.get()) || Boolean.TRUE.equals(BarixServerConfig.CORE_DEBUG.get());
    }

    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        if (!BarixServerConfig.LUCK_ENABLED.get() || !BarixServerConfig.LUCK_EVENT_FISHING.get()) return;
        Player p = event.getEntity();
        if (p == null || p.level().isClientSide) return;

        // Base: attribut Luck du joueur (si présent)
        double base = 0.0;
        var inst = p.getAttribute(Attributes.LUCK);
        if (inst != null) base = inst.getValue();

        // Heuristique simple: compter les drops "trésor"
        int treasures = 0;
        for (ItemStack s : event.getDrops()) {
            if (s.isEnchanted() || s.is(Items.ENCHANTED_BOOK) || s.is(Items.NAME_TAG) || s.is(Items.NAUTILUS_SHELL) || s.is(Items.SADDLE)) {
                treasures++;
            }
        }
        if (treasures < BarixServerConfig.LUCK_EVENT_MIN_TREASURE.get()) {
            if (dbg()) BarixConstants.log.debug("TopLuck/Fishing", Lang.tr("barix.topluck.fishing.no_treasure", BarixServerConfig.CORE_LOCALE.get(), treasures));
            return;
        }

        double luck = (base + treasures) * BarixServerConfig.LUCK_EVENT_SCALE.get();
        boolean fired = TopLuck.handle(p, luck, "Fishing");
        if (fired && dbg()) {
            BarixConstants.log.debug("TopLuck/Fishing", Lang.tr("barix.topluck.fishing.triggered", BarixServerConfig.CORE_LOCALE.get(), String.format("%.2f", base), treasures, String.format("%.3f", luck)));
        }
    }

    @SubscribeEvent
    public static void onLootingLevel(LootingLevelEvent event) {
        if (!BarixServerConfig.LUCK_ENABLED.get() || !BarixServerConfig.LUCK_EVENT_LOOTING.get()) return;
        if (event.getEntity() == null || event.getEntity().level().isClientSide) return;

        var src = event.getDamageSource();
        if (src == null || !(src.getEntity() instanceof Player p)) return;

        int looting = event.getLootingLevel();
        if (looting < BarixServerConfig.LUCK_EVENT_MIN_LOOTING.get()) {
            if (dbg()) BarixConstants.log.trace("§5TopLuck§6/§2Looting", Lang.tr("barix.topluck.looting.below_min", BarixServerConfig.CORE_LOCALE.get(), looting, BarixServerConfig.LUCK_EVENT_MIN_LOOTING.get()));
            return;
        }

        double luck = looting * BarixServerConfig.LUCK_EVENT_SCALE.get();
        boolean fired = TopLuck.handle(p, luck, "Looting");
        if (fired && dbg()) {
            BarixConstants.log.debug("§5TopLuck§6/§2Looting", Lang.tr("barix.topluck.looting.triggered", BarixServerConfig.CORE_LOCALE.get(), looting, String.format("%.3f", luck)));
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!BarixServerConfig.LUCK_ENABLED.get() || !BarixServerConfig.LUCK_EVENT_MINING.get()) return;
        var p = event.getPlayer();
        if (p == null || p.level().isClientSide) return;
        if(p.isCreative()) return;
        var state = event.getState();
        TopLuck.onBlockMined(p, state);
    }
}
