package org.cubexmc.ecobalancer.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.cubexmc.ecobalancer.EcoBalancer;
import org.cubexmc.ecobalancer.utils.DatabaseUtils;
import org.cubexmc.ecobalancer.utils.EconomicMetrics;
import org.cubexmc.ecobalancer.utils.MessageUtils;
import org.cubexmc.ecobalancer.utils.SchedulerUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 税收影响分析命令
 * 对比税收操作前后的经济指标变化
 */
public class ImpactCommand implements CommandExecutor {
    private final EcoBalancer plugin;

    public ImpactCommand(EcoBalancer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 异步加载数据
        SchedulerUtils.asyncRun(plugin, () -> {
            try {
                DatabaseUtils.OperationImpact impact;
                
                // 如果提供了操作ID参数，获取指定操作的影响数据
                if (args.length > 0) {
                    try {
                        int operationId = Integer.parseInt(args[0]);
                        impact = DatabaseUtils.getOperationImpact(plugin, operationId, plugin.getLogger());
                        
                        if (impact == null) {
                            SchedulerUtils.globalRun(plugin, () -> {
                                MessageUtils.sendMessage(sender, "&c未找到操作ID " + operationId + " 的影响数据", 
                                    plugin.getLogger(), false);
                                MessageUtils.sendMessage(sender, "&7使用 &e/eb impact &7查看最近的税收影响", 
                                    plugin.getLogger(), false);
                            }, 0, 0);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        SchedulerUtils.globalRun(plugin, () -> {
                            MessageUtils.sendMessage(sender, "&c无效的操作ID: " + args[0], 
                                plugin.getLogger(), false);
                        }, 0, 0);
                        return;
                    }
                } else {
                    // 默认获取最近一次操作的影响数据
                    List<DatabaseUtils.OperationImpact> recentImpacts = 
                        DatabaseUtils.getRecentImpacts(plugin, 1, plugin.getLogger());
                    
                    if (recentImpacts.isEmpty()) {
                        SchedulerUtils.globalRun(plugin, () -> {
                            MessageUtils.sendMessage(sender, "&c尚无税收影响数据", 
                                plugin.getLogger(), false);
                            MessageUtils.sendMessage(sender, "&7请先执行 &e/eb checkall &7以生成影响数据", 
                                plugin.getLogger(), false);
                        }, 0, 0);
                        return;
                    }
                    
                    impact = recentImpacts.get(0);
                }

                // 回到主线程发送报告
                final DatabaseUtils.OperationImpact finalImpact = impact;
                SchedulerUtils.globalRun(plugin, () -> {
                    sendImpactReport(sender, finalImpact);
                }, 0, 0);

            } catch (Exception e) {
                plugin.getLogger().severe("获取税收影响数据失败: " + e.getMessage());
                e.printStackTrace();
                SchedulerUtils.globalRun(plugin, () -> {
                    MessageUtils.sendMessage(sender, "&c获取失败，请查看控制台日志", 
                        plugin.getLogger(), false);
                }, 0, 0);
            }
        }, 0);

        return true;
    }

    /**
     * 简化的发送消息方法
     */
    private void msg(CommandSender sender, String message) {
        MessageUtils.sendMessage(sender, message, plugin.getLogger(), false);
    }

    /**
     * 发送税收影响报告
     */
    private void sendImpactReport(CommandSender sender, DatabaseUtils.OperationImpact impact) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String timestamp = dateFormat.format(new Date(impact.timestamp));

        msg(sender, "&6&l════════════════════════════════════");
        msg(sender, "&e&l         税收影响分析报告");
        msg(sender, String.format("&7操作ID: &f#%d &7| 时间: %s", impact.operationId, timestamp));
        msg(sender, "&6&l════════════════════════════════════");
        msg(sender, "");

        // 概览
        msg(sender, "&e&l▸ 操作概览:");
        msg(sender, String.format("  &7征收总额: &a%s", 
            EconomicMetrics.formatLargeNumber(impact.totalTaxCollected)));
        msg(sender, String.format("  &7影响玩家: &f%d &7名", impact.playersAffected));
        msg(sender, "");

        // 基尼系数变化
        msg(sender, "&e&l▸ 不平等程度变化:");
        double giniChange = impact.afterGini - impact.beforeGini;
        double giniChangePercent = (impact.beforeGini > 0) ? (giniChange / impact.beforeGini * 100) : 0;
        String giniTrend = getChangeIndicator(giniChange, true); // true表示值降低是改善
        String giniColor = (giniChange < 0) ? "&a" : (giniChange > 0) ? "&c" : "&e";
        
        msg(sender, String.format("  &7基尼系数: %.3f → %.3f %s%.3f (%s%.1f%%&7)", 
            impact.beforeGini, impact.afterGini, giniColor, giniChange, giniColor, giniChangePercent));
        msg(sender, "  " + generateComparisonBar(impact.beforeGini, impact.afterGini, 1.0, 20));
        msg(sender, String.format("    &8└─ %s %s", giniTrend, getGiniChangeDescription(giniChange)));
        msg(sender, "");

        // 财富集中度变化 (Top 1%)
        msg(sender, "&e&l▸ 财富集中度变化:");
        double top1Change = impact.afterTop1Pct - impact.beforeTop1Pct;
        double top1ChangePercent = (impact.beforeTop1Pct > 0) ? (top1Change / impact.beforeTop1Pct * 100) : 0;
        String top1Trend = getChangeIndicator(top1Change, true);
        String top1Color = (top1Change < 0) ? "&a" : (top1Change > 0) ? "&c" : "&e";
        
        msg(sender, String.format("  &7Top 1%% 财富占比: %.1f%% → %.1f%% %s%.1f%% (%s%.1f%%&7)", 
            impact.beforeTop1Pct * 100, impact.afterTop1Pct * 100, 
            top1Color, top1Change * 100, top1Color, top1ChangePercent));
        msg(sender, "  " + generateComparisonBar(impact.beforeTop1Pct, impact.afterTop1Pct, 1.0, 20));
        msg(sender, String.format("    &8└─ %s %s", top1Trend, getConcentrationChangeDescription(top1Change)));
        msg(sender, "");

        // 中位数和均值变化
        msg(sender, "&e&l▸ 财富分布变化:");
        
        // 中位数
        double medianChange = impact.afterMedian - impact.beforeMedian;
        double medianChangePercent = (impact.beforeMedian > 0) ? (medianChange / impact.beforeMedian * 100) : 0;
        String medianTrend = getChangeIndicator(medianChange, false); // false表示值升高是改善
        String medianColor = (medianChange > 0) ? "&a" : (medianChange < 0) ? "&c" : "&e";
        
        msg(sender, String.format("  &7中位数: %s → %s %s%s (%s%.1f%%&7)", 
            EconomicMetrics.formatLargeNumber(impact.beforeMedian),
            EconomicMetrics.formatLargeNumber(impact.afterMedian),
            medianColor, EconomicMetrics.formatLargeNumber(medianChange), 
            medianColor, medianChangePercent));
        msg(sender, String.format("    &8└─ %s", medianTrend));
        
        // 均值
        double meanChange = impact.afterMean - impact.beforeMean;
        double meanChangePercent = (impact.beforeMean > 0) ? (meanChange / impact.beforeMean * 100) : 0;
        String meanTrend = getChangeIndicator(meanChange, false);
        String meanColor = (meanChange > 0) ? "&a" : (meanChange < 0) ? "&c" : "&e";
        
        msg(sender, String.format("  &7平均值: %s → %s %s%s (%s%.1f%%&7)", 
            EconomicMetrics.formatLargeNumber(impact.beforeMean),
            EconomicMetrics.formatLargeNumber(impact.afterMean),
            meanColor, EconomicMetrics.formatLargeNumber(meanChange), 
            meanColor, meanChangePercent));
        msg(sender, String.format("    &8└─ %s", meanTrend));
        
        // 标准差
        double stdDevChange = impact.afterStdDev - impact.beforeStdDev;
        double stdDevChangePercent = (impact.beforeStdDev > 0) ? (stdDevChange / impact.beforeStdDev * 100) : 0;
        String stdDevTrend = getChangeIndicator(stdDevChange, true); // 标准差降低是改善
        String stdDevColor = (stdDevChange < 0) ? "&a" : (stdDevChange > 0) ? "&c" : "&e";
        
        msg(sender, String.format("  &7标准差: %s → %s %s%s (%s%.1f%%&7)", 
            EconomicMetrics.formatLargeNumber(impact.beforeStdDev),
            EconomicMetrics.formatLargeNumber(impact.afterStdDev),
            stdDevColor, EconomicMetrics.formatLargeNumber(stdDevChange), 
            stdDevColor, stdDevChangePercent));
        msg(sender, String.format("    &8└─ %s", stdDevTrend));
        msg(sender, "");

        // 总货币量变化
        msg(sender, "&e&l▸ 经济规模变化:");
        double totalMoneyChange = impact.afterTotalMoney - impact.beforeTotalMoney;
        double totalMoneyChangePercent = (impact.beforeTotalMoney > 0) ? 
            (totalMoneyChange / impact.beforeTotalMoney * 100) : 0;
        
        msg(sender, String.format("  &7总货币量: %s → %s", 
            EconomicMetrics.formatLargeNumber(impact.beforeTotalMoney),
            EconomicMetrics.formatLargeNumber(impact.afterTotalMoney)));
        msg(sender, String.format("  &7变化: &c%s &7(%.2f%%)", 
            EconomicMetrics.formatLargeNumber(totalMoneyChange), totalMoneyChangePercent));
        msg(sender, String.format("    &8└─ 通过税收移除了 %.2f%% 的流通货币", 
            Math.abs(totalMoneyChangePercent)));
        msg(sender, "");

        // 综合评价
        msg(sender, "&e&l▸ 综合评价:");
        String overallAssessment = getOverallAssessment(giniChange, top1Change, medianChange);
        msg(sender, "  " + overallAssessment);
        
        msg(sender, "");
        msg(sender, "&6&l════════════════════════════════════");
    }

