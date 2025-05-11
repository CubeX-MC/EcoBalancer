package org.cubexmc.ecobalancer.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.cubexmc.ecobalancer.EcoBalancer;
import org.cubexmc.ecobalancer.utils.MessageUtils;

public class DescripStatsCommand implements CommandExecutor {
    private final EcoBalancer plugin;

    public DescripStatsCommand(EcoBalancer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
        if (args.length < 1 || args.length > 3) {
            sender.sendMessage(MessageUtils.formatMessage(plugin.getLangConfig(), "messages.stats_usage", null, plugin.getMessagePrefix()));
            sender.sendMessage(MessageUtils.formatMessage(plugin.getLangConfig(), "messages.stats_limits", null, plugin.getMessagePrefix()));
            return false;
        }

        int numBars;
        double low = Double.NEGATIVE_INFINITY;
        double up = Double.POSITIVE_INFINITY;

        try {
            numBars = Integer.parseInt(args[0]);
            if (numBars < 1) {
                sender.sendMessage(MessageUtils.formatMessage(plugin.getLangConfig(), "messages.stats_invalid_number_of_bars", null, plugin.getMessagePrefix()));
                return false;
            }
            if (args.length >= 2) {
                low = args[1].equals("_") ? Double.NEGATIVE_INFINITY : Double.parseDouble(args[1]);
            }
            if (args.length == 3) {
                up = args[2].equals("_") ? Double.POSITIVE_INFINITY : Double.parseDouble(args[2]);
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.formatMessage(plugin.getLangConfig(), "messages.stats_invalid_args", null, plugin.getMessagePrefix()));
            return false;
        }

        plugin.generateHistogram(sender, numBars, low, up);
        return true;
    }
}
