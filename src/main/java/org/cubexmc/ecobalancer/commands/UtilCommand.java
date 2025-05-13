package org.cubexmc.ecobalancer.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.util.StringUtil;
import org.cubexmc.ecobalancer.EcoBalancer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class UtilCommand implements TabExecutor {
    private final EcoBalancer plugin;
    private final CheckAllCommand checkAllCommand;
    private final CheckPlayerCommand checkPlayerCommand;
    private final DescripStatsCommand statsCommand;
    private final PercentileCommand percCommand;
    private final CheckRecordsCommand checkRecordsCommand;
    private final CheckRecordCommand checkRecordCommand;
    private final RestoreCommand restoreCommand;
    private final IntervalCommand intervalCommand;

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
                Bukkit.getScheduler().cancelTasks(plugin);
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
                if (intervalCommand instanceof TabExecutor) {
                    return ((TabExecutor) intervalCommand).onCommand(sender, command, label, subArgs);
                }
                return false;
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
                plugin.getFormattedMessage("messages.commands.checkrecords", null),
                plugin.getFormattedMessage("messages.commands.checkrecord", null),
                plugin.getFormattedMessage("messages.commands.restore", null),
                plugin.getFormattedMessage("messages.commands.stats", null),
                plugin.getFormattedMessage("messages.commands.interval", null),
                plugin.getFormattedMessage("messages.commands.perc", null),
                plugin.getFormattedMessage("messages.commands.reload", null),
                plugin.getFormattedMessage("messages.help_footer", null)
        };
        for (String str : commandMessages) sender.sendMessage(str);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // 所有可用的子命令
            String[] subCommands = {"help", "reload", "checkall", "checkplayer", "stats", 
                                   "perc", "checkrecords", "checkrecord", "restore", "interval"};
            StringUtil.copyPartialMatches(args[0], Arrays.asList(subCommands), completions);
        } else if (args.length > 1) {
            // 处理子命令的参数补全
            String subCommand = args[0].toLowerCase();
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            
            switch (subCommand) {
                case "interval":
                    if (intervalCommand instanceof TabExecutor) {
                        return ((TabExecutor) intervalCommand).onTabComplete(sender, command, alias, subArgs);
                    }
                    break;
                case "checkplayer":
                    // 补全在线玩家名称
                    if (subArgs.length == 1) {
                        List<String> playerNames = new ArrayList<>();
                        Bukkit.getOnlinePlayers().forEach(player -> playerNames.add(player.getName()));
                        StringUtil.copyPartialMatches(subArgs[0], playerNames, completions);
                    }
                    break;
                case "stats":
                    // stats <number of bars> [low] [up]
                    if (subArgs.length == 1) {
                        // 提供一些常用的柱状图条数选项
                        List<String> barOptions = Arrays.asList("5", "10", "15", "20", "25", "30");
                        StringUtil.copyPartialMatches(subArgs[0], barOptions, completions);
                    }
                    break;
                case "checkrecord":
                    // checkrecord <operation_id> [deduction|alphabet] [page]
                    if (subArgs.length == 2) {
                        // 提供排序选项
                        List<String> sortOptions = Arrays.asList("deduction", "alphabet");
                        StringUtil.copyPartialMatches(subArgs[1], sortOptions, completions);
                    }
                    break;
                case "perc":
                    // 无特定参数补全，使用数字输入
                    break;
                case "checkrecords":
                    // checkrecords [page]
                    // 页码通常是数字，无需特定补全
                    break;
                case "restore":
                    // restore <operation_id>
                    // 操作ID通常是数字，无需特定补全
                    break;
                case "checkall":
                    // 无参数命令，不需要补全
                    break;
                case "help":
                    // 无参数命令，不需要补全
                    break;
                case "reload":
                    // 无参数命令，不需要补全
                    break;
            }
        }
        
        return completions;
    }
}
