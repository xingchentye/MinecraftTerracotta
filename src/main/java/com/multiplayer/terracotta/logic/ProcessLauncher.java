package com.multiplayer.terracotta.logic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 外部进程管理器
 * 负责启动和管理陶瓦联机进程
 *
 * @author xingchentye
 */
public class ProcessLauncher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessLauncher.class);
    private static final AtomicReference<Process> currentProcess = new AtomicReference<>();

    /**
     * 检查进程是否正在运行
     *
     * @return 如果进程存活则返回 true，否则返回 false
     */
    public static boolean isRunning() {
        Process process = currentProcess.get();
        return process != null && process.isAlive();
    }

    /**
     * 启动进程
     *
     * @param executablePath 可执行文件的路径
     * @param workDir 工作目录的路径
     * @param args 启动参数列表
     * @throws IOException 如果启动过程中发生 I/O 错误
     */
    public static void launch(Path executablePath, Path workDir, String... args) throws IOException {
        launch(executablePath, workDir, null, args);
    }

    /**
     * 启动进程
     *
     * @param executablePath 可执行文件的路径
     * @param workDir 工作目录的路径
     * @param outputHandler 进程输出行的回调处理函数 (可以为 null)
     * @param args 启动参数列表
     * @throws IOException 如果启动过程中发生 I/O 错误
     */
    public static void launch(Path executablePath, Path workDir, java.util.function.Consumer<String> outputHandler, String... args) throws IOException {
        stop(); // 停止已有进程
        killExistingProcesses(); // 清理系统中所有残留的陶瓦进程

        LOGGER.info("正在启动进程: {} 参数: {}", executablePath, args);
        
        // 赋予执行权限 (Linux/Mac/Android)
        File exeFile = executablePath.toFile();
        if (!exeFile.canExecute()) {
            exeFile.setExecutable(true);
        }

        List<String> command = new ArrayList<>();
        command.add(executablePath.toString());
        if (args != null) {
            Collections.addAll(command, args);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        
        // 将输出重定向到 PIPE 以便读取和记录日志，而不是 INHERIT
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        
        Process process = pb.start();
        currentProcess.set(process);
        
        // 启动线程读取输出流
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.info("[Terracotta] " + line);
                    if (outputHandler != null) {
                        try {
                            outputHandler.accept(line);
                        } catch (Exception e) {
                            LOGGER.error("处理输出回调时发生错误", e);
                        }
                    }
                }
            } catch (IOException e) {
                // 忽略流关闭错误
            }
        }, "Terracotta-Output").start();

        // 启动线程读取错误流
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.error("[Terracotta] " + line);
                }
            } catch (IOException e) {
                // 忽略流关闭错误
            }
        }, "Terracotta-Error").start();
    }

    /**
     * 停止当前进程
     */
    public static void stop() {
        // 尝试通过 API 优雅关闭
        try {
            com.multiplayer.terracotta.network.TerracottaApiClient.panic(false).join();
        } catch (Exception e) {
            // 忽略 API 调用失败 (可能进程已经不在了)
        }

        Process process = currentProcess.getAndSet(null);
        if (process != null && process.isAlive()) {
            LOGGER.info("正在停止陶瓦联机进程...");
            process.destroy();
            try {
                // 给一点时间让它优雅退出
                Thread.sleep(1000);
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * 终止所有系统中运行的陶瓦联机进程 (防止残留)
     * 此方法会阻塞直到进程终止或超时
     */
    private static void killExistingProcesses() {
        try {
            List<ProcessHandle> toKill = new ArrayList<>();
            ProcessHandle.allProcesses().forEach(ph -> {
                try {
                    ph.info().command().ifPresent(cmd -> {
                        String name = new File(cmd).getName().toLowerCase();
                        // 匹配 terracotta 或 terracotta-xxx.exe
                        if (name.startsWith("terracotta")) {
                            toKill.add(ph);
                        }
                    });
                } catch (Exception e) {
                    // 忽略无权限访问的进程
                }
            });

            if (toKill.isEmpty()) return;

            LOGGER.info("发现 {} 个残留进程，正在清理...", toKill.size());

            for (ProcessHandle ph : toKill) {
                LOGGER.info("正在终止进程 PID: {}", ph.pid());
                ph.destroy();
            }

            // 等待进程退出 (最多等待 3 秒)
            long start = System.currentTimeMillis();
            boolean allDead = false;
            while (System.currentTimeMillis() - start < 3000) {
                allDead = toKill.stream().allMatch(ph -> !ph.isAlive());
                if (allDead) break;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }

            if (!allDead) {
                LOGGER.warn("部分进程未能正常退出，尝试强制终止...");
                toKill.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
            }
            
            LOGGER.info("残留进程清理完成");

        } catch (Exception e) {
            LOGGER.warn("清理残留进程时发生错误", e);
        }
    }
}
