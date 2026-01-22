package com.multiplayer.ender;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Forge 模组配置类。
 * 定义模组的配置项，包括核心文件路径、自动更新和自动启动等。
 */
public class ConfigForge {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();

    /** 外部核心文件路径 */
    public static final ForgeConfigSpec.ConfigValue<String> EXTERNAL_ender_PATH;
    /** 自动更新开关 */
    public static final ForgeConfigSpec.BooleanValue AUTO_UPDATE;
    /** 自动启动后端开关 */
    public static final ForgeConfigSpec.BooleanValue AUTO_START_BACKEND;

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

    static final ForgeConfigSpec SPEC = BUILDER.build();
    public static final ForgeConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
}




