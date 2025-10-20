package fr.bck.barix.region;

import fr.bck.barix.BarixConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;

@Mod.EventBusSubscriber(modid = BarixConstants.MODID)
public class RegionProtectionEvents {

    // Block break
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer sp)) return;
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        BlockPos pos = e.getPos();
        if (!RegionManager.of(sl).isAllowed(sp, Flags.BREAK, pos)) {
            e.setCanceled(true);
        }
    }

    // Block place (single + multi)
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        Level lvl = (Level) e.getLevel();
        if (lvl.isClientSide) return;
        Entity ent = e.getEntity();
        if (!(ent instanceof ServerPlayer sp)) return;
        if (!(lvl instanceof ServerLevel sl)) return;
        BlockPos pos = e.getPos();
        if (!RegionManager.of(sl).isAllowed(sp, Flags.BUILD, pos)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMultiPlace(BlockEvent.EntityMultiPlaceEvent e) {
        Level lvl = (Level) e.getLevel();
        if (lvl.isClientSide) return;
        Entity ent = e.getEntity();
        if (!(ent instanceof ServerPlayer sp)) return;
        if (!(lvl instanceof ServerLevel sl)) return;
        for (var sn : e.getReplacedBlockSnapshots()) {
            BlockPos pos = sn.getPos();
            if (!RegionManager.of(sl).isAllowed(sp, Flags.BUILD, pos)) {
                e.setCanceled(true);
                return;
            }
        }
    }

    // Right click block / containers
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        Player p = e.getEntity();
        if (!(p instanceof ServerPlayer sp)) return;
        Level lvl = p.level();
        if (!(lvl instanceof ServerLevel sl)) return;
        BlockPos pos = e.getPos();
        // Distinguer containers si possible
        boolean isContainer = false;
        BlockEntity be = lvl.getBlockEntity(pos);
        if (be instanceof MenuProvider) isContainer = true;
        if (isContainer) {
            if (!RegionManager.of(sl).isAllowed(sp, Flags.CONTAINERS, pos)) {
                e.setCanceled(true);
            }
        } else {
            if (!RegionManager.of(sl).isAllowed(sp, Flags.INTERACT, pos)) {
                e.setCanceled(true);
            }
        }
    }

    // Right click item in air
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        Player p = e.getEntity();
        if (!(p instanceof ServerPlayer sp)) return;
        Level lvl = p.level();
        if (!(lvl instanceof ServerLevel sl)) return;
        BlockPos pos = p.blockPosition();
        if (!RegionManager.of(sl).isAllowed(sp, Flags.USE_ITEM, pos)) {
            e.setCanceled(true);
        }
    }

    // PvP
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        Entity target = e.getTarget();
        if (!(target instanceof ServerPlayer)) return; // PVP uniquement
        Level lvl = sp.level();
        if (!(lvl instanceof ServerLevel sl)) return;
        BlockPos pos = target.blockPosition();
        if (!RegionManager.of(sl).isAllowed(sp, Flags.PVP, pos)) {
            e.setCanceled(true);
        }
    }

    // Explosions: retirer les blocs affectés situés dans une région qui interdit EXPLOSIONS
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate e) {
        Level lvl = e.getLevel();
        if (lvl.isClientSide || !(lvl instanceof ServerLevel sl)) return;
        var list = e.getAffectedBlocks();
        Iterator<BlockPos> it = list.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            // On utilise un faux joueur localisé? Pas nécessaire: utilise un pseudo player pour vérif des règles générales
            // Ici on considère que si les régions locales interdisent EXPLOSIONS pour "général", on empêche la destruction
            // On passe un joueur nul alors RegionManager ne peut pas vérifier owner; utiliser un faux allow check:
            boolean allowed = true;
            for (Region r : RegionManager.of(sl).at(pos)) {
                var f = r.getFlag(Flags.EXPLOSIONS);
                if (f.isPresent() && !f.get()) { allowed = false; break; }
            }
            if (!allowed) it.remove();
        }
    }
}

