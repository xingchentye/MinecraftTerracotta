package com.multiplayer.terracotta.client.gui;

import com.multiplayer.terracotta.ConfigForge;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TerracottaConfigScreen extends TerracottaBaseScreen {
    private String tempPath;
    private boolean tempAutoUpdate;
    private boolean tempAutoStart;

    public TerracottaConfigScreen(Screen parent) {
        super(Component.literal("陶瓦联机设置"), parent);
        this.tempPath = ConfigForge.EXTERNAL_TERRACOTTA_PATH.get();
        this.tempAutoUpdate = ConfigForge.AUTO_UPDATE.get();
        this.tempAutoStart = ConfigForge.AUTO_START_BACKEND.get();
    }

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

    private void saveConfig() {
        ConfigForge.EXTERNAL_TERRACOTTA_PATH.set(this.tempPath);
        ConfigForge.AUTO_UPDATE.set(this.tempAutoUpdate);
        ConfigForge.AUTO_START_BACKEND.set(this.tempAutoStart);
        ConfigForge.CLIENT_SPEC.save();
    }
}

