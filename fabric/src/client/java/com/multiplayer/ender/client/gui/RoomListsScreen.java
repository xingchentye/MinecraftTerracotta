package com.multiplayer.ender.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.multiplayer.ender.network.EnderApiClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * 房间名单管理屏幕（Fabric）。
 * 允许房主管理白名单、黑名单和禁言列表。
 */
public class RoomListsScreen extends EnderBaseScreen {
    private static final Gson GSON = new Gson();
    private String currentTab = "whitelist"; 
    private JsonObject stateJson;
    private boolean isLoading = true;
    private PlayerList playerList;
    private ButtonWidget viewSwitcherButton;

    public RoomListsScreen(Screen parent) {
        super(Text.literal("名单管理"), parent);
    }

    /**
     * 初始化内容。
     * 加载当前状态，创建玩家列表和底部操作按钮。
     */
    @Override
    protected void initContent() {
        if (isLoading) {
            loadState();
        }

        int listTop = 32;
        int listBottom = this.height - 60;
        this.playerList = new PlayerList(this.client, this.width, listBottom - listTop, listTop);
        
        if (stateJson != null) {
            reloadPlayerList();
        }
        this.addDrawableChild(this.playerList);

        
        DirectionalLayoutWidget footer = DirectionalLayoutWidget.horizontal().spacing(10);
        
        
        this.viewSwitcherButton = ButtonWidget.builder(Text.literal("查看: " + getTabName(currentTab)), b -> {
            if ("whitelist".equals(currentTab)) {
                switchTab("blacklist");
            } else if ("blacklist".equals(currentTab)) {
                switchTab("mute_list");
            } else {
                switchTab("whitelist");
            }
        }).width(110).build();
        footer.add(this.viewSwitcherButton);

        footer.add(ButtonWidget.builder(Text.literal("添加玩家"), b -> openAddDialog()).width(80).build());
        footer.add(ButtonWidget.builder(Text.literal("刷新"), b -> {
            this.isLoading = true;
            loadState();
        }).width(60).build());
        footer.add(ButtonWidget.builder(Text.literal("返回"), b -> this.close()).width(60).build());
        this.layout.addFooter(footer);
    }

    /**
     * 渲染屏幕。
     * 绘制表头和加载状态提示。
     *
     * @param context 绘图上下文
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @param partialTick 部分刻
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float partialTick) {
        super.render(context, mouseX, mouseY, partialTick);
        
        
        int headerY = 20; 
        int rowWidth = 300;
        int left = (this.width - rowWidth) / 2;
        
        context.drawText(this.textRenderer, Text.literal("玩家名").formatted(net.minecraft.util.Formatting.YELLOW), left + 10, headerY, 0xFFFFFF, false);
        context.drawText(this.textRenderer, Text.literal("操作").formatted(net.minecraft.util.Formatting.YELLOW), left + 240, headerY, 0xFFFFFF, false);
        
        if (isLoading) {
             context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("加载中..."), this.width / 2, this.height / 2, 0xAAAAAA);
        }
    }
    
    /**
     * 获取标签页名称。
     *
     * @param tab 标签页标识符
     * @return 中文名称
     */
    private String getTabName(String tab) {
        switch (tab) {
            case "whitelist": return "白名单";
            case "blacklist": return "黑名单";
            case "mute_list": return "禁言列表";
            default: return tab;
        }
    }

    /**
     * 切换标签页。
     *
     * @param tab 目标标签页标识符
     */
    private void switchTab(String tab) {
        this.currentTab = tab;
        if (this.viewSwitcherButton != null) {
            this.viewSwitcherButton.setMessage(Text.literal("查看: " + getTabName(this.currentTab)));
        }
        reloadPlayerList();
    }

    private void reloadPlayerList() {
        if (this.playerList == null) {
            return;
        }
        this.playerList.reloadFromState(this.stateJson, this.currentTab);
    }

