package io.github.igoryok95.structurecontrol;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ControlNetwork {
    public static volatile Consumer<MapSnapshot> CLIENT_HANDLER = snapshot -> {};
    private static final Map<MinecraftServer, Map<UUID, Long>> LAST_REQUEST = new ConcurrentHashMap<>();

    private ControlNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToServer(MapRequest.TYPE, MapRequest.CODEC, (request, context) -> context.enqueueWork(() -> handle(request, context)));
        registrar.playToClient(MapSnapshot.TYPE, MapSnapshot.CODEC, (snapshot, context) -> context.enqueueWork(() -> CLIENT_HANDLER.accept(snapshot)));
    }

    public static void stopped(MinecraftServer server) { LAST_REQUEST.remove(server); }

    public static void open(ServerPlayer player) {
        if (!player.connection.hasChannel(MapSnapshot.TYPE)) throw new ControlFailure("error.client_missing");
        var pos = player.chunkPosition();
        send(player, new MapRequest(0, MapRequest.Action.SNAPSHOT, pos.x, pos.z, 6, "", 0, 0, "", true), "", Component.empty(), false);
    }

    private static void handle(MapRequest request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        var source = player.createCommandSourceStack();
        if (!source.hasPermission(2)) {
            source.sendFailure(Messages.tr(source, "error.permission"));
            return;
        }
        if (!valid(request)) {
            source.sendFailure(Messages.tr(source, "error.request_invalid"));
            return;
        }
        var timestamps = LAST_REQUEST.computeIfAbsent(player.getServer(), ignored -> new ConcurrentHashMap<>());
        long now = System.currentTimeMillis();
        Long last = timestamps.get(player.getUUID());
        if (last != null && now - last < 100) {
            PacketDistributor.sendToPlayer(player, new MapSnapshot(request.sequence(), request.centreX(), request.centreZ(), request.radius(),
                    player.serverLevel().dimension().location().toString(), List.of(), 0, false, "", Messages.tr(source, "error.rate_limit"), true,
                    false, !StructureControl.service(player.getServer()).data().history().isEmpty()));
            return;
        }
        timestamps.put(player.getUUID(), now);
        var service = StructureControl.service(player.getServer());
        String token = "";
        Component message = Component.empty();
        boolean error = false;
        try {
            var key = new StructureKey(player.serverLevel().dimension().location(),
                    request.structure().isEmpty() ? ResourceLocation.withDefaultNamespace("pillager_outpost") : ResourceLocation.parse(request.structure()),
                    request.startX(), request.startZ());
            switch (request.action()) {
                case SNAPSHOT -> {}
                case SPAWNS_OFF, SPAWNS_ON -> {
                    boolean enabled = request.action() == MapRequest.Action.SPAWNS_ON;
                    service.setSpawns(service.byKey(player.serverLevel(), key), enabled, player.getGameProfile().getName());
                    message = Messages.tr(source, "spawns.changed", key.structure().toString(), key.chunkX(), key.chunkZ(),
                            Messages.tr(source, enabled ? "state.on" : "state.off"));
                }
                case PREVIEW -> {
                    var pending = service.preview(service.byKey(player.serverLevel(), key), player.getUUID());
                    token = pending.token();
                    message = Messages.tr(source, "ui.confirm_warning");
                }
                case CONFIRM -> {
                    var removed = service.confirm(player.getUUID(), request.token(), player.getGameProfile().getName());
                    message = Messages.tr(source, "unregistered", removed.structure().toString(), removed.chunkX(), removed.chunkZ());
                }
                case CANCEL -> { service.cancel(player.getUUID()); message = Messages.tr(source, "cancelled"); }
                case UNDO -> {
                    var change = service.undo(player.getGameProfile().getName());
                    message = Messages.tr(source, "undone", change.key().structure().toString());
                }
            }
        } catch (ControlFailure failure) {
            message = Messages.tr(source, failure.key(), failure.arguments());
            error = true;
        } catch (IllegalArgumentException exception) {
            message = Messages.tr(source, "error.request_invalid");
            error = true;
        }
        send(player, request, token, message, error);
    }

    public static boolean valid(MapRequest request) {
        return request.radius() >= 2 && request.radius() <= 8 && Math.abs((long) request.centreX()) <= 1_875_000
                && Math.abs((long) request.centreZ()) <= 1_875_000 && Math.abs((long) request.startX()) <= 1_875_000
                && Math.abs((long) request.startZ()) <= 1_875_000;
    }

    private static MapSnapshot.Entry entry(StructureKey key, int[] bounds, boolean disabled, boolean registered, boolean hasSpawns) {
        return new MapSnapshot.Entry(key.structure().toString(), key.chunkX(), key.chunkZ(), bounds[0], bounds[1], bounds[2],
                bounds[3], bounds[4], bounds[5], disabled, registered, hasSpawns);
    }

    private static boolean intersects(int[] bounds, MapRequest request) {
        int minX = (request.centreX() - request.radius()) * 16;
        int minZ = (request.centreZ() - request.radius()) * 16;
        int maxX = (request.centreX() + request.radius() + 1) * 16;
        int maxZ = (request.centreZ() + request.radius() + 1) * 16;
        return bounds.length == 6 && bounds[0] < maxX && bounds[3] >= minX && bounds[2] < maxZ && bounds[5] >= minZ;
    }

    public static MapSnapshot snapshot(ServerLevel level, MapRequest request, String token, Component message, boolean error) {
        var data = StructureControl.service(level.getServer()).data();
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var seen = new HashSet<StructureKey>();
        List<MapSnapshot.Entry> entries = new ArrayList<>();
        int loaded = 0;
        boolean truncated = false;
        for (int x = request.centreX() - request.radius(); x <= request.centreX() + request.radius(); x++) {
            for (int z = request.centreZ() - request.radius(); z <= request.centreZ() + request.radius(); z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                loaded++;
                var types = new HashSet<>(chunk.getAllReferences().keySet());
                types.addAll(chunk.getAllStarts().keySet());
                for (Structure type : types) {
                    var id = registry.getKey(type);
                    if (id == null) continue;
                    var starts = new HashSet<Long>();
                    for (long value : chunk.getReferencesForStructure(type)) starts.add(value);
                    if (chunk.getAllStarts().containsKey(type)) starts.add(chunk.getPos().toLong());
                    for (long packed : starts) {
                        var pos = new ChunkPos(packed);
                        var key = new StructureKey(level.dimension().location(), id, pos.x, pos.z);
                        if (!seen.add(key)) continue;
                        var startChunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
                        if (startChunk == null) continue;
                        StructureStart start = startChunk.getStartForStructure(type);
                        if (start == null || !start.isValid()) continue;
                        int[] bounds = StructureService.bounds(start);
                        if (intersects(bounds, request)) {
                            if (entries.size() < MapSnapshot.ENTRY_LIMIT) entries.add(entry(key, bounds, data.isDisabled(key), true, !type.spawnOverrides().isEmpty()));
                            else truncated = true;
                        }
                    }
                }
            }
        }
        var removed = new HashSet<StructureKey>();
        for (Change change : data.history()) {
            if (change.kind() != Change.Kind.UNREGISTER || !change.key().dimension().equals(level.dimension().location())
                    || !removed.add(change.key()) || !intersects(change.bounds(), request)) continue;
            var chunk = level.getChunkSource().getChunkNow(change.key().chunkX(), change.key().chunkZ());
            var type = registry.get(change.key().structure());
            if (chunk == null || type == null) continue;
            var start = chunk.getStartForStructure(type);
            if (start == null || start.isValid()) continue;
            if (entries.size() < MapSnapshot.ENTRY_LIMIT) entries.add(entry(change.key(), change.bounds(), true, false, !type.spawnOverrides().isEmpty()));
            else truncated = true;
        }
        entries.sort(Comparator.comparing(MapSnapshot.Entry::structure).thenComparingInt(MapSnapshot.Entry::startX).thenComparingInt(MapSnapshot.Entry::startZ));
        if (message.getString().isEmpty()) message = Messages.language("en_us", "ui.loaded", loaded);
        return new MapSnapshot(request.sequence(), request.centreX(), request.centreZ(), request.radius(), level.dimension().location().toString(),
                entries, loaded, truncated, token, message, error, request.open(), !data.history().isEmpty());
    }

    private static void send(ServerPlayer player, MapRequest request, String token, Component message, boolean error) {
        if (!player.connection.hasChannel(MapSnapshot.TYPE)) return;
        PacketDistributor.sendToPlayer(player, snapshot(player.serverLevel(), request, token, message, error));
    }
}
