package com.multiplayer.ender.client.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.text.Text;
import java.util.function.BiConsumer;

public class AddListEntryScreen extends EnderBaseScreen {
    private final BiConsumer<String, String> onAdd;
    private TextFieldWidget nameField;
    private String selectedType = "whitelist"; // default
    private String[] types = new String[]{"whitelist", "blacklist", "mute_list"};

    public AddListEntryScreen(Screen parent, String initialType, BiConsumer<String, String> onAdd) {
        super(Text.literal("添加名单"), parent);
        this.onAdd = onAdd;
        this.selectedType = initialType;
    }

    @Override
    protected void initContent() {
        DirectionalLayoutWidget content = DirectionalLayoutWidget.vertical().spacing(8);
        content.getMainPositioner().alignHorizontalCenter();

        this.nameField = new TextFieldWidget(this.textRenderer, 0, 0, 200, 20, Text.literal("玩家名称"));
        this.nameField.setMaxLength(32);
        content.add(this.nameField);

        content.add(ButtonWidget.builder(Text.literal("添加到: " + getTypeName(selectedType)), b -> {
            // Cycle type
            int idx = 0;
            for(int i=0; i<types.length; i++) {
                if(types[i].equals(selectedType)) {
                    idx = i;
                    break;
                }
            }
            selectedType = types[(idx + 1) % types.length];
            b.setMessage(Text.literal("添加到: " + getTypeName(selectedType)));
        }).width(200).build());

        DirectionalLayoutWidget buttons = DirectionalLayoutWidget.horizontal().spacing(8);
        buttons.add(ButtonWidget.builder(Text.literal("添加"), b -> {
            if (this.onAdd != null) {
                this.onAdd.accept(this.nameField.getText(), this.selectedType);
            }
            this.close();
        }).width(100).build());
        
        buttons.add(ButtonWidget.builder(Text.literal("取消"), b -> this.close()).width(100).build());
        content.add(buttons);

        this.layout.addBody(content);
        
        this.setInitialFocus(this.nameField);
    }

    private String getTypeName(String type) {
        switch (type) {
            case "whitelist": return "白名单";
            case "blacklist": return "黑名单";
            case "mute_list": return "禁言列表";
            default: return type;
        }
    }
}
