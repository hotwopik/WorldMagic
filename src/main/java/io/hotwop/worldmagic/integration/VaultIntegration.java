package io.hotwop.worldmagic.integration;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;

public final class VaultIntegration{
    private static Economy economy;

    public static Economy economy(){
        return economy;
    }

    public static void loadEconomy(){
        economy=Bukkit.getServicesManager().load(Economy.class);
    }
}