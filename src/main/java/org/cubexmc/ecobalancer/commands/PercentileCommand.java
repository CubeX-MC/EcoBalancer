package org.cubexmc.ecobalancer.commands;

import org.cubexmc.ecobalancer.EcoBalancer;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public class PercentileCommand extends AbstractCommand implements CommandUtil.StatsConsumer<Double> {
    public PercentileCommand(EcoBalancer plugin) {
        super(plugin, "perc");
    }

    @Override
    void onCommand(CommandInfo info) {
        CommandUtil.handleStats(
                plugin, Double.class, info,
                "perc_usage",
                "perc_limits",
                "perc_invalid_args",
                this
        );
    }

    @Override
    public void accept(CommandInfo info, Double var, double low, double up) {
        CompletableFuture.runAsync(() -> {
            double percentile = plugin.calculatePercentile(var, low, up);
            new HashMap<String, String>(){{
                put("balance", String.format("%.2f", var));
                put("percentile", String.format("%.2f", percentile));
                put("low", String.format("%.2f", low));
                put("up", String.format("%.2f", up));
                info.getSender().sendMessage(plugin.getFormattedMessage("perc_success", this));
            }};
        });
    }
}