    private void loadState() {
        EnderApiClient.getRoomManagementState().whenComplete((jsonStr, throwable) -> {
            if (throwable != null) {
                this.isLoading = false;
            } else {
                try {
                    this.stateJson = GSON.fromJson(jsonStr, JsonObject.class);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    this.isLoading = false;
                }
            }
            if (this.client != null) {
                this.client.execute(this::reloadPlayerList);
            }
        });
    }

    private void removePlayer(String name) {
        if (stateJson == null) return;
        JsonArray list = stateJson.has(currentTab) ? stateJson.getAsJsonArray(currentTab) : new JsonArray();
        JsonArray newList = new JsonArray();
        boolean changed = false;
        for (JsonElement el : list) {
            if (!el.getAsString().equals(name)) {
                newList.add(el);
            } else {
                changed = true;
            }
        }
        
        if (changed) {
            stateJson.add(currentTab, newList);
            EnderApiClient.updateRoomManagementState(stateJson.toString());
            reloadPlayerList();
        }
    }

    private void openAddDialog() {
        if (this.client != null) {
            this.client.setScreen(new AddListEntryScreen(this, this.currentTab, (name, type) -> {
                addPlayer(name, type);
            }));
        }
    }

    
    protected void removePlayerFromList(String name) {
        removePlayer(name);
    }

    private void addPlayer(String name, String type) {
        if (stateJson == null || name == null || name.isBlank()) return;
        JsonArray list = stateJson.has(type) ? stateJson.getAsJsonArray(type) : new JsonArray();
        
        for (JsonElement el : list) {
            if (el.getAsString().equals(name)) return;
        }
        
        list.add(name);
        stateJson.add(type, list);
        EnderApiClient.updateRoomManagementState(stateJson.toString());
        
        
        com.multiplayer.ender.fabric.MinecraftEnderClient.showToast(Text.literal("提示"), Text.literal("已添加玩家: " + name));

        
        if (!this.currentTab.equals(type)) {
            this.currentTab = type;
            if (this.viewSwitcherButton != null) {
                this.viewSwitcherButton.setMessage(Text.literal("查看: " + getTabName(this.currentTab)));
            }
        }
        
        if (this.client != null) {
             reloadPlayerList();
        }
    }

    class PlayerList extends ElementListWidget<PlayerList.Entry> {
        public PlayerList(MinecraftClient client, int width, int height, int top) {
            super(client, width, height, top, 24);
            this.centerListVertically = false;
        }
        
        public void addPlayer(String name) {
            this.addEntry(new Entry(name));
        }
        
        public void reloadFromState(JsonObject state, String tab) {
            this.clearEntries();
            if (state == null) {
                return;
            }
            JsonArray list = state.has(tab) ? state.getAsJsonArray(tab) : new JsonArray();
            for (JsonElement el : list) {
                if (el != null && el.isJsonPrimitive()) {
                    this.addPlayer(el.getAsString());
                }
            }
        }
        
        @Override
        public int getRowWidth() {
            return 300;
        }
        
        
        
        
        
        

        public class Entry extends ElementListWidget.Entry<Entry> {
            private final String name;
            private final ButtonWidget removeBtn;
            
            public Entry(String name) {
                this.name = name;
                this.removeBtn = ButtonWidget.builder(Text.literal("移除"), b -> RoomListsScreen.this.removePlayerFromList(name)).width(50).build();
            }

            @Override
            public void render(DrawContext context, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
                context.drawText(textRenderer, name, left + 10, top + (height - 8) / 2, 0xFFFFFF, false);
                this.removeBtn.setX(left + width - 55);
                this.removeBtn.setY(top + (height - 20) / 2);
                this.removeBtn.render(context, mouseX, mouseY, partialTick);
            }
            
            @Override
            public List<? extends Element> children() {
                return ImmutableList.of(removeBtn);
            }

            @Override
            public List<? extends Selectable> selectableChildren() {
                return ImmutableList.of(removeBtn);
            }
        }
    }
}
