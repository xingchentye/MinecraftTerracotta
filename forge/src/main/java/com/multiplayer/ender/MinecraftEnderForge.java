package com.multiplayer.ender;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 末影联机 Forge 模组主类。
 * 负责模组的初始化、配置注册和事件总线注册。
 */
@Mod(MinecraftEnderForge.MODID)
public class MinecraftEnderForge {
    public static final String MODID = "ender_online";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 构造函数。
     * 注册配置和事件监听器。
     */
    @SuppressWarnings("removal")
    public MinecraftEnderForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ConfigForge.SPEC, "ender-common.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ConfigForge.CLIENT_SPEC, "ender.toml");
    }

    /**
     * 通用设置阶段。
     *
     * @param event FML通用设置事件
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("末影联机 Forge Mod 已加载 - 通用设置");
    }

    /**
     * 服务器启动事件。
     *
     * @param event 服务器启动事件
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("末影联机 Forge - 服务器正在启动");
    }
}



