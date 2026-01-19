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

import com.multiplayer.terracotta.network.TerracottaApiClient;

public class ProcessLauncher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessLauncher.class);
    private static final AtomicReference<Process> currentProcess = new AtomicReference<>();
    private static volatile ProcessStatus status = ProcessStatus.STOPPED;
    private static volatile boolean stopping = false;

    public static boolean isRunning() {
        Process process = currentProcess.get();
        boolean alive = process != null && process.isAlive();
        if (!alive && status == ProcessStatus.RUNNING) {
            status = ProcessStatus.CRASHED;
        }
        return alive;
    }

    public static ProcessStatus getStatus() {
        return status;
    }

    public static boolean isCrashed() {
        return status == ProcessStatus.CRASHED;
    }

    public static void launch(Path executablePath, Path workDir, String... args) throws IOException {
        launch(executablePath, workDir, null, args);
    }

    public static void launch(Path executablePath, Path workDir, java.util.function.Consumer<String> outputHandler, String... args) throws IOException {
        stopping = false;
        status = ProcessStatus.STARTING;

        LOGGER.info("正在启动进程: {} 参数: {}", executablePath, args);
        
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
        
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        
        Process process = pb.start();
        currentProcess.set(process);
        
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
            }
        }, "Terracotta-Output").start();

        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.error("[Terracotta] " + line);
                }
            } catch (IOException e) {
            }
        }, "Terracotta-Error").start();

        new Thread(() -> {
            try {
                int exitCode = process.waitFor();
                LOGGER.info("陶瓦联机进程退出，代码: {}", exitCode);
                if (currentProcess.get() == process) {
                    currentProcess.compareAndSet(process, null);
                    if (stopping) {
                        status = ProcessStatus.STOPPED;
                    } else if (status == ProcessStatus.STARTING) {
                        status = ProcessStatus.RUNNING;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Terracotta-Watcher").start();
    }

    public static void stop() {
        stopping = true;
        TerracottaApiClient.clearDynamicPort();
        try {
            TerracottaApiClient.panic(false).join();
        } catch (Exception e) {
        }

        Process process = currentProcess.getAndSet(null);
        if (process != null && process.isAlive()) {
            LOGGER.info("正在停止陶瓦联机进程...");
            process.destroy();
        }
        status = ProcessStatus.STOPPED;
    }

    private static void killExistingProcesses() {
        try {
            List<ProcessHandle> toKill = new ArrayList<>();
            ProcessHandle.allProcesses().forEach(ph -> {
                try {
                    ph.info().command().ifPresent(cmd -> {
                        String name = new File(cmd).getName().toLowerCase();
                        if (name.startsWith("terracotta")) {
                            toKill.add(ph);
                        }
                    });
                } catch (Exception e) {
                }
            });

            if (toKill.isEmpty()) return;

            LOGGER.info("发现 {} 个残留进程，正在清理...", toKill.size());

            for (ProcessHandle ph : toKill) {
                LOGGER.info("正在终止进程 PID: {}", ph.pid());
                ph.destroy();
            }

            toKill.forEach(ph -> {
                if (ph.isAlive()) {
                    try {
                        LOGGER.info("正在终止进程 PID: {}", ph.pid());
                        ph.destroy();
                    } catch (Exception e) {
                    }
                }
            });
            LOGGER.info("残留进程终止指令已发送");
        } catch (Exception e) {
            LOGGER.warn("清理残留进程时发生错误", e);
        }
    }

    public enum ProcessStatus {
        STOPPED,
        STARTING,
        RUNNING,
        CRASHED
    }
}
