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
 * Core WebSocket 客户端：提供连接管理、请求/响应与状态观测能力。
 */
public final class CoreWebSocketClient implements CoreConnectionManager, CoreMessageClient, CoreStateMonitor {
    private final Object lifecycleLock = new Object();
    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.CLOSED);
    private final CopyOnWriteArrayList<BiConsumer<ConnectionState, ConnectionState>> stateListeners = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<CoreEventListener>> eventListeners = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<CoreEventListener> anyEventListeners = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<Long, PendingRequest> pending = new ConcurrentHashMap<>();
    private final AtomicLong requestIdSeq = new AtomicLong(1);

    private final CoreWebSocketConfig config;
    private final CoreExceptionHandler exceptionHandler;
    private final Executor callbackExecutor;
    private final ScheduledExecutorService scheduler;
    private final CoreFrameCodec codec;
    private final ConnectionMetrics metrics = new ConnectionMetrics();

    private volatile URI endpoint;
    private volatile WebSocketClient client;
    private volatile CompletableFuture<Void> connectFuture;
    private volatile boolean closing;
    private volatile Duration dynamicBackoff;

    /**
     * 创建 Core WebSocket 客户端。
     *
     * @param config           配置
     * @param exceptionHandler 异常回调（可为 null）
     * @param callbackExecutor 状态回调执行器（可为 null）
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
     * 连接到指定 WebSocket 服务端。
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
     * 关闭连接并回收 pending 请求。
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
     */
    public ConnectionState state() {
        return state.get();
    }

    @Override
    /**
     * 判断当前是否处于已连接状态。
     */
    public boolean isConnected() {
        return state.get() == ConnectionState.CONNECTED;
    }

    @Override
    /**
     * 异步发送请求并返回 future。
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
     * 同步发送请求并等待响应，超时会抛出异常。
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
     * 发送单向事件，不等待响应。
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
     * 获取指标快照。
     */
    public ConnectionMetricsSnapshot metrics() {
        return metrics.snapshot(pending.size());
    }

    @Override
    /**
     * 订阅连接状态变化事件。
     */
    public void onStateChanged(BiConsumer<ConnectionState, ConnectionState> listener) {
        stateListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * 订阅指定 kind 的事件。
     *
     * @param kind     事件 kind（namespace:path）
     * @param listener 监听器
     */
    public void onEvent(String kind, CoreEventListener listener) {
        CoreKinds.validate(kind);
        Objects.requireNonNull(listener, "listener");
        eventListeners.computeIfAbsent(kind, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * 订阅所有事件。
     *
     * @param listener 监听器
     */
    public void onAnyEvent(CoreEventListener listener) {
        anyEventListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * 创建底层 WebSocketClient 并绑定回调。
     */
    private WebSocketClient newClient(URI endpoint) {
        return new WebSocketClient(endpoint) {
            @Override
            /**
             * WebSocket 握手完成。
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
             * 收到文本帧时按协议错误处理并关闭连接。
             */
            public void onMessage(String message) {
                metrics.onProtocolError();
                CoreProtocolException e = new CoreProtocolException("不支持文本帧");
                exceptionHandler.onProtocolError(e);
                close(1003, e.getMessage());
            }

            @Override
            /**
             * 收到二进制帧并进行协议解码与响应分发。
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
             * WebSocket 连接关闭回调。
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
             * WebSocket 底层错误回调。
             */
            public void onError(Exception ex) {
                exceptionHandler.onConnectionError(new CoreConnectException("连接错误: " + endpoint, ex));
            }
        };
    }

    /**
     * 处理响应帧：匹配 pending 请求并完成对应 future。
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
     * 处理事件帧：分发到订阅监听器。
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
     * 处理心跳帧：当前用于标记连接活跃。
     */
    private void onHeartbeatFrame(CoreFrame frame) {
        metrics.setLastRttMillis(0);
    }

    /**
     * 计划一次重连（带退避与抖动）。
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
     */
    private Duration nextBackoff(Duration current) {
        long next = Math.min(current.toMillis() * 2L, config.reconnectBackoffMax().toMillis());
        return Duration.ofMillis(next);
    }

    /**
     * 启动心跳定时任务。
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
     * 切换连接状态并触发订阅回调。
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
     * 将所有 pending 请求以指定异常失败，并清空映射。
     */
    private void failPending(RuntimeException error) {
        for (PendingRequest pr : pending.values()) {
            pr.timeoutTask.cancel(false);
            pr.future.completeExceptionally(error);
        }
        pending.clear();
    }

    private static final class PendingRequest {
        private final String kind;
        private final long startNanos;
        private final CompletableFuture<CoreResponse> future;
        private final ScheduledFuture<?> timeoutTask;

        /**
         * 构建 pending 请求上下文。
         */
        private PendingRequest(String kind, long startNanos, CompletableFuture<CoreResponse> future, ScheduledFuture<?> timeoutTask) {
            this.kind = kind;
            this.startNanos = startNanos;
            this.future = future;
            this.timeoutTask = timeoutTask;
        }
    }

    private static final class NoopExceptionHandler implements CoreExceptionHandler {
        @Override
        /**
         * 忽略连接异常。
         */
        public void onConnectionError(Throwable error) {
        }

        @Override
        /**
         * 忽略协议异常。
         */
        public void onProtocolError(Throwable error) {
        }

        @Override
        /**
         * 忽略远端业务异常。
         */
        public void onRemoteError(Throwable error) {
        }

        @Override
        /**
         * 忽略超时异常。
         */
        public void onTimeout(Throwable error) {
        }
    }
}
