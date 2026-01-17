package com.multiplayer.terracotta.client.gui;

import com.multiplayer.terracotta.network.NetworkClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 联机大厅界面
 * 玩家连接成功后显示的界面
 */
public class LobbyScreen extends TerracottaBaseScreen {
    private EditBox chatBox;
    private Button sendBtn;

    public LobbyScreen(Screen parent) {
        super(Component.translatable("menu.minecraftterracotta.lobby.title"), parent);
    }

    @Override
    protected void initContent() {
        // 断开连接按钮 -> Footer
        this.layout.addToFooter(Button.builder(Component.literal("断开连接"), (button) -> {
            this.onClose();
        }).width(200).build());

        // 聊天输入框和发送按钮
        // 注意：这里我们手动添加组件，不放入 layout，以便自定义位置（底部）
        this.chatBox = new EditBox(this.font, 20, this.height - 40, this.width - 130, 20, Component.literal("Chat"));
        this.chatBox.setMaxLength(256);
        this.addRenderableWidget(this.chatBox);

        this.sendBtn = Button.builder(Component.literal("发送"), b -> {
            String msg = chatBox.getValue();
            if (!msg.isEmpty()) {
                chatBox.setValue("");
                // TODO: 发送聊天数据包
            }
        }).bounds(this.width - 100, this.height - 40, 80, 20).build();
        this.addRenderableWidget(this.sendBtn);
    }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        // 手动调整聊天框位置
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
        
        // 临时内容
        guiGraphics.drawCenteredString(this.font, Component.literal("欢迎来到陶瓦联机大厅"), this.width / 2, this.height / 2 - 20, 0x00FF00);
    }

    @Override
    public void onClose() {
        // 断开连接
        NetworkClient.getInstance().close();
        super.onClose();
    }
}
