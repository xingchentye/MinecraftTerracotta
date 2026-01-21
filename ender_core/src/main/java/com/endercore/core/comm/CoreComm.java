package com.endercore.core.comm;

import com.endercore.core.comm.api.CoreExceptionHandler;
import com.endercore.core.comm.client.CoreWebSocketClient;
import com.endercore.core.comm.config.CoreWebSocketConfig;
import com.endercore.core.comm.protocol.CoreResponse;
import com.endercore.core.comm.server.CoreRequest;
import com.endercore.core.comm.server.CoreWebSocketServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

/**
 * Core 通信模块入口：提供创建客户端/服务端的便捷方法。
 */
public final class CoreComm {
    private CoreComm() {
    }

    /**
     * 创建 WebSocket 客户端。
     *
     * @param config           配置
     * @param exceptionHandler 异常回调（可为 null）
     * @param callbackExecutor 回调执行器（可为 null）
     * @return 客户端实例
     */
    public static CoreWebSocketClient newClient(CoreWebSocketConfig config, CoreExceptionHandler exceptionHandler, Executor callbackExecutor) {
        return new CoreWebSocketClient(config, exceptionHandler, callbackExecutor);
    }

    /**
     * 创建 WebSocket 服务端。
     *
     * @param address        监听地址
     * @param maxFrameBytes  最大帧大小
     * @param handlerExecutor handler 执行线程池（可为 null）
     * @return 服务端实例
     */
    public static CoreWebSocketServer newServer(InetSocketAddress address, int maxFrameBytes, Executor handlerExecutor) {
        return new CoreWebSocketServer(address, maxFrameBytes, handlerExecutor);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage:");
            System.err.println("  server [host] [port]");
            System.err.println("  client <wsUrl> [kind] [payloadUtf8]");
            return;
        }

        String mode = args[0];
        if ("server".equalsIgnoreCase(mode)) {
            String host = args.length >= 2 ? args[1] : "0.0.0.0";
            int port = args.length >= 3 ? Integer.parseInt(args[2]) : 18080;

            CoreWebSocketServer server = newServer(new InetSocketAddress(host, port), 4 * 1024 * 1024, null);
            server.register("c:ping", (CoreRequest req) -> new CoreResponse(0, req.requestId(), req.kind(), req.payload()));
            server.start();
            server.awaitStarted(Duration.ofSeconds(5));
            System.out.println("CoreWebSocketServer started on ws://" + host + ":" + port);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    server.stop(1000);
                } catch (Exception ignored) {
                }
            }));

            new CountDownLatch(1).await();
            return;
        }

        if ("client".equalsIgnoreCase(mode)) {
            if (args.length < 2) {
                System.err.println("client mode requires wsUrl");
                return;
            }
            URI uri = URI.create(args[1]);
            String kind = args.length >= 3 ? args[2] : "c:ping";
            String payloadUtf8 = args.length >= 4 ? args[3] : "hello";

            CoreWebSocketClient client = newClient(CoreWebSocketConfig.builder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .requestTimeout(Duration.ofSeconds(5))
                    .heartbeatInterval(Duration.ZERO)
                    .build(), null, null);
            client.connect(uri).get();
            CoreResponse resp = client.sendSync(kind, payloadUtf8.getBytes(StandardCharsets.UTF_8), Duration.ofSeconds(5));
            System.out.println("status=" + resp.status() + ", ok=" + resp.isOk() + ", payloadUtf8=" + resp.payloadUtf8());
            client.close(Duration.ofSeconds(2)).get();
            return;
        }

        System.err.println("Unknown mode: " + mode);
        System.err.println("Usage:");
        System.err.println("  server [host] [port]");
        System.err.println("  client <wsUrl> [kind] [payloadUtf8]");
    }
}
