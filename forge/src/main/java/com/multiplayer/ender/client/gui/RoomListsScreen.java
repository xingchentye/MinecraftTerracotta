package com.multiplayer.ender.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.multiplayer.ender.network.EnderApiClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * 房间名单管理屏幕。
 * <p>
 * 提供白名单、黑名单和禁言列表的管理界面。
 * 支持查看列表、添加玩家、移除玩家，并实时同步到后端。
 * </p>
 */
public class RoomListsScreen extends EnderBaseScreen {
    private static final Gson GSON = new Gson();
    private String currentTab = "whitelist"; 
    private JsonObject stateJson;
    private boolean isLoading = true;
    private PlayerList playerList;
    private Button viewSwitcherButton;

    public RoomListsScreen(Screen parent) {
        super(Component.literal("名单管理"), parent);
    }

    @Override
    protected void initContent() {
        if (isLoading) {
            loadState();
        }

        int listTop = 32;
        int listBottom = this.height - 60;
        this.playerList = new PlayerList(this.minecraft, this.width, listBottom - listTop, listTop);
        
        if (stateJson != null) {
            reloadPlayerList();
        }
        this.addRenderableWidget(this.playerList);

        
        LinearLayout footer = LinearLayout.horizontal().spacing(10);
        
        
        this.viewSwitcherButton = Button.builder(Component.literal("查看: " + getTabName(currentTab)), b -> {
            if ("whitelist".equals(currentTab)) {
                switchTab("blacklist");
            } else if ("blacklist".equals(currentTab)) {
                switchTab("mute_list");
            } else {
                switchTab("whitelist");
            }
        }).width(110).build();
        footer.addChild(this.viewSwitcherButton);

        footer.addChild(Button.builder(Component.literal("添加玩家"), b -> openAddDialog()).width(80).build());
        footer.addChild(Button.builder(Component.literal("刷新"), b -> {
            this.isLoading = true;
            loadState();
        }).width(60).build());
        footer.addChild(Button.builder(Component.literal("返回"), b -> this.onClose()).width(60).build());
        this.layout.addToFooter(footer);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        
        int headerY = 20; 
        int rowWidth = 300;
        int left = (this.width - rowWidth) / 2;
        
        guiGraphics.drawString(this.font, Component.literal("玩家名").withStyle(net.minecraft.ChatFormatting.YELLOW), left + 10, headerY, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.literal("操作").withStyle(net.minecraft.ChatFormatting.YELLOW), left + 240, headerY, 0xFFFFFF);
        
        if (isLoading) {
             guiGraphics.drawCenteredString(this.font, "加载中...", this.width / 2, this.height / 2, 0xAAAAAA);
        }
    }
    
    

    private String getTabName(String tab) {
        switch (tab) {
            case "whitelist": return "白名单";
            case "blacklist": return "黑名单";
            case "mute_list": return "禁言列表";
            default: return tab;
        }
    }

    /**
     * 切换当前查看的列表类型。
     *
     * @param tab 目标列表类型 (whitelist, blacklist, mute_list)
     */
    private void switchTab(String tab) {
        this.currentTab = tab;
        if (this.viewSwitcherButton != null) {
            this.viewSwitcherButton.setMessage(Component.literal("查看: " + getTabName(this.currentTab)));
        }
        reloadPlayerList();
    }

    /**
     * 重新加载列表 UI 数据。
     * 根据当前选中的 tab 和 stateJson 更新列表项。
     */
    private void reloadPlayerList() {
        if (this.playerList == null) {
            return;
        }
        this.playerList.reloadFromState(this.stateJson, this.currentTab);
    }

    /**
     * 从后端加载房间管理状态。
     * 异步获取 JSON 数据并刷新界面。
     */
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
            if (this.minecraft != null) {
                this.minecraft.execute(this::reloadPlayerList);
            }
        });
    }

    /**
     * 移除玩家。
     * 修改 JSON 数据并调用后端 API 更新。
     *
     * @param name 玩家名
     */
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
        if (this.minecraft != null) {
            this.minecraft.setScreen(new AddListEntryScreen(this, this.currentTab, (name, type) -> {
                addPlayer(name, type);
            }));
        }
    }
    
    
    protected void removePlayerFromList(String name) {
        removePlayer(name);
    }

    /**
     * 添加玩家。
     * 检查是否重复，更新 JSON 数据并调用后端 API。
     *
     * @param name 玩家名
     * @param type 列表类型
     */
    private void addPlayer(String name, String type) {
        if (stateJson == null || name == null || name.isBlank()) return;
        JsonArray list = stateJson.has(type) ? stateJson.getAsJsonArray(type) : new JsonArray();
        
        for (JsonElement el : list) {
            if (el.getAsString().equals(name)) return;
        }
        
        list.add(name);
        stateJson.add(type, list);
        EnderApiClient.updateRoomManagementState(stateJson.toString());
        
        
        com.multiplayer.ender.client.ClientSetupForge.showToast(Component.literal("提示"), Component.literal("已添加玩家: " + name));

        
        if (!this.currentTab.equals(type)) {
            this.currentTab = type;
            if (this.viewSwitcherButton != null) {
                this.viewSwitcherButton.setMessage(Component.literal("查看: " + getTabName(this.currentTab)));
            }
        }

        if (this.minecraft != null) {
            reloadPlayerList();
        }
    }

    /**
     * 玩家列表组件。
     * 继承自 ObjectSelectionList，用于显示可滚动的玩家条目。
     */
    class PlayerList extends ObjectSelectionList<PlayerList.Entry> {
        public PlayerList(Minecraft mc, int width, int height, int top) {
            super(mc, width, height, top, 24);
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

        @Override
        protected int getScrollbarPosition() {
            return (this.width / 2) + (getRowWidth() / 2) + 6;
        }

        public class Entry extends ObjectSelectionList.Entry<Entry> {
            private final String name;
            private final Button removeBtn;
            
            public Entry(String name) {
                this.name = name;
                this.removeBtn = Button.builder(Component.literal("移除"), b -> RoomListsScreen.this.removePlayerFromList(name)).width(50).build();
            }

            @Override
            public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
                guiGraphics.drawString(font, name, left + 10, top + (height - 8) / 2, 0xFFFFFF);
                this.removeBtn.setX(left + width - 55);
                this.removeBtn.setY(top + (height - 20) / 2);
                this.removeBtn.render(guiGraphics, mouseX, mouseY, partialTick);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (this.removeBtn.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
                return super.mouseClicked(mouseX, mouseY, button);
            }
            
            @Override
            public Component getNarration() {
                return Component.literal(name);
            }
        }
    }
}
