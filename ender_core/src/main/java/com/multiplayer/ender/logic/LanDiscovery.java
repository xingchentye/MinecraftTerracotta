package com.multiplayer.ender.logic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 局域网发现服务类，用于广播局域网内的服务器信息。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public class LanDiscovery {
    /**
     * 日志记录器
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(LanDiscovery.class);

    /**
     * 组播地址
     */
    private static final String GROUP_ADDRESS = "224.0.2.60";

    /**
     * 组播端口号
     */
    private static final int PORT = 4445;

    /**
     * 广播任务调度执行器
     */
    private static ScheduledExecutorService broadcasterExecutor;

     
    /**
     * 启动局域网广播服务。
     *
     * @param port 本地服务器端口号
     * @param motd 服务器描述信息 (Message Of The Day)
     */
    public static synchronized void startBroadcaster(int port, String motd) {
        stopBroadcaster();
        
        broadcasterExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Ender-Lan-Broadcaster");
            t.setDaemon(true);
            return t;
        });

        broadcasterExecutor.scheduleAtFixedRate(() -> {
            try {
                
                String msg = String.format("[MOTD]%s[/MOTD][AD]%d[/AD]", motd, port);
                byte[] data = msg.getBytes(StandardCharsets.UTF_8);
                InetAddress group = InetAddress.getByName(GROUP_ADDRESS);
                
                try (DatagramSocket socket = new DatagramSocket()) {
                    DatagramPacket packet = new DatagramPacket(data, data.length, group, PORT);
                    socket.send(packet);
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to broadcast LAN packet: {}", e.getMessage());
            }
        }, 0, 1500, TimeUnit.MILLISECONDS);
        
        LOGGER.info("Started LAN broadcaster for port {} with MOTD: {}", port, motd);
    }

    /**
     * 停止局域网广播服务。
     */
    public static synchronized void stopBroadcaster() {
        if (broadcasterExecutor != null) {
            broadcasterExecutor.shutdownNow();
            broadcasterExecutor = null;
            LOGGER.info("Stopped LAN broadcaster");
        }
    }
}
