package com.endercore.core.comm.protocol;

import java.util.Arrays;
import java.util.Objects;

/**
 * ECWS/1 应用层帧模型。
 */
public final class CoreFrame {
    private final CoreMessageType type;
    private final byte flags;
    private final int status;
    private final long requestId;
    private final String kind;
    private final byte[] payload;

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
     */
    public CoreMessageType type() {
        return type;
    }

    /**
     * 获取 flags 字段。
     */
    public byte flags() {
        return flags;
    }

    /**
     * 获取 status（响应使用）。
     */
    public int status() {
        return status;
    }

    /**
     * 获取 requestId（请求/响应使用）。
     */
    public long requestId() {
        return requestId;
    }

    /**
     * 获取 kind（namespace:path）。
     */
    public String kind() {
        return kind;
    }

    /**
     * 获取 payload。
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
