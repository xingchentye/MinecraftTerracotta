package com.multiplayer.ender.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * 末影联机基础屏幕类。
 * 所有末影联机的 GUI 屏幕都应继承此类，提供了统一的头部和尾部布局管理。
 */
public abstract class EnderBaseScreen extends Screen {
    /** 父屏幕，用于关闭当前屏幕时返回 */
    protected final Screen parent;
    /** 头部和尾部布局管理器 */
    protected HeaderAndFooterLayout layout;

    /**
     * 构造函数。
     *
     * @param title 屏幕标题
     * @param parent 父屏幕
     */
    protected EnderBaseScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    /**
     * 初始化屏幕。
     * 设置布局管理器，添加标题，调用子类内容初始化，并重新定位元素。
     */
    @Override
    protected void init() {
        this.layout = new HeaderAndFooterLayout(this, 30, 30);
        this.layout.addTitleHeader(this.title, this.font);
        this.initContent();
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    /**
     * 初始化屏幕中间内容区域。
     * 子类必须实现此方法以添加特定的控件。
     */
    protected abstract void initContent();

    /**
     * 重新定位屏幕元素。
     * 当屏幕大小改变时调用布局管理器的 arrangeElements。
     */
    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }

    /**
     * 渲染屏幕。
     *
     * @param guiGraphics GUI 图形上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param partialTick 渲染部分刻
     */
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    /**
     * 关闭屏幕。
     * 返回到父屏幕。
     */
    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}

