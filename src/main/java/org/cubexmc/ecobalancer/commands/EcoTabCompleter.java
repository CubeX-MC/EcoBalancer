package org.cubexmc.ecobalancer.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import org.cubexmc.ecobalancer.EcoBalancer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Dedicated TabCompleter for /ecobal command to keep UtilCommand focused on execution only.
 */
public class EcoTabCompleter implements TabCompleter {
    private final UtilCommand util;

    public EcoTabCompleter(EcoBalancer plugin, UtilCommand util) {
        this.util = util;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String[] subCommands = {"help", "reload", "checkall", "checkplayer", "stats",
                    "perc", "checkrecords", "checkrecord", "restore", "interval",
                    "gini", "concentration", "report"};
            StringUtil.copyPartialMatches(args[0], Arrays.asList(subCommands), completions);
            return completions;
        }

        if (args.length > 1) {
            String subCommand = args[0].toLowerCase();
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

            switch (subCommand) {
                case "interval":
                    // Delegate to IntervalCommand's own completer if present
                    if (util.getIntervalCommand() != null) {
                        return util.getIntervalCommand().onTabComplete(sender, command, alias, subArgs);
                    }
                    break;
                case "checkplayer":
                    if (subArgs.length == 1) {
                        List<String> playerNames = new ArrayList<>();
                        Bukkit.getOnlinePlayers().forEach(player -> playerNames.add(player.getName()));
                        StringUtil.copyPartialMatches(subArgs[0], playerNames, completions);
                        return completions;
                    }
                    break;
                case "stats":
                    if (subArgs.length == 1) {
                        List<String> barOptions = Arrays.asList("5", "10", "15", "20", "25", "30");
                        StringUtil.copyPartialMatches(subArgs[0], barOptions, completions);
                        return completions;
                    }
                    break;
                case "checkrecord":
                    // Delegate to CheckRecordCommand's completer if present (it implements TabExecutor)
                    if (util.getCheckRecordCommand() != null) {
                        return util.getCheckRecordCommand().onTabComplete(sender, command, alias, subArgs);
                    }
                    // Fallback to static options for the 2nd arg
                    if (subArgs.length == 2) {
                        List<String> sortOptions = Arrays.asList("deduction", "alphabet");
                        StringUtil.copyPartialMatches(subArgs[1], sortOptions, completions);
                        return completions;
                    }
                    break;
                case "gini":
                    // gini [days] - suggest common day ranges
                    if (subArgs.length == 1) {
                        List<String> dayOptions = Arrays.asList("7", "30", "60", "90");
                        StringUtil.copyPartialMatches(subArgs[0], dayOptions, completions);
                        return completions;
                    }
                    break;
                case "concentration":
                    // concentration [percentages...] - suggest common percentages
                    if (subArgs.length >= 1) {
                        List<String> pctOptions = Arrays.asList("1", "5", "10", "20", "25", "50");
                        StringUtil.copyPartialMatches(subArgs[subArgs.length - 1], pctOptions, completions);
                        return completions;
                    }
                    break;
                case "report":
                    // report [operation_id] - no specific completion for IDs
                    break;
                case "perc":
                case "checkrecords":
                case "restore":
                case "checkall":
                case "help":
                case "reload":
                default:
                    // No specific completion
                    break;
            }
        }

        return completions;
    }
}
