package com.securemod.crypto;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Arrays;

/**
 * Représente l'état d'une session sécurisée pour un joueur/connexion.
 *
 * La paire RSA est maintenant injectée depuis le pool pré-généré
 * (SecureChannel) pour éviter la latence à la connexion.
 */
public class SecureSession {

    public static final long HANDSHAKE_TIMEOUT_MS = 30_000L; // 30s — généreux pour les serveurs lents

    public enum State {
        INIT, HELLO_SENT, KEYS_EXCHANGED, CHALLENGE_SENT,
        CHALLENGE_VERIFIED, AUTHENTICATED, SECURE, FAILED
    }

    private final String sessionId;
    private final KeyPair localKeyPair;
    private PublicKey remotePublicKey;
    private byte[] sentChallenge;
    private byte[] receivedChallenge;
    private SecretKey sessionKey;
    private State state = State.INIT;
    private final long createdAt = System.currentTimeMillis();

    /** Token de 10 chars dérivé de la clé publique distante (calculé en Phase 1). */
    private String token = null;

    /**
     * Constructeur principal : reçoit une KeyPair pré-générée depuis le pool.
     */
    public SecureSession(String sessionId, KeyPair keyPair) {
        this.sessionId = sessionId;
        this.localKeyPair = keyPair;
    }

    /**
     * Constructeur de fallback : génère la KeyPair à la volée (lent).
     */
    public SecureSession(String sessionId) throws Exception {
        this.sessionId = sessionId;
        this.localKeyPair = CryptoUtils.generateRSAKeyPair();
    }

    public boolean isTimedOut() {
        if (state == State.SECURE || state == State.FAILED) return false;
        return (System.currentTimeMillis() - createdAt) > HANDSHAKE_TIMEOUT_MS;
    }

    public long getAgeMs() {
        return System.currentTimeMillis() - createdAt;
    }

    // ─── Accesseurs ────────────────────────────────────────────────────────────

    public String getSessionId()          { return sessionId; }
    public State  getState()              { return state; }
    public void   setState(State s)       { this.state = s; }
    public KeyPair getLocalKeyPair()      { return localKeyPair; }
    public PublicKey getRemotePublicKey() { return remotePublicKey; }
    public byte[] getSentChallenge()      { return sentChallenge; }
    public byte[] getReceivedChallenge()  { return receivedChallenge; }
    public SecretKey getSessionKey()      { return sessionKey; }
    public String getToken()              { return token; }

    /**
     * Côté client : stocke le token assigné par le serveur
     * (calculé depuis notre clé publique RSA, reçu dans SECURE_CHANNEL_READY).
     */
    public void setClientToken(String t)  { this.token = t; }

    public byte[] getLocalPublicKeyBytes() {
        return localKeyPair.getPublic().getEncoded();
    }

    // ─── Phase 1 ───────────────────────────────────────────────────────────────

    public void setRemotePublicKey(byte[] encoded) throws Exception {
        this.remotePublicKey = CryptoUtils.publicKeyFromBytes(encoded);
        this.token = CryptoUtils.deriveToken(encoded);
        this.state = State.KEYS_EXCHANGED;
    }

    // ─── Phase 2 ───────────────────────────────────────────────────────────────

    public byte[] prepareChallenge() throws Exception {
        this.sentChallenge = CryptoUtils.generateNonce();
        this.state = State.CHALLENGE_SENT;
        return CryptoUtils.encryptWithPublicKey(sentChallenge, remotePublicKey);
    }

    public byte[] answerChallenge(byte[] encryptedChallenge) throws Exception {
        this.receivedChallenge = CryptoUtils.decryptWithPrivateKey(
                encryptedChallenge, localKeyPair.getPrivate());
        return CryptoUtils.sign(receivedChallenge, localKeyPair.getPrivate());
    }

    public boolean verifyChallenge(byte[] signature) throws Exception {
        if (sentChallenge == null || remotePublicKey == null) return false;
        boolean ok = CryptoUtils.verify(sentChallenge, signature, remotePublicKey);
        if (ok) this.state = State.CHALLENGE_VERIFIED;
        else    this.state = State.FAILED;
        return ok;
    }

    // ─── Phase 3 ───────────────────────────────────────────────────────────────

    public byte[] generateAndExportSessionKey() throws Exception {
        this.sessionKey = CryptoUtils.generateAESKey();
        byte[] exported = CryptoUtils.encryptWithPublicKey(
                sessionKey.getEncoded(), remotePublicKey);
        this.state = State.SECURE;
        return exported;
    }

    public void importSessionKey(byte[] encryptedKey) throws Exception {
        byte[] raw = CryptoUtils.decryptWithPrivateKey(
                encryptedKey, localKeyPair.getPrivate());
        this.sessionKey = CryptoUtils.aesKeyFromBytes(raw);
        this.state = State.SECURE;
    }

    // ─── Phase 4 ───────────────────────────────────────────────────────────────

    public byte[] encryptMessage(byte[] plaintext) throws Exception {
        assertSecure();
        byte[] encrypted = CryptoUtils.encryptAES(plaintext, sessionKey);
        byte[] mac       = CryptoUtils.hmac(encrypted, sessionKey);
        byte[] out = new byte[CryptoUtils.HMAC_SIZE + encrypted.length];
        System.arraycopy(mac,       0, out, 0,                      CryptoUtils.HMAC_SIZE);
        System.arraycopy(encrypted, 0, out, CryptoUtils.HMAC_SIZE, encrypted.length);
        return out;
    }

    public byte[] decryptMessage(byte[] payload) throws Exception {
        assertSecure();
        if (payload.length < CryptoUtils.HMAC_SIZE)
            throw new SecurityException("Payload too short");
        byte[] mac       = Arrays.copyOfRange(payload, 0, CryptoUtils.HMAC_SIZE);
        byte[] encrypted = Arrays.copyOfRange(payload, CryptoUtils.HMAC_SIZE, payload.length);
        if (!CryptoUtils.verifyHmac(encrypted, mac, sessionKey))
            throw new SecurityException("Invalid HMAC — message tampered");
        return CryptoUtils.decryptAES(encrypted, sessionKey);
    }

    public boolean isSecure() { return state == State.SECURE; }

    private void assertSecure() {
        if (!isSecure())
            throw new IllegalStateException("Session not secured (state: " + state + ")");
    }
}
