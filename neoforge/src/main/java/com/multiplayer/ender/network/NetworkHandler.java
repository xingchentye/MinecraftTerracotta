package com.multiplayer.ender.network;

import org.slf4j.Logger;

import com.multiplayer.ender.MinecraftEnder;
import com.multiplayer.ender.client.gui.ConnectingScreen;
import com.multiplayer.ender.client.gui.LobbyScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.multiplayer.ender.client.gui.StartupScreen;
import com.multiplayer.ender.client.gui.EnderDashboard;

/**
 * 网络操作辅助类。
 * <p>
 * 提供与网络连接相关的上层业务逻辑封装，主要用于 UI 层调用。
 * 包括：
 * 1. 处理“末影联机”入口点击事件（检查后端健康状态并跳转对应屏幕）。
 * 2. 处理连接到服务器的逻辑（封装 {@link NetworkClient} 的调用）。
 * </p>
 */
public class NetworkHandler {
    private static final Logger LOGGER = MinecraftEnder.LOGGER;

    /**
     * 初始化网络模块。
     * 目前仅记录日志，可用于注册网络通道等初始化操作。
     */
    public static void init() {
        LOGGER.info("初始化网络通信模块...");
    }

    /**
     * 当用户点击主菜单的“末影联机”按钮时调用。
     * <p>
     * 逻辑流程：
     * 1. 检查是否已记录动态端口（即后端是否可能正在运行）。
     * 2. 如果有端口，检查后端健康状态 {@link EnderApiClient#checkHealth()}。
     * 3. 如果健康，跳转到仪表盘 {@link EnderDashboard}。
     * 4. 如果不健康或无端口，跳转到启动屏幕 {@link StartupScreen} 以启动后端进程。
     * </p>
     */
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

    /**
     * 连接到指定服务器。
     * <p>
     * 启动连接流程，显示连接中屏幕 {@link ConnectingScreen}。
     * 调用 {@link NetworkClient#connect} 执行实际连接。
     * 连接成功跳转大厅 {@link LobbyScreen}，失败显示错误信息。
     * </p>
     *
     * @param host         目标主机地址
     * @param port         目标端口
     * @param parentScreen 父屏幕，用于返回
     */
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


