package com.multiplayer.ender.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.multiplayer.ender.network.EnderApiClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.GameRules;

import java.util.List;

/**
 * 房间托管逻辑处理类（Fabric）。
 * 负责在房主端同步 Minecraft 服务器状态（如游戏规则、权限）到后端。
 */
public class RoomHostLogic {
    private static int tickCounter = 0;
    private static boolean isSyncedWithServer = false;

    /**
     * 每 Tick 执行的逻辑。
     * 只有当当前状态为 HOSTING 时才执行同步。
     * 每 20 tick（约 1 秒）执行一次。
     *
     * @param server Minecraft 服务器实例
     */
    public static void onServerTick(MinecraftServer server) {
        if (tickCounter++ % 20 != 0) return; 

        if (EnderApiClient.getCurrentState() != EnderApiClient.State.HOSTING) {
             isSyncedWithServer = false;
             return;
        }

        if (!isSyncedWithServer) {
            syncFromMinecraft(server);
            isSyncedWithServer = true;
        }

        JsonObject state = EnderApiClient.getRoomManagementStateSync();
        if (state == null) return;

        applyState(server, state);
    }

    /**
     * 将 Minecraft 服务器的配置同步到后端。
     * 包括 PVP、作弊权限、出生点保护、游戏规则（死亡不掉落、天气锁定等）。
     *
     * @param server Minecraft 服务器实例
     */
    private static void syncFromMinecraft(MinecraftServer server) {
        JsonObject update = new JsonObject();
        
        
        boolean pvp = true;
        try {
            pvp = (boolean) server.getClass().getMethod("isPvpAllowed").invoke(server);
        } catch (Exception e) {
            try {
                pvp = (boolean) server.getClass().getMethod("isPvpEnabled").invoke(server);
            } catch (Exception ignored) {}
        }
        update.addProperty("allow_pvp", pvp);
        
        
        boolean allowCheats = false;
        try {
             Object playerManager = server.getClass().getMethod("getPlayerManager").invoke(server);
             try {
                 allowCheats = (boolean) playerManager.getClass().getMethod("areCheatsAllowed").invoke(playerManager);
             } catch (Exception e) {
                 allowCheats = (boolean) playerManager.getClass().getMethod("isAllowCheatsForAllPlayers").invoke(playerManager);
             }
        } catch (Exception ignored) {}
        update.addProperty("allow_cheats", allowCheats);
        
        
        int spawnProtection = 0;
        try {
             java.lang.reflect.Method m = server.getClass().getMethod("getSpawnProtectionRadius");
             spawnProtection = (int) m.invoke(server);
        } catch (Exception ignored) {}
        update.addProperty("spawn_protection", spawnProtection);

        
        GameRules rules = server.getGameRules();
        update.addProperty("keep_inventory", rules.getBoolean(GameRules.KEEP_INVENTORY));
        update.addProperty("weather_lock", !rules.getBoolean(GameRules.DO_WEATHER_CYCLE));
        update.addProperty("mob_spawning", rules.getBoolean(GameRules.DO_MOB_SPAWNING));
        update.addProperty("fire_spread", rules.getBoolean(GameRules.DO_FIRE_TICK));
        
        boolean cycle = rules.getBoolean(GameRules.DO_DAYLIGHT_CYCLE);
        update.addProperty("time_lock", cycle ? "cycle" : "fixed");

        EnderApiClient.updateRoomManagementState(update.toString());
    }

    /**
     * 应用后端状态到 Minecraft 服务器。
     * 包括执行访问控制（白名单/黑名单）和同步游戏规则。
     *
     * @param server Minecraft 服务器实例
     * @param state 后端状态 JSON
     */
    public static void applyState(MinecraftServer server, JsonObject state) {
        enforceAccessControl(server, state);
        enforceGameRules(server, state);
    }

