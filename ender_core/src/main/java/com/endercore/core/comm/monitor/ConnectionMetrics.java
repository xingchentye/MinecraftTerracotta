package com.endercore.core.comm.monitor;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 指标采集器（线程安全），内部使用原子变量累积指标。
 */
public final class ConnectionMetrics {
    private final AtomicLong bytesSent = new AtomicLong();
    private final AtomicLong bytesReceived = new AtomicLong();
    private final AtomicLong framesSent = new AtomicLong();
    private final AtomicLong framesReceived = new AtomicLong();
    private final AtomicLong requestsSent = new AtomicLong();
    private final AtomicLong responsesReceived = new AtomicLong();
    private final AtomicLong requestTimeouts = new AtomicLong();
    private final AtomicLong protocolErrors = new AtomicLong();
    private final AtomicLong lastRttMillis = new AtomicLong();

    /**
     * 记录发送帧（累积帧数与字节数）。
     */
    public void onFrameSent(int bytes) {
        framesSent.incrementAndGet();
        bytesSent.addAndGet(bytes);
    }

    /**
     * 记录接收帧（累积帧数与字节数）。
     */
    public void onFrameReceived(int bytes) {
        framesReceived.incrementAndGet();
        bytesReceived.addAndGet(bytes);
    }

    /**
     * 记录请求发送次数。
     */
    public void onRequestSent() {
        requestsSent.incrementAndGet();
    }

    /**
     * 记录响应接收次数。
     */
    public void onResponseReceived() {
        responsesReceived.incrementAndGet();
    }

    /**
     * 记录请求超时次数。
     */
    public void onRequestTimeout() {
        requestTimeouts.incrementAndGet();
    }

    /**
     * 记录协议错误次数。
     */
    public void onProtocolError() {
        protocolErrors.incrementAndGet();
    }

    /**
     * 设置最近一次往返耗时（毫秒）。
     */
    public void setLastRttMillis(long millis) {
        lastRttMillis.set(millis);
    }

    /**
     * 获取指标快照。
     */
    public ConnectionMetricsSnapshot snapshot(long pendingRequests) {
        return new ConnectionMetricsSnapshot(
                Instant.now(),
                bytesSent.get(),
                bytesReceived.get(),
                framesSent.get(),
                framesReceived.get(),
                requestsSent.get(),
                responsesReceived.get(),
                requestTimeouts.get(),
                protocolErrors.get(),
                pendingRequests,
                lastRttMillis.get()
        );
    }
}
