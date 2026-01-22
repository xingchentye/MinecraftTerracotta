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
/**
 * 客户端专用的 Mod 入口类。
 * <p>
 * 处理仅在客户端执行的初始化逻辑，如注册配置屏幕工厂。
 * </p>
 */
public class MinecraftEnderClient {
    /**
     * 构造函数。
     * 注册 Mod 的配置屏幕扩展点，使得用户可以在 Mod 列表中打开配置界面。
     *
     * @param container Mod 容器
     */
    public MinecraftEnderClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    /**
     * 客户端设置事件回调。
     * 在 FMLClientSetupEvent 事件触发时执行，用于客户端特有的初始化。
     *
     * @param event 客户端设置事件
     */
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        MinecraftEnder.LOGGER.info("MinecraftEnder 客户端设置完成");
    }
}

