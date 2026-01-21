package com.multiplayer.ender.logic;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BufferPool {
    private static final int BUFFER_SIZE = 4096;
    private static final int MAX_POOL_SIZE = 100;
    
    private static final Queue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();

    public static ByteBuffer acquire() {
        ByteBuffer buffer = pool.poll();
        if (buffer == null) {
            return ByteBuffer.allocate(BUFFER_SIZE);
        }
        buffer.clear();
        return buffer;
    }

    public static void release(ByteBuffer buffer) {
        if (buffer != null && pool.size() < MAX_POOL_SIZE) {
            buffer.clear();
            pool.offer(buffer);
        }
    }
}

