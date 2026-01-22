package com.multiplayer.ender;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Mod 配置文件定义类。
 * <p>
 * 使用 NeoForge 的 ConfigSpec 系统定义配置项。
 * 包含通用配置 (Common) 和客户端配置 (Client)。
 * </p>
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

    /** 外部核心文件路径配置项 */
    public static final ModConfigSpec.ConfigValue<String> EXTERNAL_ender_PATH;
    /** 自动更新核心配置项 */
    public static final ModConfigSpec.BooleanValue AUTO_UPDATE;
    /** 自动启动后端配置项 */
    public static final ModConfigSpec.BooleanValue AUTO_START_BACKEND;

    static {
        CLIENT_BUILDER.comment("客户端设置").push("client");

        EXTERNAL_ender_PATH = CLIENT_BUILDER
                .comment("外部核心文件路径 (如果设置，将跳过下载并直接使用此文件)")
                .define("externalEnderPath", "");

        AUTO_UPDATE = CLIENT_BUILDER
                .comment("是否自动更新核心")
                .define("autoUpdate", true);

        AUTO_START_BACKEND = CLIENT_BUILDER
                .comment("是否自动启动核心 (进入菜单时)")
                .define("autoStartBackend", false);

        CLIENT_BUILDER.pop();
    }

    /** 通用配置规格对象 */
    static final ModConfigSpec SPEC = BUILDER.build();
    /** 客户端配置规格对象 */
    public static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
}




