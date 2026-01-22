package com.multiplayer.ender.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.multiplayer.ender.logic.ProcessLauncher;
import com.multiplayer.ender.network.EnderApiClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

public class RoomInfoScreen extends EnderBaseScreen {
    private static final Gson GSON = new Gson();
    private long lastStateCheck = 0;
    private Text backendDisplay = Text.translatable("ender.dashboard.status.fetching");
    private boolean backendPrefixed = false;
    private String lastStateRaw = null;
    private JsonObject lastStateJson = null;

    public RoomInfoScreen(Screen parent) {
        super(Text.literal("房间信息"), parent);
    }

    @Override
    protected void initContent() {
        DirectionalLayoutWidget contentLayout = DirectionalLayoutWidget.vertical().spacing(8);
        contentLayout.getMainPositioner().alignHorizontalCenter();

        Text statusText = backendPrefixed
                ? Text.literal("状态: ").append(Text.translatable("ender.dashboard.status_prefix").append(backendDisplay))
                : Text.literal("状态: ").append(backendDisplay);
        contentLayout.add(new TextWidget(statusText, this.textRenderer));

        String roomCode = lastStateJson != null && lastStateJson.has("room")
                ? lastStateJson.get("room").getAsString()
                : EnderApiClient.getLastRoomCode();
        if (roomCode == null || roomCode.isEmpty()) {
            roomCode = "未知";
        }
        contentLayout.add(new TextWidget(Text.literal("房间号: " + roomCode), this.textRenderer));

        java.util.List<String> members = extractMembers(lastStateJson);
        contentLayout.add(new TextWidget(Text.literal("成员数: " + members.size()), this.textRenderer));
        contentLayout.add(ButtonWidget.builder(Text.literal("详细列表"), b -> {
            if (this.client != null) {
                this.client.setScreen(new RoomListsScreen(this));
            }
        }).width(120).build());

        this.layout.addBody(contentLayout);

        DirectionalLayoutWidget footerLayout = DirectionalLayoutWidget.horizontal().spacing(10);
        footerLayout.add(ButtonWidget.builder(Text.literal("断开连接"), button -> {
            EnderApiClient.setIdle();
            new Thread(ProcessLauncher::stop, "Ender-Stopper").start();
            this.close();
        }).width(120).build());
        footerLayout.add(ButtonWidget.builder(Text.literal("返回"), button -> this.close()).width(120).build());
        this.layout.addFooter(footerLayout);

        checkStateImmediately();
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        if (now - lastStateCheck > 1000) {
            lastStateCheck = now;
            EnderApiClient.getState().thenAccept(stateJson -> {
                if (stateJson == null) {
                    return;
                }
                if (this.client != null) {
                    this.client.execute(() -> updateBackendState(stateJson));
                }
            });
        }
    }

    @Override
    public void render(@NotNull DrawContext context, int mouseX, int mouseY, float partialTick) {
        super.render(context, mouseX, mouseY, partialTick);
    }

    private void checkStateImmediately() {
        if (!EnderApiClient.hasDynamicPort()) {
            return;
        }
        EnderApiClient.getState().thenAccept(stateJson -> {
            if (stateJson == null) {
                return;
            }
            if (this.client != null) {
                this.client.execute(() -> updateBackendState(stateJson));
            }
        });
    }

    private void updateBackendState(String stateJson) {
        if (stateJson == null || stateJson.equals(this.lastStateRaw)) {
            return;
        }
        this.lastStateRaw = stateJson;
        try {
            JsonObject json = GSON.fromJson(stateJson, JsonObject.class);
            this.lastStateJson = json;
            String state = "";
            if (json.has("state")) {
                state = json.get("state").getAsString();
            }

            String displayKey;
            switch (state) {
                case "idle": displayKey = "ender.state.idle"; break;
                case "host-starting": displayKey = "ender.state.host_starting"; break;
                case "host-scanning": displayKey = "ender.state.host_scanning"; break;
                case "host-ok": displayKey = "ender.state.connected"; break;
                case "guest-starting": displayKey = "ender.state.guest_starting"; break;
                case "guest-connecting": displayKey = "ender.state.guest_connecting"; break;
                case "guest-ok": displayKey = "ender.state.connected"; break;
                case "waiting": displayKey = "ender.state.waiting"; break;
                default: displayKey = null; break;
            }

            if (displayKey != null) {
                this.backendDisplay = Text.translatable(displayKey);
                this.backendPrefixed = true;
            } else {
                this.backendDisplay = Text.literal(state);
                this.backendPrefixed = true;
            }
        } catch (Exception ignored) {
        }

        if (this.client != null) {
            this.init(this.client, this.width, this.height);
        }
    }

    private java.util.List<String> extractMembers(JsonObject json) {
        java.util.List<String> members = new java.util.ArrayList<>();
        if (json == null) {
            return members;
        }
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



