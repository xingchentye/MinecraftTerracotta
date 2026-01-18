package com.multiplayer.terracotta.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.multiplayer.terracotta.fabric.FabricConfig;
import com.multiplayer.terracotta.fabric.MinecraftTerracottaClient;
import com.multiplayer.terracotta.network.TerracottaApiClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.EditGameRulesScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.world.Difficulty;

public class TerracottaDashboard extends TerracottaBaseScreen {
    private static final Gson GSON = new Gson();
    private static String lastClipboard = "";
    private static boolean wasConnected = false;
    private static JsonObject lastStateJson = null;

    private Text backendDisplay = Text.translatable("terracotta.dashboard.status.fetching");
    private boolean backendPrefixed = false;
    private long lastStateCheck = 0;
    private boolean isUiConnected = false;

    private boolean showPlayerList = false;
    private boolean showServerSettings = false;

    private String tempPath;
    private boolean tempAutoUpdate;
    private boolean tempAutoStart;

    public enum ViewMode {
        FULL,
        INGAME_INFO,
        INGAME_SETTINGS
    }

    private ViewMode currentMode = ViewMode.FULL;

    public TerracottaDashboard(Screen parent) {
        this(parent, ViewMode.FULL);
    }

    public TerracottaDashboard(Screen parent, ViewMode mode) {
        super(Text.literal("陶瓦联机中心"), parent);
        this.currentMode = mode;
        this.tempPath = FabricConfig.getExternalTerracottaPath();
        this.tempAutoUpdate = FabricConfig.isAutoUpdate();
        this.tempAutoStart = FabricConfig.isAutoStartBackend();
    }

    public void setConnected(boolean connected) {
        wasConnected = connected;
    }

