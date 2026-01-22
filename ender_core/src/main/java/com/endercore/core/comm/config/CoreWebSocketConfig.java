package com.endercore.core.comm.config;

import java.time.Duration;

 
/**
 * WebSocket 客户端配置。
 * 用于配置 CoreWebSocketClient 的各种参数。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
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
     * 获取连接超时时间。
     *
     * @return 连接超时时间
     */
    public Duration connectTimeout() {
        return connectTimeout;
    }

    /**
     * 获取请求超时时间。
     *
     * @return 请求超时时间
     */
    public Duration requestTimeout() {
        return requestTimeout;
    }

    /**
     * 获取心跳间隔。
     *
     * @return 心跳间隔
     */
    public Duration heartbeatInterval() {
        return heartbeatInterval;
    }

    /**
     * 获取是否自动重连。
     *
     * @return 是否自动重连
     */
    public boolean autoReconnect() {
        return autoReconnect;
    }

    /**
     * 获取最小重连退避时间。
     *
     * @return 最小重连退避时间
     */
    public Duration reconnectBackoffMin() {
        return reconnectBackoffMin;
    }

    /**
     * 获取最大重连退避时间。
     *
     * @return 最大重连退避时间
     */
    public Duration reconnectBackoffMax() {
        return reconnectBackoffMax;
    }

    /**
     * 获取最大帧大小（字节）。
     *
     * @return 最大帧大小
     */
    public int maxFrameBytes() {
        return maxFrameBytes;
    }

    /**
     * 获取配置构建器。
     *
     * @return 构建器实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 配置构建器类。
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
         * 设置连接超时时间。
         *
         * @param connectTimeout 连接超时时间
         * @return 构建器实例
         */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        /**
         * 设置请求超时时间。
         *
         * @param requestTimeout 请求超时时间
         * @return 构建器实例
         */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        /**
         * 设置心跳间隔。
         *
         * @param heartbeatInterval 心跳间隔
         * @return 构建器实例
         */
        public Builder heartbeatInterval(Duration heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
            return this;
        }

        /**
         * 设置是否自动重连。
         *
         * @param autoReconnect 是否自动重连
         * @return 构建器实例
         */
        public Builder autoReconnect(boolean autoReconnect) {
            this.autoReconnect = autoReconnect;
            return this;
        }

        /**
         * 设置最小重连退避时间。
         *
         * @param reconnectBackoffMin 最小重连退避时间
         * @return 构建器实例
         */
        public Builder reconnectBackoffMin(Duration reconnectBackoffMin) {
            this.reconnectBackoffMin = reconnectBackoffMin;
            return this;
        }

        /**
         * 设置最大重连退避时间。
         *
         * @param reconnectBackoffMax 最大重连退避时间
         * @return 构建器实例
         */
        public Builder reconnectBackoffMax(Duration reconnectBackoffMax) {
            this.reconnectBackoffMax = reconnectBackoffMax;
            return this;
        }

        /**
         * 设置最大帧大小。
         *
         * @param maxFrameBytes 最大帧大小（字节）
         * @return 构建器实例
         */
        public Builder maxFrameBytes(int maxFrameBytes) {
            this.maxFrameBytes = maxFrameBytes;
            return this;
        }

        /**
         * 构建配置对象。
         *
         * @return 配置对象
         */
        public CoreWebSocketConfig build() {
            return new CoreWebSocketConfig(this);
        }
    }
}
