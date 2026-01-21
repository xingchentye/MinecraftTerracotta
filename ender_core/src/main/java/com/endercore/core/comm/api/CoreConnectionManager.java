package com.endercore.core.comm.api;

import com.endercore.core.comm.monitor.ConnectionState;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * 连接管理接口：建立/维护/关闭连接。
 */
public interface CoreConnectionManager {

    /**
     * 建立到目标 WebSocket 服务端的连接。
     *
     * @param endpoint WebSocket 地址，例如 ws://127.0.0.1:8080/ws
     * @return 连接成功后完成的 future
     */
    CompletableFuture<Void> connect(URI endpoint);

    /**
     * 关闭连接并释放资源。
     *
     * @param timeout 优雅关闭等待时间
     * @return 关闭完成后完成的 future
     */
    CompletableFuture<Void> close(Duration timeout);

    /**
     * 当前连接状态。
     *
     * @return 状态枚举
     */
    ConnectionState state();

    /**
     * 是否处于已连接状态。
     *
     * @return true 表示连接可用
     */
    boolean isConnected();
}

