package com.multiplayer.ender.client.gui;

import com.multiplayer.ender.network.NetworkClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 末影联机大厅界面。
 * 提供简单的聊天和状态展示功能。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public class LobbyScreen extends EnderBaseScreen {
    /** 聊天输入框 */
    private EditBox chatBox;
    /** 发送按钮 */
    private Button sendBtn;

    /**
     * 构造函数。
     *
     * @param parent 父屏幕
     */
    public LobbyScreen(Screen parent) {
        super(Component.translatable("menu.ender_online.lobby.title"), parent);
    }

    /**
     * 初始化界面内容。
     * 创建聊天框和按钮。
     */
    @Override
    protected void initContent() {
        this.layout.addToFooter(Button.builder(Component.literal("断开连接"), (button) -> {
            this.onClose();
        }).width(200).build());

        this.chatBox = new EditBox(this.font, 20, this.height - 40, this.width - 130, 20, Component.literal("Chat"));
        this.chatBox.setMaxLength(256);
        this.addRenderableWidget(this.chatBox);

        this.sendBtn = Button.builder(Component.literal("发送"), b -> {
            String msg = chatBox.getValue();
            if (!msg.isEmpty()) {
                chatBox.setValue("");
            }
        }).bounds(this.width - 100, this.height - 40, 80, 20).build();
        this.addRenderableWidget(this.sendBtn);
    }

    /**
     * 重新排列元素。
     * 确保聊天框和按钮位于底部。
     */
    @Override
    protected void repositionElements() {
        super.repositionElements();
        if (this.chatBox != null) {
            this.chatBox.setX(20);
            this.chatBox.setY(this.height - 40);
            this.chatBox.setWidth(this.width - 130);
        }
        if (this.sendBtn != null) {
            this.sendBtn.setX(this.width - 100);
            this.sendBtn.setY(this.height - 40);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        guiGraphics.drawCenteredString(this.font, Component.literal("欢迎来到末影联机大厅"), this.width / 2, this.height / 2 - 20, 0x00FF00);
    }

    /**
     * 关闭屏幕。
     * 断开网络连接并返回。
     */
    @Override
    public void onClose() {
        NetworkClient.getInstance().close();
        super.onClose();
    }
}



