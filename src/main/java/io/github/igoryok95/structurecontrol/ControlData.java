package io.github.igoryok95.structurecontrol;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.saveddata.SavedData;

public final class ControlData extends SavedData {
    public static final String FILE = "structure_control";
    public static final int HISTORY_LIMIT = 32;
    public static final Factory<ControlData> FACTORY = new Factory<>(ControlData::new, ControlData::load);
    private volatile Set<StructureKey> disabled = Set.of();
    private final List<Change> history = new ArrayList<>();
    private boolean valid = true;
    private boolean loadedFromDisk;

    public boolean isDisabled(StructureKey key) { return disabled.contains(key); }
    public boolean isValid() { return valid; }
    public boolean wasLoadedFromDisk() { return loadedFromDisk; }
    public void makeReadOnly() { valid = false; }
    public Set<StructureKey> disabledKeys() { return disabled; }
    public List<Change> history() { return List.copyOf(history); }

    public void setDisabled(StructureKey key, boolean value) {
        LinkedHashSet<StructureKey> changed = new LinkedHashSet<>(disabled);
        if (value) changed.add(key); else changed.remove(key);
        disabled = Set.copyOf(changed);
        setDirty();
    }

    public void record(Change change) {
        history.add(change);
        while (history.size() > HISTORY_LIMIT) history.removeFirst();
        setDirty();
    }

    public void removeLast(Change expected) {
        if (history.isEmpty() || !history.getLast().id().equals(expected.id())) {
            throw new IllegalStateException("Undo history changed during operation");
        }
        history.removeLast();
        setDirty();
    }

    public static ControlData load(CompoundTag tag, HolderLookup.Provider registries) {
        ControlData data = new ControlData();
        data.loadedFromDisk = true;
        try {
            if (tag.getInt("schema") != 1) throw new IllegalArgumentException("Unsupported Structure Control data schema");
            Set<StructureKey> keys = new LinkedHashSet<>();
            for (var entry : tag.getList("disabled", 10)) keys.add(StructureKey.load((CompoundTag) entry));
            data.disabled = Set.copyOf(keys);
            for (var entry : tag.getList("history", 10)) data.history.add(Change.load((CompoundTag) entry));
            if (data.history.size() > HISTORY_LIMIT) throw new IllegalArgumentException("Oversized undo history");
        } catch (RuntimeException exception) {
            data.valid = false;
            StructureControl.LOGGER.error("Structure Control data is invalid; commands are read-only and the file will not be overwritten", exception);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (!valid) throw new IllegalStateException("Refusing to overwrite invalid Structure Control data");
        tag.putInt("schema", 1);
        ListTag rules = new ListTag();
        disabled.forEach(key -> rules.add(key.save()));
        tag.put("disabled", rules);
        ListTag changes = new ListTag();
        history.forEach(change -> changes.add(change.save()));
        tag.put("history", changes);
        return tag;
    }
}
