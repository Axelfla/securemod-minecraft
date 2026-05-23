package com.securemod.network;

import com.securemod.SecureMod;
import com.securemod.crypto.CryptoUtils;
import com.securemod.crypto.SecureSession;

import java.security.KeyPair;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Registre central des sessions sécurisées actives.
 *
 * Optimisation : les clés RSA sont pré-générées en arrière-plan au démarrage
 * du mod, pas au moment de la connexion du joueur. Cela évite que la génération
 * RSA (lente) consomme le délai du timeout de handshake.
 */
public class SecureChannel {

    // Pool de clés RSA pré-générées (prêtes à l'emploi)
    private static final java.util.concurrent.BlockingQueue<KeyPair> KEY_POOL =
        new java.util.concurrent.LinkedBlockingQueue<>();

    // Sessions côté serveur : TOKEN (ou "pending:<uuid>") → session
    private static final ConcurrentHashMap<String, SecureSession> SERVER_SESSIONS = new ConcurrentHashMap<>();

    // Mapping UUID réseau → token (alimenté après Phase 1)
    private static final ConcurrentHashMap<UUID, String> UUID_TO_TOKEN = new ConcurrentHashMap<>();

    // Session côté client (une seule à la fois)
    private static SecureSession clientSession = null;

    /**
     * À appeler au démarrage du mod (FMLCommonSetupEvent).
     * Pré-génère des paires RSA en arrière-plan pour éviter
     * la latence à la connexion des joueurs.
     */
    public static void init() {
        SecureMod.LOGGER.info("[SecureMod] SecureChannel initialized — pre-generating RSA keys...");

        // Générer 4 paires RSA en arrière-plan (serveur + client + 2 de réserve)
        ExecutorService warmup = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "securemod-rsa-warmup");
            t.setDaemon(true);
            return t;
        });

        for (int i = 0; i < 4; i++) {
            warmup.submit(() -> {
                try {
                    KeyPair kp = CryptoUtils.generateRSAKeyPair();
                    KEY_POOL.offer(kp);
                    SecureMod.LOGGER.debug("[SecureMod] RSA key pre-generated (pool: {})", KEY_POOL.size());
                } catch (Exception e) {
                    SecureMod.LOGGER.error("[SecureMod] RSA pre-generation error: {}", e.getMessage());
                }
            });
        }
        warmup.shutdown();
    }

    /**
     * Obtient une paire RSA du pool (instantané) ou en génère une nouvelle
     * si le pool est vide (fallback).
     */
    private static KeyPair getKeyPair() throws Exception {
        KeyPair kp = KEY_POOL.poll(); // non-bloquant
        if (kp != null) {
            // Regénérer une clé en arrière-plan pour remplacer celle consommée
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "securemod-rsa-refill");
                t.setDaemon(true);
                return t;
            }).submit(() -> {
                try {
                    KEY_POOL.offer(CryptoUtils.generateRSAKeyPair());
                } catch (Exception ignored) {}
            });
            return kp;
        }
        // Pool vide : génération synchrone (rare)
        SecureMod.LOGGER.warn("[SecureMod] RSA pool empty — synchronous generation (may be slow)");
        return CryptoUtils.generateRSAKeyPair();
    }

    // ─── Côté serveur ──────────────────────────────────────────────────────────

    public static SecureSession createPendingSession(UUID networkId) {
        try {
            KeyPair kp = getKeyPair(); // instantané si pool rempli
            SecureSession session = new SecureSession("pending:" + networkId, kp);
            SERVER_SESSIONS.put("pending:" + networkId, session);
            SecureMod.LOGGER.info("[SecureMod] Pending session created for {}", networkId);
            return session;
        } catch (Exception e) {
            throw new RuntimeException("Unable to create RSA session", e);
        }
    }

    public static void promoteSession(UUID networkId, String token) {
        String pendingKey = "pending:" + networkId;
        SecureSession session = SERVER_SESSIONS.remove(pendingKey);
        if (session != null) {
            SERVER_SESSIONS.put(token, session);
            UUID_TO_TOKEN.put(networkId, token);
            SecureMod.LOGGER.info("[SecureMod] Session promoted {} → token={}", networkId, token);
        } else {
            SecureMod.LOGGER.warn("[SecureMod] promoteSession: pending not found for {}", networkId);
        }
    }

    public static SecureSession getServerSession(UUID networkId) {
        String token = UUID_TO_TOKEN.get(networkId);
        if (token != null) return SERVER_SESSIONS.get(token);
        return SERVER_SESSIONS.get("pending:" + networkId);
    }

    public static SecureSession getServerSessionByToken(String token) {
        return SERVER_SESSIONS.get(token);
    }

    public static UUID getUUIDForToken(String token) {
        for (Map.Entry<UUID, String> entry : UUID_TO_TOKEN.entrySet()) {
            if (entry.getValue().equals(token)) return entry.getKey();
        }
        return null;
    }

    public static String getTokenForPlayer(UUID networkId) {
        return UUID_TO_TOKEN.get(networkId);
    }

    public static void removeServerSession(UUID networkId) {
        String token = UUID_TO_TOKEN.remove(networkId);
        if (token != null) {
            SERVER_SESSIONS.remove(token);
            SecureMod.LOGGER.info("[SecureMod] Session removed token={} ({})", token, networkId);
        } else {
            SERVER_SESSIONS.remove("pending:" + networkId);
            SecureMod.LOGGER.info("[SecureMod] Pending session removed for {}", networkId);
        }
    }

    // ─── Côté client ───────────────────────────────────────────────────────────

    public static SecureSession getOrCreateClientSession() {
        if (clientSession == null) {
            try {
                KeyPair kp = getKeyPair(); // instantané si pool rempli
                clientSession = new SecureSession("client", kp);
                SecureMod.LOGGER.info("[SecureMod] Client session created");
            } catch (Exception e) {
                throw new RuntimeException("Unable to create client session", e);
            }
        }
        return clientSession;
    }

    public static SecureSession getClientSession() {
        return clientSession;
    }

    public static void clearClientSession() {
        clientSession = null;
        SecureMod.LOGGER.info("[SecureMod] Client session cleared");
    }
}
