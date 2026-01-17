package com.multiplayer.terracotta;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Mod 配置类
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> SERVER_HOST;
    public static final ModConfigSpec.IntValue SERVER_PORT;
    public static final ModConfigSpec.ConfigValue<String> EXTERNAL_TERRACOTTA_PATH;
    public static final ModConfigSpec.BooleanValue AUTO_UPDATE;
    public static final ModConfigSpec.BooleanValue AUTO_START_BACKEND;

    static {
        CLIENT_BUILDER.comment("客户端设置").push("client");

        SERVER_HOST = CLIENT_BUILDER
                .comment("服务器主机地址")
                .define("serverHost", "127.0.0.1");

        SERVER_PORT = CLIENT_BUILDER
                .comment("服务器端口")
                .defineInRange("serverPort", 25566, 1, 65535);

        EXTERNAL_TERRACOTTA_PATH = CLIENT_BUILDER
                .comment("外部核心文件路径 (如果设置，将跳过下载并直接使用此文件)")
                .define("externalTerracottaPath", "");

        AUTO_UPDATE = CLIENT_BUILDER
                .comment("是否自动更新核心")
                .define("autoUpdate", true);

        AUTO_START_BACKEND = CLIENT_BUILDER
                .comment("是否自动启动核心 (进入菜单时)")
                .define("autoStartBackend", false);

        CLIENT_BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();
    public static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
}

