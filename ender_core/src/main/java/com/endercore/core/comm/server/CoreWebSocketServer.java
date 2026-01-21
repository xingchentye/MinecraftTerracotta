package com.endercore.core.comm.server;

import com.endercore.core.comm.exception.CoreProtocolException;
import com.endercore.core.comm.monitor.ConnectionMetrics;
import com.endercore.core.comm.monitor.ConnectionMetricsSnapshot;
import com.endercore.core.comm.protocol.CoreFrame;
import com.endercore.core.comm.protocol.CoreFrameCodec;
import com.endercore.core.comm.protocol.CoreKinds;
import com.endercore.core.comm.protocol.CoreMessageType;
import com.endercore.core.comm.protocol.CoreResponse;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Core WebSocket 服务端：接收 ECWS/1 Request 并分发到注册的处理器。
 */
public final class CoreWebSocketServer extends WebSocketServer {
    private final CoreFrameCodec codec;
    private final ConcurrentHashMap<String, CoreRequestHandler> handlers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CoreEventHandler> eventHandlers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<InetSocketAddress, WebSocket> connectionsByRemote = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<InetSocketAddress>> closeListeners = new CopyOnWriteArrayList<>();
    private final Executor handlerExecutor;
    private final CompletableFuture<Void> started = new CompletableFuture<>();
    private final ConnectionMetrics metrics = new ConnectionMetrics();
    private final AtomicLong connections = new AtomicLong();

    public CoreWebSocketServer(InetSocketAddress address, int maxFrameBytes, Executor handlerExecutor) {
        super(address);
        this.codec = new CoreFrameCodec(maxFrameBytes);
        this.handlerExecutor = handlerExecutor == null ? Executors.newCachedThreadPool() : handlerExecutor;
    }

    /**
     * 注册请求处理器。
     *
     * @param kind    namespace:path
     * @param handler 处理器
     */
    public void register(String kind, CoreRequestHandler handler) {
        CoreKinds.validate(kind);
        handlers.put(kind, Objects.requireNonNull(handler, "handler"));
    }

    /**
     * 注册事件处理器（处理客户端发来的单向事件）。
     *
     * @param kind    namespace:path
     * @param handler 处理器
     */
    public void registerEvent(String kind, CoreEventHandler handler) {
        CoreKinds.validate(kind);
        eventHandlers.put(kind, Objects.requireNonNull(handler, "handler"));
    }

    /**
     * 广播单向事件给所有连接的客户端。
     *
     * @param kind    namespace:path
     * @param payload 负载
     */
    public void broadcastEvent(String kind, byte[] payload) {
        CoreKinds.validate(kind);
        byte[] bytes = codec.encode(new CoreFrame(CoreMessageType.EVENT, (byte) 0, 0, 0, kind, payload));
        broadcast(bytes);
    }

    public boolean sendEventTo(InetSocketAddress remoteAddress, String kind, byte[] payload) {
        Objects.requireNonNull(remoteAddress, "remoteAddress");
        CoreKinds.validate(kind);
        WebSocket conn = connectionsByRemote.get(remoteAddress);
        if (conn == null) {
            return false;
        }
        byte[] bytes = codec.encode(new CoreFrame(CoreMessageType.EVENT, (byte) 0, 0, 0, kind, payload));
        metrics.onFrameSent(bytes.length);
        conn.send(bytes);
        return true;
    }

    public void sendEventToMany(Iterable<InetSocketAddress> remoteAddresses, String kind, byte[] payload) {
        Objects.requireNonNull(remoteAddresses, "remoteAddresses");
        CoreKinds.validate(kind);
        byte[] bytes = codec.encode(new CoreFrame(CoreMessageType.EVENT, (byte) 0, 0, 0, kind, payload));
        for (InetSocketAddress remote : remoteAddresses) {
            WebSocket conn = remote == null ? null : connectionsByRemote.get(remote);
            if (conn == null) {
                continue;
            }
            metrics.onFrameSent(bytes.length);
            conn.send(bytes);
        }
    }

    public void onConnectionClosed(Consumer<InetSocketAddress> listener) {
        closeListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    /**
     * 连接建立回调。
     */
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connections.incrementAndGet();
        InetSocketAddress remote = conn.getRemoteSocketAddress();
        if (remote != null) {
            connectionsByRemote.put(remote, conn);
        }
    }

