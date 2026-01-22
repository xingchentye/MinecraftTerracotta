package com.multiplayer.ender.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.multiplayer.ender.network.EnderApiClient;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;

import java.util.List;

/**
 * 房间托管逻辑核心类。
 * 负责在房主端同步游戏状态到后端，并执行后端的控制指令。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public class RoomHostLogic {
    private static int tickCounter = 0;
    private static boolean initialized = false;

    /**
     * 服务器 Tick 回调。
     * 定期同步状态并应用规则。
     *
     * @param server Minecraft 服务器实例
     */
    public static void onServerTick(MinecraftServer server) {
        if (tickCounter++ % 20 != 0) return; 

        if (EnderApiClient.getCurrentState() != EnderApiClient.State.HOSTING) {
            initialized = false;
            return;
        }

        if (!initialized) {
            syncFromMinecraft(server);
            initialized = true;
        }

        JsonObject state = EnderApiClient.getRoomManagementStateSync();
        if (state == null) return;

        applyState(server, state);
    }

    /**
     * 将 Minecraft 游戏规则和设置同步到后端。
     *
     * @param server Minecraft 服务器实例
     */
    private static void syncFromMinecraft(MinecraftServer server) {
        JsonObject update = new JsonObject();
        
        
        update.addProperty("allow_pvp", server.isPvpAllowed());
        
        
        boolean allowCheats = false;
        try {
             Object playerList = server.getPlayerList();
             java.lang.reflect.Method m = playerList.getClass().getMethod("isAllowCheatsForAllPlayers");
             allowCheats = (boolean) m.invoke(playerList);
        } catch (Exception ignored) {
            try {
                 Object playerList = server.getPlayerList();
                 java.lang.reflect.Method m = playerList.getClass().getMethod("isAllowCommandsForAllPlayers");
                 allowCheats = (boolean) m.invoke(playerList);
            } catch (Exception ignored2) {}
        }
        update.addProperty("allow_cheats", allowCheats);
        
        
        int spawnProtection = 0;
        try {
             java.lang.reflect.Method m = server.getClass().getMethod("getSpawnProtectionRadius");
             spawnProtection = (int) m.invoke(server);
        } catch (Exception ignored) {
             try {
                 Object playerList = server.getPlayerList();
                 java.lang.reflect.Method m = playerList.getClass().getMethod("getSpawnProtectionRadius");
                 spawnProtection = (int) m.invoke(playerList);
             } catch (Exception ignored2) {}
        }
        update.addProperty("spawn_protection", spawnProtection);
        
        
        GameRules rules = server.getGameRules();
        update.addProperty("keep_inventory", rules.getBoolean(GameRules.RULE_KEEPINVENTORY));
        update.addProperty("weather_lock", !rules.getBoolean(GameRules.RULE_WEATHER_CYCLE));
        
        update.addProperty("mob_spawning", getBooleanGameRule(server, "RULE_DOMOBSPAWNING", "RULE_DO_MOB_SPAWNING"));
        update.addProperty("fire_spread", getBooleanGameRule(server, "RULE_DOFIRETICK", "RULE_DO_FIRE_TICK"));
        
        boolean cycle = getBooleanGameRule(server, "RULE_DAYLIGHT", "RULE_DAYLIGHT_CYCLE", "RULE_DO_DAYLIGHT_CYCLE");
        update.addProperty("time_lock", cycle ? "cycle" : "fixed");

        EnderApiClient.updateRoomManagementState(update.toString());
    }

    /**
     * 获取布尔类型的游戏规则值。
     * 兼容不同版本的字段名称。
     *
     * @param server     服务器实例
     * @param fieldNames 字段名称列表
     * @return 规则值，如果未找到则返回 true
     */
    private static boolean getBooleanGameRule(MinecraftServer server, String... fieldNames) {
        for (String fieldName : fieldNames) {
            try {
                java.lang.reflect.Field f = GameRules.class.getField(fieldName);
                Object key = f.get(null);
                if (key instanceof GameRules.Key) {
                    return server.getGameRules().getBoolean((GameRules.Key<GameRules.BooleanValue>) key);
                }
            } catch (Exception ignored) {}
        }
        return true;
    }

    /**
     * 应用后端状态到服务器。
     * 执行访问控制和游戏规则同步。
     *
     * @param server Minecraft 服务器实例
     * @param state  后端状态 JSON
     */
    public static void applyState(MinecraftServer server, JsonObject state) {
        enforceAccessControl(server, state);
        enforceGameRules(server, state);
    }

    /**
     * 执行访问控制。
     * 处理白名单、黑名单和访客权限。
     *
     * @param server Minecraft 服务器实例
     * @param state  后端状态 JSON
     */
    private static void enforceAccessControl(MinecraftServer server, JsonObject state) {
        String hostName = "";
        if (server.isSingleplayer()) {
            hostName = server.getSingleplayerProfile() != null ? server.getSingleplayerProfile().getName() : null;
        }

        JsonArray blacklist = state.has("blacklist") ? state.getAsJsonArray("blacklist") : new JsonArray();
        JsonArray whitelist = state.has("whitelist") ? state.getAsJsonArray("whitelist") : new JsonArray();
        String visitorPermission = state.has("visitor_permission") ? state.get("visitor_permission").getAsString() : "可交互";

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            String name = player.getGameProfile().getName();
            if (name != null && hostName != null && name.equalsIgnoreCase(hostName)) {
                continue;
            }
            
            if (containsName(blacklist, name)) {
                disconnectPlayer(player, Component.literal("你已被房主加入黑名单"));
                continue;
            }
            if (whitelist.size() > 0 && !containsName(whitelist, name)) {
                disconnectPlayer(player, Component.literal("你不在白名单中"));
                continue;
            }
            
            if ("禁止进入".equals(visitorPermission)) {
                disconnectPlayer(player, Component.literal("房间禁止访客进入"));
                continue;
            }
            
            if ("仅观战".equals(visitorPermission)) {
                setPlayerGameType(player, GameType.SPECTATOR);
            } else if ("仅聊天".equals(visitorPermission)) {
                setPlayerGameType(player, GameType.ADVENTURE);
            }
        }
    }

    /**
     * 执行游戏规则同步。
     *
     * @param server Minecraft 服务器实例
     * @param state  后端状态 JSON
     */
    private static void enforceGameRules(MinecraftServer server, JsonObject state) {
        if (state.has("allow_pvp")) {
            boolean pvp = state.get("allow_pvp").getAsBoolean();
            if (server.isPvpAllowed() != pvp) {
                server.setPvpAllowed(pvp);
            }
        }
        
        if (state.has("allow_cheats")) {
            setCheatsAllowed(server, state.get("allow_cheats").getAsBoolean());
        }
        
        if (state.has("spawn_protection")) {
             setSpawnProtection(server, state.get("spawn_protection").getAsInt());
        }

        if (state.has("keep_inventory")) {
            boolean val = state.get("keep_inventory").getAsBoolean();
            setBooleanGameRule(server, val, GameRules.RULE_KEEPINVENTORY);
        }
        
        if (state.has("mob_spawning")) {
            boolean val = state.get("mob_spawning").getAsBoolean();
            setBooleanGameRule(server, val, "RULE_DOMOBSPAWNING", "RULE_DO_MOB_SPAWNING");
        }
        
        if (state.has("fire_spread")) {
            boolean val = state.get("fire_spread").getAsBoolean();
            setBooleanGameRule(server, val, "RULE_DOFIRETICK", "RULE_DO_FIRE_TICK");
        }
        
        if (state.has("weather_lock")) {
             boolean val = state.get("weather_lock").getAsBoolean();
             setBooleanGameRule(server, !val, GameRules.RULE_WEATHER_CYCLE);
        }

        if (state.has("time_lock")) {
            String mode = state.get("time_lock").getAsString();
            boolean cycle = "cycle".equals(mode);
            setBooleanGameRule(server, cycle, "RULE_DAYLIGHT", "RULE_DAYLIGHT_CYCLE", "RULE_DO_DAYLIGHT_CYCLE");
            if (!cycle) {
                ServerLevel level = server.overworld();
                if (level != null) {
                    long time = "night".equals(mode) ? 13000L : 1000L;
                    if (Math.abs(level.getDayTime() % 24000 - time) > 1000) {
                        level.setDayTime(time);
                    }
                }
            }
        }
    }
    
    private static boolean containsName(JsonArray array, String name) {
        if (array == null || name == null) return false;
        for (JsonElement el : array) {
            if (el.getAsString().equalsIgnoreCase(name)) return true;
        }
        return false;
    }
    
    private static void disconnectPlayer(ServerPlayer player, Component reason) {
        try {
            player.connection.disconnect(reason);
        } catch (Exception ignored) {}
    }
    
    private static void setPlayerGameType(ServerPlayer player, GameType type) {
        if (player.gameMode.getGameModeForPlayer() == type) return;
        try {
            java.lang.reflect.Method m = player.getClass().getMethod("setGameMode", GameType.class);
            m.invoke(player, type);
        } catch (Exception ignored) {}
    }
    
    private static void setCheatsAllowed(MinecraftServer server, boolean value) {
        try {
            Object playerList = server.getPlayerList();
            java.lang.reflect.Method m = playerList.getClass().getMethod("setAllowCheatsForAllPlayers", boolean.class);
            m.invoke(playerList, value);
            return;
        } catch (Exception ignored) {}
        try {
            Object playerList = server.getPlayerList();
            java.lang.reflect.Method m = playerList.getClass().getMethod("setAllowCommandsForAllPlayers", boolean.class);
            m.invoke(playerList, value);
        } catch (Exception ignored) {}
    }
    
    private static void setSpawnProtection(MinecraftServer server, int value) {
        int radius = Math.max(0, value);
        try {
            Object playerList = server.getPlayerList();
            java.lang.reflect.Method m = playerList.getClass().getMethod("setSpawnProtectionRadius", int.class);
            m.invoke(playerList, radius);
            return;
        } catch (Exception ignored) {}
        try {
            Object playerList = server.getPlayerList();
            java.lang.reflect.Method m = playerList.getClass().getMethod("setSpawnProtection", int.class);
            m.invoke(playerList, radius);
        } catch (Exception ignored) {}
    }
    
    private static void setBooleanGameRule(MinecraftServer server, boolean value, GameRules.Key<GameRules.BooleanValue> key) {
        server.getGameRules().getRule(key).set(value, server);
    }

    private static void setBooleanGameRule(MinecraftServer server, boolean value, String... fieldNames) {
        if (server == null || fieldNames == null) return;
        for (String fieldName : fieldNames) {
            if (fieldName == null || fieldName.isBlank()) continue;
            try {
                java.lang.reflect.Field f = GameRules.class.getField(fieldName);
                Object key = f.get(null);
                if (key instanceof GameRules.Key) {
                    server.getGameRules().getRule((GameRules.Key<GameRules.BooleanValue>) key).set(value, server);
                    return;
                }
            } catch (Exception ignored) {}
        }
    }
}
