package com.multiplayer.terracotta.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ConnectingScreen extends TerracottaBaseScreen {
    private Text status = Text.translatable("connect.connecting");

    public ConnectingScreen(Screen parent) {
        super(Text.translatable("menu.minecraftterracotta.title"), parent);
    }

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

    public void setStatus(Text status) {
        this.status = status;
    }
}

