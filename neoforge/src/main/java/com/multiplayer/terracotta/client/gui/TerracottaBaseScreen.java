package com.multiplayer.terracotta.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public abstract class TerracottaBaseScreen extends Screen {
    protected final Screen parent;
    protected HeaderAndFooterLayout layout;

    protected TerracottaBaseScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.layout = new HeaderAndFooterLayout(this, 30, 30);
        this.layout.addTitleHeader(this.title, this.font);
        this.initContent();
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    protected abstract void initContent();

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}

