package com.securemod.client.screen;

/**
 * Temporary storage of the password entered by the player.
 * Cleared after verification during handshake.
 */
public class PendingTokenStorage {

    private static String pendingToken = null;

    public static void setToken(String token) {
        pendingToken = token;
    }

    public static String getToken() {
        return pendingToken;
    }

    public static boolean hasToken() {
        return pendingToken != null && !pendingToken.isEmpty();
    }

    public static void clear() {
        pendingToken = null;
    }
}
