package com.endercore.core.comm.protocol;

/**
 * ECWS/1 消息类型。
 */
public enum CoreMessageType {
    REQUEST((byte) 0),
    RESPONSE((byte) 1),
    EVENT((byte) 2),
    HEARTBEAT((byte) 3);

    private final byte code;

    CoreMessageType(byte code) {
        this.code = code;
    }

    public byte code() {
        return code;
    }

    /**
     * 根据类型码解析消息类型。
     *
     * @param code 类型码
     * @return 消息类型
     */
    public static CoreMessageType from(byte code) {
        for (CoreMessageType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知消息类型: " + (code & 0xFF));
    }
}
