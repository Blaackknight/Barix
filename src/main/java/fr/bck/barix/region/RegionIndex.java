package fr.bck.barix.region;

import net.minecraft.core.BlockPos;

import java.util.*;

public class RegionIndex {
    private final Map<Long, List<Region>> byChunk = new HashMap<>();

    private static long key(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
    }

    void clear() { byChunk.clear(); }

    void add(Region r) {
        int cminX = r.getMin().getX() >> 4;
        int cmaxX = r.getMax().getX() >> 4;
        int cminZ = r.getMin().getZ() >> 4;
        int cmaxZ = r.getMax().getZ() >> 4;
        for (int cx = cminX; cx <= cmaxX; cx++) {
            for (int cz = cminZ; cz <= cmaxZ; cz++) {
                long k = key(cx, cz);
                byChunk.computeIfAbsent(k, t -> new ArrayList<>()).add(r);
            }
        }
    }

    void remove(Region r) {
        int cminX = r.getMin().getX() >> 4;
        int cmaxX = r.getMax().getX() >> 4;
        int cminZ = r.getMin().getZ() >> 4;
        int cmaxZ = r.getMax().getZ() >> 4;
        for (int cx = cminX; cx <= cmaxX; cx++) {
            for (int cz = cminZ; cz <= cmaxZ; cz++) {
                long k = key(cx, cz);
                List<Region> list = byChunk.get(k);
                if (list != null) list.remove(r);
            }
        }
    }

    void rebuild(Collection<Region> regions) {
        clear();
        for (Region r : regions) add(r);
    }

    List<Region> query(BlockPos pos) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        List<Region> list = byChunk.getOrDefault(key(cx, cz), Collections.emptyList());
        if (list.isEmpty()) return Collections.emptyList();
        List<Region> out = new ArrayList<>();
        for (Region r : list) if (r.contains(pos)) out.add(r);
        // Tri par priorité décroissante puis id
        out.sort(Comparator.comparingInt(Region::getPriority).reversed().thenComparing(Region::getId));
        return out;
    }
}
