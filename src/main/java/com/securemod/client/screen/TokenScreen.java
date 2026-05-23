package com.securemod.client.screen;

import com.securemod.SecureMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

/**
 * Écran de saisie du token avant connexion sécurisée.
 * Apparaît quand le joueur clique "Connexion Normale" (sans token)
 * ou directement depuis le bouton "🔒 Secure Connection".
 */
public class TokenScreen extends Screen {

    private final Screen parent;
    private final ServerData serverData;

    private EditBox tokenField;
    private Button connectButton;
    private String errorMessage = "";

    public TokenScreen(Screen parent, ServerData serverData) {
        super(Component.literal("🔒 Secure Connection"));
        this.parent = parent;
        this.serverData = serverData;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Champ token
        tokenField = new EditBox(this.font, centerX - 150, centerY - 20, 300, 20,
            Component.literal("Access password"));
        tokenField.setMaxLength(64);
        tokenField.setHint(Component.literal("Enter your password..."));
        tokenField.setResponder(text -> {
            errorMessage = "";
            updateConnectButton();
        });
        this.addRenderableWidget(tokenField);

        // Bouton Connexion
        connectButton = Button.builder(
            Component.literal("🔒 Connect"),
            btn -> attemptConnect()
        ).bounds(centerX - 155, centerY + 10, 150, 20).build();
        connectButton.active = false;
        this.addRenderableWidget(connectButton);

        // Bouton Retour
        this.addRenderableWidget(Button.builder(
            Component.literal("Cancel"),
            btn -> this.minecraft.setScreen(parent)
        ).bounds(centerX + 5, centerY + 10, 150, 20).build());

        this.setInitialFocus(tokenField);
    }

    private void updateConnectButton() {
        connectButton.active = tokenField != null && !tokenField.getValue().trim().isEmpty();
    }

    private void attemptConnect() {
        String token = tokenField.getValue().trim();
        if (token.isEmpty()) {
            errorMessage = "§cPassword cannot be empty!";
            return;
        }

        // Stocker le token pour la session — sera vérifié lors du handshake
        PendingTokenStorage.setToken(token);
        SecureMod.LOGGER.info("[TokenScreen] Password saved, connecting to {}...", serverData.ip);

        // Lancer la connexion au serveur
        this.minecraft.setScreen(null);
        this.minecraft.getSoundManager().stop();
        try {
            net.minecraft.client.multiplayer.resolver.ServerAddress address =
                net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(serverData.ip);
            net.minecraft.client.multiplayer.ServerStatusPinger pinger =
                new net.minecraft.client.multiplayer.ServerStatusPinger();
            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                parent, this.minecraft, address, serverData, false, null);
        } catch (Exception e) {
            SecureMod.LOGGER.error("[TokenScreen] Connection error: {}", e.getMessage());
            errorMessage = "§cError: " + e.getMessage();
            this.minecraft.setScreen(this);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Panneau central semi-transparent
        graphics.fill(centerX - 170, centerY - 60, centerX + 170, centerY + 40, 0xAA000000);

        // Titre
        graphics.drawCenteredString(this.font,
            Component.literal("§6🔒 Secure Connection"), centerX, centerY - 50, 0xFFFFFF);

        // Serveur cible
        graphics.drawCenteredString(this.font,
            Component.literal("§7Server: §f" + serverData.ip), centerX, centerY - 36, 0xFFFFFF);

        // Label champ
        graphics.drawString(this.font,
            Component.literal("§7Access password :"), centerX - 150, centerY - 32, 0xFFFFFF);

        // Champ token
        tokenField.render(graphics, mouseX, mouseY, partialTick);

        // Message d'erreur
        if (!errorMessage.isEmpty()) {
            graphics.drawCenteredString(this.font,
                Component.literal(errorMessage), centerX, centerY + 35, 0xFF5555);
        }

        // Info bas
        graphics.drawCenteredString(this.font,
            Component.literal("§8The password is provided by the server administrator"),
            centerX, centerY + 55, 0xAAAAAA);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter
            if (connectButton.active) attemptConnect();
            return true;
        }
        if (keyCode == 256) { // Escape
            this.minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
