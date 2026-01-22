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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = "ender_online", value = Dist.CLIENT)
/**
 * 局域网分享屏幕处理器。
 * <p>
 * 该类负责拦截并修改 Minecraft 的“对局域网开放”屏幕（ShareToLanScreen）。
 * 它添加了一个“末影联机”开关，并劫持“开始局域网世界”按钮，
 * 以便在开启局域网世界的同时启动末影联机房间托管。
 * </p>
 */
public class LanShareHandler {
    /** 是否开启末影联机功能的标志位 */
    private static boolean enableEnder = false;
    private static final Gson GSON = new Gson();
    /** 用于后台轮询房间状态的调度线程池 */
    private static final ScheduledExecutorService ROOM_POLL_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "Ender-Room-Poll");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 当屏幕初始化完成后调用。
     * <p>
     * 此方法会在 {@link ShareToLanScreen} 初始化后触发。
     * 它执行以下操作：
     * 1. 添加一个切换“末影联机”开启状态的按钮。
     * 2. 查找原版的“开始局域网世界”按钮，将其隐藏。
     * 3. 添加一个新的“开始局域网世界”按钮，点击时会先调用原版逻辑，
     *    如果开启了“末影联机”，则进一步尝试启动远程托管。
     * </p>
     *
     * @param event 屏幕初始化后期事件
     */
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

    /**
     * 启动末影联机托管服务。
     * <p>
     * 该方法调用 {@link EnderApiClient#startHosting} 向后端申请房间号。
     * 成功后会在聊天栏显示房间号通知。
     * </p>
     *
     * @param port 本地局域网世界监听的端口号
     */
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
                    ClientSetup.handleRoomCodeNotification(roomCode);
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



