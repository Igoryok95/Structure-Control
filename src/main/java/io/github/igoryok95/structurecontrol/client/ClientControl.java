package io.github.igoryok95.structurecontrol.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.igoryok95.structurecontrol.ControlNetwork;
import io.github.igoryok95.structurecontrol.MapRequest;
import io.github.igoryok95.structurecontrol.MapSnapshot;
import io.github.igoryok95.structurecontrol.StructureControl;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = StructureControl.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientControl {
    private static final KeyMapping MAP = new KeyMapping("key.structure_control.map", InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), "key.categories.structure_control");

    private ClientControl() {}

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ControlNetwork.CLIENT_HANDLER = ClientControl::receive);
    }

    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(MAP); }

    private static void receive(MapSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().location().toString().equals(snapshot.dimension())) return;
        if (snapshot.open()) minecraft.setScreen(new StructureMapScreen(snapshot));
        else if (minecraft.screen instanceof StructureMapScreen screen) screen.apply(snapshot);
    }

    @EventBusSubscriber(modid = StructureControl.MOD_ID, value = Dist.CLIENT)
    public static final class Ticks {
        @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            while (MAP.consumeClick()) {
                if (minecraft.player == null || minecraft.getConnection() == null) continue;
                if (!minecraft.getConnection().hasChannel(MapRequest.TYPE)) {
                    minecraft.player.displayClientMessage(Component.translatable("structure_control.error.server_missing"), false);
                    continue;
                }
                var pos = minecraft.player.chunkPosition();
                PacketDistributor.sendToServer(new MapRequest(0, MapRequest.Action.SNAPSHOT, pos.x, pos.z, 6, "", 0, 0, "", true));
            }
        }
    }
}
