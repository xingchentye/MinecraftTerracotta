package com.endercore.core.comm.api;

 
/**
 * 核心异常处理器接口。
 * 用于处理 CoreComm 通信过程中发生的各种异常。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public interface CoreExceptionHandler {

    /**
     * 处理连接错误。
     * 如连接中断、连接失败等。
     *
     * @param error 异常对象
     */
    void onConnectionError(Throwable error);

    /**
     * 处理协议错误。
     * 如数据包解析失败、协议版本不匹配等。
     *
     * @param error 异常对象
     */
    void onProtocolError(Throwable error);

    /**
     * 处理远程错误。
     * 如服务器返回错误响应。
     *
     * @param error 异常对象
     */
    void onRemoteError(Throwable error);

    /**
     * 处理超时错误。
     * 如请求超时。
     *
     * @param error 异常对象
     */
    void onTimeout(Throwable error);
}

