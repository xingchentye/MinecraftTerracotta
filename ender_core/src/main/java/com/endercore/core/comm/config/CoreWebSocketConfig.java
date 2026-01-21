package com.endercore.core.comm.config;

import java.time.Duration;

/**
 * Core WebSocket 通信配置。
 */
public final class CoreWebSocketConfig {
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final Duration heartbeatInterval;
    private final boolean autoReconnect;
    private final Duration reconnectBackoffMin;
    private final Duration reconnectBackoffMax;
    private final int maxFrameBytes;

    private CoreWebSocketConfig(Builder builder) {
        this.connectTimeout = builder.connectTimeout;
        this.requestTimeout = builder.requestTimeout;
        this.heartbeatInterval = builder.heartbeatInterval;
        this.autoReconnect = builder.autoReconnect;
        this.reconnectBackoffMin = builder.reconnectBackoffMin;
        this.reconnectBackoffMax = builder.reconnectBackoffMax;
        this.maxFrameBytes = builder.maxFrameBytes;
    }

    /**
     * 连接建立超时。
     */
    public Duration connectTimeout() {
        return connectTimeout;
    }

    /**
     * 默认请求超时（用于 sendAsync）。
     */
    public Duration requestTimeout() {
        return requestTimeout;
    }

    /**
     * 心跳发送间隔，<=0 表示关闭心跳。
     */
    public Duration heartbeatInterval() {
        return heartbeatInterval;
    }

    /**
     * 是否启用断线自动重连。
     */
    public boolean autoReconnect() {
        return autoReconnect;
    }

    /**
     * 重连退避最小值。
     */
    public Duration reconnectBackoffMin() {
        return reconnectBackoffMin;
    }

    /**
     * 重连退避最大值。
     */
    public Duration reconnectBackoffMax() {
        return reconnectBackoffMax;
    }

    /**
     * 单帧最大字节数（协议与库共同限制）。
     */
    public int maxFrameBytes() {
        return maxFrameBytes;
    }

    /**
     * 创建配置构建器。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 配置构建器。
     */
    public static final class Builder {
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration requestTimeout = Duration.ofSeconds(10);
        private Duration heartbeatInterval = Duration.ofSeconds(15);
        private boolean autoReconnect = true;
        private Duration reconnectBackoffMin = Duration.ofMillis(200);
        private Duration reconnectBackoffMax = Duration.ofSeconds(5);
        private int maxFrameBytes = 4 * 1024 * 1024;

        /**
         * 设置连接建立超时。
         */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        /**
         * 设置默认请求超时。
         */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        /**
         * 设置心跳间隔。
         */
        public Builder heartbeatInterval(Duration heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
            return this;
        }

        /**
         * 设置是否启用自动重连。
         */
        public Builder autoReconnect(boolean autoReconnect) {
            this.autoReconnect = autoReconnect;
            return this;
        }

        /**
         * 设置重连退避最小值。
         */
        public Builder reconnectBackoffMin(Duration reconnectBackoffMin) {
            this.reconnectBackoffMin = reconnectBackoffMin;
            return this;
        }

        /**
         * 设置重连退避最大值。
         */
        public Builder reconnectBackoffMax(Duration reconnectBackoffMax) {
            this.reconnectBackoffMax = reconnectBackoffMax;
            return this;
        }

        /**
         * 设置单帧最大字节数。
         */
        public Builder maxFrameBytes(int maxFrameBytes) {
            this.maxFrameBytes = maxFrameBytes;
            return this;
        }

        /**
         * 构建配置对象。
         */
        public CoreWebSocketConfig build() {
            return new CoreWebSocketConfig(this);
        }
    }
}
