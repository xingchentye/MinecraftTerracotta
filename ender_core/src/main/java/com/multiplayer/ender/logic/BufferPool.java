package com.multiplayer.ender.logic;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 缓冲区池管理类。
 * 用于复用 ByteBuffer 对象，减少内存分配和垃圾回收开销。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public class BufferPool {
    /**
     * 单个缓冲区的大小（4KB）。
     */
    private static final int BUFFER_SIZE = 4096;
    
    /**
     * 池中最大允许缓存的缓冲区数量。
     */
    private static final int MAX_POOL_SIZE = 100;
    
    /**
     * 存放空闲缓冲区的队列。
     */
    private static final Queue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();

    /**
     * 获取一个 ByteBuffer。
     * 如果池中有空闲的缓冲区则重用，否则创建一个新的。
     *
     * @return 准备好的 ByteBuffer 对象
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
     * 归还一个 ByteBuffer 到池中。
     * 如果池未满，则将缓冲区清理后放入池中等待复用。
     *
     * @param buffer 要归还的 ByteBuffer 对象
     */
    public static void release(ByteBuffer buffer) {
        if (buffer != null && pool.size() < MAX_POOL_SIZE) {
            buffer.clear();
            pool.offer(buffer);
        }
    }
}

