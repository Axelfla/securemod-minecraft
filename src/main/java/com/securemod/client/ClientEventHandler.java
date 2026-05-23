package com.securemod.client;

import com.securemod.SecureMod;
import com.securemod.network.SecureChannel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = SecureMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        SecureMod.LOGGER.info("[Client] Connection — session ready, waiting for server HELLO");
        SecureChannel.clearClientSession();
        SecureChannel.getOrCreateClientSession();
    }

    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SecureMod.LOGGER.info("[Client] Disconnection — cleaning session");
        SecureChannel.clearClientSession();
    }
}
