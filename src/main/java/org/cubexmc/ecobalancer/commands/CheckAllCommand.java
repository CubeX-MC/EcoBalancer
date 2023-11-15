package org.cubexmc.ecobalancer.commands;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.cubexmc.ecobalancer.EcoBalancer;

import java.util.UUID;

public class CheckAllCommand implements CommandExecutor {
    EcoBalancer plugin;

    public CheckAllCommand(EcoBalancer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!player.hasPermission("ecobalancer.admin")) {
                player.sendMessage(plugin.getFormattedMessage("messages.no_permission", null));
                return true;
            }
            if (args.length == 0) {
                player.sendMessage(plugin.getFormattedMessage("messages.scanning_offline_players", null));
                plugin.checkAll(sender);
            }
        } else if (sender.hasPermission("ecobalancer.admin")) {
            if (args.length == 0) {
                sender.sendMessage(plugin.getFormattedMessage("messages.scanning_offline_players", null));
                plugin.checkAll(sender);
            }
        }

        return true;
    }
}
