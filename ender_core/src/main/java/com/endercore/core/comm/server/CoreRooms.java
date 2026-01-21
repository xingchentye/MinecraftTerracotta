package com.endercore.core.comm.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.endercore.core.comm.protocol.CoreResponse;

public final class CoreRooms {
    private CoreRooms() {
    }

    public static void install(CoreWebSocketServer server) {
        Objects.requireNonNull(server, "server");
        RoomManager manager = new RoomManager(server);

        server.onConnectionClosed(manager::onDisconnect);

        server.register("room:create", manager::create);
        server.register("room:join", manager::join);
        server.register("room:leave", manager::leave);
        server.register("room:list", manager::list);
        server.register("room:info", manager::info);
        server.register("room:send", manager::send);
        server.register("room:set_meta", manager::setMeta);
        server.register("room:destroy", manager::destroy);
    }

    private static final class RoomManager {
        private static final int STATUS_INVALID_PAYLOAD = 1;
        private static final int STATUS_NOT_FOUND = 2;
        private static final int STATUS_ROOM_FULL = 3;
        private static final int STATUS_ROOM_CLOSED = 4;
        private static final int STATUS_PERMISSION_DENIED = 5;
        private static final int STATUS_NOT_IN_ROOM = 6;
        private static final int STATUS_ALREADY_EXISTS = 7;

        private static final SecureRandom RANDOM = new SecureRandom();
        private static final char[] CODE_CHARS = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
        private static final BigInteger CODE_BASE = BigInteger.valueOf(34);
        private static final BigInteger CODE_SPACE = CODE_BASE.pow(16);
        private static final BigInteger CODE_CHECK = BigInteger.valueOf(7);

        private final CoreWebSocketServer server;
        private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Set<String>> memberRooms = new ConcurrentHashMap<>();

        private RoomManager(CoreWebSocketServer server) {
            this.server = server;
        }

        private void onDisconnect(InetSocketAddress remoteAddress) {
            String memberId = connectionId(remoteAddress);
            Set<String> joined = memberRooms.remove(memberId);
            if (joined == null || joined.isEmpty()) {
                return;
            }
            for (String roomId : joined) {
                Room room = rooms.get(roomId);
                if (room == null) {
                    continue;
                }
                handleLeaveInternal(room, memberId);
            }
        }

        private CoreResponse create(CoreRequest req) throws Exception {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(req.payload()));
            String name = readString(in);
            int maxMembers = in.readUnsignedShort();
            String preferredRoomId = readString(in);
            boolean open = in.readUnsignedByte() != 0;

            if (name.isBlank() || name.length() > 64) {
                return error(req, STATUS_INVALID_PAYLOAD, "invalid room name");
            }
            if (maxMembers <= 0 || maxMembers > 256) {
                return error(req, STATUS_INVALID_PAYLOAD, "invalid maxMembers");
            }

            RoomCode code = preferredRoomId.isBlank() ? generateRoomCode() : parseRoomCode(preferredRoomId);
            if (code == null) {
                return error(req, STATUS_INVALID_PAYLOAD, "invalid roomId");
            }
            if (rooms.containsKey(code.code)) {
                return error(req, STATUS_ALREADY_EXISTS, "room already exists");
            }

            String hostId = connectionId(req.remoteAddress());
            Room room = new Room(code.code, code.networkName, code.networkSecret, name, maxMembers, open, hostId);
            room.members.put(hostId, req.remoteAddress());
            rooms.put(code.code, room);
            memberRooms.computeIfAbsent(hostId, k -> ConcurrentHashMap.newKeySet()).add(code.code);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            writeString(out, code.code);
            writeString(out, hostId);
            out.writeShort(maxMembers);
            out.writeByte(open ? 1 : 0);
            writeString(out, name);
            writeString(out, code.networkName);
            writeString(out, code.networkSecret);
            return new CoreResponse(0, req.requestId(), req.kind(), baos.toByteArray());
        }

        private CoreResponse join(CoreRequest req) throws Exception {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(req.payload()));
            String roomIdInput = readString(in);
            if (roomIdInput.isBlank()) {
                return error(req, STATUS_INVALID_PAYLOAD, "missing roomId");
            }
            RoomCode parsed = parseRoomCode(roomIdInput);
            if (parsed == null) {
                return error(req, STATUS_INVALID_PAYLOAD, "invalid roomId");
            }
            String roomId = parsed.code;
            Room room = rooms.get(roomId);
            if (room == null) {
                return error(req, STATUS_NOT_FOUND, "room not found");
            }

