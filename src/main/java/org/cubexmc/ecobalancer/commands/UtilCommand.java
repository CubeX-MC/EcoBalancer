package org.cubexmc.ecobalancer.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.cubexmc.ecobalancer.EcoBalancer;
import org.cubexmc.ecobalancer.utils.SchedulerUtils;

import java.util.Arrays;

public class UtilCommand implements CommandExecutor {
    private final EcoBalancer plugin;
    private final CheckAllCommand checkAllCommand;
    private final CheckPlayerCommand checkPlayerCommand;
    private final DescripStatsCommand statsCommand;
    private final PercentileCommand percCommand;
    private final CheckRecordsCommand checkRecordsCommand;
    private final CheckRecordCommand checkRecordCommand;
    private final RestoreCommand restoreCommand;
    private final IntervalCommand intervalCommand;
    private final GiniCommand giniCommand;
    private final ConcentrationCommand concentrationCommand;
    private final TaxReportCommand reportCommand;
    private final HealthCommand healthCommand;
    private final ImpactCommand impactCommand;
    private final TrendsCommand trendsCommand;

    public UtilCommand(EcoBalancer plugin) {
        this.plugin = plugin;
        this.checkAllCommand = new CheckAllCommand(plugin);
        this.checkPlayerCommand = new CheckPlayerCommand(plugin);
        this.statsCommand = new DescripStatsCommand(plugin);
        this.percCommand = new PercentileCommand(plugin);
        this.checkRecordsCommand = new CheckRecordsCommand(plugin);
        this.checkRecordCommand = new CheckRecordCommand(plugin);
        this.restoreCommand = new RestoreCommand(plugin);
        this.intervalCommand = new IntervalCommand(plugin);
        this.giniCommand = new GiniCommand(plugin);
        this.concentrationCommand = new ConcentrationCommand(plugin);
        this.reportCommand = new TaxReportCommand(plugin);
        this.healthCommand = new HealthCommand(plugin);
        this.impactCommand = new ImpactCommand(plugin);
        this.trendsCommand = new TrendsCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // 显示帮助信息
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (subCommand) {
            case "reload":
                SchedulerUtils.cancelAllTasks(plugin);
                plugin.reloadConfig();
                plugin.loadConfiguration();
                sender.sendMessage(plugin.getFormattedMessage("messages.reload_success", null));
                return true;
            case "help":
                showHelp(sender);
                return true;
            case "checkall":
                return checkAllCommand.onCommand(sender, command, label, subArgs);
            case "checkplayer":
                return checkPlayerCommand.onCommand(sender, command, label, subArgs);
            case "stats":
                return statsCommand.onCommand(sender, command, label, subArgs);
            case "perc":
                return percCommand.onCommand(sender, command, label, subArgs);
            case "checkrecords":
                return checkRecordsCommand.onCommand(sender, command, label, subArgs);
            case "checkrecord":
                return checkRecordCommand.onCommand(sender, command, label, subArgs);
            case "restore":
                return restoreCommand.onCommand(sender, command, label, subArgs);
            case "interval":
                return intervalCommand.onCommand(sender, command, label, subArgs);
            case "gini":
                return giniCommand.onCommand(sender, command, label, subArgs);
            case "concentration":
                return concentrationCommand.onCommand(sender, command, label, subArgs);
            case "report":
                return reportCommand.onCommand(sender, command, label, subArgs);
            case "health":
                return healthCommand.onCommand(sender, command, label, subArgs);
            case "impact":
                return impactCommand.onCommand(sender, command, label, subArgs);
            case "trends":
                return trendsCommand.onCommand(sender, command, label, subArgs);
            default:
                sender.sendMessage(plugin.getFormattedMessage("messages.unknown_command", null));
                return false;
        }
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(plugin.getFormattedMessage("messages.help_header", null));
        String[] commandMessages = {
                plugin.getFormattedMessage("messages.commands.help", null),
                plugin.getFormattedMessage("messages.commands.checkall", null),
                plugin.getFormattedMessage("messages.commands.checkplayer", null),
                plugin.getFormattedMessage("messages.commands.gini", null),
                plugin.getFormattedMessage("messages.commands.concentration", null),
                plugin.getFormattedMessage("messages.commands.report", null),
                plugin.getFormattedMessage("messages.commands.checkrecords", null),
                plugin.getFormattedMessage("messages.commands.checkrecord", null),
                plugin.getFormattedMessage("messages.commands.restore", null),
                plugin.getFormattedMessage("messages.commands.stats", null),
                plugin.getFormattedMessage("messages.commands.interval", null),
                plugin.getFormattedMessage("messages.commands.perc", null),
                plugin.getFormattedMessage("messages.commands.health", null),
                plugin.getFormattedMessage("messages.commands.impact", null),
                plugin.getFormattedMessage("messages.commands.trends", null),
                plugin.getFormattedMessage("messages.commands.reload", null),
                plugin.getFormattedMessage("messages.help_footer", null)
        };
        for (String str : commandMessages) sender.sendMessage(str);
    }

    // Expose subcommands for completer routing
    public IntervalCommand getIntervalCommand() { return intervalCommand; }
    public CheckRecordCommand getCheckRecordCommand() { return checkRecordCommand; }
}
