package io.github.igoryok95.structurecontrol;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasurePieces;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StructureControl.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NativeStructureTests {
    private static final ResourceLocation OUTPOST = ResourceLocation.withDefaultNamespace("pillager_outpost");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public static StructureService.Located fixture(ServerLevel level,BlockPos pos) {
        Structure type=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(OUTPOST);
        LevelChunk chunk=level.getChunkAt(pos);
        var start=new StructureStart(type,chunk.getPos(),0,new PiecesContainer(List.of(new BuriedTreasurePieces.BuriedTreasurePiece(pos))));
        chunk.setStartForStructure(type,start);
        chunk.addReferenceForStructure(type,chunk.getPos().toLong());
        level.onStructureStartsAvailable(chunk);
        return new StructureService.Located(new StructureKey(level.dimension().location(),OUTPOST,chunk.getPos().x,chunk.getPos().z),type,start,chunk);
    }

    private static boolean pillagers(ServerLevel level,BlockPos pos) {
        return level.getChunkSource().getGenerator().getMobsAt(level.getBiome(pos),level.structureManager(),MobCategory.MONSTER,pos)
                .unwrap().stream().anyMatch(entry->entry.type==EntityType.PILLAGER);
    }

    private static void replace(StructureService.Located located, BlockPos pos) {
        located.chunk().setStartForStructure(located.structure(),new StructureStart(located.structure(),located.chunk().getPos(),0,
                new PiecesContainer(List.of(new BuriedTreasurePieces.BuriedTreasurePiece(pos)))));
    }

    private static void failure(GameTestHelper h,Runnable action,String key) {
        try { action.run();h.fail("Expected failure: "+key); }
        catch(ControlFailure failure) { h.assertTrue(failure.key().equals(key),"Unexpected error: "+failure.key()); }
    }

    @GameTest(template="empty")
    public static void reversibleFilteringKeepsDetectionAndOtherInstances(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(5,2,5));var other=pos.east(20);
        var a=fixture(level,pos);var b=fixture(level,other);var service=StructureControl.service(level.getServer());
        h.assertTrue(!a.key().equals(b.key()),"Fixtures need different start chunks");
        h.assertTrue(pillagers(level,pos)&&pillagers(level,other),"Native outpost lists are active");
        service.setSpawns(a,false,"test");
        h.assertTrue(!pillagers(level,pos),"Disabled instance falls back to biome spawns");
        h.assertTrue(pillagers(level,other),"Other instance stays enabled");
        h.assertTrue(level.structureManager().getStructureAt(pos,a.structure()).isValid(),"Spawn filtering preserves registration");
        service.setSpawns(a,true,"test");
        h.assertTrue(pillagers(level,pos),"Enabling restores native spawn list");h.succeed();
    }

    @GameTest(template="empty")
    public static void unregisterAndUndoPreserveChestBlocksAndRefreshNativeCache(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(5,2,5));var located=fixture(level,pos);
        level.setBlockAndUpdate(pos,Blocks.CHEST.defaultBlockState());
        var chest=(ChestBlockEntity)level.getBlockEntity(pos);chest.setItem(0,new ItemStack(Items.DIAMOND,17));
        var service=StructureControl.service(level.getServer());
        var placement=new RandomSpreadStructurePlacement(32,8,RandomSpreadType.LINEAR,42);
        var key=located.key();
        var before=level.structureManager().checkStructurePresence(located.chunk().getPos(),located.structure(),placement,false);
        h.assertTrue(before==StructureCheckResult.START_PRESENT,"Native cache starts populated");
        var preview=service.preview(located,OWNER);
        h.assertTrue(located.chunk().getStartForStructure(located.structure()).isValid(),"Preview does not mutate chunks");
        service.confirm(OWNER,preview.token(),"test");
        h.assertTrue(!pillagers(level,pos),"Unregistered start no longer overrides mobs");
        h.assertTrue(!level.structureManager().getStructureAt(pos,located.structure()).isValid(),"Detection sees no valid start");
        h.assertTrue(level.structureManager().checkStructurePresence(located.chunk().getPos(),located.structure(),placement,false)==StructureCheckResult.START_NOT_PRESENT,"Locate cache is invalidated");
        h.assertTrue(level.getBlockState(pos).is(Blocks.CHEST)&&((ChestBlockEntity)level.getBlockEntity(pos)).getItem(0).getCount()==17,"Inventory and block are preserved");
        service.undo("test");
        h.assertTrue(pillagers(level,pos),"Undo restores structure spawn table");
        h.assertTrue(level.structureManager().checkStructurePresence(located.chunk().getPos(),located.structure(),placement,false)==StructureCheckResult.START_PRESENT,"Undo refreshes locate cache");
        h.assertTrue(((ChestBlockEntity)level.getBlockEntity(pos)).getItem(0).getCount()==17,"Undo preserves items");h.succeed();
    }

    @GameTest(template="empty")
    public static void confirmationsAreOwnerBoundExpireAndDetectChangedStructures(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(5,2,5));var located=fixture(level,pos);
        var clock=new AtomicLong(1000);var data=new ControlData();var service=new StructureService(level.getServer(),data,clock::get);
        var preview=service.preview(located,OWNER);
        failure(h,()->service.confirm(UUID.randomUUID(),preview.token(),"test"),"error.confirmation_missing");
        failure(h,()->service.confirm(OWNER,"wrong","test"),"error.confirmation_missing");
        clock.addAndGet(StructureService.CONFIRM_MILLIS+1);
        failure(h,()->service.confirm(OWNER,preview.token(),"test"),"error.confirmation_expired");
        h.assertTrue(located.chunk().getStartForStructure(located.structure()).isValid(),"Expired preview keeps original start");
        var next=service.preview(located,OWNER);replace(located,pos.east(1));
        failure(h,()->service.confirm(OWNER,next.token(),"test"),"error.structure_changed");
        h.assertTrue(data.history().isEmpty(),"Rejected operations do not enter history");h.succeed();
    }

    @GameTest(template="empty")
    public static void undoJournalCanBeReloadedAndRefusesConflictingStarts(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(5,2,5));var located=fixture(level,pos);
        var data=new ControlData();var service=new StructureService(level.getServer(),data,System::currentTimeMillis);
        var preview=service.preview(located,OWNER);service.confirm(OWNER,preview.token(),"test");
        var loaded=ControlData.load(data.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
        var reloadedService=new StructureService(level.getServer(),loaded,System::currentTimeMillis);
        reloadedService.undo("test");
        h.assertTrue(pillagers(level,pos),"Reloaded native NBT restores valid structure");
        var again=reloadedService.preview(reloadedService.byKey(level,located.key()),OWNER);
        reloadedService.confirm(OWNER,again.token(),"test");replace(located,pos.east(1));
        failure(h,()->reloadedService.undo("test"),"error.structure_changed");
        h.assertTrue(loaded.history().size()==1,"Conflicting undo preserves recovery record");h.succeed();
    }

    @GameTest(template="empty")
    public static void unloadedCommandsDoNotGenerateChunksAndNonOperatorsCannotEdit(GameTestHelper h) {
        var level=h.getLevel();var service=StructureControl.service(level.getServer());var far=new BlockPos(25_000_000,70,25_000_000);
        h.assertTrue(level.getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"Target starts unloaded");
        failure(h,()->service.select(level,far,OUTPOST),"error.chunk_unloaded");
        h.assertTrue(level.getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"Selection did not load or generate the target");
        var source=level.getServer().createCommandSourceStack().withLevel(level).withPermission(1);
        try {level.getServer().getCommands().getDispatcher().execute("structurecontrol undo",source);h.fail("Non-operator command unexpectedly ran");}
        catch(CommandSyntaxException expected) {h.succeed();}
    }

    @GameTest(template="empty")
    public static void mapSnapshotsReflectStateWithoutLoadingUnknownChunks(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(5,2,5));var located=fixture(level,pos);
        var service=StructureControl.service(level.getServer());var cp=located.chunk().getPos();
        var request=new MapRequest(1,MapRequest.Action.SNAPSHOT,cp.x,cp.z,2,"",0,0,"",false);
        var enabled=ControlNetwork.snapshot(level,request,"",net.minecraft.network.chat.Component.empty(),false);
        h.assertTrue(enabled.entries().stream().anyMatch(e->e.startX()==cp.x&&e.startZ()==cp.z&&e.registered()&&!e.disabled()),"Map includes active instance");
        service.setSpawns(located,false,"test");
        var disabled=ControlNetwork.snapshot(level,request,"",net.minecraft.network.chat.Component.empty(),false);
        h.assertTrue(disabled.entries().stream().anyMatch(e->e.startX()==cp.x&&e.startZ()==cp.z&&e.disabled()),"Map includes disabled state");
        var preview=service.preview(located,OWNER);service.confirm(OWNER,preview.token(),"test");
        var removed=ControlNetwork.snapshot(level,request,"",net.minecraft.network.chat.Component.empty(),false);
        h.assertTrue(removed.entries().stream().anyMatch(e->e.startX()==cp.x&&e.startZ()==cp.z&&!e.registered()),"Map includes undoable removed instance");
        service.undo("test");service.setSpawns(service.byKey(level,located.key()),true,"test");
        h.succeed();
    }
}
