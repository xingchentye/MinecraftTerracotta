package com.multiplayer.terracotta.logic;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class BufferPoolTest {

    @Test
    void testAcquireAndRelease() {
        // 获取第一个 buffer
        ByteBuffer buffer1 = BufferPool.acquire();
        assertNotNull(buffer1);
        assertEquals(4096, buffer1.capacity());

        // 归还 buffer
        BufferPool.release(buffer1);

        // 再次获取，应该是同一个对象 (如果池逻辑正确)
        // 注意：这里取决于具体的池实现，ConcurrentLinkedQueue 是 FIFO，所以如果只有一个，它就是同一个
        ByteBuffer buffer2 = BufferPool.acquire();
        assertSame(buffer1, buffer2, "Should reuse the released buffer");
    }

    @Test
    void testClearOnAcquire() {
        ByteBuffer buffer = BufferPool.acquire();
        buffer.put((byte) 1);
        BufferPool.release(buffer);

        ByteBuffer buffer2 = BufferPool.acquire();
        assertEquals(0, buffer2.position(), "Buffer should be cleared on acquire");
    }
}
