package com.multiplayer.terracotta;

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

/**
 * 陶瓦联机 Mod 主类
 */
@Mod(MinecraftTerracotta.MODID)
public class MinecraftTerracotta {
    /**
     * Mod ID
     */
    public static final String MODID = "minecraftterracotta";
    /**
     * 日志记录器
     */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 构造函数，初始化 Mod
     *
     * @param modEventBus Mod 事件总线
     * @param modContainer Mod 容器
     */
    public MinecraftTerracotta(IEventBus modEventBus, ModContainer modContainer) {
        // 注册通用设置方法
        modEventBus.addListener(this::commonSetup);

        // 注册到 NeoForge 事件总线
        NeoForge.EVENT_BUS.register(this);

        // 注册配置
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC, "terracotta-common.toml");
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC, "terracotta.toml");
    }

    /**
     * 通用设置，在 Mod 加载期间运行
     *
     * @param event 通用设置事件
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("陶瓦联机 Mod 已加载 - 通用设置");
    }

    /**
     * 服务器启动事件监听
     *
     * @param event 服务器启动事件
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("陶瓦联机 - 服务器正在启动");
    }
}

