package com.multiplayer.terracotta.fabric.mixin.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.multiplayer.terracotta.client.gui.StartupScreen;
import com.multiplayer.terracotta.fabric.MinecraftTerracottaClient;
import com.multiplayer.terracotta.network.TerracottaApiClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.OpenToLanScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Mixin(OpenToLanScreen.class)
public abstract class ShareToLanScreenMixin extends Screen {
    private static boolean enableTerracotta = false;
    private static final Gson GSON = new Gson();
    private static final ScheduledExecutorService ROOM_POLL_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "Terracotta-Room-Poll");
        thread.setDaemon(true);
        return thread;
    });

    protected ShareToLanScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        int width = this.width;
        int buttonWidth = 120;
        int x = width - buttonWidth - 5;
        int y = 5;

        ButtonWidget toggleBtn = ButtonWidget.builder(Text.literal("陶瓦联机: " + (enableTerracotta ? "开" : "关")), button -> {
            enableTerracotta = !enableTerracotta;
            button.setMessage(Text.literal("陶瓦联机: " + (enableTerracotta ? "开" : "关")));
        }).dimensions(x, y, buttonWidth, 20).build();
        this.addDrawableChild(toggleBtn);

        ButtonWidget originalStart = null;
        for (ClickableWidget widget : this.children().stream().filter(e -> e instanceof ClickableWidget).map(e -> (ClickableWidget) e).toList()) {
            if (widget instanceof ButtonWidget btn) {
                String label = btn.getMessage().getString();
                if (label.contains("LAN") || label.contains("局域网")) {
                    originalStart = btn;
                    break;
                }
            }
        }

        if (originalStart != null) {
            originalStart.visible = false;
            originalStart.active = false;

            final ButtonWidget startButton = originalStart;

            ButtonWidget newStartBtn = ButtonWidget.builder(startButton.getMessage(), button -> {
                startButton.onPress();

                MinecraftClient mc = MinecraftClient.getInstance();
                if (enableTerracotta && mc.getServer() != null) {
                    int port = mc.getServer().getServerPort();
                    if (TerracottaApiClient.hasDynamicPort()) {
                        startTerracottaHosting(port);
                    } else {
                        mc.setScreen(new StartupScreen(null, () -> {
                            mc.setScreen(null);
                            startTerracottaHosting(port);
                        }));
                    }
                }
            }).dimensions(originalStart.getX(), originalStart.getY(), originalStart.getWidth(), originalStart.getHeight()).build();

            this.addDrawableChild(newStartBtn);
        }
    }

    private static void startTerracottaHosting(int port) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!TerracottaApiClient.hasDynamicPort()) {
            mc.inGameHud.getChatHud().addMessage(Text.literal("[Terracotta] 错误：陶瓦联机服务未启动或未连接。请先在多人游戏菜单中启动陶瓦联机。").formatted(Formatting.RED));
            return;
        }

        String playerName = mc.getSession() != null ? mc.getSession().getUsername() : "Player";

        mc.inGameHud.getChatHud().addMessage(Text.literal("[Terracotta] 正在尝试建立陶瓦联机连接...").formatted(Formatting.GRAY));

        TerracottaApiClient.startHosting(port, playerName).thenAccept(error -> {
            if (error == null) {
                pollForRoomCode();
            } else {
                mc.inGameHud.getChatHud().addMessage(Text.literal("[Terracotta] 启动失败: " + error).formatted(Formatting.RED));
            }
        }).exceptionally(e -> {
            mc.inGameHud.getChatHud().addMessage(Text.literal("[Terracotta] 启动失败，发生通信错误: " + e.getMessage()).formatted(Formatting.RED));
            return null;
        });
    }

    private static void pollForRoomCode() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<ScheduledFuture<?>> scheduledRef = new AtomicReference<>();
        Runnable task = () -> {
            try {
                int attempt = attempts.incrementAndGet();
                String stateJson = TerracottaApiClient.getState().join();
                if (stateJson != null) {
                    try {
                        JsonObject json = GSON.fromJson(stateJson, JsonObject.class);
                        if (json.has("state") && "host-ok".equals(json.get("state").getAsString())) {
                            if (json.has("room")) {
                                String roomCode = json.get("room").getAsString();
                                MinecraftTerracottaClient.handleRoomCodeNotification(roomCode);
                                cancelScheduled(scheduledRef);
                                return;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (attempt >= 20) {
                    cancelScheduled(scheduledRef);
                    MinecraftClient.getInstance().execute(() ->
                            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(
                                    Text.literal("[Terracotta] 房间创建成功，但获取房间号超时。").formatted(Formatting.YELLOW)
                            )
                    );
                }
            } catch (Exception e) {
                cancelScheduled(scheduledRef);
                e.printStackTrace();
            }
        };
        scheduledRef.set(ROOM_POLL_EXECUTOR.scheduleAtFixedRate(task, 0, 500, TimeUnit.MILLISECONDS));
    }

    private static void cancelScheduled(AtomicReference<ScheduledFuture<?>> scheduledRef) {
        ScheduledFuture<?> scheduled = scheduledRef.get();
        if (scheduled != null) {
            scheduled.cancel(false);
        }
    }
}
