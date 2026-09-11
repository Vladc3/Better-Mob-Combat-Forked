package me.Thelnfamous1.bettermobcombat.network;

public class BMCFabricNetwork {

    public static void register() {
        // Fabric registers packets directly without needing an event or a registrar instance
        PacketHandler.registerPackets();
    }
}