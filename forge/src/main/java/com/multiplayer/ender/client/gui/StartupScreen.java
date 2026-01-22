package com.multiplayer.ender.client.gui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.multiplayer.ender.ConfigForge;
import com.multiplayer.ender.logic.DownloadManager;
import com.multiplayer.ender.logic.PlatformHelper;
import com.multiplayer.ender.logic.ProcessLauncher;
import com.multiplayer.ender.logic.VersionChecker;
import com.multiplayer.ender.network.NetworkClient;
import com.multiplayer.ender.network.EnderApiClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 启动屏幕。
 * 负责在初次启动时检查环境、下载依赖、启动后台服务进程。
 */
public class StartupScreen extends EnderBaseScreen {
    private static final Logger LOGGER = LoggerFactory.getLogger(StartupScreen.class);
    /** 用于执行启动任务的单线程调度执行器 */
    private static final ScheduledExecutorService STARTUP_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "Ender-Startup");
        thread.setDaemon(true);
        return thread;
    });

    /** 当前主要状态文本 */
    private String statusText = "初始化中...";
    /** 当前次要状态文本 */
    private String subStatusText = "";
    /** 启动进度 (0.0 - 1.0) */
    private double progress = 0.0;
    /** 是否发生错误 */
    private boolean isError = false;
    /** 是否完成启动 */
    private boolean isFinished = false;
    /** 是否已开始启动序列 */
    private boolean isStarted = false;
    /** 是否保持进程存活（调试用） */
    private boolean keepProcessAlive = false;
    /** 是否为全新启动（非重连） */
    private boolean isFreshLaunch = false;
    /** 启动完成后的回调 */
    private Runnable onStartupComplete;

    public StartupScreen(Screen parent) {
        this(parent, null);
    }

    public StartupScreen(Screen parent, Runnable onStartupComplete) {
        super(Component.literal("末影联机启动器"), parent);
        this.onStartupComplete = onStartupComplete;
    }

    /**
     * 设置启动完成后的回调。
     *
     * @param onStartupComplete 回调函数
     */
    public void setOnStartupComplete(Runnable onStartupComplete) {
        this.onStartupComplete = onStartupComplete;
    }

    /**
     * 初始化内容。
     * 添加取消按钮，并在未启动时触发启动序列。
     */
    @Override
    protected void initContent() {
        this.layout.addToFooter(Button.builder(Component.literal("取消"), (button) -> {
            this.cancelAndClose();
        }).width(200).build());

        if (!isStarted && !isFinished && !isError) {
            startStartupSequence();
        }
    }

    /**
     * 开始启动序列。
     * 检查是否存在动态端口（后台服务是否已在运行）。
     * 如果已运行，尝试健康检查并连接。
     * 否则，执行完整启动流程。
     */
    private void startStartupSequence() {
        isStarted = true;

        if (EnderApiClient.hasDynamicPort()) {
            isFreshLaunch = false;
            updateStatus("检测到后台服务运行中...", 0.5);
            CompletableFuture.runAsync(() -> {
                if (EnderApiClient.checkHealth().join()) {
                    updateStatus("服务状态正常，正在连接...", 1.0);
                    delay(500).thenRun(() -> this.minecraft.execute(this::onStartupSuccess));
                } else {
                    LOGGER.warn("后台服务无响应，将重新启动...");
                    performFullStartup();
                }
            });
            return;
        }

        performFullStartup();
    }

    /**
     * 执行完整启动流程。
     * 包括环境检测、架构判断、资源下载、进程启动等。
     */
    private void performFullStartup() {
        isFreshLaunch = true;
        CompletableFuture.runAsync(() -> {
           try {
               updateStatus("正在检测系统环境...", 0.05);
               PlatformHelper.OS os = PlatformHelper.getOS();
               PlatformHelper.Arch arch = PlatformHelper.getArch();

               String customPathStr = ConfigForge.EXTERNAL_ender_PATH.get();
               if (!customPathStr.isEmpty()) {
                    Path customPath = Path.of(customPathStr);
                    if (Files.exists(customPath)) {
                        updateStatus("使用自定义核心...", 1.0);
                        launchAndConnect(customPath, customPath.getParent());
                        return;
                    } else {
                        throw new RuntimeException("找不到自定义核心文件: " + customPathStr);
                    }
               }

               Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
               Path downloadDir = gameDir.resolve("ender");
               if (!Files.exists(downloadDir)) {
                   Files.createDirectories(downloadDir);
               }
               boolean autoUpdate = ConfigForge.AUTO_UPDATE.get();

               Path existingExe = scanForExecutable(downloadDir);

               if (existingExe != null && !autoUpdate) {
                   updateStatus("发现本地核心，跳过更新...", 1.0);
                   launchAndConnect(existingExe, downloadDir);
                   return;
               }

               updateStatus("正在检查更新...", 0.1);
               String version;
               String filename;
               try {
                   version = VersionChecker.getLatestVersion();
                   filename = PlatformHelper.getDownloadFilename(version);
               } catch (Exception e) {
                   if (existingExe != null) {
                        LOGGER.warn("检查更新失败，使用本地版本", e);
                        updateStatus("检查更新失败，使用本地核心...", 1.0);
                        launchAndConnect(existingExe, downloadDir);
                        return;
                   }
                   throw e;
               }

               if (filename == null) {
                   throw new RuntimeException("不支持的平台: " + os + " (" + arch + ")");
               }

               String exeName = PlatformHelper.getExecutableName(version);

               existingExe = findExecutable(downloadDir, exeName);
               if (existingExe != null) {
                   updateStatus("发现最新版本核心，跳过下载...", 1.0);
                   launchAndConnect(existingExe, downloadDir);
                   return;
               }

               updateStatus("检测到平台: " + os + " (" + arch + ")", 0.15);
               delay(500).join();

               updateStatus("正在下载 EnderCore 组件...", 0.2);
               com.endercore.core.easytier.EasyTierManager.init(downloadDir);
               com.endercore.core.easytier.EasyTierManager.getInstance().initialize(p -> {
                   updateStatus("正在下载 EnderCore 组件... " + String.format("%.0f%%", p * 100), 0.2 + p * 0.7);
               }).join();

               launchAndConnect(null, downloadDir);

           } catch (Exception e) {
               LOGGER.error("启动失败", e);
               isError = true;
               updateStatus("启动失败: " + e.getMessage(), 0);
           }
       });
    }

    private Path scanForExecutable(Path dir) {
        if (!Files.exists(dir)) return null;
        try (var stream = Files.walk(dir, 3)) {
            return stream
                    .filter(p -> p.getFileName().toString().equals(PlatformHelper.getExecutableName()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            LOGGER.warn("查找可执行文件失败", e);
            return null;
        }
    }

    private Path findExecutable(Path dir, String exeName) {
        if (!Files.exists(dir)) return null;
        try {
            Path direct = dir.resolve(exeName);
            if (Files.exists(direct)) return direct;

            try (var stream = Files.walk(dir, 3)) {
                return stream
                    .filter(p -> p.getFileName().toString().equals(exeName))
                    .findFirst()
                    .orElse(null);
            }
        } catch (Exception e) {
            LOGGER.warn("查找可执行文件失败", e);
            return null;
        }
    }

    private void launchAndConnect(Path exePath, Path workDir) throws Exception {
        updateStatus("正在初始化...", 0.95);

        
        

        updateStatus("初始化完成", 1.0);
        
        int port = PlatformHelper.findAvailablePort();
        if (port == -1) {
            throw new RuntimeException("无法找到可用端口");
        }
        EnderApiClient.setPort(port); 

        

        delay(500).thenRun(() -> this.minecraft.execute(this::onStartupSuccess));
    }

    private CompletableFuture<Void> delay(long millis) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        STARTUP_EXECUTOR.schedule(() -> future.complete(null), millis, TimeUnit.MILLISECONDS);
        return future;
    }

    private CompletableFuture<Integer> waitForPort(Path portFile, int maxAttempts, long delayMs, String timeoutMessage) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<ScheduledFuture<?>> scheduledRef = new AtomicReference<>();
        Runnable task = () -> {
            if (isError) {
                completeAndCancel(future, scheduledRef, -1);
                return;
            }
            int attempt = attempts.incrementAndGet();
            int port = readPortFile(portFile);
            if (port > 0) {
                completeAndCancel(future, scheduledRef, port);
                return;
            }
            if (attempt >= maxAttempts) {
                ScheduledFuture<?> scheduled = scheduledRef.get();
                if (scheduled != null) {
                    scheduled.cancel(false);
                }
                future.completeExceptionally(new RuntimeException(timeoutMessage));
            }
        };
        scheduledRef.set(STARTUP_EXECUTOR.scheduleAtFixedRate(task, 0, delayMs, TimeUnit.MILLISECONDS));
        return future;
    }

    private int readPortFile(Path portFile) {
        if (!Files.exists(portFile)) return -1;
        try {
            String content = Files.readString(portFile);
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
            if (json.has("port")) {
                return json.get("port").getAsInt();
            }
        } catch (Exception e) {
        }
        return -1;
    }

    private <T> void completeAndCancel(CompletableFuture<T> future, AtomicReference<ScheduledFuture<?>> scheduledRef, T value) {
        ScheduledFuture<?> scheduled = scheduledRef.get();
        if (scheduled != null) {
            scheduled.cancel(false);
        }
        future.complete(value);
    }

    private void onStartupSuccess() {
        if (onStartupComplete != null) {
            keepProcessAlive = true;
            onStartupComplete.run();
        } else {
            this.onClose();
        }
    }

    private void updateStatus(String text, double progress) {
        this.statusText = text;
        this.progress = progress;
    }

    private void cancelAndClose() {
        if (!keepProcessAlive && isFreshLaunch) {
            new Thread(ProcessLauncher::stop, "Ender-Stopper").start();
        }
        this.onClose();
    }

    @Override
    public void removed() {
        super.removed();
        if (!keepProcessAlive && isFreshLaunch) {
            new Thread(ProcessLauncher::stop, "Ender-Stopper").start();
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        guiGraphics.drawCenteredString(this.font, this.statusText, centerX, centerY - 10, isError ? 0xFF5555 : 0xFFFFFF);
        if (!this.subStatusText.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, this.subStatusText, centerX, centerY + 5, 0xAAAAAA);
        }

        int barWidth = 200;
        int barHeight = 10;
        int barX = centerX - barWidth / 2;
        int barY = centerY + 20;

        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF555555);

        int filledWidth = (int) (barWidth * this.progress);
        guiGraphics.fill(barX, barY, barX + filledWidth, barY + barHeight, isError ? 0xFFFF5555 : 0xFF55FF55);
    }
}



