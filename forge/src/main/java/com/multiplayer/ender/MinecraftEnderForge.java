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

@Mod(MinecraftEnderForge.MODID)
public class MinecraftEnderForge {
    public static final String MODID = "ender_online";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MinecraftEnderForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ConfigForge.SPEC, "ender-common.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ConfigForge.CLIENT_SPEC, "ender.toml");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("末影联机 Forge Mod 已加载 - 通用设置");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("末影联机 Forge - 服务器正在启动");
    }
}



