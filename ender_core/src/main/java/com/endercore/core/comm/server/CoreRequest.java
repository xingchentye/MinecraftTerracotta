package com.endercore.core.comm.server;

import java.net.InetSocketAddress;

 
/**
 * 核心请求对象。
 * 封装了 WebSocket 请求的相关信息。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public final class CoreRequest {
    /**
     * 请求 ID
     */
    private final long requestId;
    
    /**
     * 请求类型
     */
    private final String kind;
    
    /**
     * 请求负载
     */
    private final byte[] payload;
    
    /**
     * 远程地址
     */
    private final InetSocketAddress remoteAddress;

    /**
     * 构造函数。
     *
     * @param requestId 请求 ID
     * @param kind 请求类型
     * @param payload 请求负载
     * @param remoteAddress 远程地址
     */
    public CoreRequest(long requestId, String kind, byte[] payload, InetSocketAddress remoteAddress) {
        this.requestId = requestId;
        this.kind = kind;
        this.payload = payload == null ? new byte[0] : payload;
        this.remoteAddress = remoteAddress;
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
     * 获取请求类型。
     *
     * @return 请求类型
     */
    public String kind() {
        return kind;
    }

    /**
     * 获取请求负载。
     *
     * @return 请求负载
     */
    public byte[] payload() {
        return payload;
    }

    /**
     * 获取远程地址。
     *
     * @return 远程地址
     */
    public InetSocketAddress remoteAddress() {
        return remoteAddress;
    }
}
