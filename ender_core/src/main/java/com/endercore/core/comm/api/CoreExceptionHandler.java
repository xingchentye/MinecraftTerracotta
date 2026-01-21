package com.endercore.core.comm.api;

/**
 * 异常处理接口：用于统一承接连接、协议、远端与超时异常。
 */
public interface CoreExceptionHandler {

    /**
     * 连接级异常：连接失败、断链、重连失败等。
     *
     * @param error 异常
     */
    void onConnectionError(Throwable error);

    /**
     * 协议级异常：非法帧、版本不匹配、超限帧等。
     *
     * @param error 异常
     */
    void onProtocolError(Throwable error);

    /**
     * 远端返回的业务失败（非 0 status）。
     *
     * @param error 异常
     */
    void onRemoteError(Throwable error);

    /**
     * 请求超时。
     *
     * @param error 异常
     */
    void onTimeout(Throwable error);
}

