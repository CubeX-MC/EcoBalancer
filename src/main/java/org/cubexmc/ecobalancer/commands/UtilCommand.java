package org.cubexmc.ecobalancer.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;
import org.cubexmc.ecobalancer.EcoBalancer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class UtilCommand extends AbstractCommand {

    public UtilCommand(final EcoBalancer plugin) {
        super(plugin, "ecobal");
    }

    @Override
    void onCommand(CommandInfo info) {
        info.checkInput(1);
        switch (info.getInput(0).toLowerCase(Locale.ROOT)) {
            case "reload": {
                Bukkit.getScheduler().cancelTasks(plugin);
                plugin.reloadConfig();
                plugin.loadConfiguration();
                info.getSender().sendMessage(plugin.getFormattedMessage("reload_success"));
                break;
            }
            case "help": {
                final CommandSender sender = info.getSender();
                sender.sendMessage(plugin.getFormattedMessage("help_header"));
                final List<String> keys = Arrays.asList("checkall", "checkplayer", "checkrecords", "checkrecord", "restore", "stats", "interval", "reload");
                for (final String key : keys) sender.sendMessage(plugin.getFormattedMessage("commands." + key));
                sender.sendMessage(plugin.getFormattedMessage("help_footer"));
            }
            default:break;
        }
    }

    @Override
    void onTabComplete(TabCompleteInfo info) {
        info.checkInput(1);
        info.setReturnResult(StringUtil.copyPartialMatches(
                info.getInput(0).toLowerCase(Locale.ROOT),
                Arrays.asList("reload", "help"),
                new ArrayList<>()
        ));
    }
}
