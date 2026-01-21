package com.multiplayer.ender.client.gui;

import com.multiplayer.ender.fabric.FabricConfig;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.gui.widget.GridWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class EnderConfigScreen extends EnderBaseScreen {
    private String tempPath;
    private boolean tempAutoUpdate;
    private boolean tempAutoStart;

    public EnderConfigScreen(Screen parent) {
        super(Text.literal("末影联机设置"), parent);
        this.tempPath = FabricConfig.getExternalEnderPath();
        this.tempAutoUpdate = FabricConfig.isAutoUpdate();
        this.tempAutoStart = FabricConfig.isAutoStartBackend();
    }

    @Override
    protected void initContent() {
        DirectionalLayoutWidget content = DirectionalLayoutWidget.vertical().spacing(8);
        content.getMainPositioner().alignHorizontalCenter();

        DirectionalLayoutWidget pathRow = DirectionalLayoutWidget.horizontal().spacing(8);
        pathRow.add(new TextWidget(Text.literal("核心路径"), this.textRenderer));
        TextFieldWidget pathBox = new TextFieldWidget(this.textRenderer, 200, 20, Text.literal("Path"));
        pathBox.setText(this.tempPath);
        pathBox.setMaxLength(1024);
        pathBox.setChangedListener(val -> this.tempPath = val);
        pathRow.add(pathBox);
        content.add(pathRow);

        DirectionalLayoutWidget togglesRow = DirectionalLayoutWidget.horizontal().spacing(8);
        ButtonWidget autoUpdateBtn = ButtonWidget.builder(Text.literal("自动更新: " + (this.tempAutoUpdate ? "开" : "关")), button -> {
            this.tempAutoUpdate = !this.tempAutoUpdate;
            button.setMessage(Text.literal("自动更新: " + (this.tempAutoUpdate ? "开" : "关")));
        }).width(150).build();
        togglesRow.add(autoUpdateBtn);

        ButtonWidget autoStartBtn = ButtonWidget.builder(Text.literal("自动启动: " + (this.tempAutoStart ? "开" : "关")), button -> {
            this.tempAutoStart = !this.tempAutoStart;
            button.setMessage(Text.literal("自动启动: " + (this.tempAutoStart ? "开" : "关")));
        }).width(150).build();
        togglesRow.add(autoStartBtn);

        content.add(togglesRow);

        this.layout.addBody(content);

        DirectionalLayoutWidget footerButtons = DirectionalLayoutWidget.horizontal().spacing(8);
        footerButtons.add(ButtonWidget.builder(Text.literal("保存并返回"), button -> {
            this.saveConfig();
            this.close();
        }).width(150).build());

        footerButtons.add(ButtonWidget.builder(Text.literal("取消"), button -> {
            this.close();
        }).width(150).build());

        this.layout.addFooter(footerButtons);
    }

    private void saveConfig() {
        FabricConfig.setExternalEnderPath(this.tempPath);
        FabricConfig.setAutoUpdate(this.tempAutoUpdate);
        FabricConfig.setAutoStartBackend(this.tempAutoStart);
    }
}


