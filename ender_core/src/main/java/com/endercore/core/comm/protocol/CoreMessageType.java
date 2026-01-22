package com.endercore.core.comm.protocol;

 
/**
 * 消息类型枚举。
 * 定义了 Core 协议支持的四种消息类型：请求、响应、事件和心跳。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public enum CoreMessageType {
    /**
     * 请求消息
     */
    REQUEST((byte) 0),
    
    /**
     * 响应消息
     */
    RESPONSE((byte) 1),
    
    /**
     * 事件消息
     */
    EVENT((byte) 2),
    
    /**
     * 心跳消息
     */
    HEARTBEAT((byte) 3);

    private final byte code;

    /**
     * 构造函数。
     *
     * @param code 类型代码
     */
    CoreMessageType(byte code) {
        this.code = code;
    }

    /**
     * 获取类型代码。
     *
     * @return 类型代码
     */
    public byte code() {
        return code;
    }

    /**
     * 从代码获取枚举实例。
     *
     * @param code 类型代码
     * @return 对应的 CoreMessageType 实例
     * @throws IllegalArgumentException 当代码未知时抛出
     */
    public static CoreMessageType from(byte code) {
        for (CoreMessageType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知消息类型: " + (code & 0xFF));
    }
}
