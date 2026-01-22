package com.multiplayer.ender.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 连接等待界面。
 * 在网络连接建立过程中显示的过渡屏幕。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public class ConnectingScreen extends EnderBaseScreen {
    /** 连接状态文本 */
    private Component status = Component.translatable("connect.connecting");

    /**
     * 构造函数。
     *
     * @param parent 父屏幕
     */
    public ConnectingScreen(Screen parent) {
        super(Component.translatable("menu.ender_online.title"), parent);
    }

    /**
     * 初始化界面内容。
     * 添加取消按钮。
     */
    @Override
    protected void initContent() {
        this.layout.addToFooter(Button.builder(Component.translatable("gui.cancel"), (button) -> {
            this.onClose();
        }).width(200).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.status, this.width / 2, this.height / 2 - 10, 0xAAAAAA);
    }
    
    /**
     * 更新状态文本。
     *
     * @param status 新的状态文本
     */
    public void setStatus(Component status) {
        this.status = status;
    }
}


