package io.github.igoryok95.structurecontrol;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

@Mod(StructureControl.MOD_ID)
public final class StructureControl {
    public static final String MOD_ID = "structure_control";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<MinecraftServer, StructureService> SERVICES = new ConcurrentHashMap<>();

    public StructureControl(IEventBus modBus) {
        modBus.addListener(ControlNetwork::register);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::stopped);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        ControlCommands.register(event.getDispatcher());
    }

    private void started(ServerStartedEvent event) {
        service(event.getServer());
    }

    private void stopped(ServerStoppedEvent event) {
        SERVICES.remove(event.getServer());
        ControlNetwork.stopped(event.getServer());
    }

    public static StructureService service(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Structure commands must run on the server thread");
        return SERVICES.computeIfAbsent(server, key -> {
            ControlData data = key.overworld().getDataStorage().computeIfAbsent(ControlData.FACTORY, ControlData.FILE);
            if (!data.wasLoadedFromDisk() && Files.exists(key.getWorldPath(LevelResource.ROOT).resolve("data/" + ControlData.FILE + ".dat"))) {
                data.makeReadOnly();
                LOGGER.error("Structure Control save exists but could not be loaded; editing is disabled");
            }
            return new StructureService(key, data, System::currentTimeMillis);
        });
    }

    public static boolean disabled(ServerLevel level, StructureStart start) {
        StructureService service = SERVICES.get(level.getServer());
        if (service == null || !start.isValid()) return false;
        var id = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(start.getStructure());
        if (id == null) return false;
        var pos = start.getChunkPos();
        return service.data().isDisabled(new StructureKey(level.dimension().location(), id, pos.x, pos.z));
    }
}
