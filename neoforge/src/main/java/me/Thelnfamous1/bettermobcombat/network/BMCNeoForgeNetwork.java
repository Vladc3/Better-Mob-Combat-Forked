package me.Thelnfamous1.bettermobcombat.network;

import me.Thelnfamous1.bettermobcombat.platform.ForgePlatformHelper;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class BMCNeoForgeNetwork {

    private static final String PROTOCOL_VERSION = "1.0";

    public static void register(RegisterPayloadHandlersEvent event) {
        ForgePlatformHelper.registrar = event.registrar(PROTOCOL_VERSION);
        PacketHandler.registerPackets();

    }
}
