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

public class EasyTierRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(EasyTierRunner.class);
    private final Path executablePath;
    private final AtomicReference<Process> processRef = new AtomicReference<>();
    private volatile boolean isRunning = false;
    private final Map<String, String> peerHostnames = new ConcurrentHashMap<>();
    private final Map<String, String> peerIps = new ConcurrentHashMap<>();
    private ScheduledExecutorService pollerScheduler;
    private int rpcPort = 11010; // Will be updated from args

    public EasyTierRunner(Path executablePath) {
        this.executablePath = executablePath;
    }

    public void start(String... args) throws IOException {
        start(args, null);
    }

    private final List<String> peerList = Collections.synchronizedList(new ArrayList<>());
    private final List<String> ignoredPeers = Collections.synchronizedList(new ArrayList<>());
    private final List<String> infrastructureUrls = Collections.synchronizedList(new ArrayList<>());

    public List<String> getPeers() {
        return new ArrayList<>(peerList);
    }

    public Map<String, String> getPeerHostnames() {
        return new HashMap<>(peerHostnames);
    }

    public Map<String, String> getPeerIps() {
        return new HashMap<>(peerIps);
    }

    public void start(String[] args, Map<String, String> env) throws IOException {
        if (isRunning()) {
            LOGGER.warn("EasyTier is already running.");
            return;
        }

        peerList.clear(); // Clear peers on restart
        ignoredPeers.clear();
        infrastructureUrls.clear();
        peerHostnames.clear();
        peerIps.clear();

        List<String> command = new ArrayList<>();
        command.add(executablePath.toAbsolutePath().toString());
        Collections.addAll(command, args);

        // Parse args to find initial peers to ignore (public nodes) and RPC port
        for (int i = 0; i < args.length; i++) {
            if ("-p".equals(args[i]) || "--peers".equals(args[i])) {
                if (i + 1 < args.length) {
                    infrastructureUrls.add(args[i+1]);
                }
            }
            if ("--rpc-portal".equals(args[i]) && i + 1 < args.length) {
                String val = args[i+1]; // 127.0.0.1:58252
                if (val.contains(":")) {
                    try {
                        rpcPort = Integer.parseInt(val.split(":")[1]);
                    } catch (NumberFormatException e) {
                        // ignore
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

        // Output handler
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.info("[EasyTier] {}", line);
                    
                    // Identify ignored peers from connection info
                    // Log: new peer connection added. ... dst_peer_id: 12345, ... remote_addr: Some(Url { url: "tcp://public..." })
                    if (line.contains("new peer connection added")) {
                        try {
                            String dstPeerId = null;
                            String remoteUrl = null;
                            
                            // Extract dst_peer_id
                            int idIdx = line.indexOf("dst_peer_id: ");
                            if (idIdx != -1) {
                                int end = line.indexOf(",", idIdx);
                                if (end != -1) {
                                    dstPeerId = line.substring(idIdx + 13, end).trim();
                                }
                            }
                            
                            // Extract remote_addr url
                            int urlIdx = line.indexOf("url: \"");
                            if (urlIdx != -1) {
                                int end = line.indexOf("\"", urlIdx + 6);
                                if (end != -1) {
                                    remoteUrl = line.substring(urlIdx + 6, end);
                                }
                            }
                            
                            if (dstPeerId != null && remoteUrl != null) {
                                // Check if this is a public node
                                boolean isInfrastructure = false;
                                for (String infraUrl : infrastructureUrls) {
                                    // Simple contains check, can be improved if needed
                                    // Remove protocol if needed, but contains usually works if arg was "tcp://..."
                                    // The log usually has full URL
                                    if (remoteUrl.contains(infraUrl) || infraUrl.contains(remoteUrl)) {
                                        isInfrastructure = true;
                                        break;
                                    }
                                    // Also check for known public domains just in case args were missed or different
                                    if (remoteUrl.contains("public.easytier.top") || 
                                        remoteUrl.contains("public2.easytier.cn")) {
                                        isInfrastructure = true;
                                        break;
                                    }
                                }
                                
                                if (isInfrastructure) {
                                    if (!ignoredPeers.contains(dstPeerId)) {
                                        ignoredPeers.add(dstPeerId);
                                        // Also remove from peerList if it was already added
                                        peerList.remove(dstPeerId);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Failed to parse peer connection info: {}", line);
                        }
                    }

                    // Parse peer info
                    if (line.contains("new peer added")) {
                         // Example log: [EasyTier] ... new peer added. peer_id: 1418985250
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
                        // Example log: ... remove peer ... peer_id: 12345
                         try {
                             String[] parts = line.split("peer_id:");
                             if (parts.length > 1) {
                                 String peerId = parts[1].trim();
                                 peerList.remove(peerId);
                                 ignoredPeers.remove(peerId); // Clean up ignore list
                             }
                         } catch (Exception e) {
                             LOGGER.warn("Failed to parse peer id for removal from line: {}", line);
                         }
                    }
                }
            } catch (IOException e) {
                // Ignore stream closed
            } finally {
                isRunning = false;
                LOGGER.info("EasyTier process exited.");
            }
        }, "EasyTier-Output").start();
    }

    public void stop() {
        Process process = processRef.get();
        if (process != null && process.isAlive()) {
            LOGGER.info("Stopping EasyTier...");
            stopPeerPoller();
            process.destroy(); // Try graceful first
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

    public boolean isRunning() {
        Process process = processRef.get();
        return process != null && process.isAlive();
    }

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

    private void stopPeerPoller() {
        if (pollerScheduler != null) {
            pollerScheduler.shutdownNow();
            pollerScheduler = null;
        }
    }

    private void fetchPeerInfo() {
        if (!isRunning) return;
        
        try {
            // Determine CLI path
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
            // Ignore
        }
    }
}
