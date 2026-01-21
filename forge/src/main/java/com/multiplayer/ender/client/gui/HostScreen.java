package com.multiplayer.ender.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.multiplayer.ender.network.EnderApiClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class HostScreen extends EnderBaseScreen {
    private Component statusText = Component.translatable("ender.host.status.ready");
    private String roomCode = "";
    private boolean isWorking = false;
    private boolean keepConnection = false;
    private Button startBtn;
    private long lastStateCheck = 0;
    private long lastClickTime = 0;
    private static final Gson GSON = new Gson();

    public HostScreen(Screen parent) {
        super(Component.translatable("ender.host.title"), parent);
    }

    @Override
    protected void initContent() {
        LinearLayout contentLayout = LinearLayout.vertical().spacing(12);

        this.startBtn = Button.builder(Component.translatable("ender.host.button.start"), button -> {
            this.startHosting();
        }).width(200).build();
        contentLayout.addChild(this.startBtn);

        contentLayout.addChild(Button.builder(Component.translatable("ender.common.button.cancel"), button -> {
            this.onClose();
        }).width(200).build());

        this.layout.addToContents(contentLayout);
    }

    private void startHosting() {
        long now = System.currentTimeMillis();
        if (now - lastClickTime < 300) return;
        lastClickTime = now;
        if (isWorking) return;
        isWorking = true;
        statusText = Component.translatable("ender.host.status.requesting");
        startBtn.active = false;

        int port = 25565;
        if (this.minecraft.getSingleplayerServer() != null) {
            port = this.minecraft.getSingleplayerServer().getPort();
        }
        String playerName = this.minecraft.getUser().getName();

        EnderApiClient.startHosting(port, playerName).thenAccept(roomCode -> {
            if (roomCode != null && !roomCode.isEmpty()) {
                statusText = Component.translatable("ender.host.status.success");
                this.roomCode = roomCode;
            } else {
                statusText = Component.translatable("ender.host.status.failed").append(": 未能获取房间号");
                isWorking = false;
                startBtn.active = true;
            }
        });
    }

    @Override
    public void tick() {
        super.tick();
        if (isWorking) {
            long now = System.currentTimeMillis();
            if (now - lastStateCheck > 1000) {
                lastStateCheck = now;
                checkHostStatus();
            }
        }
    }

    private void checkHostStatus() {
        EnderApiClient.getState().thenAccept(stateJson -> {
            if (stateJson == null) return;
            try {
                JsonObject json = GSON.fromJson(stateJson, JsonObject.class);
                if (json.has("state")) {
                    String state = json.get("state").getAsString();

                    if ("host-ok".equals(state)) {
                        if (json.has("room")) {
                            this.roomCode = json.get("room").getAsString();
                            statusText = Component.translatable("ender.host.status.created");
                            keepConnection = true;
                            if (this.parent instanceof EnderDashboard) {
                                ((EnderDashboard) this.parent).setConnected(true);
                            }

                            this.minecraft.execute(this::onClose);
                        }
                    } else if ("host-starting".equals(state)) {
                        statusText = Component.translatable("ender.host.status.creating");
                    } else if ("host-scanning".equals(state)) {
                        statusText = Component.translatable("ender.host.status.scanning");
                    }
                }
            } catch (Exception e) {
            }
        });
    }

    @Override
    public void onClose() {
        if (isWorking && !keepConnection) {
            EnderApiClient.setIdle();
        }
        super.onClose();
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int textY = this.layout.getHeaderHeight() + 20;
        guiGraphics.drawCenteredString(this.font, this.statusText, this.width / 2, textY, 0xAAAAAA);

        if (!roomCode.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("ender.host.invite_code_prefix").append(roomCode), this.width / 2, textY + 20, 0x55FF55);
            guiGraphics.drawCenteredString(this.font, Component.translatable("ender.host.share_hint"), this.width / 2, textY + 35, 0xAAAAAA);
        }
    }
}



