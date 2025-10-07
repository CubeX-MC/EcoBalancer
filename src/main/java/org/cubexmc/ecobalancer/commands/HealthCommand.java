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
 * 经济健康度评估命令
 * 综合多个经济指标评估服务器经济健康状态
 */
public class HealthCommand implements CommandExecutor {
    private final EcoBalancer plugin;

    public HealthCommand(EcoBalancer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 异步计算健康度
        SchedulerUtils.asyncRun(plugin, () -> {
            try {
                // 收集所有玩家余额
                List<Double> balances = EconomicMetrics.collectBalances(null);
                if (balances.isEmpty()) {
                    SchedulerUtils.globalRun(plugin, () -> {
                        MessageUtils.sendMessage(sender, "&c没有找到任何玩家数据", plugin.getLogger(), false);
                    }, 0, 0);
                    return;
                }

                // 计算当前指标
                double gini = EconomicMetrics.calculateGini(balances);
                double top1Pct = EconomicMetrics.calculateConcentration(balances, 1.0) * 100;
                double top10Pct = EconomicMetrics.calculateConcentration(balances, 10.0) * 100;
                
                List<Double> sortedBalances = EconomicMetrics.getSortedBalances(balances);
                double median = EconomicMetrics.calculateMedian(sortedBalances);
                double mean = EconomicMetrics.calculateMean(balances);
                double stdDev = EconomicMetrics.calculateStdDev(sortedBalances, mean);

                // 计算健康度评分
                HealthScore healthScore = calculateHealthScore(gini, top1Pct, top10Pct, stdDev, mean);

                // 获取历史数据进行趋势分析
                DatabaseUtils.EconomicSnapshot latestSnapshot = DatabaseUtils.getLatestSnapshot(plugin, plugin.getLogger());
                String trend = "";
                if (latestSnapshot != null) {
                    trend = analyzeTrend(gini, latestSnapshot.gini);
                }

                // 回到主线程发送消息
                final HealthScore finalScore = healthScore;
                final String finalTrend = trend;
                final int playerCount = balances.size();
                SchedulerUtils.globalRun(plugin, () -> {
                    sendHealthReport(sender, finalScore, gini, top1Pct, top10Pct, median, mean, 
                                   stdDev, playerCount, finalTrend);
                }, 0, 0);

            } catch (Exception e) {
                plugin.getLogger().severe("计算经济健康度失败: " + e.getMessage());
                e.printStackTrace();
                SchedulerUtils.globalRun(plugin, () -> {
                    MessageUtils.sendMessage(sender, "&c计算失败，请查看控制台日志", plugin.getLogger(), false);
                }, 0, 0);
            }
        }, 0);

        return true;
    }

    /**
     * 计算综合健康度评分 (0-100)
     */
    private HealthScore calculateHealthScore(double gini, double top1Pct, double top10Pct, 
                                           double stdDev, double mean) {
        HealthScore score = new HealthScore();

        // 1. 基尼系数评分 (权重: 40%)
        // 理想值: 0.3-0.4, 0.5+为警戒
        double giniScore;
        if (gini <= 0.35) {
            giniScore = 100;
        } else if (gini <= 0.45) {
            giniScore = 100 - (gini - 0.35) * 500; // 0.35-0.45区间: 100-50分
        } else if (gini <= 0.6) {
            giniScore = 50 - (gini - 0.45) * 200; // 0.45-0.6区间: 50-20分
        } else {
            giniScore = Math.max(0, 20 - (gini - 0.6) * 50); // 0.6+: 20-0分
        }
        score.giniScore = giniScore;

        // 2. 财富集中度评分 (权重: 30%)
        // top1%理想值<20%, top10%理想值<50%
        double concentrationScore;
        if (top1Pct <= 20 && top10Pct <= 50) {
            concentrationScore = 100;
        } else if (top1Pct <= 30 && top10Pct <= 60) {
            concentrationScore = 80;
        } else if (top1Pct <= 40 && top10Pct <= 70) {
            concentrationScore = 60;
        } else if (top1Pct <= 50 && top10Pct <= 80) {
            concentrationScore = 40;
        } else {
            concentrationScore = 20;
        }
        score.concentrationScore = concentrationScore;

        // 3. 分布均匀度评分 (权重: 30%)
        // 使用变异系数 (CV = stdDev / mean)，理想值<1.0
        double cv = (mean > 0) ? (stdDev / mean) : Double.MAX_VALUE;
        double distributionScore;
        if (cv <= 1.0) {
            distributionScore = 100;
        } else if (cv <= 2.0) {
            distributionScore = 100 - (cv - 1.0) * 50; // 1.0-2.0: 100-50分
        } else if (cv <= 3.0) {
            distributionScore = 50 - (cv - 2.0) * 30; // 2.0-3.0: 50-20分
        } else {
            distributionScore = Math.max(0, 20 - (cv - 3.0) * 10); // 3.0+: 20-0分
        }
        score.distributionScore = distributionScore;

        // 4. 综合评分
        score.totalScore = giniScore * 0.4 + concentrationScore * 0.3 + distributionScore * 0.3;

        // 5. 健康等级判定
        if (score.totalScore >= 80) {
            score.level = HealthLevel.EXCELLENT;
        } else if (score.totalScore >= 60) {
            score.level = HealthLevel.GOOD;
        } else if (score.totalScore >= 40) {
            score.level = HealthLevel.MODERATE;
        } else if (score.totalScore >= 20) {
            score.level = HealthLevel.POOR;
        } else {
            score.level = HealthLevel.CRITICAL;
        }

        return score;
    }

