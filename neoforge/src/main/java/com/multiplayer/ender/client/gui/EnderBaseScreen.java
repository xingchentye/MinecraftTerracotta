package com.multiplayer.ender.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Ender 模组屏幕基类。
 * 提供统一的头部和底部布局管理，简化屏幕开发。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public abstract class EnderBaseScreen extends Screen {
    /** 父屏幕，用于返回 */
    protected final Screen parent;
    /** 头部和底部布局管理器 */
    protected HeaderAndFooterLayout layout;

    /**
     * 构造函数。
     *
     * @param title  屏幕标题
     * @param parent 父屏幕
     */
    protected EnderBaseScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    /**
     * 初始化屏幕。
     * 创建布局，添加标题，调用 {@link #initContent()} 初始化内容，并排列元素。
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
     * 初始化具体内容。
     * 子类应在此方法中向 {@link #layout} 添加内容组件。
     */
    protected abstract void initContent();

    /**
     * 重新排列元素。
     * 当屏幕大小改变时调用。
     */
    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }

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

