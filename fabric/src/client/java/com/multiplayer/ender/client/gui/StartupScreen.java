package com.multiplayer.ender.client.gui;

import com.multiplayer.ender.fabric.FabricConfig;
import com.multiplayer.ender.logic.DownloadManager;
import com.multiplayer.ender.logic.PlatformHelper;
import com.multiplayer.ender.logic.ProcessLauncher;
import com.multiplayer.ender.logic.VersionChecker;
import com.multiplayer.ender.network.NetworkClient;
import com.multiplayer.ender.network.EnderApiClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class StartupScreen extends EnderBaseScreen {
    private static final Logger LOGGER = LoggerFactory.getLogger(StartupScreen.class);
    private static final ScheduledExecutorService STARTUP_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "Ender-Startup");
        thread.setDaemon(true);
        return thread;
    });

    private String statusText = "初始化中...";
    private String subStatusText = "";
    private double progress = 0.0;
    private boolean isError = false;
    private boolean isFinished = false;
    private boolean isStarted = false;
    private boolean keepProcessAlive = false;
    private boolean isFreshLaunch = false;
    private Runnable onStartupComplete;

    public StartupScreen(Screen parent) {
        this(parent, null);
    }

    public StartupScreen(Screen parent, Runnable onStartupComplete) {
        super(Text.literal("末影联机启动器"), parent);
        this.onStartupComplete = onStartupComplete;
    }

    public void setOnStartupComplete(Runnable onStartupComplete) {
        this.onStartupComplete = onStartupComplete;
    }

    @Override
    protected void initContent() {
        this.layout.addFooter(ButtonWidget.builder(Text.literal("取消"), button -> {
            this.cancelAndClose();
        }).width(200).build());

        if (!isStarted && !isFinished && !isError) {
            startStartupSequence();
        }
    }

    private void startStartupSequence() {
        isStarted = true;

        if (EnderApiClient.hasDynamicPort()) {
            isFreshLaunch = false;
            updateStatus("检测到后台服务运行中...", 0.5);
            CompletableFuture.runAsync(() -> {
                if (EnderApiClient.checkHealth().join()) {
                    updateStatus("服务状态正常，正在连接...", 1.0);
                    delay(500).thenRun(() -> MinecraftClient.getInstance().execute(this::onStartupSuccess));
                } else {
                    LOGGER.warn("后台服务无响应，将重新启动...");
                    performFullStartup();
                }
            });
            return;
        }

        performFullStartup();
    }

    private void performFullStartup() {
        isFreshLaunch = true;
        CompletableFuture.runAsync(() -> {
            try {
                updateStatus("正在检测系统环境...", 0.05);
                PlatformHelper.OS os = PlatformHelper.getOS();
                PlatformHelper.Arch arch = PlatformHelper.getArch();

                String customPathStr = FabricConfig.getExternalEnderPath();
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

                Path gameDir = MinecraftClient.getInstance().runDirectory.toPath();
                Path downloadDir = gameDir.resolve("ender");
                if (!Files.exists(downloadDir)) {
                    Files.createDirectories(downloadDir);
                }
                boolean autoUpdate = FabricConfig.isAutoUpdate();

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

        // 确保 EnderCore 环境已准备就绪（文件已下载）
        // 实际的 EasyTier 启动将在加入/创建房间时进行

        updateStatus("初始化完成", 1.0);
        
        int port = PlatformHelper.findAvailablePort();
        if (port == -1) {
            throw new RuntimeException("无法找到可用端口");
        }
        EnderApiClient.setPort(port); // 标记服务就绪

        // EnderApiClient.checkHealth().join();

        delay(500).thenRun(() -> MinecraftClient.getInstance().execute(this::onStartupSuccess));
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

    private CompletableFuture<Boolean> waitForHealth(int maxAttempts, long delayMs, String timeoutMessage) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<ScheduledFuture<?>> scheduledRef = new AtomicReference<>();
        Runnable task = () -> {
            if (isError) {
                completeAndCancel(future, scheduledRef, false);
                return;
            }
            int attempt = attempts.incrementAndGet();
            if (EnderApiClient.checkHealth().join()) {
                completeAndCancel(future, scheduledRef, true);
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

    private <T> void completeAndCancel(CompletableFuture<T> future, AtomicReference<ScheduledFuture<?>> scheduledRef, T value) {
        ScheduledFuture<?> scheduled = scheduledRef.get();
        if (scheduled != null) {
            scheduled.cancel(false);
        }
        future.complete(value);
    }

    private void onStartupSuccess() {
        keepProcessAlive = true;
        if (onStartupComplete != null) {
            onStartupComplete.run();
        } else {
            MinecraftClient.getInstance().setScreen(parent);
        }
    }

    private void updateStatus(String text, double progress) {
        this.statusText = text;
        this.progress = progress;
        this.subStatusText = "";
    }

    @Override
    public void render(@NotNull DrawContext context, int mouseX, int mouseY, float partialTick) {
        super.render(context, mouseX, mouseY, partialTick);

        int color = isError ? 0xFF5555 : 0xAAAAAA;
        int centerY = this.height / 2;

        if (isError) {
            int maxWidth = this.width - 40;
            var lines = this.textRenderer.wrapLines(Text.literal(this.statusText), maxWidth);
            int totalHeight = lines.size() * this.textRenderer.fontHeight;
            int startY = centerY - totalHeight / 2;

            for (int i = 0; i < lines.size(); i++) {
                context.drawCenteredTextWithShadow(this.textRenderer, lines.get(i), this.width / 2, startY + i * this.textRenderer.fontHeight, color);
            }
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer, this.statusText, this.width / 2, centerY - 20, color);
        }

        if (!subStatusText.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, this.subStatusText, this.width / 2, centerY, 0xFFFFFF);
        }

        if (!isError && !isFinished) {
            int barWidth = 200;
            int barHeight = 4;
            int barX = this.width / 2 - barWidth / 2;
            int barY = centerY + 20;

            context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF555555);
            context.fill(barX, barY, barX + (int) (barWidth * progress), barY + barHeight, 0xFF55FF55);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.cancelAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void cancelAndClose() {
        if (isFreshLaunch && !isFinished) {
            new Thread(ProcessLauncher::stop, "Ender-Stopper").start();
        }
        this.close();
    }

    @Override
    public void close() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!keepProcessAlive) {
            new Thread(ProcessLauncher::stop, "Ender-Stopper").start();
        }
        client.setScreen(this.parent);
    }
}



