package com.multiplayer.terracotta.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.multiplayer.terracotta.client.ClientSetup;
import com.multiplayer.terracotta.network.TerracottaApiClient;
import com.multiplayer.terracotta.logic.ProcessLauncher;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import com.multiplayer.terracotta.Config;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.worldselection.EditGameRulesScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.Difficulty;

public class TerracottaDashboard extends TerracottaBaseScreen {
    private String backendState = Component.translatable("terracotta.dashboard.status.fetching").getString();
    private long lastStateCheck = 0;
    private static final Gson GSON = new Gson();
    private static String lastClipboard = "";
    private static boolean wasConnected = false;
    private boolean isUiConnected = false;
    private static JsonObject lastStateJson = null;

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
        super(Component.literal("陶瓦联机中心"), parent);
        this.currentMode = mode;
        this.tempPath = Config.EXTERNAL_TERRACOTTA_PATH.get();
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
                    if (clipboard.matches("^U/[A-Z0-9]{4}(-[A-Z0-9]{4}){3}$")) {
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

    @Override
    protected void initContent() {
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
        if (!TerracottaApiClient.hasDynamicPort()) return;

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
                            this.minecraft.execute(() -> this.init(this.minecraft, this.width, this.height));
                        } else if (isConnected && lastStateJson == null) {
                            lastStateJson = json;
                            this.minecraft.execute(() -> this.init(this.minecraft, this.width, this.height));
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
        this.init(this.minecraft, this.width, this.height);
    }

    private void initConnectedContent() {
        this.isUiConnected = true;
        boolean isHost = this.minecraft.getSingleplayerServer() != null && this.minecraft.getSingleplayerServer().isPublished();

        if (!isHost) {
             initGuestConnectedContent();
             return;
        }
        
        LinearLayout mainLayout = LinearLayout.horizontal().spacing(20);
        
        if (currentMode == ViewMode.FULL || currentMode == ViewMode.INGAME_INFO) {
            LinearLayout leftPanel = LinearLayout.vertical().spacing(10);
            leftPanel.defaultCellSetting().alignHorizontallyCenter();
            
            leftPanel.addChild(new StringWidget(Component.literal(" 房间信息 "), this.font));

            String roomCode = "未知";
            if (lastStateJson != null && lastStateJson.has("room")) {
                roomCode = lastStateJson.get("room").getAsString();
            }
            String finalRoomCode = roomCode;
            leftPanel.addChild(new StringWidget(Component.literal("房间号: " + finalRoomCode), this.font));
            leftPanel.addChild(Button.builder(Component.literal("复制房间号"), button -> {
                try {
                    this.minecraft.keyboardHandler.setClipboard(finalRoomCode);
                    ClientSetup.showToast(Component.literal("提示"), Component.literal("房间号已复制"));
                } catch (Exception e) {
                    ClientSetup.showToast(Component.literal("提示"), Component.literal("复制失败，请手动复制房间号"));
                }
            }).width(180).build());

            boolean shouldShowPlayers = (currentMode == ViewMode.INGAME_INFO) || (showPlayerList);
            
            if (shouldShowPlayers) {
                 addPlayerListToLayout(leftPanel);
            }

            mainLayout.addChild(leftPanel);
        }

        if (currentMode == ViewMode.FULL || currentMode == ViewMode.INGAME_SETTINGS) {
            LinearLayout rightPanel = LinearLayout.vertical().spacing(10);
            rightPanel.defaultCellSetting().alignHorizontallyCenter();

            rightPanel.addChild(new StringWidget(Component.literal(" 房间设置 "), this.font));

            if (currentMode == ViewMode.FULL) {
                rightPanel.addChild(new StringWidget(Component.literal(" 核心设置 "), this.font));
                
                EditBox pathBox = new EditBox(this.font, 0, 0, 180, 20, Component.literal("Path"));
                pathBox.setValue(this.tempPath);
                pathBox.setMaxLength(1024);
                pathBox.setResponder(val -> this.tempPath = val);
                rightPanel.addChild(pathBox);

                Button autoUpdateBtn = Button.builder(Component.literal("自动更新: " + (this.tempAutoUpdate ? "开" : "关")), button -> {
                    this.tempAutoUpdate = !this.tempAutoUpdate;
                    button.setMessage(Component.literal("自动更新: " + (this.tempAutoUpdate ? "开" : "关")));
                }).width(180).build();
                rightPanel.addChild(autoUpdateBtn);

                Button autoStartBtn = Button.builder(Component.literal("自动启动: " + (this.tempAutoStart ? "开" : "关")), button -> {
                    this.tempAutoStart = !this.tempAutoStart;
                    button.setMessage(Component.literal("自动启动: " + (this.tempAutoStart ? "开" : "关")));
                }).width(180).build();
                rightPanel.addChild(autoStartBtn);
                
                rightPanel.addChild(Button.builder(Component.literal("应用设置"), button -> {
                    this.saveConfig();
                }).width(180).build());
            }

            if (isHost) {
                rightPanel.addChild(new StringWidget(Component.literal(" 房主管理 "), this.font));

                if (currentMode == ViewMode.FULL) {
                    rightPanel.addChild(Button.builder(Component.literal("房间信息 (" + (showPlayerList ? "隐藏" : "显示") + ")"), b -> {
                        this.showPlayerList = !this.showPlayerList;
                        this.init(this.minecraft, this.width, this.height);
                    }).width(180).build());
                }

                rightPanel.addChild(Button.builder(Component.literal("游戏规则"), b -> {
                    IntegratedServer server = this.minecraft.getSingleplayerServer();
                    if (server != null) {
                        this.minecraft.setScreen(new EditGameRulesScreen(server.getGameRules().copy(), (optionalRules) -> {
                            this.minecraft.setScreen(this);
                            optionalRules.ifPresent(rules -> server.getGameRules().assignFrom(rules, server));
                        }));
                    }
                }).width(180).build());

                rightPanel.addChild(Button.builder(Component.literal("服务端设置 (" + (showServerSettings ? "隐藏" : "显示") + ")"), b -> {
                    this.showServerSettings = !this.showServerSettings;
                    this.init(this.minecraft, this.width, this.height);
                }).width(180).build());

                if (showServerSettings) {
                    IntegratedServer server = this.minecraft.getSingleplayerServer();
                    if (server != null) {
                        rightPanel.addChild(Button.builder(Component.literal("难度: " + server.getWorldData().getDifficulty().getKey()), b -> {
                            Difficulty current = server.getWorldData().getDifficulty();
                            Difficulty next = Difficulty.byId((current.getId() + 1) % 4);
                            server.setDifficulty(next, true);
                            b.setMessage(Component.literal("难度: " + next.getKey()));
                        }).width(180).build());
                        
                        boolean pvp = server.isPvpAllowed();
                        rightPanel.addChild(Button.builder(Component.literal("PVP: " + (pvp ? "允许" : "禁止")), b -> {
                            server.setPvpAllowed(!pvp);
                            b.setMessage(Component.literal("PVP: " + (!pvp ? "允许" : "禁止")));
                        }).width(180).build());
                    }
                }
            }

            rightPanel.addChild(Button.builder(Component.literal(isHost ? "关闭房间" : "退出联机"), button -> {
                TerracottaApiClient.setIdle();
                new Thread(ProcessLauncher::stop, "Terracotta-Stopper").start();
                wasConnected = false;
                this.isUiConnected = false;
                this.onClose();
            }).width(180).build());

            mainLayout.addChild(rightPanel);
        }

        this.layout.addToContents(mainLayout);

        if (currentMode == ViewMode.FULL) {
            this.layout.addToFooter(Button.builder(Component.literal("断开连接"), button -> {
                TerracottaApiClient.setIdle();
                new Thread(ProcessLauncher::stop, "Terracotta-Stopper").start();
                wasConnected = false; 
                this.isUiConnected = false;
                this.init(this.minecraft, this.width, this.height);
            }).width(200).build());
        } else {
            this.layout.addToFooter(Button.builder(Component.literal("返回"), button -> {
                this.onClose();
            }).width(200).build());
        }
    }

    private void initGuestConnectedContent() {
        LinearLayout content = LinearLayout.vertical().spacing(10);
        content.defaultCellSetting().alignHorizontallyCenter();
        
        content.addChild(new StringWidget(Component.literal(" 房间玩家列表 "), this.font));
        
        addPlayerListToLayout(content);
        
        this.layout.addToContents(content);
        
        LinearLayout footerLayout = LinearLayout.horizontal().spacing(10);
        
        if (lastStateJson != null && lastStateJson.has("url")) {
             String url = lastStateJson.get("url").getAsString();
             footerLayout.addChild(Button.builder(Component.literal("加入"), b -> connectToServer(url)).width(80).build());
             footerLayout.addChild(Button.builder(Component.literal("进入服务器"), b -> connectToServer(url)).width(100).build());
        }

        footerLayout.addChild(Button.builder(Component.literal("断开连接"), b -> {
            TerracottaApiClient.setIdle();
            new Thread(ProcessLauncher::stop, "Terracotta-Stopper").start();
            wasConnected = false; 
            this.isUiConnected = false;
            this.onClose();
        }).width(80).build());
        
        footerLayout.addChild(Button.builder(Component.literal("返回"), b -> this.onClose()).width(80).build());
        
        this.layout.addToFooter(footerLayout);
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
        Config.EXTERNAL_TERRACOTTA_PATH.set(this.tempPath);
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
             ConnectScreen.startConnecting(this.parent, this.minecraft, serverAddress, new ServerData("Terracotta Server", connectUrl, ServerData.Type.OTHER), false, null);
         } catch (Exception e) {
         }
    }

