package com.multiplayer.ender.client.gui;

import com.multiplayer.ender.Config;
import com.multiplayer.ender.network.EnderApiClient;
import com.multiplayer.ender.logic.ProcessLauncher;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.EditGameRulesScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import org.jetbrains.annotations.NotNull;

/**
 * 房间设置界面。
 * 用于配置游戏规则、PVP、难度以及核心设置。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public class RoomSettingsScreen extends Screen {
    private final Screen parent;
    private String tempPath = "";
    private boolean tempAutoUpdate = false;
    private boolean tempAutoStart = false;

    /**
     * 构造函数。
     *
     * @param parent 父屏幕
     */
    public RoomSettingsScreen(Screen parent) {
        super(Component.literal("房间设置"));
        this.parent = parent;
    }

    /**
     * 初始化界面。
     * 创建设置按钮和游戏规则配置项。
     */
    @Override
    protected void init() {
        tempPath = Config.EXTERNAL_ender_PATH.get();
        tempAutoUpdate = Config.AUTO_UPDATE.get();
        tempAutoStart = Config.AUTO_START_BACKEND.get();

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int startY = centerY - 80;

        this.addRenderableWidget(Button.builder(Component.literal("自动更新: " + (tempAutoUpdate ? "开" : "关")), b -> {
            tempAutoUpdate = !tempAutoUpdate;
            b.setMessage(Component.literal("自动更新: " + (tempAutoUpdate ? "开" : "关")));
        }).bounds(centerX - 90, startY, 180, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("自动启动: " + (tempAutoStart ? "开" : "关")), b -> {
            tempAutoStart = !tempAutoStart;
            b.setMessage(Component.literal("自动启动: " + (tempAutoStart ? "开" : "关")));
        }).bounds(centerX - 90, startY + 24, 180, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("应用设置"), b -> {
            Config.AUTO_UPDATE.set(tempAutoUpdate);
            Config.AUTO_START_BACKEND.set(tempAutoStart);
            Config.CLIENT_SPEC.save();
            b.setMessage(Component.literal("设置已保存"));
        }).bounds(centerX - 90, startY + 48, 180, 20).build());

        int currentY = startY + 82;

        IntegratedServer server = this.minecraft.getSingleplayerServer();
        boolean isHost = server != null && server.isPublished();

        if (isHost) {
            this.addRenderableWidget(Button.builder(Component.literal("难度: " + server.getWorldData().getDifficulty().getKey()), b -> {
                Difficulty current = server.getWorldData().getDifficulty();
                Difficulty next = Difficulty.byId((current.getId() + 1) % 4);
                server.setDifficulty(next, true);
                b.setMessage(Component.literal("难度: " + next.getKey()));
            }).bounds(centerX - 90, currentY, 180, 20).build());
            currentY += 24;

            boolean pvp = server.isPvpAllowed();
            this.addRenderableWidget(Button.builder(Component.literal("PVP: " + (pvp ? "允许" : "禁止")), b -> {
                boolean newPvp = !server.isPvpAllowed();
                server.setPvpAllowed(newPvp);
                b.setMessage(Component.literal("PVP: " + (newPvp ? "允许" : "禁止")));
            }).bounds(centerX - 90, currentY, 180, 20).build());
            currentY += 24;

            this.addRenderableWidget(Button.builder(Component.literal("游戏规则"), b -> {
                this.minecraft.setScreen(new EditGameRulesScreen(server.getGameRules().copy(), (optionalRules) -> {
                    this.minecraft.setScreen(this);
                    optionalRules.ifPresent(rules -> server.getGameRules().assignFrom(rules, server));
                }));
            }).bounds(centerX - 90, currentY, 180, 20).build());
            currentY += 24;
        }

        Button disconnectBtn = Button.builder(Component.literal(isHost ? "关闭房间" : "退出联机"), b -> {
            EnderApiClient.setIdle();
            new Thread(ProcessLauncher::stop, "Ender-Stopper").start();
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
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 100, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}



