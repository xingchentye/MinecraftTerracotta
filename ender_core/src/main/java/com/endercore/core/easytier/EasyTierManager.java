package com.endercore.core.easytier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class EasyTierManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(EasyTierManager.class);
    private static EasyTierManager instance;
    private final EasyTierDownloader downloader;
    private EasyTierRunner runner;
    private final Path workDir;
    private EasyTierConfig config;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private EasyTierManager(Path workDir) {
        this.workDir = workDir;
        this.downloader = new EasyTierDownloader(workDir);
        migrateOldFiles();
    }

    private void migrateOldFiles() {
        try {
            if (!Files.exists(workDir)) {
                Files.createDirectories(workDir);
            }

            // Migrate easytier_config.json
            Path oldConfig = Paths.get("easytier_config.json");
            Path newConfig = workDir.resolve("easytier_config.json");
            if (Files.exists(oldConfig)) {
                try {
                    Files.move(oldConfig, newConfig, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("Migrated easytier_config.json to {}", newConfig);
                } catch (Exception e) {
                    LOGGER.warn("Failed to migrate easytier_config.json", e);
                }
            }

            // Migrate easytier directory
            Path oldDir = Paths.get("easytier");
            Path newDir = workDir.resolve("easytier");
            if (Files.exists(oldDir) && Files.isDirectory(oldDir)) {
                if (!Files.exists(newDir)) {
                    try {
                        Files.move(oldDir, newDir, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        LOGGER.info("Migrated easytier directory to {}", newDir);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to migrate easytier directory", e);
                    }
                } else {
                    // If target exists, delete source to clean up
                    try (java.util.stream.Stream<Path> walk = Files.walk(oldDir)) {
                        walk.sorted(java.util.Comparator.reverseOrder())
                                .map(Path::toFile)
                                .forEach(java.io.File::delete);
                        LOGGER.info("Removed old easytier directory from root");
                    } catch (Exception e) {
                        LOGGER.warn("Failed to remove old easytier directory", e);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Migration failed", e);
        }
    }

    public static synchronized EasyTierManager getInstance() {
        if (instance == null) {
            // Default path, relative to working directory
            instance = new EasyTierManager(Paths.get("ender_core_data"));
        }
        return instance;
    }

    public static synchronized void init(Path workDir) {
        if (instance == null) {
            instance = new EasyTierManager(workDir);
        } else if (!instance.workDir.equals(workDir)) {
            LOGGER.warn("EasyTierManager already initialized with different path: {} vs {}", instance.workDir, workDir);
        }
    }

    public CompletableFuture<Void> initialize() {
        return initialize(null);
    }

    public CompletableFuture<Void> initialize(Consumer<Double> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            try {
                LOGGER.info("Initializing EasyTier...");
                loadConfig(); // Load config during initialization
                Path installDir = downloader.downloadAndExtract(progressCallback);
                // The downloader now returns the base dir, and files are extracted there
                // But we need to handle if it is in a subdirectory (which downloadAndExtract tries to do)
                // However, our downloader change made it return the directory containing the executable.
                
                // Let's just find the executable in installDir
                Path executable = installDir.resolve(isWindows() ? "easytier-core.exe" : "easytier-core");
                
                // If not found directly, check if it's inside a subdirectory (e.g. from zip extraction)
                if (!Files.exists(executable)) {
                     try (var stream = Files.walk(installDir, 2)) {
                         Path found = stream.filter(p -> p.getFileName().toString().equals(isWindows() ? "easytier-core.exe" : "easytier-core"))
                                            .findFirst().orElse(null);
                         if (found != null) {
                             executable = found;
                         }
                     }
                }
                
                runner = new EasyTierRunner(executable);
                LOGGER.info("EasyTier initialized at {}", executable);
            } catch (Exception e) {
                LOGGER.error("Failed to initialize EasyTier", e);
                throw new RuntimeException(e);
            }
        });
    }

    public void loadConfig() {
        Path configPath = workDir.resolve("easytier_config.json");
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                config = gson.fromJson(json, EasyTierConfig.class);
                
                // Migration: Remove old peer
                if (config.peers != null) {
                    boolean removed = config.peers.removeIf(p -> p.contains("etnode.zkitefly.eu.org"));
                    if (removed) {
                         saveConfig();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load config, using default", e);
                config = new EasyTierConfig();
            }
        } else {
            config = new EasyTierConfig();
            saveConfig();
        }
    }

    public void saveConfig() {
        try {
            if (!Files.exists(workDir)) {
                Files.createDirectories(workDir);
            }
            Path configPath = workDir.resolve("easytier_config.json");
            Files.writeString(configPath, gson.toJson(config));
        } catch (Exception e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    public EasyTierConfig getConfig() {
        if (config == null) {
            loadConfig();
        }
        return config;
    }

    public boolean isInitialized() {
        return runner != null;
    }

    public void start(String networkName, String networkSecret) throws IOException {
        EasyTierConfig cfg = getConfig();
        cfg.networkName = networkName;
        cfg.networkSecret = networkSecret;
        // Don't save transient room info to disk, just use in memory for start
        start(cfg);
    }

    public void start(EasyTierConfig config) throws IOException {
        if (runner == null) {
            throw new IllegalStateException("EasyTier not initialized. Call initialize() first.");
        }

        // Dynamic RPC port check
        if (config.rpcPort > 0 && !isPortAvailable(config.rpcPort)) {
            LOGGER.warn("RPC Port {} is in use, finding a new one...", config.rpcPort);
            int newPort = findAvailablePort();
            if (newPort > 0) {
                config.rpcPort = newPort;
                LOGGER.info("Using new RPC Port: {}", newPort);
            }
        }

        killAllExistingInstances();

        List<String> args = config.toArgs();
        
        java.util.Map<String, String> env = new java.util.HashMap<>();
        if (config.logLevel != null && !config.logLevel.isEmpty()) {
            env.put("RUST_LOG", config.logLevel);
        }
        
        runner.start(args.toArray(new String[0]), env);
    }

    private int findAvailablePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            LOGGER.error("Failed to find available port", e);
            return -1;
        }
    }

    private boolean isPortAvailable(int port) {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void start(String... args) throws IOException {
        if (runner == null) {
            throw new IllegalStateException("EasyTier not initialized. Call initialize() first.");
        }
        runner.start(args);
    }

    public void stop() {
        if (runner != null) {
            runner.stop();
        }
    }
    
    public List<String> getPeers() {
        if (runner != null) {
            return runner.getPeers();
        }
        return Collections.emptyList();
    }
    
    public java.util.Map<String, String> getPeerHostnames() {
        if (runner != null) {
            return runner.getPeerHostnames();
        }
        return Collections.emptyMap();
    }

    public java.util.Map<String, String> getPeerIps() {
        if (runner != null) {
            return runner.getPeerIps();
        }
        return Collections.emptyMap();
    }
    
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private void killAllExistingInstances() {
        LOGGER.info("Cleaning up existing EasyTier processes...");
        try {
            if (isWindows()) {
                new ProcessBuilder("taskkill", "/F", "/IM", "easytier-core.exe").start().waitFor();
            } else {
                new ProcessBuilder("pkill", "-9", "-f", "easytier-core").start().waitFor();
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to kill existing EasyTier processes: {}", e.getMessage());
        }
    }
}
