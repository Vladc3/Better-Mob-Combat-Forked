package me.Thelnfamous1.bettermobcombat;

import net.fabricmc.api.ClientModInitializer;

public class BetterMobCombatFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BetterMobCombatClient.init();
    }
}
