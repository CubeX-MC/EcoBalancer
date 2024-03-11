package org.cubexmc.ecobalancer.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.cubexmc.ecobalancer.EcoBalancer;

import java.util.HashMap;
import java.util.Map;

public class PercentileCommand implements CommandExecutor {
    private final EcoBalancer plugin;

    public PercentileCommand(EcoBalancer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || args.length > 3) {
            sender.sendMessage(plugin.getFormattedMessage("messages.perc_usage", null));
            sender.sendMessage(plugin.getFormattedMessage("messages.perc_limits", null));
            return false;
        }

        double balance;
        double low = Double.NEGATIVE_INFINITY;
        double up = Double.POSITIVE_INFINITY;

        try {
            balance = Double.parseDouble(args[0]);
            if (args.length >= 2) {
                low = args[1].equals("_") ? Double.NEGATIVE_INFINITY : Double.parseDouble(args[1]);
            }
            if (args.length == 3) {
                up = args[2].equals("_") ? Double.POSITIVE_INFINITY : Double.parseDouble(args[2]);
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getFormattedMessage("messages.perc_invalid_args", null));
            return false;
        }

        double percentile = plugin.calculatePercentile(balance, low, up);
        // put placeholders in a map
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("balance", String.format("%.2f", balance));
        placeholders.put("percentile", String.format("%.2f", percentile));
        placeholders.put("low", String.format("%.2f", low));
        placeholders.put("up", String.format("%.2f", up));
        sender.sendMessage(plugin.getFormattedMessage("messages.perc_success", placeholders));
        return true;
    }
}
