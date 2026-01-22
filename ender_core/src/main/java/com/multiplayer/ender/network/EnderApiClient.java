package com.multiplayer.ender.network;

import com.endercore.core.comm.CoreComm;
import com.endercore.core.comm.client.CoreWebSocketClient;
import com.endercore.core.comm.config.CoreWebSocketConfig;
import com.endercore.core.comm.protocol.CoreResponse;
import com.endercore.core.comm.server.CoreRequest;
import com.endercore.core.comm.server.CoreWebSocketServer;
import com.endercore.core.easytier.EasyTierManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


import com.multiplayer.ender.logic.LanDiscovery;

/**
 * Ender API 客户端类。
 * 负责处理房间管理、EasyTier 网络连接、Scaffolding 协议通信以及玩家配置文件的管理。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public class EnderApiClient {
    private static final Gson GSON = new Gson();
    private static final String SCAFFOLDING_PREFIX = "scaffolding-mc-server-";
    private static final int DEFAULT_SCAFFOLDING_PORT = 13448;
    private static final String VENDOR = "ender";
    private static final String LOCAL_MACHINE_ID = UUID.randomUUID().toString();
    private static final CopyOnWriteArrayList<Profile> profiles = new CopyOnWriteArrayList<>();
    private static final ConcurrentHashMap<String, Long> profileLastSeen = new ConcurrentHashMap<>();
    private static volatile CoreWebSocketServer scaffoldingServer;
    private static volatile CoreWebSocketClient scaffoldingClient;
    private static volatile ScheduledExecutorService profileScheduler;
    private static volatile ScheduledExecutorService scaffoldingClientScheduler;
    private static volatile InetSocketAddress scaffoldingRemote;
    private static volatile int scaffoldingPort = DEFAULT_SCAFFOLDING_PORT;
    private static volatile int hostedMcPort = 25565;
    private static volatile int remoteMcPort = 25565;
    private static volatile String localPlayerName = "";
    private static volatile String lastRoomCode = "";
    private static final Logger LOGGER = LoggerFactory.getLogger(EnderApiClient.class);
    private static int dynamicPort = -1;
    
    /**
     * 客户端状态枚举。
     */
    public enum State {
        IDLE,
        HOSTING_STARTING,
        JOINING_STARTING,
        HOSTING,
        JOINING,
        ERROR
    }

    private static State currentState = State.IDLE;
    private static String currentRoom = "";
    private static String lastError = "";
    private static final Object ROOM_STATE_LOCK = new Object();
    private static final java.io.File CONFIG_FILE = new java.io.File("config/ender_room_config.json");
    private static JsonObject roomManagementState = createDefaultRoomManagementState();

    /**
     * 获取当前客户端状态。
     *
     * @return 当前状态枚举值
     */
    public static State getCurrentState() {
        return currentState;
    }

    /**
     * 加载房间配置。
     *
     * @param state 要加载到的 JsonObject 对象
     */
    private static void loadRoomConfig(JsonObject state) {
        if (!CONFIG_FILE.exists()) {
            return;
        }
        try {
            String content = java.nio.file.Files.readString(CONFIG_FILE.toPath());
            JsonObject loaded = GSON.fromJson(content, JsonObject.class);
            if (loaded != null) {
                mergeJsonObject(state, loaded);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load room config", e);
        }
    }

    /**
     * 保存房间配置到文件。
     */
    private static void saveRoomConfig() {
        try {
            java.io.File parent = CONFIG_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            
            JsonObject toSave = new JsonObject();
            synchronized (ROOM_STATE_LOCK) {
                
                for (Map.Entry<String, JsonElement> entry : roomManagementState.entrySet()) {
                    String key = entry.getKey();
                    if (isMcNativeSetting(key)) {
                        continue;
                    }
                    toSave.add(key, entry.getValue());
                }
                
                if (roomManagementState.has("whitelist")) {
                    toSave.add("whitelist", roomManagementState.get("whitelist"));
                }
                if (roomManagementState.has("blacklist")) {
                    toSave.add("blacklist", roomManagementState.get("blacklist"));
                }
                if (roomManagementState.has("mute_list")) {
                    toSave.add("mute_list", roomManagementState.get("mute_list"));
                }
                if (roomManagementState.has("whitelist_enabled")) {
                    toSave.add("whitelist_enabled", roomManagementState.get("whitelist_enabled"));
                }
            }
            
            java.nio.file.Files.writeString(CONFIG_FILE.toPath(), GSON.toJson(toSave));
        } catch (Exception e) {
            LOGGER.warn("Failed to save room config", e);
        }
    }

    /**
     * 检查是否为 Minecraft 原生设置。
     *
     * @param key 设置键名
     * @return 如果是原生设置则返回 true，否则返回 false
     */
    private static boolean isMcNativeSetting(String key) {
        return (key.startsWith("allow_") && !key.equals("allow_cheats") && !key.equals("allow_pvp")) || 
               key.startsWith("spawn_protection") || 
               key.startsWith("keep_inventory") || 
               key.startsWith("fire_spread") || 
               key.startsWith("mob_spawning") || 
               key.startsWith("time_lock") || 
               key.startsWith("weather_lock") || 
               key.startsWith("respawn_") || 
               key.startsWith("world_border_") ||
               key.equals("last_updated"); 
    }

    /**
     * 设置动态端口。
     *
     * @param port 端口号
     */
    public static void setPort(int port) {
        dynamicPort = port;
    }

    /**
     * 清除动态端口并停止广播。
     */
    public static void clearDynamicPort() {
        dynamicPort = -1;
        LanDiscovery.stopBroadcaster();
    }

    /**
     * 检查是否设置了动态端口。
     *
     * @return 如果设置了动态端口则返回 true，否则返回 false
     */
    public static boolean hasDynamicPort() {
        return dynamicPort > 0;
    }

    /**
     * 获取当前使用的端口。
     *
     * @return 如果设置了动态端口则返回该端口，否则返回默认端口 25566
     */
    public static int getPort() {
        return dynamicPort > 0 ? dynamicPort : 25566;
    }

    /**
     * 获取远程 Minecraft 服务器端口。
     *
     * @return 远程服务器端口号
     */
    public static int getRemoteMcPort() {
        return remoteMcPort;
    }

    /**
     * 获取主机 IP 地址。
     *
     * @return 主机 IP 地址字符串，如果未找到则返回 null
     */
    public static String getHostIp() {
        InetSocketAddress remote = scaffoldingRemote;
        if (remote != null && remote.getAddress() != null) {
            return remote.getAddress().getHostAddress();
        }
        return null;
    }

    /**
     * 获取元数据。
     *
     * @return 包含版本信息的 CompletableFuture
     */
    public static CompletableFuture<String> getMeta() {
        return CompletableFuture.completedFuture("{\"version\": \"ender_core_compat\"}");
    }

    /**
     * 同步获取房间管理状态。
     *
     * @return 房间管理状态的 JsonObject 副本
     */
    public static JsonObject getRoomManagementStateSync() {
        synchronized (ROOM_STATE_LOCK) {
            return roomManagementState.deepCopy();
        }
    }

    /**
     * 设置本地房间设置。
     *
     * @param allowCheats 是否允许作弊
     * @param visitorPermission 访客权限设置
     */
    public static void setLocalSettings(boolean allowCheats, String visitorPermission) {
        synchronized (ROOM_STATE_LOCK) {
            roomManagementState.addProperty("allow_cheats", allowCheats);
            roomManagementState.addProperty("visitor_permission", visitorPermission);
            roomManagementState.addProperty("last_updated", System.currentTimeMillis());
        }
        saveRoomConfig();
    }

    /**
     * 异步获取房间管理状态。
     *
     * @return 包含房间管理状态 JSON 字符串的 CompletableFuture
     */
    public static CompletableFuture<String> getRoomManagementState() {
        synchronized (ROOM_STATE_LOCK) {
            return CompletableFuture.completedFuture(roomManagementState.toString());
        }
    }

    /**
     * 更新房间管理状态。
     *
     * @param stateJson 新的状态 JSON 字符串
     * @return CompletableFuture，完成时无返回值
     */
    public static CompletableFuture<Void> updateRoomManagementState(String stateJson) {
        if (stateJson == null) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            JsonObject incoming = GSON.fromJson(stateJson, JsonObject.class);
            if (incoming == null) {
                return CompletableFuture.completedFuture(null);
            }
            synchronized (ROOM_STATE_LOCK) {
                JsonObject next = roomManagementState.deepCopy();
                if (incoming.has("log_entry")) {
                    String entry = incoming.get("log_entry").getAsString();
                    appendLogEntry(next, entry);
                }
                
                String oldRemark = next.has("room_remark") ? next.get("room_remark").getAsString() : "";
                mergeJsonObject(next, incoming);
                String newRemark = next.has("room_remark") ? next.get("room_remark").getAsString() : "";
                
                if (currentState == State.HOSTING && hasDynamicPort() && !oldRemark.equals(newRemark)) {
                    String broadcast = newRemark.isEmpty() ? "Ender Online Room" : newRemark;
                    LanDiscovery.startBroadcaster(getPort(), broadcast);
                }
                
                next.addProperty("last_updated", System.currentTimeMillis());
                roomManagementState = next;
                saveRoomConfig();
            }
        } catch (Exception ignored) {
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 添加房间管理日志条目。
     *
     * @param entry 日志内容
     */
    public static void appendRoomManagementLog(String entry) {
        if (entry == null || entry.isEmpty()) {
            return;
        }
        synchronized (ROOM_STATE_LOCK) {
            JsonObject next = roomManagementState.deepCopy();
            appendLogEntry(next, entry);
            next.addProperty("last_updated", System.currentTimeMillis());
            roomManagementState = next;
        }
    }

    /**
     * 检查客户端健康状态。
     *
     * @return 包含 true 的 CompletableFuture
     */
    public static CompletableFuture<Boolean> checkHealth() {
        return CompletableFuture.completedFuture(true);
    }

    /**
     * 紧急停止所有服务。
     *
     * @param peaceful 是否平滑停止（当前未使用）
     * @return CompletableFuture，完成时无返回值
     */
    public static CompletableFuture<Void> panic(boolean peaceful) {
        EasyTierManager.getInstance().stop();
        stopScaffoldingClient();
        stopScaffoldingServer();
        resetProfiles();
        clearDynamicPort();
        currentState = State.IDLE;
        currentRoom = "";
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 获取日志内容。
     *
     * @param fetch 是否获取最新日志（当前实现总是返回空）
     * @return 包含日志内容的 CompletableFuture
     */
    public static CompletableFuture<String> getLog(boolean fetch) {
        return CompletableFuture.completedFuture("");
    }

    /**
     * 设置正在扫描的玩家（当前实现为空）。
     *
     * @param player 玩家名称
     * @return CompletableFuture，完成时无返回值
     */
    public static CompletableFuture<Void> setScanning(String player) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 将客户端状态设置为 IDLE 并停止相关服务。
     *
     * @return CompletableFuture，完成时无返回值
     */
    public static CompletableFuture<Void> setIdle() {
        EasyTierManager.getInstance().stop();
        stopScaffoldingClient();
        stopScaffoldingServer();
        resetProfiles();
        currentState = State.IDLE;
        currentRoom = "";
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 加入指定房间。
     *
     * @param room 房间代码
     * @param player 玩家名称
     * @return 包含加入结果（成功/失败）的 CompletableFuture
     */
    public static CompletableFuture<Boolean> joinRoom(String room, String player) {
        LOGGER.info("Joining room (EasyTier Network): {}", room);
        currentState = State.JOINING_STARTING;
        return CompletableFuture.supplyAsync(() -> {
            try {
                EasyTierManager manager = EasyTierManager.getInstance();
                
                
                if (!manager.isInitialized()) {
                    manager.initialize().join();
                }

                manager.stop();
                
                String name = "";
                String secret = "";
                
                
                if (room.matches("^U/[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$")) {
                    String raw = room.substring(2);
                    String[] parts = raw.split("-");
                    name = "scaffolding-mc-" + parts[0] + "-" + parts[1];
                    secret = parts[2] + "-" + parts[3];
                } else {
                    LOGGER.warn("Invalid room code format: {}", room);
                    currentState = State.ERROR;
                    lastError = "Invalid room code format";
                    return false;
                }
                
                com.endercore.core.easytier.EasyTierConfig cfg = manager.getConfig();
                cfg.networkName = name;
                cfg.networkSecret = secret;
                cfg.hostname = player == null ? "" : player;
                cfg.tcpWhitelist = "0";
                cfg.udpWhitelist = "0";
                if (hasDynamicPort()) {
                    cfg.rpcPort = getPort();
                }
                manager.start(cfg);
                
                
                Thread.sleep(1000);

                
                boolean hostFound = false;
                boolean hostSeenButNoIp = false;
                for (int i = 0; i < 30; i++) {
                    ScanResult result = scanForScaffoldingRemote();
                    if (result.address != null) {
                        hostFound = true;
                        break;
                    }
                    if (result.hostSeen) {
                        hostSeenButNoIp = true;
                    }
                    Thread.sleep(1000);
                }

                if (!hostFound) {
                    LOGGER.warn("Room host not found for room: {}", room);
                    manager.stop();
                    currentState = State.ERROR;
                    if (hostSeenButNoIp) {
                        lastError = "Host found but unreachable (Route Error)";
                    } else {
                        lastError = "Room not found or host offline";
                    }
                    return false;
                }

                
                if (hasDynamicPort()) {
                     LanDiscovery.startBroadcaster(getPort(), "Ender Online Room");
                }
                
                currentState = State.JOINING;
                currentRoom = room;
                localPlayerName = player == null ? "" : player;
                startScaffoldingClient();
                return true;
            } catch (Exception e) {
                LOGGER.error("Failed to start EasyTier for joining", e);
                currentState = State.ERROR;
                lastError = e.getMessage();
                return false;
            }
        });
    }

    /**
     * 记住最后加入的房间代码。
     *
     * @param roomCode 房间代码
     */
    public static void rememberRoomCode(String roomCode) {
        lastRoomCode = roomCode;
    }

    /**
     * 获取最后加入的房间代码。
     *
     * @return 房间代码
     */
    public static String getLastRoomCode() {
        return lastRoomCode;
    }

    /**
     * 获取最后一次发生的错误信息。
     *
     * @return 错误信息字符串
     */
    public static String getLastError() {
        return lastError;
    }

    /**
     * 获取当前状态详情。
     *
     * @return 包含状态信息的 JSON 字符串的 CompletableFuture
     */
    public static CompletableFuture<String> getState() {
        JsonObject json = new JsonObject();
        
        json.addProperty("status", currentState.name());
        
        if (currentState == State.HOSTING) {
             json.addProperty("state", "host-ok");
             json.addProperty("room", currentRoom);

             JsonArray profileArray = buildProfilesJson();
             if (profileArray.size() > 0) {
                 json.add("profiles", profileArray);
                 json.add("players", buildPlayersJson(profileArray));
             }
        } else if (currentState == State.JOINING) {
             json.addProperty("state", "guest-ok");
             json.addProperty("room", currentRoom);
             JsonArray profileArray = buildProfilesJson();
             if (profileArray.size() > 0) {
                 json.add("profiles", profileArray);
                 json.add("players", buildPlayersJson(profileArray));
             }
        } else if (currentState == State.HOSTING_STARTING) {
             json.addProperty("state", "host-starting");
        } else if (currentState == State.JOINING_STARTING) {
             json.addProperty("state", "guest-starting");
        } else if (currentState == State.ERROR) {
             json.addProperty("error", lastError);
        }
        
        return CompletableFuture.completedFuture(json.toString());
    }

    /**
     * 创建默认的房间管理状态。
     *
     * @return 包含默认配置的 JsonObject
     */
    private static JsonObject createDefaultRoomManagementState() {
        JsonObject json = new JsonObject();
        json.addProperty("room_name", "未命名房间");
        json.addProperty("room_remark", "");
        json.addProperty("visitor_permission", "可交互");
        json.addProperty("whitelist_enabled", false);
        json.add("whitelist", new JsonArray());
        json.add("blacklist", new JsonArray());
        json.add("mute_list", new JsonArray());
        json.add("operation_logs", new JsonArray());
        json.addProperty("allow_cheats", false);
        json.addProperty("allow_pvp", true);
        json.addProperty("spawn_protection", 16);
        json.addProperty("keep_inventory", false);
        json.addProperty("fire_spread", true);
        json.addProperty("mob_spawning", true);
        json.addProperty("time_lock", "cycle");
        json.addProperty("weather_lock", false);
        json.addProperty("respawn_x", 0);
        json.addProperty("respawn_y", 0);
        json.addProperty("respawn_z", 0);
        json.addProperty("world_border_center_x", 0);
        json.addProperty("world_border_center_z", 0);
        json.addProperty("world_border_radius", 0);
        json.addProperty("auto_reconnect", true);
        json.addProperty("reconnect_retries", 3);
        json.addProperty("host_migration", false);
        json.addProperty("backend_version", "当前");
        json.addProperty("update_policy", "立即");
        json.addProperty("log_level", "INFO");
        json.addProperty("cpu_limit", 0);
        json.addProperty("memory_limit", 0);
        JsonArray versions = new JsonArray();
        versions.add("当前");
        versions.add("备用");
        json.add("backend_versions", versions);
        json.addProperty("last_updated", System.currentTimeMillis());
        
        
        loadRoomConfig(json);
        
        return json;
    }

    /**
     * 合并两个 JsonObject，将 source 的内容合并到 target 中。
     *
     * @param target 目标 JsonObject
     * @param source 源 JsonObject
     */
    private static void mergeJsonObject(JsonObject target, JsonObject source) {
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            String key = entry.getKey();
            if ("log_entry".equals(key)) {
                continue;
            }
            JsonElement value = entry.getValue();
            target.add(key, value);
        }
    }

    /**
     * 向 JsonObject 添加日志条目。
     *
     * @param target 目标 JsonObject
     * @param entry 日志内容
     */
    private static void appendLogEntry(JsonObject target, String entry) {
        JsonArray logs = target.has("operation_logs") && target.get("operation_logs").isJsonArray()
                ? target.getAsJsonArray("operation_logs")
                : new JsonArray();
        String line = System.currentTimeMillis() + " " + entry;
        logs.add(line);
        JsonArray trimmed = new JsonArray();
        int start = Math.max(0, logs.size() - 20);
        for (int i = start; i < logs.size(); i++) {
            trimmed.add(logs.get(i));
        }
        target.add("operation_logs", trimmed);
    }

    /**
     * 开始主持游戏（创建房间）。
     *
     * @param port Minecraft 服务器端口
     * @param playerName 玩家名称
     * @return 包含房间代码的 CompletableFuture
     */
    public static CompletableFuture<String> startHosting(int port, String playerName) {
        LOGGER.info("Starting hosting for {} on port {}", playerName, port);
        currentState = State.HOSTING_STARTING;
        return CompletableFuture.supplyAsync(() -> {
            try {
                EasyTierManager manager = EasyTierManager.getInstance();
                
                
                if (!manager.isInitialized()) {
                    manager.initialize().join();
                }
                
                manager.stop();
                
                
                String p1 = generateRandomString(4);
                String p2 = generateRandomString(4);
                String p3 = generateRandomString(4);
                String p4 = generateRandomString(4);
                
                String roomCode = "U/" + p1 + "-" + p2 + "-" + p3 + "-" + p4;
                String networkName = "scaffolding-mc-" + p1 + "-" + p2;
                String networkSecret = p3 + "-" + p4;

                hostedMcPort = port;
                localPlayerName = playerName == null ? "" : playerName;
                int serverPort = startScaffoldingServer(port, localPlayerName);
                
                
                com.endercore.core.easytier.EasyTierConfig cfg = manager.getConfig();
                cfg.networkName = networkName;
                cfg.networkSecret = networkSecret;
                cfg.hostname = SCAFFOLDING_PREFIX + serverPort;
                cfg.tcpWhitelist = serverPort + "," + port;
                cfg.udpWhitelist = String.valueOf(port);
                if (hasDynamicPort()) {
                    cfg.rpcPort = getPort();
                }
                
                if (roomManagementState.has("log_level")) {
                    cfg.logLevel = roomManagementState.get("log_level").getAsString();
                }
                
                manager.start(cfg);
                
                
                Thread.sleep(1000);

                
                if (hasDynamicPort()) {
                     String remark = roomManagementState.has("room_remark") ? roomManagementState.get("room_remark").getAsString() : "";
                     if (remark == null || remark.isEmpty()) {
                         remark = "Ender Online Room";
                     }
                     LanDiscovery.startBroadcaster(getPort(), remark);
                }
                
                currentState = State.HOSTING;
                currentRoom = roomCode;
                
                return roomCode;
            } catch (Exception e) {
                LOGGER.error("Failed to start hosting", e);
                currentState = State.ERROR;
                lastError = e.getMessage();
                stopScaffoldingServer();
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 生成指定长度的随机字符串。
     *
     * @param length 字符串长度
     * @return 随机字符串
     */
    private static String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 启动 Scaffolding 服务器。
     *
     * @param mcPort Minecraft 服务器端口
     * @param hostName 主机名称
     * @return Scaffolding 服务器绑定的端口
     */
    private static int startScaffoldingServer(int mcPort, String hostName) {
        stopScaffoldingServer();
        resetProfiles();
        hostedMcPort = mcPort;
        scaffoldingPort = pickAvailablePort(DEFAULT_SCAFFOLDING_PORT);
        Profile hostProfile = new Profile(LOCAL_MACHINE_ID, hostName, VENDOR, "HOST");
        profiles.add(hostProfile);
        profileLastSeen.put(LOCAL_MACHINE_ID, System.currentTimeMillis());
        CoreWebSocketServer server = CoreComm.newServer(new InetSocketAddress("0.0.0.0", scaffoldingPort), 4 * 1024 * 1024, null);
        server.register("c:ping", EnderApiClient::handlePing);
        server.register("c:protocols", EnderApiClient::handleProtocols);
        server.register("c:server_port", EnderApiClient::handleServerPort);
        server.register("c:player_ping", EnderApiClient::handlePlayerPing);
        server.register("c:player_profiles_list", EnderApiClient::handlePlayerProfilesList);
        server.register("c:room_state_sync", EnderApiClient::handleRoomStateSync);
        server.start();
        server.awaitStarted(Duration.ofSeconds(3));
        scaffoldingServer = server;
        profileScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Ender-Scaffolding-Profiles");
            t.setDaemon(true);
            return t;
        });
        profileScheduler.scheduleWithFixedDelay(EnderApiClient::pruneGuestProfiles, 5, 5, TimeUnit.SECONDS);
        return scaffoldingPort;
    }

    /**
     * 停止 Scaffolding 服务器。
     */
    private static void stopScaffoldingServer() {
        if (profileScheduler != null) {
            profileScheduler.shutdownNow();
            profileScheduler = null;
        }
        CoreWebSocketServer server = scaffoldingServer;
        scaffoldingServer = null;
        if (server != null) {
            try {
                server.stop(1000);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 启动 Scaffolding 客户端。
     */
    private static void startScaffoldingClient() {
        stopScaffoldingClient();
        scaffoldingClientScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Ender-Scaffolding-Client");
            t.setDaemon(true);
            return t;
        });
        scaffoldingClientScheduler.scheduleWithFixedDelay(EnderApiClient::pollScaffoldingServer, 0, 5, TimeUnit.SECONDS);
    }

    /**
     * 停止 Scaffolding 客户端。
     */
    private static void stopScaffoldingClient() {
        if (scaffoldingClientScheduler != null) {
            scaffoldingClientScheduler.shutdownNow();
            scaffoldingClientScheduler = null;
        }
        CoreWebSocketClient client = scaffoldingClient;
        scaffoldingClient = null;
        scaffoldingRemote = null;
        if (client != null) {
            try {
                client.close(Duration.ofSeconds(1)).join();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 轮询 Scaffolding 服务器以保持连接和同步数据。
     */
    private static void pollScaffoldingServer() {
        if (currentState != State.JOINING) {
            return;
        }
        
        
        updateProfilesFromEasyTier();

        
        InetSocketAddress remote = findScaffoldingRemote();
        if (remote == null) {
            LOGGER.debug("Scaffolding remote not found during poll");
            return;
        }
        if (scaffoldingRemote == null || !scaffoldingRemote.equals(remote) || scaffoldingClient == null || !scaffoldingClient.isConnected()) {
            LOGGER.info("Connecting to scaffolding remote: {}", remote);
            connectScaffolding(remote);
        }
        CoreWebSocketClient client = scaffoldingClient;
        if (client == null || !client.isConnected()) {
            LOGGER.warn("Scaffolding client not connected after attempt");
            return;
        }
        try {
            JsonObject ping = new JsonObject();
            ping.addProperty("machine_id", LOCAL_MACHINE_ID);
            ping.addProperty("name", localPlayerName);
            ping.addProperty("vendor", VENDOR);
            
            client.sendSync("c:player_ping", ping.toString().getBytes(StandardCharsets.UTF_8), Duration.ofSeconds(10));
            CoreResponse resp = client.sendSync("c:player_profiles_list", new byte[0], Duration.ofSeconds(10));
            if (!resp.isOk()) {
                LOGGER.warn("Failed to fetch profiles: status={}", resp.status());
                return;
            }
            String json = new String(resp.payload(), StandardCharsets.UTF_8);
            
            JsonArray array = GSON.fromJson(json, JsonArray.class);
            if (array != null) {
                updateProfilesFromArray(array);
            }

            
            CoreResponse stateResp = client.sendSync("c:room_state_sync", new byte[0], Duration.ofSeconds(10));
            if (stateResp.isOk()) {
                String stateJson = new String(stateResp.payload(), StandardCharsets.UTF_8);
                updateRoomManagementState(stateJson);
            } else {
                LOGGER.warn("Failed to sync room state: status={}", stateResp.status());
            }

            
            CoreResponse portResp = client.sendSync("c:server_port", new byte[0], Duration.ofSeconds(10));
            if (portResp.isOk()) {
                try {
                    java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(portResp.payload()));
                    remoteMcPort = in.readUnsignedShort();
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            LOGGER.error("Error polling scaffolding server", e);
        }
    }

    /**
     * 从 EasyTier 获取并更新玩家资料。
     */
    private static void updateProfilesFromEasyTier() {
        try {
            Map<String, String> hostnames = EasyTierManager.getInstance().getPeerHostnames();
            for (Map.Entry<String, String> entry : hostnames.entrySet()) {
                String id = entry.getKey();
                String hostname = entry.getValue();
                if (hostname == null || hostname.isBlank()) continue;
                
                
                
                
                
                if (hostname.startsWith("PublicServer_") || hostname.contains(".easytier.")) {
                    continue;
                }

                String kind = "GUEST";
                String name = hostname;
                if (hostname.startsWith(SCAFFOLDING_PREFIX)) {
                    kind = "HOST";
                }
                
                boolean found = false;
                for (Profile p : profiles) {
                    if (p.machineId.equals(id)) {
                        if ("GUEST".equals(p.kind)) {
                            p.name = name;
                            profileLastSeen.put(id, System.currentTimeMillis());
                        } else if ("HOST".equals(p.kind)) {
                             profileLastSeen.put(id, System.currentTimeMillis());
                        }
                        found = true;
                        break;
                    }
                }
                
                if (!found) {
                    
                    String displayName = name;
                    
                    profiles.add(new Profile(id, displayName, "EasyTier", kind));
                    profileLastSeen.put(id, System.currentTimeMillis());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to update profiles from EasyTier", e);
        }
    }

    /**
     * 连接到 Scaffolding 服务器。
     *
     * @param remote 远程服务器地址
     */
    private static void connectScaffolding(InetSocketAddress remote) {
        try {
            if (scaffoldingClient != null) {
                scaffoldingClient.close(Duration.ofSeconds(1)).join();
            }
        } catch (Exception ignored) {
        }
        CoreWebSocketClient client = CoreComm.newClient(CoreWebSocketConfig.builder()
                .connectTimeout(Duration.ofSeconds(10))
                .requestTimeout(Duration.ofSeconds(15))
                .heartbeatInterval(Duration.ZERO)
                .build(), null, null);
        try {
            URI uri = URI.create("ws://" + remote.getHostString() + ":" + remote.getPort() + "/ws");
            LOGGER.info("Attempting WebSocket connection to: {}", uri);
            client.connect(uri).get(10, TimeUnit.SECONDS);
            scaffoldingClient = client;
            scaffoldingRemote = remote;
            LOGGER.info("WebSocket connected successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to connect to scaffolding server: " + remote, e);
            try {
                client.close(Duration.ofSeconds(1)).join();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 扫描结果类。
     */
    private static class ScanResult {
        InetSocketAddress address;
        boolean hostSeen;
        boolean ipMissing;
    }

    /**
     * 扫描 Scaffolding 远程主机。
     *
     * @return 扫描结果
     */
    private static ScanResult scanForScaffoldingRemote() {
        ScanResult result = new ScanResult();
        Map<String, String> hostnames = EasyTierManager.getInstance().getPeerHostnames();
        Map<String, String> ips = EasyTierManager.getInstance().getPeerIps();
        for (Map.Entry<String, String> entry : hostnames.entrySet()) {
            String hostname = entry.getValue();
            if (hostname == null) {
                continue;
            }
            String trimmed = hostname.trim();
            LOGGER.info("Scanning host: {} -> {}", entry.getKey(), trimmed);
            if (!trimmed.startsWith(SCAFFOLDING_PREFIX)) {
                continue;
            }
            
            result.hostSeen = true;
            
            LOGGER.info("Found scaffolding host: {} -> {}", entry.getKey(), trimmed);
            
            String portStr = trimmed.substring(SCAFFOLDING_PREFIX.length());
            int port;
            try {
                port = Integer.parseInt(portStr);
            } catch (Exception e) {
                continue;
            }
            String ip = ips.get(entry.getKey());
            if (ip == null || ip.isBlank()) {
                
                
                
                result.ipMissing = true;
                LOGGER.warn("Host found but no IP for peer: {}", entry.getKey());
                continue;
            }
            result.address = new InetSocketAddress(ip, port);
            return result;
        }
        return result;
    }

    /**
     * 查找 Scaffolding 远程主机地址。
     *
     * @return 远程主机地址，如果未找到则返回 null
     */
    private static InetSocketAddress findScaffoldingRemote() {
        return scanForScaffoldingRemote().address;
    }

    /**
     * 处理 Ping 请求。
     *
     * @param req 核心请求对象
     * @return 核心响应对象
     */
    private static CoreResponse handlePing(CoreRequest req) {
        return new CoreResponse(0, req.requestId(), req.kind(), req.payload());
    }

    /**
     * 处理协议列表请求。
     *
     * @param req 核心请求对象
     * @return 包含支持的协议列表的响应对象
     */
    private static CoreResponse handleProtocols(CoreRequest req) {
        byte[] payload = String.join("\0",
                "c:ping",
                "c:protocols",
                "c:server_port",
                "c:player_ping",
                "c:player_profiles_list",
                "c:room_state_sync").getBytes(StandardCharsets.UTF_8);
        return new CoreResponse(0, req.requestId(), req.kind(), payload);
    }

    /**
     * 处理房间状态同步请求。
     *
     * @param req 核心请求对象
     * @return 包含房间状态 JSON 的响应对象
     */
    private static CoreResponse handleRoomStateSync(CoreRequest req) {
        String json;
        synchronized (ROOM_STATE_LOCK) {
            json = roomManagementState.toString();
        }
        return new CoreResponse(0, req.requestId(), req.kind(), json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 处理服务器端口请求。
     *
     * @param req 核心请求对象
     * @return 包含服务器端口号的响应对象
     */
    private static CoreResponse handleServerPort(CoreRequest req) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeShort((short) hostedMcPort);
            return new CoreResponse(0, req.requestId(), req.kind(), baos.toByteArray());
        } catch (Exception e) {
            return new CoreResponse(1, req.requestId(), req.kind(), new byte[0]);
        }
    }

    /**
     * 处理玩家 Ping 请求，用于更新玩家在线状态。
     *
     * @param req 核心请求对象
     * @return 响应对象
     */
    private static CoreResponse handlePlayerPing(CoreRequest req) {
        try {
            String body = new String(req.payload(), StandardCharsets.UTF_8);
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (json == null) {
                return new CoreResponse(1, req.requestId(), req.kind(), new byte[0]);
            }
            String machineId = json.has("machine_id") ? json.get("machine_id").getAsString() : "";
            String name = json.has("name") ? json.get("name").getAsString() : "";
            String vendor = json.has("vendor") ? json.get("vendor").getAsString() : "";
            if (machineId.isBlank() || name.isBlank()) {
                return new CoreResponse(1, req.requestId(), req.kind(), new byte[0]);
            }
            if (machineId.equals(LOCAL_MACHINE_ID)) {
                updateHostProfile(name);
                return new CoreResponse(0, req.requestId(), req.kind(), new byte[0]);
            }
            upsertGuestProfile(machineId, name, vendor);
            return new CoreResponse(0, req.requestId(), req.kind(), new byte[0]);
        } catch (Exception e) {
            return new CoreResponse(1, req.requestId(), req.kind(), new byte[0]);
        }
    }

    /**
     * 处理获取玩家资料列表请求。
     *
     * @param req 核心请求对象
     * @return 包含玩家资料列表 JSON 的响应对象
     */
    private static CoreResponse handlePlayerProfilesList(CoreRequest req) {
        JsonArray array = buildProfilesJson();
        byte[] payload = array.toString().getBytes(StandardCharsets.UTF_8);
        return new CoreResponse(0, req.requestId(), req.kind(), payload);
    }

    /**
     * 更新主机玩家资料。
     *
     * @param name 主机玩家名称
     */
    private static void updateHostProfile(String name) {
        for (Profile profile : profiles) {
            if (profile.machineId.equals(LOCAL_MACHINE_ID) && "HOST".equals(profile.kind)) {
                profile.name = name;
                profileLastSeen.put(LOCAL_MACHINE_ID, System.currentTimeMillis());
                return;
            }
        }
        Profile host = new Profile(LOCAL_MACHINE_ID, name, VENDOR, "HOST");
        profiles.add(host);
        profileLastSeen.put(LOCAL_MACHINE_ID, System.currentTimeMillis());
    }

    /**
     * 更新或插入访客玩家资料。
     *
     * @param machineId 机器ID
     * @param name 玩家名称
     * @param vendor 客户端供应商
     */
    private static void upsertGuestProfile(String machineId, String name, String vendor) {
        for (Profile profile : profiles) {
            if (profile.machineId.equals(machineId)) {
                profile.name = name;
                profile.vendor = vendor;
                profile.kind = "GUEST";
                profileLastSeen.put(machineId, System.currentTimeMillis());
                return;
            }
        }
        profiles.add(new Profile(machineId, name, vendor, "GUEST"));
        profileLastSeen.put(machineId, System.currentTimeMillis());
    }

    /**
     * 清理过期的访客资料。
     */
    private static void pruneGuestProfiles() {
        long now = System.currentTimeMillis();
        for (Profile profile : new ArrayList<>(profiles)) {
            if ("HOST".equals(profile.kind)) {
                continue;
            }
            Long last = profileLastSeen.get(profile.machineId);
            if (last == null || now - last > 10_000) {
                profiles.remove(profile);
                profileLastSeen.remove(profile.machineId);
            }
        }
    }

    /**
     * 从 JSON 数组更新资料列表。
     *
     * @param array JSON 数组
     */
    private static void updateProfilesFromArray(JsonArray array) {
        profiles.clear();
        profileLastSeen.clear();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            String name = obj.has("name") ? obj.get("name").getAsString() : "";
            String machineId = obj.has("machine_id") ? obj.get("machine_id").getAsString() : "";
            String vendor = obj.has("vendor") ? obj.get("vendor").getAsString() : "";
            String kind = obj.has("kind") ? obj.get("kind").getAsString() : "";
            if (machineId.isBlank() || name.isBlank()) {
                continue;
            }
            profiles.add(new Profile(machineId, name, vendor, kind.isBlank() ? "GUEST" : kind));
            profileLastSeen.put(machineId, System.currentTimeMillis());
        }
    }

    /**
     * 构建包含所有玩家资料的 JSON 数组。
     *
     * @return JSON 数组
     */
    private static JsonArray buildProfilesJson() {
        JsonArray array = new JsonArray();
        for (Profile profile : profiles) {
            if (profile.name == null || profile.name.isBlank()) {
                continue;
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("name", profile.name);
            obj.addProperty("machine_id", profile.machineId);
            obj.addProperty("vendor", profile.vendor);
            obj.addProperty("kind", profile.kind);
            array.add(obj);
        }
        return array;
    }

    /**
     * 构建仅包含玩家名称的 JSON 数组。
     *
     * @param profilesArray 玩家资料 JSON 数组
     * @return 玩家名称 JSON 数组
     */
    private static JsonArray buildPlayersJson(JsonArray profilesArray) {
        JsonArray players = new JsonArray();
        for (JsonElement element : profilesArray) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            if (!obj.has("name")) {
                continue;
            }
            String name = obj.get("name").getAsString();
            if (name != null && !name.isBlank()) {
                players.add(name);
            }
        }
        return players;
    }

    /**
     * 重置所有玩家资料。
     */
    private static void resetProfiles() {
        profiles.clear();
        profileLastSeen.clear();
    }

    /**
     * 选择一个可用的端口。
     *
     * @param preferred 首选端口
     * @return 可用的端口号
     */
    private static int pickAvailablePort(int preferred) {
        if (isPortAvailable(preferred)) {
            return preferred;
        }
        return findAvailablePort();
    }

    /**
     * 查找一个随机可用端口。
     *
     * @return 端口号
     */
    private static int findAvailablePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            return DEFAULT_SCAFFOLDING_PORT;
        }
    }

    /**
     * 检查指定端口是否可用。
     *
     * @param port 端口号
     * @return 如果端口可用则返回 true，否则返回 false
     */
    private static boolean isPortAvailable(int port) {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 玩家资料内部类。
     */
    private static final class Profile {
        private final String machineId;
        private volatile String name;
        private volatile String vendor;
        private volatile String kind;

        private Profile(String machineId, String name, String vendor, String kind) {
            this.machineId = machineId;
            this.name = name;
            this.vendor = vendor == null ? "" : vendor;
            this.kind = kind == null ? "" : kind;
        }
    }
}
