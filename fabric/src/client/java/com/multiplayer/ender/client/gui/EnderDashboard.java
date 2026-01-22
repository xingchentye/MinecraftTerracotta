package com.multiplayer.ender.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.multiplayer.ender.fabric.FabricConfig;
import com.multiplayer.ender.fabric.MinecraftEnderClient;
import com.multiplayer.ender.network.EnderApiClient;
import com.multiplayer.ender.logic.ProcessLauncher;
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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.util.math.BlockPos;

import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.Selectable;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import java.util.List;
import net.minecraft.client.gui.Element;

/**
 * 末影联机仪表盘（Fabric）。
 * 模组的核心 GUI，整合了房间状态、玩家列表、设置管理等功能。
 */
public class EnderDashboard extends EnderBaseScreen {
    private static final Gson GSON = new Gson();
    private static String lastClipboard = "";
    private static boolean wasConnected = false;
    private static JsonObject lastStateJson = null;

    private Text backendDisplay = Text.translatable("ender.dashboard.status.fetching");
    private boolean backendPrefixed = false;
    private long lastStateCheck = 0;
    private boolean isUiConnected = false;

    private boolean showPlayerList = true;
    private boolean showServerSettings = false;

    private String tempPath;
    private boolean tempAutoUpdate;
    private boolean tempAutoStart;
    private long lastRoomSync = 0;
    private int lastPingMs = -1;
    private String networkQualityLabel = "良好";
    private String roomName = "未命名房间";
    private String roomRemark = "";
    private int currentPlayers = 0;
    private int maxPlayers = 0;
    private String visitorPermission = "可交互";
    private boolean whitelistEnabled = false;
    private JsonArray whitelist = new JsonArray();
    private JsonArray blacklist = new JsonArray();
    private JsonArray muteList = new JsonArray();
    private JsonArray operationLogs = new JsonArray();
    private boolean allowCheats = false;
    private int spawnProtection = 16;
    private boolean keepInventory = false;
    private boolean fireSpread = true;
    private boolean mobSpawning = true;
    private String timeControl = "cycle";
    private boolean weatherLock = false;
    private int respawnX = 0;
    private int respawnY = 0;
    private int respawnZ = 0;
    private int worldBorderCenterX = 0;
    private int worldBorderCenterZ = 0;
    private int worldBorderRadius = 0;
    private boolean autoReconnect = true;
    private int reconnectRetries = 3;
    private boolean hostMigration = false;
    private String backendVersion = "当前";
    private String updatePolicy = "立即";
    private String logLevel = "INFO";
    private int cpuLimit = 0;
    private int memoryLimit = 0;
    private JsonArray backendVersions = new JsonArray();
    private boolean roomStateDirty = false;
    private long lastPushTime = 0;
    private PlayerListWidget playerListWidget;

    public enum ViewMode {
        FULL,
        INGAME_INFO,
        INGAME_SETTINGS
    }

    private ViewMode currentMode = ViewMode.FULL;
    public enum RoomPage {
        OVERVIEW,
        PERMISSIONS,
        RULES,
        WORLD,
        NETWORK,
        BACKEND
    }
    private RoomPage currentRoomPage = RoomPage.OVERVIEW;
    private boolean roomPageRefreshRequested = false;

    public EnderDashboard(Screen parent) {
        this(parent, ViewMode.FULL);
    }

