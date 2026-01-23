package com.endercore.core.comm.protocol;

import java.util.Objects;

/**
 * 协议帧对象。
 * 表示一个完整的协议帧，包含类型、标志位、状态码、请求 ID、消息种类和负载。
 * 使用 Java Record 优化数据类定义。
 *
 * @param type      消息类型
 * @param flags     标志位
 * @param status    状态码
 * @param requestId 请求 ID
 * @param kind      消息种类
 * @param payload   负载数据
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public record CoreFrame(CoreMessageType type, byte flags, int status, long requestId, String kind, byte[] payload) {
    /**
     * 构造函数。
     *
     * @param type      消息类型
     * @param flags     标志位
     * @param status    状态码
     * @param requestId 请求 ID
     * @param kind      消息种类
     * @param payload   负载数据
     */
    public CoreFrame {
        Objects.requireNonNull(type, "type");
        if (kind == null) kind = "";
        if (payload == null) payload = new byte[0];
    }
}
