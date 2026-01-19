package com.multiplayer.terracotta.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.multiplayer.terracotta.client.gui.StartupScreen;
import com.multiplayer.terracotta.network.TerracottaApiClient;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = "minecraftterracotta", value = Dist.CLIENT)
public class LanShareHandler {
    private static boolean enableTerracotta = false;
    private static final Gson GSON = new Gson();
    private static final ScheduledExecutorService ROOM_POLL_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "Terracotta-Room-Poll");
        thread.setDaemon(true);
        return thread;
    });

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof ShareToLanScreen screen) {
            int width = screen.width;
            int buttonWidth = 120;
            int x = width - buttonWidth - 5;
            int y = 5;

            Button toggleBtn = Button.builder(Component.literal("陶瓦联机: " + (enableTerracotta ? "开" : "关")), button -> {
                enableTerracotta = !enableTerracotta;
                button.setMessage(Component.literal("陶瓦联机: " + (enableTerracotta ? "开" : "关")));
            }).width(buttonWidth).bounds(x, y, buttonWidth, 20).build();

            event.addListener(toggleBtn);

            Button startBtn = null;
            for (GuiEventListener listener : event.getListenersList()) {
                if (listener instanceof Button btn) {
                    if (btn.getMessage().getString().contains("LAN") || btn.getMessage().getString().contains("局域网")) {
                        startBtn = btn;
                        break;
                    }
                }
            }

            if (startBtn != null) {
                startBtn.visible = false;
                startBtn.active = false;

                final Button originalBtn = startBtn;
                Button newStartBtn = Button.builder(originalBtn.getMessage(), button -> {
                    originalBtn.onPress();

                    Minecraft mc = Minecraft.getInstance();
                    if (enableTerracotta && mc.getSingleplayerServer() != null && mc.getSingleplayerServer().isPublished()) {
                        int port = mc.getSingleplayerServer().getPort();
                        if (TerracottaApiClient.hasDynamicPort()) {
                            startTerracottaHosting(port);
                        } else {
                            mc.setScreen(new StartupScreen(null, () -> {
                                mc.setScreen(null);
                                startTerracottaHosting(port);
                            }));
                        }
                    }
                })
                .bounds(originalBtn.getX(), originalBtn.getY(), originalBtn.getWidth(), originalBtn.getHeight())
                .build();

                event.addListener(newStartBtn);
            }
        }
    }

    private static void startTerracottaHosting(int port) {
        Minecraft mc = Minecraft.getInstance();

        if (!TerracottaApiClient.hasDynamicPort()) {
            mc.gui.getChat().addMessage(Component.literal("[Terracotta] 错误：陶瓦联机服务未启动或未连接。请先在多人游戏菜单中启动陶瓦联机。").withStyle(ChatFormatting.RED));
            return;
        }

        String playerName = mc.getUser().getName();

        mc.gui.getChat().addMessage(Component.literal("[Terracotta] 正在尝试建立陶瓦联机连接...").withStyle(ChatFormatting.GRAY));

        TerracottaApiClient.startHosting(port, playerName).thenAccept(error -> {
            if (error == null) {
                pollForRoomCode();
            } else {
                mc.gui.getChat().addMessage(Component.literal("[Terracotta] 启动失败: " + error).withStyle(ChatFormatting.RED));
            }
        }).exceptionally(e -> {
             mc.gui.getChat().addMessage(Component.literal("[Terracotta] 启动失败，发生通信错误: " + e.getMessage()).withStyle(ChatFormatting.RED));
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
                                ClientSetup.handleRoomCodeNotification(roomCode);
                                cancelScheduled(scheduledRef);
                                return;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (attempt >= 20) {
                    cancelScheduled(scheduledRef);
                    Minecraft.getInstance().execute(() ->
                        Minecraft.getInstance().gui.getChat().addMessage(Component.literal("[Terracotta] 房间创建成功，但获取房间号超时。").withStyle(ChatFormatting.YELLOW))
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

