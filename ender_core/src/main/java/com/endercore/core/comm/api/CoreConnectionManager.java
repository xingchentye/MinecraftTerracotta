package com.endercore.core.comm.api;

import com.endercore.core.comm.monitor.ConnectionState;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

 
/**
 * 核心连接管理器接口。
 * 用于管理 CoreComm 的连接生命周期。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public interface CoreConnectionManager {

    /**
     * 连接到指定的端点。
     *
     * @param endpoint 连接端点 URI
     * @return 连接完成的 CompletableFuture
     */
    CompletableFuture<Void> connect(URI endpoint);

    /**
     * 关闭连接。
     *
     * @param timeout 关闭超时时间
     * @return 关闭完成的 CompletableFuture
     */
    CompletableFuture<Void> close(Duration timeout);

    /**
     * 获取当前连接状态。
     *
     * @return 当前连接状态
     */
    ConnectionState state();

    /**
     * 检查是否已连接。
     *
     * @return 如果已连接返回 true，否则返回 false
     */
    boolean isConnected();
}

