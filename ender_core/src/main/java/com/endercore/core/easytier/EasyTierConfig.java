package com.endercore.core.easytier;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class EasyTierConfig {
    @SerializedName("network_name")
    public String networkName = "";

    @SerializedName("network_secret")
    public String networkSecret = "";

    @SerializedName("peers")
    public List<String> peers = new ArrayList<>();

    @SerializedName("listeners")
    public List<String> listeners = new ArrayList<>();

    @SerializedName("no_tun")
    public boolean noTun = true;

    @SerializedName("compression")
    public String compression = "zstd";

    @SerializedName("multi_thread")
    public boolean multiThread = true;

    @SerializedName("latency_first")
    public boolean latencyFirst = true;

    @SerializedName("enable_kcp_proxy")
    public boolean enableKcpProxy = true;

    @SerializedName("p2p_only")
    public boolean p2pOnly = true;

    @SerializedName("daemon")
    public boolean daemon = false;

    @SerializedName("tcp_whitelist")
    public String tcpWhitelist = "0";

    @SerializedName("udp_whitelist")
    public String udpWhitelist = "0";

    @SerializedName("rpc_port")
    public int rpcPort = 58252;
    
    @SerializedName("hostname")
    public String hostname = "";

    @SerializedName("log_level")
    public String logLevel = "INFO";
    
    // Default constructor
    public EasyTierConfig() {
        // Add default peers from user example
        peers.add("tcp://public.easytier.top:11010");
        peers.add("tcp://public2.easytier.cn:54321");
        peers.add("tcp://8.148.29.206:11010");
        peers.add("tcp://39.108.52.138:11010");
        // peers.add("https://etnode.zkitefly.eu.org/node2");
        
        // Add default listeners
        listeners.add("udp://0.0.0.0:0");
        listeners.add("tcp://0.0.0.0:0");
    }

    public List<String> toArgs() {
        List<String> args = new ArrayList<>();
        
        if (networkName != null && !networkName.isEmpty()) {
            args.add("--network-name");
            args.add(networkName);
        } else {
             // If networkName is empty, maybe we are just starting without joining? 
             // Or maybe we use -i? User example used --network-name
             // Wait, user example used --network-name, but my previous code used -i.
             // Let's stick to user example style or support both. 
             // EasyTier CLI usually supports -i <id> OR --network-name <name> --network-secret <secret>
             // Let's assume networkName acts as the identifier/roomID if -i is not used.
             // Actually, the example uses --network-name AND --network-secret.
             // I should probably prefer that.
        }

        if (networkSecret != null && !networkSecret.isEmpty()) {
            args.add("--network-secret");
            args.add(networkSecret);
        }

        for (String peer : peers) {
            args.add("-p");
            args.add(peer);
        }

        for (String listener : listeners) {
            args.add("-l");
            args.add(listener);
        }

        if (noTun) args.add("--no-tun");
        
        if (compression != null && !compression.isEmpty()) {
            args.add("--compression=" + compression);
        }
        
        if (multiThread) args.add("--multi-thread");
        if (latencyFirst) args.add("--latency-first");
        if (enableKcpProxy) args.add("--enable-kcp-proxy");
        if (p2pOnly) args.add("--disable-p2p=false"); // 默认允许 P2P
        
        if (daemon) {
            args.add("-d"); // Enable DHCP
        }
        
        if (tcpWhitelist != null && !tcpWhitelist.isEmpty()) {
            args.add("--tcp-whitelist=" + tcpWhitelist);
        }
        
        if (udpWhitelist != null && !udpWhitelist.isEmpty()) {
            args.add("--udp-whitelist=" + udpWhitelist);
        }
        
        if (rpcPort > 0) {
            args.add("--rpc-portal");
            args.add("127.0.0.1:" + rpcPort);
        }

        if (hostname != null && !hostname.isEmpty()) {
            args.add("--hostname");
            args.add(hostname);
        }

        return args;
    }
}
