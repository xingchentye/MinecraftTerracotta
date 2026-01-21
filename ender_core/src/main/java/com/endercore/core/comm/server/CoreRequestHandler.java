package com.endercore.core.comm.server;

import com.endercore.core.comm.protocol.CoreResponse;

/**
 * 请求处理器：将请求映射为响应。
 */
@FunctionalInterface
public interface CoreRequestHandler {
    /**
     * 处理请求并返回响应。
     *
     * @param request 请求
     * @return 响应
     * @throws Exception 处理过程中的异常
     */
    CoreResponse handle(CoreRequest request) throws Exception;
}
