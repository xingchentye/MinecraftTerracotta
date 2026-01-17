package com.multiplayer.terracotta.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 连接状态界面
 * 显示正在连接服务器的状态，并提供取消选项
 */
public class ConnectingScreen extends TerracottaBaseScreen {
    private Component status = Component.translatable("connect.connecting");

    public ConnectingScreen(Screen parent) {
        super(Component.translatable("menu.minecraftterracotta.title"), parent);
    }

    @Override
    protected void initContent() {
        // 添加取消按钮 -> Footer
        this.layout.addToFooter(Button.builder(Component.translatable("gui.cancel"), (button) -> {
            this.onClose();
        }).width(200).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // 绘制状态
        guiGraphics.drawCenteredString(this.font, this.status, this.width / 2, this.height / 2 - 10, 0xAAAAAA);
    }
    
    public void setStatus(Component status) {
        this.status = status;
    }
}
