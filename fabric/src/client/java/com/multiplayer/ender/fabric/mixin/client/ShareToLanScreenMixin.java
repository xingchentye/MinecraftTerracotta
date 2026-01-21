package com.multiplayer.ender.fabric.mixin.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.multiplayer.ender.client.gui.StartupScreen;
import com.multiplayer.ender.fabric.MinecraftEnderClient;
import com.multiplayer.ender.network.EnderApiClient;
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
    private static boolean enableEnder = false;
    private static final Gson GSON = new Gson();
    private static final ScheduledExecutorService ROOM_POLL_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "Ender-Room-Poll");
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

        ButtonWidget toggleBtn = ButtonWidget.builder(Text.literal("末影联机: " + (enableEnder ? "开" : "关")), button -> {
            enableEnder = !enableEnder;
            button.setMessage(Text.literal("末影联机: " + (enableEnder ? "开" : "关")));
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
                if (enableEnder && mc.getServer() != null) {
                    int port = mc.getServer().getServerPort();
                    
                    boolean allowCheats = false;
                    try {
                        Object playerManager = mc.getServer().getPlayerManager();
                        java.lang.reflect.Method m = playerManager.getClass().getMethod("areCheatsAllowed");
                        allowCheats = (boolean) m.invoke(playerManager);
                    } catch (Exception e) {
                        try {
                            Object playerManager = mc.getServer().getPlayerManager();
                            java.lang.reflect.Method m = playerManager.getClass().getMethod("areCommandsAllowed");
                            allowCheats = (boolean) m.invoke(playerManager);
                        } catch (Exception ignored) {}
                    }
                    
                    net.minecraft.world.GameMode gameMode = mc.getServer().getDefaultGameMode();
                    String visitorPermission = "可交互";
                    if (gameMode == net.minecraft.world.GameMode.SPECTATOR) {
                        visitorPermission = "仅观战";
                    }
                    
                    EnderApiClient.setLocalSettings(allowCheats, visitorPermission);
                    
                    if (EnderApiClient.hasDynamicPort()) {
                        startEnderHosting(port);
                    } else {
                        mc.setScreen(new StartupScreen(null, () -> {
                            mc.setScreen(null);
                            startEnderHosting(port);
                        }));
                    }
                }
            }).dimensions(originalStart.getX(), originalStart.getY(), originalStart.getWidth(), originalStart.getHeight()).build();

            this.addDrawableChild(newStartBtn);
        }
    }

    private static void startEnderHosting(int port) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!EnderApiClient.hasDynamicPort()) {
            mc.inGameHud.getChatHud().addMessage(Text.literal("[Ender Core] 错误：末影联机服务未启动或未连接。请先在多人游戏菜单中启动末影联机。").formatted(Formatting.RED));
            return;
        }

        String playerName = mc.getSession() != null ? mc.getSession().getUsername() : "Player";

        mc.inGameHud.getChatHud().addMessage(Text.literal("[Ender Core] 正在尝试建立末影联机连接...").formatted(Formatting.GRAY));

        EnderApiClient.startHosting(port, playerName).thenAccept(roomCode -> {
            if (roomCode != null && !roomCode.isEmpty()) {
                MinecraftClient.getInstance().execute(() -> {
                    MinecraftEnderClient.handleRoomCodeNotification(roomCode);
                });
            } else {
                mc.inGameHud.getChatHud().addMessage(Text.literal("[Ender Core] 启动失败: 未能生成房间号").formatted(Formatting.RED));
            }
        }).exceptionally(e -> {
            mc.inGameHud.getChatHud().addMessage(Text.literal("[Ender Core] 启动失败，发生通信错误: " + e.getMessage()).formatted(Formatting.RED));
            return null;
        });
    }
}


