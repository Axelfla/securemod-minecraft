package com.securemod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Tous les paquets du protocole sécurisé, un par étape.
 *
 * Phase 1 : HelloPacket, PublicKeyPacket
 * Phase 2 : ChallengePacket, SignaturePacket
 * Phase 3 : SessionKeyPacket
 * Phase 4 : SecureMessagePacket
 *
 * API NeoForge 1.21.11 : ResourceLocation via new ResourceLocation(namespace, path)
 */
public final class Packets {

    // ─── Phase 1 ───────────────────────────────────────────────────────────────

    public record HelloPacket(String protocolVersion) implements CustomPacketPayload {
        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("securemod", "hello");
        public static final Type<HelloPacket> TYPE = new Type<>(ID);

        public static final StreamCodec<FriendlyByteBuf, HelloPacket> CODEC =
            StreamCodec.of(
                (buf, pkt) -> buf.writeUtf(pkt.protocolVersion),
                buf        -> new HelloPacket(buf.readUtf(32))
            );

        @Override public Type<HelloPacket> type() { return TYPE; }
    }

    public record PublicKeyPacket(byte[] publicKeyBytes) implements CustomPacketPayload {
        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("securemod", "pubkey");
        public static final Type<PublicKeyPacket> TYPE = new Type<>(ID);

        public static final StreamCodec<FriendlyByteBuf, PublicKeyPacket> CODEC =
            StreamCodec.of(
                (buf, pkt) -> { buf.writeInt(pkt.publicKeyBytes.length); buf.writeBytes(pkt.publicKeyBytes); },
                buf        -> { int len = buf.readInt(); byte[] b = new byte[len]; buf.readBytes(b); return new PublicKeyPacket(b); }
            );

        @Override public Type<PublicKeyPacket> type() { return TYPE; }
    }

    // ─── Phase 2 ───────────────────────────────────────────────────────────────

    public record ChallengePacket(byte[] encryptedChallenge) implements CustomPacketPayload {
        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("securemod", "challenge");
        public static final Type<ChallengePacket> TYPE = new Type<>(ID);

        public static final StreamCodec<FriendlyByteBuf, ChallengePacket> CODEC =
            StreamCodec.of(
                (buf, pkt) -> { buf.writeInt(pkt.encryptedChallenge.length); buf.writeBytes(pkt.encryptedChallenge); },
                buf        -> { int len = buf.readInt(); byte[] b = new byte[len]; buf.readBytes(b); return new ChallengePacket(b); }
            );

        @Override public Type<ChallengePacket> type() { return TYPE; }
    }

    public record SignaturePacket(byte[] signature) implements CustomPacketPayload {
        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("securemod", "signature");
        public static final Type<SignaturePacket> TYPE = new Type<>(ID);

        public static final StreamCodec<FriendlyByteBuf, SignaturePacket> CODEC =
            StreamCodec.of(
                (buf, pkt) -> { buf.writeInt(pkt.signature.length); buf.writeBytes(pkt.signature); },
                buf        -> { int len = buf.readInt(); byte[] b = new byte[len]; buf.readBytes(b); return new SignaturePacket(b); }
            );

        @Override public Type<SignaturePacket> type() { return TYPE; }
    }

    // ─── Phase 3 ───────────────────────────────────────────────────────────────

    public record SessionKeyPacket(byte[] encryptedSessionKey, String password) implements CustomPacketPayload {
        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("securemod", "sessionkey");
        public static final Type<SessionKeyPacket> TYPE = new Type<>(ID);

        public static final StreamCodec<FriendlyByteBuf, SessionKeyPacket> CODEC =
            StreamCodec.of(
                (buf, pkt) -> {
                    buf.writeInt(pkt.encryptedSessionKey.length);
                    buf.writeBytes(pkt.encryptedSessionKey);
                    buf.writeUtf(pkt.password != null ? pkt.password : "");
                },
                buf -> {
                    int len = buf.readInt(); byte[] b = new byte[len]; buf.readBytes(b);
                    String pw = buf.readUtf(256);
                    return new SessionKeyPacket(b, pw);
                }
            );

        @Override public Type<SessionKeyPacket> type() { return TYPE; }
    }

    // ─── Phase 4 ───────────────────────────────────────────────────────────────

    public record SecureMessagePacket(byte[] payload) implements CustomPacketPayload {
        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("securemod", "securemsg");
        public static final Type<SecureMessagePacket> TYPE = new Type<>(ID);

        public static final StreamCodec<FriendlyByteBuf, SecureMessagePacket> CODEC =
            StreamCodec.of(
                (buf, pkt) -> { buf.writeInt(pkt.payload.length); buf.writeBytes(pkt.payload); },
                buf        -> { int len = buf.readInt(); byte[] b = new byte[len]; buf.readBytes(b); return new SecureMessagePacket(b); }
            );

        @Override public Type<SecureMessagePacket> type() { return TYPE; }
    }

    private Packets() {}
}
