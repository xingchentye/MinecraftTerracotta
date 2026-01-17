package com.multiplayer.terracotta.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.multiplayer.terracotta.client.gui.StartupScreen;
import com.multiplayer.terracotta.logic.ProcessLauncher;
import com.multiplayer.terracotta.network.TerracottaApiClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@EventBusSubscriber(modid = "minecraftterracotta", value = Dist.CLIENT)
public class LanShareHandler {
    private static boolean enableTerracotta = false;
    private static final Gson GSON = new Gson();

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof ShareToLanScreen screen) {
            // 添加陶瓦联机开关按钮
            // 位置：右上角
            int width = screen.width;
            int buttonWidth = 120; // 稍微缩小一点
            int x = width - buttonWidth - 5;
            int y = 5; 

            Button toggleBtn = Button.builder(Component.literal("陶瓦联机: " + (enableTerracotta ? "开" : "关")), button -> {
                enableTerracotta = !enableTerracotta;
                button.setMessage(Component.literal("陶瓦联机: " + (enableTerracotta ? "开" : "关")));
            }).width(buttonWidth).bounds(x, y, buttonWidth, 20).build();

            event.addListener(toggleBtn);

            // 查找并劫持 "Start LAN World" 按钮
            Button startBtn = null;
            for (GuiEventListener listener : event.getListenersList()) {
                if (listener instanceof Button btn) {
                    // 通过翻译键或文本判断是否是开始按钮
                    if (btn.getMessage().getString().contains("LAN") || btn.getMessage().getString().contains("局域网")) {
                        startBtn = btn;
                        break;
                    }
                }
            }

            if (startBtn != null) {
                // 隐藏原始按钮
                startBtn.visible = false;
                startBtn.active = false;

                // 创建新按钮替代
                final Button originalBtn = startBtn;
                Button newStartBtn = Button.builder(originalBtn.getMessage(), button -> {
                    // 调用原始逻辑
                    originalBtn.onPress();

                    // 检查是否开启了陶瓦联机且局域网已启动
                    Minecraft mc = Minecraft.getInstance();
                    if (enableTerracotta && mc.getSingleplayerServer() != null && mc.getSingleplayerServer().isPublished()) {
                        int port = mc.getSingleplayerServer().getPort();
                        if (ProcessLauncher.isRunning()) {
                            startTerracottaHosting(port);
                        } else {
                            // 如果后端未启动，先启动后端
                            mc.setScreen(new StartupScreen(null, () -> {
                                mc.setScreen(null); // 关闭启动界面返回游戏
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
        
        // 检查后端是否就绪
        if (!TerracottaApiClient.hasDynamicPort()) {
            mc.gui.getChat().addMessage(Component.literal("[Terracotta] 错误：陶瓦联机服务未启动或未连接。请先在多人游戏菜单中启动陶瓦联机。").withStyle(ChatFormatting.RED));
            return;
        }

        String playerName = mc.getUser().getName();

        mc.gui.getChat().addMessage(Component.literal("[Terracotta] 正在尝试建立陶瓦联机连接...").withStyle(ChatFormatting.GRAY));

        TerracottaApiClient.startHosting(port, playerName).thenAccept(error -> {
            if (error == null) {
                // 启动成功，轮询获取房间号
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
        CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < 20; i++) { // 增加尝试次数到 20 次 (10秒)
                    String stateJson = TerracottaApiClient.getState().join();
                    if (stateJson != null) {
                        try {
                            JsonObject json = GSON.fromJson(stateJson, JsonObject.class);
                            if (json.has("state") && "host-ok".equals(json.get("state").getAsString())) {
                                if (json.has("room")) {
                                    String roomCode = json.get("room").getAsString();
                                    // 确保在主线程执行 UI 操作
                                    Minecraft.getInstance().execute(() -> showRoomCodeInChat(roomCode));
                                    return;
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                    Thread.sleep(500);
                }
                
                Minecraft.getInstance().execute(() -> 
                    Minecraft.getInstance().gui.getChat().addMessage(Component.literal("[Terracotta] 房间创建成功，但获取房间号超时。").withStyle(ChatFormatting.YELLOW))
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static void showRoomCodeInChat(String roomCode) {
        Minecraft mc = Minecraft.getInstance();
        
        // 自动复制到剪贴板
        mc.keyboardHandler.setClipboard(roomCode);
        
        MutableComponent msg = Component.literal("[Terracotta] 房间已创建！房间号: ");
        msg.withStyle(ChatFormatting.GREEN);
        
        MutableComponent code = Component.literal(roomCode);
        code.withStyle(style -> style
            .withColor(ChatFormatting.AQUA)
            .withBold(true)
            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, roomCode))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("点击复制")))
        );
        
        msg.append(code);
        
        mc.gui.getChat().addMessage(msg);
        mc.gui.getChat().addMessage(Component.literal("[Terracotta] 房间号已自动复制到剪贴板。").withStyle(ChatFormatting.GRAY));
    }
}
