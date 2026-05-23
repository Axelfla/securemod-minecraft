package com.securemod.common;

import com.securemod.SecureMod;
import com.securemod.crypto.SecureSession;
import com.securemod.network.Packets;
import com.securemod.network.SecureChannel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;

public class SecureAPI {

    public static boolean sendToServer(String message) {
        SecureSession session = SecureChannel.getClientSession();
        if (session == null || !session.isSecure()) {
            SecureMod.LOGGER.warn("[SecureAPI] Attempted to send without active tunnel");
            return false;
        }
        try {
            byte[] payload = session.encryptMessage(message.getBytes(StandardCharsets.UTF_8));
            PacketDistributor.sendToServer(new Packets.SecureMessagePacket(payload));
            return true;
        } catch (Exception e) {
            SecureMod.LOGGER.error("[SecureAPI] Client→server send error: {}", e.getMessage());
            return false;
        }
    }

    public static boolean sendToPlayer(ServerPlayer player, String message) {
        SecureSession session = SecureChannel.getServerSession(player.getUUID());
        if (session == null || !session.isSecure()) {
            SecureMod.LOGGER.warn("[SecureAPI] No tunnel for {}", player.getName().getString());
            return false;
        }
        try {
            byte[] payload = session.encryptMessage(message.getBytes(StandardCharsets.UTF_8));
            PacketDistributor.sendToPlayer(player, new Packets.SecureMessagePacket(payload));
            return true;
        } catch (Exception e) {
            SecureMod.LOGGER.error("[SecureAPI] Server→{} send error: {}", player.getName().getString(), e.getMessage());
            return false;
        }
    }

    public static boolean isClientSecure() {
        SecureSession s = SecureChannel.getClientSession();
        return s != null && s.isSecure();
    }

    /**
     * Returns the client session token (10 chars), or null if not yet connected.
     */
    public static String getClientToken() {
        SecureSession s = SecureChannel.getClientSession();
        return s != null ? s.getToken() : null;
    }

    public static boolean isPlayerSecure(ServerPlayer player) {
        SecureSession s = SecureChannel.getServerSession(player.getUUID());
        return s != null && s.isSecure();
    }
}
