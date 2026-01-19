package com.multiplayer.terracotta.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.multiplayer.terracotta.network.TerracottaApiClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

public class JoinScreen extends TerracottaBaseScreen {
    private TextFieldWidget roomCodeBox;
    private Text statusText = Text.translatable("terracotta.join.status.enter_code");
    private boolean isWorking = false;
    private ButtonWidget joinBtn;
    private long lastStateCheck = 0;
    private static final Gson GSON = new Gson();
    private String autoJoinCode = null;
    private boolean keepConnection = false;

    public JoinScreen(Screen parent) {
        super(Text.translatable("terracotta.join.title"), parent);
    }

    public JoinScreen(Screen parent, String autoJoinCode) {
        this(parent);
        this.autoJoinCode = autoJoinCode;
    }

    @Override
    protected void initContent() {
        DirectionalLayoutWidget contentLayout = DirectionalLayoutWidget.vertical().spacing(12);

        this.roomCodeBox = new TextFieldWidget(this.textRenderer, 200, 20, Text.translatable("terracotta.join.input.label"));
        this.roomCodeBox.setMaxLength(128);
        this.roomCodeBox.setPlaceholder(Text.translatable("terracotta.join.input.hint"));
        contentLayout.add(this.roomCodeBox);

        this.joinBtn = ButtonWidget.builder(Text.translatable("terracotta.common.join"), button -> joinRoom())
                .width(200)
                .build();
        contentLayout.add(this.joinBtn);

        this.layout.addBody(contentLayout);

        this.layout.addFooter(ButtonWidget.builder(Text.translatable("terracotta.common.back"), button -> this.close())
                .width(200)
                .build());

        if (this.autoJoinCode != null && !this.autoJoinCode.isEmpty()) {
            this.roomCodeBox.setText(this.autoJoinCode);
            this.client.execute(this::joinRoom);
        }
    }

    private void joinRoom() {
        String roomCode = roomCodeBox.getText();
        if (roomCode.isEmpty()) {
            statusText = Text.translatable("terracotta.join.error.empty");
            return;
        }

        if (isWorking) {
            return;
        }
        isWorking = true;
        statusText = Text.translatable("terracotta.join.status.requesting");
        joinBtn.active = false;

        String playerName = this.client != null && this.client.getSession() != null
                ? this.client.getSession().getUsername()
                : "Player";

        TerracottaApiClient.joinRoom(roomCode, playerName).thenAccept(success -> {
            if (success) {
                statusText = Text.translatable("terracotta.join.status.success");
            } else {
                statusText = Text.translatable("terracotta.join.status.failed").append(Text.literal("Unknown error"));
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
            if (stateJson == null) {
                return;
            }
            try {
                JsonObject json = GSON.fromJson(stateJson, JsonObject.class);
                if (json.has("state")) {
                    String state = json.get("state").getAsString();

                    if ("guest-ok".equals(state)) {
                        if (json.has("url")) {
                            statusText = Text.translatable("terracotta.join.status.connected");
                            isWorking = false;
                            keepConnection = true;
                            if (this.parent instanceof TerracottaDashboard dashboard) {
                                dashboard.setConnected(true);
                                this.client.execute(() -> {
                                    dashboard.updateFromState(json);
                                    this.client.setScreen(dashboard);
                                });
                            } else {
                                this.client.execute(this::close);
                            }
                        }
                    } else if ("guest-connecting".equals(state)) {
                        statusText = Text.translatable("terracotta.join.status.connecting_p2p");
                    } else if ("guest-starting".equals(state)) {
                        statusText = Text.translatable("terracotta.join.status.initializing");
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void close() {
        if (isWorking && !keepConnection) {
            TerracottaApiClient.setIdle();
        }
        super.close();
    }

    @Override
    public void render(@NotNull DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        if (this.joinBtn != null) {
            int textY = this.joinBtn.getY() + this.joinBtn.getHeight() + 10;
            context.drawCenteredTextWithShadow(this.textRenderer, this.statusText, this.width / 2, textY, 0xAAAAAA);
        }
    }
}

