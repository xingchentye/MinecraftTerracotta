package com.endercore.core.comm.api;

/**
 * 事件监听器：用于接收远端推送的单向事件。
 */
@FunctionalInterface
public interface CoreEventListener {

    /**
     * 处理事件回调。
     *
     * @param kind    事件类型（namespace:path）
     * @param payload 事件负载
     */
    void onEvent(String kind, byte[] payload);
}

