package com.multiplayer.terracotta.logic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 版本检测工具类
 * 用于获取 Gitee 仓库的最新版本信息
 */
public class VersionChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger(VersionChecker.class);
    private static final String API_URL = "https://gitee.com/api/v5/repos/burningtnt/Terracotta/releases/latest";

    /**
     * 获取最新版本号 (Tag Name)
     * @return 最新版本号 (例如 "v0.4.1")
     * @throws IOException 如果获取失败
     */
    public static String getLatestVersion() throws IOException, InterruptedException {
        LOGGER.info("正在检查最新版本...");
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("无法获取版本信息，HTTP状态码: " + response.statusCode());
        }

        String body = response.body();
        // 使用简单的正则提取 tag_name
        // "tag_name": "v0.4.1"
        Pattern pattern = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(body);

        if (matcher.find()) {
            String version = matcher.group(1);
            LOGGER.info("检测到最新版本: {}", version);
            return version;
        } else {
            throw new IOException("无法解析版本信息");
        }
    }
}
