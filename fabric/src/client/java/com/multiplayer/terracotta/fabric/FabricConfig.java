package com.multiplayer.terracotta.fabric;

import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class FabricConfig {
    private static boolean loaded = false;
    private static String externalTerracottaPath = "";
    private static boolean autoUpdate = true;
    private static boolean autoStartBackend = false;

    private static Path getConfigPath() {
        Path configDir = MinecraftClient.getInstance().runDirectory.toPath().resolve("config");
        return configDir.resolve("terracotta-fabric.conf");
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Path path = getConfigPath();
            if (!Files.exists(path)) {
                return;
            }
            Map<String, String> values = new HashMap<>();
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                values.put(key, value);
            }
            externalTerracottaPath = values.getOrDefault("externalTerracottaPath", externalTerracottaPath);
            autoUpdate = parseBoolean(values.getOrDefault("autoUpdate", Boolean.toString(autoUpdate)));
            autoStartBackend = parseBoolean(values.getOrDefault("autoStartBackend", Boolean.toString(autoStartBackend)));
        } catch (Exception ignored) {
        }
    }

    private static boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static void save() {
        try {
            Path path = getConfigPath();
            Path dir = path.getParent();
            if (dir != null && !Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("externalTerracottaPath=").append(externalTerracottaPath == null ? "" : externalTerracottaPath).append("\n");
            sb.append("autoUpdate=").append(autoUpdate ? "true" : "false").append("\n");
            sb.append("autoStartBackend=").append(autoStartBackend ? "true" : "false").append("\n");
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static String getExternalTerracottaPath() {
        ensureLoaded();
        return externalTerracottaPath;
    }

    public static void setExternalTerracottaPath(String path) {
        ensureLoaded();
        externalTerracottaPath = path == null ? "" : path;
        save();
    }

    public static boolean isAutoUpdate() {
        ensureLoaded();
        return autoUpdate;
    }

    public static void setAutoUpdate(boolean value) {
        ensureLoaded();
        autoUpdate = value;
        save();
    }

    public static boolean isAutoStartBackend() {
        ensureLoaded();
        return autoStartBackend;
    }

    public static void setAutoStartBackend(boolean value) {
        ensureLoaded();
        autoStartBackend = value;
        save();
    }
}

