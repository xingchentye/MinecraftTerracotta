package com.multiplayer.ender.client.gui;

import com.multiplayer.ender.fabric.FabricConfig;
import com.multiplayer.ender.network.EnderApiClient;
import com.multiplayer.ender.logic.ProcessLauncher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.EditGameRulesScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.world.Difficulty;
import org.jetbrains.annotations.NotNull;

/**
 * 房间设置屏幕（Fabric）。
 * 允许玩家配置房间属性（如难度、PVP）以及模组的全局设置。
 */
public class RoomSettingsScreen extends Screen {
    private final Screen parent;
    private String tempPath = "";
    private boolean tempAutoUpdate = false;
    private boolean tempAutoStart = false;

    public RoomSettingsScreen(Screen parent) {
        super(Text.literal("房间设置"));
        this.parent = parent;
    }

    /**
     * 初始化屏幕。
     * 加载当前配置，创建配置按钮（自动更新、自动启动），
     * 如果是房主，则添加难度和 PVP 设置按钮。
     */
    @Override
    protected void init() {
        tempPath = FabricConfig.getExternalEnderPath();
        tempAutoUpdate = FabricConfig.isAutoUpdate();
        tempAutoStart = FabricConfig.isAutoStartBackend();

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int startY = centerY - 80;

        this.addDrawableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(
                Text.literal("自动更新: " + (tempAutoUpdate ? "开" : "关")),
                button -> {
                    tempAutoUpdate = !tempAutoUpdate;
                    button.setMessage(Text.literal("自动更新: " + (tempAutoUpdate ? "开" : "关")));
                }
        ).dimensions(centerX - 90, startY, 180, 20).build());

        this.addDrawableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(
                Text.literal("自动启动: " + (tempAutoStart ? "开" : "关")),
                button -> {
                    tempAutoStart = !tempAutoStart;
                    button.setMessage(Text.literal("自动启动: " + (tempAutoStart ? "开" : "关")));
                }
        ).dimensions(centerX - 90, startY + 24, 180, 20).build());

        this.addDrawableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(
                Text.literal("应用设置"),
                button -> {
                    FabricConfig.setAutoUpdate(tempAutoUpdate);
                    FabricConfig.setAutoStartBackend(tempAutoStart);
                    button.setMessage(Text.literal("设置已保存"));
                }
        ).dimensions(centerX - 90, startY + 48, 180, 20).build());

        int currentY = startY + 82;

        MinecraftClient client = MinecraftClient.getInstance();
        MinecraftServer server = client.getServer();
        boolean isHost = server != null && client.world != null;

        if (isHost) {
            Difficulty currentDifficulty = client.world.getDifficulty();
            this.addDrawableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(
                    Text.literal("难度: " + currentDifficulty.getName()),
                    button -> {
                        if (client.world == null || client.getServer() == null) {
                            return;
                        }
                        Difficulty cur = client.world.getDifficulty();
                        int nextId = (cur.getId() + 1) % Difficulty.values().length;
                        Difficulty next = Difficulty.byId(nextId);
                        client.getServer().setDifficulty(next, true);
                        button.setMessage(Text.literal("难度: " + next.getName()));
                    }
            ).dimensions(centerX - 90, currentY, 180, 20).build());
            currentY += 24;

            boolean pvp = queryPvpEnabled(server);
            this.addDrawableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(
                    Text.literal("PVP: " + (pvp ? "允许" : "禁止")),
                    button -> {
                        boolean newPvp = !queryPvpEnabled(server);
                        if (setPvpEnabled(server, newPvp)) {
                            button.setMessage(Text.literal("PVP: " + (newPvp ? "允许" : "禁止")));
                        } else {
                            com.multiplayer.ender.fabric.MinecraftEnderClient.showToast(
                                    Text.literal("提示"),
                                    Text.literal("当前版本暂不支持在游戏内修改 PVP，请在 server.properties 中修改")
                            );
                        }
                    }
            ).dimensions(centerX - 90, currentY, 180, 20).build());
            currentY += 24;

            this.addDrawableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(
                    Text.literal("游戏规则"),
                    button -> {
                        if (client.getServer() == null) {
                            return;
                        }
                        MinecraftServer s = client.getServer();
                        client.setScreen(new EditGameRulesScreen(s.getGameRules(), optionalRules -> {
                            client.setScreen(this);
                        }));
                    }
            ).dimensions(centerX - 90, currentY, 180, 20).build());
            currentY += 24;
        }

        net.minecraft.client.gui.widget.ButtonWidget disconnectBtn = net.minecraft.client.gui.widget.ButtonWidget.builder(
                Text.literal(isHost ? "关闭房间" : "退出联机"),
                button -> {
                    EnderApiClient.setIdle();
                    new Thread(ProcessLauncher::stop, "Ender-Stopper").start();
                    this.close();
                }
        ).dimensions(centerX - 90, currentY, 88, 20).build();
        this.addDrawableChild(disconnectBtn);

        net.minecraft.client.gui.widget.ButtonWidget backBtn = net.minecraft.client.gui.widget.ButtonWidget.builder(
                Text.literal("返回"),
                button -> this.close()
        ).dimensions(centerX + 2, currentY, 88, 20).build();
        this.addDrawableChild(backBtn);
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

    @Override
    public void render(@NotNull DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 100, 0xFFFFFF);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(this.parent);
    }
}


