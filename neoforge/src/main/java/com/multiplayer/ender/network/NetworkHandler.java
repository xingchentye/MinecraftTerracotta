package com.multiplayer.ender.network;

import org.slf4j.Logger;

import com.multiplayer.ender.MinecraftEnder;
import com.multiplayer.ender.client.gui.ConnectingScreen;
import com.multiplayer.ender.client.gui.LobbyScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.multiplayer.ender.client.gui.StartupScreen;
import com.multiplayer.ender.client.gui.EnderDashboard;

public class NetworkHandler {
    private static final Logger LOGGER = MinecraftEnder.LOGGER;

    public static void init() {
        LOGGER.info("初始化网络通信模块...");
    }

    public static void onConnectButtonClicked() {
        Minecraft minecraft = Minecraft.getInstance();
        if (EnderApiClient.hasDynamicPort()) {
            EnderApiClient.checkHealth().thenAccept(ok -> {
                minecraft.execute(() -> {
                    if (ok) {
                        minecraft.setScreen(new EnderDashboard(minecraft.screen));
                    } else {
                        EnderApiClient.clearDynamicPort();
                        minecraft.setScreen(new StartupScreen(minecraft.screen));
                    }
                });
            });
            return;
        }

        minecraft.setScreen(new StartupScreen(minecraft.screen));
    }

    public static void connectToServer(String host, int port, net.minecraft.client.gui.screens.Screen parentScreen) {
        LOGGER.info("正在连接到 {}:{}...", host, port);
        
        Minecraft minecraft = Minecraft.getInstance();
        ConnectingScreen connectingScreen = new ConnectingScreen(parentScreen);
        minecraft.setScreen(connectingScreen);
        
        NetworkClient.getInstance().connect(host, port).whenComplete((result, ex) -> {
            minecraft.execute(() -> {
                if (ex != null) {
                    LOGGER.error("连接失败", ex);
                    connectingScreen.setStatus(Component.literal("连接失败: " + ex.getMessage()));
                } else {
                    LOGGER.info("连接成功");
                    minecraft.setScreen(new LobbyScreen(parentScreen));
                }
            });
        });
    }
}


