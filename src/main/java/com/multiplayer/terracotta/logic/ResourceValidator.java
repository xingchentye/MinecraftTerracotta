package com.multiplayer.terracotta.logic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

/**
 * 资源加载验证逻辑模块
 * 负责检查核心二进制文件是否存在
 */
public class ResourceValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceValidator.class);

    /**
     * 需要验证的核心资源文件列表
     * 这些文件应当位于 assets/minecraftterracotta/ 目录下
     */
    private static final String[] REQUIRED_RESOURCES = {
            "assets/minecraftterracotta/terracotta-0.4.1-windows-x86_64.exe",
            "assets/minecraftterracotta/VCRUNTIME140.DLL"
    };

    /**
     * 执行资源验证
     * 检查所有必需的资源文件是否可以从 ClassPath 中加载
     *
     * @throws IllegalStateException 如果有资源文件缺失
     */
    public static void validate() {
        LOGGER.info("开始验证核心资源文件...");
        boolean allFound = true;

        for (String resourcePath : REQUIRED_RESOURCES) {
            URL resourceUrl = ResourceValidator.class.getClassLoader().getResource(resourcePath);
            if (resourceUrl == null) {
                LOGGER.error("缺失核心资源文件: {}", resourcePath);
                allFound = false;
            } else {
                LOGGER.info("已确认资源文件: {}", resourcePath);
            }
        }

        if (!allFound) {
            throw new IllegalStateException("陶瓦联机 Mod 缺失核心资源文件，无法启动！请检查安装完整性。");
        }

        LOGGER.info("核心资源文件验证通过。");
    }
}
