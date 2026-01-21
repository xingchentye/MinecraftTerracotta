package com.endercore.core.comm.protocol;

import com.endercore.core.comm.exception.CoreProtocolException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * ECWS/1 帧编解码器。
 */
public final class CoreFrameCodec {
    private final int maxFrameBytes;

    /**
     * 创建编解码器。
     *
     * @param maxFrameBytes 最大帧大小
     */
    public CoreFrameCodec(int maxFrameBytes) {
        if (maxFrameBytes < CoreProtocol.HEADER_BYTES) {
            throw new IllegalArgumentException("maxFrameBytes 过小: " + maxFrameBytes);
        }
        this.maxFrameBytes = maxFrameBytes;
    }

    /**
     * 将帧编码为二进制数组，用于 WebSocket binary frame 发送。
     *
     * @param frame 帧对象
     * @return 编码后字节数组
     */
    public byte[] encode(CoreFrame frame) {
        byte[] kindBytes = frame.kind().getBytes(StandardCharsets.UTF_8);
        byte[] payload = frame.payload();

        if (kindBytes.length > 0xFFFF) {
            throw new CoreProtocolException("kind 过长: " + kindBytes.length);
        }
        if (payload.length < 0) {
            throw new CoreProtocolException("payload 长度非法: " + payload.length);
        }

        int total = CoreProtocol.HEADER_BYTES + kindBytes.length + payload.length;
        if (total > maxFrameBytes) {
            throw new CoreProtocolException("帧大小超过上限: " + total + " > " + maxFrameBytes);
        }

        ByteBuffer buf = ByteBuffer.allocate(total);
        buf.put(CoreProtocol.MAGIC_0);
        buf.put(CoreProtocol.MAGIC_1);
        buf.put(CoreProtocol.VERSION);
        buf.put(frame.type().code());
        buf.put(frame.flags());
        buf.put((byte) (frame.status() & 0xFF));
        buf.putLong(frame.requestId());
        buf.putShort((short) (kindBytes.length & 0xFFFF));
        buf.putInt(payload.length);
        buf.put(kindBytes);
        buf.put(payload);
        return buf.array();
    }

    /**
     * 从二进制缓冲区解码得到帧对象。
     *
     * @param input 输入缓冲区
     * @return 帧对象
     */
    public CoreFrame decode(ByteBuffer input) {
        if (input == null) {
            throw new CoreProtocolException("空帧");
        }
        if (input.remaining() > maxFrameBytes) {
            throw new CoreProtocolException("帧大小超过上限: " + input.remaining() + " > " + maxFrameBytes);
        }
        if (input.remaining() < CoreProtocol.HEADER_BYTES) {
            throw new CoreProtocolException("帧长度不足: " + input.remaining());
        }

        byte magic0 = input.get();
        byte magic1 = input.get();
        if (magic0 != CoreProtocol.MAGIC_0 || magic1 != CoreProtocol.MAGIC_1) {
            throw new CoreProtocolException("magic 不匹配");
        }

        byte version = input.get();
        if (version != CoreProtocol.VERSION) {
            throw new CoreProtocolException("版本不支持: " + (version & 0xFF));
        }

        CoreMessageType type;
        try {
            type = CoreMessageType.from(input.get());
        } catch (IllegalArgumentException e) {
            throw new CoreProtocolException("type 不支持", e);
        }

        byte flags = input.get();
        int status = input.get() & 0xFF;
        long requestId = input.getLong();
        int kindLen = input.getShort() & 0xFFFF;
        int payloadLen = input.getInt();
        if (payloadLen < 0) {
            throw new CoreProtocolException("payloadLen 非法: " + payloadLen);
        }

        int need = CoreProtocol.HEADER_BYTES + kindLen + payloadLen;
        if (need > maxFrameBytes) {
            throw new CoreProtocolException("帧大小超过上限: " + need + " > " + maxFrameBytes);
        }
        if (input.remaining() != kindLen + payloadLen) {
            throw new CoreProtocolException("帧长度不一致: remaining=" + input.remaining() + ", expected=" + (kindLen + payloadLen));
        }

        byte[] kindBytes = new byte[kindLen];
        input.get(kindBytes);
        String kind = new String(kindBytes, StandardCharsets.UTF_8);

        byte[] payload = new byte[payloadLen];
        input.get(payload);

        return new CoreFrame(type, flags, status, requestId, kind, payload);
    }
}
