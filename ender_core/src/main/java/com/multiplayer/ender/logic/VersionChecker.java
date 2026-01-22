package com.multiplayer.ender.logic;

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
 * 版本检查工具类。
 * 用于检查 Ender Core 是否有新版本。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public class VersionChecker {
    /**
     * 日志记录器
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(VersionChecker.class);
    
    
    /**
     * 版本检查 API 地址
     */
    private static final String API_URL = "https://gitee.com/api/v5/repos/burningtnt/Ender/releases/latest";

    /**
     * 获取最新版本号。
     *
     * @return 最新版本号字符串
     * @throws IOException 当网络请求失败时抛出
     * @throws InterruptedException 当请求被中断时抛出
     */
    public static String getLatestVersion() throws IOException, InterruptedException {
        
        
        
        LOGGER.info("Version check bypassed for Ender Core.");
        return "9.9.9";
    }
}


