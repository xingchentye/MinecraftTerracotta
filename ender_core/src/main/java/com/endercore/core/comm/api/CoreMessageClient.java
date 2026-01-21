package com.endercore.core.comm.api;

import com.endercore.core.comm.protocol.CoreResponse;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * 消息收发接口：支持同步/异步两种模式。
 */
public interface CoreMessageClient {

    /**
     * 异步发送请求并等待响应。
     *
     * @param kind    请求类型，格式为 namespace:path
     * @param payload 二进制请求体
     * @return 异步响应 future
     */
    CompletableFuture<CoreResponse> sendAsync(String kind, byte[] payload);

    /**
     * 同步发送请求并等待响应。
     *
     * @param kind    请求类型，格式为 namespace:path
     * @param payload 二进制请求体
     * @param timeout 超时时间
     * @return 响应
     */
    CoreResponse sendSync(String kind, byte[] payload, Duration timeout);

    /**
     * 发送单向事件，不等待响应。
     *
     * @param kind    事件类型，格式为 namespace:path
     * @param payload 事件负载
     */
    void sendEvent(String kind, byte[] payload);
}

