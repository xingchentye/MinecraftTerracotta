package com.multiplayer.ender.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = "ender_online", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerTickHandler {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
             MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
             if (server != null) {
                 RoomHostLogic.onServerTick(server);
             }
        }
    }
}
