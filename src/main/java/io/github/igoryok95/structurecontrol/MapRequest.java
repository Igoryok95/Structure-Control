package io.github.igoryok95.structurecontrol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MapRequest(int sequence, Action action, int centreX, int centreZ, int radius,
                         String structure, int startX, int startZ, String token, boolean open) implements CustomPacketPayload {
    public enum Action { SNAPSHOT, SPAWNS_OFF, SPAWNS_ON, PREVIEW, CONFIRM, CANCEL, UNDO }
    public static final Type<MapRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StructureControl.MOD_ID, "map_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MapRequest> CODEC = StreamCodec.of(MapRequest::encode, MapRequest::decode);

    private static void encode(RegistryFriendlyByteBuf buf, MapRequest request) {
        buf.writeVarInt(request.sequence);
        buf.writeEnum(request.action);
        buf.writeVarInt(request.centreX); buf.writeVarInt(request.centreZ); buf.writeVarInt(request.radius);
        buf.writeUtf(request.structure, 256);
        buf.writeVarInt(request.startX); buf.writeVarInt(request.startZ);
        buf.writeUtf(request.token, 64);
        buf.writeBoolean(request.open);
    }

    private static MapRequest decode(RegistryFriendlyByteBuf buf) {
        return new MapRequest(buf.readVarInt(), buf.readEnum(Action.class), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readUtf(256), buf.readVarInt(), buf.readVarInt(), buf.readUtf(64), buf.readBoolean());
    }

    @Override public Type<MapRequest> type() { return TYPE; }
}
