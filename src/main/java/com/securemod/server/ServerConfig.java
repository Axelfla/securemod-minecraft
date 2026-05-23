package com.securemod.server;

import com.securemod.SecureMod;
import java.io.*;
import java.nio.file.*;

/**
 * Simple config: access password for the secure server.
 * File: config/securemod.properties
 */
public class ServerConfig {

    private static String accessPassword = null;
    private static final String CONFIG_FILE = "config/securemod.properties";

    public static void load() {
        Path path = Paths.get(CONFIG_FILE);
        try {
            if (!Files.exists(path)) {
                // Créer le fichier par défaut
                Files.createDirectories(path.getParent());
                Files.writeString(path,
                    "# Mot de passe pour se connecter au serveur sécurisé\n" +
                    "# Changez cette valeur et communiquez-la à vos joueurs\n" +
                    "access_password=changeme123\n");
                SecureMod.LOGGER.info("[ServerConfig] File created: {} — change the password!", CONFIG_FILE);
            }

            java.util.Properties props = new java.util.Properties();
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            }
            accessPassword = props.getProperty("access_password", "changeme123").trim();
            SecureMod.LOGGER.info("[ServerConfig] Password loaded (length: {})", accessPassword.length());

        } catch (IOException e) {
            SecureMod.LOGGER.error("[ServerConfig] Config read error: {}", e.getMessage());
            accessPassword = "changeme123";
        }
    }

    public static boolean checkPassword(String input) {
        if (accessPassword == null) load();
        return accessPassword.equals(input);
    }

    public static String getPassword() {
        if (accessPassword == null) load();
        return accessPassword;
    }
}
