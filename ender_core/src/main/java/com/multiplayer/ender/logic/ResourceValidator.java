package com.multiplayer.ender.logic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceValidator.class);

    public static void validate() {
        LOGGER.info("Resource validation skipped for Ender Core (EasyTier).");
        // We assume EasyTierDownloader handles the necessary files.
    }
}

