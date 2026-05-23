package com.securemod.mixin;

import com.securemod.SecureMod;
import com.securemod.client.screen.TokenScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MixinMultiplayerScreen extends Screen {

    @Shadow private ServerSelectionList serverSelectionList;

    protected MixinMultiplayerScreen() {
        super(Component.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        this.addRenderableWidget(Button.builder(
            Component.literal("§a🔒 Secure Connection"),
            btn -> {
                ServerData server = getSelected();
                if (server == null) {
                    SecureMod.LOGGER.warn("[MixinMultiplayer] No server selected");
                    return;
                }
                Minecraft.getInstance().setScreen(new TokenScreen((JoinMultiplayerScreen)(Object)this, server));
            }
        ).bounds(this.width / 2 + 4, this.height - 52, 152, 20).build());
    }

    @Inject(method = "join()V", at = @At("HEAD"), cancellable = true, require = 0)
    private void onJoin(CallbackInfo ci) {
        ServerData server = getSelected();
        if (server == null) return;
        ci.cancel();
        Minecraft.getInstance().setScreen(new TokenScreen((JoinMultiplayerScreen)(Object)this, server));
    }

    @Inject(method = "joinSelectedServer()V", at = @At("HEAD"), cancellable = true, require = 0)
    private void onJoinSelected(CallbackInfo ci) {
        ServerData server = getSelected();
        if (server == null) return;
        ci.cancel();
        Minecraft.getInstance().setScreen(new TokenScreen((JoinMultiplayerScreen)(Object)this, server));
    }

    private ServerData getSelected() {
        if (serverSelectionList == null) return null;
        // En 1.21.1 NeoForge : ServerSelectionList.getSelectedServer() ou getSelected()
        try {
            var entry = serverSelectionList.getSelected();
            if (entry == null) return null;
            // ServerSelectionList.Entry a un champ serverData
            java.lang.reflect.Field f = entry.getClass().getDeclaredField("serverData");
            f.setAccessible(true);
            return (ServerData) f.get(entry);
        } catch (Exception e1) {
            try {
                java.lang.reflect.Method m = serverSelectionList.getClass()
                    .getMethod("getSelectedServer");
                return (ServerData) m.invoke(serverSelectionList);
            } catch (Exception e2) {
                SecureMod.LOGGER.warn("[MixinMultiplayer] getSelected() failed: {}", e1.getMessage());
                return null;
            }
        }
    }
}
