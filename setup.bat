@echo off
setlocal enabledelayedexpansion
title SecureMod - Setup

echo ============================================
echo  SecureMod - Installation automatique
echo ============================================
echo.

:: ─── 1. Trouver JDK 21 ────────────────────────────────────────────────────
echo [1/3] Recherche de JDK 21...

set "JDK21="

:: Chercher dans Program Files
for /d %%D in ("%ProgramFiles%\Java\jdk-21*" "%ProgramFiles%\Eclipse Adoptium\jdk-21*" "%ProgramFiles%\Microsoft\jdk-21*" "%ProgramFiles%\Zulu\zulu-21*") do (
    if exist "%%D\bin\javac.exe" (
        if not defined JDK21 set "JDK21=%%D"
    )
)

:: Chercher aussi dans Program Files (x86)
for /d %%D in ("%ProgramFiles(x86)%\Java\jdk-21*") do (
    if exist "%%D\bin\javac.exe" (
        if not defined JDK21 set "JDK21=%%D"
    )
)

:: Vérifier la variable d'environnement JAVA_HOME si elle pointe sur JDK 21
if defined JAVA_HOME (
    "%JAVA_HOME%\bin\java.exe" -version 2>&1 | findstr /i "version \"21" > nul
    if !errorlevel! == 0 (
        if exist "%JAVA_HOME%\bin\javac.exe" set "JDK21=%JAVA_HOME%"
    )
)

if not defined JDK21 (
    echo.
    echo  ERREUR : JDK 21 introuvable sur cette machine.
    echo  Telecharge-le ici : https://adoptium.net/temurin/releases/?version=21
    echo  Puis relance setup.bat
    echo.
    pause
    exit /b 1
)

echo  JDK 21 trouve : %JDK21%

:: Ecrire le chemin dans gradle.properties
powershell -Command "(Get-Content 'gradle.properties') -replace 'org\.gradle\.java\.home=.*', 'org.gradle.java.home=%JDK21:\=/%' | Set-Content 'gradle.properties'"
echo  gradle.properties mis a jour.

:: ─── 2. Télécharger gradle-wrapper.jar ────────────────────────────────────
echo.
echo [2/3] Telechargement de gradle-wrapper.jar...

set "JAR_URL=https://raw.githubusercontent.com/gradle/gradle/v8.8.0/gradle/wrapper/gradle-wrapper.jar"
set "JAR_PATH=gradle\wrapper\gradle-wrapper.jar"

if exist "%JAR_PATH%" (
    echo  gradle-wrapper.jar deja present, skip.
) else (
    :: Essai avec PowerShell (disponible sur Windows 7+)
    powershell -Command "try { (New-Object Net.WebClient).DownloadFile('%JAR_URL%', '%JAR_PATH%'); Write-Host ' OK' } catch { Write-Host ' ECHEC PowerShell'; exit 1 }"
    if not exist "%JAR_PATH%" (
        :: Fallback : curl (disponible Windows 10+)
        curl -L -o "%JAR_PATH%" "%JAR_URL%" 2>nul
    )
    if not exist "%JAR_PATH%" (
        echo.
        echo  ERREUR : impossible de telecharger gradle-wrapper.jar
        echo  Verifie ta connexion internet et relance setup.bat
        echo.
        pause
        exit /b 1
    )
    echo  gradle-wrapper.jar telecharge avec succes.
)

:: ─── 3. Lancer le build ────────────────────────────────────────────────────
echo.
echo [3/3] Compilation du mod...
echo  (premiere fois : Gradle va telecharger NeoForge, ~5 min)
echo.

:: Nettoyer les anciens artifacts avant de builder
if exist build rmdir /s /q build

call gradlew.bat build
if %errorlevel% neq 0 (
    echo.
    echo  BUILD ECHOUE. Consulte le log ci-dessus.
    pause
    exit /b 1
)

echo.
echo ============================================
echo  BUILD REUSSI !
echo  Fichier : build\libs\securemod-1.0.0.jar
echo ============================================
pause
