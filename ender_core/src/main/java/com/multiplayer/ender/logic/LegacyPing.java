package com.multiplayer.ender.logic;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 传统服务器Ping检测工具类。
 * 用于通过发送0xFE数据包来检测服务器是否在线。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public class LegacyPing {
     
    /**
     * 检测目标服务器是否可连接。
     *
     * @param host 目标主机地址
     * @param port 目标端口号
     * @param timeout 连接超时时间（毫秒）
     * @return 如果服务器响应正常返回true，否则返回false
     */
    public static boolean check(String host, int port, int timeout) {
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(timeout);
            socket.connect(new InetSocketAddress(host, port), timeout);
            
            OutputStream out = socket.getOutputStream();
            out.write(0xFE); 
            out.flush();
            
            InputStream in = socket.getInputStream();
            int firstByte = in.read();
            
            
            return firstByte == 0xFF;
        } catch (Exception e) {
            return false;
        }
    }
}
