package com.multiplayer.ender;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(MinecraftEnder.MODID)
/**
 * 末影联机 Mod 主入口类。
 * <p>
 * 负责 Mod 的生命周期管理、配置注册和事件总线监听。
 * 这是 NeoForge Mod 的核心入口。
 * </p>
 */
public class MinecraftEnder {
    /** Mod ID */
    public static final String MODID = "ender_online";
    /** 全局日志记录器 */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 构造函数。
     * 注册 Mod 事件总线监听器和配置文件。
     *
     * @param modEventBus Mod 事件总线
     * @param modContainer Mod 容器
     */
    public MinecraftEnder(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC, "ender-common.toml");
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC, "ender.toml");
    }

    /**
     * 通用设置阶段回调。
     * 在 FMLCommonSetupEvent 事件触发时执行，用于跨端（客户端+服务端）的通用初始化。
     *
     * @param event 通用设置事件
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("末影联机 Mod 已加载 - 通用设置");
    }

    /**
     * 服务器启动事件回调。
     * 监听 Minecraft 服务器启动事件。
     *
     * @param event 服务器启动事件
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("末影联机 - 服务器正在启动");
    }
}




