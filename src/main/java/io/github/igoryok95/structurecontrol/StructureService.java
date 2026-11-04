package io.github.igoryok95.structurecontrol;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

public final class StructureService {
    public static final long CONFIRM_MILLIS = 60_000;
    private static final long MAX_START_BYTES = 1_048_576;
    private final MinecraftServer server;
    private final ControlData data;
    private final LongSupplier clock;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public record Located(StructureKey key, Structure structure, StructureStart start, LevelChunk chunk) {}
    public record Pending(String token, StructureKey key, CompoundTag snapshot, long expires) {
        public Pending { snapshot = snapshot.copy(); }
        @Override public CompoundTag snapshot() { return snapshot.copy(); }
    }

    public StructureService(MinecraftServer server, ControlData data, LongSupplier clock) {
        this.server = server;
        this.data = data;
        this.clock = clock;
    }

    public ControlData data() { return data; }

    private void writable() {
        if (!server.isSameThread()) throw new IllegalStateException("Structure mutation outside server thread");
        if (!data.isValid()) throw new ControlFailure("error.data_invalid");
    }

    private LevelChunk loaded(ServerLevel level, int x, int z) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
        if (chunk == null) throw new ControlFailure("error.chunk_unloaded", x, z);
        return chunk;
    }

    public List<Located> inspect(ServerLevel level, BlockPos pos, ResourceLocation selected) {
        LevelChunk chunk = loaded(level, pos.getX() >> 4, pos.getZ() >> 4);
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        LinkedHashSet<Structure> types = new LinkedHashSet<>(chunk.getAllReferences().keySet());
        types.addAll(chunk.getAllStarts().keySet());
        List<Located> found = new ArrayList<>();
        for (Structure type : types) {
            ResourceLocation id = registry.getKey(type);
            if (id == null || selected != null && !selected.equals(id)) continue;
            var starts = new LinkedHashSet<Long>();
            for (long packed : chunk.getReferencesForStructure(type)) starts.add(packed);
            if (chunk.getAllStarts().containsKey(type)) starts.add(chunk.getPos().toLong());
            for (long packed : starts) {
                ChunkPos startPos = new ChunkPos(packed);
                LevelChunk startChunk = loaded(level, startPos.x, startPos.z);
                StructureStart start = startChunk.getStartForStructure(type);
                if (start != null && start.isValid() && start.getBoundingBox().isInside(pos)) {
                    found.add(new Located(new StructureKey(level.dimension().location(), id, startPos.x, startPos.z), type, start, startChunk));
                }
            }
        }
        found.sort(Comparator.comparing(value -> value.key().structure().toString()));
        return List.copyOf(found);
    }

    public Located select(ServerLevel level, BlockPos pos, ResourceLocation id) {
        if (!level.registryAccess().registryOrThrow(Registries.STRUCTURE).containsKey(id)) {
            throw new ControlFailure("error.unknown_structure", id.toString());
        }
        var matches = inspect(level, pos, id);
        if (matches.isEmpty()) throw new ControlFailure("error.not_found", id.toString());
        if (matches.size() != 1) throw new ControlFailure("error.ambiguous", id.toString());
        return matches.getFirst();
    }

    public void setSpawns(Located located, boolean enabled, String actor) {
        writable();
        if (located.structure().spawnOverrides().isEmpty()) throw new ControlFailure("error.no_overrides");
        boolean previous = data.isDisabled(located.key());
        if (previous == !enabled) throw new ControlFailure(enabled ? "error.already_on" : "error.already_off");
        data.setDisabled(located.key(), !enabled);
        data.record(new Change(UUID.randomUUID(), located.key(), Change.Kind.SPAWNS, previous, !enabled,
                new CompoundTag(), bounds(located.start()), actor, clock.getAsLong()));
        StructureControl.LOGGER.info("{} set structure spawns {} for {}", actor, enabled, located.key());
    }

    public Pending preview(Located located, UUID source) {
        writable();
        pending.values().removeIf(value -> value.expires() < clock.getAsLong());
        CompoundTag snapshot = located.start().createTag(StructurePieceSerializationContext.fromLevel(level(located.key())), located.start().getChunkPos());
        if (snapshot.sizeInBytes() > MAX_START_BYTES) throw new ControlFailure("error.too_large");
        Pending action = new Pending(UUID.randomUUID().toString().replace("-", "").substring(0, 12), located.key(), snapshot,
                clock.getAsLong() + CONFIRM_MILLIS);
        pending.put(source, action);
        return action;
    }

    public void cancel(UUID source) { pending.remove(source); }

    private ServerLevel level(StructureKey key) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, key.dimension()));
        if (level == null) throw new ControlFailure("error.dimension_unavailable", key.dimension().toString());
        return level;
    }

    private Structure structure(ServerLevel level, StructureKey key) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(key.structure());
        if (structure == null) throw new ControlFailure("error.unknown_structure", key.structure().toString());
        return structure;
    }

    public StructureKey confirm(UUID source, String token, String actor) {
        writable();
        Pending action = pending.get(source);
        if (action == null || !action.token().equals(token)) throw new ControlFailure("error.confirmation_missing");
        if (clock.getAsLong() > action.expires()) {
            pending.remove(source);
            throw new ControlFailure("error.confirmation_expired");
        }
        StructureKey key = action.key();
        ServerLevel level = level(key);
        Structure structure = structure(level, key);
        LevelChunk chunk = loaded(level, key.chunkX(), key.chunkZ());
        StructureStart current = chunk.getStartForStructure(structure);
        if (current == null || !current.isValid() || !current.createTag(StructurePieceSerializationContext.fromLevel(level), chunk.getPos()).equals(action.snapshot())) {
            pending.remove(source);
            throw new ControlFailure("error.structure_changed");
        }
        Change change = new Change(UUID.randomUUID(), key, Change.Kind.UNREGISTER, data.isDisabled(key), data.isDisabled(key),
                action.snapshot(), bounds(current), actor, clock.getAsLong());
        chunk.setStartForStructure(structure, StructureStart.INVALID_START);
        level.onStructureStartsAvailable(chunk);
        data.record(change);
        pending.remove(source);
        StructureControl.LOGGER.info("{} unregistered structure {}", actor, key);
        return key;
    }

    public static int[] bounds(StructureStart start) {
        var box = start.getBoundingBox();
        return new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()};
    }

    public Located byKey(ServerLevel level, StructureKey key) {
        if (!level.dimension().location().equals(key.dimension())) throw new ControlFailure("error.request_invalid");
        LevelChunk chunk = loaded(level, key.chunkX(), key.chunkZ());
        Structure type = structure(level, key);
        StructureStart start = chunk.getStartForStructure(type);
        if (start == null || !start.isValid()) throw new ControlFailure("error.not_found", key.structure().toString());
        return new Located(key, type, start, chunk);
    }

    public Change undo(String actor) {
        writable();
        List<Change> history = data.history();
        if (history.isEmpty()) throw new ControlFailure("error.no_history");
        Change change = history.getLast();
        StructureKey key = change.key();
        if (change.kind() == Change.Kind.SPAWNS) {
            if (data.isDisabled(key) != change.afterDisabled()) throw new ControlFailure("error.structure_changed");
            data.setDisabled(key, change.beforeDisabled());
        } else {
            ServerLevel level = level(key);
            Structure type = structure(level, key);
            LevelChunk chunk = loaded(level, key.chunkX(), key.chunkZ());
            StructureStart current = chunk.getStartForStructure(type);
            if (current == null || current.isValid()) throw new ControlFailure("error.structure_changed");
            StructureStart restored = StructureStart.loadStaticStart(StructurePieceSerializationContext.fromLevel(level), change.originalStart(), level.getSeed());
            if (restored == null || !restored.isValid() || restored.getStructure() != type || !restored.getChunkPos().equals(chunk.getPos())) {
                throw new ControlFailure("error.restore_failed");
            }
            chunk.setStartForStructure(type, restored);
            level.onStructureStartsAvailable(chunk);
        }
        data.removeLast(change);
        StructureControl.LOGGER.info("{} undid {} for {}", actor, change.kind(), key);
        return change;
    }
}