    /**
     * 生成对比进度条
     */
    private String generateComparisonBar(double before, double after, double max, int length) {
        int beforePos = (int) Math.round((before / max) * length);
        int afterPos = (int) Math.round((after / max) * length);
        beforePos = Math.max(0, Math.min(length, beforePos));
        afterPos = Math.max(0, Math.min(length, afterPos));
        
        StringBuilder bar = new StringBuilder("  &7[");
        
        for (int i = 0; i < length; i++) {
            if (i == beforePos && i == afterPos) {
                bar.append("&e◆"); // 没变化
            } else if (i == beforePos) {
                bar.append("&c●"); // 之前的位置
            } else if (i == afterPos) {
                bar.append("&a●"); // 之后的位置
            } else if ((beforePos < afterPos && i > beforePos && i < afterPos) ||
                      (beforePos > afterPos && i > afterPos && i < beforePos)) {
                bar.append("&7─"); // 变化区间
            } else {
                bar.append("&8░"); // 空白区域
            }
        }
        
        bar.append("&7] &c● &7→ &a●");
        return ChatColor.translateAlternateColorCodes('&', bar.toString());
    }

    /**
     * 获取变化指示器
     * @param change 变化值
     * @param lowerIsBetter 是否值降低代表改善
     */
    private String getChangeIndicator(double change, boolean lowerIsBetter) {
        if (Math.abs(change) < 0.001) {
            return "&e→ 保持稳定";
        }
        
        boolean isImprovement = lowerIsBetter ? (change < 0) : (change > 0);
        String arrow = (change < 0) ? "↓" : "↑";
        String color = isImprovement ? "&a" : "&c";
        String status = isImprovement ? "改善" : "恶化";
        
        return String.format("%s%s %s", color, arrow, status);
    }

