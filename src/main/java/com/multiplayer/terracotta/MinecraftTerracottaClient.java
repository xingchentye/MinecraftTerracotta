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

// 这个类不会在专用服务器上加载。从这里访问客户端代码是安全的。
@Mod(value = MinecraftTerracotta.MODID, dist = Dist.CLIENT)
// 你可以使用 EventBusSubscriber 自动注册所有带有 @SubscribeEvent 注解的静态方法
@EventBusSubscriber(modid = MinecraftTerracotta.MODID, value = Dist.CLIENT)
public class MinecraftTerracottaClient {
    public MinecraftTerracottaClient(ModContainer container) {
        // 允许 NeoForge 为此 mod 的配置创建配置屏幕。
        // 通过转到 Mods 屏幕 > 点击你的 mod > 点击配置来访问配置屏幕。
        // 别忘了在 en_us.json 文件中为你的配置选项添加翻译。
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // 一些客户端设置代码
        MinecraftTerracotta.LOGGER.info("来自客户端设置的问候");
        MinecraftTerracotta.LOGGER.info("MINECRAFT 名称 >> {}", Minecraft.getInstance().getUser().getName());
    }
}
