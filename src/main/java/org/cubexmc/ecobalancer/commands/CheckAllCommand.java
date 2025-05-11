package org.cubexmc.ecobalancer.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.cubexmc.ecobalancer.EcoBalancer;
import org.cubexmc.ecobalancer.utils.MessageUtils;

public class CheckAllCommand implements CommandExecutor {
    private final EcoBalancer plugin;

    public CheckAllCommand(EcoBalancer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
        if (sender instanceof Player || sender.hasPermission("ecobalancer.admin")) {
            if (args.length == 0) {
                sender.sendMessage(MessageUtils.formatMessage(plugin.getLangConfig(), "messages.scanning_offline_players", null, plugin.getMessagePrefix()));
                plugin.checkAll(sender);
            }
            return true;
        }
        return false;
    }
}
