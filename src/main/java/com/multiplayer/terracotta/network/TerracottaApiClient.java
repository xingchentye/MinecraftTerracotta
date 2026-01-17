package com.multiplayer.terracotta.network;

import com.multiplayer.terracotta.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.Gson;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * 陶瓦 HTTP API 客户端
 * 用于通过 HTTP 协议控制后端服务 (启动、停止、状态查询等)
 * 
 * @author xingchentye
 */
public class TerracottaApiClient {
    /** 日志记录器 */
    private static final Logger LOGGER = LoggerFactory.getLogger(TerracottaApiClient.class);
    /** Gson 实例 */
    private static final Gson GSON = new Gson();
    /** 动态端口 (由后端启动时分配) */
    private static int dynamicPort = -1;
    private static final int DEFAULT_PORT = 25566;

    /**
     * 设置动态端口 (通常由 ProcessLauncher 从输出中捕获)
     * 
     * @param port 端口号
     */
    public static void setPort(int port) {
        dynamicPort = port;
        LOGGER.info("已更新 API 目标端口为: {}", port);
    }

    /**
     * 清除动态端口信息
     * 当连接断开或服务停止时调用
     */
    public static void clearDynamicPort() {
        dynamicPort = -1;
    }

    /**
     * 检查是否已设置动态端口
     * 
     * @return 如果已设置返回 true，否则返回 false
     */
    public static boolean hasDynamicPort() {
        return dynamicPort > 0;
    }

    /**
     * 获取当前使用的端口
     * 优先使用动态端口，如果未设置则使用配置中的默认端口
     * 
     * @return 端口号
     */
    public static int getPort() {
        return dynamicPort > 0 ? dynamicPort : DEFAULT_PORT;
    }

    /** HTTP 客户端实例，配置超时时间为 2 秒 */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    /**
     * 获取基础 URL
     * 
     * @return http://127.0.0.1:port
     */
    private static String getBaseUrl() {
        // 后端始终运行在本地
        return String.format("http://127.0.0.1:%d", getPort());
    }

    //  API 方法 

    /**
     * 获取后端元数据信息
     * 对应接口: GET /meta
     * 
     * @return 包含版本信息的 CompletableFuture
     */
    public static CompletableFuture<String> getMeta() {
        return sendGetRequest("/meta");
    }

    /**
     * 检查服务健康状态
     * 通过尝试获取元数据来判断服务是否存活
     * 
     * @return 存活返回 true，否则返回 false
     */
    public static CompletableFuture<Boolean> checkHealth() {
        return getMeta().thenApply(response -> response != null && !response.isEmpty())
                .exceptionally(e -> false);
    }

    /**
     * 强制停止后端服务 (Panic)
     * 对应接口: GET /panic?peaceful=<bool>
     * 
     * @param peaceful 是否尝试优雅关闭
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> panic(boolean peaceful) {
        return sendGetRequest("/panic?peaceful=" + peaceful).thenAccept(r -> {});
    }

    /**
     * 获取后端日志
     * 对应接口: GET /log?fetch=<bool>
     * 
     * @param fetch 是否获取最新日志
     * @return 日志内容
     */
    public static CompletableFuture<String> getLog(boolean fetch) {
        return sendGetRequest("/log?fetch=" + fetch);
    }

    /**
     * 设置为扫描模式 (准备创建房间)
     * 对应接口: GET /state/scanning?player=<string>
     * 
     * @param player 玩家名称
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> setScanning(String player) {
        String encodedPlayer = URLEncoder.encode(player, StandardCharsets.UTF_8);
        return sendGetRequest("/state/scanning?player=" + encodedPlayer).thenAccept(r -> {});
    }

    /**
     * 设置为空闲模式 (取消操作或断开连接)
     * 对应接口: GET /state/ide
     * 
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> setIdle() {
        return sendGetRequest("/state/ide").thenAccept(r -> {});
    }

    /**
     * 加入房间请求
     * 对应接口: GET /state/guesting?room=<room>&player=<player>
     * 
     * @param room 房间邀请码
     * @param player 玩家名称
     * @return 成功返回 true，失败返回 false
     */
    public static CompletableFuture<Boolean> joinRoom(String room, String player) {
        String encodedRoom = URLEncoder.encode(room, StandardCharsets.UTF_8);
        String encodedPlayer = URLEncoder.encode(player, StandardCharsets.UTF_8);
        String url = getBaseUrl() + "/state/guesting?room=" + encodedRoom + "&player=" + encodedPlayer;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200)
                .exceptionally(e -> false);
    }

    /**
     * 获取当前状态 JSON
     * 对应接口: GET /state
     * 
     * @return JSON 字符串
     */
    public static CompletableFuture<String> getState() {
        return sendGetRequest("/state");
    }

    /**
     * 开始主持游戏 (Host)
     * 流程：先重置为空闲状态，然后进入 Scanning 状态，后端自动检测 LAN 端口并启动 Host
     * 
     * @param port 游戏端口
     * @param player 玩家名称
     * @return 成功返回 null，失败返回错误信息
     */
    public static CompletableFuture<String> startHosting(int port, String player) {
        // 先调用 setIdle 确保状态重置，避免因之前的状态导致扫描失败
        return setIdle()
                .thenCompose(v -> setScanning(player))
                .thenApply(v -> (String) null)
                .exceptionally(e -> "Exception: " + e.getMessage());
    }

    /**
     * URL 编码字符串
     * 
     * @param value 原始字符串
     * @return 编码后的字符串
     */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 发送 GET 请求
     * 
     * @param path 请求路径 (相对路径)
     * @return 响应体内容，失败返回 null
     */
    private static CompletableFuture<String> sendGetRequest(String path) {
        String url = getBaseUrl() + path;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return response.body();
                    } else {
                        LOGGER.warn("API request failed: {} code={}", path, response.statusCode());
                        return null;
                    }
                })
                .exceptionally(e -> {
                    LOGGER.warn("API request error: {} msg={}", path, e.getMessage());
                    return null;
                });
    }
}
