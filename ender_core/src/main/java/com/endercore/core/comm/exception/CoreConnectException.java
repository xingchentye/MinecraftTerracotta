package com.endercore.core.comm.exception;

/**
 * 连接相关异常：连接失败、握手失败、断链等。
 */
public final class CoreConnectException extends CoreCommException {
    /**
     * 构建连接异常。
     *
     * @param message 错误信息
     * @param cause   根因
     */
    public CoreConnectException(String message, Throwable cause) {
        super(message, cause);
    }
}
