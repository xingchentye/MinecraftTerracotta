package com.multiplayer.ender.network;

import com.endercore.core.comm.CoreComm;
import com.endercore.core.comm.client.CoreWebSocketClient;
import com.endercore.core.comm.config.CoreWebSocketConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class NetworkClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkClient.class);
    private static NetworkClient instance;
    private CoreWebSocketClient client;

    private NetworkClient() {}

    public static synchronized NetworkClient getInstance() {
        if (instance == null) {
            instance = new NetworkClient();
        }
        return instance;
    }

    public CompletableFuture<Void> connect(String host, int port) {
        LOGGER.info("Connecting to {}:{} via EnderCore WebSocket", host, port);
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        try {
            CoreWebSocketConfig config = CoreWebSocketConfig.builder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
                
            client = CoreComm.newClient(config, null, null);
            // Assuming default path, should be configured
            URI uri = URI.create("ws://" + host + ":" + port + "/ws"); 
            
            // Asynchronous connect
            new Thread(() -> {
                try {
                    client.connect(uri).get();
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }).start();
            
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }
    
    public void close() {
        if (client != null) {
            try {
                client.close(Duration.ofSeconds(1));
            } catch (Exception e) {
                LOGGER.warn("Error closing client", e);
            }
        }
    }
}