    private void initIdleContent() {
        this.isUiConnected = false;
        LinearLayout contentLayout = LinearLayout.vertical().spacing(15);

        contentLayout.addChild(Button.builder(Component.literal("加入房间"), button -> {
            if (TerracottaApiClient.hasDynamicPort()) {
                this.minecraft.setScreen(new JoinScreen(this));
            } else {
                this.minecraft.setScreen(new StartupScreen(this.parent));
            }
        }).width(200).build());

        contentLayout.addChild(Button.builder(Component.literal("设置"), button -> {
            this.minecraft.setScreen(new TerracottaConfigScreen(this));
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
            
            if (TerracottaApiClient.hasDynamicPort()) {
                TerracottaApiClient.getState().thenAccept(this::updateBackendState);
            } else {
                this.backendState = Component.translatable("terracotta.state.not_started").getString();
            }
        }
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        if (this.currentMode == ViewMode.FULL || this.currentMode == ViewMode.INGAME_INFO) {
            int textY = this.layout.getHeaderHeight() + 5;
            guiGraphics.drawCenteredString(this.font, Component.translatable("terracotta.dashboard.status.fetching").getString().equals(this.backendState) ? 
                this.backendState : Component.translatable("terracotta.dashboard.status_prefix").append(this.backendState).getString(), this.width / 2, textY, 0xAAAAAA);
        }
    }

    private void updateBackendState(String stateJson) {
        if (stateJson != null) {
             try {
                    JsonObject json = GSON.fromJson(stateJson, JsonObject.class);
                    String state = "";
                    if (json.has("state")) {
                        state = json.get("state").getAsString();
                    }
                    
                    String displayKey = "terracotta.state.idle";
                    
                    switch (state) {
                        case "idle": displayKey = "terracotta.state.idle"; break;
                        case "host-starting": displayKey = "terracotta.state.host_starting"; break;
                        case "host-scanning": displayKey = "terracotta.state.host_scanning"; break;
                        case "host-ok": displayKey = "terracotta.state.connected"; break; 
                        case "guest-starting": displayKey = "terracotta.state.guest_starting"; break;
                        case "guest-connecting": displayKey = "terracotta.state.guest_connecting"; break;
                        case "guest-ok": displayKey = "terracotta.state.connected"; break;
                        case "waiting": displayKey = "terracotta.state.waiting"; break;
                        default: displayKey = null; break;
                    }
                    
                    if (displayKey != null) {
                        this.backendState = Component.translatable(displayKey).getString();
                    } else {
                        this.backendState = state;
                    }
             } catch (Exception ignored) {}
        }
    }
}
