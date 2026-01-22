package com.endercore.core.comm.client;

import com.endercore.core.comm.api.CoreConnectionManager;
import com.endercore.core.comm.api.CoreExceptionHandler;
import com.endercore.core.comm.api.CoreEventListener;
import com.endercore.core.comm.api.CoreMessageClient;
import com.endercore.core.comm.api.CoreStateMonitor;
import com.endercore.core.comm.config.CoreWebSocketConfig;
import com.endercore.core.comm.exception.CoreClosedException;
import com.endercore.core.comm.exception.CoreConnectException;
import com.endercore.core.comm.exception.CoreProtocolException;
import com.endercore.core.comm.exception.CoreRemoteException;
import com.endercore.core.comm.exception.CoreTimeoutException;
import com.endercore.core.comm.monitor.ConnectionMetrics;
import com.endercore.core.comm.monitor.ConnectionMetricsSnapshot;
import com.endercore.core.comm.monitor.ConnectionState;
import com.endercore.core.comm.protocol.CoreFrame;
import com.endercore.core.comm.protocol.CoreFrameCodec;
import com.endercore.core.comm.protocol.CoreKinds;
import com.endercore.core.comm.protocol.CoreMessageType;
import com.endercore.core.comm.protocol.CoreResponse;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

 
/**
 * 核心 WebSocket 客户端实现。
 * 负责管理 WebSocket 连接、发送请求、处理响应和事件、以及自动重连。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public final class CoreWebSocketClient implements CoreConnectionManager, CoreMessageClient, CoreStateMonitor {
    private final Object lifecycleLock = new Object();
    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.CLOSED);
    private final CopyOnWriteArrayList<BiConsumer<ConnectionState, ConnectionState>> stateListeners = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<CoreEventListener>> eventListeners = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<CoreEventListener> anyEventListeners = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<Long, PendingRequest> pending = new ConcurrentHashMap<>();
    private final AtomicLong requestIdSeq = new AtomicLong(1);

    /**
     * 客户端配置
     */
    private final CoreWebSocketConfig config;
    
    /**
     * 异常处理器
     */
    private final CoreExceptionHandler exceptionHandler;
    
    /**
     * 回调执行器
     */
    private final Executor callbackExecutor;
    
    /**
     * 调度执行器
     */
    private final ScheduledExecutorService scheduler;
    
    /**
     * 帧编解码器
     */
    private final CoreFrameCodec codec;
    
    /**
     * 连接指标
     */
    private final ConnectionMetrics metrics = new ConnectionMetrics();

    private volatile URI endpoint;
    private volatile WebSocketClient client;
    private volatile CompletableFuture<Void> connectFuture;
    private volatile boolean closing;
    private volatile Duration dynamicBackoff;

    /**
     * 构造函数。
     *
     * @param config 客户端配置
     * @param exceptionHandler 异常处理器，如果为 null 则使用 NoopExceptionHandler
     * @param callbackExecutor 回调执行器，如果为 null 则使用 CachedThreadPool
     */
    public CoreWebSocketClient(CoreWebSocketConfig config, CoreExceptionHandler exceptionHandler, Executor callbackExecutor) {
        this.config = Objects.requireNonNull(config, "config");
        this.exceptionHandler = exceptionHandler == null ? new NoopExceptionHandler() : exceptionHandler;
        this.callbackExecutor = callbackExecutor == null ? Executors.newCachedThreadPool() : callbackExecutor;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "endercore-core-comm-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.codec = new CoreFrameCodec(config.maxFrameBytes());
        this.dynamicBackoff = config.reconnectBackoffMin();
    }

    @Override
    /**
     * 连接到指定端点。
     *
     * @param endpoint 连接端点 URI
     * @return 连接 Future
     */
    public CompletableFuture<Void> connect(URI endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        synchronized (lifecycleLock) {
            if (state.get() == ConnectionState.CONNECTED) {
                return CompletableFuture.completedFuture(null);
            }
            if (connectFuture != null && !connectFuture.isDone()) {
                return connectFuture;
            }
            this.endpoint = endpoint;
            this.closing = false;
            setState(ConnectionState.CONNECTING);
            this.connectFuture = new CompletableFuture<>();
            this.client = newClient(endpoint);
            try {
                boolean started = this.client.connectBlocking(config.connectTimeout().toMillis(), TimeUnit.MILLISECONDS);
                if (!started) {
                    CoreConnectException e = new CoreConnectException("连接超时: " + endpoint, null);
                    exceptionHandler.onConnectionError(e);
                    setState(ConnectionState.FAILED);
                    connectFuture.completeExceptionally(e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                CoreConnectException ce = new CoreConnectException("连接被中断: " + endpoint, e);
                exceptionHandler.onConnectionError(ce);
                setState(ConnectionState.FAILED);
                connectFuture.completeExceptionally(ce);
            } catch (Exception e) {
                CoreConnectException ce = new CoreConnectException("连接失败: " + endpoint, e);
                exceptionHandler.onConnectionError(ce);
                setState(ConnectionState.FAILED);
                connectFuture.completeExceptionally(ce);
            }
            return connectFuture;
        }
    }

    @Override
    /**
     * 关闭连接。
     *
     * @param timeout 超时时间
     * @return 关闭 Future
     */
    public CompletableFuture<Void> close(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        synchronized (lifecycleLock) {
            closing = true;
            setState(ConnectionState.CLOSING);
            WebSocketClient c = this.client;
            if (c == null) {
                setState(ConnectionState.CLOSED);
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<Void> f = new CompletableFuture<>();
            scheduler.execute(() -> {
                try {
                    c.closeBlocking();
                    failPending(new CoreClosedException("连接已关闭"));
                    setState(ConnectionState.CLOSED);
                    f.complete(null);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failPending(new CoreClosedException("连接关闭被中断"));
                    setState(ConnectionState.CLOSED);
                    f.completeExceptionally(e);
                } catch (Exception e) {
                    failPending(new CoreClosedException("连接关闭失败"));
                    setState(ConnectionState.CLOSED);
                    f.completeExceptionally(e);
                }
            });
            return f.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    @Override
    /**
     * 获取当前连接状态。
     *
     * @return 连接状态
     */
    public ConnectionState state() {
        return state.get();
    }

    @Override
    /**
     * 检查是否已连接。
     *
     * @return 如果已连接则返回 true，否则返回 false
     */
    public boolean isConnected() {
        return state.get() == ConnectionState.CONNECTED;
    }

    @Override
    /**
     * 异步发送请求。
     *
     * @param kind 请求类型
     * @param payload 请求负载
     * @return 响应 Future
     */
    public CompletableFuture<CoreResponse> sendAsync(String kind, byte[] payload) {
        CoreKinds.validate(kind);
        if (!isConnected()) {
            CompletableFuture<CoreResponse> f = new CompletableFuture<>();
            f.completeExceptionally(new CoreClosedException("连接不可用: state=" + state.get()));
            return f;
        }

        long requestId = requestIdSeq.getAndIncrement();
        CoreFrame requestFrame = new CoreFrame(CoreMessageType.REQUEST, (byte) 0, 0, requestId, kind, payload);
        byte[] bytes = codec.encode(requestFrame);

        CompletableFuture<CoreResponse> future = new CompletableFuture<>();
        Duration timeout = config.requestTimeout();
        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            PendingRequest removed = pending.remove(requestId);
            if (removed != null && removed.future.completeExceptionally(new CoreTimeoutException(kind, requestId, timeout))) {
                metrics.onRequestTimeout();
                exceptionHandler.onTimeout(new CoreTimeoutException(kind, requestId, timeout));
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);

        pending.put(requestId, new PendingRequest(kind, System.nanoTime(), future, timeoutTask));

        try {
            metrics.onRequestSent();
            metrics.onFrameSent(bytes.length);
            client.send(bytes);
        } catch (Exception e) {
            PendingRequest removed = pending.remove(requestId);
            if (removed != null) {
                removed.timeoutTask.cancel(false);
            }
            future.completeExceptionally(new CoreConnectException("发送失败: " + kind, e));
        }

        return future;
    }

    @Override
    /**
     * 同步发送请求。
     *
     * @param kind 请求类型
     * @param payload 请求负载
     * @param timeout 超时时间
     * @return 响应对象
     * @throws CoreConnectException 当连接错误或发送失败时抛出
     */
    public CoreResponse sendSync(String kind, byte[] payload, Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        try {
            return sendAsync(kind, payload).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new CoreConnectException("同步请求失败: " + kind, e);
        }
    }

    @Override
    /**
     * 发送事件。
     *
     * @param kind 事件类型
     * @param payload 事件负载
     * @throws CoreClosedException 当连接不可用时抛出
     */
    public void sendEvent(String kind, byte[] payload) {
        CoreKinds.validate(kind);
        if (!isConnected()) {
            throw new CoreClosedException("连接不可用: state=" + state.get());
        }
        CoreFrame frame = new CoreFrame(CoreMessageType.EVENT, (byte) 0, 0, 0, kind, payload);
        byte[] bytes = codec.encode(frame);
        metrics.onFrameSent(bytes.length);
        client.send(bytes);
    }

    @Override
    /**
     * 获取连接指标快照。
     *
     * @return 连接指标快照
     */
    public ConnectionMetricsSnapshot metrics() {
        return metrics.snapshot(pending.size());
    }

    @Override
    /**
     * 注册状态变更监听器。
     *
     * @param listener 状态变更监听器
     */
    public void onStateChanged(BiConsumer<ConnectionState, ConnectionState> listener) {
        stateListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * 注册特定类型的事件监听器。
     *
     * @param kind 事件类型
     * @param listener 事件监听器
     */
    public void onEvent(String kind, CoreEventListener listener) {
        CoreKinds.validate(kind);
        Objects.requireNonNull(listener, "listener");
        eventListeners.computeIfAbsent(kind, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * 注册全局事件监听器。
     *
     * @param listener 事件监听器
     */
    public void onAnyEvent(CoreEventListener listener) {
        anyEventListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * 创建内部 WebSocket 客户端。
     *
     * @param endpoint 连接端点
     * @return WebSocketClient 实例
     */
    private WebSocketClient newClient(URI endpoint) {
        return new WebSocketClient(endpoint) {
            @Override
            /**
             * 当连接打开时调用。
             *
             * @param handshakedata 握手数据
             */
            public void onOpen(ServerHandshake handshakedata) {
                dynamicBackoff = config.reconnectBackoffMin();
                setState(ConnectionState.CONNECTED);
                if (connectFuture != null && !connectFuture.isDone()) {
                    connectFuture.complete(null);
                }
                scheduleHeartbeat();
            }

            @Override
            /**
             * 当接收到文本消息时调用。
             * 本客户端不支持文本帧，将关闭连接。
             *
             * @param message 文本消息
             */
            public void onMessage(String message) {
                metrics.onProtocolError();
                CoreProtocolException e = new CoreProtocolException("不支持文本帧");
                exceptionHandler.onProtocolError(e);
                close(1003, e.getMessage());
            }

            @Override
            /**
             * 当接收到二进制消息时调用。
             *
             * @param bytes 二进制数据
             */
            public void onMessage(ByteBuffer bytes) {
                metrics.onFrameReceived(bytes.remaining());
                CoreFrame frame;
                try {
                    frame = codec.decode(bytes);
                } catch (CoreProtocolException e) {
                    metrics.onProtocolError();
                    exceptionHandler.onProtocolError(e);
                    close(1002, e.getMessage());
                    return;
                } catch (RuntimeException e) {
                    metrics.onProtocolError();
                    exceptionHandler.onProtocolError(e);
                    close(1011, "协议解析失败");
                    return;
                }

                if (frame.type() == CoreMessageType.RESPONSE) {
                    onResponseFrame(frame);
                } else if (frame.type() == CoreMessageType.EVENT) {
                    onEventFrame(frame);
                } else if (frame.type() == CoreMessageType.HEARTBEAT) {
                    onHeartbeatFrame(frame);
                }
            }

            @Override
            /**
             * 当连接关闭时调用。
             *
             * @param code 关闭代码
             * @param reason 关闭原因
             * @param remote 是否由远程关闭
             */
            public void onClose(int code, String reason, boolean remote) {
                if (closing) {
                    setState(ConnectionState.CLOSED);
                } else {
                    setState(ConnectionState.FAILED);
                }
                failPending(new CoreClosedException("连接已关闭: code=" + code + ", reason=" + reason));
                if (!closing) {
                    exceptionHandler.onConnectionError(new CoreConnectException("连接断开: " + reason, null));
                    if (config.autoReconnect()) {
                        scheduleReconnect();
                    }
                }
            }

            @Override
            /**
             * 当发生错误时调用。
             *
             * @param ex 异常对象
             */
            public void onError(Exception ex) {
                exceptionHandler.onConnectionError(new CoreConnectException("连接错误: " + endpoint, ex));
            }
        };
    }

    /**
     * 处理响应帧。
     *
     * @param frame 响应帧
     */
    private void onResponseFrame(CoreFrame frame) {
        metrics.onResponseReceived();
        PendingRequest pendingRequest = pending.remove(frame.requestId());
        if (pendingRequest == null) {
            return;
        }
        pendingRequest.timeoutTask.cancel(false);

        long rttMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - pendingRequest.startNanos);
        metrics.setLastRttMillis(rttMillis);

        if (frame.status() == 0) {
            pendingRequest.future.complete(new CoreResponse(frame.status(), frame.requestId(), frame.kind(), frame.payload()));
        } else {
            String msg = new String(frame.payload(), StandardCharsets.UTF_8);
            CoreRemoteException e = new CoreRemoteException(frame.status(), frame.kind(), frame.requestId(), msg);
            exceptionHandler.onRemoteError(e);
            pendingRequest.future.completeExceptionally(e);
        }
    }

    /**
     * 处理事件帧。
     *
     * @param frame 事件帧
     */
    private void onEventFrame(CoreFrame frame) {
        CopyOnWriteArrayList<CoreEventListener> specific = eventListeners.get(frame.kind());
        if (specific != null) {
            for (CoreEventListener listener : specific) {
                callbackExecutor.execute(() -> listener.onEvent(frame.kind(), frame.payload()));
            }
        }
        for (CoreEventListener listener : anyEventListeners) {
            callbackExecutor.execute(() -> listener.onEvent(frame.kind(), frame.payload()));
        }
    }

    /**
     * 处理心跳帧。
     *
     * @param frame 心跳帧
     */
    private void onHeartbeatFrame(CoreFrame frame) {
        metrics.setLastRttMillis(0);
    }

    /**
     * 调度重连。
     */
    private void scheduleReconnect() {
        Duration backoff = dynamicBackoff;
        long jitter = ThreadLocalRandom.current().nextLong(0, 100);
        scheduler.schedule(() -> {
            if (closing) {
                return;
            }
            try {
                connect(endpoint);
            } catch (Exception e) {
                exceptionHandler.onConnectionError(new CoreConnectException("重连失败", e));
            }
            dynamicBackoff = nextBackoff(dynamicBackoff);
        }, backoff.toMillis() + jitter, TimeUnit.MILLISECONDS);
    }

    /**
     * 计算下一次重连退避时间。
     *
     * @param current 当前退避时间
     * @return 下一次退避时间
     */
    private Duration nextBackoff(Duration current) {
        long next = Math.min(current.toMillis() * 2L, config.reconnectBackoffMax().toMillis());
        return Duration.ofMillis(next);
    }

    /**
     * 调度心跳发送。
     */
    private void scheduleHeartbeat() {
        Duration interval = config.heartbeatInterval();
        if (interval == null || interval.isZero() || interval.isNegative()) {
            return;
        }
        scheduler.scheduleAtFixedRate(() -> {
            if (!isConnected()) {
                return;
            }
            try {
                byte[] bytes = codec.encode(new CoreFrame(CoreMessageType.HEARTBEAT, (byte) 0, 0, 0, "", new byte[0]));
                metrics.onFrameSent(bytes.length);
                client.send(bytes);
            } catch (Exception e) {
                exceptionHandler.onConnectionError(new CoreConnectException("心跳发送失败", e));
            }
        }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * 设置连接状态。
     *
     * @param newState 新状态
     */
    private void setState(ConnectionState newState) {
        ConnectionState old = state.getAndSet(newState);
        if (old == newState) {
            return;
        }
        for (BiConsumer<ConnectionState, ConnectionState> l : stateListeners) {
            callbackExecutor.execute(() -> l.accept(old, newState));
        }
    }

    /**
     * 使所有挂起的请求失败。
     *
     * @param error 异常对象
     */
    private void failPending(RuntimeException error) {
        for (PendingRequest pr : pending.values()) {
            pr.timeoutTask.cancel(false);
            pr.future.completeExceptionally(error);
        }
        pending.clear();
    }

    /**
     * 挂起的请求信息。
     */
    private static final class PendingRequest {
        private final String kind;
        private final long startNanos;
        private final CompletableFuture<CoreResponse> future;
        private final ScheduledFuture<?> timeoutTask;

        /**
         * 构造函数。
         *
         * @param kind 请求类型
         * @param startNanos 开始时间（纳秒）
         * @param future 响应 Future
         * @param timeoutTask 超时任务
         */
        private PendingRequest(String kind, long startNanos, CompletableFuture<CoreResponse> future, ScheduledFuture<?> timeoutTask) {
            this.kind = kind;
            this.startNanos = startNanos;
            this.future = future;
            this.timeoutTask = timeoutTask;
        }
    }

    /**
     * 空操作异常处理器。
     */
    private static final class NoopExceptionHandler implements CoreExceptionHandler {
        @Override
        /**
         * 处理连接错误。
         *
         * @param error 异常对象
         */
        public void onConnectionError(Throwable error) {
        }

        @Override
        /**
         * 处理协议错误。
         *
         * @param error 异常对象
         */
        public void onProtocolError(Throwable error) {
        }

        @Override
        /**
         * 处理远程错误。
         *
         * @param error 异常对象
         */
        public void onRemoteError(Throwable error) {
        }

        @Override
        /**
         * 处理超时错误。
         *
         * @param error 异常对象
         */
        public void onTimeout(Throwable error) {
        }
    }
}
