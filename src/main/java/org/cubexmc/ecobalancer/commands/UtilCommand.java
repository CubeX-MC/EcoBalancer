package org.cubexmc.ecobalancer.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.cubexmc.ecobalancer.EcoBalancer;

import java.util.HashMap;
import java.util.Map;

public class UtilCommand implements CommandExecutor {
    EcoBalancer plugin;

    public UtilCommand(EcoBalancer plugin) {
        this.plugin = plugin;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            Bukkit.getScheduler().cancelTasks(plugin);
            plugin.reloadConfig();
            plugin.loadConfiguration(); // You should create this method
            sender.sendMessage(plugin.getFormattedMessage("messages.reload_success", null));
            return true;
        } else if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(plugin.getFormattedMessage("messages.help_header", null));
            String[] commandMessages = {
                    plugin.getFormattedMessage("messages.commands.help", null),
                    plugin.getFormattedMessage("messages.commands.checkall", null),
                    plugin.getFormattedMessage("messages.commands.checkplayer", null),
                    plugin.getFormattedMessage("messages.commands.checkrecords", null),
                    plugin.getFormattedMessage("messages.commands.checkrecord", null),
                    plugin.getFormattedMessage("messages.commands.restore", null),
                    plugin.getFormattedMessage("messages.commands.stats", null),
                    plugin.getFormattedMessage("messages.commands.perc", null),
                    plugin.getFormattedMessage("messages.commands.reload", null),
                    plugin.getFormattedMessage("messages.help_footer", null)
            };
            for (String str : commandMessages) sender.sendMessage(str);
            return true;
        }
        return false;
    }
}
