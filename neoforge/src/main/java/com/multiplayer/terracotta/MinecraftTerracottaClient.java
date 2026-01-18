package com.multiplayer.terracotta;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = MinecraftTerracotta.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = MinecraftTerracotta.MODID, value = Dist.CLIENT)
public class MinecraftTerracottaClient {
    public MinecraftTerracottaClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        MinecraftTerracotta.LOGGER.info("MinecraftTerracotta 客户端设置完成");
    }
}

