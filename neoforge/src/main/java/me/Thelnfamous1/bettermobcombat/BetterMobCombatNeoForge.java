package me.Thelnfamous1.bettermobcombat;

import me.Thelnfamous1.bettermobcombat.network.BMCNeoForgeNetwork;
import me.Thelnfamous1.bettermobcombat.network.PacketHandler;
import me.Thelnfamous1.bettermobcombat.network.S2CConfigSync;
import me.Thelnfamous1.bettermobcombat.platform.Services;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber
@Mod(Constants.MOD_ID)
public class BetterMobCombatNeoForge {
    
    public BetterMobCombatNeoForge(IEventBus bus, Dist dist) {
    
        // This method is invoked by the Forge mod loader when it is ready
        // to load your mod. You can access Forge and Common code in this
        // project.
    
        // Use Forge to bootstrap the Common mod.
        BetterMobCombat.init();
        if(dist == Dist.CLIENT){
            BetterMobCombatClient.init();
        }
        bus.addListener(BMCNeoForgeNetwork::register);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) ->
                PacketHandler.sendToClient(new S2CConfigSync(BetterMobCombat.getServerConfigSerialized())
                        ,(ServerPlayer)event.getEntity()));
    }

    @SubscribeEvent
    static void onGatherData(GatherDataEvent event){

    }
}