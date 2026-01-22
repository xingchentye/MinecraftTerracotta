package com.multiplayer.ender.client;

import com.multiplayer.ender.client.gui.StartupScreen;
import com.multiplayer.ender.client.gui.EnderDashboard;
import com.multiplayer.ender.network.EnderApiClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 多人游戏菜单处理器（Forge）。
 * 在多人游戏屏幕添加“末影联机”按钮。
 */
@Mod.EventBusSubscriber(modid = "ender_online", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MultiplayerMenuHandlerForge {

    /**
     * 屏幕初始化后事件。
     * 在 JoinMultiplayerScreen 中添加入口按钮。
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




