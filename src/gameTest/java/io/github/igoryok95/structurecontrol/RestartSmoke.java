package io.github.igoryok95.structurecontrol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid=StructureControl.MOD_ID)
public final class RestartSmoke {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        String mode=System.getProperty("structurecontrol.restartSmoke","");
        if (mode.isEmpty()) return;
        MinecraftServer server=event.getServer();
        try {
            var path=server.getWorldPath(LevelResource.ROOT).resolve("structure-control-smoke.json");
            var level=server.overworld();var service=StructureControl.service(server);
            if (mode.equals("write")) {
                BlockPos a=new BlockPos(8,80,8), b=new BlockPos(72,80,8), c=new BlockPos(-56,80,8);
                var first=NativeStructureTests.fixture(level,a);
                var second=NativeStructureTests.fixture(level,b);
                NativeStructureTests.fixture(level,c);
                level.setBlockAndUpdate(b,Blocks.CHEST.defaultBlockState());
                ((ChestBlockEntity)level.getBlockEntity(b)).setItem(0,new ItemStack(Items.DIAMOND,17));
                service.setSpawns(first,false,"restart-test");
                var pending=service.preview(second,new UUID(0,1));
                service.confirm(new UUID(0,1),pending.token(),"restart-test");
                JsonObject data=new JsonObject();data.addProperty("a_x",a.getX());data.addProperty("a_z",a.getZ());
                data.addProperty("b_x",b.getX());data.addProperty("b_z",b.getZ());
                Files.writeString(path,data.toString(),StandardCharsets.UTF_8);
                StructureControl.LOGGER.info("STRUCTURE_CONTROL_RESTART_WRITE_OK");
            } else if (mode.equals("verify")) {
                JsonObject data=JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();
                var a=new BlockPos(data.get("a_x").getAsInt(),80,data.get("a_z").getAsInt());
                var b=new BlockPos(data.get("b_x").getAsInt(),80,data.get("b_z").getAsInt());
                var type=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.withDefaultNamespace("pillager_outpost"));
                var ac=level.getChunkAt(a);var bc=level.getChunkAt(b);
                if (!service.data().wasLoadedFromDisk()||!service.data().isDisabled(new StructureKey(level.dimension().location(),ResourceLocation.withDefaultNamespace("pillager_outpost"),ac.getPos().x,ac.getPos().z))) throw new AssertionError("Disabled rule did not survive disk reload");
                if (bc.getStartForStructure(type).isValid()) throw new AssertionError("Unregistered start did not survive disk reload");
                service.undo("restart-test");
                if (!bc.getStartForStructure(type).isValid()) throw new AssertionError("Persisted undo did not restore start");
                if (((ChestBlockEntity)level.getBlockEntity(b)).getItem(0).getCount()!=17) throw new AssertionError("Persisted undo changed chest items");
                var second=service.byKey(level,new StructureKey(level.dimension().location(),ResourceLocation.withDefaultNamespace("pillager_outpost"),bc.getPos().x,bc.getPos().z));
                var preview=service.preview(second,new UUID(0,1));service.confirm(new UUID(0,1),preview.token(),"restart-test");
                StructureControl.LOGGER.info("STRUCTURE_CONTROL_RESTART_VERIFY_OK");
            } else throw new IllegalArgumentException("Unknown smoke mode");
        } catch(Exception|AssertionError exception) {
            StructureControl.LOGGER.error("STRUCTURE_CONTROL_RESTART_FAILED",exception);
        } finally { server.halt(false); }
    }
}