            String memberId = connectionId(req.remoteAddress());
            InetSocketAddress remote = req.remoteAddress();
            boolean joinedNow;
            List<InetSocketAddress> remotes;
            synchronized (room.lock) {
                if (!room.open) {
                    return error(req, STATUS_ROOM_CLOSED, "room is closed");
                }
                if (room.members.size() >= room.maxMembers && !room.members.containsKey(memberId)) {
                    return error(req, STATUS_ROOM_FULL, "room is full");
                }
                joinedNow = room.members.putIfAbsent(memberId, remote) == null;
                if (joinedNow) {
                    memberRooms.computeIfAbsent(memberId, k -> ConcurrentHashMap.newKeySet()).add(roomId);
                }
                remotes = new ArrayList<>(room.members.values());
            }

            if (joinedNow) {
                server.sendEventToMany(remotes, "room:member_joined", payloadRoomMember(roomId, memberId));
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            writeString(out, room.id);
            writeString(out, memberId);
            writeString(out, room.hostId);
            writeString(out, room.name);
            out.writeShort(room.maxMembers);
            out.writeByte(room.open ? 1 : 0);
            writeMembers(out, room.members.keySet());
            writeString(out, room.networkName);
            writeString(out, room.networkSecret);
            return new CoreResponse(0, req.requestId(), req.kind(), baos.toByteArray());
        }

        private CoreResponse leave(CoreRequest req) throws Exception {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(req.payload()));
            String roomIdInput = readString(in);
            if (roomIdInput.isBlank()) {
                return error(req, STATUS_INVALID_PAYLOAD, "missing roomId");
            }
            RoomCode parsed = parseRoomCode(roomIdInput);
            if (parsed == null) {
                return error(req, STATUS_INVALID_PAYLOAD, "invalid roomId");
            }
            String roomId = parsed.code;
            Room room = rooms.get(roomId);
            if (room == null) {
                return error(req, STATUS_NOT_FOUND, "room not found");
            }
            String memberId = connectionId(req.remoteAddress());
            boolean left = handleLeaveInternal(room, memberId);
            if (!left) {
                return error(req, STATUS_NOT_IN_ROOM, "not in room");
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            writeString(out, roomId);
            return new CoreResponse(0, req.requestId(), req.kind(), baos.toByteArray());
        }

        private boolean handleLeaveInternal(Room room, String memberId) {
            boolean removed;
            List<InetSocketAddress> remainingRemotes = List.of();
            boolean destroyed = false;

            synchronized (room.lock) {
                removed = room.members.remove(memberId) != null;
                if (!removed) {
                    return false;
                }
                Set<String> joined = memberRooms.get(memberId);
                if (joined != null) {
                    joined.remove(room.id);
                    if (joined.isEmpty()) {
                        memberRooms.remove(memberId, joined);
                    }
                }
                if (room.members.isEmpty()) {
                    rooms.remove(room.id, room);
                    destroyed = true;
                } else if (Objects.equals(room.hostId, memberId)) {
                    destroyed = true;
                    rooms.remove(room.id, room);
                    remainingRemotes = new ArrayList<>(room.members.values());
                    for (String otherMemberId : room.members.keySet()) {
                        Set<String> otherJoined = memberRooms.get(otherMemberId);
                        if (otherJoined != null) {
                            otherJoined.remove(room.id);
                            if (otherJoined.isEmpty()) {
                                memberRooms.remove(otherMemberId, otherJoined);
                            }
                        }
                    }
                    room.members.clear();
                } else {
                    remainingRemotes = new ArrayList<>(room.members.values());
                }
            }

            if (removed && !destroyed) {
                server.sendEventToMany(remainingRemotes, "room:member_left", payloadRoomMember(room.id, memberId));
            }
            if (destroyed) {
                server.sendEventToMany(remainingRemotes, "room:destroyed", payloadRoom(room.id));
            }
            return true;
        }

