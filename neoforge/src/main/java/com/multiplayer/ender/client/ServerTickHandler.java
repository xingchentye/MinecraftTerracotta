package com.multiplayer.ender.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "ender_online", value = Dist.CLIENT)
public class ServerTickHandler {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
         if (server != null) {
             RoomHostLogic.onServerTick(server);
         }
    }
}