    /**
     * 分析趋势
     */
    private String analyzeTrend(double currentGini, double previousGini) {
        double change = currentGini - previousGini;
        if (Math.abs(change) < 0.01) {
            return "→"; // 稳定
        } else if (change < 0) {
            return "↓"; // 改善（基尼系数下降）
        } else {
            return "↑"; // 恶化（基尼系数上升）
        }
    }

    /**
     * 简化的发送消息方法
     */
    private void msg(CommandSender sender, String message) {
        MessageUtils.sendMessage(sender, message, plugin.getLogger(), false);
    }

    /**
     * 发送健康度报告
     */
    private void sendHealthReport(CommandSender sender, HealthScore score, double gini, 
                                 double top1Pct, double top10Pct, double median, 
                                 double mean, double stdDev, int playerCount, String trend) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String timestamp = dateFormat.format(new Date());

        msg(sender, "&6&l════════════════════════════════════");
        msg(sender, "&e&l         经济健康度评估报告");
        msg(sender, "&7生成时间: " + timestamp);
        msg(sender, "&6&l════════════════════════════════════");
        
        // 综合评分和等级
        String levelColor = getLevelColor(score.level);
        String levelName = getLevelName(score.level);
        msg(sender, "");
        msg(sender, String.format("&b综合评分: %s%.1f/100 &7(%s%s&7)", 
            levelColor, score.totalScore, levelColor, levelName));
        msg(sender, generateProgressBar(score.totalScore, 100, 30, levelColor));
        
        if (!trend.isEmpty()) {
            msg(sender, String.format("&7趋势: %s", 
                trend.equals("↑") ? "&c↑ 恶化" : trend.equals("↓") ? "&a↓ 改善" : "&e→ 稳定"));
        }

        // 详细指标
        msg(sender, "");
        msg(sender, "&e&l▸ 详细指标:");
        
        // 1. 基尼系数
        String giniColor = gini <= 0.4 ? "&a" : gini <= 0.5 ? "&e" : "&c";
        msg(sender, String.format("  &7基尼系数: %s%.3f &7(评分: &b%.1f&7/40)", 
            giniColor, gini, score.giniScore * 0.4));
        msg(sender, "  " + generateProgressBar(gini, 1.0, 20, giniColor));
        msg(sender, String.format("    &8└─ %s", getGiniDescription(gini)));

        // 2. 财富集中度
        String concColor = top1Pct <= 25 ? "&a" : top1Pct <= 40 ? "&e" : "&c";
        msg(sender, String.format("  &7财富集中度: (评分: &b%.1f&7/30)", 
            score.concentrationScore * 0.3));
        msg(sender, String.format("    &8• &7Top 1%%: %s%.1f%% &7财富", 
            concColor, top1Pct));
        msg(sender, "      " + generateProgressBar(top1Pct, 100, 18, concColor));
        msg(sender, String.format("    &8• &7Top 10%%: %s%.1f%% &7财富", 
            concColor, top10Pct));
        msg(sender, "      " + generateProgressBar(top10Pct, 100, 18, concColor));

