package org.cubexmc.ecobalancer.commands;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.util.StringUtil;
import org.cubexmc.ecobalancer.EcoBalancer;

import java.util.*;

public class IntervalCommand implements TabExecutor {
    private final EcoBalancer plugin;

    public IntervalCommand(EcoBalancer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sortBy = "alphabet";
        double low = Double.NEGATIVE_INFINITY;
        double up = Double.POSITIVE_INFINITY;
        int page = 1;

        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("alphabet") || args[0].equalsIgnoreCase("balance")) {
                sortBy = args[0].toLowerCase();
                if (args.length > 1) {
                    try {
                        low = args[1].equals("_") ? Double.NEGATIVE_INFINITY : Double.parseDouble(args[1]);
                        if (args.length > 2) {
                            try {
                                up = args[2].equals("_") ? Double.POSITIVE_INFINITY : Double.parseDouble(args[2]);
                                if (args.length > 3) {
                                    try {
                                        page = Integer.parseInt(args[3]);
                                    } catch (NumberFormatException e) {
                                        sender.sendMessage(plugin.getFormattedMessage("messages.invalid_page", null));
                                        return true;
                                    }
                                }
                            } catch (NumberFormatException e) {
                                sender.sendMessage(plugin.getFormattedMessage("messages.interval_invalid_up", null));
                                return true;
                            }
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage(plugin.getFormattedMessage("messages.interval_invalid_low", null));
                        return true;
                    }
                }
            } else {
                try {
                    low = args[0].equals("_") ? Double.NEGATIVE_INFINITY : Double.parseDouble(args[0]);
                    if (args.length > 1) {
                        try {
                            up = args[1].equals("_") ? Double.POSITIVE_INFINITY : Double.parseDouble(args[1]);
                            if (args.length > 2) {
                                try {
                                    page = Integer.parseInt(args[2]);
                                } catch (NumberFormatException e) {
                                    sender.sendMessage(plugin.getFormattedMessage("messages.invalid_page", null));
                                    return true;
                                }
                            }
                        } catch (NumberFormatException e) {
                            sender.sendMessage(plugin.getFormattedMessage("messages.interval_invalid_up", null));
                            return true;
                        }
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(plugin.getFormattedMessage("messages.interval_invalid_low", null));
                    return true;
                }
            }
        }

        OfflinePlayer[] players = Bukkit.getOfflinePlayers();
        List<OfflinePlayer> matchedPlayers = new ArrayList<>();

        sender.sendMessage(plugin.getFormattedMessage("messages.interval_collecting", null));
        for (OfflinePlayer player : players) {
            if (plugin.getEconomy().hasAccount(player)) {
                double balance = plugin.getEconomy().getBalance(player);
                if (balance >= low && balance <= up) {
                    matchedPlayers.add(player);
                }
            }
        }
        sender.sendMessage(plugin.getFormattedMessage("messages.interval_sorting", null));
        if (sortBy.equals("balance")) {
            matchedPlayers.sort((p1, p2) -> Double.compare(plugin.getEconomy().getBalance(p2), plugin.getEconomy().getBalance(p1)));
        } else {
            matchedPlayers.sort(Comparator.comparing(OfflinePlayer::getName));
        }
        int pageSize = 10; // 每页显示10个玩家
        int totalPages = (matchedPlayers.size() + pageSize - 1) / pageSize;

        if (page < 1 || page > totalPages) {
            sender.sendMessage(plugin.getFormattedMessage("messages.invalid_page", null));
            return true;
        }

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, matchedPlayers.size());

        Map<String, String> headerPlaceholders = new HashMap<>();
        headerPlaceholders.put("low", String.format("%.2f", low));
        headerPlaceholders.put("up", String.format("%.2f", up));
        sender.sendMessage(plugin.getFormattedMessage("messages.interval_header", headerPlaceholders));

        for (int i = start; i < end; i++) {
            OfflinePlayer player = matchedPlayers.get(i);
            double balance = plugin.getEconomy().getBalance(player);
            long lastPlayed = player.getLastPlayed();
            long currentTime = System.currentTimeMillis();
            long daysOffline = (currentTime - lastPlayed) / (1000 * 60 * 60 * 24);

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            placeholders.put("balance", String.format("%.2f", balance));
            placeholders.put("days_offline", String.valueOf(daysOffline));

            sender.sendMessage(plugin.getFormattedMessage("messages.interval_player", placeholders));
        }

        Map<String, String> footerPlaceholders = new HashMap<>();
        footerPlaceholders.put("page", String.valueOf(page));
        footerPlaceholders.put("total", String.valueOf(totalPages));
        // add clickable next and previous page messages
        TextComponent previouwPage = new TextComponent();
        TextComponent nextPage = new TextComponent();
        if (page > 1) {
            previouwPage.setText(plugin.getFormattedMessage("messages.prev_page", null));
            previouwPage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/interval " + sortBy + " " + low + " " + up + " " + (page - 1)));
        } else {
            previouwPage.setText(plugin.getFormattedMessage("messages.no_prev_page", null));
        }
        if (page < totalPages) {
            nextPage.setText(plugin.getFormattedMessage("messages.next_page", null));
            nextPage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/interval " + sortBy + " " + low + " " + up + " " + (page + 1)));
        } else {
            nextPage.setText(plugin.getFormattedMessage("messages.no_next_page", null));
        }
        footerPlaceholders.put("prev", previouwPage.toPlainText());
        footerPlaceholders.put("next", nextPage.toPlainText());

        TextComponent message = plugin.getFormattedMessage("messages.interval_page", footerPlaceholders, new String[]{"prev", "next"}, new TextComponent[]{previouwPage, nextPage});
        sender.spigot().sendMessage(message);
        sender.sendMessage(plugin.getFormattedMessage("messages.interval_footer", null));

        return true;
    }

    private boolean isDouble(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private int parseIntOrDefault(String value, int defaultValue, CommandSender sender) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getFormattedMessage("messages.invalid_page", null));
            return defaultValue;
        }
    }

    @Override
    public final List<String> onTabComplete(final CommandSender commandSender, final Command command, final String s, final String[] strings) {
        final int size = 2;
        final Collection<String> ret = new ArrayList<>(size);
        if (1 == strings.length) {
            ret.add("alphabet");
            ret.add("balance");
        }
        final String lowerCase = strings[0].toLowerCase(Locale.ROOT);
        return StringUtil.copyPartialMatches(lowerCase, ret, new ArrayList<>(size));
    }
}
