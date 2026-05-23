package com.securemod.server;

import com.securemod.SecureMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Reçoit les payloads déchiffrés et exécute les actions côté serveur.
 *
 * Format : "TYPE:données"
 */
public class SecureServerDispatcher {

    public static void dispatch(ServerPlayer player, String payload) {
        if (payload == null || payload.isEmpty()) return;

        int sep = payload.indexOf(':');
        if (sep < 0) {
            SecureMod.LOGGER.warn("[Dispatcher] Invalid payload from {}: {}", player.getName().getString(), payload);
            return;
        }

        String type = payload.substring(0, sep);
        String data = payload.substring(sep + 1);

        try {
            switch (type) {
                case "CHAT"            -> handleChat(player, data);
                case "CMD"             -> handleCommand(player, data);
                case "MOVE"            -> handleMove(player, data);
                case "INTERACT_BLOCK"  -> handleInteractBlock(player, data);
                case "INTERACT_ENTITY" -> handleInteractEntity(player, data);
                case "INTERACT_EMPTY"  -> { /* pas d'action serveur requise */ }
                default -> SecureMod.LOGGER.warn("[Dispatcher] Unknown type '{}' from {}", type, player.getName().getString());
            }
        } catch (Exception e) {
            SecureMod.LOGGER.error("[Dispatcher] Dispatch error {} from {}: {}",
                type, player.getName().getString(), e.getMessage());
        }
    }

    // ─── Chat ──────────────────────────────────────────────────────────────────

    private static void handleChat(ServerPlayer player, String message) {
        SecureMod.LOGGER.info("[Dispatcher] Encrypted chat from {}: {}", player.getName().getString(), message);

        // Broadcaster le message à tous les joueurs comme un chat normal
        MinecraftServer server = player.getServer();
        if (server == null) return;

        Component chatMsg = Component.literal("<" + player.getName().getString() + "> " + message);
        server.getPlayerList().broadcastSystemMessage(chatMsg, false);
    }

    // ─── Commandes ─────────────────────────────────────────────────────────────

    private static void handleCommand(ServerPlayer player, String command) {
        SecureMod.LOGGER.info("[Dispatcher] Encrypted command from {}: /{}", player.getName().getString(), command);

        MinecraftServer server = player.getServer();
        if (server == null) return;

        // Exécuter la commande avec les permissions du joueur
        CommandSourceStack source = player.createCommandSourceStack();
        server.getCommands().performPrefixedCommand(source, command);
    }

    // ─── Mouvements ────────────────────────────────────────────────────────────

    private static void handleMove(ServerPlayer player, String data) {
        // Format : x,y,z,yaw,pitch
        String[] parts = data.split(",");
        if (parts.length != 5) return;

        double x     = Double.parseDouble(parts[0]);
        double y     = Double.parseDouble(parts[1]);
        double z     = Double.parseDouble(parts[2]);
        float  yaw   = Float.parseFloat(parts[3]);
        float  pitch = Float.parseFloat(parts[4]);

        // Validation anti-cheat basique : limiter la distance max par tick
        double dist = player.position().distanceTo(new Vec3(x, y, z));
        if (dist > 20.0) {
            SecureMod.LOGGER.warn("[Dispatcher] Suspicious movement from {} : {}m", player.getName().getString(), dist);
            return;
        }

        player.moveTo(x, y, z, yaw, pitch);
    }

    // ─── Interaction blocs ─────────────────────────────────────────────────────

    private static void handleInteractBlock(ServerPlayer player, String data) {
        // Format : x,y,z,face,hand
        String[] parts = data.split(",");
        if (parts.length != 5) return;

        int bx   = Integer.parseInt(parts[0]);
        int by   = Integer.parseInt(parts[1]);
        int bz   = Integer.parseInt(parts[2]);
        String faceName = parts[3];
        InteractionHand hand = "MAIN".equals(parts[4]) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;

        BlockPos pos = new BlockPos(bx, by, bz);
        Direction face = Direction.byName(faceName);
        if (face == null) face = Direction.UP;

        // Distance maximale d'interaction : 6 blocs
        if (player.blockPosition().distSqr(pos) > 36) {
            SecureMod.LOGGER.warn("[Dispatcher] Block interaction too far from {}", player.getName().getString());
            return;
        }

        Vec3 hitVec = new Vec3(bx + 0.5, by + 0.5, bz + 0.5);
        BlockHitResult hitResult = new BlockHitResult(hitVec, face, pos, false);

        player.getServer().execute(() -> {
            // useItemOn remplace use() en 1.21.1
            net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
            stack.useOn(new net.minecraft.world.item.context.UseOnContext(
                player.serverLevel(), player, hand, stack, hitResult));
        });

        SecureMod.LOGGER.debug("[Dispatcher] Block interaction ({},{},{}) by {}", bx, by, bz, player.getName().getString());
    }

    // ─── Interaction entités ───────────────────────────────────────────────────

    private static void handleInteractEntity(ServerPlayer player, String data) {
        String[] parts = data.split(",");
        if (parts.length != 2) return;

        int entityId = Integer.parseInt(parts[0]);
        InteractionHand hand = "MAIN".equals(parts[1]) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;

        player.getServer().execute(() -> {
            var entity = player.level().getEntity(entityId);
            if (entity == null) return;

            // Distance maximale : 6 blocs
            if (player.distanceToSqr(entity) > 36) {
                SecureMod.LOGGER.warn("[Dispatcher] Entity interaction too far from {}", player.getName().getString());
                return;
            }

            entity.interact(player, hand);
            SecureMod.LOGGER.debug("[Dispatcher] Entity interaction {} by {}", entityId, player.getName().getString());
        });
    }
}
