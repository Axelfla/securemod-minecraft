# SecureMod — Protocole RSA+AES pour Minecraft NeoForge 1.21.1

## 📦 Structure du projet

```
securemod/
├── build.gradle
├── settings.gradle
└── src/main/java/com/securemod/
    ├── SecureMod.java                        ← Point d'entrée du mod
    ├── crypto/
    │   ├── CryptoUtils.java                  ← RSA-2048, AES-256, HMAC-SHA256
    │   └── SecureSession.java                ← Machine à états du handshake
    ├── network/
    │   ├── Packets.java                      ← Tous les paquets (6 types)
    │   ├── SecurePacketHandler.java          ← Enregistrement NeoForge
    │   ├── SecureChannel.java                ← Registre des sessions actives
    │   └── handler/
    │       ├── ServerPacketHandler.java      ← Logique serveur (4 phases)
    │       └── ClientPacketHandler.java      ← Logique client (4 phases)
    ├── client/
    │   └── ClientEventHandler.java           ← Lance le handshake à la connexion
    ├── server/
    │   └── ServerEventHandler.java           ← Nettoyage sessions à la déconnexion
    └── common/
        └── SecureAPI.java                    ← API publique pour autres mods
```

## 🔐 Protocole implémenté

| Phase | Description | Crypto |
|-------|-------------|--------|
| 1 | Échange de clés publiques | RSA-2048 (X.509) |
| 2 | Authentification mutuelle | Challenge + Signature RSA SHA256 |
| 3 | Échange clé de session | AES-256 chiffré RSA/OAEP |
| 4 | Communication chiffrée | AES-256-CBC + HMAC-SHA256 |

## 🛠️ Compilation

### Prérequis
- **Java 21** (JDK, pas JRE)
- **Git** (pour télécharger les dépendances Gradle)
- Connexion internet (premier build uniquement)

### Étapes

```bash
# 1. Télécharger le MDK NeoForge 1.21.1
#    https://neoforged.net/ → MDK → 1.21.1

# 2. Remplacer src/ et les fichiers build par ce projet
#    (ou copier ce dossier dans le MDK)

# 3. Compiler
./gradlew build

# Le .jar est généré dans :
# build/libs/securemod-1.0.0.jar
```

### Installation rapide avec le MDK officiel

```bash
# Télécharger le MDK
wget https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.86/neoforge-21.1.86-mdk.zip
unzip neoforge-21.1.86-mdk.zip -d securemod-mdk
cd securemod-mdk

# Copier les sources de ce projet
cp -r /chemin/vers/securemod/src ./
cp /chemin/vers/securemod/build.gradle ./
cp /chemin/vers/securemod/settings.gradle ./

# Compiler
./gradlew build
```

### Premier build (génération de l'environnement)

```bash
# Génère l'environnement de développement (~5 min, télécharge ~1 Go)
./gradlew genEclipseRuns
# ou pour IntelliJ IDEA :
./gradlew genIntellijRuns
```

## 🚀 Utilisation via l'API

```java
// Envoyer un message chiffré du client vers le serveur
SecureAPI.sendToServer("MON_MOD:action:données");

// Envoyer depuis le serveur vers un joueur
SecureAPI.sendToPlayer(player, "MON_MOD:réponse:ok");

// Vérifier si le tunnel est actif
if (SecureAPI.isClientSecure()) { ... }
```

## ⚠️ Comportement de sécurité

- Si le mod est absent côté serveur ou client → connexion refusée
- Si l'authentification échoue → kick immédiat du joueur
- Si un HMAC est invalide → message rejeté + kick
- Si la version du protocole diffère → kick immédiat

## 📋 Version

- Minecraft : **1.21.1**
- NeoForge  : **21.1.86**
- Java      : **21**
