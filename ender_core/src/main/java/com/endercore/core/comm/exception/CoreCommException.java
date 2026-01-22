package com.endercore.core.comm.exception;

 
/**
 * 通信异常基类。
 * 所有 Core 通信相关异常的父类。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public class CoreCommException extends RuntimeException {
    /**
     * 构造函数。
     *
     * @param message 错误消息
     */
    public CoreCommException(String message) {
        super(message);
    }

    /**
     * 构造函数。
     *
     * @param message 错误消息
     * @param cause 原因异常
     */
    public CoreCommException(String message, Throwable cause) {
        super(message, cause);
    }
}
