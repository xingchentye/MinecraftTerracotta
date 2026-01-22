package com.multiplayer.ender.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.multiplayer.ender.Config;
import com.multiplayer.ender.client.ClientSetup;
import com.multiplayer.ender.logic.ProcessLauncher;
import com.multiplayer.ender.network.EnderApiClient;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.AbstractLayout;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.EditGameRulesScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.border.WorldBorder;


public class EnderDashboard extends EnderBaseScreen {
    private String backendState = Component.translatable("ender.dashboard.status.fetching").getString();
    private long lastStateCheck = 0;
    private static final Gson GSON = new Gson();
    private static String lastClipboard = "";
    private static boolean wasConnected = false;
    private boolean isUiConnected = false;
    private static JsonObject lastStateJson = null;

    private boolean showPlayerList = true;
    private boolean showServerSettings = false;

    private String tempPath;
    private boolean tempAutoUpdate;
    private boolean tempAutoStart;
    private long lastRoomSync = 0;
    private long lastPingCheck = 0;
    private int lastPingMs = -1;
    private int networkQualityColor = 0x00FF00;
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
    private final List<Integer> performanceSamples = new ArrayList<>();
    private JsonArray backendVersions = new JsonArray();
    private boolean roomStateDirty = false;
    private long lastPushTime = 0;
    private final List<AbstractWidget> roomPageWidgets = new ArrayList<>();
    private final List<Button> roomPageButtons = new ArrayList<>();
    private RoomPagePanel roomPagePanel;
    private PlayerListScrollWidget playerListWidget;

    public enum ViewMode {
        FULL,
        INGAME_INFO,
        INGAME_SETTINGS
    }

    public enum RoomPage {
        OVERVIEW,
        PERMISSIONS,
        RULES,
        WORLD,
        NETWORK,
        BACKEND
    }

    private ViewMode currentMode = ViewMode.FULL;
    private RoomPage currentRoomPage = RoomPage.OVERVIEW;

    public EnderDashboard(Screen parent) {
        this(parent, ViewMode.FULL);
    }

    public EnderDashboard(Screen parent, ViewMode mode) {
        super(Component.literal("末影联机中心"), parent);
        this.currentMode = mode;
        this.tempPath = Config.EXTERNAL_ender_PATH.get();
        this.tempAutoUpdate = Config.AUTO_UPDATE.get();
        this.tempAutoStart = Config.AUTO_START_BACKEND.get();
    }

    public void setConnected(boolean connected) {
        wasConnected = connected;
    }

