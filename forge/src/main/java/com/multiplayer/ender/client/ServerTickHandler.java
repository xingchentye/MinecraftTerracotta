package com.multiplayer.ender.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 服务器 Tick 事件处理器。
 * 监听 Forge 的服务器 Tick 事件，用于驱动房间状态同步逻辑。
 */
@Mod.EventBusSubscriber(modid = "ender_online", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerTickHandler {

    /**
     * 当服务器 Tick 发生时调用。
     * 在 Tick 结束阶段调用 RoomHostLogic 的处理方法。
     *
     * @param event 服务器 Tick 事件
     */
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