    /**
     * 强制执行访问控制。
     * 检查当前在线玩家是否符合白名单/黑名单规则，不符合则踢出。
     *
     * @param server Minecraft 服务器实例
     * @param state 后端状态 JSON
     */
    private static void enforceAccessControl(MinecraftServer server, JsonObject state) {
        String hostName = "";
        try {
            
            if (server.isSingleplayer()) {
                try {
                     
                     java.lang.reflect.Method m = server.getClass().getMethod("getUserName");
                     hostName = (String) m.invoke(server);
                } catch (Exception ignored) {
                     try {
                         java.lang.reflect.Method m = server.getClass().getMethod("getSinglePlayerName");
                         hostName = (String) m.invoke(server);
                     } catch (Exception ignored2) {}
                }
            }
        } catch (Exception ignored) {
            try {
                
                java.lang.reflect.Method m = server.getClass().getMethod("getUserName");
                hostName = (String) m.invoke(server);
            } catch (Exception ignored2) {}
        }

        JsonArray blacklist = state.has("blacklist") ? state.getAsJsonArray("blacklist") : new JsonArray();
        JsonArray whitelist = state.has("whitelist") ? state.getAsJsonArray("whitelist") : new JsonArray();
        String visitorPermission = state.has("visitor_permission") ? state.get("visitor_permission").getAsString() : "可交互";

        
        Object playerManager;
        try {
            playerManager = server.getClass().getMethod("getPlayerManager").invoke(server);
        } catch (Exception ignored) {
            return;
        }
        
        List<?> players;
        try {
            Object list = playerManager.getClass().getMethod("getPlayerList").invoke(playerManager);
            players = list instanceof List<?> l ? l : List.of();
        } catch (Exception ignored) {
             try {
                 Object list = playerManager.getClass().getMethod("getPlayers").invoke(playerManager);
                 players = list instanceof List<?> l ? l : List.of();
             } catch (Exception ignored2) {
                 return;
             }
        }

        for (Object player : players) {
            String name = getPlayerName(player);
            if (name != null && !hostName.isEmpty() && name.equalsIgnoreCase(hostName)) {
                continue;
            }
            
            if (containsName(blacklist, name)) {
                disconnectPlayer(player, Text.of("你已被房主加入黑名单"));
                continue;
            }
            if (whitelist.size() > 0 && !containsName(whitelist, name)) {
                disconnectPlayer(player, Text.of("你不在白名单中"));
                continue;
            }
            
            if ("禁止进入".equals(visitorPermission)) {
                disconnectPlayer(player, Text.of("房间禁止访客进入"));
                continue;
            }
            
            if ("仅观战".equals(visitorPermission)) {
                setPlayerGameMode(player, "SPECTATOR");
            } else if ("仅聊天".equals(visitorPermission)) {
                setPlayerGameMode(player, "ADVENTURE");
            }
        }
    }

