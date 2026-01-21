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

public class LanDiscovery {
    private static final Logger LOGGER = LoggerFactory.getLogger(LanDiscovery.class);
    private static final String GROUP_ADDRESS = "224.0.2.60";
    private static final int PORT = 4445;
    private static ScheduledExecutorService broadcasterExecutor;

    /**
     * Starts broadcasting the presence of a LAN server.
     * This makes the server visible in the Minecraft multiplayer menu (LAN World).
     *
     * @param port The port where the Minecraft server (or proxy) is listening
     * @param motd The message of the day (description) to show
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
                // Format: [MOTD]{motd}[/MOTD][AD]{port}[/AD]
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

    public static synchronized void stopBroadcaster() {
        if (broadcasterExecutor != null) {
            broadcasterExecutor.shutdownNow();
            broadcasterExecutor = null;
            LOGGER.info("Stopped LAN broadcaster");
        }
    }
}
