package org.cubexmc.ecobalancer.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.cubexmc.ecobalancer.EcoBalancer;
import org.cubexmc.ecobalancer.utils.MessageUtils;
import org.cubexmc.ecobalancer.utils.StatisticsUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PercentileCommand implements CommandExecutor {
    private final EcoBalancer plugin;

    public PercentileCommand(EcoBalancer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || args.length > 3) {
            sender.sendMessage(MessageUtils.formatMessage(plugin.getLangConfig(), "messages.perc_usage", null, plugin.getMessagePrefix()));
            sender.sendMessage(MessageUtils.formatMessage(plugin.getLangConfig(), "messages.perc_limits", null, plugin.getMessagePrefix()));
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
            sender.sendMessage(MessageUtils.formatMessage(plugin.getLangConfig(), "messages.perc_invalid_args", null, plugin.getMessagePrefix()));
            return false;
        }

        // 收集符合条件的玩家余额
        List<Double> balances = StatisticsUtils.collectBalancesInRange(low, up);
        
        // 计算百分位数
        double percentile = StatisticsUtils.calculatePercentile(balance, balances);
        
        // 准备消息占位符
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("balance", String.format("%.2f", balance));
        placeholders.put("percentile", String.format("%.2f", percentile));
        placeholders.put("low", low == Double.NEGATIVE_INFINITY ? "∞" : String.format("%.2f", low));
        placeholders.put("up", up == Double.POSITIVE_INFINITY ? "∞" : String.format("%.2f", up));
        
        // 发送结果消息
        sender.sendMessage(MessageUtils.formatMessage(plugin.getLangConfig(), "messages.perc_success", placeholders, plugin.getMessagePrefix()));
        return true;
    }
}
