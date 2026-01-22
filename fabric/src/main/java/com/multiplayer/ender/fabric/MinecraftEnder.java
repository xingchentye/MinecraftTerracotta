package com.multiplayer.ender.fabric;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric 模组主类。
 * 处理服务端或通用逻辑的初始化。
 */
public class MinecraftEnder implements ModInitializer {
	public static final String MOD_ID = "ender_online";

	
	
	
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * 模组初始化。
	 * 在模组加载时调用。
	 */
	@Override
	public void onInitialize() {
		
		
		

		LOGGER.info("Hello Fabric world!");
	}
}
