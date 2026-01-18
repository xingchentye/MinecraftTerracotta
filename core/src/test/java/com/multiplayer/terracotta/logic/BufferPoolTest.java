package com.multiplayer.terracotta.logic;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class BufferPoolTest {

    @Test
    void testAcquireAndRelease() {
        ByteBuffer buffer1 = BufferPool.acquire();
        assertNotNull(buffer1);
        assertEquals(4096, buffer1.capacity());

        BufferPool.release(buffer1);

        ByteBuffer buffer2 = BufferPool.acquire();
        assertSame(buffer1, buffer2);
    }

    @Test
    void testClearOnAcquire() {
        ByteBuffer buffer = BufferPool.acquire();
        buffer.put((byte) 1);
        BufferPool.release(buffer);

        ByteBuffer buffer2 = BufferPool.acquire();
        assertEquals(0, buffer2.position());
    }
}

