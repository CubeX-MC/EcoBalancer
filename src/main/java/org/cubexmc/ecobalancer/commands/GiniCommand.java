package org.cubexmc.ecobalancer.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.cubexmc.ecobalancer.EcoBalancer;
import org.cubexmc.ecobalancer.utils.EconomicMetrics;
import org.cubexmc.ecobalancer.utils.SchedulerUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基尼系数命令
 * 用法: /ecobal gini [days]
 * - 不带参数：计算所有玩家的基尼系数
 * - 带days参数：仅计算N天内活跃玩家的基尼系数
 */
public class GiniCommand implements CommandExecutor {
    private final EcoBalancer plugin;

    public GiniCommand(EcoBalancer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 解析参数
        Integer activeDays = null;
        if (args.length > 0) {
            try {
                activeDays = Integer.parseInt(args[0]);
                if (activeDays <= 0) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("days", args[0]);
                    sender.sendMessage(plugin.getFormattedMessage("messages.gini.invalid_days", placeholders));
                    return false;
                }
            } catch (NumberFormatException e) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("input", args[0]);
                sender.sendMessage(plugin.getFormattedMessage("messages.gini.invalid_number", placeholders));
                return false;
            }
        }

        // 提示开始计算
        sender.sendMessage(plugin.getFormattedMessage("messages.gini.calculating", null));

        final Integer finalActiveDays = activeDays;

        // 异步计算（避免阻塞主线程）
        SchedulerUtils.runTaskAsync(plugin, () -> {
            try {
                // 收集余额数据
                List<Double> balances = EconomicMetrics.collectBalances(finalActiveDays);

                if (balances.isEmpty()) {
                    SchedulerUtils.runTask(plugin, () -> {
                        sender.sendMessage(plugin.getFormattedMessage("messages.gini.no_data", null));
                    });
                    return;
                }

                // 计算基尼系数
                double gini = EconomicMetrics.calculateGini(balances);
                double totalMoney = EconomicMetrics.calculateTotalMoney(balances);
                String giniLevel = EconomicMetrics.getGiniLevel(gini);

                // 主线程发送结果
                SchedulerUtils.runTask(plugin, () -> {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("gini", String.format("%.4f", gini));
                    placeholders.put("gini_percentage", String.format("%.2f%%", gini * 100));
                    placeholders.put("level", giniLevel);
                    placeholders.put("player_count", String.valueOf(balances.size()));
                    placeholders.put("total_money", EconomicMetrics.formatLargeNumber(totalMoney));
                    placeholders.put("days", finalActiveDays == null ? "∞" : String.valueOf(finalActiveDays));

                    sender.sendMessage(plugin.getFormattedMessage("messages.gini.header", null));
                    sender.sendMessage(plugin.getFormattedMessage("messages.gini.result", placeholders));
                    
                    // 额外的解释信息
                    if (gini >= 0.6) {
                        sender.sendMessage(plugin.getFormattedMessage("messages.gini.warning_high", null));
                    } else if (gini >= 0.5) {
                        sender.sendMessage(plugin.getFormattedMessage("messages.gini.warning_moderate", null));
                    } else if (gini < 0.3) {
                        sender.sendMessage(plugin.getFormattedMessage("messages.gini.info_low", null));
                    }
                });

            } catch (Exception e) {
                SchedulerUtils.runTask(plugin, () -> {
                    sender.sendMessage(plugin.getFormattedMessage("messages.gini.error", null));
                    plugin.getLogger().severe("Error calculating Gini coefficient: " + e.getMessage());
                    e.printStackTrace();
                });
            }
        });

        return true;
    }
}