        private CoreResponse list(CoreRequest req) throws Exception {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(req.payload()));
            int offset = in.readUnsignedShort();
            int limit = in.readUnsignedShort();
            if (limit <= 0) {
                limit = 50;
            }
            if (limit > 200) {
                limit = 200;
            }

            List<Room> all = new ArrayList<>(rooms.values());
            all.sort(Comparator.comparing(r -> r.id));

            int from = Math.min(offset, all.size());
            int to = Math.min(from + limit, all.size());
            List<Room> page = all.subList(from, to);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeShort(page.size());
            for (Room room : page) {
                writeString(out, room.id);
                writeString(out, room.name);
                writeString(out, room.hostId);
                out.writeShort(room.members.size());
                out.writeShort(room.maxMembers);
                out.writeByte(room.open ? 1 : 0);
            }
            return new CoreResponse(0, req.requestId(), req.kind(), baos.toByteArray());
        }

        private CoreResponse info(CoreRequest req) throws Exception {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(req.payload()));
            String roomIdInput = readString(in);
            if (roomIdInput.isBlank()) {
                return error(req, STATUS_INVALID_PAYLOAD, "missing roomId");
            }
            RoomCode parsed = parseRoomCode(roomIdInput);
            if (parsed == null) {
                return error(req, STATUS_INVALID_PAYLOAD, "invalid roomId");
            }
            String roomId = parsed.code;
            Room room = rooms.get(roomId);
            if (room == null) {
                return error(req, STATUS_NOT_FOUND, "room not found");
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            writeString(out, room.id);
            writeString(out, room.name);
            writeString(out, room.hostId);
            out.writeLong(room.createdAtMillis);
            out.writeShort(room.maxMembers);
            out.writeByte(room.open ? 1 : 0);
            writeMembers(out, room.members.keySet());
            byte[] meta = room.meta;
            out.writeInt(meta == null ? 0 : meta.length);
            if (meta != null && meta.length > 0) {
                out.write(meta);
            }
            writeString(out, room.networkName);
            writeString(out, room.networkSecret);
            return new CoreResponse(0, req.requestId(), req.kind(), baos.toByteArray());
        }

        private CoreResponse send(CoreRequest req) throws Exception {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(req.payload()));
            String roomIdInput = readString(in);
            String channel = readString(in);
            int messageLen = in.readInt();
            if (roomIdInput.isBlank() || channel.isBlank() || messageLen < 0) {
                return error(req, STATUS_INVALID_PAYLOAD, "invalid payload");
            }
            RoomCode parsed = parseRoomCode(roomIdInput);
            if (parsed == null) {
                return error(req, STATUS_INVALID_PAYLOAD, "invalid roomId");
            }
            String roomId = parsed.code;
            byte[] message = new byte[messageLen];
            in.readFully(message);

            Room room = rooms.get(roomId);
            if (room == null) {
                return error(req, STATUS_NOT_FOUND, "room not found");
            }

            String fromId = connectionId(req.remoteAddress());
            List<InetSocketAddress> remotes;
            synchronized (room.lock) {
                if (!room.members.containsKey(fromId)) {
                    return error(req, STATUS_NOT_IN_ROOM, "not in room");
                }
                remotes = new ArrayList<>(room.members.values());
            }

            server.sendEventToMany(remotes, "room:message", payloadRoomMessage(roomId, fromId, channel, message));
            return new CoreResponse(0, req.requestId(), req.kind(), new byte[0]);
        }

        private CoreResponse setMeta(CoreRequest req) throws Exception {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(req.payload()));
            String roomIdInput = readString(in);
            int len = in.readInt();
            if (roomIdInput.isBlank() || len < 0) {
                return error(req, STATUS_INVALID_PAYLOAD, "invalid payload");
            }
            RoomCode parsed = parseRoomCode(roomIdInput);
            if (parsed == null) {
                return error(req, STATUS_INVALID_PAYLOAD, "invalid roomId");
            }
            String roomId = parsed.code;
            byte[] meta = new byte[len];
            in.readFully(meta);

            Room room = rooms.get(roomId);
            if (room == null) {
                return error(req, STATUS_NOT_FOUND, "room not found");
            }

            String callerId = connectionId(req.remoteAddress());
            List<InetSocketAddress> remotes;
            synchronized (room.lock) {
                if (!Objects.equals(room.hostId, callerId)) {
                    return error(req, STATUS_PERMISSION_DENIED, "permission denied");
                }
                room.meta = meta;
                remotes = new ArrayList<>(room.members.values());
            }
            server.sendEventToMany(remotes, "room:meta_changed", payloadRoomMeta(roomId, meta));
            return new CoreResponse(0, req.requestId(), req.kind(), new byte[0]);
        }

        private CoreResponse destroy(CoreRequest req) throws Exception {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(req.payload()));
            String roomIdInput = readString(in);
            if (roomIdInput.isBlank()) {
                return error(req, STATUS_INVALID_PAYLOAD, "missing roomId");
            }
            RoomCode parsed = parseRoomCode(roomIdInput);
            if (parsed == null) {
                return error(req, STATUS_INVALID_PAYLOAD, "invalid roomId");
            }
            String roomId = parsed.code;

            Room room = rooms.get(roomId);
            if (room == null) {
                return error(req, STATUS_NOT_FOUND, "room not found");
            }

            String callerId = connectionId(req.remoteAddress());
            Collection<InetSocketAddress> remotes;
            synchronized (room.lock) {
                if (!Objects.equals(room.hostId, callerId)) {
                    return error(req, STATUS_PERMISSION_DENIED, "permission denied");
                }
                rooms.remove(roomId, room);
                remotes = new ArrayList<>(room.members.values());
                for (String memberId : room.members.keySet()) {
                    Set<String> joined = memberRooms.get(memberId);
                    if (joined != null) {
                        joined.remove(roomId);
                        if (joined.isEmpty()) {
                            memberRooms.remove(memberId, joined);
                        }
                    }
                }
                room.members.clear();
            }
            server.sendEventToMany(remotes, "room:destroyed", payloadRoom(roomId));
            return new CoreResponse(0, req.requestId(), req.kind(), new byte[0]);
        }

        private static CoreResponse error(CoreRequest req, int status, String messageUtf8) {
            return new CoreResponse(status, req.requestId(), req.kind(), messageUtf8.getBytes(StandardCharsets.UTF_8));
        }

        private static String connectionId(InetSocketAddress remoteAddress) {
            if (remoteAddress == null) {
                return "unknown:0";
            }
            String host = remoteAddress.getAddress() != null ? remoteAddress.getAddress().getHostAddress() : remoteAddress.getHostString();
            return host + ":" + remoteAddress.getPort();
        }

        private static RoomCode generateRoomCode() {
            BigInteger value = new BigInteger(128, RANDOM).mod(CODE_SPACE);
            value = value.subtract(value.mod(CODE_CHECK));
            int[] digits = new int[16];
            BigInteger v = value;
            for (int i = 0; i < 16; i++) {
                BigInteger[] divRem = v.divideAndRemainder(CODE_BASE);
                digits[i] = divRem[1].intValue();
                v = divRem[0];
            }
            return fromDigits(digits);
        }

        private static RoomCode parseRoomCode(String input) {
            if (input == null) {
                return null;
            }
            String code = input.toUpperCase();
            int wantLen = "U/XXXX-XXXX-XXXX-XXXX".length();
            if (code.length() < wantLen) {
                return null;
            }
            for (int start = 0; start <= code.length() - wantLen; start++) {
                if (code.charAt(start) != 'U' || code.charAt(start + 1) != '/') {
                    continue;
                }
                int[] digits = new int[16];
                int di = 0;
                boolean ok = true;
                for (int i = 2; i < wantLen; i++) {
                    char c = code.charAt(start + i);
                    if (i == 6 || i == 11 || i == 16) {
                        if (c != '-') {
                            ok = false;
                            break;
                        }
                        continue;
                    }
                    int v = lookupDigit(c);
                    if (v < 0) {
                        ok = false;
                        break;
                    }
                    digits[di++] = v;
                }
                if (!ok || di != 16) {
                    continue;
                }
                int rem = 0;
                for (int i = 15; i >= 0; i--) {
                    rem = (rem * 34 + digits[i]) % 7;
                }
                if (rem != 0) {
                    continue;
                }
                return fromDigits(digits);
            }
            return null;
        }

        private static int lookupDigit(char c) {
            char up = Character.toUpperCase(c);
            if (up == 'I') {
                up = '1';
            } else if (up == 'O') {
                up = '0';
            }
            for (int i = 0; i < CODE_CHARS.length; i++) {
                if (CODE_CHARS[i] == up) {
                    return i;
                }
            }
            return -1;
        }

        private static RoomCode fromDigits(int[] digits) {
            StringBuilder code = new StringBuilder("U/XXXX-XXXX-XXXX-XXXX".length());
            code.append("U/");
            StringBuilder networkName = new StringBuilder("scaffolding-mc-XXXX-XXXX".length());
            networkName.append("scaffolding-mc-");
            StringBuilder networkSecret = new StringBuilder("XXXX-XXXX".length());

            for (int i = 0; i < 16; i++) {
                char ch = CODE_CHARS[digits[i]];
                if (i == 4 || i == 8 || i == 12) {
                    code.append('-');
                }
                code.append(ch);
                if (i < 8) {
                    if (i == 4) {
                        networkName.append('-');
                    }
                    networkName.append(ch);
                } else {
                    if (i == 12) {
                        networkSecret.append('-');
                    }
                    networkSecret.append(ch);
                }
            }
            return new RoomCode(code.toString(), networkName.toString(), networkSecret.toString());
        }

        private static final class RoomCode {
            private final String code;
            private final String networkName;
            private final String networkSecret;

            private RoomCode(String code, String networkName, String networkSecret) {
                this.code = code;
                this.networkName = networkName;
                this.networkSecret = networkSecret;
            }
        }

        private static String readString(DataInputStream in) throws Exception {
            int len = in.readUnsignedShort();
            if (len == 0) {
                return "";
            }
            byte[] bytes = new byte[len];
            in.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        private static void writeString(DataOutputStream out, String s) throws Exception {
            if (s == null || s.isEmpty()) {
                out.writeShort(0);
                return;
            }
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 65535) {
                throw new IllegalArgumentException("string too long");
            }
            out.writeShort(bytes.length);
            out.write(bytes);
        }

        private static void writeMembers(DataOutputStream out, Set<String> memberIds) throws Exception {
            out.writeShort(memberIds.size());
            for (String id : memberIds) {
                writeString(out, id);
            }
        }

        private static byte[] payloadRoom(String roomId) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(baos);
                writeString(out, roomId);
                return baos.toByteArray();
            } catch (Exception e) {
                return new byte[0];
            }
        }

        private static byte[] payloadRoomMember(String roomId, String memberId) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(baos);
                writeString(out, roomId);
                writeString(out, memberId);
                return baos.toByteArray();
            } catch (Exception e) {
                return new byte[0];
            }
        }

        private static byte[] payloadRoomMeta(String roomId, byte[] meta) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(baos);
                writeString(out, roomId);
                out.writeInt(meta == null ? 0 : meta.length);
                if (meta != null && meta.length > 0) {
                    out.write(meta);
                }
                return baos.toByteArray();
            } catch (Exception e) {
                return new byte[0];
            }
        }

        private static byte[] payloadRoomMessage(String roomId, String fromId, String channel, byte[] message) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(baos);
                writeString(out, roomId);
                writeString(out, fromId);
                writeString(out, channel);
                out.writeInt(message == null ? 0 : message.length);
                if (message != null && message.length > 0) {
                    out.write(message);
                }
                return baos.toByteArray();
            } catch (Exception e) {
                return new byte[0];
            }
        }
    }

    private static final class Room {
        private final String id;
        private final String networkName;
        private final String networkSecret;
        private final Object lock = new Object();
        private final ConcurrentHashMap<String, InetSocketAddress> members = new ConcurrentHashMap<>();
        private final long createdAtMillis = System.currentTimeMillis();
        private final int maxMembers;
        private volatile String name;
        private volatile boolean open;
        private volatile String hostId;
        private volatile byte[] meta;

        private Room(String id, String networkName, String networkSecret, String name, int maxMembers, boolean open, String hostId) {
            this.id = id;
            this.networkName = networkName;
            this.networkSecret = networkSecret;
            this.name = name;
            this.maxMembers = maxMembers;
            this.open = open;
            this.hostId = hostId;
        }
    }
}
