package com.multiplayer.terracotta.client.gui;

import com.multiplayer.terracotta.MinecraftTerracotta;
import com.multiplayer.terracotta.network.NetworkHandler;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * 多人游戏界面处理器
 * 负责在 Minecraft 多人游戏菜单中添加自定义 UI 元素
 */
@EventBusSubscriber(modid = MinecraftTerracotta.MODID, value = Dist.CLIENT)
public class MultiplayerMenuHandler {

    /**
     * 监听屏幕初始化后事件
     * 在多人游戏界面添加"陶瓦联机"按钮
     *
     * @param event 屏幕初始化后事件
     */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof JoinMultiplayerScreen) {
            // 将按钮放置在界面右上角，避免遮挡主要功能
            int buttonWidth = 100;
            int buttonHeight = 20;
            int x = event.getScreen().width - buttonWidth - 10;
            int y = 10;

            Button terracottaButton = Button.builder(
                    Component.translatable("menu.minecraftterracotta.title"),
                    (button) -> {
                        NetworkHandler.onConnectButtonClicked();
                    })
                    .bounds(x, y, buttonWidth, buttonHeight)
                    .build();

            event.addListener(terracottaButton);
        }
    }
}
