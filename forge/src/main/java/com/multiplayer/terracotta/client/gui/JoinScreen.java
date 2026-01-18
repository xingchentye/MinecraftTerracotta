package com.multiplayer.terracotta.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.multiplayer.terracotta.network.TerracottaApiClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class JoinScreen extends TerracottaBaseScreen {
    private EditBox roomCodeBox;
    private Component statusText = Component.translatable("terracotta.join.status.enter_code");
    private boolean isWorking = false;
    private Button joinBtn;
    private long lastStateCheck = 0;
    private static final Gson GSON = new Gson();
    private String autoJoinCode = null;
    private boolean keepConnection = false;

    public JoinScreen(Screen parent) {
        super(Component.translatable("terracotta.join.title"), parent);
    }

    public JoinScreen(Screen parent, String autoJoinCode) {
        this(parent);
        this.autoJoinCode = autoJoinCode;
    }

    @Override
    protected void initContent() {
        LinearLayout contentLayout = LinearLayout.vertical().spacing(12);

        this.roomCodeBox = new EditBox(this.font, 0, 0, 200, 20, Component.translatable("terracotta.join.input.label"));
        this.roomCodeBox.setMaxLength(128);
        this.roomCodeBox.setHint(Component.translatable("terracotta.join.input.hint"));
        contentLayout.addChild(this.roomCodeBox);

        this.joinBtn = Button.builder(Component.translatable("terracotta.common.join"), button -> {
            this.joinRoom();
        }).width(200).build();
        contentLayout.addChild(this.joinBtn);

        this.layout.addToContents(contentLayout);

        this.layout.addToFooter(Button.builder(Component.translatable("terracotta.common.back"), button -> {
            this.onClose();
        }).width(200).build());

        if (this.autoJoinCode != null && !this.autoJoinCode.isEmpty()) {
            this.roomCodeBox.setValue(this.autoJoinCode);
        }
    }

    private void joinRoom() {
        String roomCode = roomCodeBox.getValue();
        if (roomCode.isEmpty()) {
            statusText = Component.translatable("terracotta.join.error.empty");
            return;
        }

        if (isWorking) return;
        isWorking = true;
        statusText = Component.translatable("terracotta.join.status.requesting");
        joinBtn.active = false;

        String playerName = this.minecraft.getUser().getName();

        TerracottaApiClient.joinRoom(roomCode, playerName).thenAccept(success -> {
            if (success) {
                statusText = Component.translatable("terracotta.join.status.success");
            } else {
                statusText = Component.translatable("terracotta.join.status.failed").append("Unknown error");
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
        TerracottaApiClient.getState().thenAccept(stateJson -> {
            if (stateJson == null) return;
            try {
                JsonObject json = GSON.fromJson(stateJson, JsonObject.class);
                if (json.has("state")) {
                    String state = json.get("state").getAsString();

                    if ("guest-ok".equals(state)) {
                        if (json.has("url")) {
                            statusText = Component.translatable("terracotta.join.status.connected");
                            isWorking = false;
                            keepConnection = true;
                            if (this.parent instanceof TerracottaDashboard dashboard) {
                                dashboard.setConnected(true);
                                this.minecraft.execute(() -> {
                                    dashboard.updateFromState(json);
                                    this.minecraft.setScreen(dashboard);
                                });
                            } else {
                                this.minecraft.execute(this::onClose);
                            }
                        }
                    } else if ("guest-connecting".equals(state)) {
                        statusText = Component.translatable("terracotta.join.status.connecting_p2p");
                    } else if ("guest-starting".equals(state)) {
                        statusText = Component.translatable("terracotta.join.status.initializing");
                    } else if ("waiting".equals(state)) {
                    }
                }
            } catch (Exception e) {
            }
        });
    }

    @Override
    public void onClose() {
        if (isWorking && !keepConnection) {
            TerracottaApiClient.setIdle();
        }
        super.onClose();
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (this.joinBtn != null) {
            int textY = this.joinBtn.getY() + this.joinBtn.getHeight() + 10;
            guiGraphics.drawCenteredString(this.font, this.statusText, this.width / 2, textY, 0xAAAAAA);
        }
    }
}
