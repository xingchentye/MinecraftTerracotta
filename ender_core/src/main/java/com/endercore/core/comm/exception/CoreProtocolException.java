package com.endercore.core.comm.exception;

 
/**
 * 协议异常。
 * 当协议解析或验证失败时抛出。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public final class CoreProtocolException extends CoreCommException {
    /**
     * 构造函数。
     *
     * @param message 错误消息
     */
    public CoreProtocolException(String message) {
        super(message);
    }

    /**
     * 构造函数。
     *
     * @param message 错误消息
     * @param cause 原因异常
     */
    public CoreProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
