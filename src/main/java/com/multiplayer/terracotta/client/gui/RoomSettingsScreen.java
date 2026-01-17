package com.multiplayer.terracotta.client.gui;

import com.multiplayer.terracotta.Config;
import com.multiplayer.terracotta.network.TerracottaApiClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.EditGameRulesScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import org.jetbrains.annotations.NotNull;

public class RoomSettingsScreen extends Screen {
    private final Screen parent;
    private String tempPath = "";
    private boolean tempAutoUpdate = false;
    private boolean tempAutoStart = false;

    public RoomSettingsScreen(Screen parent) {
        super(Component.literal("房间设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // 初始化配置变量
        tempPath = Config.EXTERNAL_TERRACOTTA_PATH.get();
        tempAutoUpdate = Config.AUTO_UPDATE.get();
        tempAutoStart = Config.AUTO_START_BACKEND.get();

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int startY = centerY - 80;

        // 标题
        // 渲染在 render 方法中

        // 自动更新 (上移填补输入框位置)
        this.addRenderableWidget(Button.builder(Component.literal("自动更新: " + (tempAutoUpdate ? "开" : "关")), b -> {
            tempAutoUpdate = !tempAutoUpdate;
            b.setMessage(Component.literal("自动更新: " + (tempAutoUpdate ? "开" : "关")));
        }).bounds(centerX - 90, startY, 180, 20).build());

        // 自动启动
        this.addRenderableWidget(Button.builder(Component.literal("自动启动: " + (tempAutoStart ? "开" : "关")), b -> {
            tempAutoStart = !tempAutoStart;
            b.setMessage(Component.literal("自动启动: " + (tempAutoStart ? "开" : "关")));
        }).bounds(centerX - 90, startY + 24, 180, 20).build());

        // 应用设置
        this.addRenderableWidget(Button.builder(Component.literal("应用设置"), b -> {
            // Config.EXTERNAL_TERRACOTTA_PATH.set(tempPath); // 路径不再通过 GUI 修改
            Config.AUTO_UPDATE.set(tempAutoUpdate);
            Config.AUTO_START_BACKEND.set(tempAutoStart);
            Config.CLIENT_SPEC.save();
            b.setMessage(Component.literal("设置已保存"));
        }).bounds(centerX - 90, startY + 48, 180, 20).build());

        int currentY = startY + 82;

        // 房主特有设置
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        boolean isHost = server != null && server.isPublished();

        if (isHost) {
            // 难度
            this.addRenderableWidget(Button.builder(Component.literal("难度: " + server.getWorldData().getDifficulty().getKey()), b -> {
                Difficulty current = server.getWorldData().getDifficulty();
                Difficulty next = Difficulty.byId((current.getId() + 1) % 4);
                server.setDifficulty(next, true);
                b.setMessage(Component.literal("难度: " + next.getKey()));
            }).bounds(centerX - 90, currentY, 180, 20).build());
            currentY += 24;

            // PVP
            boolean pvp = server.isPvpAllowed();
            this.addRenderableWidget(Button.builder(Component.literal("PVP: " + (pvp ? "允许" : "禁止")), b -> {
                boolean newPvp = !server.isPvpAllowed();
                server.setPvpAllowed(newPvp);
                b.setMessage(Component.literal("PVP: " + (newPvp ? "允许" : "禁止")));
            }).bounds(centerX - 90, currentY, 180, 20).build());
            currentY += 24;

            // 游戏规则
            this.addRenderableWidget(Button.builder(Component.literal("游戏规则"), b -> {
                // 打开规则编辑页面，返回时回到本页面
                this.minecraft.setScreen(new EditGameRulesScreen(server.getGameRules().copy(), (optionalRules) -> {
                    this.minecraft.setScreen(this); // 返回 RoomSettingsScreen
                    optionalRules.ifPresent(rules -> server.getGameRules().assignFrom(rules, server));
                }));
            }).bounds(centerX - 90, currentY, 180, 20).build());
            currentY += 24;
        }

        // 断开/关闭 / 返回
        Button disconnectBtn = Button.builder(Component.literal(isHost ? "关闭房间" : "退出联机"), b -> {
            TerracottaApiClient.setIdle();
            this.onClose();
        }).bounds(centerX - 90, currentY, 88, 20).build();
        this.addRenderableWidget(disconnectBtn);

        Button backBtn = Button.builder(Component.literal("返回"), b -> {
            this.onClose();
        }).bounds(centerX + 2, currentY, 88, 20).build();
        this.addRenderableWidget(backBtn);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 渲染半透明黑色背景 (而不是纯黑或泥土)
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 100, 0xFFFFFF);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 使用默认的半透明渐变背景
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