    private static void enforceGameRules(MinecraftServer server, JsonObject state) {
        if (state.has("allow_pvp")) {
            boolean pvp = state.get("allow_pvp").getAsBoolean();
            setPvpEnabled(server, pvp);
        }
        
        if (state.has("allow_cheats")) {
            setCheatsAllowed(server, state.get("allow_cheats").getAsBoolean());
        }
        
        if (state.has("spawn_protection")) {
             setSpawnProtection(server, state.get("spawn_protection").getAsInt());
        }

        GameRules rules = server.getGameRules();
        if (state.has("keep_inventory")) {
            boolean val = state.get("keep_inventory").getAsBoolean();
            setBooleanGameRule(rules, val, GameRules.KEEP_INVENTORY);
        }
        
        if (state.has("mob_spawning")) {
            boolean val = state.get("mob_spawning").getAsBoolean();
            setBooleanGameRule(rules, val, GameRules.DO_MOB_SPAWNING);
        }
        
        if (state.has("fire_spread")) {
            boolean val = state.get("fire_spread").getAsBoolean();
            setBooleanGameRule(rules, val, GameRules.DO_FIRE_TICK);
        }
        
        if (state.has("weather_lock")) {
             boolean val = state.get("weather_lock").getAsBoolean();
             setBooleanGameRule(rules, !val, GameRules.DO_WEATHER_CYCLE);
        }

        if (state.has("time_lock")) {
            String mode = state.get("time_lock").getAsString();
            boolean cycle = "cycle".equals(mode);
            setBooleanGameRule(rules, cycle, GameRules.DO_DAYLIGHT_CYCLE);
            if (!cycle) {
                ServerWorld world = server.getOverworld();
                if (world != null) {
                    long time = "night".equals(mode) ? 13000L : 1000L;
                    if (Math.abs(world.getTimeOfDay() % 24000 - time) > 1000) {
                        world.setTimeOfDay(time);
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
    
    private static String getPlayerName(Object player) {
        if (player == null) return null;
        try {
            Object profile = player.getClass().getMethod("getGameProfile").invoke(player);
            Object name = profile == null ? null : profile.getClass().getMethod("getName").invoke(profile);
            return name == null ? null : String.valueOf(name);
        } catch (Exception ignored) {}
        try {
            Object text = player.getClass().getMethod("getName").invoke(player);
            Object str = text == null ? null : text.getClass().getMethod("getString").invoke(text);
            return str == null ? null : String.valueOf(str);
        } catch (Exception ignored) {}
        return null;
    }
    
    private static void disconnectPlayer(Object player, Text reason) {
        if (player == null) return;
        try {
            java.lang.reflect.Field f = player.getClass().getField("networkHandler");
            Object handler = f.get(player);
            if (handler != null) {
                handler.getClass().getMethod("disconnect", Text.class).invoke(handler, reason);
                return;
            }
        } catch (Exception ignored) {}
        try {
            Object handler = player.getClass().getMethod("networkHandler").invoke(player);
            if (handler != null) {
                handler.getClass().getMethod("disconnect", Text.class).invoke(handler, reason);
            }
        } catch (Exception ignored) {}
    }
    
    private static void setPlayerGameMode(Object player, String modeName) {
        if (player == null || modeName == null) return;
        try {
            Class<?> gameModeClass = Class.forName("net.minecraft.world.GameMode");
            @SuppressWarnings("unchecked")
            Class<? extends Enum> enumClass = (Class<? extends Enum>) gameModeClass;
            Object mode = Enum.valueOf(enumClass, modeName);
            
            try {
                player.getClass().getMethod("changeGameMode", gameModeClass).invoke(player, mode);
                return;
            } catch (Exception ignored) {}
            try {
                player.getClass().getMethod("setGameMode", gameModeClass).invoke(player, mode);
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }
    
    private static void setCheatsAllowed(MinecraftServer server, boolean value) {
        try {
            Object playerManager = server.getClass().getMethod("getPlayerManager").invoke(server);
            try {
                playerManager.getClass().getMethod("setCheatsAllowed", boolean.class).invoke(playerManager, value);
                return;
            } catch (Exception ignored) {}
            try {
                playerManager.getClass().getMethod("setAllowCommandsForAllPlayers", boolean.class).invoke(playerManager, value);
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }
    
    private static void setSpawnProtection(MinecraftServer server, int value) {
        int radius = Math.max(0, value);
        try {
            Object playerManager = server.getClass().getMethod("getPlayerManager").invoke(server);
            try {
                playerManager.getClass().getMethod("setSpawnProtectionRadius", int.class).invoke(playerManager, radius);
                return;
            } catch (Exception ignored) {}
            try {
                playerManager.getClass().getMethod("setSpawnProtection", int.class).invoke(playerManager, radius);
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }
    
    private static void setPvpEnabled(MinecraftServer server, boolean value) {
        try {
            server.getClass().getMethod("setPvpEnabled", boolean.class).invoke(server, value);
        } catch (Exception ignored) {}
    }
    
    private static void setBooleanGameRule(GameRules rules, boolean value, GameRules.Key<GameRules.BooleanRule> key) {
        rules.get(key).set(value, null);
    }
}
