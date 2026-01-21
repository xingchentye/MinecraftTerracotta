package com.multiplayer.ender.fabric;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.multiplayer.ender.client.gui.StartupScreen;
import com.multiplayer.ender.fabric.FabricConfig;
import com.multiplayer.ender.network.EnderApiClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class MinecraftEnderClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ender_online-fabric");
    private static final Gson GSON = new Gson();

    private static boolean hasAutoStarted = false;
    private static int tickCounter = 0;
    private static boolean wasHostOk = false;
    private static String lastRoomCode = "";
    private static String lastStateValue = "";
    private static Set<String> lastMemberNames = new HashSet<>();

    private static class SimpleToast implements Toast {
        private final Text title;
        private final Text message;
        private final int width;
        private long firstDrawTime;

        private SimpleToast(Text title, Text message) {
            this.title = title;
            this.message = message;
            this.width = calculateToastWidth(title, message);
        }

        @Override
        public Visibility draw(DrawContext context, ToastManager manager, long time) {
            if (firstDrawTime == 0L) {
                firstDrawTime = time;
            }

            int width = getWidth();
            int height = getHeight();

            context.fill(0, 0, width, height, 0xCC000000);

            var textRenderer = manager.getClient().textRenderer;
            context.drawText(textRenderer, title, 8, 8, 0xFFFFFF, false);
            if (message != null) {
                context.drawText(textRenderer, message, 8, 20, 0xFFFFFF, false);
            }

            return time - firstDrawTime >= 2000L ? Visibility.HIDE : Visibility.SHOW;
        }

        @Override
        public int getWidth() {
            return width;
        }

        @Override
        public int getHeight() {
            return 32;
        }
    }

    public static void showToast(Text title, Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.getToastManager().add(new SimpleToast(title, message));
    }

    private static int calculateToastWidth(Text title, Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) {
            return 220;
        }
        int maxTextWidth = client.textRenderer.getWidth(title);
        if (message != null) {
            maxTextWidth = Math.max(maxTextWidth, client.textRenderer.getWidth(message));
        }
        int padding = 16;
        int rawWidth = maxTextWidth + padding;
        int screenWidth = client.getWindow().getScaledWidth();
        int maxWidth = Math.max(100, screenWidth - 20);
        return Math.min(rawWidth, maxWidth);
    }

    public static void handleRoomCodeNotification(String roomCode) {
        if (roomCode == null || roomCode.isEmpty()) {
            return;
        }
        if (roomCode.equals(lastRoomCode)) {
            return;
        }
        lastRoomCode = roomCode;
        LOGGER.info("Host room ready with code {}", roomCode);
        showRoomCodeToasts(roomCode);
    }

    private static void showRoomCodeInChat(String roomCode) {
        MinecraftClient client = MinecraftClient.getInstance();

        var msg = Text.literal("[Ender Core] 房间已创建！房间号: ").formatted(Formatting.GREEN);
        var code = Text.literal(roomCode).styled(style -> style
                .withColor(Formatting.AQUA)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, roomCode))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("点击复制")))
        );

        client.inGameHud.getChatHud().addMessage(msg.append(code));
    }

    @Override
    public void onInitializeClient() {
        com.multiplayer.ender.client.ServerTickHandler.init();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!hasAutoStarted && client.currentScreen instanceof TitleScreen) {
                hasAutoStarted = true;
                if (FabricConfig.isAutoStartBackend() && !EnderApiClient.hasDynamicPort()) {
                    client.execute(() -> client.setScreen(new StartupScreen(client.currentScreen)));
                }
            }

            if (client.player == null) {
                return;
            }

            tickCounter++;
            if (tickCounter >= 20) {
                tickCounter = 0;
                checkBackendState();
            }
        });
    }

    private static void checkBackendState() {
        if (!EnderApiClient.hasDynamicPort()) {
            if (wasHostOk) {
                LOGGER.info("Backend dynamic port lost while previously host-ok, room considered closed");
            }
            wasHostOk = false;
            lastRoomCode = "";
            lastMemberNames.clear();
            return;
        }

        EnderApiClient.getState().thenAccept(stateJson -> {
            if (stateJson == null) {
                return;
            }
            try {
                JsonObject json = GSON.fromJson(stateJson, JsonObject.class);
                if (json.has("state")) {
                    String state = json.get("state").getAsString();
                    if (!state.equals(lastStateValue)) {
                        LOGGER.info("Ender backend state changed: {} -> {}", lastStateValue, state);
                        lastStateValue = state;
                    }
                    boolean isHostOk = "host-ok".equals(state);
                    boolean isConnected = isHostOk || "guest-ok".equals(state);

                    if (isHostOk) {
                        if (json.has("room")) {
                            String roomCode = json.get("room").getAsString();
                            handleRoomCodeNotification(roomCode);
                        }
                    } else {
                        if (wasHostOk) {
                            LOGGER.info("Host room left host-ok state, new state={}", state);
                        }
                        lastRoomCode = "";
                    }

                    if (isConnected) {
                        Set<String> currentMembers = new HashSet<>();
                        if (json.has("profiles")) {
                            JsonArray profiles = json.getAsJsonArray("profiles");
                            for (JsonElement element : profiles) {
                                JsonObject profile = element.getAsJsonObject();
                                if (profile.has("name")) {
                                    currentMembers.add(profile.get("name").getAsString());
                                }
                            }
                        } else if (json.has("players")) {
                            JsonArray players = json.getAsJsonArray("players");
                            for (JsonElement element : players) {
                                currentMembers.add(element.getAsString());
                            }
                        }

                        if (!currentMembers.isEmpty() || !lastMemberNames.isEmpty()) {
                            Set<String> joined = new HashSet<>(currentMembers);
                            joined.removeAll(lastMemberNames);

                            Set<String> left = new HashSet<>(lastMemberNames);
                            left.removeAll(currentMembers);

                            if ((!joined.isEmpty() || !left.isEmpty()) && MinecraftClient.getInstance() != null) {
                                MinecraftClient.getInstance().execute(() -> {
                                    String selfName = MinecraftClient.getInstance().player != null
                                            ? MinecraftClient.getInstance().player.getGameProfile().getName()
                                            : null;

                                    for (String name : joined) {
                                        if (selfName != null && selfName.equals(name)) {
                                            continue;
                                        }
                                        showToast(Text.literal("成员加入"), Text.literal(name + " 加入了房间"));
                                    }

                                    for (String name : left) {
                                        if (selfName != null && selfName.equals(name)) {
                                            continue;
                                        }
                                        showToast(Text.literal("成员退出"), Text.literal(name + " 已离开房间"));
                                    }
                                });
                            }

                            lastMemberNames = currentMembers;
                        }
                    } else {
                        lastMemberNames.clear();
                    }

                    wasHostOk = isHostOk;
                }
            } catch (Exception ignored) {
            }
        });
    }

    private static void showRoomCodeToasts(String roomCode) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            showToast(Text.literal("房间号"), Text.literal("房间号: " + roomCode));
            showRoomCodeInChat(roomCode);
            try {
                client.keyboard.setClipboard(roomCode);
                showToast(Text.literal("提示"), Text.literal("房间号已复制到剪贴板"));
            } catch (Exception e) {
                showToast(Text.literal("提示"), Text.literal("复制失败，请手动复制房间号"));
            }
        });
    }
}


