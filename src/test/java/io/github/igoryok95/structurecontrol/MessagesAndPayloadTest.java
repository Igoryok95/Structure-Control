package io.github.igoryok95.structurecontrol;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class MessagesAndPayloadTest {
    @Test void bothCataloguesHaveMatchingKeysAndFormatArguments() {
        var en = Messages.catalogue("en_us");var ru = Messages.catalogue("ru_ru");
        assertEquals(en.keySet(),ru.keySet());
        en.forEach((key,value)->{
            assertEquals(value.split("%s",-1).length,ru.get(key).split("%s",-1).length,key);
            assertFalse(value.contains("�"),key);assertFalse(ru.get(key).contains("�"),key);
            assertFalse(ru.get(key).contains("\\u"),key);
        });
        assertEquals("Неизвестная структура: test:outpost",Messages.language("ru_ru","error.unknown_structure","test:outpost").getString());
        assertEquals("Unknown structure: test:outpost",Messages.language("de_de","error.unknown_structure","test:outpost").getString());
    }

    @Test void requestsCannotExpandTheScanOrOverflowWorldCoordinates() {
        assertTrue(ControlNetwork.valid(request(6,-181,209)));
        assertFalse(ControlNetwork.valid(request(1000,0,0)));
        assertFalse(ControlNetwork.valid(request(1,0,0)));
        assertFalse(ControlNetwork.valid(request(6,Integer.MIN_VALUE,0)));
        assertFalse(ControlNetwork.valid(request(6,0,Integer.MAX_VALUE)));
    }

    private static MapRequest request(int radius,int x,int z) {
        return new MapRequest(1,MapRequest.Action.SNAPSHOT,x,z,radius,"",0,0,"",false);
    }

    @Test void optionalMapPayloadsPreserveCoordinatesStateAndConfirmation() {
        var request = new MapRequest(12,MapRequest.Action.CONFIRM,-181,209,6,"minecraft:pillager_outpost",-181,209,"abcdef123456",false);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            MapRequest.CODEC.encode(buffer,request);
            assertEquals(request,MapRequest.CODEC.decode(buffer));
            var entry = new MapSnapshot.Entry("minecraft:pillager_outpost",-181,209,-2948,65,3292,-2880,81,3360,true,false,true);
            var snapshot = new MapSnapshot(12,-181,209,6,"minecraft:overworld",List.of(entry),36,false,"abcdef123456",
                    Component.literal("test"),false,false,true);
            MapSnapshot.CODEC.encode(buffer,snapshot);
            var read = MapSnapshot.CODEC.decode(buffer);
            assertEquals(snapshot.entries(),read.entries());assertEquals(snapshot.token(),read.token());
            assertEquals(snapshot.message().getString(),read.message().getString());assertTrue(read.canUndo());
            assertTrue(entry.covers(-2948,3292));assertFalse(entry.covers(-2879,3361));
        } finally { buffer.release(); }
    }

    @Test void oversizedServerSnapshotsAreRejectedBeforeAllocation() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            buffer.writeVarInt(1);buffer.writeVarInt(0);buffer.writeVarInt(0);buffer.writeVarInt(6);
            buffer.writeUtf("minecraft:overworld");buffer.writeVarInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class,()->MapSnapshot.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
}
