package org.cubexmc.ecobalancer.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.cubexmc.ecobalancer.EcoBalancer;

public class CheckPlayerCommand implements CommandExecutor {
    EcoBalancer plugin;

    public CheckPlayerCommand(EcoBalancer plugin) {
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
                player.sendMessage(plugin.getFormattedMessage("messages.enter_player_name_or_use_checkall", null));
            } else {
                checkPlayer(player, args[0]);
            }
        } else if (sender.hasPermission("ecobalancer.admin")) {
            if (args.length == 0) {
                sender.sendMessage(plugin.getFormattedMessage("messages.enter_player_name_or_use_checkall", null));
            } else {
                checkPlayer(sender, args[0]);
            }
        }

        return true;
    }

    private void checkPlayer(CommandSender sender, String playerName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.hasPlayedBefore()) {
            long currentTime = System.currentTimeMillis();
            plugin.checkBalance(sender, currentTime, target, true);
        } else {
            sender.sendMessage(plugin.getFormattedMessage("messages.player_not_found", null));
        }
    }
}
