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
 * CoreComm 核心通信入口类。
 * 提供创建客户端和服务器的工厂方法，以及命令行入口。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public final class CoreComm {
    private CoreComm() {
    }

    /**
     * 创建新的 WebSocket 客户端。
     *
     * @param config           客户端配置
     * @param exceptionHandler 异常处理器（可选）
     * @param callbackExecutor 回调执行器（可选，默认在 Netty 线程中执行）
     * @return WebSocket 客户端实例
     */
    public static CoreWebSocketClient newClient(CoreWebSocketConfig config, CoreExceptionHandler exceptionHandler, Executor callbackExecutor) {
        return new CoreWebSocketClient(config, exceptionHandler, callbackExecutor);
    }

    /**
     * 创建新的 WebSocket 服务器。
     *
     * @param address         绑定地址
     * @param maxFrameBytes   最大帧大小
     * @param handlerExecutor 处理器执行器（可选，默认在 Netty 线程中执行）
     * @return WebSocket 服务器实例
     */
    public static CoreWebSocketServer newServer(InetSocketAddress address, int maxFrameBytes, Executor handlerExecutor) {
        return new CoreWebSocketServer(address, maxFrameBytes, handlerExecutor);
    }

    /**
     * 命令行入口。
     * 支持 server 和 client 模式，用于测试通信功能。
     *
     * @param args 命令行参数
     * @throws Exception 启动过程中的异常
     */
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
