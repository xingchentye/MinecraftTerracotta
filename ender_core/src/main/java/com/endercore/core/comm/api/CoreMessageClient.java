package com.endercore.core.comm.api;

import com.endercore.core.comm.protocol.CoreResponse;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

 
/**
 * 核心消息客户端接口。
 * 用于发送请求和事件消息。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public interface CoreMessageClient {

    /**
     * 异步发送请求。
     *
     * @param kind    请求类型/名称
     * @param payload 请求负载数据
     * @return 包含响应的 CompletableFuture
     */
    CompletableFuture<CoreResponse> sendAsync(String kind, byte[] payload);

    /**
     * 同步发送请求。
     *
     * @param kind    请求类型/名称
     * @param payload 请求负载数据
     * @param timeout 超时时间
     * @return 响应对象
     */
    CoreResponse sendSync(String kind, byte[] payload, Duration timeout);

    /**
     * 发送事件（单向消息）。
     * 不等待响应。
     *
     * @param kind    事件类型/名称
     * @param payload 事件负载数据
     */
    void sendEvent(String kind, byte[] payload);
}

