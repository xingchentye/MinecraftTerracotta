package com.multiplayer.terracotta.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.multiplayer.terracotta.client.gui.RoomSettingsScreen;
import com.multiplayer.terracotta.client.gui.StartupScreen;
import com.multiplayer.terracotta.network.TerracottaApiClient;

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

@EventBusSubscriber(modid = "minecraftterracotta", value = Dist.CLIENT)
public class InGameMenuHandler {
    private static final Gson GSON = new Gson();
    private static boolean showInfoOverlay = false;
    private static JsonObject lastState = null;
    private static long lastCreateClickTime = 0;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof PauseScreen screen) {
            int width = screen.width;
            int height = screen.height;
            int buttonWidth = 100;
            int x = width - buttonWidth - 5;
            int y = 5;

            Button infoBtn = Button.builder(Component.literal(showInfoOverlay ? "隐藏信息" : "显示信息"), button -> {
                showInfoOverlay = !showInfoOverlay;
                button.setMessage(Component.literal(showInfoOverlay ? "隐藏信息" : "显示信息"));
            }).bounds(x, y, buttonWidth, 20).build();
            infoBtn.visible = false;
            event.addListener(infoBtn);

            Button settingsBtn = Button.builder(Component.literal("房间设置"), button -> {
                if ("房间信息".equals(button.getMessage().getString())) {
                    Minecraft.getInstance().setScreen(new com.multiplayer.terracotta.client.gui.TerracottaDashboard(screen));
                } else {
                    Minecraft.getInstance().setScreen(new RoomSettingsScreen(screen));
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
                if (!TerracottaApiClient.hasDynamicPort()) {
                     Minecraft.getInstance().setScreen(new StartupScreen(screen, () -> {
                         Minecraft.getInstance().setScreen(screen);
                         String playerName = Minecraft.getInstance().getUser().getName();
                         TerracottaApiClient.setScanning(playerName);
                     }));
                } else {
                     String playerName = Minecraft.getInstance().getUser().getName();
                     TerracottaApiClient.setScanning(playerName);
                     button.setMessage(Component.literal("请求中..."));
                     button.active = false;
                }
            }).bounds(x, y, buttonWidth, 20).build();
            createRoomBtn.visible = false;
            event.addListener(createRoomBtn);

            event.addListener(new RoomStateWidget(0, 0, width, height, infoBtn, settingsBtn, createRoomBtn, Minecraft.getInstance().font));
        }
    }

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
            
            String roomCode = lastState.has("room") ? lastState.get("room").getAsString() : "未知";
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

        private void updateState() {
            TerracottaApiClient.getState().whenComplete((stateJson, ex) -> {
                if (ex != null || stateJson == null) {
                    TerracottaApiClient.clearDynamicPort();
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
                    }
                } catch (Exception ignored) {}
            });
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        }
    }
}

