package com.multiplayer.ender.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.multiplayer.ender.network.EnderApiClient;
import com.multiplayer.ender.client.gui.EnderDashboard;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

public class JoinScreen extends EnderBaseScreen {
    private TextFieldWidget roomCodeBox;
    private Text statusText = Text.translatable("ender.join.status.enter_code");
    private boolean isWorking = false;
    private ButtonWidget joinBtn;
    private long lastStateCheck = 0;
    private static final Gson GSON = new Gson();
    private String autoJoinCode = null;
    private boolean keepConnection = false;
    private JsonObject connectedState = null;

    public JoinScreen(Screen parent) {
        super(Text.translatable("ender.join.title"), parent);
    }

    public JoinScreen(Screen parent, String autoJoinCode) {
        this(parent);
        this.autoJoinCode = autoJoinCode;
    }

    @Override
    protected void initContent() {
        DirectionalLayoutWidget contentLayout = DirectionalLayoutWidget.vertical().spacing(12);

        this.roomCodeBox = new TextFieldWidget(this.textRenderer, 200, 20, Text.translatable("ender.join.input.label"));
        this.roomCodeBox.setMaxLength(128);
        this.roomCodeBox.setPlaceholder(Text.literal("XXXX-XXXX-XXXX-XXXX"));
        contentLayout.add(this.roomCodeBox);

        this.joinBtn = ButtonWidget.builder(Text.translatable("ender.common.join"), button -> joinRoom())
                .width(200)
                .build();
        contentLayout.add(this.joinBtn);

        this.layout.addBody(contentLayout);

        this.layout.addFooter(ButtonWidget.builder(Text.translatable("ender.common.back"), button -> this.close())
                .width(200)
                .build());

        checkExistingConnection();

        if (this.autoJoinCode != null && !this.autoJoinCode.isEmpty()) {
            this.roomCodeBox.setText(this.autoJoinCode);
            this.client.execute(this::joinRoom);
        }
    }

    private void joinRoom() {
        String roomCode = roomCodeBox.getText();
        if (roomCode.isEmpty()) {
            statusText = Text.translatable("ender.join.error.empty");
            return;
        }

        if (isWorking) {
            return;
        }
        isWorking = true;
        statusText = Text.translatable("ender.join.status.requesting");
        joinBtn.active = false;

        String playerName = this.client != null && this.client.getSession() != null
                ? this.client.getSession().getUsername()
                : "Player";
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
                statusText = Text.translatable("ender.join.status.success");
            } else {
                statusText = Text.translatable("ender.join.status.failed").append(Text.literal("Unknown error"));
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
            if (stateJson == null) {
                return;
            }
            try {
                JsonObject json = GSON.fromJson(stateJson, JsonObject.class);
                if (json.has("state")) {
                    String state = json.get("state").getAsString();

                    if (isConnectedState(json)) {
                        handleConnectedState(json);
                    } else if ("guest-connecting".equals(state)) {
                        statusText = Text.translatable("ender.join.status.connecting_p2p");
                    } else if ("guest-starting".equals(state)) {
                        statusText = Text.translatable("ender.join.status.initializing");
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void checkExistingConnection() {
        if (!EnderApiClient.hasDynamicPort()) {
            return;
        }
        EnderApiClient.getState().thenAccept(stateJson -> {
            if (stateJson == null) {
                return;
            }
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
        statusText = Text.translatable("ender.join.status.connected");
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
        if (this.client == null) {
            return;
        }
        this.client.execute(() -> {
            if (this.parent instanceof EnderDashboard dashboard) {
                this.client.setScreen(dashboard);
                dashboard.updateFromState(json);
            } else {
                EnderDashboard dashboard = new EnderDashboard(this.parent);
                this.client.setScreen(dashboard);
                dashboard.updateFromState(json);
            }
        });
    }

    @Override
    public void close() {
        if (isWorking && !keepConnection) {
            EnderApiClient.setIdle();
        }
        super.close();
    }

    @Override
    public void render(@NotNull DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        if (this.joinBtn != null) {
            int textY = this.joinBtn.getY() + this.joinBtn.getHeight() + 10;
            context.drawCenteredTextWithShadow(this.textRenderer, this.statusText, this.width / 2, textY, 0xAAAAAA);
            renderRoomInfo(context, textY + 14);
        }
    }

    private void renderRoomInfo(DrawContext context, int startY) {
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
            maxWidth = Math.max(maxWidth, this.textRenderer.getWidth(line));
        }
        int boxWidth = maxWidth + padding * 2;
        int boxHeight = lineHeight * lines.size() + padding * 2;
        int startX = (this.width - boxWidth) / 2;
        context.fill(startX, startY, startX + boxWidth, startY + boxHeight, 0x80000000);
        int y = startY + padding;
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? 0xFFFFFF : (i == 1 ? 0xFFFF55 : 0xCCCCCC);
            context.drawText(this.textRenderer, lines.get(i), startX + padding, y, color, false);
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



