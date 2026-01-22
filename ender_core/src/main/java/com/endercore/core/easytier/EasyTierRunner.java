package com.endercore.core.easytier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.util.HashMap;

/**
 * EasyTier 进程运行器。
 * 负责启动 EasyTier 进程、监控其输出、解析对等节点信息以及管理进程生命周期。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public class EasyTierRunner {
    /**
     * 日志记录器
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(EasyTierRunner.class);
    
    /**
     * 可执行文件路径
     */
    private final Path executablePath;
    
    /**
     * 进程引用
     */
    private final AtomicReference<Process> processRef = new AtomicReference<>();
    
    /**
     * 运行状态标志
     */
    private volatile boolean isRunning = false;
    
    /**
     * 对等节点主机名映射表
     */
    private final Map<String, String> peerHostnames = new ConcurrentHashMap<>();
    
    /**
     * 对等节点IP映射表
     */
    private final Map<String, String> peerIps = new ConcurrentHashMap<>();
    
    /**
     * 轮询调度器
     */
    private ScheduledExecutorService pollerScheduler;
    
    /**
     * RPC 端口
     */
    private int rpcPort = 11010; 

    /**
     * 构造函数。
     *
     * @param executablePath EasyTier 可执行文件路径
     */
    public EasyTierRunner(Path executablePath) {
        this.executablePath = executablePath;
    }

    /**
     * 启动 EasyTier 进程。
     *
     * @param args 启动参数
     * @throws IOException 当启动失败时抛出
     */
    public void start(String... args) throws IOException {
        start(args, null);
    }

    /**
     * 活跃对等节点列表
     */
    private final List<String> peerList = Collections.synchronizedList(new ArrayList<>());
    
    /**
     * 忽略的对等节点列表（基础设施节点）
     */
    private final List<String> ignoredPeers = Collections.synchronizedList(new ArrayList<>());
    
    /**
     * 基础设施 URL 列表
     */
    private final List<String> infrastructureUrls = Collections.synchronizedList(new ArrayList<>());

    /**
     * 获取活跃对等节点列表。
     *
     * @return 对等节点 ID 列表
     */
    public List<String> getPeers() {
        return new ArrayList<>(peerList);
    }

    /**
     * 获取对等节点主机名映射。
     *
     * @return 主机名映射表副本
     */
    public Map<String, String> getPeerHostnames() {
        return new HashMap<>(peerHostnames);
    }

    /**
     * 获取对等节点 IP 映射。
     *
     * @return IP 映射表副本
     */
    public Map<String, String> getPeerIps() {
        return new HashMap<>(peerIps);
    }

    /**
     * 启动 EasyTier 进程（带环境变量）。
     *
     * @param args 启动参数
     * @param env 环境变量
     * @throws IOException 当启动失败时抛出
     */
    public void start(String[] args, Map<String, String> env) throws IOException {
        if (isRunning()) {
            LOGGER.warn("EasyTier is already running.");
            return;
        }

        peerList.clear(); 
        ignoredPeers.clear();
        infrastructureUrls.clear();
        peerHostnames.clear();
        peerIps.clear();

        List<String> command = new ArrayList<>();
        command.add(executablePath.toAbsolutePath().toString());
        Collections.addAll(command, args);

        
        for (int i = 0; i < args.length; i++) {
            if ("-p".equals(args[i]) || "--peers".equals(args[i])) {
                if (i + 1 < args.length) {
                    infrastructureUrls.add(args[i+1]);
                }
            }
            if ("--rpc-portal".equals(args[i]) && i + 1 < args.length) {
                String val = args[i+1]; 
                if (val.contains(":")) {
                    try {
                        rpcPort = Integer.parseInt(val.split(":")[1]);
                    } catch (NumberFormatException e) {
                        
                    }
                }
            }
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(executablePath.getParent().toFile());
        pb.redirectErrorStream(true);
        if (env != null) {
            pb.environment().putAll(env);
        }
        
        LOGGER.info("Starting EasyTier: {}", command);
        Process process = pb.start();

        processRef.set(process);
        isRunning = true;

        startPeerPoller();

        
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.info("[EasyTier] {}", line);
                    
                    
                    
                    if (line.contains("new peer connection added")) {
                        try {
                            String dstPeerId = null;
                            String remoteUrl = null;
                            
                            
                            int idIdx = line.indexOf("dst_peer_id: ");
                            if (idIdx != -1) {
                                int end = line.indexOf(",", idIdx);
                                if (end != -1) {
                                    dstPeerId = line.substring(idIdx + 13, end).trim();
                                }
                            }
                            
                            
                            int urlIdx = line.indexOf("url: \"");
                            if (urlIdx != -1) {
                                int end = line.indexOf("\"", urlIdx + 6);
                                if (end != -1) {
                                    remoteUrl = line.substring(urlIdx + 6, end);
                                }
                            }
                            
                            if (dstPeerId != null && remoteUrl != null) {
                                
                                boolean isInfrastructure = false;
                                for (String infraUrl : infrastructureUrls) {
                                    
                                    
                                    
                                    if (remoteUrl.contains(infraUrl) || infraUrl.contains(remoteUrl)) {
                                        isInfrastructure = true;
                                        break;
                                    }
                                    
                                    if (remoteUrl.contains("public.easytier.top") || 
                                        remoteUrl.contains("public2.easytier.cn")) {
                                        isInfrastructure = true;
                                        break;
                                    }
                                }
                                
                                if (isInfrastructure) {
                                    if (!ignoredPeers.contains(dstPeerId)) {
                                        ignoredPeers.add(dstPeerId);
                                        
                                        peerList.remove(dstPeerId);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Failed to parse peer connection info: {}", line);
                        }
                    }

                    
                    if (line.contains("new peer added")) {
                         
                         try {
                             String[] parts = line.split("peer_id:");
                             if (parts.length > 1) {
                                 String peerId = parts[1].trim();
                                 if (!ignoredPeers.contains(peerId)) {
                                     if (!peerList.contains(peerId)) {
                                         peerList.add(peerId);
                                     }
                                 }
                             }
                         } catch (Exception e) {
                             LOGGER.warn("Failed to parse peer id from line: {}", line);
                         }
                    }
                    if (line.contains("peer connection closed") || line.contains("remove peer")) {
                        
                         try {
                             String[] parts = line.split("peer_id:");
                             if (parts.length > 1) {
                                 String peerId = parts[1].trim();
                                 peerList.remove(peerId);
                                 ignoredPeers.remove(peerId); 
                             }
                         } catch (Exception e) {
                             LOGGER.warn("Failed to parse peer id for removal from line: {}", line);
                         }
                    }
                }
            } catch (IOException e) {
                
            } finally {
                isRunning = false;
                LOGGER.info("EasyTier process exited.");
            }
        }, "EasyTier-Output").start();
    }

    /**
     * 停止 EasyTier 进程。
     */
    public void stop() {
        Process process = processRef.get();
        if (process != null && process.isAlive()) {
            LOGGER.info("Stopping EasyTier...");
            stopPeerPoller();
            process.destroy(); 
            try {
                Thread.sleep(1000);
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        isRunning = false;
    }

    /**
     * 检查 EasyTier 是否正在运行。
     *
     * @return 如果正在运行则返回 true，否则返回 false
     */
    public boolean isRunning() {
        Process process = processRef.get();
        return process != null && process.isAlive();
    }

    /**
     * 启动对等节点信息轮询器。
     */
    private void startPeerPoller() {
        if (pollerScheduler != null && !pollerScheduler.isShutdown()) {
            return;
        }
        pollerScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EasyTier-Poller");
            t.setDaemon(true);
            return t;
        });
        pollerScheduler.scheduleWithFixedDelay(this::fetchPeerInfo, 2, 2, TimeUnit.SECONDS);
    }

    /**
     * 停止对等节点信息轮询器。
     */
    private void stopPeerPoller() {
        if (pollerScheduler != null) {
            pollerScheduler.shutdownNow();
            pollerScheduler = null;
        }
    }

    /**
     * 通过 EasyTier CLI 获取对等节点详细信息（主机名、IP等）。
     */
    private void fetchPeerInfo() {
        if (!isRunning) return;
        
        try {
            
            Path cliPath;
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            if (isWindows) {
                 cliPath = executablePath.resolveSibling("easytier-cli.exe");
            } else {
                 cliPath = executablePath.resolveSibling("easytier-cli");
            }

            if (!Files.exists(cliPath)) {
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(
                cliPath.toAbsolutePath().toString(), 
                "-p", "127.0.0.1:" + rpcPort, 
                "-o", "json",
                "peer"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            
            Map<String, String> latestHostnames = new HashMap<>();
            Map<String, String> latestIps = new HashMap<>();
            
            StringBuilder jsonBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBuilder.append(line);
                }
            }
            
            String jsonOutput = jsonBuilder.toString();
            if (!jsonOutput.isBlank()) {
                LOGGER.info("EasyTier CLI output: {}", jsonOutput);
                try {
                    com.google.gson.JsonArray peers = com.google.gson.JsonParser.parseString(jsonOutput).getAsJsonArray();
                    for (com.google.gson.JsonElement element : peers) {
                        if (!element.isJsonObject()) continue;
                        com.google.gson.JsonObject peer = element.getAsJsonObject();
                        
                        String id = peer.has("peer_id") ? peer.get("peer_id").getAsString() : (peer.has("id") ? peer.get("id").getAsString() : "");
                        String hostname = peer.has("hostname") ? peer.get("hostname").getAsString() : "";
                        String ipv4 = peer.has("ipv4") ? peer.get("ipv4").getAsString() : "";
                        
                        if (!id.isEmpty()) {
                            if (!hostname.isEmpty()) {
                                latestHostnames.put(id, hostname);
                            }
                            if (!ipv4.isEmpty()) {
                                latestIps.put(id, ipv4);
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to parse peer json: {}", e.getMessage());
                }
            }

            peerHostnames.clear();
            peerHostnames.putAll(latestHostnames);
            peerIps.clear();
            peerIps.putAll(latestIps);
            p.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            
        }
    }
}
