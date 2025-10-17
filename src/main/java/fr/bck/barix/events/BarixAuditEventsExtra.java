package fr.bck.barix.events;

import fr.bck.barix.audit.AuditJsonLog;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Map;

import static fr.bck.barix.config.BarixServerConfig.*;

public final class BarixAuditEventsExtra {

    // ---- helpers ----
    private static Map<String, Object> basePlayer(Player p) {
        var m = new LinkedHashMap<String, Object>();
        var pl = new LinkedHashMap<String, Object>();
        pl.put("name", p.getScoreboardName());
        pl.put("uuid", p.getUUID().toString());
        m.put("player", pl);
        return m;
    }

    // Détection générique d’un bloc “interactif” (compatible blocs moddés)
    private static boolean isInteractable(Level lvl, BlockPos pos, BlockState st) {
        if (st.isAir()) return false;

        // 1) Blocs avec BlockEntity (très fréquent en mods) -> interactifs
        if (lvl.getBlockEntity(pos) != null) return true;

        // 2) Fournissent un Menu (container/écran)
        if (st.getMenuProvider(lvl, pos) != null) return true;

        // 3) Source de signal (leviers, boutons, comparateurs, etc.)
        if (st.isSignalSource()) return true;

        // 4) Quelques blocs vanilla interactifs sans BlockEntity
        var b = st.getBlock();
        return b instanceof net.minecraft.world.level.block.DoorBlock || b instanceof net.minecraft.world.level.block.TrapDoorBlock || b instanceof net.minecraft.world.level.block.FenceGateBlock || b instanceof net.minecraft.world.level.block.LeverBlock || b instanceof net.minecraft.world.level.block.NoteBlock || b instanceof net.minecraft.world.level.block.CraftingTableBlock || b instanceof net.minecraft.world.level.block.AnvilBlock || b instanceof net.minecraft.world.level.block.GrindstoneBlock || b instanceof net.minecraft.world.level.block.StonecutterBlock || b instanceof net.minecraft.world.level.block.CartographyTableBlock || b instanceof net.minecraft.world.level.block.SmithingTableBlock || b instanceof net.minecraft.world.level.block.LoomBlock || b instanceof net.minecraft.world.level.block.CampfireBlock || b instanceof net.minecraft.world.level.block.ComposterBlock || b instanceof net.minecraft.world.level.block.BellBlock || b instanceof net.minecraft.world.level.block.LecternBlock || b instanceof net.minecraft.world.level.block.LayeredCauldronBlock;
    }

