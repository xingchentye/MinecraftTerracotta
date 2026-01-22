package com.endercore.core.comm.protocol;

import java.nio.charset.StandardCharsets;

 
/**
 * 核心响应对象。
 * 封装了 WebSocket 响应的相关信息。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public final class CoreResponse {
    /**
     * 响应状态码 (0 表示成功)
     */
    private final int status;
    
    /**
     * 关联的请求 ID
     */
    private final long requestId;
    
    /**
     * 响应类型
     */
    private final String kind;
    
    /**
     * 响应负载
     */
    private final byte[] payload;

    /**
     * 构造函数。
     *
     * @param status 响应状态码
     * @param requestId 关联的请求 ID
     * @param kind 响应类型
     * @param payload 响应负载
     */
    public CoreResponse(int status, long requestId, String kind, byte[] payload) {
        this.status = status;
        this.requestId = requestId;
        this.kind = kind;
        this.payload = payload == null ? new byte[0] : payload;
    }

    /**
     * 获取响应状态码。
     *
     * @return 响应状态码
     */
    public int status() {
        return status;
    }

    /**
     * 获取关联的请求 ID。
     *
     * @return 请求 ID
     */
    public long requestId() {
        return requestId;
    }

    /**
     * 获取响应类型。
     *
     * @return 响应类型
     */
    public String kind() {
        return kind;
    }

    /**
     * 获取响应负载。
     *
     * @return 响应负载
     */
    public byte[] payload() {
        return payload;
    }

    /**
     * 检查是否成功。
     *
     * @return 如果状态码为 0 则返回 true，否则返回 false
     */
    public boolean isOk() {
        return status == 0;
    }

    /**
     * 获取 UTF-8 字符串形式的负载。
     *
     * @return 字符串形式的负载
     */
    public String payloadUtf8() {
        return new String(payload, StandardCharsets.UTF_8);
    }
}
