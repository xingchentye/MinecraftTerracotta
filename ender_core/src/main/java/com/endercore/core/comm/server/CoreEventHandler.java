package com.endercore.core.comm.server;

import java.net.InetSocketAddress;

/**
 * 服务端事件处理器：处理客户端发来的单向事件。
 */
@FunctionalInterface
public interface CoreEventHandler {

    /**
     * 处理事件。
     *
     * @param kind          事件类型（namespace:path）
     * @param payload       事件负载
     * @param remoteAddress 远端地址
     * @throws Exception 处理过程中的异常
     */
    void handle(String kind, byte[] payload, InetSocketAddress remoteAddress) throws Exception;
}

