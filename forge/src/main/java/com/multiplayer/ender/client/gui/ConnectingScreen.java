package com.multiplayer.ender.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConnectingScreen extends EnderBaseScreen {
    private Component status = Component.translatable("connect.connecting");

    public ConnectingScreen(Screen parent) {
        super(Component.translatable("menu.ender_online.title"), parent);
    }

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

    public void setStatus(Component status) {
        this.status = status;
    }
}


