package io.github.igoryok95.structurecontrol;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MapSnapshot(int sequence, int centreX, int centreZ, int radius, String dimension,
                          List<Entry> entries, int loadedChunks, boolean truncated, String token,
                          Component message, boolean error, boolean open, boolean canUndo) implements CustomPacketPayload {
    public static final int ENTRY_LIMIT = 128;
    public static final Type<MapSnapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StructureControl.MOD_ID, "map_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MapSnapshot> CODEC = StreamCodec.of(MapSnapshot::encode, MapSnapshot::decode);

    public MapSnapshot { entries = List.copyOf(entries); }

    public record Entry(String structure, int startX, int startZ, int minX, int minY, int minZ,
                        int maxX, int maxY, int maxZ, boolean disabled, boolean registered, boolean hasSpawns) {
        public boolean same(Entry other) {
            return other != null && structure.equals(other.structure) && startX == other.startX && startZ == other.startZ;
        }
        public boolean covers(double x, double z) { return x >= minX && x < maxX + 1.0 && z >= minZ && z < maxZ + 1.0; }
        public long area() { return ((long) maxX - minX + 1) * ((long) maxZ - minZ + 1); }
    }

    private static void encode(RegistryFriendlyByteBuf buf, MapSnapshot snapshot) {
        buf.writeVarInt(snapshot.sequence);
        buf.writeVarInt(snapshot.centreX); buf.writeVarInt(snapshot.centreZ); buf.writeVarInt(snapshot.radius);
        buf.writeUtf(snapshot.dimension, 256);
        buf.writeVarInt(snapshot.entries.size());
        for (Entry entry : snapshot.entries) {
            buf.writeUtf(entry.structure, 256);
            buf.writeVarInt(entry.startX); buf.writeVarInt(entry.startZ);
            buf.writeVarInt(entry.minX); buf.writeVarInt(entry.minY); buf.writeVarInt(entry.minZ);
            buf.writeVarInt(entry.maxX); buf.writeVarInt(entry.maxY); buf.writeVarInt(entry.maxZ);
            buf.writeBoolean(entry.disabled); buf.writeBoolean(entry.registered); buf.writeBoolean(entry.hasSpawns);
        }
        buf.writeVarInt(snapshot.loadedChunks);
        buf.writeBoolean(snapshot.truncated);
        buf.writeUtf(snapshot.token, 64);
        ComponentSerialization.STREAM_CODEC.encode(buf, snapshot.message);
        buf.writeBoolean(snapshot.error); buf.writeBoolean(snapshot.open); buf.writeBoolean(snapshot.canUndo);
    }

    private static MapSnapshot decode(RegistryFriendlyByteBuf buf) {
        int sequence = buf.readVarInt(), x = buf.readVarInt(), z = buf.readVarInt(), radius = buf.readVarInt();
        String dimension = buf.readUtf(256);
        int count = buf.readVarInt();
        if (count < 0 || count > ENTRY_LIMIT || radius < 2 || radius > 8) throw new IllegalArgumentException("Invalid structure map size");
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) entries.add(new Entry(buf.readUtf(256), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));
        return new MapSnapshot(sequence, x, z, radius, dimension, entries, buf.readVarInt(), buf.readBoolean(), buf.readUtf(64),
                ComponentSerialization.STREAM_CODEC.decode(buf), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    @Override public Type<MapSnapshot> type() { return TYPE; }
}
