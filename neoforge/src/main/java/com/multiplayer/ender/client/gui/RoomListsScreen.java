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
 * 名单管理界面。
 * 用于管理房间的白名单、黑名单和禁言列表。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public class RoomListsScreen extends EnderBaseScreen {
    private static final Gson GSON = new Gson();
    /** 当前选中的标签页（whitelist, blacklist, mute_list） */
    private String currentTab = "whitelist"; 
    /** 房间管理状态数据 */
    private JsonObject stateJson;
    /** 是否正在加载数据 */
    private boolean isLoading = true;
    /** 玩家列表控件 */
    private PlayerList playerList;
    /** 视图切换按钮 */
    private Button viewSwitcherButton;

    /**
     * 构造函数。
     *
     * @param parent 父屏幕
     */
    public RoomListsScreen(Screen parent) {
        super(Component.literal("名单管理"), parent);
    }

    /**
     * 初始化界面内容。
     * 创建列表控件和底部按钮。
     */
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

    /**
     * 渲染界面。
     * 绘制标题和列表头。
     *
     * @param guiGraphics 图形上下文
     * @param mouseX      鼠标 X 坐标
     * @param mouseY      鼠标 Y 坐标
     * @param partialTick 部分刻
     */
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
    
    

    /**
     * 获取标签页显示名称。
     *
     * @param tab 标签页标识
     * @return 显示名称
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
     * @param tab 目标标签页
     */
    private void switchTab(String tab) {
        this.currentTab = tab;
        if (this.viewSwitcherButton != null) {
            this.viewSwitcherButton.setMessage(Component.literal("查看: " + getTabName(this.currentTab)));
        }
        reloadPlayerList();
    }

    /**
     * 重新加载玩家列表。
     * 根据当前状态和标签页更新列表内容。
     */
    private void reloadPlayerList() {
        if (this.playerList == null) {
            return;
        }
        this.playerList.reloadFromState(this.stateJson, this.currentTab);
    }

    /**
     * 从后端加载房间管理状态。
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
     *
     * @param name 玩家名称
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

    /**
     * 打开添加玩家对话框。
     */
    private void openAddDialog() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new AddListEntryScreen(this, this.currentTab, (name, type) -> {
                addPlayer(name, type);
            }));
        }
    }
    
    /**
     * 从列表中移除玩家的回调。
     *
     * @param name 玩家名称
     */
    protected void removePlayerFromList(String name) {
        removePlayer(name);
    }

    /**
     * 添加玩家。
     *
     * @param name 玩家名称
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
        
        
        com.multiplayer.ender.client.ClientSetup.showToast(Component.literal("提示"), Component.literal("已添加玩家: " + name));

        
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
     * 玩家列表控件。
     * 用于显示和管理玩家条目。
     */
    class PlayerList extends ObjectSelectionList<PlayerList.Entry> {
        /**
         * 构造函数。
         *
         * @param mc     Minecraft 实例
         * @param width  宽度
         * @param height 高度
         * @param top    顶部位置
         */
        public PlayerList(Minecraft mc, int width, int height, int top) {
            super(mc, width, height, top, 24);
        }
        
        /**
         * 添加玩家条目。
         *
         * @param name 玩家名称
         */
        public void addPlayer(String name) {
            this.addEntry(new Entry(name));
        }

        /**
         * 从状态重新加载列表。
         *
         * @param state 状态对象
         * @param tab   当前标签页
         */
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

        /**
         * 列表条目类。
         */
        public class Entry extends ObjectSelectionList.Entry<Entry> {
            private final String name;
            private final Button removeBtn;
            
            /**
             * 构造函数。
             *
             * @param name 玩家名称
             */
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
