package com.securemod;

import com.securemod.network.SecureChannel;
import com.securemod.network.SecurePacketHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(SecureMod.MOD_ID)
public class SecureMod {

    public static final String MOD_ID = "securemod";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public SecureMod(IEventBus modEventBus) {
        LOGGER.info("[SecureMod] Initializing RSA+AES secure protocol");
        modEventBus.addListener(this::onCommonSetup);
        SecurePacketHandler.register(modEventBus);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("[SecureMod] Common setup — registering network channels");
            SecureChannel.init();
        });
    }
}
