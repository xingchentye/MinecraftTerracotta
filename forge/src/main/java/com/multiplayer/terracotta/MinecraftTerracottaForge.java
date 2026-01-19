package com.multiplayer.terracotta;

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

@Mod(MinecraftTerracottaForge.MODID)
public class MinecraftTerracottaForge {
    public static final String MODID = "minecraftterracotta";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MinecraftTerracottaForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ConfigForge.SPEC, "terracotta-common.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ConfigForge.CLIENT_SPEC, "terracotta.toml");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("陶瓦联机 Forge Mod 已加载 - 通用设置");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("陶瓦联机 Forge - 服务器正在启动");
    }
}
