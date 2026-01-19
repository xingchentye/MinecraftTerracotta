package com.multiplayer.terracotta.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.multiplayer.terracotta.network.TerracottaApiClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class HostScreen extends TerracottaBaseScreen {
    private Text statusText = Text.translatable("terracotta.host.status.ready");
    private String roomCode = "";
    private boolean isWorking = false;
    private boolean keepConnection = false;
    private ButtonWidget startBtn;
    private long lastStateCheck = 0;
    private long lastClickTime = 0;
    private static final Gson GSON = new Gson();

    public HostScreen(Screen parent) {
        super(Text.translatable("terracotta.host.title"), parent);
    }

    @Override
    protected void initContent() {
        DirectionalLayoutWidget layout = DirectionalLayoutWidget.vertical().spacing(10);
        layout.getMainPositioner().alignHorizontalCenter();

        this.startBtn = ButtonWidget.builder(Text.translatable("terracotta.host.button.start"), button -> startHosting()).width(200).build();
        layout.add(this.startBtn);

        ButtonWidget cancelBtn = ButtonWidget.builder(Text.translatable("terracotta.common.button.cancel"), button -> this.close()).width(200).build();
        layout.add(cancelBtn);

        this.layout.addBody(layout);
    }

    private void startHosting() {
        long now = System.currentTimeMillis();
        if (now - lastClickTime < 300) return;
        lastClickTime = now;
        if (isWorking) return;
        isWorking = true;
        statusText = Text.translatable("terracotta.host.status.requesting");
        startBtn.active = false;

        int port = 25565;
        if (this.client != null && this.client.getServer() != null && this.client.getServer().isRemote()) {
            port = this.client.getServer().getServerPort();
        }
        String playerName = this.client != null && this.client.getSession() != null ? this.client.getSession().getUsername() : "Player";

        TerracottaApiClient.startHosting(port, playerName).thenAccept(error -> {
            if (error == null) {
                statusText = Text.translatable("terracotta.host.status.success");
            } else {
                MutableText msg = Text.translatable("terracotta.host.status.failed").append(Text.literal(": ").append(error).formatted(Formatting.RED));
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
        TerracottaApiClient.getState().thenAccept(stateJson -> {
            if (stateJson == null) return;
            try {
                JsonObject json = GSON.fromJson(stateJson, JsonObject.class);
                if (json.has("state")) {
                    String state = json.get("state").getAsString();

                    if ("host-ok".equals(state)) {
                        if (json.has("room")) {
                            this.roomCode = json.get("room").getAsString();
                            statusText = Text.translatable("terracotta.host.status.created");
                            keepConnection = true;
                            if (this.parent instanceof TerracottaDashboard dashboard) {
                                dashboard.setConnected(true);
                            }
                            if (this.client != null) {
                                this.client.execute(this::close);
                            }
                        }
                    } else if ("host-starting".equals(state)) {
                        statusText = Text.translatable("terracotta.host.status.creating");
                    } else if ("host-scanning".equals(state)) {
                        statusText = Text.translatable("terracotta.host.status.scanning");
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
    public void render(@NotNull DrawContext context, int mouseX, int mouseY, float partialTick) {
        super.render(context, mouseX, mouseY, partialTick);

        int textY = this.layout.getHeaderHeight() + 20;
        context.drawCenteredTextWithShadow(this.textRenderer, statusText, this.width / 2, textY, 0xAAAAAA);

        if (!roomCode.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("terracotta.host.invite_code_prefix").append(roomCode), this.width / 2, textY + 20, 0x55FF55);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("terracotta.host.share_hint"), this.width / 2, textY + 35, 0xAAAAAA);
        }
    }
}
