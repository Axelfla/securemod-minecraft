package com.securemod.network;

import com.securemod.SecureMod;
import com.securemod.crypto.SecureSession;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * ✅ Point 3 : Watchdog de timeout pour le handshake sécurisé.
 *
 * Dès qu'un joueur commence un handshake, un timer de 5 secondes démarre.
 * Si le tunnel n'est pas établi dans ce délai → kick immédiat.
 *
 * Note : le watchdog utilise l'UUID réseau (identifiant de connexion réseau
 * valide même pour les joueurs crackés), pas le token de session.
 */
public class HandshakeTimeoutWatchdog {

    private static final long TIMEOUT_SECONDS = 30L;
    private static final String KICK_REASON = "[SecureMod] Mod required — download the SecureMod mod to join this server.";

    private static final ScheduledExecutorService SCHEDULER =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "securemod-timeout-watchdog");
            t.setDaemon(true);
            return t;
        });

    // UUID réseau → future annulable
    private static final ConcurrentHashMap<UUID, ScheduledFuture<?>> PENDING =
        new ConcurrentHashMap<>();

    /**
     * Démarre le timer de 5 secondes pour un joueur.
     * À appeler dès l'envoi du HELLO côté serveur.
     *
     * @param networkId UUID réseau du joueur
     */
    public static void startTimer(UUID networkId) {
        cancelTimer(networkId);

        ScheduledFuture<?> future = SCHEDULER.schedule(() -> {
            checkAndKick(networkId);
        }, TIMEOUT_SECONDS, TimeUnit.SECONDS);

        PENDING.put(networkId, future);
        SecureMod.LOGGER.debug("[Watchdog] 30s timer started for {}", networkId);
    }

    /**
     * Annule le timer (handshake réussi ou déconnexion propre).
     *
     * @param networkId UUID réseau du joueur
     */
    public static void cancelTimer(UUID networkId) {
        ScheduledFuture<?> future = PENDING.remove(networkId);
        if (future != null && !future.isDone()) {
            future.cancel(false);
            SecureMod.LOGGER.debug("[Watchdog] Timer cancelled for {} (handshake successful)", networkId);
        }
    }

    private static void checkAndKick(UUID networkId) {
        PENDING.remove(networkId);

        // SecureChannel.getServerSession gère les deux phases (pending et token)
        SecureSession session = SecureChannel.getServerSession(networkId);

        if (session == null || session.isSecure()) {
            SecureMod.LOGGER.debug("[Watchdog] Session {} already secured or absent", networkId);
            return;
        }

        String token = SecureChannel.getTokenForPlayer(networkId);
        SecureMod.LOGGER.warn("[Watchdog] ⏱ TIMEOUT {} (token={}) — {}ms (state: {})",
            networkId, token != null ? token : "pending",
            session.getAgeMs(), session.getState());

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(networkId);
            if (player != null) {
                SecureMod.LOGGER.warn("[Watchdog] Kicking {} for timeout", player.getName().getString());
                player.connection.disconnect(Component.literal(KICK_REASON));
            }
            SecureChannel.removeServerSession(networkId);
        });
    }

    public static void shutdown() {
        PENDING.values().forEach(f -> f.cancel(false));
        PENDING.clear();
        SCHEDULER.shutdownNow();
        SecureMod.LOGGER.info("[Watchdog] Shutdown — all timers cancelled");
    }

    public static int getPendingCount() {
        return PENDING.size();
    }
}