    public EnderDashboard(Screen parent, ViewMode mode) {
        super(Text.literal("末影联机中心"), parent);
        this.currentMode = mode;
        this.tempPath = FabricConfig.getExternalEnderPath();
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
                    
                    if (clipboard.matches("^U/[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$")) {
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
        
        EnderApiClient.State realState = EnderApiClient.getCurrentState();
        if (realState == EnderApiClient.State.IDLE) {
            wasConnected = false;
            lastStateJson = null;
        } else if (realState == EnderApiClient.State.HOSTING || realState == EnderApiClient.State.JOINING) {
            wasConnected = true;
            
            if (lastStateJson != null && lastStateJson.has("state")) {
                String cachedState = lastStateJson.get("state").getAsString();
                boolean cachedIsHost = "host-ok".equals(cachedState);
                boolean realIsHost = (realState == EnderApiClient.State.HOSTING);
                if (cachedIsHost != realIsHost) {
                    lastStateJson = null;
                }
            } else {
                lastStateJson = null;
            }
        }

        this.playerListWidget = null;
        checkClipboardAndAutoJoin();

        if (lastStateJson != null) {
            if (lastStateJson.has("state")) {
                String s = lastStateJson.get("state").getAsString();
                boolean connected = "host-ok".equals(s) || "guest-ok".equals(s);
                if (wasConnected != connected) {
                    wasConnected = connected;
                }
            } else if (lastStateJson.has("status") && "IDLE".equals(lastStateJson.get("status").getAsString())) {
                wasConnected = false;
            }
        }

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
        if (!EnderApiClient.hasDynamicPort()) {
            return;
        }

        EnderApiClient.getState().thenAccept(stateJson -> {
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
                    } else if (json.has("status") && "IDLE".equals(json.get("status").getAsString())) {
                        if (wasConnected) {
                            wasConnected = false;
                            this.isUiConnected = false;
                            lastStateJson = null;
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
        } else if (json.has("status") && "IDLE".equals(json.get("status").getAsString())) {
            wasConnected = false;
            this.isUiConnected = false;
        }
        this.init(this.client, this.width, this.height);
    }

    private void initConnectedContent() {
        this.isUiConnected = true;

        boolean isHost = false;
        boolean isStarting = false;
        
        if (lastStateJson != null) {
            if (lastStateJson.has("state")) {
                String state = lastStateJson.get("state").getAsString();
                isHost = "host-ok".equals(state);
                isStarting = "host-starting".equals(state) || "guest-starting".equals(state);
            }
        } else {
            EnderApiClient.State state = EnderApiClient.getCurrentState();
            isHost = (state == EnderApiClient.State.HOSTING);
            isStarting = (state == EnderApiClient.State.HOSTING_STARTING || state == EnderApiClient.State.JOINING_STARTING);
        }
        
        if (isStarting) {
            
            DirectionalLayoutWidget loadingLayout = DirectionalLayoutWidget.vertical().spacing(10);
            loadingLayout.getMainPositioner().alignHorizontalCenter();
            loadingLayout.add(new TextWidget(Text.translatable("ender.host.status.requesting"), this.textRenderer));
            this.layout.addBody(loadingLayout);
            return;
        }

        if (!isHost) {
            initGuestConnectedContent();
            return;
        }
        initRoomManagementContent();

        if (currentMode == ViewMode.FULL) {
            this.layout.addFooter(net.minecraft.client.gui.widget.ButtonWidget.builder(
                    Text.translatable("ender.common.disconnect"),
                    button -> {
                        EnderApiClient.setIdle();
                        new Thread(ProcessLauncher::stop, "Ender-Stopper").start();
                        wasConnected = false;
                        this.isUiConnected = false;
                        this.init(this.client, this.width, this.height);
                    }
            ).width(200).build());
        } else {
            this.layout.addFooter(net.minecraft.client.gui.widget.ButtonWidget.builder(
                    Text.translatable("ender.common.back"),
                    button -> this.onClose()
            ).width(200).build());
        }
    }

    private void initGuestConnectedContent() {
        
        DirectionalLayoutWidget footerLayout = DirectionalLayoutWidget.horizontal().spacing(10);

        footerLayout.add(net.minecraft.client.gui.widget.ButtonWidget.builder(
                Text.translatable("ender.common.disconnect"),
                b -> {
                    EnderApiClient.setIdle();
                    new Thread(ProcessLauncher::stop, "Ender-Stopper").start();
                    wasConnected = false;
                    this.isUiConnected = false;
                    this.onClose();
                }
        ).width(80).build());

        footerLayout.add(net.minecraft.client.gui.widget.ButtonWidget.builder(
                Text.translatable("ender.common.back"),
                b -> this.onClose()
        ).width(80).build());

        this.layout.addFooter(footerLayout);

        
        int headerHeight = this.layout.getHeaderHeight() + 10;
        int footerHeight = this.layout.getFooterHeight() + 10;
        
        
        int titleY = headerHeight;
        Text title = Text.translatable("ender.dashboard.player_list");
        TextWidget titleWidget = new TextWidget(0, titleY, this.width, 20, title, this.textRenderer);
        titleWidget.alignCenter();
        this.addDrawableChild(titleWidget);

        
        int listTop = titleY + 25;
        int listBottom = this.height - footerHeight - 30;
        int listWidth = 300;
        int listX = (this.width - listWidth) / 2;
        
        PlayerListWidget listWidget = new PlayerListWidget(this.client, listWidth, listBottom - listTop, listTop, 24);
        listWidget.setX(listX);
        listWidget.updateEntries(lastStateJson);
        this.playerListWidget = listWidget;
        this.addDrawableChild(listWidget);

        net.minecraft.client.gui.widget.ButtonWidget joinGameBtn = net.minecraft.client.gui.widget.ButtonWidget.builder(
                Text.literal("加入游戏"),
                b -> {
                    String ip = EnderApiClient.getHostIp();
                    if (ip != null) {
                        int port = EnderApiClient.getRemoteMcPort();
                        ServerInfo serverInfo = new ServerInfo("Ender Room", ip + ":" + port, ServerInfo.ServerType.OTHER);
                        ConnectScreen.connect(this, this.client, ServerAddress.parse(serverInfo.address), serverInfo, false, null);
                    }
                }
        ).width(200).build();
        joinGameBtn.setPosition((this.width - 200) / 2, listBottom + 5);
        this.addDrawableChild(joinGameBtn);
    }

    private void addPlayerListToLayout(DirectionalLayoutWidget layout) {
        if (lastStateJson == null) {
            return;
        }

        if (lastStateJson.has("profiles")) {
            try {
                JsonArray profiles = lastStateJson.getAsJsonArray("profiles");
                if (profiles.size() > 0) {
                    Text header = Text.translatable("ender.dashboard.current_players_prefix")
                            .append(String.valueOf(profiles.size()))
                            .append(Text.translatable("ender.dashboard.current_players_suffix"));
                    layout.add(new TextWidget(header, this.textRenderer));
                    for (JsonElement p : profiles) {
                        JsonObject profile = p.getAsJsonObject();
                        String name = profile.get("name").getAsString();
                        String kind = profile.has("kind") ? profile.get("kind").getAsString() : "";
                        Text display = Text.literal(name);
                        if ("HOST".equals(kind)) {
                            display = display.copy().append(Text.translatable("ender.dashboard.host_label"));
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
                    Text header = Text.translatable("ender.dashboard.current_players_prefix")
                            .append(String.valueOf(players.size()))
                            .append(Text.translatable("ender.dashboard.current_players_suffix"));
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
        FabricConfig.setExternalEnderPath(this.tempPath);
        FabricConfig.setAutoUpdate(this.tempAutoUpdate);
        FabricConfig.setAutoStartBackend(this.tempAutoStart);
    }

    private void connectToServer(String connectUrl) {
        try {
            ServerAddress address = ServerAddress.parse(connectUrl);
            ServerInfo info = new ServerInfo("Ender Server", connectUrl, ServerInfo.ServerType.OTHER);
            ConnectScreen.connect(this.parent, MinecraftClient.getInstance(), address, info, false, null);
        } catch (Exception ignored) {
        }
    }

    private void initIdleContent() {
        this.isUiConnected = false;

        DirectionalLayoutWidget contentLayout = DirectionalLayoutWidget.vertical().spacing(15);

        if (this.currentMode == ViewMode.FULL) {
            contentLayout.add(net.minecraft.client.gui.widget.ButtonWidget.builder(
                    Text.translatable("ender.dashboard.join_room"),
                    button -> {
                        if (EnderApiClient.hasDynamicPort()) {
                            this.client.setScreen(new JoinScreen(this));
                        } else {
                            this.client.setScreen(new StartupScreen(this.parent));
                        }
                    }
            ).width(200).build());

            contentLayout.add(net.minecraft.client.gui.widget.ButtonWidget.builder(
                    Text.translatable("ender.common.settings"),
                    button -> this.client.setScreen(new EnderConfigScreen(this))
            ).width(200).build());
        } else {
            contentLayout.add(new TextWidget(Text.translatable("ender.dashboard.status_not_connected"), this.textRenderer));
        }

        this.layout.addBody(contentLayout);

        if (this.currentMode == ViewMode.FULL) {
            this.layout.addFooter(net.minecraft.client.gui.widget.ButtonWidget.builder(
                    Text.translatable("ender.common.exit"),
                    button -> this.onClose()
            ).width(200).build());
        } else {
            this.layout.addFooter(net.minecraft.client.gui.widget.ButtonWidget.builder(
                    Text.translatable("ender.common.back"),
                    button -> this.onClose()
            ).width(200).build());
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (roomPageRefreshRequested) {
            roomPageRefreshRequested = false;
            this.clearChildren();
            this.init(this.client, this.width, this.height);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastStateCheck > 1000) {
            lastStateCheck = now;

            if (EnderApiClient.hasDynamicPort()) {
                long startedAt = System.currentTimeMillis();
                EnderApiClient.getState().thenAccept(stateJson -> updateBackendState(stateJson, startedAt));
            } else {
                this.backendDisplay = Text.translatable("ender.state.not_started");
                this.backendPrefixed = true;
            }
        }
        if (isHostConnected() && now - lastRoomSync > 500) {
            lastRoomSync = now;
            if (roomStateDirty) {
                EnderApiClient.updateRoomManagementState(buildRoomManagementStateJson().toString());
                roomStateDirty = false;
                lastPushTime = System.currentTimeMillis();
                applyRoomManagementStateToServer();
            } else if (System.currentTimeMillis() - lastPushTime > 2000) {
                EnderApiClient.getRoomManagementState().thenAccept(this::updateRoomManagementStateFromString);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        if (this.currentMode == ViewMode.FULL || this.currentMode == ViewMode.INGAME_INFO) {
            int textY = this.layout.getHeaderHeight() + 5;
            Text display = this.backendPrefixed
                    ? Text.translatable("ender.dashboard.status_prefix").append(this.backendDisplay)
                    : this.backendDisplay;
            context.drawCenteredTextWithShadow(this.textRenderer, display, this.width / 2, textY, 0xAAAAAA);
        }
    }

    private void updateBackendState(String stateJson, long startedAt) {
        if (stateJson == null) {
            return;
        }
        try {
            JsonObject json = GSON.fromJson(stateJson, JsonObject.class);
            String state = "";
            if (json.has("state")) {
                state = json.get("state").getAsString();
            } else if (json.has("status")) {
                String status = json.get("status").getAsString();
                if ("IDLE".equals(status)) {
                    state = "idle";
                } else if ("ERROR".equals(status)) {
                    state = "error";
                } else if ("HOSTING_STARTING".equals(status)) {
                    state = "host-starting";
                } else if ("JOINING_STARTING".equals(status)) {
                    state = "guest-starting";
                } else if ("HOSTING".equals(status)) {
                    state = "host-ok";
                } else if ("JOINING".equals(status)) {
                    state = "guest-ok";
                }
            }
            boolean isConnected = "host-ok".equals(state) || "guest-ok".equals(state);

            boolean needsInit = wasConnected != isConnected || this.isUiConnected != isConnected;
            if (isConnected) {
                lastStateJson = json;
                if (this.client != null) {
                    this.client.execute(() -> {
                        if (this.playerListWidget != null) {
                            this.playerListWidget.updateEntries(lastStateJson);
                        }
                    });
                }
            } else {
                lastStateJson = null;
            }
            if (needsInit) {
                wasConnected = isConnected;
                if (this.client != null) {
                    this.client.execute(() -> this.init(this.client, this.width, this.height));
                }
            }

            String displayKey = "ender.state.idle";

            switch (state) {
                case "idle":
                    displayKey = "ender.state.idle";
                    break;
                case "host-starting":
                    displayKey = "ender.state.host_starting";
                    break;
                case "host-scanning":
                    displayKey = "ender.state.host_scanning";
                    break;
                case "host-ok":
                    displayKey = "ender.state.connected";
                    break;
                case "guest-starting":
                    displayKey = "ender.state.guest_starting";
                    break;
                case "guest-connecting":
                    displayKey = "ender.state.guest_connecting";
                    break;
                case "guest-ok":
                    displayKey = "ender.state.connected";
                    break;
                case "waiting":
                    displayKey = "ender.state.waiting";
                    break;
                case "error":
                    if (json.has("error")) {
                        String err = json.get("error").getAsString();
                        this.backendDisplay = Text.literal("错误: " + err);
                        this.backendPrefixed = false;
                        displayKey = null;
                    } else {
                        this.backendDisplay = Text.literal("错误");
                        this.backendPrefixed = true;
                        displayKey = null;
                    }
                    break;
                default:
                    displayKey = null;
                    break;
            }

            if (displayKey != null) {
                this.backendDisplay = Text.translatable(displayKey);
                this.backendPrefixed = true;
            } else if (!"error".equals(state)) {
                this.backendDisplay = Text.literal(state);
                this.backendPrefixed = true;
            }
            if (isConnected) {
                long cost = Math.max(0, System.currentTimeMillis() - startedAt);
                this.lastPingMs = (int) cost;
                if (cost <= 80) {
                    this.networkQualityLabel = "优秀";
                } else if (cost <= 150) {
                    this.networkQualityLabel = "良好";
                } else if (cost <= 250) {
                    this.networkQualityLabel = "一般";
                } else {
                    this.networkQualityLabel = "较差";
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isHostConnected() {
        if (lastStateJson == null) {
            return false;
        }
        if (lastStateJson.has("state")) {
            return "host-ok".equals(lastStateJson.get("state").getAsString());
        }
        return false;
    }

    private void updateRoomManagementStateFromString(String stateJson) {
        if (stateJson == null || roomStateDirty) {
            return;
        }
        try {
            JsonObject json = GSON.fromJson(stateJson, JsonObject.class);
            if (json == null) {
                return;
            }
            boolean changed = mergeRoomManagementState(json);
            if (changed) {
                applyRoomManagementStateToServer();
            }
            if (changed && this.client != null) {
                this.client.execute(() -> this.init(this.client, this.width, this.height));
            }
        } catch (Exception ignored) {
        }
    }

    private boolean mergeRoomManagementState(JsonObject json) {
        boolean changed = false;
        if (json.has("room_name")) {
            String v = json.get("room_name").getAsString();
            if (!v.equals(this.roomName)) {
                this.roomName = v;
                changed = true;
            }
        }
        if (json.has("room_remark")) {
            String v = json.get("room_remark").getAsString();
            if (!v.equals(this.roomRemark)) {
                this.roomRemark = v;
                changed = true;
            }
        }
        if (json.has("visitor_permission")) {
            String v = json.get("visitor_permission").getAsString();
            if (!v.equals(this.visitorPermission)) {
                this.visitorPermission = v;
                changed = true;
            }
        }
        if (json.has("whitelist_enabled")) {
            boolean v = json.get("whitelist_enabled").getAsBoolean();
            if (v != this.whitelistEnabled) {
                this.whitelistEnabled = v;
                changed = true;
            }
        }
        if (json.has("whitelist")) {
            JsonArray v = json.getAsJsonArray("whitelist");
            this.whitelist = v == null ? new JsonArray() : v;
            changed = true;
        }
        if (json.has("blacklist")) {
            JsonArray v = json.getAsJsonArray("blacklist");
            this.blacklist = v == null ? new JsonArray() : v;
            changed = true;
        }
        if (json.has("mute_list")) {
            JsonArray v = json.getAsJsonArray("mute_list");
            this.muteList = v == null ? new JsonArray() : v;
            changed = true;
        }
        if (json.has("operation_logs")) {
            JsonArray v = json.getAsJsonArray("operation_logs");
            this.operationLogs = v == null ? new JsonArray() : v;
            changed = true;
        }
        if (json.has("allow_cheats")) {
            this.allowCheats = json.get("allow_cheats").getAsBoolean();
            changed = true;
        }
        if (json.has("allow_pvp")) {
            this.pvpAllowed = json.get("allow_pvp").getAsBoolean();
            changed = true;
        }
        if (json.has("spawn_protection")) {
            this.spawnProtection = json.get("spawn_protection").getAsInt();
            changed = true;
        }
        if (json.has("keep_inventory")) {
            this.keepInventory = json.get("keep_inventory").getAsBoolean();
            changed = true;
        }
        if (json.has("fire_spread")) {
            this.fireSpread = json.get("fire_spread").getAsBoolean();
            changed = true;
        }
        if (json.has("mob_spawning")) {
            this.mobSpawning = json.get("mob_spawning").getAsBoolean();
            changed = true;
        }
        if (json.has("time_lock")) {
            this.timeControl = json.get("time_lock").getAsString();
            changed = true;
        }
        if (json.has("weather_lock")) {
            this.weatherLock = json.get("weather_lock").getAsBoolean();
            changed = true;
        }
        if (json.has("respawn_x")) {
            this.respawnX = json.get("respawn_x").getAsInt();
            changed = true;
        }
        if (json.has("respawn_y")) {
            this.respawnY = json.get("respawn_y").getAsInt();
            changed = true;
        }
        if (json.has("respawn_z")) {
            this.respawnZ = json.get("respawn_z").getAsInt();
            changed = true;
        }
        if (json.has("world_border_center_x")) {
            this.worldBorderCenterX = json.get("world_border_center_x").getAsInt();
            changed = true;
        }
        if (json.has("world_border_center_z")) {
            this.worldBorderCenterZ = json.get("world_border_center_z").getAsInt();
            changed = true;
        }
        if (json.has("world_border_radius")) {
            this.worldBorderRadius = json.get("world_border_radius").getAsInt();
            changed = true;
        }
        if (json.has("auto_reconnect")) {
            this.autoReconnect = json.get("auto_reconnect").getAsBoolean();
            changed = true;
        }
        if (json.has("reconnect_retries")) {
            this.reconnectRetries = json.get("reconnect_retries").getAsInt();
            changed = true;
        }
        if (json.has("host_migration")) {
            this.hostMigration = json.get("host_migration").getAsBoolean();
            changed = true;
        }
        if (json.has("backend_version")) {
            this.backendVersion = json.get("backend_version").getAsString();
            changed = true;
        }
        if (json.has("update_policy")) {
            this.updatePolicy = json.get("update_policy").getAsString();
            changed = true;
        }
        if (json.has("log_level")) {
            this.logLevel = json.get("log_level").getAsString();
            changed = true;
        }
        if (json.has("cpu_limit")) {
            this.cpuLimit = json.get("cpu_limit").getAsInt();
            changed = true;
        }
        if (json.has("memory_limit")) {
            this.memoryLimit = json.get("memory_limit").getAsInt();
            changed = true;
        }
        if (json.has("backend_versions")) {
            JsonArray v = json.getAsJsonArray("backend_versions");
            this.backendVersions = v == null ? new JsonArray() : v;
            changed = true;
        }
        return changed;
    }

    private JsonObject buildRoomManagementStateJson() {
        JsonObject json = new JsonObject();
        json.addProperty("room_name", roomName);
        json.addProperty("room_remark", roomRemark);
        json.addProperty("visitor_permission", visitorPermission);
        json.addProperty("whitelist_enabled", whitelistEnabled);
        json.add("whitelist", whitelist == null ? new JsonArray() : whitelist);
        json.add("blacklist", blacklist == null ? new JsonArray() : blacklist);
        json.add("mute_list", muteList == null ? new JsonArray() : muteList);
        json.add("operation_logs", operationLogs == null ? new JsonArray() : operationLogs);
        json.addProperty("allow_cheats", allowCheats);
        json.addProperty("allow_pvp", pvpAllowed);
        json.addProperty("spawn_protection", spawnProtection);
        json.addProperty("keep_inventory", keepInventory);
        json.addProperty("fire_spread", fireSpread);
        json.addProperty("mob_spawning", mobSpawning);
        json.addProperty("time_lock", timeControl);
        json.addProperty("weather_lock", weatherLock);
        json.addProperty("respawn_x", respawnX);
        json.addProperty("respawn_y", respawnY);
        json.addProperty("respawn_z", respawnZ);
        json.addProperty("world_border_center_x", worldBorderCenterX);
        json.addProperty("world_border_center_z", worldBorderCenterZ);
        json.addProperty("world_border_radius", worldBorderRadius);
        json.addProperty("auto_reconnect", autoReconnect);
        json.addProperty("reconnect_retries", reconnectRetries);
        json.addProperty("host_migration", hostMigration);
        json.addProperty("backend_version", backendVersion);
        json.addProperty("update_policy", updatePolicy);
        json.addProperty("log_level", logLevel);
        json.addProperty("cpu_limit", cpuLimit);
        json.addProperty("memory_limit", memoryLimit);
        json.add("backend_versions", backendVersions == null ? new JsonArray() : backendVersions);
        return json;
    }

    private void initRoomManagementContent() {
        
        if (roomRemark == null || roomRemark.isEmpty()) {
            JsonObject state = EnderApiClient.getRoomManagementStateSync();
            if (state != null && state.has("room_remark")) {
                roomRemark = state.get("room_remark").getAsString();
            }
        }

        
        
        int menuWidth = 120;
        int spacing = 30; 
        int headerHeight = this.layout.getHeaderHeight() + 10;
        
        
        
        
        
        
        
        
        
        
        
        DirectionalLayoutWidget menu = DirectionalLayoutWidget.vertical().spacing(6);
        menu.getMainPositioner().alignLeft(); 
        for (RoomPage page : RoomPage.values()) {
            addRoomPageMenuButton(menu, page, menuWidth);
        }
        
        
        menu.refreshPositions();
        menu.setX(10);
        menu.setY(headerHeight);
        
        
        menu.forEachChild(this::addDrawableChild);

        DirectionalLayoutWidget pageContent = DirectionalLayoutWidget.vertical().spacing(12);
        pageContent.getMainPositioner().alignHorizontalCenter();
        
        switch (currentRoomPage) {
            case OVERVIEW -> addOverviewPage(pageContent);
            case PERMISSIONS -> addPermissionsPage(pageContent);
            case RULES -> addRulesPage(pageContent);
            case WORLD -> addWorldPage(pageContent);
            case NETWORK -> addNetworkPage(pageContent);
            case BACKEND -> addBackendPage(pageContent);
        }
        
        
        
        int contentXStart = menuWidth + spacing;
        int remainingWidth = this.width - contentXStart - 10;
        
        pageContent.refreshPositions();
        int contentActualWidth = pageContent.getWidth();
        int centeredOffsetX = (remainingWidth - contentActualWidth) / 2;
        
        pageContent.setX(contentXStart + centeredOffsetX);
        pageContent.setY(headerHeight);
        
        pageContent.forEachChild(this::addDrawableChild);

        
        
        if (currentMode == ViewMode.FULL) {
            this.layout.addFooter(net.minecraft.client.gui.widget.ButtonWidget.builder(
                    Text.translatable("ender.common.disconnect"),
                    button -> {
                        EnderApiClient.setIdle();
                        new Thread(ProcessLauncher::stop, "Ender-Stopper").start();
                        wasConnected = false;
                        this.isUiConnected = false;
                        this.init(this.client, this.width, this.height);
                    }
            ).width(200).build());
        } else {
            this.layout.addFooter(net.minecraft.client.gui.widget.ButtonWidget.builder(
                    Text.translatable("ender.common.back"),
                    button -> this.onClose()
            ).width(200).build());
        }
    }

    private String getRoomPageTitle(RoomPage page) {
        return switch (page) {
            case OVERVIEW -> "房间概览";
            case PERMISSIONS -> "权限与访客";
            case RULES -> "规则与玩法";
            case WORLD -> "世界与边界";
            case NETWORK -> "网络与容灾";
            case BACKEND -> "后端与性能";
        };
    }

    private void addRoomPageMenuButton(DirectionalLayoutWidget menu, RoomPage page, int width) {
        var button = net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal(getRoomPageTitle(page)), b -> switchToPage(page)).width(width).build();
        if (page == currentRoomPage) {
            button.active = false;
        }
        menu.add(button);
    }

    private void switchToPage(RoomPage page) {
        this.currentRoomPage = page;
        this.roomPageRefreshRequested = true;
    }

    private void addOverviewPage(DirectionalLayoutWidget content) {
        DirectionalLayoutWidget roomInfo = DirectionalLayoutWidget.vertical().spacing(6);
        roomInfo.getMainPositioner().alignHorizontalCenter();
        roomInfo.add(new TextWidget(Text.literal(" 房间概览 "), this.textRenderer));

        String roomCode = "未知";
        if (lastStateJson != null && lastStateJson.has("room")) {
            roomCode = lastStateJson.get("room").getAsString();
        }
        String finalRoomCode = roomCode;
        int playerCount = 0;
        if (lastStateJson != null) {
            if (lastStateJson.has("profiles")) {
                playerCount = lastStateJson.getAsJsonArray("profiles").size();
            } else if (lastStateJson.has("players")) {
                playerCount = lastStateJson.getAsJsonArray("players").size();
            }
        }
        roomInfo.add(new TextWidget(Text.literal("房间号: " + finalRoomCode), this.textRenderer));
        roomInfo.add(new TextWidget(Text.literal("玩家数: " + playerCount), this.textRenderer));
        roomInfo.add(new TextWidget(Text.literal("网络质量: " + networkQualityLabel + " " + (lastPingMs < 0 ? "--" : lastPingMs + "ms")), this.textRenderer));

        DirectionalLayoutWidget remarkLayout = DirectionalLayoutWidget.horizontal().spacing(6);
        TextFieldWidget remarkBox = new TextFieldWidget(this.textRenderer, 150, 20, Text.literal("房间描述"));
        remarkBox.setText(roomRemark);
        remarkBox.setMaxLength(64);
        remarkBox.setPlaceholder(Text.literal("房间描述 (MOTD)"));
        remarkBox.setChangedListener(val -> {
            roomRemark = val;
            
        });
        remarkBox.setEditable(isHostConnected());
        remarkLayout.add(remarkBox);
        net.minecraft.client.gui.widget.ButtonWidget saveBtn = net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("保存"), b -> {
            
            roomRemark = remarkBox.getText();
            roomStateDirty = true;
            MinecraftEnderClient.showToast(Text.literal("提示"), Text.literal("房间描述已保存"));
        }).width(44).build();
        saveBtn.active = isHostConnected();
        remarkLayout.add(saveBtn);
        roomInfo.add(remarkLayout);

        roomInfo.add(net.minecraft.client.gui.widget.ButtonWidget.builder(
                Text.literal("复制房间号"),
                button -> {
                    try {
                        this.client.keyboard.setClipboard(finalRoomCode);
                        MinecraftEnderClient.showToast(Text.literal("提示"), Text.literal("房间号已复制"));
                    } catch (Exception ignored) {
                    }
                }
        ).width(200).build());

        roomInfo.add(net.minecraft.client.gui.widget.ButtonWidget.builder(
                Text.literal("关闭房间"),
                button -> {
                    EnderApiClient.setIdle();
                    new Thread(ProcessLauncher::stop, "Ender-Stopper").start();
                    wasConnected = false;
                    this.isUiConnected = false;
                    this.onClose();
                }
        ).width(200).build());
        content.add(roomInfo);
    }

    private void addPermissionsPage(DirectionalLayoutWidget content) {
        DirectionalLayoutWidget permission = DirectionalLayoutWidget.vertical().spacing(6);
        permission.getMainPositioner().alignHorizontalCenter();
        permission.add(new TextWidget(Text.literal(" 权限与访客 "), this.textRenderer));

        String[] permissionCycle = new String[]{"可交互", "仅聊天", "仅观战", "禁止进入"};
        permission.add(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("访客权限: " + visitorPermission), button -> {
            int idx = 0;
            for (int i = 0; i < permissionCycle.length; i++) {
                if (permissionCycle[i].equals(visitorPermission)) {
                    idx = i;
                    break;
                }
            }
            visitorPermission = permissionCycle[(idx + 1) % permissionCycle.length];
            button.setMessage(Text.literal("访客权限: " + visitorPermission));
            roomStateDirty = true;
            MinecraftEnderClient.showToast(Text.literal("提示"), Text.literal("访客权限已更新"));
        }).width(200).build());

        permission.add(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("白名单启用: " + (whitelistEnabled ? "开" : "关")), button -> {
            whitelistEnabled = !whitelistEnabled;
            button.setMessage(Text.literal("白名单启用: " + (whitelistEnabled ? "开" : "关")));
            roomStateDirty = true;
            MinecraftEnderClient.showToast(Text.literal("提示"), Text.literal("白名单设置已更新"));
        }).width(200).build());

        permission.add(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("详细名单管理 (白名单/黑名单/禁言)"), button -> {
            if (this.client != null) {
                this.client.setScreen(new RoomListsScreen(this));
            }
        }).width(240).build());

        permission.add(new TextWidget(Text.literal("当前列表概况:"), this.textRenderer));
        com.google.gson.JsonObject state = EnderApiClient.getRoomManagementStateSync();
        int wlCount = 0;
        int blCount = 0;
        int muteCount = 0;
        if (state != null) {
            if (state.has("whitelist") && state.get("whitelist").isJsonArray()) {
                wlCount = state.getAsJsonArray("whitelist").size();
            }
            if (state.has("blacklist") && state.get("blacklist").isJsonArray()) {
                blCount = state.getAsJsonArray("blacklist").size();
            }
            if (state.has("mute_list") && state.get("mute_list").isJsonArray()) {
                muteCount = state.getAsJsonArray("mute_list").size();
            }
        }
        permission.add(new TextWidget(Text.literal("白名单: " + wlCount + " | 黑名单: " + blCount + " | 禁言: " + muteCount), this.textRenderer));

        content.add(permission);
    }

    private void appendListToScroll(PlayerListScrollWidget widget, String title, JsonArray list) {
        if (list == null || list.size() == 0) return;
        for (JsonElement el : list) {
             if (el != null && el.isJsonPrimitive()) {
                 String name = el.getAsString();
                 String typeStr = title.replace("列表", "");
                 widget.addItem(name, typeStr, b -> {
                    removeNameFromArray(list, name);
                    roomStateDirty = true;
                    roomPageRefreshRequested = true;
                    MinecraftEnderClient.showToast(Text.literal("提示"), Text.literal("已从" + title + "移除 " + name));
                 });
             }
        }
    }

    private class ListHeaderWidget extends net.minecraft.client.gui.widget.ClickableWidget {
        public ListHeaderWidget(int width) {
            super(0, 0, width, 20, Text.empty());
            this.active = false;
        }
        @Override
        public void renderWidget(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
            context.drawTextWithShadow(EnderDashboard.this.textRenderer, Text.literal("游戏名").formatted(net.minecraft.util.Formatting.YELLOW), getX() + 10, getY() + 6, 0xFFFFFF);
            context.drawTextWithShadow(EnderDashboard.this.textRenderer, Text.literal("名单类型").formatted(net.minecraft.util.Formatting.YELLOW), getX() + 140, getY() + 6, 0xFFFFFF);
            context.drawTextWithShadow(EnderDashboard.this.textRenderer, Text.literal("操作").formatted(net.minecraft.util.Formatting.YELLOW), getX() + 240, getY() + 6, 0xFFFFFF);
        }
        @Override
        protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {}
    }

    private class PlayerListWrapperWidget extends ClickableWidget {
        private final PlayerListScrollWidget list;
        
        public PlayerListWrapperWidget(PlayerListScrollWidget list, int width, int height) {
            super(0, 0, width, height, Text.empty());
            this.list = list;
        }
        
        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            this.list.setDimensions(this.width, this.height);
            this.list.setX(this.getX());
            this.list.setY(this.getY());
            this.list.render(context, mouseX, mouseY, delta);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return this.list.mouseClicked(mouseX, mouseY, button);
        }
        
        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
             return this.list.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        
        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            return this.list.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        
        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {}
    }

    private class PlayerListScrollWidget extends ElementListWidget<PlayerListScrollWidget.Entry> {
        public PlayerListScrollWidget(MinecraftClient client, int width, int height, int top, int bottom) {
            super(client, width, height, top, 24);
            this.centerListVertically = false;
        }
        
        public void addItem(String name, String type, net.minecraft.client.gui.widget.ButtonWidget.PressAction onRemove) {
            this.addEntry(new Entry(name, type, onRemove));
        }
        
        @Override
        public int getRowWidth() {
            return 300;
        }

        @Override
        protected int getScrollbarX() {
            return this.getX() + this.width - 6;
        }

        public class Entry extends ElementListWidget.Entry<Entry> {
             private final String name;
             private final String type;
             private final net.minecraft.client.gui.widget.ButtonWidget removeBtn;

             public Entry(String name, String type, net.minecraft.client.gui.widget.ButtonWidget.PressAction onRemove) {
                 this.name = name;
                 this.type = type;
                 this.removeBtn = net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("移除"), onRemove).width(40).build();
             }

             @Override
             public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                 context.drawTextWithShadow(EnderDashboard.this.textRenderer, name, x + 10, y + (entryHeight - 8) / 2, 0xFFFFFF);
                 context.drawTextWithShadow(EnderDashboard.this.textRenderer, Text.literal(type).formatted(net.minecraft.util.Formatting.GRAY), x + 140, y + (entryHeight - 8) / 2, 0xFFFFFF);
                 
                 this.removeBtn.setX(x + 240);
                 this.removeBtn.setY(y + (entryHeight - 20) / 2);
                 this.removeBtn.render(context, mouseX, mouseY, tickDelta);
             }

             @Override
             public boolean mouseClicked(double mouseX, double mouseY, int button) {
                 if (this.removeBtn.mouseClicked(mouseX, mouseY, button)) {
                     return true;
                 }
                 return false;
             }

             @Override
             public List<? extends Element> children() {
                 return ImmutableList.of(this.removeBtn);
             }
             
             @Override
             public List<? extends Selectable> selectableChildren() {
                 return ImmutableList.of(this.removeBtn);
             }
        }
    }



    private boolean pvpAllowed = true;

    private void addRulesPage(DirectionalLayoutWidget content) {
        DirectionalLayoutWidget rules = DirectionalLayoutWidget.vertical().spacing(6);
        rules.getMainPositioner().alignHorizontalCenter();
        rules.add(new TextWidget(Text.literal(" 规则与玩法 "), this.textRenderer));
        rules.add(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("允许作弊: " + (allowCheats ? "开" : "关")), b -> {
            allowCheats = !allowCheats;
            b.setMessage(Text.literal("允许作弊: " + (allowCheats ? "开" : "关")));
            roomStateDirty = true;
            MinecraftEnderClient.showToast(Text.literal("提示"), Text.literal("规则已更新"));
        }).width(200).build());
        rules.add(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("保留物品: " + (keepInventory ? "开" : "关")), b -> {
            keepInventory = !keepInventory;
            b.setMessage(Text.literal("保留物品: " + (keepInventory ? "开" : "关")));
            roomStateDirty = true;
            MinecraftEnderClient.showToast(Text.literal("提示"), Text.literal("规则已更新"));
        }).width(200).build());
        rules.add(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("允许PVP: " + (pvpAllowed ? "开" : "关")), b -> {
            pvpAllowed = !pvpAllowed;
            b.setMessage(Text.literal("允许PVP: " + (pvpAllowed ? "开" : "关")));
            roomStateDirty = true;
            MinecraftEnderClient.showToast(Text.literal("提示"), Text.literal("规则已更新"));
        }).width(200).build());
        rules.add(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("天气锁定: " + (weatherLock ? "开" : "关")), b -> {
            weatherLock = !weatherLock;
            b.setMessage(Text.literal("天气锁定: " + (weatherLock ? "开" : "关")));
            roomStateDirty = true;
            MinecraftEnderClient.showToast(Text.literal("提示"), Text.literal("规则已更新"));
        }).width(200).build());

        rules.add(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("更多游戏规则设置..."), b -> {
             this.client.setScreen(new EditGameRulesScreen(new GameRules(), (rulesOpt) -> {
                 this.client.setScreen(this);
                 rulesOpt.ifPresent(r -> {
                     
                     
                     
                 });
             }));
        }).width(200).build());

        rules.add(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("应用到当前世界"), b -> applyRulesToServer()).width(200).build());
        content.add(rules);
    }

    private void addWorldPage(DirectionalLayoutWidget content) {
        DirectionalLayoutWidget world = DirectionalLayoutWidget.vertical().spacing(6);
        world.getMainPositioner().alignHorizontalCenter();
        world.add(new TextWidget(Text.literal(" 世界与边界 "), this.textRenderer));
        
        
        int fillX = respawnX;
        int fillY = respawnY;
        int fillZ = respawnZ;
        if (this.client != null && this.client.player != null) {
            fillX = (int) this.client.player.getX();
            fillY = (int) this.client.player.getY();
            fillZ = (int) this.client.player.getZ();
        }

        TextFieldWidget respawnXBox = new TextFieldWidget(this.textRenderer, 64, 20, Text.literal("X"));
        respawnXBox.setText(String.valueOf(fillX));
        respawnXBox.setChangedListener(val -> {
            respawnX = parseIntSafe(val, respawnX);
            roomStateDirty = true;
        });
        TextFieldWidget respawnYBox = new TextFieldWidget(this.textRenderer, 64, 20, Text.literal("Y"));
        respawnYBox.setText(String.valueOf(fillY));
        respawnYBox.setChangedListener(val -> {
            respawnY = parseIntSafe(val, respawnY);
            roomStateDirty = true;
        });
        TextFieldWidget respawnZBox = new TextFieldWidget(this.textRenderer, 64, 20, Text.literal("Z"));
        respawnZBox.setText(String.valueOf(fillZ));
        respawnZBox.setChangedListener(val -> {
            respawnZ = parseIntSafe(val, respawnZ);
            roomStateDirty = true;
        });
        DirectionalLayoutWidget respawnRow = DirectionalLayoutWidget.horizontal().spacing(6);
        respawnRow.add(respawnXBox);
        respawnRow.add(respawnYBox);
        respawnRow.add(respawnZBox);
        world.add(new TextWidget(Text.literal("重生点 (X Y Z)"), this.textRenderer));
        world.add(respawnRow);
        world.add(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("应用重生点"), b -> {
            applyRespawn();
            MinecraftEnderClient.showToast(Text.literal("提示"), Text.literal("重生点已应用"));
        }).width(200).build());

        TextFieldWidget borderXBox = new TextFieldWidget(this.textRenderer, 64, 20, Text.literal("中心X"));
        borderXBox.setText(String.valueOf(worldBorderCenterX));
        borderXBox.setChangedListener(val -> {
            worldBorderCenterX = parseIntSafe(val, worldBorderCenterX);
            roomStateDirty = true;
        });
        TextFieldWidget borderZBox = new TextFieldWidget(this.textRenderer, 64, 20, Text.literal("中心Z"));
        borderZBox.setText(String.valueOf(worldBorderCenterZ));
        borderZBox.setChangedListener(val -> {
            worldBorderCenterZ = parseIntSafe(val, worldBorderCenterZ);
            roomStateDirty = true;
        });
        TextFieldWidget borderRadiusBox = new TextFieldWidget(this.textRenderer, 64, 20, Text.literal("半径"));
        borderRadiusBox.setText(String.valueOf(worldBorderRadius));
        borderRadiusBox.setChangedListener(val -> {
            worldBorderRadius = parseIntSafe(val, worldBorderRadius);
            roomStateDirty = true;
        });
        DirectionalLayoutWidget borderRow = DirectionalLayoutWidget.horizontal().spacing(6);
        borderRow.add(borderXBox);
        borderRow.add(borderZBox);
        borderRow.add(borderRadiusBox);
        world.add(new TextWidget(Text.literal("世界边界 (中心X 中心Z 半径)"), this.textRenderer));
        world.add(borderRow);
        world.add(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("应用世界边界"), b -> {
            applyWorldBorder();
            MinecraftEnderClient.showToast(Text.literal("提示"), Text.literal("世界边界已应用"));
        }).width(200).build());
        content.add(world);
    }

    private void addNetworkPage(DirectionalLayoutWidget content) {
        DirectionalLayoutWidget network = DirectionalLayoutWidget.vertical().spacing(6);
        network.getMainPositioner().alignHorizontalCenter();
        network.add(new TextWidget(Text.literal(" 网络与容灾 "), this.textRenderer));
        network.add(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("自动重连: " + (autoReconnect ? "开" : "关")), b -> {
            autoReconnect = !autoReconnect;
            b.setMessage(Text.literal("自动重连: " + (autoReconnect ? "开" : "关")));
            roomStateDirty = true;
            MinecraftEnderClient.showToast(Text.literal("提示"), Text.literal("设置已更新"));
        }).width(200).build());
        TextFieldWidget retryBox = new TextFieldWidget(this.textRenderer, 200, 20, Text.literal("重试次数"));
        retryBox.setText(String.valueOf(reconnectRetries));
        retryBox.setChangedListener(val -> {
            reconnectRetries = parseIntSafe(val, reconnectRetries);
            roomStateDirty = true;
        });
        network.add(retryBox);
        content.add(network);
    }

    private void addBackendPage(DirectionalLayoutWidget content) {
        DirectionalLayoutWidget backend = DirectionalLayoutWidget.vertical().spacing(6);
        backend.getMainPositioner().alignHorizontalCenter();
        backend.add(new TextWidget(Text.literal(" 后端与性能 "), this.textRenderer));
        backend.add(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("版本: " + backendVersion), b -> {
            if (backendVersions.size() > 0) {
                int idx = 0;
                for (int i = 0; i < backendVersions.size(); i++) {
                    if (backendVersions.get(i).getAsString().equals(backendVersion)) {
                        idx = i;
                        break;
                    }
                }
                backendVersion = backendVersions.get((idx + 1) % backendVersions.size()).getAsString();
                b.setMessage(Text.literal("版本: " + backendVersion));
                roomStateDirty = true;
                MinecraftEnderClient.showToast(Text.literal("提示"), Text.literal("设置已更新"));
            }
        }).width(200).build());
        backend.add(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("更新策略: " + updatePolicy), b -> {
            updatePolicy = "立即".equals(updatePolicy) ? "延后" : "立即";
            b.setMessage(Text.literal("更新策略: " + updatePolicy));
            roomStateDirty = true;
            MinecraftEnderClient.showToast(Text.literal("提示"), Text.literal("设置已更新"));
        }).width(200).build());
        backend.add(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("日志级别: " + logLevel), b -> {
            if ("INFO".equals(logLevel)) {
                logLevel = "WARN";
            } else if ("WARN".equals(logLevel)) {
                logLevel = "DEBUG";
            } else {
                logLevel = "INFO";
            }
            b.setMessage(Text.literal("日志级别: " + logLevel));
            roomStateDirty = true;
            MinecraftEnderClient.showToast(Text.literal("提示"), Text.literal("设置已更新"));
        }).width(200).build());
        TextFieldWidget cpuBox = new TextFieldWidget(this.textRenderer, 200, 20, Text.literal("CPU限制"));
        cpuBox.setText(String.valueOf(cpuLimit));
        cpuBox.setChangedListener(val -> {
            cpuLimit = parseIntSafe(val, cpuLimit);
            roomStateDirty = true;
        });
        backend.add(cpuBox);
        TextFieldWidget memBox = new TextFieldWidget(this.textRenderer, 200, 20, Text.literal("内存限制"));
        memBox.setText(String.valueOf(memoryLimit));
        memBox.setChangedListener(val -> {
            memoryLimit = parseIntSafe(val, memoryLimit);
            roomStateDirty = true;
        });
        backend.add(memBox);
        DirectionalLayoutWidget exportRow = DirectionalLayoutWidget.horizontal().spacing(6);
        exportRow.add(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("导出设置"), b -> exportRoomState()).width(96).build());
        exportRow.add(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("导入设置"), b -> importRoomState()).width(96).build());
        backend.add(exportRow);
        if (operationLogs.size() > 0) {
            int start = Math.max(0, operationLogs.size() - 5);
            for (int i = start; i < operationLogs.size(); i++) {
                backend.add(new TextWidget(Text.literal(operationLogs.get(i).getAsString()), this.textRenderer));
            }
        }
        content.add(backend);
    }

    private void applyRulesToServer() {
        MinecraftServer server = this.client == null ? null : this.client.getServer();
        if (server == null) {
            return;
        }
        setCheatsAllowed(server, allowCheats);
        setSpawnProtection(server, spawnProtection);
        setPvpEnabled(server, pvpAllowed);
        server.getGameRules().get(GameRules.KEEP_INVENTORY).set(keepInventory, server);
        server.getGameRules().get(GameRules.DO_FIRE_TICK).set(fireSpread, server);
        server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(mobSpawning, server);
        boolean cycle = "cycle".equals(timeControl);
        server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(cycle, server);
        server.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(!weatherLock, server);
        if (!cycle) {
            ServerWorld world = server.getOverworld();
            if (world != null) {
                world.setTimeOfDay("night".equals(timeControl) ? 13000 : 1000);
            }
        }
    }

    private void applyRoomManagementStateToServer() {
        MinecraftServer server = this.client == null ? null : this.client.getServer();
        if (server == null) {
            return;
        }
        
        
        if (this.roomRemark != null) {
            try {
                server.setMotd(this.roomRemark);
            } catch (Exception ignored) {
            }
        }

        applyRulesToServer();
        enforceAccessControl(server);
    }

    private void enforceAccessControl(MinecraftServer server) {
        if (this.client == null) {
            return;
        }
        String hostName = this.client.getSession() == null ? "" : this.client.getSession().getUsername();
        Object playerManager;
        try {
            playerManager = server.getClass().getMethod("getPlayerManager").invoke(server);
        } catch (Exception ignored) {
            return;
        }
        java.util.List<?> players;
        try {
            Object list = playerManager.getClass().getMethod("getPlayerList").invoke(playerManager);
            players = list instanceof java.util.List<?> l ? l : java.util.List.of();
        } catch (Exception ignored) {
            try {
                Object list = playerManager.getClass().getMethod("getPlayers").invoke(playerManager);
                players = list instanceof java.util.List<?> l ? l : java.util.List.of();
            } catch (Exception ignored2) {
                return;
            }
        }

        for (Object player : players) {
            String name = getPlayerName(player);
            if (name != null && !hostName.isEmpty() && name.equalsIgnoreCase(hostName)) {
                continue;
            }
            if (containsName(blacklist, name)) {
                disconnectPlayer(player, Text.literal("你已被房主加入黑名单"));
                continue;
            }
            if (whitelistEnabled && whitelist != null && !containsName(whitelist, name)) {
                disconnectPlayer(player, Text.literal("你不在白名单中"));
                continue;
            }
            if ("禁止进入".equals(visitorPermission)) {
                disconnectPlayer(player, Text.literal("房间禁止访客进入"));
                continue;
            }
            if ("仅观战".equals(visitorPermission)) {
                setPlayerGameMode(player, "SPECTATOR");
            } else if ("仅聊天".equals(visitorPermission)) {
                setPlayerGameMode(player, "ADVENTURE");
            }
        }
    }

    private String getPlayerName(Object player) {
        if (player == null) {
            return null;
        }
        try {
            Object profile = player.getClass().getMethod("getGameProfile").invoke(player);
            Object name = profile == null ? null : profile.getClass().getMethod("getName").invoke(profile);
            return name == null ? null : String.valueOf(name);
        } catch (Exception ignored) {
        }
        try {
            Object text = player.getClass().getMethod("getName").invoke(player);
            Object str = text == null ? null : text.getClass().getMethod("getString").invoke(text);
            return str == null ? null : String.valueOf(str);
        } catch (Exception ignored) {
        }
        return null;
    }

    private int parseIntSafe(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void addNameToArray(JsonArray array, String name) {
        if (array == null || name == null) {
            return;
        }
        for (JsonElement el : array) {
            if (el != null && el.isJsonPrimitive() && name.equalsIgnoreCase(el.getAsString())) {
                return;
            }
        }
        array.add(name);
    }

    private void removeNameFromArray(JsonArray array, String name) {
        if (array == null || name == null) {
            return;
        }
        for (int i = 0; i < array.size(); i++) {
            JsonElement el = array.get(i);
            if (el != null && el.isJsonPrimitive() && name.equalsIgnoreCase(el.getAsString())) {
                array.remove(i);
                return;
            }
        }
    }

    private boolean containsName(JsonArray array, String name) {
        if (array == null || name == null) {
            return false;
        }
        for (JsonElement el : array) {
            if (el != null && el.isJsonPrimitive() && name.equalsIgnoreCase(el.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private void disconnectPlayer(Object player, Text reason) {
        if (player == null) {
            return;
        }
        try {
            java.lang.reflect.Field f = player.getClass().getField("networkHandler");
            Object handler = f.get(player);
            if (handler != null) {
                java.lang.reflect.Method m = handler.getClass().getMethod("disconnect", Text.class);
                m.invoke(handler, reason);
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            Object handler = player.getClass().getMethod("networkHandler").invoke(player);
            if (handler != null) {
                java.lang.reflect.Method m = handler.getClass().getMethod("disconnect", Text.class);
                m.invoke(handler, reason);
            }
        } catch (Exception ignored) {
        }
    }

    private void setPlayerGameMode(Object player, String modeName) {
        if (player == null || modeName == null) {
            return;
        }
        Class<?> gameModeClass;
        try {
            gameModeClass = Class.forName("net.minecraft.world.GameMode");
        } catch (Exception ignored) {
            return;
        }
        Object mode;
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Enum> enumClass = (Class<? extends Enum>) gameModeClass;
            mode = Enum.valueOf(enumClass, modeName);
        } catch (Exception ignored) {
            return;
        }
        try {
            java.lang.reflect.Method m = player.getClass().getMethod("changeGameMode", gameModeClass);
            m.invoke(player, mode);
            return;
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Method m = player.getClass().getMethod("setGameMode", gameModeClass);
            m.invoke(player, mode);
        } catch (Exception ignored) {
        }
    }

    private void setCheatsAllowed(MinecraftServer server, boolean value) {
        if (server == null) {
            return;
        }
        Object playerManager;
        try {
            playerManager = server.getClass().getMethod("getPlayerManager").invoke(server);
        } catch (Exception ignored) {
            return;
        }
        try {
            java.lang.reflect.Method m = playerManager.getClass().getMethod("setCheatsAllowed", boolean.class);
            m.invoke(playerManager, value);
            return;
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Method m = playerManager.getClass().getMethod("setAllowCommandsForAllPlayers", boolean.class);
            m.invoke(playerManager, value);
        } catch (Exception ignored) {
        }
    }

    private void setSpawnProtection(MinecraftServer server, int value) {
        if (server == null) {
            return;
        }
        int radius = Math.max(0, value);
        Object playerManager;
        try {
            playerManager = server.getClass().getMethod("getPlayerManager").invoke(server);
        } catch (Exception ignored) {
            return;
        }
        try {
            java.lang.reflect.Method m = playerManager.getClass().getMethod("setSpawnProtectionRadius", int.class);
            m.invoke(playerManager, radius);
            return;
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Method m = playerManager.getClass().getMethod("setSpawnProtection", int.class);
            m.invoke(playerManager, radius);
        } catch (Exception ignored) {
        }
    }

    private void applyRespawn() {
        MinecraftServer server = this.client == null ? null : this.client.getServer();
        if (server == null) {
            return;
        }
        ServerWorld world = server.getOverworld();
        if (world == null) {
            return;
        }
        world.setSpawnPos(new BlockPos(respawnX, respawnY, respawnZ), 0.0f);
    }

    private void applyWorldBorder() {
        MinecraftServer server = this.client == null ? null : this.client.getServer();
        if (server == null) {
            return;
        }
        ServerWorld world = server.getOverworld();
        if (world == null) {
            return;
        }
        world.getWorldBorder().setCenter(worldBorderCenterX, worldBorderCenterZ);
        if (worldBorderRadius > 0) {
            world.getWorldBorder().setSize(worldBorderRadius * 2.0);
        }
    }

    private void exportRoomState() {
        try {
            String json = buildRoomManagementStateJson().toString();
            this.client.keyboard.setClipboard(json);
            MinecraftEnderClient.showToast(Text.literal("提示"), Text.literal("配置已复制到剪贴板"));
        } catch (Exception ignored) {
        }
    }

    private void importRoomState() {
        try {
            String json = this.client.keyboard.getClipboard();
            if (json == null || json.isBlank()) {
                return;
            }
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj == null) {
                return;
            }
            mergeRoomManagementState(obj);
            roomStateDirty = true;
            this.init(this.client, this.width, this.height);
        } catch (Exception ignored) {
        }
    }

    private class PlayerListWidget extends ElementListWidget<PlayerListWidget.Entry> {
        public PlayerListWidget(MinecraftClient client, int width, int height, int top, int itemHeight) {
            super(client, width, height, top, itemHeight);
            this.centerListVertically = false;
        }

        public void updateEntries(JsonObject state) {
            this.clearEntries();
            if (state == null) return;
            
            if (state.has("profiles")) {
                try {
                    JsonArray profiles = state.getAsJsonArray("profiles");
                    for (JsonElement p : profiles) {
                        JsonObject profile = p.getAsJsonObject();
                        String name = profile.get("name").getAsString();
                        String kind = profile.has("kind") ? profile.get("kind").getAsString() : "";
                        String vendor = profile.has("vendor") ? profile.get("vendor").getAsString() : "";
                        
                        String status = "[已连接]";
                        if ("EasyTier".equals(vendor)) {
                            status = "[连接中]";
                        }
                        
                        this.addEntry(new Entry(name, kind, status));
                    }
                } catch (Exception ignored) {}
            } else if (state.has("players")) {
                try {
                    JsonArray players = state.getAsJsonArray("players");
                    for (JsonElement p : players) {
                        this.addEntry(new Entry(p.getAsString(), "", "[已连接]"));
                    }
                } catch (Exception ignored) {}
            }
        }

        @Override
        public int getRowWidth() {
            return 260;
        }

        @Override
        protected int getScrollbarX() {
            return this.getX() + this.width - 6;
        }

        public class Entry extends ElementListWidget.Entry<Entry> {
            private final String name;
            private final String kind;
            private final String status;

            public Entry(String name, String kind, String status) {
                this.name = name;
                this.kind = kind;
                this.status = status;
            }

            @Override
            public void render(DrawContext context, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
                int color = 0xFFFFFF;
                String display = name;
                String typeLabel = "[成员]";
                if ("HOST".equals(kind)) {
                    typeLabel = "[房主]";
                    color = 0xFFFF55;
                }
                
                int maxNameWidth = 125;
                if (textRenderer.getWidth(display) > maxNameWidth) {
                    display = textRenderer.trimToWidth(display, maxNameWidth - 10) + "...";
                }
                
                context.drawText(textRenderer, display, left + 10, top + (height - 8) / 2, color, false);
                context.drawText(textRenderer, Text.literal(typeLabel).formatted(net.minecraft.util.Formatting.GRAY), left + 140, top + (height - 8) / 2, 0xFFFFFF, false);
                
                int statusColor = 0x55FF55;
                 if ("[未连接]".equals(status)) {
                     statusColor = 0xFF5555;
                 } else if ("[连接中]".equals(status)) {
                     statusColor = 0xFFFF55;
                 }
                 if (status != null) {
                     context.drawText(textRenderer, Text.literal(status).formatted(net.minecraft.util.Formatting.GRAY), left + 200, top + (height - 8) / 2, statusColor, false);
                 }
            }

            @Override
            public List<? extends Element> children() {
                return ImmutableList.of();
            }

            @Override
            public List<? extends Selectable> selectableChildren() {
                return ImmutableList.of();
            }
        }
    }
}