        // 3. 分布均匀度
        double cv = (mean > 0) ? (stdDev / mean) : 0;
        String cvColor = cv <= 1.5 ? "&a" : cv <= 2.5 ? "&e" : "&c";
        msg(sender, String.format("  &7分布均匀度: (评分: &b%.1f&7/30)", 
            score.distributionScore * 0.3));
        msg(sender, String.format("    &8• &7变异系数: %s%.2f", cvColor, cv));
        msg(sender, String.format("    &8• &7平均值: &f%s", 
            EconomicMetrics.formatLargeNumber(mean)));
        msg(sender, String.format("    &8• &7中位数: &f%s", 
            EconomicMetrics.formatLargeNumber(median)));
        msg(sender, String.format("    &8• &7标准差: &f%s", 
            EconomicMetrics.formatLargeNumber(stdDev)));

        // 建议
        msg(sender, "");
        msg(sender, "&e&l▸ 建议措施:");
        List<String> recommendations = getRecommendations(score, gini, top1Pct, cv);
        for (String rec : recommendations) {
            msg(sender, "  &8• &7" + rec);
        }

        msg(sender, "");
        msg(sender, String.format("&7样本数量: &f%d &7名玩家", playerCount));
        msg(sender, "&6&l════════════════════════════════════");
    }

    /**
     * 生成进度条
     */
    private String generateProgressBar(double value, double max, int length, String color) {
        int filled = (int) Math.round((value / max) * length);
        filled = Math.max(0, Math.min(length, filled));
        
        StringBuilder bar = new StringBuilder("  &7[");
        for (int i = 0; i < length; i++) {
            if (i < filled) {
                bar.append(color).append("█");
            } else {
                bar.append("&8░");
            }
        }
        bar.append("&7]");
        
        return ChatColor.translateAlternateColorCodes('&', bar.toString());
    }

    /**
     * 获取等级颜色
     */
    private String getLevelColor(HealthLevel level) {
        switch (level) {
            case EXCELLENT: return "&a";
            case GOOD: return "&2";
            case MODERATE: return "&e";
            case POOR: return "&6";
            case CRITICAL: return "&c";
            default: return "&7";
        }
    }

    /**
     * 获取等级名称
     */
    private String getLevelName(HealthLevel level) {
        switch (level) {
            case EXCELLENT: return "优秀";
            case GOOD: return "良好";
            case MODERATE: return "中等";
            case POOR: return "较差";
            case CRITICAL: return "危急";
            default: return "未知";
        }
    }

    /**
     * 获取基尼系数描述
     */
    private String getGiniDescription(double gini) {
        if (gini <= 0.3) {
            return "极度平等";
        } else if (gini <= 0.4) {
            return "相对平等";
        } else if (gini <= 0.5) {
            return "差距适中";
        } else if (gini <= 0.6) {
            return "差距较大";
        } else {
            return "极度不平等";
        }
    }

    /**
     * 获取改善建议
     */
    private List<String> getRecommendations(HealthScore score, double gini, double top1Pct, double cv) {
        List<String> recommendations = new java.util.ArrayList<>();

        if (score.level == HealthLevel.EXCELLENT) {
            recommendations.add("经济状态优秀，继续保持当前政策");
            recommendations.add("可考虑微调税率以维持平衡");
            return recommendations;
        }

        // 基于基尼系数的建议
        if (gini > 0.5) {
            recommendations.add("基尼系数过高，建议加大累进税率");
            recommendations.add("考虑对富裕玩家征收更高税率");
        } else if (gini > 0.4) {
            recommendations.add("适度提高高收入阶层的税率");
        }

        // 基于财富集中度的建议
        if (top1Pct > 40) {
            recommendations.add("财富过度集中于顶层，需要再分配机制");
            recommendations.add("可设置财富上限或特别税");
        } else if (top1Pct > 25) {
            recommendations.add("顶层财富集中度偏高，建议适度调控");
        }

        // 基于分布均匀度的建议
        if (cv > 2.5) {
            recommendations.add("财富分布极不均匀，需要强力干预");
            recommendations.add("建议实施福利补贴或最低收入保障");
        } else if (cv > 1.5) {
            recommendations.add("考虑增加中产阶级扶持政策");
        }

        // 如果没有明显问题
        if (recommendations.isEmpty()) {
            recommendations.add("经济状态基本健康，维持现状即可");
        }

        return recommendations;
    }

    /**
     * 健康度评分数据类
     */
    private static class HealthScore {
        double giniScore;
        double concentrationScore;
        double distributionScore;
        double totalScore;
        HealthLevel level;
    }

    /**
     * 健康等级枚举
     */
    private enum HealthLevel {
        EXCELLENT,  // 优秀: 80+
        GOOD,       // 良好: 60-79
        MODERATE,   // 中等: 40-59
        POOR,       // 较差: 20-39
        CRITICAL    // 危急: 0-19
    }
}
