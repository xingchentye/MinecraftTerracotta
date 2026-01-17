package com.multiplayer.terracotta.client.gui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

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

/**
 * 陶瓦联机启动界面
 * 负责平台检测、下载、解压和启动后端服务
 * 
 * @author xingchentye
 */
public class StartupScreen extends TerracottaBaseScreen {
    /** 日志记录器 */
    private static final Logger LOGGER = LoggerFactory.getLogger(StartupScreen.class);
    
    /** 当前状态文本，显示在屏幕中央 */
    private String statusText = "初始化中...";
    /** 子状态文本，显示详细进度或百分比 */
    private String subStatusText = "";
    /** 进度条进度 (0.0 - 1.0) */
    private double progress = 0.0;
    /** 是否发生错误 */
    private boolean isError = false;
    /** 是否已完成启动流程 */
    private boolean isFinished = false;
    /** 是否已开始启动流程 */
    private boolean isStarted = false;
    /** 是否保持进程存活 (退出界面时不杀进程) */
    private boolean keepProcessAlive = false;
    /** 是否是全新启动 (非复用) */
    private boolean isFreshLaunch = false;
    /** 启动完成后的回调 */
    private Runnable onStartupComplete;

    /**
     * 构造函数
     * 
     * @param parent 父界面
     */
    public StartupScreen(Screen parent) {
        this(parent, null);
    }

    /**
     * 构造函数
     * 
     * @param parent 父界面
     * @param onStartupComplete 启动完成后的回调任务
     */
    public StartupScreen(Screen parent, Runnable onStartupComplete) {
        super(Component.literal("陶瓦联机启动器"), parent);
        this.onStartupComplete = onStartupComplete;
    }

    /**
     * 设置启动完成回调
     * 
     * @param onStartupComplete 回调任务
     */
    public void setOnStartupComplete(Runnable onStartupComplete) {
        this.onStartupComplete = onStartupComplete;
    }

    /**
     * 初始化界面内容
     * 添加取消按钮并启动自动流程
     */
    @Override
    protected void initContent() {
        // 添加底部的取消按钮
        this.layout.addToFooter(Button.builder(Component.literal("取消"), (button) -> {
            this.cancelAndClose();
        }).width(200).build());

        // 如果尚未开始且无错误，启动流程
        if (!isStarted && !isFinished && !isError) {
            startStartupSequence();
        }
    }

