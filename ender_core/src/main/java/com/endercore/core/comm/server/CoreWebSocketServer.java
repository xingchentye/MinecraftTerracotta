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
 * 核心 WebSocket 服务器实现。
 * 负责处理 WebSocket 连接、请求分发、事件广播以及连接管理。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
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

    /**
     * 构造函数。
     *
     * @param address 绑定地址
     * @param maxFrameBytes 最大帧大小（字节）
     * @param handlerExecutor 处理器执行器，如果为 null 则使用 CachedThreadPool
     */
    public CoreWebSocketServer(InetSocketAddress address, int maxFrameBytes, Executor handlerExecutor) {
        super(address);
        this.codec = new CoreFrameCodec(maxFrameBytes);
        this.handlerExecutor = handlerExecutor == null ? Executors.newCachedThreadPool() : handlerExecutor;
    }

    /**
     * 注册请求处理器。
     *
     * @param kind 请求类型
     * @param handler 请求处理器
     */
    public void register(String kind, CoreRequestHandler handler) {
        CoreKinds.validate(kind);
        handlers.put(kind, Objects.requireNonNull(handler, "handler"));
    }

    /**
     * 注册事件处理器。
     *
     * @param kind 事件类型
     * @param handler 事件处理器
     */
    public void registerEvent(String kind, CoreEventHandler handler) {
        CoreKinds.validate(kind);
        eventHandlers.put(kind, Objects.requireNonNull(handler, "handler"));
    }

    /**
     * 广播事件给所有连接的客户端。
     *
     * @param kind 事件类型
     * @param payload 事件负载
     */
    public void broadcastEvent(String kind, byte[] payload) {
        CoreKinds.validate(kind);
        byte[] bytes = codec.encode(new CoreFrame(CoreMessageType.EVENT, (byte) 0, 0, 0, kind, payload));
        broadcast(bytes);
    }

    /**
     * 发送事件到指定客户端。
     *
     * @param remoteAddress 远程地址
     * @param kind 事件类型
     * @param payload 事件负载
     * @return 如果发送成功返回 true，否则返回 false（例如客户端未连接）
     */
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

    /**
     * 发送事件到多个客户端。
     *
     * @param remoteAddresses 远程地址集合
     * @param kind 事件类型
     * @param payload 事件负载
     */
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

    /**
     * 注册连接关闭监听器。
     *
     * @param listener 监听器
     */
    public void onConnectionClosed(Consumer<InetSocketAddress> listener) {
        closeListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    /**
     * 当 WebSocket 连接打开时调用。
     *
     * @param conn WebSocket 连接
     * @param handshake 握手信息
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
     * 当 WebSocket 连接关闭时调用。
     *
     * @param conn WebSocket 连接
     * @param code 关闭代码
     * @param reason 关闭原因
     * @param remote 是否由远程关闭
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
     * 当接收到二进制消息时调用。
     *
     * @param conn WebSocket 连接
     * @param message 二进制消息
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
     * 当接收到文本消息时调用。
     * 本服务器不支持文本帧。
     *
     * @param conn WebSocket 连接
     * @param message 文本消息
     */
    public void onMessage(WebSocket conn, String message) {
        conn.close(1003, "不支持文本帧");
    }

    @Override
    /**
     * 当发生错误时调用。
     *
     * @param conn WebSocket 连接
     * @param ex 异常对象
     */
    public void onError(WebSocket conn, Exception ex) {
    }

    @Override
    /**
     * 当服务器启动时调用。
     */
    public void onStart() {
        started.complete(null);
    }

    /**
     * 等待服务器启动完成。
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
     * 处理请求帧。
     *
     * @param conn WebSocket 连接
     * @param frame 请求帧
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
     *
     * @param conn WebSocket 连接
     * @param response 响应对象
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
     * 处理事件帧。
     *
     * @param conn WebSocket 连接
     * @param frame 事件帧
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
     * 处理心跳帧。
     *
     * @param conn WebSocket 连接
     * @param frame 心跳帧
     */
    private void handleHeartbeat(WebSocket conn, CoreFrame frame) {
        byte[] bytes = codec.encode(new CoreFrame(CoreMessageType.HEARTBEAT, (byte) 0, 0, frame.requestId(), "", new byte[0]));
        metrics.onFrameSent(bytes.length);
        conn.send(bytes);
    }

    /**
     * 获取连接指标快照。
     *
     * @return 连接指标快照
     */
    public ConnectionMetricsSnapshot metrics() {
        return metrics.snapshot(0);
    }

    /**
     * 获取当前连接数。
     *
     * @return 当前连接数
     */
    public long connections() {
        return connections.get();
    }
}
