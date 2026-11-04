package io.github.igoryok95.structurecontrol.client;

import io.github.igoryok95.structurecontrol.StructureControl;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid=StructureControl.MOD_ID,value=Dist.CLIENT)
public final class ClientSmoke {
    private static boolean prepared;
    private static int ticks;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("structurecontrol.clientSmoke")) return;
        var minecraft=Minecraft.getInstance();
        minecraft.getWindow().setTitle("Structure Control - isolated UI test");
        if (prepared||minecraft.player==null||minecraft.level==null||minecraft.getSingleplayerServer()==null||++ticks<40) return;
        prepared=true;
        var id=minecraft.player.getUUID();var server=minecraft.getSingleplayerServer();
        server.execute(()->{
            var player=server.getPlayerList().getPlayer(id);
            if (player!=null) {
                server.getPlayerList().op(player.getGameProfile());
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(8,105,8);
                StructureControl.LOGGER.info("STRUCTURE_CONTROL_CLIENT_SMOKE_READY");
            }
        });
    }
}
