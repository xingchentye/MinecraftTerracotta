package com.multiplayer.ender.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.multiplayer.ender.network.EnderApiClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class HostScreen extends EnderBaseScreen {
    private Text statusText = Text.translatable("ender.host.status.ready");
    private String roomCode = "";
    private boolean isWorking = false;
    private boolean keepConnection = false;
    private ButtonWidget startBtn;
    private long lastStateCheck = 0;
    private long lastClickTime = 0;
    private static final Gson GSON = new Gson();

    public HostScreen(Screen parent) {
        super(Text.translatable("ender.host.title"), parent);
    }

    @Override
    protected void initContent() {
        DirectionalLayoutWidget layout = DirectionalLayoutWidget.vertical().spacing(10);
        layout.getMainPositioner().alignHorizontalCenter();

        this.startBtn = ButtonWidget.builder(Text.translatable("ender.host.button.start"), button -> startHosting()).width(200).build();
        layout.add(this.startBtn);

        ButtonWidget cancelBtn = ButtonWidget.builder(Text.translatable("ender.common.button.cancel"), button -> this.close()).width(200).build();
        layout.add(cancelBtn);

        this.layout.addBody(layout);
    }

    private void startHosting() {
        long now = System.currentTimeMillis();
        if (now - lastClickTime < 300) return;
        lastClickTime = now;
        if (isWorking) return;
        isWorking = true;
        statusText = Text.translatable("ender.host.status.requesting");
        startBtn.active = false;

        int port = 25565;
        if (this.client != null && this.client.getServer() != null && this.client.getServer().isRemote()) {
            port = this.client.getServer().getServerPort();
        }
        String playerName = this.client != null && this.client.getSession() != null ? this.client.getSession().getUsername() : "Player";

        EnderApiClient.startHosting(port, playerName).thenAccept(roomCode -> {
            if (roomCode != null && !roomCode.isEmpty()) {
                statusText = Text.translatable("ender.host.status.success");
                this.roomCode = roomCode;
            } else {
                MutableText msg = Text.translatable("ender.host.status.failed").append(Text.literal(": 未能获取房间号").formatted(Formatting.RED));
                statusText = msg;
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
                            statusText = Text.translatable("ender.host.status.created");
                            keepConnection = true;
                            if (this.parent instanceof EnderDashboard dashboard) {
                                dashboard.setConnected(true);
                            }
                            if (this.client != null) {
                                this.client.execute(this::close);
                            }
                        }
                    } else if ("host-starting".equals(state)) {
                        statusText = Text.translatable("ender.host.status.creating");
                    } else if ("host-scanning".equals(state)) {
                        statusText = Text.translatable("ender.host.status.scanning");
                    }
                }
            } catch (Exception ignored) {
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
    public void render(@NotNull DrawContext context, int mouseX, int mouseY, float partialTick) {
        super.render(context, mouseX, mouseY, partialTick);

        int textY = this.layout.getHeaderHeight() + 20;
        context.drawCenteredTextWithShadow(this.textRenderer, statusText, this.width / 2, textY, 0xAAAAAA);

        if (!roomCode.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("ender.host.invite_code_prefix").append(roomCode), this.width / 2, textY + 20, 0x55FF55);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("ender.host.share_hint"), this.width / 2, textY + 35, 0xAAAAAA);
        }
    }
}


