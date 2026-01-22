package com.endercore.core.comm.monitor;

 
/**
 * 连接状态枚举。
 * 定义了 WebSocket 连接的各种生命周期状态。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public enum ConnectionState {
    /**
     * 连接中
     */
    CONNECTING,
    
    /**
     * 已连接
     */
    CONNECTED,
    
    /**
     * 关闭中
     */
    CLOSING,
    
    /**
     * 已关闭
     */
    CLOSED,
    
    /**
     * 连接失败
     */
    FAILED
}