    @Override
    /**
     * 连接关闭回调。
     */
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        connections.decrementAndGet();
        InetSocketAddress remoteAddress = conn == null ? null : conn.getRemoteSocketAddress();
        if (remoteAddress != null) {
            connectionsByRemote.remove(remoteAddress);
            for (Consumer<InetSocketAddress> listener : closeListeners) {
                try {
                    listener.accept(remoteAddress);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    /**
     * 收到二进制帧回调：解析协议并分发请求。
     */
    public void onMessage(WebSocket conn, ByteBuffer message) {
        metrics.onFrameReceived(message.remaining());
        CoreFrame frame;
        try {
            frame = codec.decode(message);
        } catch (CoreProtocolException e) {
            conn.close(1002, e.getMessage());
            return;
        } catch (RuntimeException e) {
            conn.close(1011, "协议解析失败");
            return;
        }

        if (frame.type() == CoreMessageType.REQUEST) {
            handleRequest(conn, frame);
        } else if (frame.type() == CoreMessageType.EVENT) {
            handleEvent(conn, frame);
        } else if (frame.type() == CoreMessageType.HEARTBEAT) {
            handleHeartbeat(conn, frame);
        }
    }

    @Override
    /**
     * 收到文本帧回调：按协议错误处理。
     */
    public void onMessage(WebSocket conn, String message) {
        conn.close(1003, "不支持文本帧");
    }

    @Override
    /**
     * WebSocket 底层错误回调。
     */
    public void onError(WebSocket conn, Exception ex) {
    }

    @Override
    /**
     * 服务端启动完成回调。
     */
    public void onStart() {
        started.complete(null);
    }

    /**
     * 等待服务端启动完成（监听端口绑定成功）。
     *
     * @param timeout 超时时间
     */
    public void awaitStarted(java.time.Duration timeout) {
        try {
            started.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("服务端启动超时", e);
        }
    }

    /**
     * 处理请求帧：路由到 handler 并将结果回包。
     */
    private void handleRequest(WebSocket conn, CoreFrame frame) {
        CoreKinds.validate(frame.kind());
        CoreRequestHandler handler = handlers.get(frame.kind());
        if (handler == null) {
            byte[] payload = ("Requested protocol hasn't been implemented: " + frame.kind())
                    .getBytes(StandardCharsets.UTF_8);
            sendResponse(conn, new CoreResponse(255, frame.requestId(), frame.kind(), payload));
            return;
        }

        InetSocketAddress remote = conn.getRemoteSocketAddress();
        CoreRequest request = new CoreRequest(frame.requestId(), frame.kind(), frame.payload(), remote);
        handlerExecutor.execute(() -> {
            try {
                CoreResponse response = handler.handle(request);
                if (response == null) {
                    response = new CoreResponse(255, request.requestId(), request.kind(),
                            "Handler returned null response".getBytes(StandardCharsets.UTF_8));
                }
                sendResponse(conn, response);
            } catch (Exception e) {
                byte[] payload = String.valueOf(e).getBytes(StandardCharsets.UTF_8);
                sendResponse(conn, new CoreResponse(255, request.requestId(), request.kind(), payload));
            }
        });
    }

    /**
     * 发送响应帧。
     */
    private void sendResponse(WebSocket conn, CoreResponse response) {
        byte[] bytes = codec.encode(new CoreFrame(
                CoreMessageType.RESPONSE,
                (byte) 0,
                response.status(),
                response.requestId(),
                response.kind(),
                response.payload()
        ));
        metrics.onFrameSent(bytes.length);
        conn.send(bytes);
    }

    /**
     * 处理事件帧：路由到事件处理器，不回包。
     */
    private void handleEvent(WebSocket conn, CoreFrame frame) {
        CoreKinds.validate(frame.kind());
        CoreEventHandler handler = eventHandlers.get(frame.kind());
        if (handler == null) {
            return;
        }
        InetSocketAddress remote = conn.getRemoteSocketAddress();
        handlerExecutor.execute(() -> {
            try {
                handler.handle(frame.kind(), frame.payload(), remote);
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * 处理心跳帧：回发心跳响应以便对端观测链路状态。
     */
    private void handleHeartbeat(WebSocket conn, CoreFrame frame) {
        byte[] bytes = codec.encode(new CoreFrame(CoreMessageType.HEARTBEAT, (byte) 0, 0, frame.requestId(), "", new byte[0]));
        metrics.onFrameSent(bytes.length);
        conn.send(bytes);
    }

    /**
     * 获取当前服务端指标快照。
     */
    public ConnectionMetricsSnapshot metrics() {
        return metrics.snapshot(0);
    }

    /**
     * 获取当前连接数。
     */
    public long connections() {
        return connections.get();
    }
}
