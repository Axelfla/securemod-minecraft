package com.securemod.socket;

import com.securemod.SecureMod;
import com.securemod.crypto.SecureSession;
import com.securemod.network.SecureChannel;
import com.securemod.server.SecureServerDispatcher;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Serveur TCP dédié sur le port 27654.
 * Reçoit les messages AES chiffrés des clients connectés.
 *
 * Protocole de trame :
 *   [4 bytes : longueur payload] [N bytes : payload AES chiffré]
 *   Premier message : token (10 bytes ASCII) pour identifier la session
 */
public class SecureSocketServer {

    public static final int PORT = 27654;

    private static ServerSocket serverSocket;
    private static ExecutorService pool;
    private static volatile boolean running = false;
    private static MinecraftServer mcServer;

    /** token → socket client */
    private static final Map<String, ClientConnection> connections = new ConcurrentHashMap<>();

    public static void start(MinecraftServer server) {
        mcServer = server;
        pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "SecureSocket-Worker");
            t.setDaemon(true);
            return t;
        });

        Thread acceptThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                running = true;
                SecureMod.LOGGER.info("[SecureSocket] Server started on port {}", PORT);

                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        pool.submit(() -> handleClient(client));
                    } catch (IOException e) {
                        if (running) SecureMod.LOGGER.error("[SecureSocket] Accept error: {}", e.getMessage());
                    }
                }
            } catch (IOException e) {
                SecureMod.LOGGER.error("[SecureSocket] Unable to start on port {}: {}", PORT, e.getMessage());
            }
        }, "SecureSocket-Accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public static void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        if (pool != null) pool.shutdownNow();
        connections.clear();
        SecureMod.LOGGER.info("[SecureSocket] Server stopped");
    }

    public static void disconnectPlayer(String token) {
        ClientConnection conn = connections.remove(token);
        if (conn != null) conn.close();
    }

    // ─── Envoyer un message chiffré à un joueur via socket ────────────────────

    public static boolean sendToPlayer(String token, byte[] encryptedPayload) {
        ClientConnection conn = connections.get(token);
        if (conn == null) return false;
        return conn.send(encryptedPayload);
    }

    // ─── Gestion d'un client ──────────────────────────────────────────────────

    private static void handleClient(Socket socket) {
        String token = null;
        try {
            socket.setSoTimeout(10_000); // 10s timeout pour l'auth
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // Premier message = token (10 bytes)
            byte[] tokenBytes = new byte[10];
            in.readFully(tokenBytes);
            token = new String(tokenBytes, "ASCII").trim();

            // Vérifier que le token correspond à une session active
            SecureSession session = SecureChannel.getServerSessionByToken(token);
            if (session == null || !session.isSecure()) {
                SecureMod.LOGGER.warn("[SecureSocket] Invalid token: {}", token);
                socket.close();
                return;
            }

            socket.setSoTimeout(0); // pas de timeout pendant la session
            ClientConnection conn = new ClientConnection(token, socket);
            connections.put(token, conn);
            SecureMod.LOGGER.info("[SecureSocket] Client connected on port {} — token={}", PORT, token);

            // Boucle de lecture
            while (!socket.isClosed()) {
                // Lire longueur
                int len;
                try { len = in.readInt(); } catch (EOFException e) { break; }
                if (len <= 0 || len > 65536) break;

                byte[] payload = new byte[len];
                in.readFully(payload);

                // Déchiffrer
                byte[] plain;
                try { plain = session.decryptMessage(payload); }
                catch (Exception e) { SecureMod.LOGGER.error("[SecureSocket] Decryption error token={}: {}", token, e.getMessage()); continue; }
                String message = new String(plain, "UTF-8");

                // Dispatcher sur le thread Minecraft
                final String finalToken = token;
                final String finalMessage = message;
                if (mcServer != null) {
                    mcServer.execute(() -> {
                        ServerPlayer player = getPlayerByToken(finalToken);
                        if (player != null) {
                            SecureServerDispatcher.dispatch(player, finalMessage);
                        }
                    });
                }
            }

        } catch (SecurityException e) {
            SecureMod.LOGGER.error("[SecureSocket] Invalid HMAC from token={} — possible attack!", token);
        } catch (IOException e) {
            if (running) SecureMod.LOGGER.debug("[SecureSocket] Disconnection token={}: {}", token, e.getMessage());
        } finally {
            if (token != null) connections.remove(token);
            try { socket.close(); } catch (IOException ignored) {}
            SecureMod.LOGGER.info("[SecureSocket] Client disconnected — token={}", token);
        }
    }

    private static ServerPlayer getPlayerByToken(String token) {
        if (mcServer == null) return null;
        UUID uuid = SecureChannel.getUUIDForToken(token);
        if (uuid == null) return null;
        return mcServer.getPlayerList().getPlayer(uuid);
    }

    // ─── Connexion client ─────────────────────────────────────────────────────

    public static class ClientConnection {
        private final String token;
        private final Socket socket;
        private final DataOutputStream out;

        public ClientConnection(String token, Socket socket) throws IOException {
            this.token = token;
            this.socket = socket;
            this.out = new DataOutputStream(socket.getOutputStream());
        }

        public boolean send(byte[] payload) {
            try {
                out.writeInt(payload.length);
                out.write(payload);
                out.flush();
                return true;
            } catch (IOException e) {
                SecureMod.LOGGER.debug("[SecureSocket] Send error token={}: {}", token, e.getMessage());
                return false;
            }
        }

        public void close() {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
