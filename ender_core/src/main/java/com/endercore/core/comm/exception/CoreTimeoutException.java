package com.endercore.core.comm.exception;

import java.time.Duration;

 
/**
 * 请求超时异常。
 * 当请求在指定时间内未收到响应时抛出。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public final class CoreTimeoutException extends CoreCommException {
    /**
     * 请求类型
     */
    private final String kind;
    
    /**
     * 请求 ID
     */
    private final long requestId;
    
    /**
     * 超时时间
     */
    private final Duration timeout;

    /**
     * 构造函数。
     *
     * @param kind 请求类型
     * @param requestId 请求 ID
     * @param timeout 超时时间
     */
    public CoreTimeoutException(String kind, long requestId, Duration timeout) {
        super("请求超时: kind=" + kind + ", requestId=" + requestId + ", timeout=" + timeout);
        this.kind = kind;
        this.requestId = requestId;
        this.timeout = timeout;
    }

    /**
     * 获取请求类型。
     *
     * @return 请求类型
     */
    public String kind() {
        return kind;
    }

    /**
     * 获取请求 ID。
     *
     * @return 请求 ID
     */
    public long requestId() {
        return requestId;
    }

    /**
     * 获取超时时间。
     *
     * @return 超时时间
     */
    public Duration timeout() {
        return timeout;
    }
}
