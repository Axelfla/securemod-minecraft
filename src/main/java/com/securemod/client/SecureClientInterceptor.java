package com.securemod.client;

import com.securemod.SecureMod;
import com.securemod.common.SecureAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Intercepte mouvements et interactions côté client.
 * Chat et commandes sont interceptés via MixinClientPacketListener.
 */
@EventBusSubscriber(modid = SecureMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class SecureClientInterceptor {

    // ─── Mouvements (tick joueur) ───────────────────────────────────────────────

    private static double lastX = Double.NaN;
    private static double lastZ = Double.NaN;
    private static float  lastYaw   = Float.NaN;
    private static float  lastPitch = Float.NaN;
    private static int    moveTick  = 0;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || event.getEntity() != mc.player) return;
        if (!SecureAPI.isClientSecure()) return;

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        float yaw   = mc.player.getYRot();
        float pitch = mc.player.getXRot();

        moveTick++;
        boolean posChanged = (x != lastX || z != lastZ);
        boolean rotChanged = (Math.abs(yaw - lastYaw) > 0.5f || Math.abs(pitch - lastPitch) > 0.5f);

        if ((posChanged || rotChanged) && moveTick % 2 == 0) {
            String payload = String.format("MOVE:%.4f,%.4f,%.4f,%.2f,%.2f", x, y, z, yaw, pitch);
            SecureAPI.sendToServer(payload);
            lastX = x; lastZ = z; lastYaw = yaw; lastPitch = pitch;
        }
    }

    // ─── Interaction blocs ─────────────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || event.getEntity() != mc.player) return;
        if (!SecureAPI.isClientSecure()) return;

        event.setCanceled(true);

        BlockHitResult hit = event.getHitVec();
        String hand = event.getHand() == InteractionHand.MAIN_HAND ? "MAIN" : "OFF";
        String payload = String.format("INTERACT_BLOCK:%d,%d,%d,%s,%s",
            hit.getBlockPos().getX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ(),
            hit.getDirection().getName(), hand);
        SecureAPI.sendToServer(payload);
    }

    // ─── Interaction entités ───────────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || event.getEntity() != mc.player) return;
        if (!SecureAPI.isClientSecure()) return;

        event.setCanceled(true);

        String hand = event.getHand() == InteractionHand.MAIN_HAND ? "MAIN" : "OFF";
        String payload = "INTERACT_ENTITY:" + event.getTarget().getId() + "," + hand;
        SecureAPI.sendToServer(payload);
    }
}
