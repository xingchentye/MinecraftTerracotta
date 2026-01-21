package com.multiplayer.ender;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = MinecraftEnder.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = MinecraftEnder.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class MinecraftEnderClient {
    public MinecraftEnderClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        MinecraftEnder.LOGGER.info("MinecraftEnder 客户端设置完成");
    }
}