    /**
     * 获取基尼系数变化描述
     */
    private String getGiniChangeDescription(double change) {
        double absChange = Math.abs(change);
        if (absChange >= 0.1) {
            return "显著变化";
        } else if (absChange >= 0.05) {
            return "明显变化";
        } else if (absChange >= 0.01) {
            return "轻微变化";
        } else {
            return "微小变化";
        }
    }

    /**
     * 获取集中度变化描述
     */
    private String getConcentrationChangeDescription(double change) {
        double absChange = Math.abs(change) * 100;
        if (absChange >= 5) {
            return "显著再分配";
        } else if (absChange >= 2) {
            return "明显再分配";
        } else if (absChange >= 0.5) {
            return "轻微再分配";
        } else {
            return "影响微小";
        }
    }

    /**
     * 获取综合评价
     */
    private String getOverallAssessment(double giniChange, double top1Change, double medianChange) {
        int improveCount = 0;
        int worsenCount = 0;
        
        // 基尼系数：降低是改善
        if (giniChange < -0.01) improveCount++;
        else if (giniChange > 0.01) worsenCount++;
        
        // 集中度：降低是改善
        if (top1Change < -0.005) improveCount++;
        else if (top1Change > 0.005) worsenCount++;
        
        // 中位数：升高是改善
        if (medianChange > 0) improveCount++;
        else if (medianChange < 0) worsenCount++;
        
        if (improveCount >= 2 && worsenCount == 0) {
            return "&a&l✓ 税收政策效果显著，成功改善了经济不平等";
        } else if (improveCount > worsenCount) {
            return "&a&l✓ 税收政策总体有效，经济状况得到改善";
        } else if (improveCount == worsenCount) {
            return "&e&l⚠ 税收政策效果中性，建议调整税率";
        } else if (worsenCount > improveCount) {
            return "&c&l✗ 税收政策可能需要调整，部分指标恶化";
        } else {
            return "&c&l✗ 税收政策效果不佳，建议重新评估税率设置";
        }
    }
}
