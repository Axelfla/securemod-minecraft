#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# setup.sh — Installation automatique de SecureMod (NeoForge 1.21.1)
# Télécharge gradle-wrapper.jar si absent, puis lance la compilation.
# ─────────────────────────────────────────────────────────────────────────────

set -e

WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v8.8.0/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL_ALT="https://github.com/gradle/gradle/raw/v8.8.0/gradle/wrapper/gradle-wrapper.jar"

echo "╔══════════════════════════════════════════════╗"
echo "║         SecureMod — Setup & Build            ║"
echo "╚══════════════════════════════════════════════╝"
echo ""

# ── 1. Vérifier Java 21 ───────────────────────────────────────────────────────
echo "▶ Vérification de Java..."
if ! command -v java &>/dev/null; then
    echo "  ❌ Java introuvable. Installe le JDK 21 :"
    echo "     https://adoptium.net/temurin/releases/?version=21"
    exit 1
fi
JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
echo "  Java version majeure détectée : $JAVA_VER"
if [ "$JAVA_VER" -lt 21 ]; then
    echo "  ❌ Java 21+ requis (trouvé : $JAVA_VER)"
    exit 1
fi
echo "  ✅ Java $JAVA_VER OK"
echo ""

# ── 2. Télécharger gradle-wrapper.jar si absent ───────────────────────────────
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "▶ gradle-wrapper.jar absent — téléchargement..."
    mkdir -p gradle/wrapper

    if command -v curl &>/dev/null; then
        curl -fsSL "$WRAPPER_URL" -o "$WRAPPER_JAR" || \
        curl -fsSL "$WRAPPER_URL_ALT" -o "$WRAPPER_JAR"
    elif command -v wget &>/dev/null; then
        wget -q "$WRAPPER_URL" -O "$WRAPPER_JAR" || \
        wget -q "$WRAPPER_URL_ALT" -O "$WRAPPER_JAR"
    else
        echo "  ❌ curl et wget introuvables."
        echo "     Télécharge manuellement :"
        echo "     $WRAPPER_URL"
        echo "     → gradle/wrapper/gradle-wrapper.jar"
        exit 1
    fi
    echo "  ✅ gradle-wrapper.jar téléchargé"
else
    echo "▶ gradle-wrapper.jar déjà présent ✅"
fi
echo ""

# ── 3. Rendre gradlew exécutable ──────────────────────────────────────────────
chmod +x gradlew
echo "▶ gradlew rendu exécutable ✅"
echo ""

# ── 4. Lancer la compilation ──────────────────────────────────────────────────
echo "▶ Compilation en cours... (première fois : ~5 min, télécharge ~1 Go)"
echo "  (NeoForge MDK + dépendances Minecraft)"
echo ""
./gradlew build

echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║  ✅ Compilation réussie !                    ║"
echo "║  📦 build/libs/securemod-1.0.0.jar           ║"
echo "║                                              ║"
echo "║  → Copie le .jar dans ton dossier mods/      ║"
echo "╚══════════════════════════════════════════════╝"
