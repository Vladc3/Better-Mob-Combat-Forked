package me.Thelnfamous1.bettermobcombat;

import me.Thelnfamous1.bettermobcombat.network.BMCFabricNetwork;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

public class BetterMobCombatFabric implements ModInitializer {

    private static MinecraftServer currentServer;

    @Override
    public void onInitialize() {
        
        // This method is invoked by the Fabric mod loader when it is ready
        // to load your mod. You can access Fabric and Common code in this
        // project.

        // Use Fabric to bootstrap the Common mod.
        BetterMobCombat.init();
        // Payload types must be registered on the dedicated server too, or S2C packets
        // fall back to DiscardedPayload's codec and the encoder disconnects the player.
        BMCFabricNetwork.register();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> currentServer = server);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> currentServer = null);
    }

    @Nullable
    public static MinecraftServer getServer(){
        return currentServer;
    }
}
