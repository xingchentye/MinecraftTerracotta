package com.endercore.core.comm.exception;

 
/**
 * 远程异常。
 * 当服务端返回非零状态码时抛出。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public final class CoreRemoteException extends CoreCommException {
    /**
     * 状态码
     */
    private final int status;
    
    /**
     * 请求类型
     */
    private final String kind;
    
    /**
     * 请求 ID
     */
    private final long requestId;

    /**
     * 构造函数。
     *
     * @param status 状态码
     * @param kind 请求类型
     * @param requestId 请求 ID
     * @param message 错误消息
     */
    public CoreRemoteException(int status, String kind, long requestId, String message) {
        super(message);
        this.status = status;
        this.kind = kind;
        this.requestId = requestId;
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
     * 获取请求类型。
     *
     * @return 请求类型
     */
    public String kind() {
        return kind;
    }

    /**
     * 获取请求 ID。
     *
     * @return 请求 ID
     */
    public long requestId() {
        return requestId;
    }
}
