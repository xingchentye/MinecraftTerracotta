package com.endercore.core.comm.protocol;

import java.nio.charset.StandardCharsets;

/**
 * RPC 响应对象。
 */
public final class CoreResponse {
    private final int status;
    private final long requestId;
    private final String kind;
    private final byte[] payload;

    public CoreResponse(int status, long requestId, String kind, byte[] payload) {
        this.status = status;
        this.requestId = requestId;
        this.kind = kind;
        this.payload = payload == null ? new byte[0] : payload;
    }

    /**
     * 获取响应状态码：0 表示成功。
     */
    public int status() {
        return status;
    }

    /**
     * 获取 requestId。
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

    /**
     * 判断响应是否成功。
     */
    public boolean isOk() {
        return status == 0;
    }

    /**
     * 将 payload 按 UTF-8 解码为字符串。
     */
    public String payloadUtf8() {
        return new String(payload, StandardCharsets.UTF_8);
    }
}
