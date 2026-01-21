package com.multiplayer.ender.logic;

import com.endercore.core.easytier.EasyTierManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public class ProcessLauncher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessLauncher.class);
    
    public enum ProcessStatus {
        STOPPED, STARTING, RUNNING, CRASHED
    }

    public static boolean isRunning() {
        // We can check EasyTier status
        return true; // Mock for now, or check EasyTierManager
    }

    public static ProcessStatus getStatus() {
        return ProcessStatus.RUNNING;
    }

    public static boolean isCrashed() {
        return false;
    }

    public static void launch(Path executablePath, Path workDir, String... args) throws IOException {
        launch(executablePath, workDir, null, args);
    }

    public static void launch(Path executablePath, Path workDir, java.util.function.Consumer<String> outputHandler, String... args) throws IOException {
        LOGGER.info("ProcessLauncher (Compat) launching EasyTier...");
        try {
            EasyTierManager.getInstance().initialize().join();
            EasyTierManager.getInstance().start(args);
        } catch (Exception e) {
            throw new IOException("Failed to start EasyTier", e);
        }
    }

    public static void stop() {
        LOGGER.info("Stopping process...");
        try {
            EasyTierManager.getInstance().stop();
        } catch (Exception e) {
            LOGGER.error("Failed to stop process", e);
        }
    }
}

