package org.cubexmc.ecobalancer.commands;

import org.cubexmc.ecobalancer.EcoBalancer;

import java.util.concurrent.CompletableFuture;

public class DescriptionStatsCommand extends AbstractCommand implements CommandUtil.StatsConsumer<Integer> {
    public DescriptionStatsCommand(EcoBalancer plugin) {
        super(plugin, "stats");
    }

    @Override
    void onCommand(CommandInfo info) {
        CommandUtil.handleStats(
                plugin,
                Integer.class,
                info,
                "stats_usage",
                "stats_limits",
                "stats_invalid_args",
                this
        );
    }

    @Override
    public void accept(CommandInfo info, Integer var, double low, double up) {
        CompletableFuture.runAsync(() -> plugin.generateHistogram(info.getSender(), var, low, up)); // 异步执行
    }
}
