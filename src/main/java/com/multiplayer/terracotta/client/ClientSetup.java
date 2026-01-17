package com.multiplayer.terracotta.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.multiplayer.terracotta.Config;
import com.multiplayer.terracotta.client.gui.StartupScreen;
import com.multiplayer.terracotta.network.TerracottaApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
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

@EventBusSubscriber(modid = "minecraftterracotta", value = Dist.CLIENT)
/**
 * 客户端设置类
 * 处理客户端事件和后台状态检查
 *
 * @author xingchentye
 */
public class ClientSetup {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSetup.class);
    private static boolean hasAutoStarted = false;
    private static int tickCounter = 0;
    private static boolean wasHostOk = false;
    private static String lastRoomCode = "";
    private static final Gson GSON = new Gson();
    private static String lastStateValue = "";

    private static class SimpleToast implements Toast {
        private final Component title;
        private final Component message;
        private long firstDrawTime;

        private SimpleToast(Component title, Component message) {
            this.title = title;
            this.message = message;
        }

        @Override
        public Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long time) {
            if (firstDrawTime == 0L) {
                firstDrawTime = time;
            }

            int width = width();
            int height = height();

            guiGraphics.fill(0, 0, width, height, 0xCC000000);

            var font = toastComponent.getMinecraft().font;
            guiGraphics.drawString(font, title, 8, 8, 0xFFFFFF, false);
            if (message != null) {
                guiGraphics.drawString(font, message, 8, 20, 0xFFFFFF, false);
            }

            return time - firstDrawTime >= 2000L ? Visibility.HIDE : Visibility.SHOW;
        }

        @Override
        public int width() {
            return 220;
        }

        @Override
        public int height() {
            return 32;
        }
    }

    public static void showToast(Component title, Component message) {
        Minecraft mc = Minecraft.getInstance();
        mc.getToasts().addToast(new SimpleToast(title, message));
    }

    private static void showRoomCodeInChat(String roomCode) {
        Minecraft mc = Minecraft.getInstance();
        MutableComponent msg = Component.literal("[Terracotta] 房间已创建！房间号: ");
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

    /**
     * 客户端 tick 事件处理
     * 定期检查后台状态
     *
     * @param event 客户端 tick 事件对象
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().player == null) return;

        tickCounter++;
        if (tickCounter >= 20) {
            tickCounter = 0;
            checkBackendState();
        }
    }

    /**
     * 检查后台服务状态
     * 如果检测到状态变为 host-ok，自动复制房间号
     */
    private static void checkBackendState() {
        if (!TerracottaApiClient.hasDynamicPort()) {
            if (wasHostOk) {
                LOGGER.info("Backend dynamic port lost while previously host-ok, room considered closed");
            }
            wasHostOk = false;
            lastRoomCode = "";
            return;
        }

        TerracottaApiClient.getState().thenAccept(stateJson -> {
            if (stateJson == null) return;
            try {
                JsonObject json = GSON.fromJson(stateJson, JsonObject.class);
                if (json.has("state")) {
                    String state = json.get("state").getAsString();
                    if (!state.equals(lastStateValue)) {
                        LOGGER.info("Terracotta backend state changed: {} -> {}", lastStateValue, state);
                        lastStateValue = state;
                    }
                    boolean isHostOk = "host-ok".equals(state);

                    if (isHostOk) {
                        if (json.has("room")) {
                            String roomCode = json.get("room").getAsString();
                            if (!wasHostOk || !roomCode.equals(lastRoomCode)) {
                                lastRoomCode = roomCode;
                                LOGGER.info("Host room ready with code {}", roomCode);
                                showRoomCodeToasts(roomCode);
                            }
                        }
                    } else {
                        if (wasHostOk) {
                            LOGGER.info("Host room left host-ok state, new state={}", state);
                        }
                        lastRoomCode = "";
                    }
                    wasHostOk = isHostOk;
                }
            } catch (Exception ignored) {}
        });
    }

    private static void showRoomCodeToasts(String roomCode) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            showToast(Component.literal("房间号"), Component.literal("房间号: " + roomCode));
            showRoomCodeInChat(roomCode);
            try {
                minecraft.keyboardHandler.setClipboard(roomCode);
                showToast(Component.literal("提示"), Component.literal("房间号已复制到剪贴板"));
            } catch (Exception e) {
                showToast(Component.literal("提示"), Component.literal("复制失败，请手动复制房间号"));
            }
        });
    }

    /**
     * 屏幕初始化事件处理
     * 处理自动启动逻辑
     *
     * @param event 屏幕初始化事件对象
     */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        // 仅在首次进入主菜单时触发
        if (event.getScreen() instanceof TitleScreen && !hasAutoStarted) {
            hasAutoStarted = true;
            
            // 如果配置启用了自动启动，且尚未连接后端
            if (Config.AUTO_START_BACKEND.get() && !TerracottaApiClient.hasDynamicPort()) {
                // 切换到启动界面进行初始化
                // 使用 execute 确保在渲染线程安全执行（虽然事件本身就在主线程）
                Minecraft.getInstance().execute(() -> {
                     Minecraft.getInstance().setScreen(new StartupScreen(event.getScreen()));
                });
            }
        }
    }
}
