package com.multiplayer.ender.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.multiplayer.ender.network.EnderApiClient;
import com.multiplayer.ender.client.gui.EnderDashboard;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class JoinScreen extends EnderBaseScreen {
    private EditBox roomCodeBox;
    private Component statusText = Component.translatable("ender.join.status.enter_code");
    private boolean isWorking = false;
    private Button joinBtn;
    private long lastStateCheck = 0;
    private static final Gson GSON = new Gson();
    private String autoJoinCode = null;
    private boolean keepConnection = false;
    private JsonObject connectedState = null;

    public JoinScreen(Screen parent) {
        super(Component.translatable("ender.join.title"), parent);
    }

    public JoinScreen(Screen parent, String autoJoinCode) {
        this(parent);
        this.autoJoinCode = autoJoinCode;
    }

    @Override
    protected void initContent() {
        LinearLayout contentLayout = LinearLayout.vertical().spacing(12);

        this.roomCodeBox = new EditBox(this.font, 0, 0, 200, 20, Component.translatable("ender.join.input.label"));
        this.roomCodeBox.setMaxLength(128);
        this.roomCodeBox.setHint(Component.literal("XXXX-XXXX-XXXX-XXXX"));
        contentLayout.addChild(this.roomCodeBox);

        this.joinBtn = Button.builder(Component.translatable("ender.common.join"), button -> {
            this.joinRoom();
        }).width(200).build();
        contentLayout.addChild(this.joinBtn);

        this.layout.addToContents(contentLayout);

        this.layout.addToFooter(Button.builder(Component.translatable("ender.common.back"), button -> {
            this.onClose();
        }).width(200).build());

        checkExistingConnection();

        if (this.autoJoinCode != null && !this.autoJoinCode.isEmpty()) {
            this.roomCodeBox.setValue(this.autoJoinCode);
        }
    }

    private void joinRoom() {
        String roomCode = roomCodeBox.getValue();
        if (roomCode.isEmpty()) {
            statusText = Component.translatable("ender.join.error.empty");
            return;
        }

        if (isWorking) return;
        isWorking = true;
        statusText = Component.translatable("ender.join.status.requesting");
        joinBtn.active = false;

        String playerName = this.minecraft.getUser().getName();
        EnderApiClient.rememberRoomCode(roomCode);

        EnderApiClient.getState().thenAccept(stateJson -> {
            if (stateJson != null) {
                try {
                    JsonObject json = GSON.fromJson(stateJson, JsonObject.class);
                    if (isConnectedState(json)) {
                        handleConnectedState(json);
                        return;
                    }
                } catch (Exception ignored) {
                }
            }
            doJoinRequest(roomCode, playerName);
        });
    }

    private void doJoinRequest(String roomCode, String playerName) {
        EnderApiClient.joinRoom(roomCode, playerName).thenAccept(success -> {
            if (success) {
                statusText = Component.translatable("ender.join.status.success");
            } else {
                statusText = Component.translatable("ender.join.status.failed").append("Unknown error");
                isWorking = false;
                joinBtn.active = true;
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
                checkConnectionStatus();
            }
        }
    }

    private void checkConnectionStatus() {
        EnderApiClient.getState().thenAccept(stateJson -> {
            if (stateJson == null) return;
            try {
                JsonObject json = GSON.fromJson(stateJson, JsonObject.class);
                if (json.has("state")) {
                    String state = json.get("state").getAsString();

                    if (isConnectedState(json)) {
                        handleConnectedState(json);
                    } else if ("guest-connecting".equals(state)) {
                        statusText = Component.translatable("ender.join.status.connecting_p2p");
                    } else if ("guest-starting".equals(state)) {
                        statusText = Component.translatable("ender.join.status.initializing");
                    } else if ("waiting".equals(state)) {
                    }
                }
            } catch (Exception e) {
            }
        });
    }

    private void checkExistingConnection() {
        if (!EnderApiClient.hasDynamicPort()) {
            return;
        }
        EnderApiClient.getState().thenAccept(stateJson -> {
            if (stateJson == null) return;
            try {
                JsonObject json = GSON.fromJson(stateJson, JsonObject.class);
                if (isConnectedState(json)) {
                    handleConnectedState(json);
                }
            } catch (Exception ignored) {
            }
        });
    }

    private boolean isConnectedState(JsonObject json) {
        if (json == null || !json.has("state")) {
            return false;
        }
        String state = json.get("state").getAsString();
        return "guest-ok".equals(state) || "host-ok".equals(state);
    }

    private void handleConnectedState(JsonObject json) {
        statusText = Component.translatable("ender.join.status.connected");
        isWorking = false;
        keepConnection = true;
        connectedState = json;
        if (json != null && json.has("room")) {
            EnderApiClient.rememberRoomCode(json.get("room").getAsString());
        }
        joinBtn.active = false;
        openConnectedScreen(json);
    }

    private void openConnectedScreen(JsonObject json) {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.execute(() -> {
            if (this.parent instanceof EnderDashboard dashboard) {
                this.minecraft.setScreen(dashboard);
                dashboard.updateFromState(json);
            } else {
                EnderDashboard dashboard = new EnderDashboard(this.parent);
                this.minecraft.setScreen(dashboard);
                dashboard.updateFromState(json);
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

        if (this.joinBtn != null) {
            int textY = this.joinBtn.getY() + this.joinBtn.getHeight() + 10;
            guiGraphics.drawCenteredString(this.font, this.statusText, this.width / 2, textY, 0xAAAAAA);
            renderRoomInfo(guiGraphics, textY + 14);
        }
    }

    private void renderRoomInfo(GuiGraphics guiGraphics, int startY) {
        if (connectedState == null) {
            return;
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("房间信息");
        String roomCode = connectedState.has("room") ? connectedState.get("room").getAsString() : EnderApiClient.getLastRoomCode();
        if (roomCode == null || roomCode.isEmpty()) {
            roomCode = "未知";
        }
        lines.add("房间号: " + roomCode);
        java.util.List<String> members = extractMembers(connectedState);
        if (!members.isEmpty()) {
            lines.add("成员: " + String.join(", ", members));
        }
        int lineHeight = 10;
        int padding = 6;
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, this.font.width(line));
        }
        int boxWidth = maxWidth + padding * 2;
        int boxHeight = lineHeight * lines.size() + padding * 2;
        int startX = (this.width - boxWidth) / 2;
        guiGraphics.fill(startX, startY, startX + boxWidth, startY + boxHeight, 0x80000000);
        int y = startY + padding;
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? 0xFFFFFF : (i == 1 ? 0xFFFF55 : 0xCCCCCC);
            guiGraphics.drawString(this.font, lines.get(i), startX + padding, y, color);
            y += lineHeight;
        }
    }

    private java.util.List<String> extractMembers(JsonObject json) {
        java.util.List<String> members = new java.util.ArrayList<>();
        if (json.has("profiles")) {
            JsonArray profiles = json.getAsJsonArray("profiles");
            for (JsonElement element : profiles) {
                if (!element.isJsonObject()) continue;
                JsonObject profile = element.getAsJsonObject();
                if (profile.has("name")) {
                    members.add(profile.get("name").getAsString());
                }
            }
        } else if (json.has("players")) {
            JsonArray players = json.getAsJsonArray("players");
            for (JsonElement element : players) {
                members.add(element.getAsString());
            }
        }
        return members;
    }
}