    private void checkClipboardAndAutoJoin() {
        if (wasConnected || this.client == null || this.client.keyboard == null) {
            return;
        }
        try {
            String clipboard = this.client.keyboard.getClipboard();
            if (clipboard != null) {
                clipboard = clipboard.trim();
                if (!clipboard.isEmpty() && !clipboard.equals(lastClipboard)) {
                    if (clipboard.matches("^U/[A-Z0-9]{4}(-[A-Z0-9]{4}){3}$")) {
                        lastClipboard = clipboard;
                        String finalClipboard = clipboard;
                        this.client.execute(() -> this.client.setScreen(new JoinScreen(this, finalClipboard)));
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void initContent() {
        checkClipboardAndAutoJoin();

        if (!wasConnected) {
            checkStateImmediately();
        }

        if (wasConnected) {
            initConnectedContent();
        } else {
            initIdleContent();
        }
    }

    private void checkStateImmediately() {
        if (!TerracottaApiClient.hasDynamicPort()) {
            return;
        }

        TerracottaApiClient.getState().thenAccept(stateJson -> {
            if (stateJson != null) {
                try {
                    JsonObject json = GSON.fromJson(stateJson, JsonObject.class);
                    if (json.has("state")) {
                        String state = json.get("state").getAsString();
                        boolean isConnected = "host-ok".equals(state) || "guest-ok".equals(state);

                        if (wasConnected != isConnected || this.isUiConnected != isConnected) {
                            wasConnected = isConnected;
                            lastStateJson = json;
                            MinecraftClient.getInstance().execute(() -> this.init(this.client, this.width, this.height));
                        } else if (isConnected && lastStateJson == null) {
                            lastStateJson = json;
                            MinecraftClient.getInstance().execute(() -> this.init(this.client, this.width, this.height));
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    public void updateFromState(JsonObject json) {
        if (json == null) {
            return;
        }
        lastStateJson = json;
        if (json.has("state")) {
            String state = json.get("state").getAsString();
            boolean isConnected = "host-ok".equals(state) || "guest-ok".equals(state);
            wasConnected = isConnected;
            this.isUiConnected = isConnected;
        }
        this.init(this.client, this.width, this.height);
    }

    private void initConnectedContent() {
        this.isUiConnected = true;

        boolean isHost = false;
        if (lastStateJson != null && lastStateJson.has("state")) {
            String state = lastStateJson.get("state").getAsString();
            isHost = "host-ok".equals(state);
        }

        if (!isHost) {
            initGuestConnectedContent();
            return;
        }

        DirectionalLayoutWidget mainLayout = DirectionalLayoutWidget.horizontal().spacing(20);

        if (currentMode == ViewMode.FULL || currentMode == ViewMode.INGAME_INFO) {
            DirectionalLayoutWidget leftPanel = DirectionalLayoutWidget.vertical().spacing(10);
            leftPanel.getMainPositioner().alignHorizontalCenter();

            leftPanel.add(new TextWidget(Text.translatable("terracotta.dashboard.room_info"), this.textRenderer));

            String roomCode = "未知";
            if (lastStateJson != null && lastStateJson.has("room")) {
                roomCode = lastStateJson.get("room").getAsString();
            }
            String finalRoomCode = roomCode;
            leftPanel.add(new TextWidget(Text.translatable("terracotta.dashboard.copy_room_code").append(finalRoomCode), this.textRenderer));
            leftPanel.add(net.minecraft.client.gui.widget.ButtonWidget.builder(
                    Text.translatable("terracotta.dashboard.copy_room_code"),
                    button -> {
                        try {
                            this.client.keyboard.setClipboard(finalRoomCode);
                            MinecraftTerracottaClient.showToast(Text.literal("提示"), Text.literal("房间号已复制"));
                        } catch (Exception e) {
                            MinecraftTerracottaClient.showToast(Text.literal("提示"), Text.literal("复制失败，请手动复制房间号"));
                        }
                    }
            ).width(180).build());

            boolean shouldShowPlayers = (currentMode == ViewMode.INGAME_INFO) || showPlayerList;
            if (shouldShowPlayers) {
                addPlayerListToLayout(leftPanel);
            }

            mainLayout.add(leftPanel);
        }

        if (currentMode == ViewMode.FULL || currentMode == ViewMode.INGAME_SETTINGS) {
            DirectionalLayoutWidget rightPanel = DirectionalLayoutWidget.vertical().spacing(10);
            rightPanel.getMainPositioner().alignHorizontalCenter();

            rightPanel.add(new TextWidget(Text.translatable("terracotta.dashboard.room_settings"), this.textRenderer));

            if (currentMode == ViewMode.FULL) {
                rightPanel.add(new TextWidget(Text.translatable("terracotta.dashboard.core_settings"), this.textRenderer));

                TextFieldWidget pathBox = new TextFieldWidget(this.textRenderer, 180, 20, Text.literal("Path"));
                pathBox.setText(this.tempPath);
                pathBox.setMaxLength(1024);
                pathBox.setChangedListener(val -> this.tempPath = val);
                rightPanel.add(pathBox);

                Text autoUpdateLabel = Text.translatable("terracotta.dashboard.auto_update")
                        .append(FabricConfig.isAutoUpdate() ? Text.translatable("terracotta.common.on") : Text.translatable("terracotta.common.off"));
                net.minecraft.client.gui.widget.ButtonWidget autoUpdateBtn = net.minecraft.client.gui.widget.ButtonWidget.builder(autoUpdateLabel, button -> {
                    this.tempAutoUpdate = !this.tempAutoUpdate;
                    Text label = Text.translatable("terracotta.dashboard.auto_update")
                            .append(this.tempAutoUpdate ? Text.translatable("terracotta.common.on") : Text.translatable("terracotta.common.off"));
                    button.setMessage(label);
                }).width(180).build();
                rightPanel.add(autoUpdateBtn);

                Text autoStartLabel = Text.translatable("terracotta.dashboard.auto_start")
                        .append(FabricConfig.isAutoStartBackend() ? Text.translatable("terracotta.common.on") : Text.translatable("terracotta.common.off"));
                net.minecraft.client.gui.widget.ButtonWidget autoStartBtn = net.minecraft.client.gui.widget.ButtonWidget.builder(autoStartLabel, button -> {
                    this.tempAutoStart = !this.tempAutoStart;
                    Text label = Text.translatable("terracotta.dashboard.auto_start")
                            .append(this.tempAutoStart ? Text.translatable("terracotta.common.on") : Text.translatable("terracotta.common.off"));
                    button.setMessage(label);
                }).width(180).build();
                rightPanel.add(autoStartBtn);

                rightPanel.add(net.minecraft.client.gui.widget.ButtonWidget.builder(
                        Text.translatable("terracotta.dashboard.apply_settings"),
                        button -> saveConfig()
                ).width(180).build());
            }

            if (isHost) {
                rightPanel.add(new TextWidget(Text.translatable("terracotta.dashboard.host_management"), this.textRenderer));

                if (currentMode == ViewMode.FULL) {
                    Text toggleLabel = Text.translatable("terracotta.dashboard.room_info_toggle")
                            .append(showPlayerList ? Text.translatable("terracotta.common.hide") : Text.translatable("terracotta.common.show"))
                            .append(")");
                    rightPanel.add(net.minecraft.client.gui.widget.ButtonWidget.builder(toggleLabel, button -> {
                        this.showPlayerList = !this.showPlayerList;
                        this.init(this.client, this.width, this.height);
                    }).width(180).build());
                }

                rightPanel.add(net.minecraft.client.gui.widget.ButtonWidget.builder(
                        Text.translatable("terracotta.dashboard.gamerules"),
                        button -> {
                            MinecraftServer server = this.client.getServer();
                            if (server != null) {
                                this.client.setScreen(new EditGameRulesScreen(server.getGameRules(), optionalRules -> {
                                    this.client.setScreen(this);
                                }));
                            }
                        }
                ).width(180).build());

                Text serverSettingsToggle = Text.translatable("terracotta.dashboard.server_settings_toggle")
                        .append(showServerSettings ? Text.translatable("terracotta.common.hide") : Text.translatable("terracotta.common.show"))
                        .append(")");
                rightPanel.add(net.minecraft.client.gui.widget.ButtonWidget.builder(serverSettingsToggle, button -> {
                    this.showServerSettings = !this.showServerSettings;
                    this.init(this.client, this.width, this.height);
                }).width(180).build());

                if (showServerSettings) {
                    MinecraftServer server = this.client.getServer();
                    if (server != null && this.client.world != null) {
                        Difficulty currentDifficulty = this.client.world.getDifficulty();
                        Text difficultyLabel = Text.translatable("terracotta.dashboard.difficulty")
                                .append(currentDifficulty.getTranslatableName());
                        rightPanel.add(net.minecraft.client.gui.widget.ButtonWidget.builder(difficultyLabel, button -> {
                            if (this.client.world == null || this.client.getServer() == null) {
                                return;
                            }
                            Difficulty cur = this.client.world.getDifficulty();
                            int nextId = (cur.getId() + 1) % Difficulty.values().length;
                            Difficulty next = Difficulty.byId(nextId);
                            this.client.getServer().setDifficulty(next, true);
                            Text newLabel = Text.translatable("terracotta.dashboard.difficulty")
                                    .append(next.getTranslatableName());
                            button.setMessage(newLabel);
                        }).width(180).build());

                        boolean pvpEnabled = queryPvpEnabled(server);
                        Text pvpLabel = Text.translatable("terracotta.dashboard.pvp")
                                .append(pvpEnabled ? Text.translatable("terracotta.common.on") : Text.translatable("terracotta.common.off"));
                        rightPanel.add(net.minecraft.client.gui.widget.ButtonWidget.builder(pvpLabel, button -> {
                            MinecraftServer s = this.client.getServer();
                            boolean current = queryPvpEnabled(s);
                            boolean newValue = !current;
                            if (setPvpEnabled(s, newValue)) {
                                Text newLabel = Text.translatable("terracotta.dashboard.pvp")
                                        .append(newValue ? Text.translatable("terracotta.common.on") : Text.translatable("terracotta.common.off"));
                                button.setMessage(newLabel);
                            } else {
                                MinecraftTerracottaClient.showToast(Text.literal("提示"), Text.literal("当前版本暂不支持在游戏内修改 PVP，请在 server.properties 中修改"));
                            }
                        }).width(180).build());
                    }
                }
            }

            Text closeLabel = isHost
                    ? Text.translatable("terracotta.dashboard.close_room")
                    : Text.translatable("terracotta.dashboard.disconnect_network");
            rightPanel.add(net.minecraft.client.gui.widget.ButtonWidget.builder(closeLabel, button -> {
                TerracottaApiClient.setIdle();
                wasConnected = false;
                this.isUiConnected = false;
                this.onClose();
            }).width(180).build());

            mainLayout.add(rightPanel);
        }

        this.layout.addBody(mainLayout);

        if (currentMode == ViewMode.FULL) {
            this.layout.addFooter(net.minecraft.client.gui.widget.ButtonWidget.builder(
                    Text.translatable("terracotta.common.disconnect"),
                    button -> {
                        TerracottaApiClient.setIdle();
                        wasConnected = false;
                        this.isUiConnected = false;
                        this.init(this.client, this.width, this.height);
                    }
            ).width(200).build());
        } else {
            this.layout.addFooter(net.minecraft.client.gui.widget.ButtonWidget.builder(
                    Text.translatable("terracotta.common.back"),
                    button -> this.onClose()
            ).width(200).build());
        }
    }

    private void initGuestConnectedContent() {
        DirectionalLayoutWidget content = DirectionalLayoutWidget.vertical().spacing(10);
        content.getMainPositioner().alignHorizontalCenter();

        content.add(new TextWidget(Text.translatable("terracotta.dashboard.player_list"), this.textRenderer));

        addPlayerListToLayout(content);

        this.layout.addBody(content);

        DirectionalLayoutWidget footerLayout = DirectionalLayoutWidget.horizontal().spacing(10);

        if (lastStateJson != null && lastStateJson.has("url")) {
            String url = lastStateJson.get("url").getAsString();
            footerLayout.add(net.minecraft.client.gui.widget.ButtonWidget.builder(
                    Text.translatable("terracotta.common.join"),
                    b -> connectToServer(url)
            ).width(80).build());
            footerLayout.add(net.minecraft.client.gui.widget.ButtonWidget.builder(
                    Text.translatable("terracotta.common.enter_server"),
                    b -> connectToServer(url)
            ).width(100).build());
        }

        footerLayout.add(net.minecraft.client.gui.widget.ButtonWidget.builder(
                Text.translatable("terracotta.common.disconnect"),
                b -> {
                    TerracottaApiClient.setIdle();
                    wasConnected = false;
                    this.isUiConnected = false;
                    this.onClose();
                }
        ).width(80).build());

        footerLayout.add(net.minecraft.client.gui.widget.ButtonWidget.builder(
                Text.translatable("terracotta.common.back"),
                b -> this.onClose()
        ).width(80).build());

        this.layout.addFooter(footerLayout);
    }

    private void addPlayerListToLayout(DirectionalLayoutWidget layout) {
        if (lastStateJson == null) {
            return;
        }

        if (lastStateJson.has("profiles")) {
            try {
                JsonArray profiles = lastStateJson.getAsJsonArray("profiles");
                if (profiles.size() > 0) {
                    Text header = Text.translatable("terracotta.dashboard.current_players_prefix")
                            .append(String.valueOf(profiles.size()))
                            .append(Text.translatable("terracotta.dashboard.current_players_suffix"));
                    layout.add(new TextWidget(header, this.textRenderer));
                    for (JsonElement p : profiles) {
                        JsonObject profile = p.getAsJsonObject();
                        String name = profile.get("name").getAsString();
                        String kind = profile.has("kind") ? profile.get("kind").getAsString() : "";
                        Text display = Text.literal(name);
                        if ("HOST".equals(kind)) {
                            display = display.copy().append(Text.translatable("terracotta.dashboard.host_label"));
                        }
                        layout.add(new TextWidget(display, this.textRenderer));
                    }
                }
            } catch (Exception ignored) {
            }
        } else if (lastStateJson.has("players")) {
            try {
                JsonArray players = lastStateJson.getAsJsonArray("players");
                if (players.size() > 0) {
                    Text header = Text.translatable("terracotta.dashboard.current_players_prefix")
                            .append(String.valueOf(players.size()))
                            .append(Text.translatable("terracotta.dashboard.current_players_suffix"));
                    layout.add(new TextWidget(header, this.textRenderer));
                    for (JsonElement p : players) {
                        String pName = p.getAsString();
                        layout.add(new TextWidget(Text.literal(pName), this.textRenderer));
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private boolean queryPvpEnabled(MinecraftServer server) {
        if (server == null) {
            return true;
        }
        try {
            java.lang.reflect.Method getter = server.getClass().getMethod("isPvpEnabled");
            Object result = getter.invoke(server);
            if (result instanceof Boolean b) {
                return b;
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    private boolean setPvpEnabled(MinecraftServer server, boolean value) {
        if (server == null) {
            return false;
        }
        try {
            java.lang.reflect.Method setter = server.getClass().getMethod("setPvpEnabled", boolean.class);
            setter.invoke(server, value);
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }


    private void saveConfig() {
        FabricConfig.setExternalTerracottaPath(this.tempPath);
        FabricConfig.setAutoUpdate(this.tempAutoUpdate);
        FabricConfig.setAutoStartBackend(this.tempAutoStart);
    }

    private void connectToServer(String connectUrl) {
        try {
            ServerAddress address = ServerAddress.parse(connectUrl);
            ServerInfo info = new ServerInfo("Terracotta Server", connectUrl, ServerInfo.ServerType.OTHER);
            ConnectScreen.connect(this.parent, MinecraftClient.getInstance(), address, info, false, null);
        } catch (Exception ignored) {
        }
    }

    private void initIdleContent() {
        this.isUiConnected = false;

        DirectionalLayoutWidget contentLayout = DirectionalLayoutWidget.vertical().spacing(15);

        if (this.currentMode == ViewMode.FULL) {
            contentLayout.add(net.minecraft.client.gui.widget.ButtonWidget.builder(
                    Text.translatable("terracotta.dashboard.join_room"),
                    button -> {
                        if (TerracottaApiClient.hasDynamicPort()) {
                            this.client.setScreen(new JoinScreen(this));
                        } else {
                            this.client.setScreen(new StartupScreen(this.parent));
                        }
                    }
            ).width(200).build());

            contentLayout.add(net.minecraft.client.gui.widget.ButtonWidget.builder(
                    Text.translatable("terracotta.common.settings"),
                    button -> this.client.setScreen(new TerracottaConfigScreen(this))
            ).width(200).build());
        } else {
            contentLayout.add(new TextWidget(Text.translatable("terracotta.dashboard.status_not_connected"), this.textRenderer));
        }

        this.layout.addBody(contentLayout);

        if (this.currentMode == ViewMode.FULL) {
            this.layout.addFooter(net.minecraft.client.gui.widget.ButtonWidget.builder(
                    Text.translatable("terracotta.common.exit"),
                    button -> this.onClose()
            ).width(200).build());
        } else {
            this.layout.addFooter(net.minecraft.client.gui.widget.ButtonWidget.builder(
                    Text.translatable("terracotta.common.back"),
                    button -> this.onClose()
            ).width(200).build());
        }
    }

    @Override
    public void tick() {
        super.tick();

        long now = System.currentTimeMillis();
        if (now - lastStateCheck > 1000) {
            lastStateCheck = now;

            if (TerracottaApiClient.hasDynamicPort()) {
                TerracottaApiClient.getState().thenAccept(this::updateBackendState);
            } else {
                this.backendDisplay = Text.translatable("terracotta.state.not_started");
                this.backendPrefixed = true;
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        if (this.currentMode == ViewMode.FULL || this.currentMode == ViewMode.INGAME_INFO) {
            int textY = this.layout.getHeaderHeight() + 5;
            Text display = this.backendPrefixed
                    ? Text.translatable("terracotta.dashboard.status_prefix").append(this.backendDisplay)
                    : this.backendDisplay;
            context.drawCenteredTextWithShadow(this.textRenderer, display, this.width / 2, textY, 0xAAAAAA);
        }
    }

    private void updateBackendState(String stateJson) {
        if (stateJson == null) {
            return;
        }
        try {
            JsonObject json = GSON.fromJson(stateJson, JsonObject.class);
            String state = "";
            if (json.has("state")) {
                state = json.get("state").getAsString();
            }

            String displayKey = "terracotta.state.idle";

            switch (state) {
                case "idle":
                    displayKey = "terracotta.state.idle";
                    break;
                case "host-starting":
                    displayKey = "terracotta.state.host_starting";
                    break;
                case "host-scanning":
                    displayKey = "terracotta.state.host_scanning";
                    break;
                case "host-ok":
                    displayKey = "terracotta.state.connected";
                    break;
                case "guest-starting":
                    displayKey = "terracotta.state.guest_starting";
                    break;
                case "guest-connecting":
                    displayKey = "terracotta.state.guest_connecting";
                    break;
                case "guest-ok":
                    displayKey = "terracotta.state.connected";
                    break;
                case "waiting":
                    displayKey = "terracotta.state.waiting";
                    break;
                default:
                    displayKey = null;
                    break;
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
    }
}
