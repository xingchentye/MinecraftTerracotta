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

@Mixin(OpenToLanScreen.class)
public abstract class ShareToLanScreenMixin extends Screen {
    private static boolean enableTerracotta = false;
    private static final Gson GSON = new Gson();

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
                                    MinecraftTerracottaClient.handleRoomCodeNotification(roomCode);
                                    return;
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    Thread.sleep(500);
                }

                MinecraftClient.getInstance().execute(() ->
                        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(
                                Text.literal("[Terracotta] 房间创建成功，但获取房间号超时。").formatted(Formatting.YELLOW)
                        )
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
