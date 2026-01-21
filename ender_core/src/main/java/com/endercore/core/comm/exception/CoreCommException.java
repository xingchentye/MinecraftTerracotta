package com.endercore.core.comm.exception;

/**
 * Core 通信模块统一异常基类。
 */
public class CoreCommException extends RuntimeException {
    /**
     * 构建通信异常。
     *
     * @param message 错误信息
     */
    public CoreCommException(String message) {
        super(message);
    }

    /**
     * 构建通信异常。
     *
     * @param message 错误信息
     * @param cause   根因
     */
    public CoreCommException(String message, Throwable cause) {
        super(message, cause);
    }
}
