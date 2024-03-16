package org.cubexmc.ecobalancer.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.util.StringUtil;
import org.cubexmc.ecobalancer.EcoBalancer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class UtilCommand implements TabExecutor {
    EcoBalancer plugin;

    public UtilCommand(EcoBalancer plugin) {
        this.plugin = plugin;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            Bukkit.getScheduler().cancelTasks(plugin);
            plugin.reloadConfig();
            plugin.loadConfiguration(); // You should create this method
            sender.sendMessage(plugin.getFormattedMessage("messages.reload_success", null));
            return true;
        } else if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
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
            return true;
        }
        return false;
    }

    @Override
    public final List<String> onTabComplete(final CommandSender commandSender, final Command command, final String s, final String[] strings) {
        final int size = 2;
        final Collection<String> ret = new ArrayList<>(size);
        if (1 == strings.length) {
            ret.add("reload");
            ret.add("help");
        }
        final String lowerCase = strings[0].toLowerCase(Locale.ROOT);
        return StringUtil.copyPartialMatches(lowerCase, ret, new ArrayList<>(size));
    }
}
