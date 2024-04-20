package org.cubexmc.ecobalancer.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.cubexmc.ecobalancer.EcoBalancer;

import java.util.HashMap;

public class AdminLoginListener implements Listener {
    EcoBalancer plugin;

    public AdminLoginListener(EcoBalancer plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAdminLogin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (player.hasPermission("ecobalance.admin") && plugin.getConfig().getBoolean("info-on-login")) {
            if (plugin.useTaxAccount()) {
                player.sendMessage(plugin.getFormattedMessage("tax_account_enabled", new HashMap<String, String>(){{
                    put("tax_account_name", plugin.getTaxAccountName());
                    put("tax_account_balance", plugin.getTaxAccountBalance());
                }}));
            } else {
                player.sendMessage(plugin.getFormattedMessage("tax_account_disabled"));
            }
        }
    }
}