package com.endercore.core.comm.exception;

 
/**
 * 连接关闭异常。
 * 当尝试在已关闭的连接上执行操作时抛出。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public final class CoreClosedException extends CoreCommException {
    /**
     * 构造函数。
     *
     * @param message 错误消息
     */
    public CoreClosedException(String message) {
        super(message);
    }
}
