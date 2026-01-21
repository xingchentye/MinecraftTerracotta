package com.endercore.core.comm.monitor;

import java.time.Instant;

/**
 * 指标快照：用于对外观测连接状态与吞吐情况。
 */
public final class ConnectionMetricsSnapshot {
    private final Instant createdAt;
    private final long bytesSent;
    private final long bytesReceived;
    private final long framesSent;
    private final long framesReceived;
    private final long requestsSent;
    private final long responsesReceived;
    private final long requestTimeouts;
    private final long protocolErrors;
    private final long pendingRequests;
    private final long lastRttMillis;

    public ConnectionMetricsSnapshot(
            Instant createdAt,
            long bytesSent,
            long bytesReceived,
            long framesSent,
            long framesReceived,
            long requestsSent,
            long responsesReceived,
            long requestTimeouts,
            long protocolErrors,
            long pendingRequests,
            long lastRttMillis
    ) {
        this.createdAt = createdAt;
        this.bytesSent = bytesSent;
        this.bytesReceived = bytesReceived;
        this.framesSent = framesSent;
        this.framesReceived = framesReceived;
        this.requestsSent = requestsSent;
        this.responsesReceived = responsesReceived;
        this.requestTimeouts = requestTimeouts;
        this.protocolErrors = protocolErrors;
        this.pendingRequests = pendingRequests;
        this.lastRttMillis = lastRttMillis;
    }

    /**
     * 快照创建时间。
     */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * 已发送字节数。
     */
    public long bytesSent() {
        return bytesSent;
    }

    /**
     * 已接收字节数。
     */
    public long bytesReceived() {
        return bytesReceived;
    }

    /**
     * 已发送帧数。
     */
    public long framesSent() {
        return framesSent;
    }

    /**
     * 已接收帧数。
     */
    public long framesReceived() {
        return framesReceived;
    }

    /**
     * 已发送请求数。
     */
    public long requestsSent() {
        return requestsSent;
    }

    /**
     * 已接收响应数。
     */
    public long responsesReceived() {
        return responsesReceived;
    }

    /**
     * 请求超时次数。
     */
    public long requestTimeouts() {
        return requestTimeouts;
    }

    /**
     * 协议错误次数。
     */
    public long protocolErrors() {
        return protocolErrors;
    }

    /**
     * 当前 pending 请求数量。
     */
    public long pendingRequests() {
        return pendingRequests;
    }

    /**
     * 最近一次往返耗时（毫秒）。
     */
    public long lastRttMillis() {
        return lastRttMillis;
    }
}
