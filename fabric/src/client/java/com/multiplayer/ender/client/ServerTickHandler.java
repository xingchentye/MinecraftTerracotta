package com.multiplayer.ender.client;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * 服务器 Tick 事件处理器（Fabric）。
 * 注册 Fabric 的 ServerTickEvents，用于驱动房间状态同步逻辑。
 */
public class ServerTickHandler {
    /**
     * 初始化事件监听器。
     * 注册 END_SERVER_TICK 回调。
     */
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            RoomHostLogic.onServerTick(server);
        });
    }
}
