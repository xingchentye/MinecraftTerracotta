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

public class VersionChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger(VersionChecker.class);
    // Keep checking original repo or change to EasyTier? 
    // For now, let's keep it but maybe we don't really use it for core update anymore
    private static final String API_URL = "https://gitee.com/api/v5/repos/burningtnt/Ender/releases/latest";

    public static String getLatestVersion() throws IOException, InterruptedException {
        // Mock version check to always return current version to avoid update prompts
        // Or return "2.4.5" to match EasyTier?
        // Let's return a dummy version that indicates compatibility
        LOGGER.info("Version check bypassed for Ender Core.");
        return "9.9.9";
    }
}


