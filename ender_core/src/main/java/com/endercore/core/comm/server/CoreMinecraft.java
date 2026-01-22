package com.endercore.core.comm.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.endercore.core.comm.protocol.CoreResponse;

/**
 * Minecraft 核心工具类。
 * 提供 Minecraft 服务器状态查询功能的工具类，支持通过 WebSocket 请求查询 Minecraft 服务器状态。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public final class CoreMinecraft {
    /**
     * 私有构造函数，防止实例化。
     */
    private CoreMinecraft() {
    }

    /**
     * 在 CoreWebSocketServer 上注册 Minecraft 查询服务。
     * 注册 "mc:query_status" 请求处理器。
     *
     * @param server CoreWebSocketServer 实例
     */
    public static void install(CoreWebSocketServer server) {
        Objects.requireNonNull(server, "server");
        server.register("mc:query_status", CoreMinecraft::queryStatus);
    }

    /**
     * 处理状态查询请求。
     * 解析请求参数，查询 Minecraft 服务器状态，并返回 JSON 响应和延迟。
     *
     * @param req 核心请求对象
     * @return 核心响应对象
     * @throws Exception 当查询失败时抛出
     */
    private static CoreResponse queryStatus(CoreRequest req) throws Exception {
        QueryArgs args;
        try {
            args = parseArgs(req.payload());
        } catch (Exception e) {
            return error(req, 1, "invalid payload");
        }

        long pingId = System.nanoTime();
        String json;
        long latencyMillis;
        try {
            QueryResult r = ping(args.host, args.port, args.timeoutMillis, pingId);
            json = r.json;
            latencyMillis = r.latencyMillis;
        } catch (Exception e) {
            return error(req, 2, String.valueOf(e));
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeInt((int) Math.min(Math.max(latencyMillis, 0), Integer.MAX_VALUE));
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
        return new CoreResponse(0, req.requestId(), req.kind(), baos.toByteArray());
    }

    /**
     * 构建错误响应。
     *
     * @param req 原始请求
     * @param status 状态码
     * @param messageUtf8 错误消息
     * @return 错误响应对象
     */
    private static CoreResponse error(CoreRequest req, int status, String messageUtf8) {
        return new CoreResponse(status, req.requestId(), req.kind(), messageUtf8.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解析查询参数。
     * 支持二进制格式和字符串格式（Host:Port|Timeout）。
     *
     * @param payload 请求负载
     * @return 查询参数对象
     * @throws Exception 当参数解析失败时抛出
     */
    private static QueryArgs parseArgs(byte[] payload) throws Exception {
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("empty");
        }

        QueryArgs binary = tryParseBinary(payload);
        if (binary != null) {
            return binary;
        }

        String s = new String(payload, StandardCharsets.UTF_8).trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("empty");
        }

        long timeoutMillis = 3000;
        int split = s.lastIndexOf('|');
        if (split >= 0) {
            String left = s.substring(0, split).trim();
            String right = s.substring(split + 1).trim();
            if (!right.isEmpty()) {
                timeoutMillis = Long.parseLong(right);
            }
            s = left;
        }

        String host = s;
        int port = 25565;
        if (s.startsWith("[")) {
            int close = s.indexOf(']');
            if (close <= 0) {
                throw new IllegalArgumentException("bad ipv6");
            }
            host = s.substring(1, close);
            if (close + 1 < s.length() && s.charAt(close + 1) == ':') {
                port = Integer.parseInt(s.substring(close + 2));
            }
        } else {
            int colon = s.lastIndexOf(':');
            if (colon > 0 && colon < s.length() - 1) {
                host = s.substring(0, colon);
                port = Integer.parseInt(s.substring(colon + 1));
            }
        }

        if (host.isBlank() || port <= 0 || port > 65535 || timeoutMillis <= 0 || timeoutMillis > 120_000) {
            throw new IllegalArgumentException("bad args");
        }
        return new QueryArgs(host, port, timeoutMillis);
    }

    /**
     * 尝试解析二进制格式的查询参数。
     * 格式：HostLength(2) + HostBytes + Port(2) + Timeout(4)
     *
     * @param payload 请求负载
     * @return 查询参数对象，如果解析失败返回 null
     */
    private static QueryArgs tryParseBinary(byte[] payload) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            int hostLen = in.readUnsignedShort();
            if (hostLen <= 0 || hostLen > 1024 || payload.length < 2 + hostLen + 2 + 4) {
                return null;
            }
            byte[] hostBytes = new byte[hostLen];
            in.readFully(hostBytes);
            String host = new String(hostBytes, StandardCharsets.UTF_8).trim();
            int port = in.readUnsignedShort();
            long timeoutMillis = Integer.toUnsignedLong(in.readInt());
            if (port == 0) {
                port = 25565;
            }
            if (timeoutMillis == 0) {
                timeoutMillis = 3000;
            }
            if (host.isBlank() || port <= 0 || port > 65535 || timeoutMillis <= 0 || timeoutMillis > 120_000) {
                return null;
            }
            return new QueryArgs(host, port, timeoutMillis);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 执行 Minecraft 服务器状态 Ping 操作。
     *
     * @param host 主机名
     * @param port 端口
     * @param timeoutMillis 超时时间
     * @param pingId Ping ID
     * @return 查询结果
     * @throws Exception 当 Ping 失败时抛出
     */
    private static QueryResult ping(String host, int port, long timeoutMillis, long pingId) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), (int) timeoutMillis);
            socket.setSoTimeout((int) timeoutMillis);

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            sendHandshake(out, host, port);
            sendStatusRequest(out);

            String json = readStatusResponse(in);

            long t0 = System.nanoTime();
            sendPing(out, pingId);
            readPong(in);
            long latencyMillis = (System.nanoTime() - t0) / 1_000_000L;

            return new QueryResult(json, latencyMillis);
        }
    }

    /**
     * 发送握手包。
     *
     * @param out 输出流
     * @param host 主机名
     * @param port 端口
     * @throws Exception 当发送失败时抛出
     */
    private static void sendHandshake(OutputStream out, String host, int port) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeVarInt(body, 0);
        writeVarInt(body, 765);
        writeMcString(body, host);
        body.write((port >>> 8) & 0xFF);
        body.write(port & 0xFF);
        writeVarInt(body, 1);
        sendPacket(out, body.toByteArray());
    }

    /**
     * 发送状态请求包。
     *
     * @param out 输出流
     * @throws Exception 当发送失败时抛出
     */
    private static void sendStatusRequest(OutputStream out) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeVarInt(body, 0);
        sendPacket(out, body.toByteArray());
    }

    /**
     * 读取状态响应。
     *
     * @param in 输入流
     * @return 状态响应 JSON 字符串
     * @throws Exception 当读取失败时抛出
     */
    private static String readStatusResponse(InputStream in) throws Exception {
        int packetLen = readVarInt(in);
        byte[] packet = readFully(in, packetLen);
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(packet));
        int packetId = readVarInt(data);
        if (packetId != 0) {
            throw new IllegalStateException("unexpected packetId=" + packetId);
        }
        return readMcString(data);
    }

    /**
     * 发送 Ping 包。
     *
     * @param out 输出流
     * @param pingId Ping ID
     * @throws Exception 当发送失败时抛出
     */
    private static void sendPing(OutputStream out, long pingId) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeVarInt(body, 1);
        DataOutputStream data = new DataOutputStream(body);
        data.writeLong(pingId);
        sendPacket(out, body.toByteArray());
    }

    /**
     * 读取 Pong 包。
     *
     * @param in 输入流
     * @throws Exception 当读取失败时抛出
     */
    private static void readPong(InputStream in) throws Exception {
        int packetLen = readVarInt(in);
        byte[] packet = readFully(in, packetLen);
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(packet));
        int packetId = readVarInt(data);
        if (packetId != 1) {
            throw new IllegalStateException("unexpected pong packetId=" + packetId);
        }
        data.readLong();
    }

    /**
     * 发送数据包。
     * 格式：PacketLength(VarInt) + PacketBody
     *
     * @param out 输出流
     * @param body 数据包体
     * @throws Exception 当发送失败时抛出
     */
    private static void sendPacket(OutputStream out, byte[] body) throws Exception {
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        writeVarInt(packet, body.length);
        packet.write(body);
        out.write(packet.toByteArray());
        out.flush();
    }

    /**
     * 写入 Minecraft 字符串。
     * 格式：Length(VarInt) + StringBytes
     *
     * @param out 输出流
     * @param s 字符串
     * @throws Exception 当写入失败时抛出
     */
    private static void writeMcString(ByteArrayOutputStream out, String s) throws Exception {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    /**
     * 读取 Minecraft 字符串。
     *
     * @param in 输入流
     * @return 字符串
     * @throws Exception 当读取失败时抛出
     */
    private static String readMcString(DataInputStream in) throws Exception {
        int len = readVarInt(in);
        if (len < 0 || len > 1_048_576) {
            throw new IllegalArgumentException("string too long");
        }
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 写入 VarInt。
     *
     * @param out 输出流
     * @param value 整数值
     */
    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        int v = value;
        while ((v & 0xFFFFFF80) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v & 0x7F);
    }

    /**
     * 读取 VarInt。
     *
     * @param in 输入流
     * @return 整数值
     * @throws Exception 当读取失败时抛出
     */
    private static int readVarInt(InputStream in) throws Exception {
        int numRead = 0;
        int result = 0;
        int read;
        do {
            read = in.read();
            if (read == -1) {
                throw new EOFException();
            }
            int value = read & 0x7F;
            result |= (value << (7 * numRead));
            numRead++;
            if (numRead > 5) {
                throw new IllegalArgumentException("VarInt too big");
            }
        } while ((read & 0x80) != 0);
        return result;
    }

    /**
     * 读取 VarInt (DataInputStream)。
     *
     * @param in 输入流
     * @return 整数值
     * @throws Exception 当读取失败时抛出
     */
    private static int readVarInt(DataInputStream in) throws Exception {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = in.readByte();
            int value = read & 0x7F;
            result |= (value << (7 * numRead));
            numRead++;
            if (numRead > 5) {
                throw new IllegalArgumentException("VarInt too big");
            }
        } while ((read & 0x80) != 0);
        return result;
    }

    /**
     * 读取指定长度的字节。
     *
     * @param in 输入流
     * @param len 长度
     * @return 字节数组
     * @throws Exception 当读取失败时抛出
     */
    private static byte[] readFully(InputStream in, int len) throws Exception {
        if (len < 0) {
            throw new IllegalArgumentException("len<0");
        }
        byte[] bytes = new byte[len];
        int off = 0;
        while (off < len) {
            int r = in.read(bytes, off, len - off);
            if (r == -1) {
                throw new EOFException();
            }
            off += r;
        }
        return bytes;
    }

    /**
     * 查询参数类。
     */
    private static final class QueryArgs {
        private final String host;
        private final int port;
        private final long timeoutMillis;

        /**
         * 构造函数。
         *
         * @param host 主机名
         * @param port 端口
         * @param timeoutMillis 超时时间
         */
        private QueryArgs(String host, int port, long timeoutMillis) {
            this.host = host;
            this.port = port;
            this.timeoutMillis = timeoutMillis;
        }
    }

    /**
     * 查询结果类。
     */
    private static final class QueryResult {
        private final String json;
        private final long latencyMillis;

        /**
         * 构造函数。
         *
         * @param json 状态响应 JSON
         * @param latencyMillis 延迟（毫秒）
         */
        private QueryResult(String json, long latencyMillis) {
            this.json = json;
            this.latencyMillis = latencyMillis;
        }
    }
}
