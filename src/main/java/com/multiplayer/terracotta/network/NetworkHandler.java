package com.multiplayer.terracotta.network;

import org.slf4j.Logger;

import com.multiplayer.terracotta.MinecraftTerracotta;
import com.multiplayer.terracotta.client.gui.ConnectingScreen;
import com.multiplayer.terracotta.client.gui.LobbyScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.multiplayer.terracotta.client.gui.StartupScreen;
import com.multiplayer.terracotta.client.gui.TerracottaDashboard;

/**
 * 网络通信模块核心处理类
 * 负责处理客户端与服务器之间的连接和数据传输
 * 
 * @author xingchentye
 */
public class NetworkHandler {
    /** 日志记录器 */
    private static final Logger LOGGER = MinecraftTerracotta.LOGGER;

    /**
     * 初始化网络模块
     */
    public static void init() {
        LOGGER.info("初始化网络通信模块...");
        // 预留: 初始化网络通道逻辑
    }

    /**
     * 处理"陶瓦联机"按钮点击事件
     * 检查后端服务状态并决定进入仪表盘还是启动界面
     */
    public static void onConnectButtonClicked() {
        Minecraft minecraft = Minecraft.getInstance();
        // 优先检查动态端口，如果存在则说明服务已启动
        if (TerracottaApiClient.hasDynamicPort()) {
            // 异步检查健康状态
            TerracottaApiClient.checkHealth().thenAccept(ok -> {
                minecraft.execute(() -> {
                    if (ok) {
                        // 健康状态良好，进入仪表盘
                        minecraft.setScreen(new TerracottaDashboard(minecraft.screen));
                    } else {
                        // 健康检查失败，清除端口并重新启动
                        TerracottaApiClient.clearDynamicPort();
                        minecraft.setScreen(new StartupScreen(minecraft.screen));
                    }
                });
            });
            return;
        }

        // 未连接后端，进入启动界面
        minecraft.setScreen(new StartupScreen(minecraft.screen));
    }

    /**
     * 连接到指定服务器
     * 
     * @param host 服务器地址
     * @param port 服务器端口
     * @param parentScreen 父界面
     */
    public static void connectToServer(String host, int port, net.minecraft.client.gui.screens.Screen parentScreen) {
        LOGGER.info("正在连接到 {}:{}...", host, port);
        
        Minecraft minecraft = Minecraft.getInstance();
        ConnectingScreen connectingScreen = new ConnectingScreen(parentScreen);
        minecraft.setScreen(connectingScreen);
        
        // 启动异步连接
        NetworkClient.getInstance().connect(host, port).whenComplete((result, ex) -> {
            // 回到主线程更新 UI
            minecraft.execute(() -> {
                if (ex != null) {
                    LOGGER.error("连接失败", ex);
                    connectingScreen.setStatus(Component.literal("连接失败: " + ex.getMessage()));
                } else {
                    LOGGER.info("连接成功");
                    // 连接成功，切换到大厅界面
                    minecraft.setScreen(new LobbyScreen(parentScreen));
                }
            });
        });
    }
}
