package com.multiplayer.ender.client.gui;

import com.multiplayer.ender.Config;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 设置界面。
 * 用于配置核心路径、自动更新和自动启动选项。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public class EnderConfigScreen extends EnderBaseScreen {
    /** 临时存储的核心路径 */
    private String tempPath;
    /** 临时存储的自动更新开关 */
    private boolean tempAutoUpdate;
    /** 临时存储的自动启动开关 */
    private boolean tempAutoStart;

    /**
     * 构造函数。
     *
     * @param parent 父屏幕
     */
    public EnderConfigScreen(Screen parent) {
        super(Component.literal("末影联机设置"), parent);
        this.tempPath = Config.EXTERNAL_ender_PATH.get();
        this.tempAutoUpdate = Config.AUTO_UPDATE.get();
        this.tempAutoStart = Config.AUTO_START_BACKEND.get();
    }

    /**
     * 初始化界面内容。
     * 创建配置表单和保存/取消按钮。
     */
    @Override
    protected void initContent() {
        GridLayout grid = new GridLayout();
        grid.defaultCellSetting().paddingBottom(8);

        grid.addChild(new StringWidget(Component.literal("核心路径"), this.font), 0, 0);
        EditBox pathBox = new EditBox(this.font, 0, 0, 200, 20, Component.literal("Path"));
        pathBox.setValue(this.tempPath);
        pathBox.setMaxLength(1024);
        pathBox.setResponder(val -> this.tempPath = val);
        grid.addChild(pathBox, 0, 1);

        Button autoUpdateBtn = Button.builder(Component.literal("自动更新: " + (this.tempAutoUpdate ? "开" : "关")), button -> {
            this.tempAutoUpdate = !this.tempAutoUpdate;
            button.setMessage(Component.literal("自动更新: " + (this.tempAutoUpdate ? "开" : "关")));
        }).width(150).build();
        grid.addChild(autoUpdateBtn, 1, 0, 1, 2);

        Button autoStartBtn = Button.builder(Component.literal("自动启动: " + (this.tempAutoStart ? "开" : "关")), button -> {
            this.tempAutoStart = !this.tempAutoStart;
            button.setMessage(Component.literal("自动启动: " + (this.tempAutoStart ? "开" : "关")));
        }).width(150).build();
        grid.addChild(autoStartBtn, 2, 0, 1, 2);

        this.layout.addToContents(grid);

        LinearLayout footerButtons = LinearLayout.horizontal().spacing(8);
        footerButtons.addChild(Button.builder(Component.literal("保存并返回"), button -> {
            this.saveConfig();
            this.onClose();
        }).width(150).build());

        footerButtons.addChild(Button.builder(Component.literal("取消"), button -> {
            this.onClose();
        }).width(150).build());

        this.layout.addToFooter(footerButtons);
    }

    /**
     * 保存配置。
     * 将临时变量写入配置文件并持久化。
     */
    private void saveConfig() {
        Config.EXTERNAL_ender_PATH.set(this.tempPath);
        Config.AUTO_UPDATE.set(this.tempAutoUpdate);
        Config.AUTO_START_BACKEND.set(this.tempAutoStart);
        Config.CLIENT_SPEC.save();
    }
}




