package com.endercore.core.easytier;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * EasyTier 配置类。
 * 用于定义和生成 EasyTier 进程的启动参数。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public class EasyTierConfig {
    /**
     * 网络名称（ID）。
     */
    @SerializedName("network_name")
    public String networkName = "";

    /**
     * 网络密钥。
     */
    @SerializedName("network_secret")
    public String networkSecret = "";

    /**
     * 对等节点列表（Peers）。
     */
    @SerializedName("peers")
    public List<String> peers = new ArrayList<>();

    /**
     * 监听地址列表。
     */
    @SerializedName("listeners")
    public List<String> listeners = new ArrayList<>();

    /**
     * 是否禁用 TUN 设备（启用无 TUN 模式）。
     */
    @SerializedName("no_tun")
    public boolean noTun = true;

    /**
     * 压缩算法（默认为 zstd）。
     */
    @SerializedName("compression")
    public String compression = "zstd";

    /**
     * 是否启用多线程。
     */
    @SerializedName("multi_thread")
    public boolean multiThread = true;

    /**
     * 是否优先降低延迟。
     */
    @SerializedName("latency_first")
    public boolean latencyFirst = true;

    /**
     * 是否启用 KCP 代理。
     */
    @SerializedName("enable_kcp_proxy")
    public boolean enableKcpProxy = true;

    /**
     * 是否仅使用 P2P 连接（禁用服务器中转）。
     */
    @SerializedName("p2p_only")
    public boolean p2pOnly = true;

    /**
     * 是否以守护进程方式运行。
     */
    @SerializedName("daemon")
    public boolean daemon = false;

    /**
     * TCP 白名单端口。
     */
    @SerializedName("tcp_whitelist")
    public String tcpWhitelist = "0";

    /**
     * UDP 白名单端口。
     */
    @SerializedName("udp_whitelist")
    public String udpWhitelist = "0";

    /**
     * RPC 端口号。
     */
    @SerializedName("rpc_port")
    public int rpcPort = 58252;
    
    /**
     * 主机名。
     */
    @SerializedName("hostname")
    public String hostname = "";

    /**
     * 日志级别。
     */
    @SerializedName("log_level")
    public String logLevel = "INFO";
    
    
    /**
     * 构造函数，初始化默认的 Peers 和 Listeners。
     */
    public EasyTierConfig() {
        
        peers.add("tcp://public.easytier.top:11010");
        peers.add("tcp://public2.easytier.cn:54321");
        peers.add("tcp://8.148.29.206:11010");
        peers.add("tcp://39.108.52.138:11010");
        
        
        
        listeners.add("udp://0.0.0.0:0");
        listeners.add("tcp://0.0.0.0:0");
    }

    /**
     * 将配置转换为命令行参数列表。
     *
     * @return 命令行参数字符串列表
     */
    public List<String> toArgs() {
        List<String> args = new ArrayList<>();
        
        if (networkName != null && !networkName.isEmpty()) {
            args.add("--network-name");
            args.add(networkName);
        } else {
             
             
             
             
             
             
             
             
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
        if (p2pOnly) args.add("--disable-p2p=false"); 
        
        if (daemon) {
            args.add("-d"); 
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
