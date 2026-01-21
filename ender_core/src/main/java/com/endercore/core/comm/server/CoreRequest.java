package com.endercore.core.comm.server;

import java.net.InetSocketAddress;

/**
 * 服务端收到的请求对象。
 */
public final class CoreRequest {
    private final long requestId;
    private final String kind;
    private final byte[] payload;
    private final InetSocketAddress remoteAddress;

    public CoreRequest(long requestId, String kind, byte[] payload, InetSocketAddress remoteAddress) {
        this.requestId = requestId;
        this.kind = kind;
        this.payload = payload == null ? new byte[0] : payload;
        this.remoteAddress = remoteAddress;
    }

    /**
     * 获取请求 ID。
     */
    public long requestId() {
        return requestId;
    }

    /**
     * 获取请求 kind（namespace:path）。
     */
    public String kind() {
        return kind;
    }

    /**
     * 获取请求负载。
     */
    public byte[] payload() {
        return payload;
    }

    /**
     * 获取远端地址。
     */
    public InetSocketAddress remoteAddress() {
        return remoteAddress;
    }
}