    /**
     * 开始启动序列
     * 包括平台检测、版本检查、下载、解压和启动核心
     */
    private void startStartupSequence() {
        isStarted = true;
        
        // 尝试复用已有进程
        if (TerracottaApiClient.hasDynamicPort()) {
            isFreshLaunch = false;
            updateStatus("检测到后台服务运行中...", 0.5);
            CompletableFuture.runAsync(() -> {
                if (TerracottaApiClient.checkHealth().join()) {
                    updateStatus("服务状态正常，正在连接...", 1.0);
                    try {
                        Thread.sleep(500); // 短暂延迟以展示状态
                    } catch (InterruptedException ignored) {}
                    
                    this.minecraft.execute(this::onStartupSuccess);
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
      * 执行完整的启动流程
      */
     private void performFullStartup() {
         isFreshLaunch = true;
         CompletableFuture.runAsync(() -> {
            try {
                // 平台检测阶段
                updateStatus("正在检测系统环境...", 0.05);
                PlatformHelper.OS os = PlatformHelper.getOS();
                PlatformHelper.Arch arch = PlatformHelper.getArch();
                
                // 检查是否配置了自定义核心路径
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
                // 使用专门的 terracotta 子目录
                Path downloadDir = gameDir.resolve("terracotta");
                if (!Files.exists(downloadDir)) {
                    Files.createDirectories(downloadDir);
                }
                boolean autoUpdate = Config.AUTO_UPDATE.get();
                
                // 扫描本地是否存在可执行文件
                Path existingExe = scanForExecutable(downloadDir);
                
                // 如果存在且不强制更新，则直接启动
                if (existingExe != null && !autoUpdate) {
                    updateStatus("发现本地核心，跳过更新...", 1.0);
                    launchAndConnect(existingExe, downloadDir);
                    return;
                }
                
                // 版本检查阶段
                updateStatus("正在检查更新...", 0.1);
                String version;
                String filename;
                try {
                    version = VersionChecker.getLatestVersion();
                    filename = PlatformHelper.getDownloadFilename(version);
                } catch (Exception e) {
                    // 如果检查更新失败但本地有文件，尝试使用本地文件
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
                
                // 获取该版本对应的可执行文件名
                String exeName = PlatformHelper.getExecutableName(version);
                
                // 检查是否已存在目标版本
                existingExe = findExecutable(downloadDir, exeName);
                if (existingExe != null) {
                    updateStatus("发现最新版本核心，跳过下载...", 1.0);
                    launchAndConnect(existingExe, downloadDir);
                    return;
                }
                
                updateStatus("检测到平台: " + os + " (" + arch + ")", 0.15);
                Thread.sleep(500);

                String downloadUrl = String.format("https://gitee.com/burningtnt/Terracotta/releases/download/%s/%s", version, filename);
                LOGGER.info("下载地址: {}", downloadUrl);

                // 下载阶段
                updateStatus("正在下载组件: " + filename, 0.2);
                Path downloadedFile = DownloadManager.download(downloadUrl, downloadDir, filename, (p) -> {
                    this.progress = 0.2 + (p * 0.6);
                    this.subStatusText = String.format("%.1f%%", p * 100);
                }).join();

                // 解压阶段 (如果是压缩包)
                if (filename.endsWith(".tar.gz")) {
                    updateStatus("正在解压资源...", 0.85);
                    DownloadManager.extractTarGz(downloadedFile, downloadDir);
                }
                
                // 文件处理与启动阶段
                // 如果直接下载的是 exe，可能需要重命名
                if (filename.endsWith(".exe") && !Files.exists(downloadDir.resolve(exeName))) {
                     if (!filename.equals(exeName)) {
                         Files.move(downloadedFile, downloadDir.resolve(exeName));
                     }
                }

                existingExe = findExecutable(downloadDir, exeName);
                if (existingExe == null) {
                     // 尝试查找原始下载文件作为可执行文件
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
    
    /**
     * 扫描目录寻找符合规则的可执行文件
     * 
     * @param dir 扫描目录
     * @return 找到的文件路径，未找到返回 null
     */
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
    
    /**
     * 查找指定名称的可执行文件
     * 
     * @param dir 查找目录
     * @param exeName 目标文件名
     * @return 文件路径，未找到返回 null
     */
    private Path findExecutable(Path dir, String exeName) {
        if (!Files.exists(dir)) return null;
        try {
            // 优先检查直接路径
            Path direct = dir.resolve(exeName);
            if (Files.exists(direct)) return direct;
            
            // 递归查找 (深度3)
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

    /**
     * 启动核心进程并连接
     * 
     * @param exePath 可执行文件路径
     * @param workDir 工作目录
     * @throws Exception 启动过程中的异常
     */
    private void launchAndConnect(Path exePath, Path workDir) throws Exception {
         updateStatus("正在启动服务...", 0.95);

         String uniqueId = java.util.UUID.randomUUID().toString();
         Path portFile = workDir.resolve("port_" + uniqueId + ".json");
         
         // 清理可能存在的残留文件
         try {
             Files.deleteIfExists(portFile);
         } catch (Exception ignored) {}

         // 启动进程，传入端口文件路径参数
         ProcessLauncher.launch(exePath, workDir, (line) -> {
             // 可以在此处捕获输出日志
         }, "--hmcl", portFile.toAbsolutePath().toString());
         
         updateStatus("正在等待服务就绪...", 1.0);
         
         int maxWait = 150; // 最大等待 15 秒 (150 * 100ms)
         int port = -1;
         
         // 轮询端口文件
         for (int i = 0; i < maxWait; i++) {
             if (isError) return;
             
             if (Files.exists(portFile)) {
                 try {
                     String content = Files.readString(portFile);
                     com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
                     if (json.has("port")) {
                         port = json.get("port").getAsInt();
                         break;
                     }
                 } catch (Exception e) {
                     // 忽略读取过程中的临时错误
                 }
             }
             
             Thread.sleep(100);
         }
         
         if (port == -1) {
             throw new RuntimeException("服务启动超时，未能获取端口信息");
         }

         // 设置 API 客户端端口
         TerracottaApiClient.setPort(port);
         
         updateStatus("正在连接...", 1.0);
         boolean connected = false;
         
         // 检查服务健康状态
         for (int i = 0; i < 20; i++) {
             if (isError) return;
             
             if (TerracottaApiClient.checkHealth().join()) {
                 connected = true;
                 break;
             }
             Thread.sleep(500);
         }
         
         if (!connected) {
             throw new RuntimeException("连接超时，服务未能启动或拒绝连接");
         }

         // 清理端口文件
         try {
             Files.deleteIfExists(portFile);
         } catch (Exception e) {
             // 忽略清理错误
         }
         
         // 尝试建立 Socket 连接 (可选)
         String host = "127.0.0.1";
         try {
             NetworkClient.getInstance().connect(host, port).join();
         } catch (Exception e) {
             LOGGER.warn("Socket 连接失败，仅使用 HTTP API 控制", e);
         }
         
         // 回到主线程执行后续操作
         Minecraft.getInstance().execute(this::onStartupSuccess);
    }

    /**
     * 启动成功后的处理
     */
    private void onStartupSuccess() {
        keepProcessAlive = true; // 只要启动成功，就保持进程存活
        if (onStartupComplete != null) {
            onStartupComplete.run();
        } else {
            Minecraft.getInstance().setScreen(new TerracottaDashboard(parent));
        }
    }

    /**
     * 更新状态文本和进度
     * 
     * @param text 状态文本
     * @param progress 进度 (0.0-1.0)
     */
    private void updateStatus(String text, double progress) {
        this.statusText = text;
        this.progress = progress;
        this.subStatusText = "";
    }

    /**
     * 渲染界面
     * 
     * @param guiGraphics 图形上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param partialTick 渲染分帧时间
     */
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 绘制状态文本 (位于屏幕垂直居中位置)
        int color = isError ? 0xFF5555 : 0xAAAAAA;
        int centerY = this.height / 2;
        
        if (isError) {
            // 如果出错，处理多行文本换行
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
        
        // 绘制子状态文本
        if (!subStatusText.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, this.subStatusText, this.width / 2, centerY, 0xFFFFFF);
        }

        // 绘制进度条
        if (!isError && !isFinished) {
            int barWidth = 200;
            int barHeight = 4;
            int barX = this.width / 2 - barWidth / 2;
            int barY = centerY + 20;
            
            guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF555555); // 背景
            guiGraphics.fill(barX, barY, barX + (int)(barWidth * progress), barY + barHeight, 0xFF55FF55); // 进度
        }
    }
    
    /**
     * 处理键盘输入 (拦截 ESC)
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            this.cancelAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 用户主动取消并关闭
     */
    private void cancelAndClose() {
        // 只有当这是新启动的进程，且尚未完成时，才停止进程
        // 如果是复用进程，或者是已经完成启动但被用户取消(理论上不会)，也不停止
        if (isFreshLaunch && !isFinished) {
            ProcessLauncher.stop();
        }
        this.onClose();
    }
    
    /**
     * 关闭界面时的处理
     * 不再在此处停止进程，防止非用户意图的关闭导致误杀
     */
    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
