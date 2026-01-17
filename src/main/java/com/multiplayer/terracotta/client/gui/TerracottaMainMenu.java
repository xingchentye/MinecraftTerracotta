package com.multiplayer.terracotta.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 陶瓦联机主菜单
 * 包含启动连接、设置等功能
 */
public class TerracottaMainMenu extends TerracottaBaseScreen {
    private static boolean autoStartTriggered = false;

    public TerracottaMainMenu(Screen parent) {
        super(Component.literal("陶瓦联机中心"), parent);
    }

    @Override
    protected void initContent() {
        LinearLayout contentLayout = LinearLayout.vertical().spacing(12);

        contentLayout.addChild(Button.builder(Component.literal("联机设置"), button -> {
            this.minecraft.setScreen(new TerracottaConfigScreen(this));
        }).width(200).build());

        this.layout.addToContents(contentLayout);

        this.layout.addToFooter(Button.builder(Component.literal("返回"), button -> {
            this.onClose();
        }).width(200).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        String targetInfo = "当前目标: 本地 Terracotta 后端";
        guiGraphics.drawCenteredString(this.font, targetInfo, this.width / 2, 50, 0xAAAAAA);
    }
}
