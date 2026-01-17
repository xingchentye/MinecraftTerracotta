package com.multiplayer.terracotta.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.multiplayer.terracotta.logic.ProcessLauncher;
import com.multiplayer.terracotta.network.TerracottaApiClient;
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

/**
 * 陶瓦联机中心仪表盘
 * 负责显示当前连接状态、房间信息、玩家列表以及提供设置选项
 * 
 * @author xingchentye
 */
public class TerracottaDashboard extends TerracottaBaseScreen {
    /** 后端状态显示文本 */
    private String backendState = Component.translatable("terracotta.dashboard.status.fetching").getString();
    /** 上次检查状态的时间戳 */
    private long lastStateCheck = 0;
    /** Gson 实例 */
    private static final Gson GSON = new Gson();
    /** 上次剪贴板内容 (用于自动识别邀请码) */
    private String lastClipboard = "";
    /** 静态连接状态标志 (跨界面保持) */
    private static boolean wasConnected = false;
    /** 当前 UI 连接状态 */
    private boolean isUiConnected = false;
    /** 上次获取的状态 JSON 数据 */
    private static JsonObject lastStateJson = null;

    // --- 视图状态 ---
    /** 是否显示玩家列表 */
    private boolean showPlayerList = false;
    /** 是否显示服务端高级设置 */
    private boolean showServerSettings = false;

    // --- 设置临时变量 ---
    private String tempPath;
    private boolean tempAutoUpdate;
    private boolean tempAutoStart;

    /**
     * 视图模式枚举
     */
    public enum ViewMode {
        /** 完整模式 (主界面) */
        FULL,
        /** 游戏内信息模式 */
        INGAME_INFO,
        /** 游戏内设置模式 */
        INGAME_SETTINGS
    }

    /** 当前视图模式 */
    private ViewMode currentMode = ViewMode.FULL;

    /**
     * 构造函数 (默认完整模式)
     * 
     * @param parent 父界面
     */
    public TerracottaDashboard(Screen parent) {
        this(parent, ViewMode.FULL);
    }

    /**
     * 构造函数
     * 
     * @param parent 父界面
     * @param mode 视图模式
     */
    public TerracottaDashboard(Screen parent, ViewMode mode) {
        super(Component.literal("陶瓦联机中心"), parent);
        this.currentMode = mode;
        this.tempPath = Config.EXTERNAL_TERRACOTTA_PATH.get();
        this.tempAutoUpdate = Config.AUTO_UPDATE.get();
        this.tempAutoStart = Config.AUTO_START_BACKEND.get();
    }

    /**
     * 设置连接状态
     * 
     * @param connected 是否已连接
     */
    public void setConnected(boolean connected) {
        wasConnected = connected;
    }

