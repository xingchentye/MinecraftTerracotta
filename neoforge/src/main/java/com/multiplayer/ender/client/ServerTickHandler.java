package com.multiplayer.ender.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 服务器 Tick 事件处理器。
 * 用于在客户端逻辑服务器运行时执行周期性任务。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
@EventBusSubscriber(modid = "ender_online", value = Dist.CLIENT)
public class ServerTickHandler {

    /**
     * 当服务器 Tick 发生时调用。
     *
     * @param event Tick 事件
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
         if (server != null) {
             RoomHostLogic.onServerTick(server);
         }
    }
}
