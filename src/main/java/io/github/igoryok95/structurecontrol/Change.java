package io.github.igoryok95.structurecontrol;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public record Change(UUID id, StructureKey key, Kind kind, boolean beforeDisabled,
                     boolean afterDisabled, CompoundTag originalStart, int[] bounds, String actor, long timestamp) {
    public enum Kind { SPAWNS, UNREGISTER }

    public Change {
        originalStart = originalStart.copy();
        bounds = bounds.clone();
    }

    @Override
    public CompoundTag originalStart() {
        return originalStart.copy();
    }

    @Override public int[] bounds() { return bounds.clone(); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.put("key", key.save());
        tag.putString("kind", kind.name());
        tag.putBoolean("before_disabled", beforeDisabled);
        tag.putBoolean("after_disabled", afterDisabled);
        tag.put("original_start", originalStart.copy());
        tag.putIntArray("bounds", bounds);
        tag.putString("actor", actor);
        tag.putLong("timestamp", timestamp);
        return tag;
    }

    public static Change load(CompoundTag tag) {
        return new Change(tag.getUUID("id"), StructureKey.load(tag.getCompound("key")),
                Kind.valueOf(tag.getString("kind")), tag.getBoolean("before_disabled"),
                tag.getBoolean("after_disabled"), tag.getCompound("original_start"), tag.getIntArray("bounds"),
                tag.getString("actor"), tag.getLong("timestamp"));
    }
}
