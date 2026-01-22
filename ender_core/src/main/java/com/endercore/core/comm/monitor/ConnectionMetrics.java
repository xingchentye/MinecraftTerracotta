package com.endercore.core.comm.monitor;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

 
/**
 * 连接指标收集器。
 * 负责收集 WebSocket 连接的各种性能指标，如流量、帧数、请求数、延迟等。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
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
     * 记录帧发送。
     *
     * @param bytes 发送的字节数
     */
    public void onFrameSent(int bytes) {
        framesSent.incrementAndGet();
        bytesSent.addAndGet(bytes);
    }

    /**
     * 记录帧接收。
     *
     * @param bytes 接收的字节数
     */
    public void onFrameReceived(int bytes) {
        framesReceived.incrementAndGet();
        bytesReceived.addAndGet(bytes);
    }

    /**
     * 记录请求发送。
     */
    public void onRequestSent() {
        requestsSent.incrementAndGet();
    }

    /**
     * 记录响应接收。
     */
    public void onResponseReceived() {
        responsesReceived.incrementAndGet();
    }

    /**
     * 记录请求超时。
     */
    public void onRequestTimeout() {
        requestTimeouts.incrementAndGet();
    }

    /**
     * 记录协议错误。
     */
    public void onProtocolError() {
        protocolErrors.incrementAndGet();
    }

    /**
     * 设置最近一次 RTT（往返时间）。
     *
     * @param millis RTT 毫秒数
     */
    public void setLastRttMillis(long millis) {
        lastRttMillis.set(millis);
    }

    /**
     * 获取当前指标快照。
     *
     * @param pendingRequests 当前挂起的请求数
     * @return 指标快照对象
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
