package com.multiplayer.ender.client;

import com.multiplayer.ender.client.gui.StartupScreen;
import com.multiplayer.ender.client.gui.EnderDashboard;
import com.multiplayer.ender.network.EnderApiClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * 多人游戏菜单处理器。
 * 在多人游戏屏幕上添加“末影联机”入口按钮。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
@EventBusSubscriber(modid = "ender_online", value = Dist.CLIENT)
public class MultiplayerMenuHandler {

    /**
     * 当屏幕初始化时调用。
     *
     * @param event 屏幕初始化事件
     */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (screen instanceof JoinMultiplayerScreen joinScreen) {
            int width = joinScreen.width;
            int buttonWidth = 120;
            int x = width - buttonWidth - 5;
            int y = 5;

            Button enderBtn = Button.builder(Component.literal("末影联机"), button -> {
                Minecraft mc = Minecraft.getInstance();
                if (EnderApiClient.hasDynamicPort()) {
                    mc.setScreen(new EnderDashboard(joinScreen));
                } else {
                    mc.setScreen(new StartupScreen(joinScreen, () -> {
                        mc.setScreen(new EnderDashboard(joinScreen));
                    }));
                }
            }).bounds(x, y, buttonWidth, 20).build();

            event.addListener(enderBtn);
        }
    }
}




