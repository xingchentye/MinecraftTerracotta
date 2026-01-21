package com.endercore.core.comm.api;

import com.endercore.core.comm.monitor.ConnectionMetricsSnapshot;
import com.endercore.core.comm.monitor.ConnectionState;

import java.util.function.BiConsumer;

/**
 * 状态监控接口：提供连接状态与指标快照，并支持订阅回调。
 */
public interface CoreStateMonitor {

    /**
     * 获取当前连接状态。
     *
     * @return 连接状态
     */
    ConnectionState state();

    /**
     * 获取当前指标快照。
     *
     * @return 指标快照
     */
    ConnectionMetricsSnapshot metrics();

    /**
     * 订阅连接状态变化事件。
     *
     * @param listener 回调：oldState, newState
     */
    void onStateChanged(BiConsumer<ConnectionState, ConnectionState> listener);
}

