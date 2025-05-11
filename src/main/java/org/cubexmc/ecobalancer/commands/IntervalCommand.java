package org.cubexmc.ecobalancer.commands;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.StringUtil;
import org.cubexmc.ecobalancer.EcoBalancer;
import org.cubexmc.ecobalancer.utils.MessageUtils;
import org.cubexmc.ecobalancer.utils.PageUtils;
import org.cubexmc.ecobalancer.utils.VaultUtils;

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

        // 解析命令参数
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
                                        sender.sendMessage(MessageUtils.formatMessage(plugin.getLangConfig(), "messages.invalid_page", null, plugin.getMessagePrefix()));
                                        return true;
                                    }
                                }
                            } catch (NumberFormatException e) {
                                sender.sendMessage(MessageUtils.formatMessage(plugin.getLangConfig(), "messages.interval_invalid_up", null, plugin.getMessagePrefix()));
                                return true;
                            }
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage(MessageUtils.formatMessage(plugin.getLangConfig(), "messages.interval_invalid_low", null, plugin.getMessagePrefix()));
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
                                    sender.sendMessage(MessageUtils.formatMessage(plugin.getLangConfig(), "messages.invalid_page", null, plugin.getMessagePrefix()));
                                    return true;
                                }
                            }
                        } catch (NumberFormatException e) {
                            sender.sendMessage(MessageUtils.formatMessage(plugin.getLangConfig(), "messages.interval_invalid_up", null, plugin.getMessagePrefix()));
                            return true;
                        }
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(MessageUtils.formatMessage(plugin.getLangConfig(), "messages.interval_invalid_low", null, plugin.getMessagePrefix()));
                    return true;
                }
            }
        }

        // 收集符合条件的玩家
        OfflinePlayer[] players = Bukkit.getOfflinePlayers();
        List<OfflinePlayer> matchedPlayers = new ArrayList<>();

        sender.sendMessage(MessageUtils.formatMessage(plugin.getLangConfig(), "messages.interval_collecting", null, plugin.getMessagePrefix()));
        for (OfflinePlayer player : players) {
            if (VaultUtils.hasAccount(player)) {
                double balance = VaultUtils.getBalance(player);
                if (balance >= low && balance <= up) {
                    matchedPlayers.add(player);
                }
            }
        }

        // 排序玩家列表
        sender.sendMessage(MessageUtils.formatMessage(plugin.getLangConfig(), "messages.interval_sorting", null, plugin.getMessagePrefix()));
        if (sortBy.equals("balance")) {
            matchedPlayers.sort((p1, p2) -> Double.compare(VaultUtils.getBalance(p2), VaultUtils.getBalance(p1)));
        } else {
            matchedPlayers.sort(Comparator.comparing(OfflinePlayer::getName));
        }

        // 处理分页显示
        final int pageSize = 10; // 每页显示10个玩家
        int totalPages = PageUtils.calculateTotalPages(matchedPlayers.size(), pageSize);

        if (!PageUtils.isValidPage(page, totalPages)) {
            sender.sendMessage(MessageUtils.formatMessage(plugin.getLangConfig(), "messages.invalid_page", null, plugin.getMessagePrefix()));
            return true;
        }

        // 显示页头
        Map<String, String> headerPlaceholders = new HashMap<>();
        headerPlaceholders.put("low", String.format("%.2f", low));
        headerPlaceholders.put("up", String.format("%.2f", up));
        sender.sendMessage(MessageUtils.formatMessage(plugin.getLangConfig(), "messages.interval_header", headerPlaceholders, plugin.getMessagePrefix()));

        // 使用PageUtils渲染玩家列表
        final String commandFormat = "/interval " + sortBy + " " + low + " " + up + " %d";
        final double finalLow = low;
        final double finalUp = up;
        
        PageUtils.renderPagination(
            sender,
            matchedPlayers,
            pageSize,
            page,
            (s, player, i) -> {
                // 渲染单个玩家
                double balance = VaultUtils.getBalance(player);
                long lastPlayed = player.getLastPlayed();
                long currentTime = System.currentTimeMillis();
                long daysOffline = (currentTime - lastPlayed) / (1000 * 60 * 60 * 24);

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                placeholders.put("balance", String.format("%.2f", balance));
                placeholders.put("days_offline", String.valueOf(daysOffline));

                s.sendMessage(MessageUtils.formatMessage(plugin.getLangConfig(), "messages.interval_player", placeholders, plugin.getMessagePrefix()));
            },
            "messages.interval_header",
            "messages.interval_footer",
            "messages.interval_page",
            commandFormat,
            plugin.getLangConfig(),
            "messages.invalid_page",
            plugin.getMessagePrefix(),
            headerPlaceholders
        );

        return true;
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
