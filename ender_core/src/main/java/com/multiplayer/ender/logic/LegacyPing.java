package com.multiplayer.ender.logic;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class LegacyPing {
    /**
     * Checks if a Minecraft server is reachable using the legacy 0xFE ping.
     * 
     * @param host Host to connect to
     * @param port Port to connect to
     * @param timeout Timeout in milliseconds
     * @return true if the server responded with 0xFF
     */
    public static boolean check(String host, int port, int timeout) {
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(timeout);
            socket.connect(new InetSocketAddress(host, port), timeout);
            
            OutputStream out = socket.getOutputStream();
            out.write(0xFE); // Legacy ping packet
            out.flush();
            
            InputStream in = socket.getInputStream();
            int firstByte = in.read();
            
            // Expecting 0xFF (255) as the first byte of the Kick/Disconnect packet
            return firstByte == 0xFF;
        } catch (Exception e) {
            return false;
        }
    }
}
