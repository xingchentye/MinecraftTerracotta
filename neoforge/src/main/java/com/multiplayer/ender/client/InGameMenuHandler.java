package com.multiplayer.ender.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.multiplayer.ender.client.gui.EnderDashboard;
import com.multiplayer.ender.client.gui.StartupScreen;
import com.multiplayer.ender.network.EnderApiClient;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = "ender_online", value = Dist.CLIENT)
/**
 * 游戏内菜单（暂停界面）处理器。
 * <p>
 * 该类负责在 Minecraft 暂停界面（Esc 菜单）中注入自定义按钮和信息显示。
 * 功能包括：
 * 1. 显示/隐藏“末影联机”房间信息悬浮窗。
 * 2. 提供“房间设置”入口（房主）或“房间信息”入口（访客）。
 * 3. 在单人游戏且已开放局域网时，提供快速“创建房间”按钮。
 * </p>
 */
public class InGameMenuHandler {
    private static final Gson GSON = new Gson();
    private static boolean showInfoOverlay = false;
    private static JsonObject lastState = null;
    private static long lastCreateClickTime = 0;

    /**
     * 屏幕初始化事件回调。
     * 当暂停界面打开时调用，注入自定义按钮和状态组件。
     *
     * @param event 屏幕初始化事件
     */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof PauseScreen screen) {
            int width = screen.width;
            int height = screen.height;
            int buttonWidth = 100;
            int x = width - buttonWidth - 5;
            int y = 5;

            // 预先创建按钮，初始可见性由 RoomStateWidget 接管
            Button infoBtn = Button.builder(Component.literal(showInfoOverlay ? "隐藏信息" : "显示信息"), button -> {
                showInfoOverlay = !showInfoOverlay;
                button.setMessage(Component.literal(showInfoOverlay ? "隐藏信息" : "显示信息"));
            }).bounds(x, y, buttonWidth, 20).build();
            infoBtn.visible = false;
            event.addListener(infoBtn);

            Button settingsBtn = Button.builder(Component.literal("房间设置"), button -> {
                if ("房间信息".equals(button.getMessage().getString())) {
                    Minecraft.getInstance().setScreen(new EnderDashboard(screen, EnderDashboard.ViewMode.INGAME_INFO));
                } else {
                    Minecraft.getInstance().setScreen(new EnderDashboard(screen, EnderDashboard.ViewMode.INGAME_SETTINGS));
                }
            }).bounds(x, y + 24, buttonWidth, 20).build();
            settingsBtn.visible = false;
            event.addListener(settingsBtn);

            Button createRoomBtn = Button.builder(Component.literal("创建房间"), button -> {
                long now = System.currentTimeMillis();
                if (now - lastCreateClickTime < 300) {
                    return;
                }
                lastCreateClickTime = now;
                Minecraft mc = Minecraft.getInstance();
                if (!EnderApiClient.hasDynamicPort()) {
                    mc.setScreen(new StartupScreen(screen, () -> {
                        mc.setScreen(screen);
                        String playerName = mc.getUser().getName();
                        // 确保使用正确端口
                        int port = 25565;
                        if (mc.getSingleplayerServer() != null) {
                            port = mc.getSingleplayerServer().getPort();
                        }
                        EnderApiClient.startHosting(port, playerName);
                    }));
                } else {
                    button.active = false;
                    CompletableFuture
                        .supplyAsync(() -> EnderApiClient.checkHealth().join())
                        .thenAccept(healthy -> {
                            if (!healthy) {
                                EnderApiClient.clearDynamicPort();
                                mc.execute(() -> {
                                    button.setMessage(Component.literal("创建房间"));
                                    button.active = true;
                                    mc.setScreen(new StartupScreen(screen, () -> {
                                        mc.setScreen(screen);
                                        String playerName = mc.getUser().getName();
                                        int port = 25565;
                                        if (mc.getSingleplayerServer() != null) {
                                            port = mc.getSingleplayerServer().getPort();
                                        }
                                        EnderApiClient.startHosting(port, playerName);
                                    }));
                                });
                            } else {
                                String playerName = mc.getUser().getName();
                                int port = 25565;
                                if (mc.getSingleplayerServer() != null) {
                                    port = mc.getSingleplayerServer().getPort();
                                }
                                EnderApiClient.startHosting(port, playerName);
                                mc.execute(() -> {
                                    button.setMessage(Component.literal("请求中..."));
                                    button.active = false;
                                });
                            }
                        });
                }
            }).bounds(x, y, buttonWidth, 20).build();
            createRoomBtn.visible = false;
            event.addListener(createRoomBtn);

            // 立即触发一次状态更新，避免按钮闪烁或消失
            RoomStateWidget stateWidget = new RoomStateWidget(0, 0, width, height, infoBtn, settingsBtn, createRoomBtn, Minecraft.getInstance().font);
            event.addListener(stateWidget);
            stateWidget.forceUpdate(); // 新增：强制立即更新状态
        }
    }

    /**
     * 内部组件：房间状态挂件。
     * 负责定期轮询后端状态，控制按钮显隐，并渲染信息悬浮窗。
     * 该组件不可见（透明），但利用 render 方法进行逻辑更新和覆盖绘制。
     */
    private static class RoomStateWidget extends AbstractWidget {
        private final Button infoBtn;
        private final Button settingsBtn;
        private final Button createRoomBtn;
        private final Font font;
        private long lastCheck = 0;

        public RoomStateWidget(int x, int y, int w, int h, Button infoBtn, Button settingsBtn, Button createRoomBtn, Font font) {
            super(x, y, w, h, Component.empty());
            this.infoBtn = infoBtn;
            this.settingsBtn = settingsBtn;
            this.createRoomBtn = createRoomBtn;
            this.font = font;
            this.lastCheck = System.currentTimeMillis() - 2000; 
        }

        /**
         * 强制立即执行一次状态更新。
         */
        public void forceUpdate() {
            this.lastCheck = System.currentTimeMillis();
            updateState();
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            long now = System.currentTimeMillis();
            if (now - lastCheck > 1000) {
                lastCheck = now;
                updateState();
            }

            if (showInfoOverlay && lastState != null && (infoBtn.visible || settingsBtn.visible)) {
                renderInfoOverlay(guiGraphics);
            }
        }

        /**
         * 渲染房间信息悬浮窗。
         * 显示当前房间号、在线成员列表等信息。
         *
         * @param guiGraphics GUI 绘图上下文
         */
        private void renderInfoOverlay(GuiGraphics guiGraphics) {
            int startX = 10;
            int startY = 10;
            int lineHeight = 10;
            int padding = 5;
            
            int contentHeight = lineHeight * 3;
            if (lastState.has("profiles")) {
                contentHeight += lastState.getAsJsonArray("profiles").size() * lineHeight;
            }
            int boxWidth = 180;
            
            guiGraphics.fill(startX - padding, startY - padding, startX + boxWidth + padding, startY + contentHeight + padding, 0x40000000);
            
            int currentY = startY;
            
            guiGraphics.drawString(font, Component.literal(" 房间信息 ").withStyle(ChatFormatting.BOLD), startX, currentY, 0xFFFFFF);
            currentY += lineHeight;
            
            String roomCode = lastState.has("room") ? lastState.get("room").getAsString() : EnderApiClient.getLastRoomCode();
            if (roomCode == null || roomCode.isEmpty()) {
                roomCode = "未知";
            }
            guiGraphics.drawString(font, "房间号: " + roomCode, startX, currentY, 0xFFFF55);
            currentY += lineHeight;
            
            guiGraphics.drawString(font, Component.literal("在线成员:").withStyle(ChatFormatting.UNDERLINE), startX, currentY, 0xAAAAAA);
            currentY += lineHeight;
            
            if (lastState.has("profiles")) {
                JsonArray profiles = lastState.getAsJsonArray("profiles");
                for (JsonElement p : profiles) {
                    JsonObject profile = p.getAsJsonObject();
                    String name = profile.get("name").getAsString();
                    String kind = profile.has("kind") ? profile.get("kind").getAsString() : "";
                    String display = name;
                    if ("HOST".equals(kind)) {
                        display += " [房主]";
                    }
                    guiGraphics.drawString(font, display, startX + 5, currentY, 0xFFFFFF);
                    currentY += lineHeight;
                }
            }
        }

        /**
         * 更新当前状态。
         * 异步请求后端状态，并根据返回结果更新 UI 按钮的可见性和文本。
         */
        private void updateState() {
            EnderApiClient.getState().whenComplete((stateJson, ex) -> {
                if (ex != null || stateJson == null) {
                    EnderApiClient.checkHealth().thenAccept(healthy -> {
                        if (!healthy) {
                            EnderApiClient.clearDynamicPort();
                        }
                        Minecraft.getInstance().execute(() -> {
                            infoBtn.visible = false;
                            settingsBtn.visible = false;
                            
                            if (Minecraft.getInstance().getSingleplayerServer() != null 
                                && Minecraft.getInstance().getSingleplayerServer().isPublished()) {
                                createRoomBtn.visible = true;
                                createRoomBtn.setMessage(Component.literal("创建房间"));
                                createRoomBtn.active = true;
                            } else {
                                createRoomBtn.visible = false;
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
                        
                        Minecraft.getInstance().execute(() -> {
                            if (isConnected) {
                                infoBtn.visible = true;
                                settingsBtn.visible = true;
                                if ("guest-ok".equals(state)) {
                                    settingsBtn.setMessage(Component.literal("房间信息"));
                                } else {
                                    settingsBtn.setMessage(Component.literal("房间设置"));
                                }
                                createRoomBtn.visible = false;
                            } else {
                                infoBtn.visible = false;
                                settingsBtn.visible = false;

                                if (Minecraft.getInstance().getSingleplayerServer() != null 
                                    && Minecraft.getInstance().getSingleplayerServer().isPublished()) {
                                    createRoomBtn.visible = true;
                                    if ("scanning".equals(state)) {
                                         createRoomBtn.setMessage(Component.literal("请求中..."));
                                         createRoomBtn.active = false;
                                    } else {
                                         createRoomBtn.setMessage(Component.literal("创建房间"));
                                         createRoomBtn.active = true;
                                    }
                                } else {
                                    createRoomBtn.visible = false;
                                }
                            }
                        });
                    } else if (json.has("status") && "IDLE".equals(json.get("status").getAsString())) {
                        lastState = null;
                        Minecraft.getInstance().execute(() -> {
                            infoBtn.visible = false;
                            settingsBtn.visible = false;
                            if (Minecraft.getInstance().getSingleplayerServer() != null
                                && Minecraft.getInstance().getSingleplayerServer().isPublished()) {
                                createRoomBtn.visible = true;
                                createRoomBtn.setMessage(Component.literal("创建房间"));
                                createRoomBtn.active = true;
                            } else {
                                createRoomBtn.visible = false;
                            }
                        });
                    }
                } catch (Exception ignored) {}
            });
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        }
    }
}

