package com.multiplayer.ender.fabric.mixin.client;

import com.multiplayer.ender.client.gui.StartupScreen;
import com.multiplayer.ender.client.gui.EnderDashboard;
import com.multiplayer.ender.network.EnderApiClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerMenuMixin extends Screen {
    protected MultiplayerMenuMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int width = this.width;
        int buttonWidth = 120;
        int x = width - buttonWidth - 5;
        int y = 5;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("末影联机"), button -> {
            if (EnderApiClient.hasDynamicPort()) {
                mc.setScreen(new EnderDashboard((Screen) (Object) this));
            } else {
                mc.setScreen(new StartupScreen((Screen) (Object) this, () -> {
                    mc.setScreen(new EnderDashboard((Screen) (Object) this));
                }));
            }
        }).dimensions(x, y, buttonWidth, 20).build());
    }
}


