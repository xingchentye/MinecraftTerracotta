package com.multiplayer.ender.client;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

public class ServerTickHandler {
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            RoomHostLogic.onServerTick(server);
        });
    }
}
