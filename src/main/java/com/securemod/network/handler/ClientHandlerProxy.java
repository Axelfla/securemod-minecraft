package com.securemod.network.handler;

import com.securemod.network.Packets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Proxy qui délègue aux handlers client UNIQUEMENT côté CLIENT.
 * Cette classe est chargée des deux côtés mais les méthodes
 * sont annotées @OnlyIn(Dist.CLIENT) pour éviter le crash serveur.
 */
public class ClientHandlerProxy {

    @OnlyIn(Dist.CLIENT)
    public static void handleHello(Packets.HelloPacket pkt, IPayloadContext ctx) {
        ClientPacketHandler.handleHello(pkt, ctx);
    }

    @OnlyIn(Dist.CLIENT)
    public static void handlePublicKey(Packets.PublicKeyPacket pkt, IPayloadContext ctx) {
        ClientPacketHandler.handlePublicKey(pkt, ctx);
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleChallenge(Packets.ChallengePacket pkt, IPayloadContext ctx) {
        ClientPacketHandler.handleChallenge(pkt, ctx);
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleSecureMessage(Packets.SecureMessagePacket pkt, IPayloadContext ctx) {
        ClientPacketHandler.handleSecureMessage(pkt, ctx);
    }
}
