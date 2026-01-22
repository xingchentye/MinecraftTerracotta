package com.multiplayer.ender.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 连接中屏幕（Fabric）。
 * 显示连接状态，通常在加入房间或启动托管时短暂显示。
 */
public class ConnectingScreen extends EnderBaseScreen {
    private Text status = Text.translatable("connect.connecting");

    public ConnectingScreen(Screen parent) {
        super(Text.translatable("menu.ender_online.title"), parent);
    }

    /**
     * 初始化内容。
     * 仅添加一个取消按钮。
     */
    @Override
    protected void initContent() {
        this.layout.addFooter(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> {
            this.close();
        }).width(200).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float partialTick) {
        super.render(context, mouseX, mouseY, partialTick);
        context.drawCenteredTextWithShadow(this.textRenderer, this.status, this.width / 2, this.height / 2 - 10, 0xAAAAAA);
    }

    /**
     * 设置状态文本。
     *
     * @param status 新的状态文本
     */
    public void setStatus(Text status) {
        this.status = status;
    }
}


