package com.multiplayer.terracotta.logic;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * ByteBuffer 对象池
 * 用于复用网络通信中的缓冲区，减少 GC 压力
 */
public class BufferPool {
    private static final int BUFFER_SIZE = 4096;
    private static final int MAX_POOL_SIZE = 100;
    
    private static final Queue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();

    /**
     * 获取一个 ByteBuffer
     * 如果池中有可用对象则复用，否则创建新的
     *
     * @return 清空状态的 ByteBuffer
     */
    public static ByteBuffer acquire() {
        ByteBuffer buffer = pool.poll();
        if (buffer == null) {
            return ByteBuffer.allocate(BUFFER_SIZE);
        }
        buffer.clear();
        return buffer;
    }

    /**
     * 归还 ByteBuffer 到池中
     *
     * @param buffer 要归还的 ByteBuffer
     */
    public static void release(ByteBuffer buffer) {
        if (buffer != null && pool.size() < MAX_POOL_SIZE) {
            buffer.clear();
            pool.offer(buffer);
        }
    }
}
