package com.multiplayer.terracotta.client.gui;

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

import com.multiplayer.terracotta.Config;
import com.multiplayer.terracotta.logic.DownloadManager;
import com.multiplayer.terracotta.logic.PlatformHelper;
import com.multiplayer.terracotta.logic.ProcessLauncher;
import com.multiplayer.terracotta.logic.VersionChecker;
import com.multiplayer.terracotta.network.NetworkClient;
import com.multiplayer.terracotta.network.TerracottaApiClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class StartupScreen extends TerracottaBaseScreen {
    private static final Logger LOGGER = LoggerFactory.getLogger(StartupScreen.class);
    private static final ScheduledExecutorService STARTUP_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "Terracotta-Startup");
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
        super(Component.literal("陶瓦联机启动器"), parent);
        this.onStartupComplete = onStartupComplete;
    }

    public void setOnStartupComplete(Runnable onStartupComplete) {
        this.onStartupComplete = onStartupComplete;
    }

    @Override
    protected void initContent() {
        this.layout.addToFooter(Button.builder(Component.literal("取消"), (button) -> {
            this.cancelAndClose();
        }).width(200).build());

        if (!isStarted && !isFinished && !isError) {
            startStartupSequence();
        }
    }

    private void startStartupSequence() {
        isStarted = true;
        
        if (TerracottaApiClient.hasDynamicPort()) {
            isFreshLaunch = false;
            updateStatus("检测到后台服务运行中...", 0.5);
            CompletableFuture.runAsync(() -> {
                if (TerracottaApiClient.checkHealth().join()) {
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

    private void performFullStartup() {
        isFreshLaunch = true;
        CompletableFuture.runAsync(() -> {
           try {
               updateStatus("正在检测系统环境...", 0.05);
               PlatformHelper.OS os = PlatformHelper.getOS();
               PlatformHelper.Arch arch = PlatformHelper.getArch();
               
               String customPathStr = Config.EXTERNAL_TERRACOTTA_PATH.get();
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
               Path downloadDir = gameDir.resolve("terracotta");
               if (!Files.exists(downloadDir)) {
                   Files.createDirectories(downloadDir);
               }
               boolean autoUpdate = Config.AUTO_UPDATE.get();
               
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

               String downloadUrl = String.format("https://gitee.com/burningtnt/Terracotta/releases/download/%s/%s", version, filename);
               LOGGER.info("下载地址: {}", downloadUrl);

               updateStatus("正在下载组件: " + filename, 0.2);
               Path downloadedFile = DownloadManager.download(downloadUrl, downloadDir, filename, (p) -> {
                   this.progress = 0.2 + (p * 0.6);
                   this.subStatusText = String.format("%.1f%%", p * 100);
               }).join();

               if (filename.endsWith(".tar.gz")) {
                   updateStatus("正在解压资源...", 0.85);
                   DownloadManager.extractTarGz(downloadedFile, downloadDir);
               }
               
               if (filename.endsWith(".exe") && !Files.exists(downloadDir.resolve(exeName))) {
                    if (!filename.equals(exeName)) {
                        Files.move(downloadedFile, downloadDir.resolve(exeName));
                    }
               }

               existingExe = findExecutable(downloadDir, exeName);
               if (existingExe == null) {
                    existingExe = findExecutable(downloadDir, filename);
                    if (existingExe == null) {
                        throw new RuntimeException("找不到可执行文件: " + exeName + " 或 " + filename);
                    }
               }
               
               launchAndConnect(existingExe, downloadDir);
               
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
                .filter(p -> {
                    String name = p.getFileName().toString();
                    if (PlatformHelper.getOS() == PlatformHelper.OS.WINDOWS) {
                         return name.startsWith("terracotta-") && name.endsWith(".exe");
                    }
                    return name.equals("terracotta");
                })
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            LOGGER.warn("扫描可执行文件失败", e);
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
         updateStatus("正在启动服务...", 0.95);

         String uniqueId = java.util.UUID.randomUUID().toString();
         Path portFile = workDir.resolve("port_" + uniqueId + ".json");
         
         try {
             Files.deleteIfExists(portFile);
         } catch (Exception ignored) {}

         ProcessLauncher.launch(exePath, workDir, (line) -> {
         }, "--hmcl", portFile.toAbsolutePath().toString());
         
         updateStatus("正在等待服务就绪...", 1.0);
         int port;
         try {
             port = waitForPort(portFile, 150, 100, "服务启动超时，未能获取端口信息").get();
         } catch (InterruptedException e) {
             Thread.currentThread().interrupt();
             throw e;
         }
         if (isError || port == -1) {
             return;
         }

         TerracottaApiClient.setPort(port);
         
         updateStatus("正在连接...", 1.0);
         try {
             boolean connected = waitForHealth(20, 500, "连接超时，服务未能启动或拒绝连接").get();
             if (!connected || isError) {
                 return;
             }
         } catch (InterruptedException e) {
             Thread.currentThread().interrupt();
             throw e;
         }

         try {
             Files.deleteIfExists(portFile);
         } catch (Exception e) {
         }
         
         String host = "127.0.0.1";
         try {
             NetworkClient.getInstance().connect(host, port).join();
         } catch (Exception e) {
             LOGGER.warn("Socket 连接失败，仅使用 HTTP API 控制", e);
         }
         
         Minecraft.getInstance().execute(this::onStartupSuccess);
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
            if (TerracottaApiClient.checkHealth().join()) {
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
            Minecraft.getInstance().setScreen(new TerracottaDashboard(parent));
        }
    }

    private void updateStatus(String text, double progress) {
        this.statusText = text;
        this.progress = progress;
        this.subStatusText = "";
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int color = isError ? 0xFF5555 : 0xAAAAAA;
        int centerY = this.height / 2;
        
        if (isError) {
            int maxWidth = this.width - 40;
            var lines = this.font.split(Component.literal(this.statusText), maxWidth);
            int totalHeight = lines.size() * this.font.lineHeight;
            int startY = centerY - totalHeight / 2;
            
            for (int i = 0; i < lines.size(); i++) {
                guiGraphics.drawCenteredString(this.font, lines.get(i), this.width / 2, startY + i * this.font.lineHeight, color);
            }
        } else {
            guiGraphics.drawCenteredString(this.font, this.statusText, this.width / 2, centerY - 20, color);
        }
        
        if (!subStatusText.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, this.subStatusText, this.width / 2, centerY, 0xFFFFFF);
        }

        if (!isError && !isFinished) {
            int barWidth = 200;
            int barHeight = 4;
            int barX = this.width / 2 - barWidth / 2;
            int barY = centerY + 20;
            
            guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF555555);
            guiGraphics.fill(barX, barY, barX + (int)(barWidth * progress), barY + barHeight, 0xFF55FF55);
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
            new Thread(ProcessLauncher::stop, "Terracotta-Stopper").start();
        }
        this.onClose();
    }
    
    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
