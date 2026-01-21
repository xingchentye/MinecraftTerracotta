package com.endercore.core.comm.monitor;

/**
 * 连接状态机。
 */
public enum ConnectionState {
    CONNECTING,
    CONNECTED,
    CLOSING,
    CLOSED,
    FAILED
}

