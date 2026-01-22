package com.endercore.core.comm.server;

import com.endercore.core.comm.protocol.CoreResponse;

 
/**
 * 核心请求处理器接口。
 * 用于处理 WebSocket 接收到的请求。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
@FunctionalInterface
public interface CoreRequestHandler {
    /**
     * 处理请求。
     *
     * @param request 请求对象
     * @return 响应对象
     * @throws Exception 当处理失败时抛出
     */
    CoreResponse handle(CoreRequest request) throws Exception;
}
