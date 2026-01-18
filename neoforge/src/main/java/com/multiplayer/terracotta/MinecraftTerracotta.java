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

@Mod(MinecraftTerracotta.MODID)
public class MinecraftTerracotta {
    public static final String MODID = "minecraftterracotta";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MinecraftTerracotta(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC, "terracotta-common.toml");
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC, "terracotta.toml");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("陶瓦联机 Mod 已加载 - 通用设置");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("陶瓦联机 - 服务器正在启动");
    }
}

