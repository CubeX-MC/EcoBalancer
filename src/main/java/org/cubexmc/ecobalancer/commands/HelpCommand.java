package org.cubexmc.ecobalancer.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class HelpCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(ChatColor.GREEN + "EcoBalancer Help");
        sender.sendMessage(ChatColor.YELLOW + "/checkall - Check and update all players' balances.");
        sender.sendMessage(ChatColor.YELLOW + "/checkplayer <player> - Check and update a specific player's balance.");
        return true;
    }
}
