package com.multiplayer.ender.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

public abstract class EnderBaseScreen extends Screen {
    protected final Screen parent;
    protected ThreePartsLayoutWidget layout;

    protected EnderBaseScreen(Text title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.layout = new ThreePartsLayoutWidget(this);
        this.layout.addHeader(this.title, this.textRenderer);
        this.initContent();
        this.layout.forEachChild(this::addDrawableChild);
        this.repositionElements();
    }

    protected abstract void initContent();

    protected void repositionElements() {
        this.layout.refreshPositions();
    }

    @Override
    public void render(@NotNull DrawContext context, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(context, mouseX, mouseY, partialTick);
        super.render(context, mouseX, mouseY, partialTick);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    public void onClose() {
        this.close();
    }
}


