package com.multiplayer.terracotta.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.Gson;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class TerracottaApiClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerracottaApiClient.class);
    private static final Gson GSON = new Gson();
    private static int dynamicPort = -1;
    private static final int DEFAULT_PORT = 25566;

    public static void setPort(int port) {
        dynamicPort = port;
        LOGGER.info("已更新 API 目标端口为: {}", port);
    }

    public static void clearDynamicPort() {
        dynamicPort = -1;
    }

    public static boolean hasDynamicPort() {
        return dynamicPort > 0;
    }

    public static int getPort() {
        return dynamicPort > 0 ? dynamicPort : DEFAULT_PORT;
    }

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private static String getBaseUrl() {
        return String.format("http://127.0.0.1:%d", getPort());
    }

    public static CompletableFuture<String> getMeta() {
        return sendGetRequest("/meta");
    }

    public static CompletableFuture<Boolean> checkHealth() {
        return getMeta().thenApply(response -> response != null && !response.isEmpty())
                .exceptionally(e -> false);
    }

    public static CompletableFuture<Void> panic(boolean peaceful) {
        return sendGetRequest("/panic?peaceful=" + peaceful).thenAccept(r -> {});
    }

    public static CompletableFuture<String> getLog(boolean fetch) {
        return sendGetRequest("/log?fetch=" + fetch);
    }

    public static CompletableFuture<Void> setScanning(String player) {
        String encodedPlayer = URLEncoder.encode(player, StandardCharsets.UTF_8);
        return sendGetRequest("/state/scanning?player=" + encodedPlayer).thenAccept(r -> {});
    }

    public static CompletableFuture<Void> setIdle() {
        return sendGetRequest("/state/ide").thenAccept(r -> {});
    }

    public static CompletableFuture<Boolean> joinRoom(String room, String player) {
        String encodedRoom = URLEncoder.encode(room, StandardCharsets.UTF_8);
        String encodedPlayer = URLEncoder.encode(player, StandardCharsets.UTF_8);
        String url = getBaseUrl() + "/state/guesting?room=" + encodedRoom + "&player=" + encodedPlayer;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200)
                .exceptionally(e -> false);
    }

    public static CompletableFuture<String> getState() {
        return sendGetRequest("/state");
    }

    public static CompletableFuture<String> startHosting(int port, String player) {
        return setIdle()
                .thenCompose(v -> setScanning(player))
                .thenApply(v -> (String) null)
                .exceptionally(e -> "Exception: " + e.getMessage());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static CompletableFuture<String> sendGetRequest(String path) {
        String url = getBaseUrl() + path;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return response.body();
                    } else {
                        LOGGER.warn("API request failed: {} code={}", path, response.statusCode());
                        return null;
                    }
                })
                .exceptionally(e -> {
                    LOGGER.warn("API request error: {} msg={}", path, e.getMessage());
                    return null;
                });
    }
}

