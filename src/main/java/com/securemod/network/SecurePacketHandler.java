package com.securemod.network;

import com.securemod.SecureMod;
import com.securemod.network.handler.ClientHandlerProxy;
import com.securemod.network.handler.ServerPacketHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class SecurePacketHandler {

    public static final String PROTOCOL_VERSION = "1.0";

    public static void register(IEventBus modBus) {
        modBus.addListener(SecurePacketHandler::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar(SecureMod.MOD_ID)
                .versioned(PROTOCOL_VERSION);

        // S→C : HELLO — client handler only, never loaded server-side
        reg.playToClient(Packets.HelloPacket.TYPE, Packets.HelloPacket.CODEC,
            (pkt, ctx) -> ClientHandlerProxy.handleHello(pkt, ctx));

        // Bidirectional: PublicKey
        reg.playBidirectional(Packets.PublicKeyPacket.TYPE, Packets.PublicKeyPacket.CODEC,
            (pkt, ctx) -> {
                if (ctx.flow().isServerbound()) ServerPacketHandler.handlePublicKey(pkt, ctx);
                else                            ClientHandlerProxy.handlePublicKey(pkt, ctx);
            });

        // S→C: Challenge
        reg.playToClient(Packets.ChallengePacket.TYPE, Packets.ChallengePacket.CODEC,
            (pkt, ctx) -> ClientHandlerProxy.handleChallenge(pkt, ctx));

        // C→S: Signature
        reg.playToServer(Packets.SignaturePacket.TYPE, Packets.SignaturePacket.CODEC,
            ServerPacketHandler::handleSignature);

        // C→S: SessionKey
        reg.playToServer(Packets.SessionKeyPacket.TYPE, Packets.SessionKeyPacket.CODEC,
            ServerPacketHandler::handleSessionKey);

        // Bidirectional: SecureMessage (Phase 4)
        reg.playBidirectional(Packets.SecureMessagePacket.TYPE, Packets.SecureMessagePacket.CODEC,
            (pkt, ctx) -> {
                if (ctx.flow().isServerbound()) ServerPacketHandler.handleSecureMessage(pkt, ctx);
                else                            ClientHandlerProxy.handleSecureMessage(pkt, ctx);
            });

        SecureMod.LOGGER.info("[SecureMod] 6 packets registered");
    }
}
