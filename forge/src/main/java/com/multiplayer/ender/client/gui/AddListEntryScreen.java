package com.multiplayer.ender.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import java.util.function.BiConsumer;

public class AddListEntryScreen extends EnderBaseScreen {
    private final BiConsumer<String, String> onAdd;
    private EditBox nameField;
    private String selectedType = "whitelist"; // default
    private String[] types = new String[]{"whitelist", "blacklist", "mute_list"};

    public AddListEntryScreen(Screen parent, String initialType, BiConsumer<String, String> onAdd) {
        super(Component.literal("添加名单"), parent);
        this.onAdd = onAdd;
        this.selectedType = initialType;
    }

    @Override
    protected void initContent() {
        LinearLayout content = LinearLayout.vertical().spacing(8);
        content.defaultCellSetting().alignHorizontallyCenter();

        this.nameField = new EditBox(this.font, 0, 0, 200, 20, Component.literal("玩家名称"));
        this.nameField.setMaxLength(32);
        content.addChild(this.nameField);

        content.addChild(Button.builder(Component.literal("添加到: " + getTypeName(selectedType)), b -> {
            // Cycle type
            int idx = 0;
            for(int i=0; i<types.length; i++) {
                if(types[i].equals(selectedType)) {
                    idx = i;
                    break;
                }
            }
            selectedType = types[(idx + 1) % types.length];
            b.setMessage(Component.literal("添加到: " + getTypeName(selectedType)));
        }).width(200).build());

        LinearLayout buttons = LinearLayout.horizontal().spacing(8);
        buttons.addChild(Button.builder(Component.literal("添加"), b -> {
            if (this.onAdd != null) {
                this.onAdd.accept(this.nameField.getValue(), this.selectedType);
            }
            this.onClose();
        }).width(100).build());
        
        buttons.addChild(Button.builder(Component.literal("取消"), b -> this.onClose()).width(100).build());
        content.addChild(buttons);

        this.layout.addToContents(content);
        
        this.setInitialFocus(this.nameField);
    }

    private String getTypeName(String type) {
        return switch (type) {
            case "whitelist" -> "白名单";
            case "blacklist" -> "黑名单";
            case "mute_list" -> "禁言列表";
            default -> type;
        };
    }
}
