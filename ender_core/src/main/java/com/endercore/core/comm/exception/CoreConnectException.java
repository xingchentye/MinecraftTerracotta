package com.endercore.core.comm.exception;

 
/**
 * 连接异常。
 * 当连接失败或断开时抛出。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public final class CoreConnectException extends CoreCommException {
    /**
     * 构造函数。
     *
     * @param message 错误消息
     * @param cause 原因异常
     */
    public CoreConnectException(String message, Throwable cause) {
        super(message, cause);
    }
}