    /**
     * 检查剪贴板并尝试自动加入房间
     * 识别格式: U/XXXX-XXXX-XXXX-XXXX
     */
    private void checkClipboardAndAutoJoin() {
        if (wasConnected) return; // 如果已连接则跳过
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
            // 忽略剪贴板访问错误
        }
    }

    /**
     * 初始化界面内容
     * 根据当前连接状态选择加载空闲界面或已连接界面
     */
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

    /**
     * 立即检查后端状态
     * 用于界面初始化时的状态同步
     */
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

    /**
     * 使用给定的状态 JSON 更新界面
     * 
     * @param json 状态数据
     */
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

    /**
     * 初始化已连接状态的界面内容
     * 区分房主 (Host) 和房客 (Guest) 视图
     */
    private void initConnectedContent() {
        this.isUiConnected = true;
        boolean isHost = this.minecraft.getSingleplayerServer() != null && this.minecraft.getSingleplayerServer().isPublished();

        // 房客视图: 简单的玩家列表和操作按钮
        if (!isHost) {
             initGuestConnectedContent();
             return;
        }
        
        // 房主视图: 完整的控制面板
        // 主容器: 水平布局
        LinearLayout mainLayout = LinearLayout.horizontal().spacing(20);
        
        // --- 左侧面板: 房间信息 ---
        if (currentMode == ViewMode.FULL || currentMode == ViewMode.INGAME_INFO) {
            LinearLayout leftPanel = LinearLayout.vertical().spacing(10);
            leftPanel.defaultCellSetting().alignHorizontallyCenter();
            
            // 标题
            leftPanel.addChild(new StringWidget(Component.literal("=== 房间信息 ==="), this.font));

            // 房间号显示与复制按钮
            String roomCode = "未知";
            if (lastStateJson != null && lastStateJson.has("room")) {
                roomCode = lastStateJson.get("room").getAsString();
            }
            leftPanel.addChild(Button.builder(Component.literal("复制房间号: " + roomCode), button -> {
                this.minecraft.keyboardHandler.setClipboard(button.getMessage().getString().replace("复制房间号: ", ""));
            }).width(180).build());

            // 玩家列表 (房主视图下控制显示)
            boolean shouldShowPlayers = (currentMode == ViewMode.INGAME_INFO) || (showPlayerList);
            
            if (shouldShowPlayers) {
                 addPlayerListToLayout(leftPanel);
            }

            mainLayout.addChild(leftPanel);
        }

        // --- 右侧面板: 设置 ---
        if (currentMode == ViewMode.FULL || currentMode == ViewMode.INGAME_SETTINGS) {
            LinearLayout rightPanel = LinearLayout.vertical().spacing(10);
            rightPanel.defaultCellSetting().alignHorizontallyCenter();

            // 标题
            rightPanel.addChild(new StringWidget(Component.literal("=== 房间设置 ==="), this.font));

            // 1. 核心设置 (仅在完整模式显示)
            if (currentMode == ViewMode.FULL) {
                rightPanel.addChild(new StringWidget(Component.literal("--- 核心设置 ---"), this.font));
                
                // 路径输入框
                EditBox pathBox = new EditBox(this.font, 0, 0, 180, 20, Component.literal("Path"));
                pathBox.setValue(this.tempPath);
                pathBox.setMaxLength(1024);
                pathBox.setResponder(val -> this.tempPath = val);
                rightPanel.addChild(pathBox);

                // 开关按钮
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
                
                // 应用按钮
                rightPanel.addChild(Button.builder(Component.literal("应用设置"), button -> {
                    this.saveConfig();
                }).width(180).build());
            }

            // 2. 房主管理设置 (仅房主可见)
            if (isHost) {
                rightPanel.addChild(new StringWidget(Component.literal("--- 房主管理 ---"), this.font));

                // 房间信息显示切换
                if (currentMode == ViewMode.FULL) {
                    rightPanel.addChild(Button.builder(Component.literal("房间信息 (" + (showPlayerList ? "隐藏" : "显示") + ")"), b -> {
                        this.showPlayerList = !this.showPlayerList;
                        this.init(this.minecraft, this.width, this.height);
                    }).width(180).build());
                }

                // 游戏规则设置
                rightPanel.addChild(Button.builder(Component.literal("游戏规则"), b -> {
                    IntegratedServer server = this.minecraft.getSingleplayerServer();
                    if (server != null) {
                        this.minecraft.setScreen(new EditGameRulesScreen(server.getGameRules().copy(), (optionalRules) -> {
                            this.minecraft.setScreen(this);
                            optionalRules.ifPresent(rules -> server.getGameRules().assignFrom(rules, server));
                        }));
                    }
                }).width(180).build());

                // 服务端设置切换
                rightPanel.addChild(Button.builder(Component.literal("服务端设置 (" + (showServerSettings ? "隐藏" : "显示") + ")"), b -> {
                    this.showServerSettings = !this.showServerSettings;
                    this.init(this.minecraft, this.width, this.height);
                }).width(180).build());

                if (showServerSettings) {
                    IntegratedServer server = this.minecraft.getSingleplayerServer();
                    if (server != null) {
                        // 难度设置
                        rightPanel.addChild(Button.builder(Component.literal("难度: " + server.getWorldData().getDifficulty().getKey()), b -> {
                            Difficulty current = server.getWorldData().getDifficulty();
                            Difficulty next = Difficulty.byId((current.getId() + 1) % 4);
                            server.setDifficulty(next, true);
                            b.setMessage(Component.literal("难度: " + next.getKey()));
                        }).width(180).build());
                        
                        // PVP 设置
                        boolean pvp = server.isPvpAllowed();
                        rightPanel.addChild(Button.builder(Component.literal("PVP: " + (pvp ? "允许" : "禁止")), b -> {
                            server.setPvpAllowed(!pvp);
                            b.setMessage(Component.literal("PVP: " + (!pvp ? "允许" : "禁止")));
                        }).width(180).build());
                    }
                }
            }

            // 关闭房间/退出联机按钮
            rightPanel.addChild(Button.builder(Component.literal(isHost ? "关闭房间" : "退出联机"), button -> {
                TerracottaApiClient.setIdle();
                wasConnected = false; 
                this.isUiConnected = false;
                this.onClose();
            }).width(180).build());

            mainLayout.addChild(rightPanel);
        }

        this.layout.addToContents(mainLayout);

        // 断开连接按钮 (底部) - 仅在完整模式显示
        if (currentMode == ViewMode.FULL) {
            this.layout.addToFooter(Button.builder(Component.literal("断开连接"), button -> {
                TerracottaApiClient.setIdle();
                wasConnected = false; 
                this.isUiConnected = false;
                this.init(this.minecraft, this.width, this.height);
            }).width(200).build());
        } else {
            // 游戏内模式显示返回按钮
            this.layout.addToFooter(Button.builder(Component.literal("返回"), button -> {
                this.onClose();
            }).width(200).build());
        }
    }

    /**
     * 初始化房客已连接视图
     */
    private void initGuestConnectedContent() {
        LinearLayout content = LinearLayout.vertical().spacing(10);
        content.defaultCellSetting().alignHorizontallyCenter();
        
        content.addChild(new StringWidget(Component.literal("=== 房间玩家列表 ==="), this.font));
        
        addPlayerListToLayout(content);
        
        this.layout.addToContents(content);
        
        // 底部按钮
        LinearLayout footerLayout = LinearLayout.horizontal().spacing(10);
        
        if (lastStateJson != null && lastStateJson.has("url")) {
             String url = lastStateJson.get("url").getAsString();
             // 加入服务器按钮
             footerLayout.addChild(Button.builder(Component.literal("加入"), b -> connectToServer(url)).width(80).build());
             // 进入服务器按钮 (同功能)
             footerLayout.addChild(Button.builder(Component.literal("进入服务器"), b -> connectToServer(url)).width(100).build());
        }

        // 断开连接按钮
        footerLayout.addChild(Button.builder(Component.literal("断开连接"), b -> {
            TerracottaApiClient.setIdle();
            wasConnected = false; 
            this.isUiConnected = false;
            this.onClose();
        }).width(80).build());
        
        // 返回按钮
        footerLayout.addChild(Button.builder(Component.literal("返回"), b -> this.onClose()).width(80).build());
        
        this.layout.addToFooter(footerLayout);
    }

    /**
     * 将玩家列表添加到布局中
     * 
     * @param layout 目标布局
     */
    private void addPlayerListToLayout(LinearLayout layout) {
        if (lastStateJson != null) {
            if (lastStateJson.has("profiles")) {
                try {
                    JsonArray profiles = lastStateJson.getAsJsonArray("profiles");
                    if (profiles.size() > 0) {
                        layout.addChild(new StringWidget(Component.literal("--- 当前玩家 (" + profiles.size() + ") ---"), this.font));
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
                        layout.addChild(new StringWidget(Component.literal("--- 当前玩家 (" + players.size() + ") ---"), this.font));
                        for (var p : players) {
                            String pName = p.getAsString();
                            layout.addChild(new StringWidget(Component.literal(pName), this.font));
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 保存配置到文件
     */
    private void saveConfig() {
        Config.EXTERNAL_TERRACOTTA_PATH.set(this.tempPath);
        Config.AUTO_UPDATE.set(this.tempAutoUpdate);
        Config.AUTO_START_BACKEND.set(this.tempAutoStart);
        Config.CLIENT_SPEC.save();
    }

    /**
     * 连接到指定服务器地址
     * 
     * @param connectUrl 连接地址 (host:port)
     */
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
             // 忽略连接错误
         }
    }

    /**
     * 初始化空闲状态的界面内容
     */
    private void initIdleContent() {
        this.isUiConnected = false;
        LinearLayout contentLayout = LinearLayout.vertical().spacing(15);

        // 1. 加入房间按钮
        contentLayout.addChild(Button.builder(Component.literal("加入房间"), button -> {
            // 优先检查动态端口
            if (TerracottaApiClient.hasDynamicPort()) {
                this.minecraft.setScreen(new JoinScreen(this));
            } else {
                this.minecraft.setScreen(new StartupScreen(this.parent));
            }
        }).width(200).build());

        // 2. 设置按钮
        contentLayout.addChild(Button.builder(Component.literal("设置"), button -> {
            this.minecraft.setScreen(new TerracottaConfigScreen(this));
        }).width(200).build());

        this.layout.addToContents(contentLayout);

        // 3. 退出按钮 (底部)
        this.layout.addToFooter(Button.builder(Component.literal("退出"), button -> {
            this.onClose();
        }).width(200).build());
    }

    /**
     * 每帧更新逻辑
     * 定时检查后端状态
     */
    @Override
    public void tick() {
        super.tick();
        
        // 每秒检查一次后端状态
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

    /**
     * 渲染界面
     */
    @Override
    public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // 在顶部标题下方绘制状态文本
        if (this.currentMode == ViewMode.FULL || this.currentMode == ViewMode.INGAME_INFO) {
            int textY = this.layout.getHeaderHeight() + 5;
            guiGraphics.drawCenteredString(this.font, Component.translatable("terracotta.dashboard.status.fetching").getString().equals(this.backendState) ? 
                this.backendState : Component.translatable("terracotta.dashboard.status_prefix").append(this.backendState).getString(), this.width / 2, textY, 0xAAAAAA);
        }
    }

    /**
     * 更新后端状态显示文本
     * 
     * @param stateJson 状态 JSON 字符串
     */
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
