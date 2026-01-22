package com.multiplayer.ender.network;

import com.endercore.core.comm.CoreComm;
import com.endercore.core.comm.client.CoreWebSocketClient;
import com.endercore.core.comm.config.CoreWebSocketConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * 网络客户端类。
 * 负责建立和管理与 EnderCore 后端的 WebSocket 连接。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public class NetworkClient {
    /**
     * 日志记录器
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkClient.class);
    
    /**
     * 单例实例
     */
    private static NetworkClient instance;
    
    /**
     * WebSocket 客户端实例
     */
    private CoreWebSocketClient client;

    /**
     * 私有构造函数，防止外部实例化。
     */
    private NetworkClient() {}

    /**
     * 获取 NetworkClient 的单例实例。
     *
     * @return NetworkClient 实例
     */
    public static synchronized NetworkClient getInstance() {
        if (instance == null) {
            instance = new NetworkClient();
        }
        return instance;
    }

    /**
     * 连接到指定的服务器。
     *
     * @param host 服务器主机名或IP地址
     * @param port 服务器端口号
     * @return 一个 CompletableFuture，在连接成功时完成，失败时抛出异常
     */
    public CompletableFuture<Void> connect(String host, int port) {
        LOGGER.info("Connecting to {}:{} via EnderCore WebSocket", host, port);
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        try {
            CoreWebSocketConfig config = CoreWebSocketConfig.builder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
                
            client = CoreComm.newClient(config, null, null);
            
            URI uri = URI.create("ws://" + host + ":" + port + "/ws"); 
            
            
            new Thread(() -> {
                try {
                    client.connect(uri).get();
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }).start();
            
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }
    
    /**
     * 关闭当前的网络连接。
     */
    public void close() {
        if (client != null) {
            try {
                client.close(Duration.ofSeconds(1));
            } catch (Exception e) {
                LOGGER.warn("Error closing client", e);
            }
        }
    }
}

