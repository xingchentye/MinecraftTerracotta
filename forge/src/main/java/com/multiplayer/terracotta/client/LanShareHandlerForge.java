package com.multiplayer.terracotta.client;

import java.util.concurrent.CompletableFuture;

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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "minecraftterracotta", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LanShareHandlerForge {
    private static boolean enableTerracotta = false;
    private static final Gson GSON = new Gson();

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
        CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < 20; i++) {
                    String stateJson = TerracottaApiClient.getState().join();
                    if (stateJson != null) {
                        try {
                            JsonObject json = GSON.fromJson(stateJson, JsonObject.class);
                            if (json.has("state") && "host-ok".equals(json.get("state").getAsString())) {
                                if (json.has("room")) {
                                    String roomCode = json.get("room").getAsString();
                                    ClientSetupForge.handleRoomCodeNotification(roomCode);
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
}

