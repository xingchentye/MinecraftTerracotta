package com.multiplayer.ender.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.multiplayer.ender.client.gui.StartupScreen;
import com.multiplayer.ender.network.EnderApiClient;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "ender_online", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LanShareHandlerForge {
    private static boolean enableEnder = false;
    private static final Gson GSON = new Gson();
    private static final ScheduledExecutorService ROOM_POLL_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "Ender-Room-Poll");
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

            Button toggleBtn = Button.builder(Component.literal("末影联机: " + (enableEnder ? "开" : "关")), button -> {
                enableEnder = !enableEnder;
                button.setMessage(Component.literal("末影联机: " + (enableEnder ? "开" : "关")));
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
                    if (enableEnder && mc.getSingleplayerServer() != null && mc.getSingleplayerServer().isPublished()) {
                        int port = mc.getSingleplayerServer().getPort();
                        
                        boolean allowCheats = false;
                        try {
                            Object playerList = mc.getSingleplayerServer().getPlayerList();
                            java.lang.reflect.Method m = playerList.getClass().getMethod("isAllowCheatsForAllPlayers");
                            allowCheats = (boolean) m.invoke(playerList);
                        } catch (Exception e) {
                            try {
                                Object playerList = mc.getSingleplayerServer().getPlayerList();
                                java.lang.reflect.Method m = playerList.getClass().getMethod("isAllowCommandsForAllPlayers");
                                allowCheats = (boolean) m.invoke(playerList);
                            } catch (Exception ignored) {}
                        }
                        
                        net.minecraft.world.level.GameType gameMode = mc.getSingleplayerServer().getDefaultGameType();
                        String visitorPermission = "可交互";
                        if (gameMode == net.minecraft.world.level.GameType.SPECTATOR) {
                            visitorPermission = "仅观战";
                        } else if (gameMode == net.minecraft.world.level.GameType.ADVENTURE) {
                            // visitorPermission = "仅聊天"; // Optional: Map Adventure to Chat Only if desired, or keep Interactive
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
                })
                .bounds(originalBtn.getX(), originalBtn.getY(), originalBtn.getWidth(), originalBtn.getHeight())
                .build();

                event.addListener(newStartBtn);
            }
        }
    }

    private static void startEnderHosting(int port) {
        Minecraft mc = Minecraft.getInstance();

        if (!EnderApiClient.hasDynamicPort()) {
            mc.gui.getChat().addMessage(Component.literal("[Ender Core] 错误：末影联机服务未启动或未连接。请先在多人游戏菜单中启动末影联机。").withStyle(ChatFormatting.RED));
            return;
        }

        String playerName = mc.getUser().getName();

        mc.gui.getChat().addMessage(Component.literal("[Ender Core] 正在尝试建立末影联机连接...").withStyle(ChatFormatting.GRAY));

        EnderApiClient.startHosting(port, playerName).thenAccept(roomCode -> {
            if (roomCode != null && !roomCode.isEmpty()) {
                Minecraft.getInstance().execute(() -> {
                    ClientSetupForge.handleRoomCodeNotification(roomCode);
                });
            } else {
                mc.gui.getChat().addMessage(Component.literal("[Ender Core] 启动失败: 未能生成房间号").withStyle(ChatFormatting.RED));
            }
        }).exceptionally(e -> {
             mc.gui.getChat().addMessage(Component.literal("[Ender Core] 启动失败，发生通信错误: " + e.getMessage()).withStyle(ChatFormatting.RED));
             return null;
        });
    }
}


