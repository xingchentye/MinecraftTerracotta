package com.multiplayer.ender.fabric.mixin.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.multiplayer.ender.client.gui.EnderDashboard;
import com.multiplayer.ender.client.gui.StartupScreen;
import com.multiplayer.ender.network.EnderApiClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.concurrent.CompletableFuture;

/**
 * 游戏菜单屏幕 Mixin（Fabric）。
 * 在 GameMenuScreen（ESC 菜单）添加房间管理相关按钮。
 */
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    private static final Gson GSON = new Gson();
    private static boolean showInfoOverlay = false;
    private static JsonObject lastState = null;
    private static long lastCreateClickTime = 0;

    private ButtonWidget infoBtn;
    private ButtonWidget settingsBtn;
    private ButtonWidget createRoomBtn;
    private long lastCheck = 0;

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    /**
     * 屏幕初始化后注入。
     * 添加“显示信息”、“房间设置”和“创建房间”按钮。
     *
     * @param ci 回调信息
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        int width = this.width;
        int buttonWidth = 100;
        int x = width - buttonWidth - 5;
        int y = 5;

        infoBtn = ButtonWidget.builder(Text.literal(showInfoOverlay ? "隐藏信息" : "显示信息"), button -> {
            showInfoOverlay = !showInfoOverlay;
            button.setMessage(Text.literal(showInfoOverlay ? "隐藏信息" : "显示信息"));
        }).dimensions(x, y, buttonWidth, 20).build();
        infoBtn.visible = false;
        this.addDrawableChild(infoBtn);

        settingsBtn = ButtonWidget.builder(Text.literal("房间设置"), button -> {
            if ("房间信息".contentEquals(button.getMessage().getString())) {
                MinecraftClient.getInstance().setScreen(new EnderDashboard(this, EnderDashboard.ViewMode.INGAME_INFO));
            } else {
                MinecraftClient.getInstance().setScreen(new EnderDashboard(this, EnderDashboard.ViewMode.INGAME_SETTINGS));
            }
        }).dimensions(x, y + 24, buttonWidth, 20).build();
        settingsBtn.visible = false;
        this.addDrawableChild(settingsBtn);

        createRoomBtn = ButtonWidget.builder(Text.literal("创建房间"), button -> {
            long now = System.currentTimeMillis();
            if (now - lastCreateClickTime < 300) {
                return;
            }
            lastCreateClickTime = now;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!EnderApiClient.hasDynamicPort()) {
                mc.setScreen(new StartupScreen(this, () -> {
                    mc.setScreen(this);
                    String playerName = mc.getSession() != null ? mc.getSession().getUsername() : "Player";
                    EnderApiClient.startHosting(0, playerName);
                }));
            } else {
                button.active = false;
                CompletableFuture
                    .supplyAsync(() -> EnderApiClient.checkHealth().join())
                    .thenAccept(healthy -> {
                        if (!healthy) {
                            EnderApiClient.clearDynamicPort();
                            mc.execute(() -> {
                                button.setMessage(Text.literal("创建房间"));
                                button.active = true;
                                mc.setScreen(new StartupScreen(this, () -> {
                                    mc.setScreen(this);
                                    String playerName = mc.getSession() != null ? mc.getSession().getUsername() : "Player";
                                    EnderApiClient.startHosting(0, playerName);
                                }));
                            });
                        } else {
                            String playerName = mc.getSession() != null ? mc.getSession().getUsername() : "Player";
                            EnderApiClient.startHosting(0, playerName);
                            mc.execute(() -> {
                                button.setMessage(Text.literal("请求中..."));
                                button.active = false;
                            });
                        }
                    });
            }
        }).dimensions(x, y, buttonWidth, 20).build();
        createRoomBtn.visible = false;
        this.addDrawableChild(createRoomBtn);

        this.lastCheck = System.currentTimeMillis() - 2000;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        long now = System.currentTimeMillis();
        if (now - lastCheck > 1000) {
            lastCheck = now;
            updateState();
        }

        if (showInfoOverlay && lastState != null && (infoBtn != null && infoBtn.visible || settingsBtn != null && settingsBtn.visible)) {
            renderInfoOverlay(context);
        }
    }

    private void renderInfoOverlay(DrawContext context) {
        int startX = 10;
        int startY = 10;
        int lineHeight = 10;
        int padding = 5;

        int contentHeight = lineHeight * 3;
        if (lastState.has("profiles")) {
            contentHeight += lastState.getAsJsonArray("profiles").size() * lineHeight;
        }
        int boxWidth = 180;

        context.fill(startX - padding, startY - padding, startX + boxWidth + padding, startY + contentHeight + padding, 0x40000000);

        int currentY = startY;

        context.drawText(this.textRenderer, Text.literal(" 房间信息 ").formatted(Formatting.BOLD), startX, currentY, 0xFFFFFF, false);
        currentY += lineHeight;

        String roomCode = lastState.has("room") ? lastState.get("room").getAsString() : EnderApiClient.getLastRoomCode();
        if (roomCode == null || roomCode.isEmpty()) {
            roomCode = "未知";
        }
        context.drawText(this.textRenderer, "房间号: " + roomCode, startX, currentY, 0xFFFF55, false);
        currentY += lineHeight;

        context.drawText(this.textRenderer, Text.literal("在线成员:").formatted(Formatting.UNDERLINE), startX, currentY, 0xAAAAAA, false);
        currentY += lineHeight;

        if (lastState.has("profiles")) {
            JsonArray profiles = lastState.getAsJsonArray("profiles");
            for (int i = 0; i < profiles.size(); i++) {
                JsonObject profile = profiles.get(i).getAsJsonObject();
                String name = profile.get("name").getAsString();
                String kind = profile.has("kind") ? profile.get("kind").getAsString() : "";
                String display = name;
                if ("HOST".equals(kind)) {
                    display += " [房主]";
                }
                context.drawText(this.textRenderer, display, startX + 5, currentY, 0xFFFFFF, false);
                currentY += lineHeight;
            }
        }
    }

    private void updateState() {
        EnderApiClient.getState().whenComplete((stateJson, ex) -> {
            if (ex != null || stateJson == null) {
                EnderApiClient.checkHealth().thenAccept(healthy -> {
                    if (!healthy) {
                        EnderApiClient.clearDynamicPort();
                    }
                    MinecraftClient.getInstance().execute(() -> {
                        if (infoBtn != null) {
                            infoBtn.visible = false;
                        }
                        if (settingsBtn != null) {
                            settingsBtn.visible = false;
                        }
                        MinecraftClient mc = MinecraftClient.getInstance();
                        boolean canHost = mc.world != null && mc.getServer() != null && mc.getServer().isRemote();
                        if (createRoomBtn != null) {
                            if (canHost) {
                                createRoomBtn.visible = true;
                                createRoomBtn.setMessage(Text.literal("创建房间"));
                                createRoomBtn.active = true;
                            } else {
                                createRoomBtn.visible = false;
                            }
                        }
                    });
                });
                return;
            }

            try {
                JsonObject json = GSON.fromJson(stateJson, JsonObject.class);
                lastState = json;

                if (json.has("state")) {
                    String state = json.get("state").getAsString();
                    boolean isConnected = "host-ok".equals(state) || "guest-ok".equals(state);

                    MinecraftClient.getInstance().execute(() -> {
                        if (isConnected) {
                            if (infoBtn != null) {
                                infoBtn.visible = true;
                            }
                            if (settingsBtn != null) {
                                settingsBtn.visible = true;
                                if ("guest-ok".equals(state)) {
                                    settingsBtn.setMessage(Text.literal("房间信息"));
                                } else {
                                    settingsBtn.setMessage(Text.literal("房间设置"));
                                }
                            }
                            if (createRoomBtn != null) {
                                createRoomBtn.visible = false;
                            }
                        } else {
                            
                            if (infoBtn != null) {
                                infoBtn.visible = false;
                            }
                            if (settingsBtn != null) {
                                settingsBtn.visible = false;
                            }
                            
                            MinecraftClient mc = MinecraftClient.getInstance();
                            boolean canHost = mc.world != null && mc.getServer() != null && !mc.getServer().isRemote(); 
                            
                            if (createRoomBtn != null) {
                                if (canHost) {
                                    createRoomBtn.visible = true;
                                    createRoomBtn.setMessage(Text.literal("创建房间"));
                                    createRoomBtn.active = true;
                                } else {
                                    createRoomBtn.visible = false;
                                }
                            }
                        }
                    });
                }
            } catch (Exception ignored) {
            }
        });
    }
}

