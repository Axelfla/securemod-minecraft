package com.securemod.socket;

import com.securemod.SecureMod;
import com.securemod.common.SecureAPI;
import com.securemod.crypto.SecureSession;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client socket TCP côté client — se connecte sur le port 27654 du serveur.
 * Envoie tout le trafic chiffré AES via cette socket dédiée.
 *
 * Protocole : [4 bytes : longueur] [N bytes : payload AES]
 * Identification initiale : token (10 bytes ASCII) envoyé en premier
 */
public class SecureSocketClient {

    public static final int PORT = 27654;

    private static Socket socket;
    private static DataOutputStream out;
    private static DataInputStream in;
    private static volatile boolean connected = false;
    private static ExecutorService readThread;
    private static String serverHost;

    public static boolean isConnected() { return connected; }

    public static void connect(String host, String token, SecureSession session) {
        serverHost = host;
        Thread t = new Thread(() -> {
            try {
                socket = new Socket(host, PORT);
                out = new DataOutputStream(socket.getOutputStream());
                in  = new DataInputStream(socket.getInputStream());

                // Envoyer le token (10 bytes) pour s'identifier
                byte[] tokenBytes = new byte[10];
                byte[] tb = token.getBytes("ASCII");
                System.arraycopy(tb, 0, tokenBytes, 0, Math.min(tb.length, 10));
                out.write(tokenBytes);
                out.flush();

                connected = true;
                SecureMod.LOGGER.info("[SecureSocketClient] Connected on {}:{} — token={}", host, PORT, token);
                showMessage("§a[SecureMod] 🔒 Secure channel established on port " + PORT);

                // Boucle de lecture (messages serveur → client)
                while (!socket.isClosed()) {
                    try {
                        int len = in.readInt();
                        if (len <= 0 || len > 65536) break;
                        byte[] payload = new byte[len];
                        in.readFully(payload);

                        byte[] plain;
                        try { plain = session.decryptMessage(payload); }
                        catch (Exception e) { SecureMod.LOGGER.error("[SecureSocketClient] Decryption error: {}", e.getMessage()); continue; }
                        String message = new String(plain, "UTF-8");

                        // Traiter les messages venant du serveur
                        Minecraft.getInstance().execute(() -> handleServerMessage(message));

                    } catch (EOFException e) { break; }
                }

            } catch (IOException e) {
                SecureMod.LOGGER.warn("[SecureSocketClient] Unable to connect on port {}: {}", PORT, e.getMessage());
                showMessage("§e[SecureMod] ⚠ Secure channel port " + PORT + " unavailable — degraded mode");
            } finally {
                connected = false;
                try { if (socket != null) socket.close(); } catch (IOException ignored) {}
                SecureMod.LOGGER.info("[SecureSocketClient] Disconnected from secure channel");
            }
        }, "SecureSocketClient");
        t.setDaemon(true);
        t.start();
    }

    public static void disconnect() {
        connected = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    /**
     * Envoyer un payload déjà chiffré AES au serveur.
     */
    public static boolean send(byte[] encryptedPayload) {
        if (!connected || out == null) return false;
        try {
            out.writeInt(encryptedPayload.length);
            out.write(encryptedPayload);
            out.flush();
            return true;
        } catch (IOException e) {
            SecureMod.LOGGER.error("[SecureSocketClient] Send error: {}", e.getMessage());
            connected = false;
            return false;
        }
    }

    /**
     * Chiffrer et envoyer un message texte.
     */
    public static boolean sendMessage(String plaintext, SecureSession session) {
        if (!connected) return false;
        try {
            byte[] encrypted = session.encryptMessage(plaintext.getBytes("UTF-8"));
            return send(encrypted);
        } catch (Exception e) {
            SecureMod.LOGGER.error("[SecureSocketClient] Encryption error: {}", e.getMessage());
            return false;
        }
    }

    private static void handleServerMessage(String message) {
        // Messages serveur → client via socket (réponses, broadcasts, etc.)
        SecureMod.LOGGER.debug("[SecureSocketClient] Message received: {}", message);
    }

    private static void showMessage(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                if (mc.player != null)
                    mc.player.displayClientMessage(Component.literal(msg), false);
            });
        }
    }
}
