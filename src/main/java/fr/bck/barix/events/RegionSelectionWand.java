package fr.bck.barix.events;

import fr.bck.barix.config.BarixServerConfig;
import fr.bck.barix.selection.SelectionManager;
import fr.bck.barix.util.Particles;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

public class RegionSelectionWand {

    private static boolean isEnabled() {
        return Boolean.TRUE.equals(BarixServerConfig.REGIONS_WAND_ENABLE.get());
    }

    private static boolean hasPerm(ServerPlayer p) {
        int min = BarixServerConfig.REGIONS_WAND_MIN_PERMISSION_LEVEL.get();
        return p.createCommandSourceStack().hasPermission(min);
    }

    private static boolean requireSneak() {
        return Boolean.TRUE.equals(BarixServerConfig.REGIONS_WAND_SNEAK_ONLY.get());
    }

    private static boolean isConfiguredWand(ServerPlayer p) {
        String id = BarixServerConfig.REGIONS_WAND_ITEM.get();
        if (id == null || id.isBlank()) return false;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return false;
        Item target = ForgeRegistries.ITEMS.getValue(rl);
        if (target == null) return false;
        var held = p.getMainHandItem();
        return held.getItem() == target;
    }

    private static void feedback(ServerPlayer p, String msg) {
        p.displayClientMessage(Component.literal(msg), false);
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
        if (e.getLevel().isClientSide()) return;
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!isEnabled()) return;
        if (!hasPerm(p)) return;
        if (requireSneak() && !p.isShiftKeyDown()) return;
        if (!isConfiguredWand(p)) return;

        BlockPos pos = e.getPos();
        SelectionManager.setPos1(p, pos);
        feedback(p, "pos1 = " + pos.getX()+","+pos.getY()+","+pos.getZ());
        Particles.showPos(p, pos);
        var a = SelectionManager.getPos1(p); var b = SelectionManager.getPos2(p);
        if (a != null && b != null) Particles.showBox(p, a, b);
        e.setCanceled(true); // éviter de casser le bloc
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide()) return;
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!isEnabled()) return;
        if (!hasPerm(p)) return;
        if (requireSneak() && !p.isShiftKeyDown()) return;
        if (!isConfiguredWand(p)) return;

        BlockPos pos = e.getPos();
        SelectionManager.setPos2(p, pos);
        feedback(p, "pos2 = " + pos.getX()+","+pos.getY()+","+pos.getZ());
        Particles.showPos(p, pos);
        var a = SelectionManager.getPos1(p); var b = SelectionManager.getPos2(p);
        if (a != null && b != null) Particles.showBox(p, a, b);
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        if (e.getLevel().isClientSide()) return;
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!isEnabled()) return;
        if (!hasPerm(p)) return;
        if (requireSneak() && !p.isShiftKeyDown()) return;
        if (!isConfiguredWand(p)) return;

        // Clic droit en l'air: raytrace puis pos2
        BlockPos pos = SelectionManager.raytraceBlock(p, 120.0);
        if (pos == null) return;
        SelectionManager.setPos2(p, pos);
        feedback(p, "pos2 = " + pos.getX()+","+pos.getY()+","+pos.getZ());
        Particles.showPos(p, pos);
        var a = SelectionManager.getPos1(p); var b = SelectionManager.getPos2(p);
        if (a != null && b != null) Particles.showBox(p, a, b);
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }
}
