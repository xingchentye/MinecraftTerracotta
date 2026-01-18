package com.multiplayer.terracotta.network;

import com.multiplayer.terracotta.logic.BufferPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class NetworkClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkClient.class);
    private AsynchronousSocketChannel client;
    private static NetworkClient instance;

    private NetworkClient() {}

    public static synchronized NetworkClient getInstance() {
        if (instance == null) {
            instance = new NetworkClient();
        }
        return instance;
    }

    public CompletableFuture<Void> connect(String host, int port) {
        CompletableFuture<Void> resultFuture = new CompletableFuture<>();
        try {
            if (client != null && client.isOpen()) {
                LOGGER.warn("连接已存在，正在关闭旧连接...");
                close();
            }
            
            client = AsynchronousSocketChannel.open();
            InetSocketAddress address = new InetSocketAddress(host, port);
            Future<Void> future = client.connect(address);
            
            LOGGER.info("正在尝试连接到 {}:{}", host, port);

            new Thread(() -> {
                try {
                    future.get();
                    LOGGER.info("成功连接到服务器: {}:{}", host, port);
                    read();
                    resultFuture.complete(null);
                } catch (InterruptedException | ExecutionException e) {
                    LOGGER.error("连接失败: {}", e.getMessage());
                    resultFuture.completeExceptionally(e);
                }
            }, "Terracotta-Network-Thread").start();
            
        } catch (IOException e) {
            LOGGER.error("无法打开网络通道", e);
            resultFuture.completeExceptionally(e);
        }
        return resultFuture;
    }
    
    private void read() {
        if (client != null && client.isOpen()) {
            ByteBuffer buffer = BufferPool.acquire();
            client.read(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer result, ByteBuffer attachment) {
                    if (result == -1) {
                        LOGGER.info("连接被远程主机关闭");
                        BufferPool.release(attachment);
                        close();
                        TerracottaApiClient.clearDynamicPort();
                        return;
                    }
                    
                    attachment.flip();
                    byte[] data = new byte[attachment.limit()];
                    attachment.get(data);
                    LOGGER.debug("收到数据: {} 字节", result);
                    
                    BufferPool.release(attachment);
                    
                    if (client.isOpen()) {
                        read();
                    }
                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    LOGGER.error("读取数据异常", exc);
                    BufferPool.release(attachment);
                    close();
                }
            });
        }
    }
    
    public void close() {
        try {
            if (client != null && client.isOpen()) {
                client.close();
                LOGGER.info("网络连接已关闭");
            }
        } catch (IOException e) {
            LOGGER.error("关闭连接时发生错误", e);
        }
    }
    
    public void send(byte[] data) {
        if (client != null && client.isOpen()) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            client.write(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer result, ByteBuffer attachment) {
                   LOGGER.debug("成功发送 {} 字节", result);
                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    LOGGER.error("发送数据失败", exc);
                }
            });
        } else {
            LOGGER.warn("尝试在未连接状态下发送数据");
        }
    }
}

