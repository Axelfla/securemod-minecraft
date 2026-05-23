package com.securemod.crypto;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**
 * Utilitaires cryptographiques pour le protocole sécurisé.
 *
 * Phase 1 : Échange de clés publiques RSA-2048
 * Phase 2 : Authentification mutuelle par challenge/signature
 * Phase 3 : Transmission de la clé AES-256 chiffrée RSA
 * Phase 4 : Communication chiffrée AES-256-CBC + HMAC-SHA256
 */
public class CryptoUtils {

    // ─── Constantes ────────────────────────────────────────────────────────────
    public static final int RSA_KEY_SIZE    = 2048;
    public static final int AES_KEY_SIZE    = 256;
    public static final int NONCE_SIZE      = 32;   // bytes
    public static final int AES_BLOCK_SIZE  = 16;   // bytes (IV)
    public static final int HMAC_SIZE       = 32;   // SHA-256

    // ─── Phase 1 : Génération de paire RSA ────────────────────────────────────

    /**
     * Génère une nouvelle paire de clés RSA-2048.
     */
    public static KeyPair generateRSAKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(RSA_KEY_SIZE, new SecureRandom());
        return kpg.generateKeyPair();
    }

    /**
     * Reconstitue une PublicKey RSA depuis ses octets bruts (format X.509).
     */
    public static PublicKey publicKeyFromBytes(byte[] encoded) throws Exception {
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(new X509EncodedKeySpec(encoded));
    }

    // ─── Phase 2 : Challenge / Signature ──────────────────────────────────────

    /**
     * Génère un nonce aléatoire de NONCE_SIZE octets.
     */
    public static byte[] generateNonce() {
        byte[] nonce = new byte[NONCE_SIZE];
        new SecureRandom().nextBytes(nonce);
        return nonce;
    }

    /**
     * Chiffre un nonce avec la clé publique RSA du destinataire.
     * Utilise RSA/ECB/OAEPWithSHA-256AndMGF1Padding.
     */
    public static byte[] encryptWithPublicKey(byte[] data, PublicKey pubKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, pubKey);
        return cipher.doFinal(data);
    }

    /**
     * Déchiffre avec la clé privée RSA.
     */
    public static byte[] decryptWithPrivateKey(byte[] data, PrivateKey privKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privKey);
        return cipher.doFinal(data);
    }

    /**
     * Signe des données avec la clé privée RSA (SHA256withRSA).
     */
    public static byte[] sign(byte[] data, PrivateKey privKey) throws Exception {
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privKey);
        sig.update(data);
        return sig.sign();
    }

    /**
     * Vérifie une signature RSA avec la clé publique.
     * @return true si la signature est valide
     */
    public static boolean verify(byte[] data, byte[] signature, PublicKey pubKey) throws Exception {
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(pubKey);
        sig.update(data);
        return sig.verify(signature);
    }

    // ─── Phase 3 : Génération et échange de clé AES ───────────────────────────

    /**
     * Génère une clé AES-256 aléatoire.
     */
    public static SecretKey generateAESKey() throws NoSuchAlgorithmException {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(AES_KEY_SIZE, new SecureRandom());
        return kg.generateKey();
    }

    /**
     * Reconstitue une SecretKey AES depuis des octets bruts.
     */
    public static SecretKey aesKeyFromBytes(byte[] raw) {
        return new SecretKeySpec(raw, "AES");
    }

    // ─── Phase 4 : Chiffrement AES-256-CBC ────────────────────────────────────

    /**
     * Chiffre des données en AES-256-CBC.
     * Préfixe le résultat par l'IV aléatoire (16 octets).
     *
     * @return IV (16 bytes) + ciphertext
     */
    public static byte[] encryptAES(byte[] data, SecretKey key) throws Exception {
        byte[] iv = new byte[AES_BLOCK_SIZE];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(data);

        // Concat IV + ciphertext
        byte[] result = new byte[AES_BLOCK_SIZE + encrypted.length];
        System.arraycopy(iv, 0, result, 0, AES_BLOCK_SIZE);
        System.arraycopy(encrypted, 0, result, AES_BLOCK_SIZE, encrypted.length);
        return result;
    }

    /**
     * Déchiffre un payload AES-256-CBC (IV préfixé).
     */
    public static byte[] decryptAES(byte[] ivAndData, SecretKey key) throws Exception {
        byte[] iv   = Arrays.copyOfRange(ivAndData, 0, AES_BLOCK_SIZE);
        byte[] data = Arrays.copyOfRange(ivAndData, AES_BLOCK_SIZE, ivAndData.length);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        return cipher.doFinal(data);
    }

    // ─── HMAC-SHA256 (intégrité des messages, Phase 4) ────────────────────────

    /**
     * Calcule un HMAC-SHA256 sur les données avec la clé AES comme clé HMAC.
     */
    public static byte[] hmac(byte[] data, SecretKey key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(key);
        return mac.doFinal(data);
    }

    /**
     * Vérifie un HMAC-SHA256 (comparaison en temps constant).
     */
    public static boolean verifyHmac(byte[] data, byte[] expectedHmac, SecretKey key) throws Exception {
        byte[] computed = hmac(data, key);
        return MessageDigest.isEqual(computed, expectedHmac);
    }

    // ─── Token de session (identifiant court compatible crack) ────────────────

    /**
     * Caractères base62 (pas de caractères ambigus comme 0/O/1/l).
     */
    private static final String BASE62 = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    /**
     * Dérive un token de session de 10 caractères à partir des octets
     * de la clé publique RSA du client.
     *
     * Algorithme : SHA-256(pubKeyBytes) → interprété comme entier positif
     *              → encodé en base62 → tronqué à 10 caractères.
     *
     * Propriétés :
     *  - Déterministe : même clé → même token
     *  - Indépendant de l'UUID Mojang → compatible joueurs crackés
     *  - 56^10 combinaisons possibles (collision négligeable sur un serveur)
     *
     * @param pubKeyBytes clé publique RSA encodée X.509
     * @return token alphanumérique de 10 caractères
     */
    public static String deriveToken(byte[] pubKeyBytes) throws NoSuchAlgorithmException {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(pubKeyBytes);

        // Convertir en entier non-signé (BigInteger positif)
        java.math.BigInteger n = new java.math.BigInteger(1, hash);
        java.math.BigInteger base = java.math.BigInteger.valueOf(BASE62.length());

        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            java.math.BigInteger[] divRem = n.divideAndRemainder(base);
            sb.append(BASE62.charAt(divRem[1].intValue()));
            n = divRem[0];
        }
        // Ordre naturel (LSB en premier → retourner pour cohérence)
        return sb.reverse().toString();
    }
}
