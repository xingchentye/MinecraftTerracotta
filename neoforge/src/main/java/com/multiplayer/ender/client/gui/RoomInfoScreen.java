package com.multiplayer.ender.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.multiplayer.ender.logic.ProcessLauncher;
import com.multiplayer.ender.network.EnderApiClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * 房间信息界面。
 * 显示当前房间的状态、房间号和在线成员。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public class RoomInfoScreen extends EnderBaseScreen {
    private static final Gson GSON = new Gson();
    /** 上次检查状态的时间戳 */
    private long lastStateCheck = 0;
    /** 后端状态显示文本 */
    private String backendState = Component.translatable("ender.dashboard.status.fetching").getString();
    /** 上次获取的原始状态 JSON 字符串 */
    private String lastStateRaw = null;
    /** 上次解析的状态对象 */
    private JsonObject lastStateJson = null;

    /**
     * 构造函数。
     *
     * @param parent 父屏幕
     */
    public RoomInfoScreen(Screen parent) {
        super(Component.literal("房间信息"), parent);
    }

    /**
     * 初始化界面内容。
     * 显示状态、房间号、成员统计以及操作按钮。
     */
    @Override
    protected void initContent() {
        LinearLayout content = LinearLayout.vertical().spacing(8);
        content.defaultCellSetting().alignHorizontallyCenter();

        content.addChild(new StringWidget(Component.literal("状态: " + backendState), this.font));

        String roomCode = lastStateJson != null && lastStateJson.has("room")
                ? lastStateJson.get("room").getAsString()
                : EnderApiClient.getLastRoomCode();
        if (roomCode == null || roomCode.isEmpty()) {
            roomCode = "未知";
        }
        content.addChild(new StringWidget(Component.literal("房间号: " + roomCode), this.font));

        java.util.List<String> members = extractMembers(lastStateJson);
        content.addChild(new StringWidget(Component.literal("成员数: " + members.size()), this.font));
        content.addChild(Button.builder(Component.literal("详细列表"), b -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new RoomListsScreen(this));
            }
        }).width(120).build());

        this.layout.addToContents(content);

        LinearLayout footer = LinearLayout.horizontal().spacing(10);
        footer.addChild(Button.builder(Component.literal("断开连接"), button -> {
            EnderApiClient.setIdle();
            new Thread(ProcessLauncher::stop, "Ender-Stopper").start();
            this.onClose();
        }).width(120).build());
        footer.addChild(Button.builder(Component.literal("返回"), button -> this.onClose()).width(120).build());
        this.layout.addToFooter(footer);

        checkStateImmediately();
    }

    /**
     * 每帧更新。
     * 定期检查后端状态。
     */
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
                if (this.minecraft != null) {
                    this.minecraft.execute(() -> updateBackendState(stateJson));
                }
            });
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    /**
     * 立即检查状态。
     * 如果有动态端口，则获取最新状态。
     */
    private void checkStateImmediately() {
        if (!EnderApiClient.hasDynamicPort()) {
            return;
        }
        EnderApiClient.getState().thenAccept(stateJson -> {
            if (stateJson == null) {
                return;
            }
            if (this.minecraft != null) {
                this.minecraft.execute(() -> updateBackendState(stateJson));
            }
        });
    }

    /**
     * 更新后端状态显示。
     * 解析 JSON 状态并更新 UI 文本。
     *
     * @param stateJson 状态 JSON 字符串
     */
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
                this.backendState = Component.translatable(displayKey).getString();
            } else {
                this.backendState = state;
            }
        } catch (Exception ignored) {
        }

        if (this.minecraft != null) {
            this.init(this.minecraft, this.width, this.height);
        }
    }

    /**
     * 从状态 JSON 中提取成员列表。
     *
     * @param json 状态 JSON 对象
     * @return 成员名称列表
     */
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



