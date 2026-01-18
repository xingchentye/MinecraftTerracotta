package com.multiplayer.terracotta.client;

import com.multiplayer.terracotta.client.gui.StartupScreen;
import com.multiplayer.terracotta.client.gui.TerracottaDashboard;
import com.multiplayer.terracotta.network.TerracottaApiClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "minecraftterracotta", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MultiplayerMenuHandlerForge {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (screen instanceof JoinMultiplayerScreen joinScreen) {
            int width = joinScreen.width;
            int buttonWidth = 120;
            int x = width - buttonWidth - 5;
            int y = 5;

            Button terracottaBtn = Button.builder(Component.literal("陶瓦联机"), button -> {
                Minecraft mc = Minecraft.getInstance();
                if (TerracottaApiClient.hasDynamicPort()) {
                    mc.setScreen(new TerracottaDashboard(joinScreen));
                } else {
                    mc.setScreen(new StartupScreen(joinScreen, () -> {
                        mc.setScreen(new TerracottaDashboard(joinScreen));
                    }));
                }
            }).bounds(x, y, buttonWidth, 20).build();

            event.addListener(terracottaBtn);
        }
    }
}

