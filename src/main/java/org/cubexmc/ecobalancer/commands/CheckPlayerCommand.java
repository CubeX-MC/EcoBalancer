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
        if (sender instanceof Player || sender.hasPermission("ecobalancer.admin")) {
            Player player = (Player) sender;
            if (args.length == 0) {
                player.sendMessage(plugin.getFormattedMessage("messages.enter_player_name_or_use_checkall", null));
            } else {
                plugin.checkPlayer(player, args[0]);
            }
            return true;
        }

        return false;
    }


}
