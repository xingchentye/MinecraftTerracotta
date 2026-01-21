package com.endercore.core.comm.exception;

/**
 * 协议相关异常：帧格式非法、版本不匹配、超限等。
 */
public final class CoreProtocolException extends CoreCommException {
    /**
     * 构建协议异常。
     *
     * @param message 错误信息
     */
    public CoreProtocolException(String message) {
        super(message);
    }

    /**
     * 构建协议异常。
     *
     * @param message 错误信息
     * @param cause   根因
     */
    public CoreProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
