package com.endercore.core.comm.protocol;

/**
 * ECWS/1 协议常量。
 */
public final class CoreProtocol {
    public static final byte MAGIC_0 = 0x45;
    public static final byte MAGIC_1 = 0x43;
    public static final byte VERSION = 1;

    public static final int HEADER_BYTES = 2 + 1 + 1 + 1 + 1 + 8 + 2 + 4;

    private CoreProtocol() {
    }
}

