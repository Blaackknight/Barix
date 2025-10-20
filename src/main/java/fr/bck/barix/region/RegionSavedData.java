package fr.bck.barix.region;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Données sauvegardées d'une dimension: régions indexées par id.
 */
public class RegionSavedData extends SavedData {
    static final String DATA_NAME = "barix_regions";

    private final Map<String, Region> regions = new LinkedHashMap<>();

    public RegionSavedData() {}

    public Map<String, Region> regions() { return regions; }

    public Region get(String id) { return regions.get(id); }

    public void put(Region r) {
        regions.put(r.getId(), r);
        setDirty();
    }

    public Region remove(String id) {
        Region prev = regions.remove(id);
        if (prev != null) setDirty();
        return prev;
    }

    public Collection<Region> all() { return regions.values(); }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Region r : regions.values()) {
            list.add(r.save());
        }
        tag.put("list", list);
        return tag;
    }

    public static RegionSavedData load(CompoundTag tag) {
        RegionSavedData data = new RegionSavedData();
        ListTag list = tag.getList("list", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Region r = Region.load(t);
            data.regions.put(r.getId(), r);
        }
        return data;
    }
}
