package com.securemod.mixin;

import com.securemod.SecureMod;
import com.securemod.common.SecureAPI;
import com.securemod.crypto.SecureSession;
import com.securemod.network.SecureChannel;
import com.securemod.socket.SecureSocketClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepte sendChat() et sendCommand() au niveau réseau.
 * Si le socket 27654 est connecté → envoie via socket chiffré.
 * Sinon → fallback sur NeoForge packet (SecureAPI).
 */
@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void onSendChat(String message, CallbackInfo ci) {
        ci.cancel();

        if (!SecureAPI.isClientSecure()) {
            SecureMod.LOGGER.warn("[Mixin] Chat blocked — tunnel not established");
            showMessage("§c[SecureMod] Tunnel not established, message rejeté.");
            return;
        }

        String payload = "CHAT:" + message;
        sendSecure(payload);
    }

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void onSendCommand(String command, CallbackInfo ci) {
        ci.cancel();

        if (!SecureAPI.isClientSecure()) {
            SecureMod.LOGGER.warn("[Mixin] Command blocked — tunnel not established");
            showMessage("§c[SecureMod] Tunnel not established, commande rejetée.");
            return;
        }

        String payload = "CMD:" + command;
        sendSecure(payload);
    }

    private static void sendSecure(String payload) {
        SecureSession session = SecureChannel.getClientSession();
        if (session == null) return;

        // Socket dédié 27654 obligatoire — pas de fallback
        if (SecureSocketClient.isConnected()) {
            SecureSocketClient.sendMessage(payload, session);
            SecureMod.LOGGER.debug("[Mixin] Sent via socket 27654: {}", payload.substring(0, Math.min(payload.length(), 40)));
        } else {
            SecureMod.LOGGER.warn("[Mixin] Socket 27654 not connected — message blocked");
            showMessage("§c[SecureMod] Secure channel unavailable.");
        }
    }

    private static void showMessage(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null)
            mc.player.displayClientMessage(Component.literal(msg), false);
    }
}
