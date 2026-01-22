package com.multiplayer.ender.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

/**
 * 末影联机基础屏幕类（Fabric）。
 * 所有末影联机的 GUI 屏幕都应继承此类，提供了统一的三段式布局管理。
 */
public abstract class EnderBaseScreen extends Screen {
    protected final Screen parent;
    protected ThreePartsLayoutWidget layout;

    /**
     * 构造函数。
     *
     * @param title 屏幕标题
     * @param parent 父屏幕
     */
    protected EnderBaseScreen(Text title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    /**
     * 初始化屏幕。
     * 设置三段式布局，添加标题，调用子类内容初始化，并重新定位元素。
     */
    @Override
    protected void init() {
        this.layout = new ThreePartsLayoutWidget(this);
        this.layout.addHeader(this.title, this.textRenderer);
        this.initContent();
        this.layout.forEachChild(this::addDrawableChild);
        this.repositionElements();
    }

    /**
     * 初始化屏幕中间内容区域。
     * 子类必须实现此方法以添加特定的控件。
     */
    protected abstract void initContent();

    /**
     * 重新定位屏幕元素。
     * 刷新布局位置。
     */
    protected void repositionElements() {
        this.layout.refreshPositions();
    }

    /**
     * 渲染屏幕。
     * 绘制背景和内容。
     *
     * @param context 绘图上下文
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @param partialTick 部分刻
     */
    @Override
    public void render(@NotNull DrawContext context, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(context, mouseX, mouseY, partialTick);
        super.render(context, mouseX, mouseY, partialTick);
    }

    /**
     * 关闭屏幕。
     * 返回到父屏幕。
     */
    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    /**
     * 关闭屏幕（兼容方法）。
     */
    public void onClose() {
        this.close();
    }
}


