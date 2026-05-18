package org.cubexmc.ecobalancer.integrations;

import org.bukkit.OfflinePlayer;
import org.cubexmc.ecobalancer.EcoBalancer;
import org.cubexmc.ecobalancer.policies.TaxPolicy;
import org.cubexmc.ecobalancer.tax.TaxLedgerService;
import org.cubexmc.ecobalancer.tax.TaxRunState;
import org.cubexmc.ecobalancer.utils.EconomicMetrics;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public class EcoBalancerPlaceholderExpansion extends PlaceholderExpansion {
    private final EcoBalancer plugin;

    public EcoBalancerPlaceholderExpansion(EcoBalancer plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "ecobal";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) {
            return null;
        }
        TaxLedgerService.ServerTaxStats serverStats = null;
        switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "tax_fund_balance":
                serverStats = plugin.getTaxLedgerService().getServerStats();
                return EconomicMetrics.formatLargeNumber(serverStats.taxFundBalance);
            case "tax_total_collected":
                serverStats = plugin.getTaxLedgerService().getServerStats();
                return EconomicMetrics.formatLargeNumber(serverStats.totalTaxCollected);
            case "tax_latest_collected":
                serverStats = plugin.getTaxLedgerService().getServerStats();
                return EconomicMetrics.formatLargeNumber(serverStats.latestTaxCollected);
            case "tax_latest_operation":
                serverStats = plugin.getTaxLedgerService().getServerStats();
                return String.valueOf(serverStats.latestOperationId);
            case "player_latest_tax":
                if (player == null) return "0";
                return EconomicMetrics.formatLargeNumber(plugin.getTaxLedgerService().getPlayerStats(player).latestTaxPaid);
            case "player_total_tax":
                if (player == null) return "0";
                return EconomicMetrics.formatLargeNumber(plugin.getTaxLedgerService().getPlayerStats(player).totalTaxPaid);
            case "tax_next_run":
                long next = plugin.getNextScheduledRunMillis();
                return next <= 0 ? "None" : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(next));
            case "tax_active_policy":
                TaxPolicy policy = plugin.getPolicyManager().getActivePolicy();
                return policy == null ? "None" : policy.getName();
            case "tax_status":
                TaxRunState state = plugin.getTaxRunService().getState();
                return state.isRunning() ? "running" : "idle";
            case "gini":
                try {
                    return String.format("%.4f", EconomicMetrics.calculateGini(plugin.collectAllBalances()));
                } catch (Throwable t) {
                    return "0";
                }
            case "top1_concentration":
                try {
                    return String.format("%.2f", EconomicMetrics.calculateConcentration(plugin.collectAllBalances(), 1.0));
                } catch (Throwable t) {
                    return "0";
                }
            default:
                return null;
        }
    }
}
