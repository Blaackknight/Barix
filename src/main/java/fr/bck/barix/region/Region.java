package fr.bck.barix.region;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.*;

public class Region {
    private String id; // unique dans un monde
    private String name;
    private ResourceKey<Level> dimension;
    private BlockPos min;
    private BlockPos max;
    private int priority; // plus haut = prioritaire pour les infos, mais l'agrégation des flags reste "false gagne"
    private final Set<UUID> owners = new HashSet<>();
    private final Set<UUID> members = new HashSet<>();
    private final EnumMap<Flags, Boolean> flags = new EnumMap<>(Flags.class);

    public Region(String id, ResourceKey<Level> dim, BlockPos a, BlockPos b) {
        this.id = Objects.requireNonNull(id, "id");
        this.dimension = Objects.requireNonNull(dim, "dimension");
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());
        this.min = new BlockPos(minX, minY, minZ);
        this.max = new BlockPos(maxX, maxY, maxZ);
        this.priority = 0;
        this.name = id;
        // par défaut: rien d'explicite, on laisse la résolution utiliser FALLBACK_ALLOW
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ResourceKey<Level> getDimension() { return dimension; }
    public BlockPos getMin() { return min; }
    public BlockPos getMax() { return max; }
    public int getPriority() { return priority; }
    public void setPriority(int p) { this.priority = p; }
    public Set<UUID> getOwners() { return owners; }
    public Set<UUID> getMembers() { return members; }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public void setBounds(BlockPos a, BlockPos b) {
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());
        this.min = new BlockPos(minX, minY, minZ);
        this.max = new BlockPos(maxX, maxY, maxZ);
    }

    public void setFlag(Flags f, Boolean val) {
        if (val == null) flags.remove(f); else flags.put(f, val);
    }

    public Optional<Boolean> getFlag(Flags f) {
        return Optional.ofNullable(flags.get(f));
    }

    public Map<Flags, Boolean> getAllFlags() { return Collections.unmodifiableMap(flags); }

    public boolean isOwnerOrMember(UUID id) {
        return owners.contains(id) || members.contains(id);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", name == null ? "" : name);
        tag.putString("dim", dimension.location().toString());
        tag.putInt("minX", min.getX());
        tag.putInt("minY", min.getY());
        tag.putInt("minZ", min.getZ());
        tag.putInt("maxX", max.getX());
        tag.putInt("maxY", max.getY());
        tag.putInt("maxZ", max.getZ());
        tag.putInt("priority", priority);

        ListTag ow = new ListTag();
        for (UUID u : owners) {
            CompoundTag t = new CompoundTag();
            t.putUUID("u", u);
            ow.add(t);
        }
        tag.put("owners", ow);

        ListTag mb = new ListTag();
        for (UUID u : members) {
            CompoundTag t = new CompoundTag();
            t.putUUID("u", u);
            mb.add(t);
        }
        tag.put("members", mb);

        ListTag fl = new ListTag();
        for (var e : flags.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("k", e.getKey().name());
            t.putBoolean("v", e.getValue());
            fl.add(t);
        }
        tag.put("flags", fl);
        return tag;
    }

    public static Region load(CompoundTag tag) {
        String id = tag.getString("id");
        String name = tag.getString("name");
        String dim = tag.getString("dim");
        ResourceLocation rl = ResourceLocation.tryParse(dim);
        ResourceKey<Level> key = rl != null ? ResourceKey.create(Registries.DIMENSION, rl) : Level.OVERWORLD;
        BlockPos min = new BlockPos(tag.getInt("minX"), tag.getInt("minY"), tag.getInt("minZ"));
        BlockPos max = new BlockPos(tag.getInt("maxX"), tag.getInt("maxY"), tag.getInt("maxZ"));
        Region r = new Region(id, key, min, max);
        r.name = name;
        r.priority = tag.getInt("priority");

        ListTag ow = tag.getList("owners", Tag.TAG_COMPOUND);
        for (int i = 0; i < ow.size(); i++) {
            CompoundTag t = ow.getCompound(i);
            if (t.hasUUID("u")) r.owners.add(t.getUUID("u"));
        }
        ListTag mb = tag.getList("members", Tag.TAG_COMPOUND);
        for (int i = 0; i < mb.size(); i++) {
            CompoundTag t = mb.getCompound(i);
            if (t.hasUUID("u")) r.members.add(t.getUUID("u"));
        }
        ListTag fl = tag.getList("flags", Tag.TAG_COMPOUND);
        for (int i = 0; i < fl.size(); i++) {
            CompoundTag t = fl.getCompound(i);
            try {
                Flags f = Flags.valueOf(t.getString("k"));
                r.flags.put(f, t.getBoolean("v"));
            } catch (IllegalArgumentException ignored) {}
        }
        return r;
    }
}
