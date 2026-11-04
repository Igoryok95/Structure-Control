package io.github.igoryok95.structurecontrol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ControlDataTest {
    private static StructureKey key(String dimension, int x, int z) {
        return new StructureKey(ResourceLocation.parse(dimension), ResourceLocation.withDefaultNamespace("pillager_outpost"), x, z);
    }

    @Test void instanceIdentityIncludesDimensionAndNegativeStartCoordinates() {
        var original = key("minecraft:overworld", -181, 209);
        assertEquals(original, StructureKey.load(original.save()));
        assertNotEquals(original, key("minecraft:the_nether", -181, 209));
        assertNotEquals(original, key("minecraft:overworld", -180, 209));
        assertThrows(IllegalArgumentException.class, () -> StructureKey.load(new CompoundTag()));
    }

    @Test void rulesAndUndoSurviveNativeNbtSerialization() {
        var data = new ControlData();
        var key = key("minecraft:overworld", -181, 209);
        data.setDisabled(key, true);
        var original = new CompoundTag(); original.putString("id", "minecraft:pillager_outpost");
        var change = new Change(UUID.randomUUID(), key, Change.Kind.UNREGISTER, true, true, original,
                new int[]{-2948,65,3292,-2880,81,3360}, "Igoryok95", 1000);
        data.record(change);
        var loaded = ControlData.load(data.save(new CompoundTag(), RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        assertTrue(loaded.isValid());
        assertTrue(loaded.wasLoadedFromDisk());
        assertTrue(loaded.isDisabled(key));
        assertFalse(loaded.isDisabled(key("minecraft:the_nether", -181, 209)));
        assertEquals(change.id(), loaded.history().getFirst().id());
        assertEquals(original, loaded.history().getFirst().originalStart());
        assertArrayEquals(change.bounds(), loaded.history().getFirst().bounds());
        assertThrows(UnsupportedOperationException.class, () -> loaded.disabledKeys().clear());
    }

    @Test void journalSnapshotsCannotBeChangedByCallersAndHistoryIsBounded() {
        var data = new ControlData();
        var start = new CompoundTag(); start.putString("id", "minecraft:pillager_outpost");
        int[] bounds = {0,0,0,10,10,10};
        var first = new Change(UUID.randomUUID(), key("minecraft:overworld",0,0), Change.Kind.UNREGISTER, false,false,start,bounds,"test",0);
        start.putString("id", "INVALID"); bounds[0] = 123;
        first.originalStart().putString("id", "INVALID"); first.bounds()[0] = 456;
        assertEquals("minecraft:pillager_outpost", first.originalStart().getString("id"));
        assertEquals(0, first.bounds()[0]);
        for (int i=0;i<40;i++) data.record(new Change(UUID.randomUUID(),key("minecraft:overworld",i,0),Change.Kind.SPAWNS,false,true,new CompoundTag(),new int[0],"test",i));
        assertEquals(32,data.history().size());
        assertEquals(8,data.history().getFirst().timestamp());
        assertThrows(IllegalStateException.class, () -> data.removeLast(first));
    }

    @Test void unknownSaveSchemaIsReadOnlyAndCannotOverwriteTheSave() {
        var unknown = new CompoundTag(); unknown.putInt("schema", 900);
        var loaded = ControlData.load(unknown,RegistryAccess.EMPTY);
        assertFalse(loaded.isValid());
        assertThrows(IllegalStateException.class, () -> loaded.save(new CompoundTag(),RegistryAccess.EMPTY));
    }
}