    private void checkClipboardAndAutoJoin() {
        if (wasConnected) return;
        try {
            String clipboard = this.minecraft.keyboardHandler.getClipboard();
            if (clipboard != null) {
                clipboard = clipboard.trim();
                if (!clipboard.isEmpty() && !clipboard.equals(lastClipboard)) {
                    
                    if (clipboard.matches("^U/[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$")) {
                        lastClipboard = clipboard;
                        String finalClipboard = clipboard;
                        this.minecraft.execute(() -> {
                             this.minecraft.setScreen(new JoinScreen(this, finalClipboard));
                        });
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 初始化界面内容。
     * 根据当前状态决定显示闲置界面还是连接界面。
     */
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
        if (!EnderApiClient.hasDynamicPort()) return;

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
                            this.minecraft.execute(this::rebuildWidgets);
                        } else if (isConnected && lastStateJson == null) {
                            lastStateJson = json;
                            this.minecraft.execute(this::rebuildWidgets);
                        }
                    } else if (json.has("status") && "IDLE".equals(json.get("status").getAsString())) {
                        if (wasConnected) {
                            wasConnected = false;
                            this.isUiConnected = false;
                            lastStateJson = null;
                            this.minecraft.execute(this::rebuildWidgets);
                        }
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    public void updateFromState(JsonObject json) {
        if (json == null) return;
        lastStateJson = json;
        if (json.has("state")) {
            String state = json.get("state").getAsString();
            boolean isConnected = "host-ok".equals(state) || "guest-ok".equals(state);
            wasConnected = isConnected;
            this.isUiConnected = isConnected;
        }
        this.rebuildWidgets();
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
            LinearLayout loadingLayout = LinearLayout.vertical().spacing(10);
            loadingLayout.defaultCellSetting().alignHorizontallyCenter();
            loadingLayout.addChild(new StringWidget(Component.translatable("ender.host.status.requesting"), this.font));
            this.layout.addToContents(loadingLayout);
            return;
        }

        if (!isHost) {
            initGuestConnectedContent();
            return;
        }
        initRoomManagementContent();
        if (currentMode == ViewMode.FULL) {
            this.layout.addToFooter(Button.builder(Component.literal("断开连接"), button -> {
                EnderApiClient.setIdle();
                new Thread(ProcessLauncher::stop, "Ender-Stopper").start();
                wasConnected = false;
                this.isUiConnected = false;
                this.rebuildWidgets();
            }).width(200).build());
        } else {
            this.layout.addToFooter(Button.builder(Component.literal("返回"), button -> {
                this.onClose();
            }).width(200).build());
        }
    }

    private void initGuestConnectedContent() {
        
        LinearLayout footerLayout = LinearLayout.horizontal().spacing(10);
        
        footerLayout.addChild(Button.builder(Component.literal("断开连接"), b -> {
            EnderApiClient.setIdle();
            new Thread(ProcessLauncher::stop, "Ender-Stopper").start();
            wasConnected = false; 
            this.isUiConnected = false;
            this.onClose();
        }).width(80).build());
        
        footerLayout.addChild(Button.builder(Component.literal("返回"), b -> this.onClose()).width(80).build());
        
        this.layout.addToFooter(footerLayout);

        
        int headerHeight = this.layout.getHeaderHeight() + 10;
        int footerHeight = this.layout.getFooterHeight() + 10;
        
        int titleY = headerHeight;
        StringWidget title = new StringWidget(0, titleY, this.width, 20, Component.literal("房间玩家列表"), this.font);
        title.alignCenter();
        this.addRenderableWidget(title);
        
        int listTop = titleY + 25;
        int listBottom = this.height - footerHeight - 30;
        int listWidth = 300;
        int listX = (this.width - listWidth) / 2;
        
        PlayerListScrollWidget list = new PlayerListScrollWidget(this.minecraft, listWidth, listBottom - listTop, listTop, listBottom);
        list.updateWidgetSize(listWidth, listBottom - listTop, listTop, listBottom, listX);
        list.updateEntries(lastStateJson);
        this.playerListWidget = list;
        this.addRenderableWidget(list);

        Button joinGameBtn = Button.builder(Component.literal("加入游戏"), b -> {
            String ip = EnderApiClient.getHostIp();
            if (ip != null) {
                int port = EnderApiClient.getRemoteMcPort();
                ServerData serverData = new ServerData("Ender Room", ip + ":" + port, ServerData.Type.OTHER);
                ConnectScreen.startConnecting(this, this.minecraft, ServerAddress.parseString(serverData.ip), serverData, false, null);
            }
        }).width(200).build();
        joinGameBtn.setPosition((this.width - 200) / 2, listBottom + 5);
        this.addRenderableWidget(joinGameBtn);
    }

    private void addPlayerListToLayout(LinearLayout layout) {
        if (lastStateJson != null) {
            if (lastStateJson.has("profiles")) {
                try {
                    JsonArray profiles = lastStateJson.getAsJsonArray("profiles");
                    if (profiles.size() > 0) {
                        layout.addChild(new StringWidget(Component.literal(" 当前玩家 (" + profiles.size() + ") "), this.font));
                        for (JsonElement p : profiles) {
                            JsonObject profile = p.getAsJsonObject();
                            String name = profile.get("name").getAsString();
                            String kind = profile.has("kind") ? profile.get("kind").getAsString() : "";
                            String display = name;
                            if ("HOST".equals(kind)) {
                                display += " [房主]";
                            }
                            layout.addChild(new StringWidget(Component.literal(display), this.font));
                        }
                    }
                } catch (Exception ignored) {}
            } else if (lastStateJson.has("players")) {
                try {
                    var players = lastStateJson.getAsJsonArray("players");
                    if (players.size() > 0) {
                        layout.addChild(new StringWidget(Component.literal(" 当前玩家 (" + players.size() + ") "), this.font));
                        for (var p : players) {
                            String pName = p.getAsString();
                            layout.addChild(new StringWidget(Component.literal(pName), this.font));
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private void saveConfig() {
        Config.EXTERNAL_ender_PATH.set(this.tempPath);
        Config.AUTO_UPDATE.set(this.tempAutoUpdate);
        Config.AUTO_START_BACKEND.set(this.tempAutoStart);
        Config.CLIENT_SPEC.save();
    }

    private void connectToServer(String connectUrl) {
         try {
             String[] parts = connectUrl.split(":");
             String host = parts[0];
             int port = 25565;
             if (parts.length > 1) {
                 port = Integer.parseInt(parts[1]);
             }
             ServerAddress serverAddress = new ServerAddress(host, port);
             ConnectScreen.startConnecting(this.parent, this.minecraft, serverAddress, new ServerData("Ender Server", connectUrl, ServerData.Type.OTHER), false, null);
         } catch (Exception e) {
         }
    }

    private void initIdleContent() {
        this.isUiConnected = false;
        LinearLayout contentLayout = LinearLayout.vertical().spacing(15);

        contentLayout.addChild(Button.builder(Component.literal("加入房间"), button -> {
            if (EnderApiClient.hasDynamicPort()) {
                this.minecraft.setScreen(new JoinScreen(this));
            } else {
                this.minecraft.setScreen(new StartupScreen(this.parent));
            }
        }).width(200).build());

        contentLayout.addChild(Button.builder(Component.literal("设置"), button -> {
            this.minecraft.setScreen(new EnderConfigScreen(this));
        }).width(200).build());

        this.layout.addToContents(contentLayout);

        this.layout.addToFooter(Button.builder(Component.literal("退出"), button -> {
            this.onClose();
        }).width(200).build());
    }

    @Override
    public void tick() {
        super.tick();
        
        long now = System.currentTimeMillis();
        if (now - lastStateCheck > 1000) {
            lastStateCheck = now;
            
            if (EnderApiClient.hasDynamicPort()) {
                long startedAt = System.currentTimeMillis();
                EnderApiClient.getState().thenAccept(stateJson -> updateBackendState(stateJson, startedAt));
            } else {
                this.backendState = Component.translatable("ender.state.not_started").getString();
            }
        }

        if (isHostConnected()) {
            long roomInterval = 500;
            if (now - lastRoomSync > roomInterval) {
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
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        if (this.currentMode == ViewMode.FULL || this.currentMode == ViewMode.INGAME_INFO) {
            int textY = this.layout.getHeaderHeight() + 5;
            guiGraphics.drawCenteredString(this.font, Component.translatable("ender.dashboard.status.fetching").getString().equals(this.backendState) ? 
                this.backendState : Component.translatable("ender.dashboard.status_prefix").append(this.backendState).getString(), this.width / 2, textY, 0xAAAAAA);
        }
    }

    private void updateBackendState(String stateJson, long startedAt) {
        if (stateJson != null) {
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
                        if (this.minecraft != null) {
                            this.minecraft.execute(() -> {
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
                        if (this.minecraft != null) {
                            this.minecraft.execute(() -> this.init(this.minecraft, this.width, this.height));
                        }
                    }
                    
                    String displayKey = "ender.state.idle";
                    
                    switch (state) {
                        case "idle": displayKey = "ender.state.idle"; break;
                        case "host-starting": displayKey = "ender.state.host_starting"; break;
                        case "host-scanning": displayKey = "ender.state.host_scanning"; break;
                        case "host-ok": displayKey = "ender.state.connected"; break; 
                        case "guest-starting": displayKey = "ender.state.guest_starting"; break;
                        case "guest-connecting": displayKey = "ender.state.guest_connecting"; break;
                        case "guest-ok": displayKey = "ender.state.connected"; break;
                        case "waiting": displayKey = "ender.state.waiting"; break;
                        case "error":
                            if (json.has("error")) {
                                this.backendState = "错误: " + json.get("error").getAsString();
                                displayKey = null;
                            } else {
                                this.backendState = "错误";
                                displayKey = null;
                            }
                            break;
                        default: displayKey = null; break;
                    }
                    
                    if (displayKey != null) {
                        this.backendState = Component.translatable(displayKey).getString();
                    } else if (!"error".equals(state)) {
                        this.backendState = state;
                    }

                    if (isConnected) {
                        long cost = Math.max(0, System.currentTimeMillis() - startedAt);
                        this.lastPingMs = (int) cost;
                        if (cost <= 80) {
                            this.networkQualityLabel = "优秀";
                            this.networkQualityColor = 0x00FF00;
                        } else if (cost <= 150) {
                            this.networkQualityLabel = "良好";
                            this.networkQualityColor = 0x66FF00;
                        } else if (cost <= 250) {
                            this.networkQualityLabel = "一般";
                            this.networkQualityColor = 0xFFFF00;
                        } else {
                            this.networkQualityLabel = "较差";
                            this.networkQualityColor = 0xFF5555;
                        }
                    }
             } catch (Exception ignored) {}
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
            if (changed && this.minecraft != null) {
                this.minecraft.execute(() -> this.init(this.minecraft, this.width, this.height));
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
        int contentX = menuWidth + spacing;
        int contentWidth = this.width - contentX - 10; 

        LinearLayout menu = LinearLayout.vertical().spacing(6);
        menu.defaultCellSetting().alignHorizontallyLeft();
        roomPageButtons.clear();
        for (RoomPage page : RoomPage.values()) {
            addRoomPageMenuButton(menu, page, menuWidth);
        }
        updateRoomPageMenuButtons();
        
        
        menu.arrangeElements();
        menu.setPosition(10, this.layout.getHeaderHeight() + 10); 

        this.roomPagePanel = new RoomPagePanel(contentWidth, this.height - this.layout.getHeaderHeight() - this.layout.getFooterHeight() - 20);
        this.roomPagePanel.setPosition(contentX, this.layout.getHeaderHeight() + 10);
        
        rebuildRoomPageContent(false);
        
        
        
        
        menu.visitWidgets(this::addRenderableWidget);
        
    }

    private void addRoomPageMenuButton(LinearLayout menu, RoomPage page, int width) {
        Button button = Button.builder(Component.literal(getRoomPageTitle(page)), b -> switchToPage(page)).width(width).build();
        roomPageButtons.add(button);
        menu.addChild(button);
    }

    private void switchToPage(RoomPage page) {
        if (page == this.currentRoomPage) {
            return;
        }
        this.currentRoomPage = page;
        updateRoomPageMenuButtons();
        rebuildRoomPageContent(true);
    }

    private void updateRoomPageMenuButtons() {
        RoomPage[] pages = RoomPage.values();
        for (int i = 0; i < pages.length && i < roomPageButtons.size(); i++) {
            roomPageButtons.get(i).active = pages[i] != currentRoomPage;
        }
    }

    private void rebuildRoomPageContent(boolean registerWidgets) {
        if (roomPagePanel == null) {
            return;
        }
        
        
        for (AbstractWidget widget : roomPageWidgets) {
            this.removeWidget(widget);
        }
        
        roomPageWidgets.clear();
        LinearLayout pageContent = LinearLayout.vertical().spacing(12);
        pageContent.defaultCellSetting().alignHorizontallyCenter(); 
        switch (currentRoomPage) {
            case OVERVIEW -> addOverviewPage(pageContent);
            case PERMISSIONS -> addPermissionsPage(pageContent);
            case RULES -> addRulesPage(pageContent);
            case WORLD -> addWorldPage(pageContent);
            case NETWORK -> addNetworkPage(pageContent);
            case BACKEND -> addBackendPage(pageContent);
        }
        roomPagePanel.setContent(pageContent);
        
        
        roomPagePanel.arrangeElements();
        
        pageContent.visitWidgets(widget -> {
            roomPageWidgets.add(widget);
            if (registerWidgets) {
                this.addRenderableWidget(widget);
            }
        });
        
        
        
        
        
        
        
        
        
        
        
        if (!registerWidgets) {
             
             for (AbstractWidget widget : roomPageWidgets) {
                 this.addRenderableWidget(widget);
             }
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

    private static class RoomPagePanel extends AbstractLayout implements Layout {
        private LayoutElement content;
        private final int fixedWidth;
        private final int fixedHeight;

        private RoomPagePanel(int width, int height) {
            super(0, 0, width, height);
            this.fixedWidth = width;
            this.fixedHeight = height;
        }

        private void setContent(LayoutElement content) {
            this.content = content;
        }

        @Override
        public void visitChildren(Consumer<LayoutElement> consumer) {
            if (content != null) {
                consumer.accept(content);
            }
        }

        @Override
        public void arrangeElements() {
            if (content instanceof Layout layout) {
                layout.arrangeElements();
            }
            if (content != null) {
                
                int contentW = content.getWidth();
                int offset = (this.fixedWidth - contentW) / 2;
                content.setPosition(this.getX() + offset, this.getY());
            }
            this.width = fixedWidth;
            this.height = fixedHeight;
        }
    }

    private class PlayerListScrollWidget extends net.minecraft.client.gui.components.ObjectSelectionList<PlayerListScrollWidget.Entry> {
        public PlayerListScrollWidget(net.minecraft.client.Minecraft minecraft, int width, int height, int top, int bottom) {
            super(minecraft, width, height, top, 24);
        }

        public void updateWidgetSize(int width, int height, int top, int bottom, int left) {
            this.width = width;
            this.height = height;
            this.setX(left);
            this.setY(top);
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
                        String type = "HOST".equals(kind) ? "[房主]" : "[成员]";
                        
                        String status = "[已连接]";
                        if ("EasyTier".equals(vendor)) {
                            status = "[连接中]";
                        }
                        
                        this.addEntry(new Entry(name, type, status));
                    }
                } catch (Exception ignored) {}
            } else if (state.has("players")) {
                try {
                    JsonArray players = state.getAsJsonArray("players");
                    for (JsonElement p : players) {
                        this.addEntry(new Entry(p.getAsString(), "[成员]", "[已连接]"));
                    }
                } catch (Exception ignored) {}
            }
        }
        
        @Override
        public int getRowWidth() {
            return 300;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getX() + this.width - 6;
        }

        public class Entry extends net.minecraft.client.gui.components.ObjectSelectionList.Entry<Entry> {
             private final String name;
             private final String type;
             private final String status;

             public Entry(String name, String type, String status) {
                 this.name = name;
                 this.type = type;
                 this.status = status;
             }

             @Override
             public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float partialTick) {
                 int color = 0xFFFFFF;
                 if ("[房主]".equals(type)) {
                     color = 0xFFFF55;
                 }
                 
                 String displayName = name;
                 int maxNameWidth = 125;
                 if (EnderDashboard.this.font.width(displayName) > maxNameWidth) {
                     displayName = EnderDashboard.this.font.plainSubstrByWidth(displayName, maxNameWidth - 10) + "...";
                 }
                 
                 guiGraphics.drawString(EnderDashboard.this.font, displayName, x + 10, y + (entryHeight - 8) / 2, color);
                 guiGraphics.drawString(EnderDashboard.this.font, Component.literal(type).withStyle(net.minecraft.ChatFormatting.GRAY), x + 140, y + (entryHeight - 8) / 2, 0xFFFFFF);
                 
                 int statusColor = 0x55FF55; 
                 if ("[未连接]".equals(status)) {
                     statusColor = 0xFF5555; 
                 } else if ("[连接中]".equals(status)) {
                     statusColor = 0xFFFF55; 
                 }
                 guiGraphics.drawString(EnderDashboard.this.font, Component.literal(status).withStyle(net.minecraft.ChatFormatting.GRAY), x + 200, y + (entryHeight - 8) / 2, statusColor);
             }

             @Override
             public boolean mouseClicked(double mouseX, double mouseY, int button) {
                 return false;
             }
             
             @Override
             public Component getNarration() {
                 return Component.literal(name);
             }
        }
    }

    private void addOverviewPage(LinearLayout content) {
        LinearLayout roomInfo = LinearLayout.vertical().spacing(6);
        roomInfo.defaultCellSetting().alignHorizontallyCenter();
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
        roomInfo.addChild(new StringWidget(Component.literal("房间号: " + finalRoomCode), this.font));
        roomInfo.addChild(new StringWidget(Component.literal("玩家数: " + playerCount), this.font));
        roomInfo.addChild(new StringWidget(Component.literal("网络质量: " + networkQualityLabel + " " + (lastPingMs < 0 ? "--" : lastPingMs + "ms")), this.font));

        LinearLayout remarkLayout = LinearLayout.horizontal().spacing(6);
        EditBox remarkBox = new EditBox(this.font, 0, 0, 150, 20, Component.literal("房间描述"));
        remarkBox.setValue(roomRemark);
        remarkBox.setMaxLength(64);
        remarkBox.setHint(Component.literal("房间描述 (MOTD)"));
        remarkBox.setResponder(val -> {
            roomRemark = val;
            
        });
        remarkBox.setEditable(isHostConnected());
        remarkLayout.addChild(remarkBox);
        Button saveBtn = Button.builder(Component.literal("保存"), b -> {
            
            roomRemark = remarkBox.getValue();
            roomStateDirty = true;
            ClientSetup.showToast(Component.literal("提示"), Component.literal("房间描述已保存"));
        }).width(44).build();
        saveBtn.active = isHostConnected();
        remarkLayout.addChild(saveBtn);
        roomInfo.addChild(remarkLayout);

        roomInfo.addChild(Button.builder(Component.literal("复制房间号"), button -> {
            try {
                this.minecraft.keyboardHandler.setClipboard(finalRoomCode);
                ClientSetup.showToast(Component.literal("提示"), Component.literal("房间号已复制"));
            } catch (Exception e) {
                ClientSetup.showToast(Component.literal("提示"), Component.literal("复制失败，请手动复制房间号"));
            }
        }).width(200).build());

        roomInfo.addChild(Button.builder(Component.literal("关闭房间"), button -> {
            EnderApiClient.setIdle();
            new Thread(ProcessLauncher::stop, "Ender-Stopper").start();
            wasConnected = false;
            this.isUiConnected = false;
            this.onClose();
        }).width(200).build());
        content.addChild(roomInfo);
    }

    private void addPermissionsPage(LinearLayout content) {
        LinearLayout permission = LinearLayout.vertical().spacing(6);
        permission.defaultCellSetting().alignHorizontallyCenter();
        permission.addChild(new StringWidget(Component.literal(" 权限与访客 "), this.font));

        String[] permissionCycle = new String[]{"可交互", "仅聊天", "仅观战", "禁止进入"};
        Button permissionBtn = Button.builder(Component.literal("访客权限: " + visitorPermission), button -> {
            int idx = 0;
            for (int i = 0; i < permissionCycle.length; i++) {
                if (permissionCycle[i].equals(visitorPermission)) {
                    idx = i;
                    break;
                }
            }
            visitorPermission = permissionCycle[(idx + 1) % permissionCycle.length];
            button.setMessage(Component.literal("访客权限: " + visitorPermission));
            roomStateDirty = true;
            ClientSetup.showToast(Component.literal("提示"), Component.literal("访客权限已更新"));
        }).width(200).build();
        permission.addChild(permissionBtn);

        permission.addChild(Button.builder(Component.literal("白名单启用: " + (whitelistEnabled ? "开" : "关")), button -> {
            whitelistEnabled = !whitelistEnabled;
            button.setMessage(Component.literal("白名单启用: " + (whitelistEnabled ? "开" : "关")));
            roomStateDirty = true;
            ClientSetup.showToast(Component.literal("提示"), Component.literal("白名单设置已更新"));
        }).width(200).build());

        permission.addChild(Button.builder(Component.literal("详细名单管理 (白名单/黑名单/禁言)"), button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new RoomListsScreen(this));
            }
        }).width(240).build());

        permission.addChild(new StringWidget(Component.literal("当前列表概况:"), this.font));
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
        permission.addChild(new StringWidget(Component.literal("白名单: " + wlCount + " | 黑名单: " + blCount + " | 禁言: " + muteCount), this.font));

        content.addChild(permission);
    }
    
    private boolean pvpAllowed = true;

    private void addRulesPage(LinearLayout content) {
        LinearLayout rules = LinearLayout.vertical().spacing(6);
        rules.defaultCellSetting().alignHorizontallyCenter();

        rules.addChild(Button.builder(Component.literal("允许作弊: " + (allowCheats ? "开" : "关")), b -> {
            allowCheats = !allowCheats;
            b.setMessage(Component.literal("允许作弊: " + (allowCheats ? "开" : "关")));
            roomStateDirty = true;
            ClientSetup.showToast(Component.literal("提示"), Component.literal("规则已更新"));
        }).width(200).build());
        rules.addChild(Button.builder(Component.literal("保留物品: " + (keepInventory ? "开" : "关")), b -> {
            keepInventory = !keepInventory;
            b.setMessage(Component.literal("保留物品: " + (keepInventory ? "开" : "关")));
            roomStateDirty = true;
            ClientSetup.showToast(Component.literal("提示"), Component.literal("规则已更新"));
        }).width(200).build());
        rules.addChild(Button.builder(Component.literal("允许PVP: " + (pvpAllowed ? "开" : "关")), b -> {
            pvpAllowed = !pvpAllowed;
            b.setMessage(Component.literal("允许PVP: " + (pvpAllowed ? "开" : "关")));
            roomStateDirty = true;
            ClientSetup.showToast(Component.literal("提示"), Component.literal("规则已更新"));
        }).width(200).build());
        rules.addChild(Button.builder(Component.literal("天气锁定: " + (weatherLock ? "开" : "关")), b -> {
            weatherLock = !weatherLock;
            b.setMessage(Component.literal("天气锁定: " + (weatherLock ? "开" : "关")));
            roomStateDirty = true;
            ClientSetup.showToast(Component.literal("提示"), Component.literal("规则已更新"));
        }).width(200).build());

        rules.addChild(Button.builder(Component.literal("更多游戏规则设置..."), b -> {
            IntegratedServer server = this.minecraft.getSingleplayerServer();
            if (server == null) {
                return;
            }
            this.minecraft.setScreen(new EditGameRulesScreen(server.getGameRules().copy(), (rulesOpt) -> {
                this.minecraft.setScreen(this);
                rulesOpt.ifPresent(r -> server.getGameRules().assignFrom(r, server));
            }));
        }).width(200).build());

        rules.addChild(Button.builder(Component.literal("应用到当前世界"), b -> applyRulesToServer()).width(200).build());
        content.addChild(rules);
    }

    private void addWorldPage(LinearLayout content) {
        LinearLayout world = LinearLayout.vertical().spacing(6);
        world.defaultCellSetting().alignHorizontallyCenter();

        
        int fillX = respawnX;
        int fillY = respawnY;
        int fillZ = respawnZ;
        if (this.minecraft != null && this.minecraft.player != null) {
            fillX = (int) this.minecraft.player.getX();
            fillY = (int) this.minecraft.player.getY();
            fillZ = (int) this.minecraft.player.getZ();
        }

        EditBox respawnXBox = new EditBox(this.font, 0, 0, 64, 20, Component.literal("X"));
        respawnXBox.setValue(String.valueOf(fillX));
        respawnXBox.setResponder(val -> {
            respawnX = parseIntSafe(val, respawnX);
            roomStateDirty = true;
        });
        EditBox respawnYBox = new EditBox(this.font, 0, 0, 64, 20, Component.literal("Y"));
        respawnYBox.setValue(String.valueOf(fillY));
        respawnYBox.setResponder(val -> {
            respawnY = parseIntSafe(val, respawnY);
            roomStateDirty = true;
        });
        EditBox respawnZBox = new EditBox(this.font, 0, 0, 64, 20, Component.literal("Z"));
        respawnZBox.setValue(String.valueOf(fillZ));
        respawnZBox.setResponder(val -> {
            respawnZ = parseIntSafe(val, respawnZ);
            roomStateDirty = true;
        });
        LinearLayout respawnRow = LinearLayout.horizontal().spacing(6);
        respawnRow.addChild(respawnXBox);
        respawnRow.addChild(respawnYBox);
        respawnRow.addChild(respawnZBox);
        world.addChild(new StringWidget(Component.literal("重生点 (X Y Z)"), this.font));
        world.addChild(respawnRow);
        world.addChild(Button.builder(Component.literal("应用重生点"), b -> {
            applyRespawn();
            ClientSetup.showToast(Component.literal("提示"), Component.literal("重生点已应用"));
        }).width(200).build());

        EditBox borderXBox = new EditBox(this.font, 0, 0, 64, 20, Component.literal("中心X"));
        borderXBox.setValue(String.valueOf(worldBorderCenterX));
        borderXBox.setResponder(val -> {
            worldBorderCenterX = parseIntSafe(val, worldBorderCenterX);
            roomStateDirty = true;
        });
        EditBox borderZBox = new EditBox(this.font, 0, 0, 64, 20, Component.literal("中心Z"));
        borderZBox.setValue(String.valueOf(worldBorderCenterZ));
        borderZBox.setResponder(val -> {
            worldBorderCenterZ = parseIntSafe(val, worldBorderCenterZ);
            roomStateDirty = true;
        });
        EditBox borderRadiusBox = new EditBox(this.font, 0, 0, 64, 20, Component.literal("半径"));
        borderRadiusBox.setValue(String.valueOf(worldBorderRadius));
        borderRadiusBox.setResponder(val -> {
            worldBorderRadius = parseIntSafe(val, worldBorderRadius);
            roomStateDirty = true;
        });
        LinearLayout borderRow = LinearLayout.horizontal().spacing(6);
        borderRow.addChild(borderXBox);
        borderRow.addChild(borderZBox);
        borderRow.addChild(borderRadiusBox);
        world.addChild(new StringWidget(Component.literal("世界边界 (中心X 中心Z 半径)"), this.font));
        world.addChild(borderRow);
        world.addChild(Button.builder(Component.literal("应用世界边界"), b -> {
            applyWorldBorder();
            ClientSetup.showToast(Component.literal("提示"), Component.literal("世界边界已应用"));
        }).width(200).build());
        content.addChild(world);
    }

    private void addNetworkPage(LinearLayout content) {
        LinearLayout network = LinearLayout.vertical().spacing(6);
        network.defaultCellSetting().alignHorizontallyCenter();
        network.addChild(Button.builder(Component.literal("自动重连: " + (autoReconnect ? "开" : "关")), b -> {
            autoReconnect = !autoReconnect;
            b.setMessage(Component.literal("自动重连: " + (autoReconnect ? "开" : "关")));
            roomStateDirty = true;
            ClientSetup.showToast(Component.literal("提示"), Component.literal("设置已更新"));
        }).width(200).build());
        EditBox retryBox = new EditBox(this.font, 0, 0, 200, 20, Component.literal("重试次数"));
        retryBox.setValue(String.valueOf(reconnectRetries));
        retryBox.setResponder(val -> {
            reconnectRetries = parseIntSafe(val, reconnectRetries);
            roomStateDirty = true;
        });
        network.addChild(retryBox);
        content.addChild(network);
    }

    private void addBackendPage(LinearLayout content) {
        LinearLayout backend = LinearLayout.vertical().spacing(6);
        backend.defaultCellSetting().alignHorizontallyCenter();
        backend.addChild(Button.builder(Component.literal("版本: " + backendVersion), b -> {
            if (backendVersions.size() > 0) {
                int idx = 0;
                for (int i = 0; i < backendVersions.size(); i++) {
                    if (backendVersions.get(i).getAsString().equals(backendVersion)) {
                        idx = i;
                        break;
                    }
                }
                backendVersion = backendVersions.get((idx + 1) % backendVersions.size()).getAsString();
                b.setMessage(Component.literal("版本: " + backendVersion));
                roomStateDirty = true;
                ClientSetup.showToast(Component.literal("提示"), Component.literal("设置已更新"));
            }
        }).width(200).build());
        backend.addChild(Button.builder(Component.literal("更新策略: " + updatePolicy), b -> {
            updatePolicy = "立即".equals(updatePolicy) ? "延后" : "立即";
            b.setMessage(Component.literal("更新策略: " + updatePolicy));
            roomStateDirty = true;
            ClientSetup.showToast(Component.literal("提示"), Component.literal("设置已更新"));
        }).width(200).build());
        backend.addChild(Button.builder(Component.literal("日志级别: " + logLevel), b -> {
            if ("INFO".equals(logLevel)) {
                logLevel = "WARN";
            } else if ("WARN".equals(logLevel)) {
                logLevel = "DEBUG";
            } else {
                logLevel = "INFO";
            }
            b.setMessage(Component.literal("日志级别: " + logLevel));
            roomStateDirty = true;
            ClientSetup.showToast(Component.literal("提示"), Component.literal("设置已更新"));
        }).width(200).build());

        EditBox cpuBox = new EditBox(this.font, 0, 0, 200, 20, Component.literal("CPU限制"));
        cpuBox.setValue(String.valueOf(cpuLimit));
        cpuBox.setResponder(val -> {
            cpuLimit = parseIntSafe(val, cpuLimit);
            roomStateDirty = true;
        });
        backend.addChild(cpuBox);
        EditBox memBox = new EditBox(this.font, 0, 0, 200, 20, Component.literal("内存限制"));
        memBox.setValue(String.valueOf(memoryLimit));
        memBox.setResponder(val -> {
            memoryLimit = parseIntSafe(val, memoryLimit);
            roomStateDirty = true;
        });
        backend.addChild(memBox);

        LinearLayout exportRow = LinearLayout.horizontal().spacing(6);
        exportRow.addChild(Button.builder(Component.literal("导出设置"), b -> exportRoomState()).width(96).build());
        exportRow.addChild(Button.builder(Component.literal("导入设置"), b -> importRoomState()).width(96).build());
        backend.addChild(exportRow);

        if (operationLogs.size() > 0) {
            int start = Math.max(0, operationLogs.size() - 5);
            for (int i = start; i < operationLogs.size(); i++) {
                backend.addChild(new StringWidget(Component.literal(operationLogs.get(i).getAsString()), this.font));
            }
        }
        content.addChild(backend);
    }

    private void applyRulesToServer() {
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(keepInventory, server);
        server.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(!weatherLock, server);
        setBooleanGameRule(server, fireSpread, "RULE_DOFIRETICK", "RULE_DO_FIRE_TICK");
        setBooleanGameRule(server, mobSpawning, "RULE_DOMOBSPAWNING", "RULE_DO_MOB_SPAWNING");
        boolean cycle = "cycle".equals(timeControl);
        setBooleanGameRule(server, cycle, "RULE_DAYLIGHT", "RULE_DAYLIGHT_CYCLE", "RULE_DO_DAYLIGHT_CYCLE");
        if (!cycle) {
            ServerLevel level = server.overworld();
            if (level != null) {
                setWorldTime(level, "night".equals(timeControl) ? 13000L : 1000L);
            }
        }
        server.setPvpAllowed(pvpAllowed);
        setCheatsAllowed(server, allowCheats);
        setSpawnProtection(server, spawnProtection);
    }

    private void applyRoomManagementStateToServer() {
        IntegratedServer server = this.minecraft.getSingleplayerServer();
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

    private void enforceAccessControl(IntegratedServer server) {
        String hostName = this.minecraft.getUser().getName();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            String name = player.getGameProfile().getName();
            if (name != null && name.equalsIgnoreCase(hostName)) {
                continue;
            }
            if (containsName(blacklist, name)) {
                disconnectPlayer(player, Component.literal("你已被房主加入黑名单"));
                continue;
            }
            if (whitelistEnabled && whitelist != null && !containsName(whitelist, name)) {
                disconnectPlayer(player, Component.literal("你不在白名单中"));
                continue;
            }
            if ("禁止进入".equals(visitorPermission)) {
                disconnectPlayer(player, Component.literal("房间禁止访客进入"));
                continue;
            }
            if ("仅观战".equals(visitorPermission)) {
                setPlayerGameType(player, GameType.SPECTATOR);
            } else if ("仅聊天".equals(visitorPermission)) {
                setPlayerGameType(player, GameType.ADVENTURE);
            }
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

    private void disconnectPlayer(ServerPlayer player, Component reason) {
        try {
            player.connection.disconnect(reason);
        } catch (Exception ignored) {
        }
    }

    private void setPlayerGameType(ServerPlayer player, GameType type) {
        try {
            java.lang.reflect.Method m = player.getClass().getMethod("setGameMode", GameType.class);
            m.invoke(player, type);
        } catch (Exception ignored) {
        }
    }

    private void setCheatsAllowed(IntegratedServer server, boolean value) {
        try {
            Object playerList = server.getPlayerList();
            java.lang.reflect.Method m = playerList.getClass().getMethod("setAllowCheatsForAllPlayers", boolean.class);
            m.invoke(playerList, value);
            return;
        } catch (Exception ignored) {
        }
        try {
            Object playerList = server.getPlayerList();
            java.lang.reflect.Method m = playerList.getClass().getMethod("setAllowCommandsForAllPlayers", boolean.class);
            m.invoke(playerList, value);
        } catch (Exception ignored) {
        }
    }

    private void setSpawnProtection(IntegratedServer server, int value) {
        int radius = Math.max(0, value);
        try {
            Object playerList = server.getPlayerList();
            java.lang.reflect.Method m = playerList.getClass().getMethod("setSpawnProtectionRadius", int.class);
            m.invoke(playerList, radius);
            return;
        } catch (Exception ignored) {
        }
        try {
            Object playerList = server.getPlayerList();
            java.lang.reflect.Method m = playerList.getClass().getMethod("setSpawnProtection", int.class);
            m.invoke(playerList, radius);
        } catch (Exception ignored) {
        }
    }

    private void setBooleanGameRule(IntegratedServer server, boolean value, String... fieldNames) {
        if (server == null || fieldNames == null) {
            return;
        }
        Object gameRules = server.getGameRules();
        for (String fieldName : fieldNames) {
            if (fieldName == null || fieldName.isBlank()) {
                continue;
            }
            try {
                java.lang.reflect.Field f = GameRules.class.getField(fieldName);
                Object key = f.get(null);
                Object rule = null;
                try {
                    java.lang.reflect.Method m = gameRules.getClass().getMethod("getRule", key.getClass());
                    rule = m.invoke(gameRules, key);
                } catch (Exception ignored) {
                    for (java.lang.reflect.Method m : gameRules.getClass().getMethods()) {
                        if (!"getRule".equals(m.getName()) || m.getParameterCount() != 1) {
                            continue;
                        }
                        rule = m.invoke(gameRules, key);
                        break;
                    }
                }
                if (rule == null) {
                    continue;
                }
                for (java.lang.reflect.Method m : rule.getClass().getMethods()) {
                    if (!"set".equals(m.getName()) || m.getParameterCount() != 2) {
                        continue;
                    }
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 2 && params[0] == boolean.class) {
                        m.invoke(rule, value, server);
                        return;
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void setWorldTime(ServerLevel level, long time) {
        if (level == null) {
            return;
        }
        try {
            java.lang.reflect.Method m = level.getClass().getMethod("setDayTime", long.class);
            m.invoke(level, time);
            return;
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Method m = level.getClass().getMethod("setTimeOfDay", long.class);
            m.invoke(level, time);
        } catch (Exception ignored) {
        }
    }

    private void applyRespawn() {
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        ServerLevel level = server.overworld();
        if (level == null) {
            return;
        }
        level.setDefaultSpawnPos(new BlockPos(respawnX, respawnY, respawnZ), 0.0f);
    }

    private void applyWorldBorder() {
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        ServerLevel level = server.overworld();
        if (level == null) {
            return;
        }
        WorldBorder border = level.getWorldBorder();
        border.setCenter(worldBorderCenterX, worldBorderCenterZ);
        if (worldBorderRadius > 0) {
            border.setSize(worldBorderRadius * 2.0);
        }
    }

    private void exportRoomState() {
        try {
            String json = buildRoomManagementStateJson().toString();
            this.minecraft.keyboardHandler.setClipboard(json);
            ClientSetup.showToast(Component.literal("提示"), Component.literal("配置已复制到剪贴板"));
        } catch (Exception e) {
            ClientSetup.showToast(Component.literal("提示"), Component.literal("导出失败"));
        }
    }

    private void importRoomState() {
        try {
            String json = this.minecraft.keyboardHandler.getClipboard();
            if (json == null || json.isBlank()) {
                return;
            }
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj == null) {
                return;
            }
            mergeRoomManagementState(obj);
            roomStateDirty = true;
            this.init(this.minecraft, this.width, this.height);
        } catch (Exception ignored) {
        }
    }

    private int parseIntSafe(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}



