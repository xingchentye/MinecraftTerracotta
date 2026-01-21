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
    private static volatile String localPlayerName = "";
    private static volatile String lastRoomCode = "";
    private static final Logger LOGGER = LoggerFactory.getLogger(EnderApiClient.class);
    private static int dynamicPort = -1;
    
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

    public static State getCurrentState() {
        return currentState;
    }

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

    private static void saveRoomConfig() {
        try {
            java.io.File parent = CONFIG_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            
            JsonObject toSave = new JsonObject();
            synchronized (ROOM_STATE_LOCK) {
                // Only save non-MC-native settings
                for (Map.Entry<String, JsonElement> entry : roomManagementState.entrySet()) {
                    String key = entry.getKey();
                    if (isMcNativeSetting(key)) {
                        continue;
                    }
                    toSave.add(key, entry.getValue());
                }
                // Explicitly save lists
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

    public static void setPort(int port) {
        dynamicPort = port;
    }

    public static void clearDynamicPort() {
        dynamicPort = -1;
        LanDiscovery.stopBroadcaster();
    }

    public static boolean hasDynamicPort() {
        return dynamicPort > 0;
    }

    public static int getPort() {
        return dynamicPort > 0 ? dynamicPort : 25566;
    }

    public static CompletableFuture<String> getMeta() {
        return CompletableFuture.completedFuture("{\"version\": \"ender_core_compat\"}");
    }

    public static JsonObject getRoomManagementStateSync() {
        synchronized (ROOM_STATE_LOCK) {
            return roomManagementState.deepCopy();
        }
    }

    public static void setLocalSettings(boolean allowCheats, String visitorPermission) {
        synchronized (ROOM_STATE_LOCK) {
            roomManagementState.addProperty("allow_cheats", allowCheats);
            roomManagementState.addProperty("visitor_permission", visitorPermission);
            roomManagementState.addProperty("last_updated", System.currentTimeMillis());
        }
        saveRoomConfig();
    }

    public static CompletableFuture<String> getRoomManagementState() {
        synchronized (ROOM_STATE_LOCK) {
            return CompletableFuture.completedFuture(roomManagementState.toString());
        }
    }

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
            }
        } catch (Exception ignored) {
        }
        return CompletableFuture.completedFuture(null);
    }

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

    public static CompletableFuture<Boolean> checkHealth() {
        return CompletableFuture.completedFuture(true);
    }

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

    public static CompletableFuture<String> getLog(boolean fetch) {
        return CompletableFuture.completedFuture("");
    }

    public static CompletableFuture<Void> setScanning(String player) {
        return CompletableFuture.completedFuture(null);
    }

    public static CompletableFuture<Void> setIdle() {
        EasyTierManager.getInstance().stop();
        stopScaffoldingClient();
        stopScaffoldingServer();
        resetProfiles();
        currentState = State.IDLE;
        currentRoom = "";
        return CompletableFuture.completedFuture(null);
    }

    public static CompletableFuture<Boolean> joinRoom(String room, String player) {
        LOGGER.info("Joining room (EasyTier Network): {}", room);
        currentState = State.JOINING_STARTING;
        return CompletableFuture.supplyAsync(() -> {
            try {
                EasyTierManager manager = EasyTierManager.getInstance();
                
                // Ensure initialized
                if (!manager.isInitialized()) {
                    manager.initialize().join();
                }

                manager.stop();
                
                String name = "";
                String secret = "";
                
                // Parse room code format: PART1-PART2-PART3-PART4
                if (room.matches("^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$")) {
                    String[] parts = room.split("-");
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
                
                // Wait for service to start
                Thread.sleep(1000);

                // Start LAN broadcast
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

    public static void rememberRoomCode(String roomCode) {
        lastRoomCode = roomCode;
    }

    public static String getLastRoomCode() {
        return lastRoomCode;
    }

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
        
        // Load persisted config to override defaults for custom settings
        loadRoomConfig(json);
        
        return json;
    }

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

    public static CompletableFuture<String> startHosting(int port, String playerName) {
        LOGGER.info("Starting hosting for {} on port {}", playerName, port);
        currentState = State.HOSTING_STARTING;
        return CompletableFuture.supplyAsync(() -> {
            try {
                EasyTierManager manager = EasyTierManager.getInstance();
                
                // Ensure initialized before stopping or starting
                if (!manager.isInitialized()) {
                    manager.initialize().join();
                }
                
                manager.stop();
                
                // Generate Room Code
                String p1 = generateRandomString(4);
                String p2 = generateRandomString(4);
                String p3 = generateRandomString(4);
                String p4 = generateRandomString(4);
                
                String roomCode = p1 + "-" + p2 + "-" + p3 + "-" + p4;
                String networkName = "scaffolding-mc-" + p1 + "-" + p2;
                String networkSecret = p3 + "-" + p4;

                hostedMcPort = port;
                localPlayerName = playerName == null ? "" : playerName;
                int serverPort = startScaffoldingServer(port, localPlayerName);
                
                // Start Host
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
                
                // Wait for service to start
                Thread.sleep(1000);

                // Start LAN broadcast
                if (hasDynamicPort()) {
                     String remark = roomManagementState.has("room_remark") ? roomManagementState.get("room_remark").getAsString() : "";
                     if (remark == null || remark.isEmpty()) {
                         remark = "Ender Online Room";
                     }
                     LanDiscovery.startBroadcaster(getPort(), remark);
                }
                
                currentState = State.HOSTING;
                currentRoom = roomCode;
                // Return room code
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

    private static String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

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

    private static void startScaffoldingClient() {
        stopScaffoldingClient();
        scaffoldingClientScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Ender-Scaffolding-Client");
            t.setDaemon(true);
            return t;
        });
        scaffoldingClientScheduler.scheduleWithFixedDelay(EnderApiClient::pollScaffoldingServer, 2, 5, TimeUnit.SECONDS);
    }

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

    private static void pollScaffoldingServer() {
        if (currentState != State.JOINING) {
            return;
        }
        InetSocketAddress remote = findScaffoldingRemote();
        if (remote == null) {
            return;
        }
        if (scaffoldingRemote == null || !scaffoldingRemote.equals(remote) || scaffoldingClient == null || !scaffoldingClient.isConnected()) {
            connectScaffolding(remote);
        }
        CoreWebSocketClient client = scaffoldingClient;
        if (client == null || !client.isConnected()) {
            return;
        }
        try {
            JsonObject ping = new JsonObject();
            ping.addProperty("machine_id", LOCAL_MACHINE_ID);
            ping.addProperty("name", localPlayerName);
            ping.addProperty("vendor", VENDOR);
            client.sendSync("c:player_ping", ping.toString().getBytes(StandardCharsets.UTF_8), Duration.ofSeconds(4));
            CoreResponse resp = client.sendSync("c:player_profiles_list", new byte[0], Duration.ofSeconds(4));
            if (!resp.isOk()) {
                return;
            }
            String json = new String(resp.payload(), StandardCharsets.UTF_8);
            JsonArray array = GSON.fromJson(json, JsonArray.class);
            if (array != null) {
                updateProfilesFromArray(array);
            }

            // Sync room state
            CoreResponse stateResp = client.sendSync("c:room_state_sync", new byte[0], Duration.ofSeconds(4));
            if (stateResp.isOk()) {
                String stateJson = new String(stateResp.payload(), StandardCharsets.UTF_8);
                updateRoomManagementState(stateJson);
            }
        } catch (Exception ignored) {
        }
    }

    private static void connectScaffolding(InetSocketAddress remote) {
        try {
            if (scaffoldingClient != null) {
                scaffoldingClient.close(Duration.ofSeconds(1)).join();
            }
        } catch (Exception ignored) {
        }
        CoreWebSocketClient client = CoreComm.newClient(CoreWebSocketConfig.builder()
                .connectTimeout(Duration.ofSeconds(3))
                .requestTimeout(Duration.ofSeconds(4))
                .heartbeatInterval(Duration.ZERO)
                .build(), null, null);
        try {
            URI uri = URI.create("ws://" + remote.getHostString() + ":" + remote.getPort() + "/ws");
            client.connect(uri).get(4, TimeUnit.SECONDS);
            scaffoldingClient = client;
            scaffoldingRemote = remote;
        } catch (Exception e) {
            try {
                client.close(Duration.ofSeconds(1)).join();
            } catch (Exception ignored) {
            }
        }
    }

    private static InetSocketAddress findScaffoldingRemote() {
        Map<String, String> hostnames = EasyTierManager.getInstance().getPeerHostnames();
        Map<String, String> ips = EasyTierManager.getInstance().getPeerIps();
        for (Map.Entry<String, String> entry : hostnames.entrySet()) {
            String hostname = entry.getValue();
            if (hostname == null) {
                continue;
            }
            String trimmed = hostname.trim();
            if (!trimmed.startsWith(SCAFFOLDING_PREFIX)) {
                continue;
            }
            String portStr = trimmed.substring(SCAFFOLDING_PREFIX.length());
            int port;
            try {
                port = Integer.parseInt(portStr);
            } catch (Exception e) {
                continue;
            }
            String ip = ips.get(entry.getKey());
            if (ip == null || ip.isBlank()) {
                continue;
            }
            return new InetSocketAddress(ip, port);
        }
        return null;
    }

    private static CoreResponse handlePing(CoreRequest req) {
        return new CoreResponse(0, req.requestId(), req.kind(), req.payload());
    }

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

    private static CoreResponse handleRoomStateSync(CoreRequest req) {
        String json;
        synchronized (ROOM_STATE_LOCK) {
            json = roomManagementState.toString();
        }
        return new CoreResponse(0, req.requestId(), req.kind(), json.getBytes(StandardCharsets.UTF_8));
    }

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

    private static CoreResponse handlePlayerProfilesList(CoreRequest req) {
        JsonArray array = buildProfilesJson();
        byte[] payload = array.toString().getBytes(StandardCharsets.UTF_8);
        return new CoreResponse(0, req.requestId(), req.kind(), payload);
    }

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

    private static void resetProfiles() {
        profiles.clear();
        profileLastSeen.clear();
    }

    private static int pickAvailablePort(int preferred) {
        if (isPortAvailable(preferred)) {
            return preferred;
        }
        return findAvailablePort();
    }

    private static int findAvailablePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            return DEFAULT_SCAFFOLDING_PORT;
        }
    }

    private static boolean isPortAvailable(int port) {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

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
