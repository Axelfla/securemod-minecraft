package com.securemod.network.handler;

import com.securemod.SecureMod;
import com.securemod.crypto.SecureSession;
import com.securemod.network.HandshakeTimeoutWatchdog;
import com.securemod.network.Packets;
import com.securemod.network.SecureChannel;
import com.securemod.server.ServerConfig;
import com.securemod.server.SecureServerDispatcher;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class ServerPacketHandler {

    // ─── Phase 1b : Clé publique client → envoyer challenge ───────────────────

    public static void handlePublicKey(Packets.PublicKeyPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            UUID uid = player.getUUID();
            SecureSession session = SecureChannel.getServerSession(uid);
            if (session == null) { kickPlayer(player, "Session not found"); return; }

            try {
                session.setRemotePublicKey(pkt.publicKeyBytes());
                String token = session.getToken();
                SecureChannel.promoteSession(uid, token);
                SecureMod.LOGGER.info("[Serveur] Client key received from {} → token={}", player.getName().getString(), token);

                byte[] encryptedChallenge = session.prepareChallenge();
                PacketDistributor.sendToPlayer(player, new Packets.ChallengePacket(encryptedChallenge));
                SecureMod.LOGGER.info("[Serveur] Challenge sent to {}", player.getName().getString());

            } catch (Exception e) {
                SecureMod.LOGGER.error("[Serveur] Public key error: {}", e.getMessage());
                kickPlayer(player, "Key exchange error");
            }
        });
    }

    // ─── Phase 2 : Vérification signature ─────────────────────────────────────

    public static void handleSignature(Packets.SignaturePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            SecureSession session = SecureChannel.getServerSession(player.getUUID());
            if (session == null) { kickPlayer(player, "Session not found"); return; }

            try {
                boolean ok = session.verifyChallenge(pkt.signature());
                if (!ok) {
                    kickPlayer(player, "Authentication refused");
                    return;
                }
                SecureMod.LOGGER.info("[Serveur] ✅ Client AUTHENTICATED : {}", player.getName().getString());

            } catch (Exception e) {
                SecureMod.LOGGER.error("[Serveur] Verification error: {}", e.getMessage());
                kickPlayer(player, "Verification error");
            }
        });
    }

    // ─── Phase 3 : Réception clé AES ──────────────────────────────────────────

    public static void handleSessionKey(Packets.SessionKeyPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            SecureSession session = SecureChannel.getServerSession(player.getUUID());
            if (session == null) { kickPlayer(player, "Session not found"); return; }

            try {
                session.importSessionKey(pkt.encryptedSessionKey());
                String token = SecureChannel.getTokenForPlayer(player.getUUID());
                HandshakeTimeoutWatchdog.cancelTimer(player.getUUID());

                // ── Vérification du mot de passe ──────────────────────────
                String clientPassword = pkt.password();
                if (!ServerConfig.checkPassword(clientPassword)) {
                    SecureMod.LOGGER.warn("[Serveur] ❌ Wrong password from {} — kicking",
                        player.getName().getString());
                    kickPlayer(player, "§c[SecureMod] Incorrect password.");
                    return;
                }
                // ──────────────────────────────────────────────────────────

                SecureMod.LOGGER.info("[Serveur] 🔒 AES tunnel established with {} (token={}) en {}ms",
                    player.getName().getString(), token, session.getAgeMs());

                String ready = "SECURE_CHANNEL_READY:" + token;
                byte[] confirmation = session.encryptMessage(ready.getBytes(StandardCharsets.UTF_8));
                PacketDistributor.sendToPlayer(player, new Packets.SecureMessagePacket(confirmation));

            } catch (Exception e) {
                SecureMod.LOGGER.error("[Serveur] AES key error: {}", e.getMessage());
                kickPlayer(player, "Tunnel establishment error");
            }
        });
    }

    // ─── Phase 4 : Message sécurisé → dispatcher ──────────────────────────────

    public static void handleSecureMessage(Packets.SecureMessagePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            SecureSession session = SecureChannel.getServerSession(player.getUUID());
            if (session == null || !session.isSecure()) {
                kickPlayer(player, "Tunnel not established");
                return;
            }

            try {
                byte[] plain = session.decryptMessage(pkt.payload());
                String message = new String(plain, StandardCharsets.UTF_8);

                // Dispatcher vers le bon handler selon le type
                SecureServerDispatcher.dispatch(player, message);

            } catch (SecurityException e) {
                SecureMod.LOGGER.error("[Serveur] Invalid HMAC from {} — possible attack!", player.getName().getString());
                kickPlayer(player, "Compromised packet detected");
            } catch (Exception e) {
                SecureMod.LOGGER.error("[Serveur] Decryption error from {}: {}", player.getName().getString(), e.getMessage());
            }
        });
    }

    // ─── Utilitaire ────────────────────────────────────────────────────────────

    private static void kickPlayer(ServerPlayer player, String reason) {
        SecureMod.LOGGER.warn("[Serveur] Kick {} : {}", player.getName().getString(), reason);
        HandshakeTimeoutWatchdog.cancelTimer(player.getUUID());
        player.connection.disconnect(net.minecraft.network.chat.Component.literal("[SecureMod] " + reason));
        SecureChannel.removeServerSession(player.getUUID());
    }
}
