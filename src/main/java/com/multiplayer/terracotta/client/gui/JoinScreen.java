package com.multiplayer.terracotta.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.multiplayer.terracotta.network.TerracottaApiClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * 房客加入房间界面
 * 允许用户输入邀请码并加入现有房间
 * 
 * @author xingchentye
 */
public class JoinScreen extends TerracottaBaseScreen {
    /** 房间码输入框 */
    private EditBox roomCodeBox;
    /** 状态提示文本 */
    private Component statusText = Component.translatable("terracotta.join.status.enter_code");
    /** 是否正在处理请求 */
    private boolean isWorking = false;
    /** 加入按钮 */
    private Button joinBtn;
    /** 上次检查状态的时间戳 */
    private long lastStateCheck = 0;
    /** Gson 实例 */
    private static final Gson GSON = new Gson();
    /** 自动加入的房间码 (可选) */
    private String autoJoinCode = null;
    /** 是否保持连接 (加入成功后设为 true) */
    private boolean keepConnection = false;

    /**
     * 构造函数
     * 
     * @param parent 父界面
     */
    public JoinScreen(Screen parent) {
        super(Component.translatable("terracotta.join.title"), parent);
    }

    /**
     * 构造函数 (带自动加入码)
     * 
     * @param parent 父界面
     * @param autoJoinCode 预填的房间邀请码
     */
    public JoinScreen(Screen parent, String autoJoinCode) {
        this(parent);
        this.autoJoinCode = autoJoinCode;
    }

    /**
     * 初始化界面内容
     * 创建输入框、按钮等组件
     */
    @Override
    protected void initContent() {
        LinearLayout contentLayout = LinearLayout.vertical().spacing(12);

        // 房间码输入框
        this.roomCodeBox = new EditBox(this.font, 0, 0, 200, 20, Component.translatable("terracotta.join.input.label"));
        this.roomCodeBox.setMaxLength(128);
        this.roomCodeBox.setHint(Component.translatable("terracotta.join.input.hint"));
        contentLayout.addChild(this.roomCodeBox);

        // 加入按钮
        this.joinBtn = Button.builder(Component.translatable("terracotta.common.join"), button -> {
            this.joinRoom();
        }).width(200).build();
        contentLayout.addChild(this.joinBtn);

        this.layout.addToContents(contentLayout);

        // 返回按钮
        this.layout.addToFooter(Button.builder(Component.translatable("terracotta.common.back"), button -> {
            this.onClose();
        }).width(200).build());
        
        // 如果有自动加入码，填入并尝试加入
        if (this.autoJoinCode != null && !this.autoJoinCode.isEmpty()) {
            this.roomCodeBox.setValue(this.autoJoinCode);
            // 延迟一帧执行，确保界面初始化完成
            this.minecraft.execute(() -> this.joinRoom());
        }
    }

    /**
     * 发送加入房间请求
     */
    private void joinRoom() {
        String roomCode = roomCodeBox.getValue();
        if (roomCode.isEmpty()) {
            statusText = Component.translatable("terracotta.join.error.empty");
            return;
        }

        if (isWorking) return;
        isWorking = true;
        statusText = Component.translatable("terracotta.join.status.requesting");
        joinBtn.active = false;
        
        String playerName = this.minecraft.getUser().getName();

        TerracottaApiClient.joinRoom(roomCode, playerName).thenAccept(success -> {
            if (success) {
                statusText = Component.translatable("terracotta.join.status.success");
                // 开始轮询 guest-ok
            } else {
                statusText = Component.translatable("terracotta.join.status.failed").append("Unknown error");
                isWorking = false;
                joinBtn.active = true;
            }
        });
    }

    /**
     * 每帧更新逻辑
     * 用于轮询连接状态
     */
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

    /**
     * 检查后端连接状态
     * 如果连接成功 (guest-ok)，则跳转到仪表盘
     */
    private void checkConnectionStatus() {
        TerracottaApiClient.getState().thenAccept(stateJson -> {
            if (stateJson == null) return;
            try {
                JsonObject json = GSON.fromJson(stateJson, JsonObject.class);
                if (json.has("state")) {
                    String state = json.get("state").getAsString();
                    
                    if ("guest-ok".equals(state)) {
                         if (json.has("url")) {
                             statusText = Component.translatable("terracotta.join.status.connected");
                             isWorking = false;
                             keepConnection = true;
                             // 如果父界面是仪表盘，则更新仪表盘状态并返回
                             if (this.parent instanceof TerracottaDashboard dashboard) {
                                 dashboard.setConnected(true);
                                 this.minecraft.execute(() -> {
                                     dashboard.updateFromState(json);
                                     this.minecraft.setScreen(dashboard);
                                 });
                             } else {
                                 this.minecraft.execute(this::onClose);
                             }
                         }
                    } else if ("guest-connecting".equals(state)) {
                        statusText = Component.translatable("terracotta.join.status.connecting_p2p");
                    } else if ("guest-starting".equals(state)) {
                        statusText = Component.translatable("terracotta.join.status.initializing");
                    } else if ("waiting".equals(state)) {
                         // 等待中状态，可能是请求超时或还在处理
                    }
                }
            } catch (Exception e) {
                // 忽略解析错误
            }
        });
    }

    /**
     * 关闭界面处理
     * 如果未成功连接，则恢复空闲状态
     */
    @Override
    public void onClose() {
        // 如果未成功连接且正在进行操作，退出时应取消操作
        if (isWorking && !keepConnection) {
             TerracottaApiClient.setIdle();
        }
        super.onClose();
    }

    /**
     * 渲染界面
     * 
     * @param guiGraphics 图形上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param partialTick 渲染分帧时间
     */
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // 在加入按钮下方绘制状态文本
        if (this.joinBtn != null) {
            int textY = this.joinBtn.getY() + this.joinBtn.getHeight() + 10;
            guiGraphics.drawCenteredString(this.font, this.statusText, this.width / 2, textY, 0xAAAAAA);
        }
    }
}