    // LOGIN / LOGOUT
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!CORE_ENABLED.get() || !AUDIT_ENABLE_ALL.get() || !AUDIT_LOG_LOGIN.get()) return;
        var p = e.getEntity();
        var payload = basePlayer(p);
        payload.put("pos", Map.of("x", p.getBlockX(), "y", p.getBlockY(), "z", p.getBlockZ()));
        payload.put("dim", p.level().dimension().location().toString());
        AuditJsonLog.write("player_login", payload);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!CORE_ENABLED.get() || !AUDIT_ENABLE_ALL.get() || !AUDIT_LOG_LOGOUT.get()) return;
        var p = e.getEntity();
        var payload = basePlayer(p);
        payload.put("pos", Map.of("x", p.getBlockX(), "y", p.getBlockY(), "z", p.getBlockZ()));
        payload.put("dim", p.level().dimension().location().toString());
        AuditJsonLog.write("player_logout", payload);
    }

    // RESPAWN
    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!CORE_ENABLED.get() || !AUDIT_ENABLE_ALL.get() || !AUDIT_LOG_RESPAWN.get()) return;
        var p = e.getEntity();
        var payload = basePlayer(p);
        payload.put("pos", Map.of("x", p.getBlockX(), "y", p.getBlockY(), "z", p.getBlockZ()));
        payload.put("dim", p.level().dimension().location().toString());
        payload.put("end_conquered", e.isEndConquered());
        AuditJsonLog.write("player_respawn", payload);
    }

    // DIM CHANGE
    @SubscribeEvent
    public void onDim(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!CORE_ENABLED.get() || !AUDIT_ENABLE_ALL.get() || !AUDIT_LOG_DIM_CHANGE.get()) return;
        var p = e.getEntity();
        var payload = basePlayer(p);
        payload.put("from_dim", e.getFrom().location().toString());
        payload.put("to_dim", e.getTo().location().toString());
        payload.put("pos", Map.of("x", p.getBlockX(), "y", p.getBlockY(), "z", p.getBlockZ()));
        AuditJsonLog.write("player_dim_change", payload);
    }

    // PICKUP / DROP
    @SubscribeEvent
    public void onPickup(EntityItemPickupEvent e) {
        if (!CORE_ENABLED.get() || !AUDIT_ENABLE_ALL.get() || !AUDIT_LOG_PICKUP.get()) return;
        var p = e.getEntity();
        var st = e.getItem().getItem();
        var payload = basePlayer(p);
        payload.put("item", Map.of("id", String.valueOf(ForgeRegistries.ITEMS.getKey(st.getItem())), "count", st.getCount()));
        payload.put("pos", Map.of("x", e.getItem().getBlockX(), "y", e.getItem().getBlockY(), "z", e.getItem().getBlockZ()));
        payload.put("dim", p.level().dimension().location().toString());
        AuditJsonLog.write("player_item_pickup", payload);
    }

    @SubscribeEvent
    public void onDrop(ItemTossEvent e) {
        if (!CORE_ENABLED.get() || !AUDIT_ENABLE_ALL.get() || !AUDIT_LOG_DROP.get()) return;
        var p = e.getPlayer();
        var st = e.getEntity().getItem();
        var payload = basePlayer(p);
        payload.put("item", Map.of("id", String.valueOf(ForgeRegistries.ITEMS.getKey(st.getItem())), "count", st.getCount()));
        payload.put("pos", Map.of("x", e.getEntity().getBlockX(), "y", e.getEntity().getBlockY(), "z", e.getEntity().getBlockZ()));
        payload.put("dim", p.level().dimension().location().toString());
        AuditJsonLog.write("player_item_drop", payload);
    }

    // CRAFT / SMELT
    @SubscribeEvent
    public void onCraft(PlayerEvent.ItemCraftedEvent e) {
        if (!CORE_ENABLED.get() || !AUDIT_ENABLE_ALL.get() || !AUDIT_LOG_CRAFT.get()) return;
        var p = e.getEntity();
        var out = e.getCrafting();
        var payload = basePlayer(p);
        payload.put("result", Map.of("id", String.valueOf(ForgeRegistries.ITEMS.getKey(out.getItem())), "count", out.getCount()));
        var level = p.level();
        var inv = e.getInventory();

        if (inv instanceof CraftingContainer cc) {
            level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, cc, level)
                    // 1.20+: RecipeHolder.id()
                    .ifPresent(r -> payload.put("recipe", r.getId().toString()));
        }

        AuditJsonLog.write("player_craft", payload);
    }

    @SubscribeEvent
    public void onSmelt(PlayerEvent.ItemSmeltedEvent e) {
        if (!CORE_ENABLED.get() || !AUDIT_ENABLE_ALL.get() || !AUDIT_LOG_SMELT.get()) return;
        var p = e.getEntity();
        var out = e.getSmelting();
        var payload = basePlayer(p);
        payload.put("result", Map.of("id", String.valueOf(ForgeRegistries.ITEMS.getKey(out.getItem())), "count", out.getCount()));
        AuditJsonLog.write("player_smelt", payload);
    }

    // EXPLOSION
    @SubscribeEvent
    public void onExplode(ExplosionEvent.Detonate e) {
        if (!CORE_ENABLED.get() || !AUDIT_ENABLE_ALL.get() || !AUDIT_LOG_EXPLOSION.get()) return;
        var ex = e.getExplosion();
        var lvl = e.getLevel();
        var payload = new LinkedHashMap<String, Object>();
        payload.put("source", ex.getExploder() == null ? "unknown" : ex.getExploder().getType().toString());
        payload.put("pos", Map.of("x", (int) ex.getPosition().x, "y", (int) ex.getPosition().y, "z", (int) ex.getPosition().z));
        payload.put("dim", lvl.dimension().location().toString());
        payload.put("blocks_destroyed", e.getAffectedBlocks().size());
        payload.put("entities_hit", e.getAffectedEntities().size());
        AuditJsonLog.write("explosion", payload);
    }

    // INTERACTION BLOC (leviers/boutons/portes…)
    @SubscribeEvent
    public void onUse(PlayerInteractEvent.RightClickBlock e) {
        if (!CORE_ENABLED.get() || !AUDIT_ENABLE_ALL.get() || !AUDIT_LOG_BLOCK_INTERACT.get()) return;
        if (e.getUseBlock() == net.minecraftforge.eventbus.api.Event.Result.DENY) return;

        // Eviter les doublons: ne log que la main principale
        if (e.getHand() != InteractionHand.MAIN_HAND) return;

        var p = e.getEntity();
        var lvl = e.getLevel();
        var pos = e.getPos();
        BlockState st = lvl.getBlockState(pos);

        // Détection générique (mods-friendly)
        if (!isInteractable(lvl, pos, st)) return;

        var payload = basePlayer(p);
        payload.put("block", String.valueOf(ForgeRegistries.BLOCKS.getKey(st.getBlock())));
        payload.put("action", "use");
        payload.put("pos", Map.of("x", pos.getX(), "y", pos.getY(), "z", pos.getZ()));
        payload.put("dim", p.level().dimension().location().toString());
        AuditJsonLog.write("block_interact", payload);
    }

    // ANVIL
    @SubscribeEvent
    public void onAnvilRepair(AnvilRepairEvent e) {
        if (!CORE_ENABLED.get() || !AUDIT_ENABLE_ALL.get() || !AUDIT_LOG_ANVIL.get()) return;
        var p = e.getEntity();
        var payload = basePlayer(p);
        payload.put("result", String.valueOf(ForgeRegistries.ITEMS.getKey(e.getOutput().getItem())));
        payload.put("cost", e.getBreakChance()); // info limitée ici; on loggue le break chance
        AuditJsonLog.write("anvil_repair", payload);
    }

    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent e) {
        if (!CORE_ENABLED.get() || !AUDIT_ENABLE_ALL.get() || !AUDIT_LOG_ANVIL.get()) return;
        var payload = new LinkedHashMap<String, Object>();
        payload.put("left", String.valueOf(ForgeRegistries.ITEMS.getKey(e.getLeft().getItem())));
        payload.put("right", String.valueOf(ForgeRegistries.ITEMS.getKey(e.getRight().getItem())));
        payload.put("output", e.getOutput().isEmpty() ? "empty" : String.valueOf(ForgeRegistries.ITEMS.getKey(e.getOutput().getItem())));
        payload.put("cost", e.getCost());
        AuditJsonLog.write("anvil_update", payload);
    }

    // CHAT (désactivé par défaut)
    @SubscribeEvent
    public void onChat(ServerChatEvent e) {
        if (!CORE_ENABLED.get() || !AUDIT_ENABLE_ALL.get() || !AUDIT_LOG_CHAT.get()) return;
        var p = e.getPlayer();
        var payload = basePlayer(p);
        payload.put("message", e.getRawText());   // attention RGPD, flag off par défaut
        AuditJsonLog.write("player_chat", payload);
    }
}