package com.endercore.core.comm.exception;

/**
 * 远端返回的业务失败（非 0 status）。
 */
public final class CoreRemoteException extends CoreCommException {
    private final int status;
    private final String kind;
    private final long requestId;

    /**
     * 构建远端业务异常。
     *
     * @param status    远端状态码
     * @param kind      请求 kind
     * @param requestId 请求 ID
     * @param message   错误信息
     */
    public CoreRemoteException(int status, String kind, long requestId, String message) {
        super(message);
        this.status = status;
        this.kind = kind;
        this.requestId = requestId;
    }

    /**
     * 获取远端状态码。
     */
    public int status() {
        return status;
    }

    /**
     * 获取请求 kind。
     */
    public String kind() {
        return kind;
    }

    /**
     * 获取请求 ID。
     */
    public long requestId() {
        return requestId;
    }
}
