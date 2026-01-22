package com.endercore.core.comm.api;

import com.endercore.core.comm.monitor.ConnectionMetricsSnapshot;
import com.endercore.core.comm.monitor.ConnectionState;

import java.util.function.BiConsumer;

 
/**
 * 核心状态监视器接口。
 * 用于监控 CoreComm 连接状态和性能指标。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public interface CoreStateMonitor {

    /**
     * 获取当前连接状态。
     *
     * @return 当前连接状态
     */
    ConnectionState state();

    /**
     * 获取当前连接指标快照。
     * 包括收发字节数、请求延迟等信息。
     *
     * @return 连接指标快照
     */
    ConnectionMetricsSnapshot metrics();

    /**
     * 注册状态变更监听器。
     * 当连接状态发生变化时调用。
     *
     * @param listener 状态变更监听器 (oldState, newState)
     */
    void onStateChanged(BiConsumer<ConnectionState, ConnectionState> listener);
}

