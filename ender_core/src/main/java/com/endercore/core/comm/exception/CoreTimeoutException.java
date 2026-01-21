package com.endercore.core.comm.exception;

import java.time.Duration;

/**
 * 请求超时异常。
 */
public final class CoreTimeoutException extends CoreCommException {
    private final String kind;
    private final long requestId;
    private final Duration timeout;

    /**
     * 构建请求超时异常。
     *
     * @param kind      请求 kind
     * @param requestId 请求 ID
     * @param timeout   超时时间
     */
    public CoreTimeoutException(String kind, long requestId, Duration timeout) {
        super("请求超时: kind=" + kind + ", requestId=" + requestId + ", timeout=" + timeout);
        this.kind = kind;
        this.requestId = requestId;
        this.timeout = timeout;
    }

    /**
     * 获取请求 kind。
     */
    public String kind() {
        return kind;
    }

    /**
     * 获取请求 ID。
     */
    public long requestId() {
        return requestId;
    }

    /**
     * 获取超时时间。
     */
    public Duration timeout() {
        return timeout;
    }
}
