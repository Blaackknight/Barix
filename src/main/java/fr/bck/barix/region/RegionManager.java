package fr.bck.barix.region;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class RegionManager {
    private static final Map<ResourceKey<Level>, RegionManager> BY_DIM = new HashMap<>();

    public static RegionManager of(ServerLevel level) {
        return BY_DIM.computeIfAbsent(level.dimension(), k -> new RegionManager(level));
    }

    public static void clearAll() { BY_DIM.clear(); }

    private final ServerLevel level;
    private final RegionSavedData data;
    private final RegionIndex index = new RegionIndex();

    private RegionManager(ServerLevel level) {
        this.level = level;
        this.data = level.getDataStorage().computeIfAbsent(RegionSavedData::load, RegionSavedData::new, RegionSavedData.DATA_NAME);
        this.index.rebuild(this.data.all());
    }

    public Collection<Region> all() { return data.all(); }

    public Region get(String id) { return data.get(id); }

    public boolean exists(String id) { return data.get(id) != null; }

    public Region create(String id, BlockPos a, BlockPos b) {
        if (exists(id)) throw new IllegalArgumentException("Region déjà existante: " + id);
        Region r = new Region(id, level.dimension(), a, b);
        data.put(r);
        index.add(r);
        return r;
    }

    public boolean remove(String id) {
        Region r = data.remove(id);
        if (r != null) {
            index.remove(r);
            return true;
        }
        return false;
    }

    public List<Region> at(BlockPos pos) {
        return index.query(pos);
    }

    public void updateBounds(String id, BlockPos a, BlockPos b) {
        Region r = get(id);
        if (r == null) throw new IllegalArgumentException("Région inconnue: " + id);
        index.remove(r);
        r.setBounds(a, b);
        index.add(r);
        data.setDirty();
    }

    public void setPriority(String id, int prio) {
        Region r = get(id);
        if (r == null) throw new IllegalArgumentException("Région inconnue: " + id);
        r.setPriority(prio);
        data.setDirty();
    }

    public void setFlag(String id, Flags f, Boolean val) {
        Region r = get(id);
        if (r == null) throw new IllegalArgumentException("Région inconnue: " + id);
        r.setFlag(f, val);
        data.setDirty();
    }

    public void setName(String id, String name) {
        Region r = get(id);
        if (r == null) throw new IllegalArgumentException("Région inconnue: " + id);
        r.setName(name);
        data.setDirty();
    }

    public boolean addOwner(String id, UUID u) {
        Region r = get(id);
        if (r == null) throw new IllegalArgumentException("Région inconnue: " + id);
        boolean changed = r.getOwners().add(u);
        if (changed) data.setDirty();
        return changed;
    }

    public boolean removeOwner(String id, UUID u) {
        Region r = get(id);
        if (r == null) throw new IllegalArgumentException("Région inconnue: " + id);
        boolean changed = r.getOwners().remove(u);
        if (changed) data.setDirty();
        return changed;
    }

    public boolean addMember(String id, UUID u) {
        Region r = get(id);
        if (r == null) throw new IllegalArgumentException("Région inconnue: " + id);
        boolean changed = r.getMembers().add(u);
        if (changed) data.setDirty();
        return changed;
    }

    public boolean removeMember(String id, UUID u) {
        Region r = get(id);
        if (r == null) throw new IllegalArgumentException("Région inconnue: " + id);
        boolean changed = r.getMembers().remove(u);
        if (changed) data.setDirty();
        return changed;
    }

    public boolean isAllowed(Player player, Flags flag, BlockPos pos) {
        UUID uid = player.getUUID();
        List<Region> regs = at(pos);
        if (regs.isEmpty()) return true; // par défaut hors région: autorisé
        boolean allow = true;
        for (Region r : regs) {
            // règle ENTRY: si explicitement false et pas owner/member => bloquer toute action
            Optional<Boolean> entry = r.getFlag(Flags.ENTRY);
            if (entry.isPresent() && !entry.get() && !r.isOwnerOrMember(uid)) return false;
            // bypass propriétaire/membre
            if (r.isOwnerOrMember(uid)) continue;
            Optional<Boolean> v = r.getFlag(flag);
            if (v.isPresent() && !v.get()) return false;
            if (v.isPresent() && v.get()) allow = true;
        }
        return allow;
    }
}
