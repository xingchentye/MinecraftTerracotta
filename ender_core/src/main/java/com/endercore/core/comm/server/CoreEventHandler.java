package com.endercore.core.comm.server;

import java.net.InetSocketAddress;

 
/**
 * 核心事件处理器接口。
 * 用于处理 WebSocket 接收到的事件。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
@FunctionalInterface
public interface CoreEventHandler {

    /**
     * 处理事件。
     *
     * @param kind 事件类型
     * @param payload 事件负载
     * @param remoteAddress 远程地址
     * @throws Exception 当处理失败时抛出
     */
    void handle(String kind, byte[] payload, InetSocketAddress remoteAddress) throws Exception;
}

