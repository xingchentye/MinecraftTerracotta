package com.endercore.core.comm.protocol;

import java.util.Arrays;
import java.util.Objects;

 
/**
 * 协议帧对象。
 * 表示一个完整的协议帧，包含类型、标志位、状态码、请求 ID、消息种类和负载。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public final class CoreFrame {
    /**
     * 消息类型
     */
    private final CoreMessageType type;
    
    /**
     * 标志位
     */
    private final byte flags;
    
    /**
     * 状态码
     */
    private final int status;
    
    /**
     * 请求 ID
     */
    private final long requestId;
    
    /**
     * 消息种类
     */
    private final String kind;
    
    /**
     * 负载数据
     */
    private final byte[] payload;

    /**
     * 构造函数。
     *
     * @param type 消息类型
     * @param flags 标志位
     * @param status 状态码
     * @param requestId 请求 ID
     * @param kind 消息种类
     * @param payload 负载数据
     */
    public CoreFrame(CoreMessageType type, byte flags, int status, long requestId, String kind, byte[] payload) {
        this.type = Objects.requireNonNull(type, "type");
        this.flags = flags;
        this.status = status;
        this.requestId = requestId;
        this.kind = kind == null ? "" : kind;
        this.payload = payload == null ? new byte[0] : payload;
    }

    /**
     * 获取消息类型。
     *
     * @return 消息类型
     */
    public CoreMessageType type() {
        return type;
    }

    /**
     * 获取标志位。
     *
     * @return 标志位
     */
    public byte flags() {
        return flags;
    }

    /**
     * 获取状态码。
     *
     * @return 状态码
     */
    public int status() {
        return status;
    }

    /**
     * 获取请求 ID。
     *
     * @return 请求 ID
     */
    public long requestId() {
        return requestId;
    }

    /**
     * 获取消息种类。
     *
     * @return 消息种类
     */
    public String kind() {
        return kind;
    }

    /**
     * 获取负载数据。
     *
     * @return 负载数据
     */
    public byte[] payload() {
        return payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CoreFrame)) {
            return false;
        }
        CoreFrame coreFrame = (CoreFrame) o;
        return flags == coreFrame.flags
                && status == coreFrame.status
                && requestId == coreFrame.requestId
                && type == coreFrame.type
                && Objects.equals(kind, coreFrame.kind)
                && Arrays.equals(payload, coreFrame.payload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(type, flags, status, requestId, kind);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }
}
