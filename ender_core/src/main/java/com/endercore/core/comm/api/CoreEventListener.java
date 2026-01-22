package com.endercore.core.comm.api;

 
/**
 * 核心事件监听器接口。
 * 用于处理 CoreComm 接收到的事件消息。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
@FunctionalInterface
public interface CoreEventListener {

    /**
     * 当接收到事件时调用。
     *
     * @param kind    事件类型/名称
     * @param payload 事件负载数据
     */
    void onEvent(String kind, byte[] payload);
}

