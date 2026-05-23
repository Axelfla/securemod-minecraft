package com.securemod.network.handler;

import com.securemod.SecureMod;
import com.securemod.crypto.SecureSession;
import com.securemod.network.Packets;
import com.securemod.network.SecureChannel;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.charset.StandardCharsets;

/**
 * Flux côté client :
 *
 *  reçoit HELLO           → valider version, envoyer PublicKeyPacket client
 *  reçoit PublicKeyPacket → stocker clé serveur
 *  reçoit ChallengePacket → signer + envoyer SignaturePacket + SessionKeyPacket
 *  reçoit SecureMessage   → décrypter, stocker token → tunnel ACTIF ✅
 */
public class ClientPacketHandler {

    public static final String PROTOCOL_VERSION = "SECUREMOD_V1";

    // ─── HELLO reçu → envoyer notre clé publique ───────────────────────────────

    public static void handleHello(Packets.HelloPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            SecureMod.LOGGER.info("[Client] HELLO received — version: {}", pkt.protocolVersion());
            if (!PROTOCOL_VERSION.equals(pkt.protocolVersion())) {
                SecureMod.LOGGER.error("[Client] Incompatible protocol!");
                return;
            }
            // Envoyer notre clé publique RSA au serveur
            SecureSession session = SecureChannel.getOrCreateClientSession();
            PacketDistributor.sendToServer(new Packets.PublicKeyPacket(session.getLocalPublicKeyBytes()));
            SecureMod.LOGGER.info("[Client] RSA public key sent");
        });
    }

    // ─── PublicKey serveur reçue → stocker ─────────────────────────────────────

    public static void handlePublicKey(Packets.PublicKeyPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            SecureSession session = SecureChannel.getClientSession();
            if (session == null) { SecureMod.LOGGER.error("[Client] No session"); return; }
            try {
                session.setRemotePublicKey(pkt.publicKeyBytes());
                SecureMod.LOGGER.info("[Client] Server public key stored — waiting for challenge");
            } catch (Exception e) {
                SecureMod.LOGGER.error("[Client] Server key storage error: {}", e.getMessage());
            }
        });
    }

    // ─── Challenge reçu → signer + envoyer Signature + SessionKey ──────────────

    public static void handleChallenge(Packets.ChallengePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            SecureSession session = SecureChannel.getClientSession();
            if (session == null) { SecureMod.LOGGER.error("[Client] No session for challenge"); return; }
            try {
                // Déchiffrer et signer le challenge
                byte[] signature = session.answerChallenge(pkt.encryptedChallenge());
                PacketDistributor.sendToServer(new Packets.SignaturePacket(signature));
                SecureMod.LOGGER.info("[Client] Signature sent");

                // Générer la clé AES et l'envoyer chiffrée avec la clé publique serveur
                byte[] encryptedSessionKey = session.generateAndExportSessionKey();
                String pendingPw = com.securemod.client.screen.PendingTokenStorage.getToken();
                PacketDistributor.sendToServer(new Packets.SessionKeyPacket(
                    encryptedSessionKey,
                    pendingPw != null ? pendingPw : ""
                ));
                SecureMod.LOGGER.info("[Client] AES key sent (RSA encrypted)");

            } catch (Exception e) {
                SecureMod.LOGGER.error("[Client] Challenge handling error: {}", e.getMessage());
            }
        });
    }

    // ─── Message sécurisé reçu (confirmation + messages Phase 4) ──────────────

    public static void handleSecureMessage(Packets.SecureMessagePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            SecureSession session = SecureChannel.getClientSession();
            if (session == null || !session.isSecure()) {
                SecureMod.LOGGER.error("[Client] Message received but tunnel not established");
                return;
            }
            try {
                byte[] plain = session.decryptMessage(pkt.payload());
                String message = new String(plain, StandardCharsets.UTF_8);

                if (message.startsWith("SECURE_CHANNEL_READY:")) {
                    String serverToken = message.substring("SECURE_CHANNEL_READY:".length());
                    com.securemod.client.screen.PendingTokenStorage.clear();
                    com.securemod.client.screen.PendingTokenStorage.clear();
                    // ───────────────────────────────────────────────────────

                    session.setClientToken(serverToken);
                    SecureMod.LOGGER.info("[Client] 🔒 Secure tunnel ACTIVE — token={}", serverToken);

                    // Connexion au socket dédié port 27654
                    String host = net.minecraft.client.Minecraft.getInstance()
                        .getCurrentServer() != null
                        ? net.minecraft.client.Minecraft.getInstance().getCurrentServer().ip
                        : "localhost";
                    // Extraire l'IP sans le port si format "ip:port"
                    if (host.contains(":")) host = host.substring(0, host.lastIndexOf(":"));
                    com.securemod.socket.SecureSocketClient.connect(host, serverToken, session);
                } else {
                    SecureMod.LOGGER.info("[Client] Message déchiffré: {}", message);
                }
            } catch (SecurityException e) {
                SecureMod.LOGGER.error("[Client] Invalid HMAC!");
                SecureChannel.clearClientSession();
            } catch (Exception e) {
                SecureMod.LOGGER.error("[Client] Decryption error: {}", e.getMessage());
            }
        });
    }
}
