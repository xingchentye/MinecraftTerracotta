package com.multiplayer.terracotta.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * 陶瓦联机所有 GUI 的基类
 * 提供统一的布局和风格
 *
 * @author xingchentye
 */
public abstract class TerracottaBaseScreen extends Screen {
    protected final Screen parent;
    protected HeaderAndFooterLayout layout;

    /**
     * 构造函数
     *
     * @param title 屏幕标题组件
     * @param parent 父屏幕对象 (用于返回)
     */
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

    /**
     * 初始化内容
     * 子类应在此方法中向 layout 添加内容 (addToHeader, addToContents, addToFooter)
     */
    protected abstract void initContent();

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 渲染背景
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
