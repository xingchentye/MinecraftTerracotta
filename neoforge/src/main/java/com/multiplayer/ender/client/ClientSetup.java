package com.multiplayer.ender.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.multiplayer.ender.Config;
import com.multiplayer.ender.client.gui.StartupScreen;
import com.multiplayer.ender.network.EnderApiClient;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = "ender_online", value = Dist.CLIENT)
public class ClientSetup {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSetup.class);
    private static boolean hasAutoStarted = false;
    private static int tickCounter = 0;
    private static boolean wasHostOk = false;
    private static String lastRoomCode = "";
    private static final Gson GSON = new Gson();
    private static String lastStateValue = "";
    private static java.util.Set<String> lastMemberNames = new java.util.HashSet<>();
    private static final java.util.List<HudToast> activeToasts = new java.util.ArrayList<>();

    private static class HudToast {
        private final Component title;
        private final Component message;
        private final int width;
        private final int height;
        private final long startTime;

        private HudToast(Component title, Component message, int width, int height, long startTime) {
            this.title = title;
            this.message = message;
            this.width = width;
            this.height = height;
            this.startTime = startTime;
        }
    }

    public static void showToast(Component title, Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return;
        }
        int width = calculateToastWidth(title, message);
        int height = 32;
        long now = System.currentTimeMillis();
        activeToasts.add(new HudToast(title, message, width, height, now));
    }

    private static int calculateToastWidth(Component title, Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return 220;
        }
        int maxTextWidth = mc.font.width(title);
        if (message != null) {
            maxTextWidth = Math.max(maxTextWidth, mc.font.width(message));
        }
        int padding = 16;
        int rawWidth = maxTextWidth + padding;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
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
        Minecraft mc = Minecraft.getInstance();
        MutableComponent msg = Component.literal("[Ender Core] 房间已创建！房间号: ");
        msg.withStyle(ChatFormatting.GREEN);

        MutableComponent code = Component.literal(roomCode);
        code.withStyle(style -> style
            .withColor(ChatFormatting.AQUA)
            .withBold(true)
            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, roomCode))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("点击复制")))
        );

        msg.append(code);
        mc.gui.getChat().addMessage(msg);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().player == null) return;

        tickCounter++;
        if (tickCounter >= 20) {
            tickCounter = 0;
            checkBackendState();
        }
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
            if (stateJson == null) return;
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
                        java.util.Set<String> currentMembers = new java.util.HashSet<>();
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
                            java.util.Set<String> joined = new java.util.HashSet<>(currentMembers);
                            joined.removeAll(lastMemberNames);

                            java.util.Set<String> left = new java.util.HashSet<>(lastMemberNames);
                            left.removeAll(currentMembers);

                            if ((!joined.isEmpty() || !left.isEmpty()) && Minecraft.getInstance() != null) {
                                Minecraft.getInstance().execute(() -> {
                                    String selfName = Minecraft.getInstance().player != null
                                            ? Minecraft.getInstance().player.getGameProfile().getName()
                                            : null;

                                    for (String name : joined) {
                                        if (selfName != null && selfName.equals(name)) continue;
                                        showToast(Component.literal("成员加入"), Component.literal(name + " 加入了房间"));
                                    }

                                    for (String name : left) {
                                        if (selfName != null && selfName.equals(name)) continue;
                                        showToast(Component.literal("成员退出"), Component.literal(name + " 已离开房间"));
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
            } catch (Exception ignored) {}
        });
    }

    private static void showRoomCodeToast(String roomCode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return;
        }
        int width = calculateToastWidth(Component.literal("房间号"), Component.literal("房间号: " + roomCode));
        int height = 32;
        long now = System.currentTimeMillis();
        activeToasts.add(new HudToast(Component.literal("房间号"), Component.literal("房间号: " + roomCode), width, height, now));
    }

    private static void showRoomCodeToasts(String roomCode) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            showRoomCodeToast(roomCode);
            showRoomCodeInChat(roomCode);
            try {
                minecraft.keyboardHandler.setClipboard(roomCode);
                showToast(Component.literal("提示"), Component.literal("房间号已复制到剪贴板"));
            } catch (Exception e) {
                showToast(Component.literal("提示"), Component.literal("复制失败，请手动复制房间号"));
            }
        });
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (activeToasts.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return;
        }
        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        long now = System.currentTimeMillis();
        int y = 5;
        int margin = 5;

        java.util.Iterator<HudToast> iterator = activeToasts.iterator();
        while (iterator.hasNext()) {
            HudToast toast = iterator.next();
            long age = now - toast.startTime;
            if (age >= 2000L) {
                iterator.remove();
                continue;
            }
            double inDuration = 200.0;
            double outDuration = 200.0;
            double offset = 0.0;
            if (age < inDuration) {
                double t = age / inDuration;
                offset = 1.0 - t;
            } else if (age > 2000.0 - outDuration) {
                double t = (age - (2000.0 - outDuration)) / outDuration;
                offset = t;
            }
            int x = screenWidth - toast.width - 5 + (int) (toast.width * offset);
            guiGraphics.fill(x, y, x + toast.width, y + toast.height, 0xCC000000);
            guiGraphics.drawString(mc.font, toast.title, x + 8, y + 8, 0xFFFFFF, false);
            if (toast.message != null) {
                guiGraphics.drawString(mc.font, toast.message, x + 8, y + 20, 0xFFFFFF, false);
            }
            y += toast.height + margin;
        }
    }

    @SubscribeEvent
    public static void onClientLogout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        if (wasHostOk) {
             LOGGER.info("Detected world disconnect, stopping hosting...");
             EnderApiClient.setIdle();
             new Thread(com.multiplayer.ender.logic.ProcessLauncher::stop, "Ender-Stopper").start();
             wasHostOk = false;
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen && !hasAutoStarted) {
            hasAutoStarted = true;
            
            if (Config.AUTO_START_BACKEND.get() && !EnderApiClient.hasDynamicPort()) {
                Minecraft.getInstance().execute(() -> {
                     Minecraft.getInstance().setScreen(new StartupScreen(event.getScreen()));
                });
            }
        }
    }
}


