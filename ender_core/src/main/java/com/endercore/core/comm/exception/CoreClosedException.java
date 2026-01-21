package com.endercore.core.comm.exception;

/**
 * 连接已关闭或不可用。
 */
public final class CoreClosedException extends CoreCommException {
    /**
     * 构建连接关闭异常。
     *
     * @param message 错误信息
     */
    public CoreClosedException(String message) {
        super(message);
    }
}
