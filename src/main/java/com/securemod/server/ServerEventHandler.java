package com.securemod.server;

import com.securemod.SecureMod;
import com.securemod.crypto.SecureSession;
import com.securemod.network.HandshakeTimeoutWatchdog;
import com.securemod.network.Packets;
import com.securemod.network.SecureChannel;
import com.securemod.socket.SecureSocketServer;
import com.securemod.server.ServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = SecureMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class ServerEventHandler {

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerConfig.load();
        SecureSocketServer.start(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        SecureMod.LOGGER.info("[Serveur] {} connected — starting handshake", player.getName().getString());

        SecureSession session = SecureChannel.createPendingSession(player.getUUID());
        HandshakeTimeoutWatchdog.startTimer(player.getUUID());

        try {
            PacketDistributor.sendToPlayer(player, new Packets.HelloPacket(PROTOCOL_VERSION));
            PacketDistributor.sendToPlayer(player, new Packets.PublicKeyPacket(session.getLocalPublicKeyBytes()));
            SecureMod.LOGGER.info("[Serveur] HELLO + PublicKey sent to {}", player.getName().getString());
        } catch (Exception e) {
            SecureMod.LOGGER.error("[Serveur] Handshake initiation error: {}", e.getMessage());
            HandshakeTimeoutWatchdog.cancelTimer(player.getUUID());
            SecureChannel.removeServerSession(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SecureMod.LOGGER.info("[Serveur] {} disconnected — cleaning session", player.getName().getString());
        HandshakeTimeoutWatchdog.cancelTimer(player.getUUID());
        String token = SecureChannel.getTokenForPlayer(player.getUUID());
        if (token != null) SecureSocketServer.disconnectPlayer(token);
        SecureChannel.removeServerSession(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SecureMod.LOGGER.info("[Serveur] Stopping — closing sockets");
        HandshakeTimeoutWatchdog.shutdown();
        SecureSocketServer.stop();
    }

    public static final String PROTOCOL_VERSION = "SECUREMOD_V1";
}
