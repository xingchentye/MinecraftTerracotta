package com.endercore.core.comm.monitor;

import java.time.Instant;

 
/**
 * 连接指标快照对象。
 * 包含了某一时刻的连接性能指标数据。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
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

    /**
     * 构造函数。
     *
     * @param createdAt 创建时间
     * @param bytesSent 发送字节数
     * @param bytesReceived 接收字节数
     * @param framesSent 发送帧数
     * @param framesReceived 接收帧数
     * @param requestsSent 发送请求数
     * @param responsesReceived 接收响应数
     * @param requestTimeouts 请求超时数
     * @param protocolErrors 协议错误数
     * @param pendingRequests 挂起的请求数
     * @param lastRttMillis 最近一次 RTT
     */
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
     * 获取创建时间。
     *
     * @return 创建时间
     */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * 获取发送字节数。
     *
     * @return 发送字节数
     */
    public long bytesSent() {
        return bytesSent;
    }

    /**
     * 获取接收字节数。
     *
     * @return 接收字节数
     */
    public long bytesReceived() {
        return bytesReceived;
    }

    /**
     * 获取发送帧数。
     *
     * @return 发送帧数
     */
    public long framesSent() {
        return framesSent;
    }

    /**
     * 获取接收帧数。
     *
     * @return 接收帧数
     */
    public long framesReceived() {
        return framesReceived;
    }

    /**
     * 获取发送请求数。
     *
     * @return 发送请求数
     */
    public long requestsSent() {
        return requestsSent;
    }

    /**
     * 获取接收响应数。
     *
     * @return 接收响应数
     */
    public long responsesReceived() {
        return responsesReceived;
    }

    /**
     * 获取请求超时数。
     *
     * @return 请求超时数
     */
    public long requestTimeouts() {
        return requestTimeouts;
    }

    /**
     * 获取协议错误数。
     *
     * @return 协议错误数
     */
    public long protocolErrors() {
        return protocolErrors;
    }

    /**
     * 获取挂起的请求数。
     *
     * @return 挂起的请求数
     */
    public long pendingRequests() {
        return pendingRequests;
    }

    /**
     * 获取最近一次 RTT（往返时间）。
     *
     * @return RTT 毫秒数
     */
    public long lastRttMillis() {
        return lastRttMillis;
    }
}
