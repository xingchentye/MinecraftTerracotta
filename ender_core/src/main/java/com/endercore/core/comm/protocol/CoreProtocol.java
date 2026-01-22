package com.endercore.core.comm.protocol;

 
/**
 * 协议常量定义。
 * 定义了协议的魔数、版本号和头部长度。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public final class CoreProtocol {
    /**
     * 魔数第一字节 (E)
     */
    public static final byte MAGIC_0 = 0x45;
    
    /**
     * 魔数第二字节 (C)
     */
    public static final byte MAGIC_1 = 0x43;
    
    /**
     * 协议版本号
     */
    public static final byte VERSION = 1;

    /**
     * 协议头长度
     * 2(Magic) + 1(Version) + 1(Type) + 1(Flags) + 1(Status) + 8(RequestId) + 2(KindLen) + 4(PayloadLen)
     */
    public static final int HEADER_BYTES = 2 + 1 + 1 + 1 + 1 + 8 + 2 + 4;

    /**
     * 私有构造函数，防止实例化。
     */
    private CoreProtocol() {
    }
}

