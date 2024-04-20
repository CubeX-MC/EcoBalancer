package org.cubexmc.ecobalancer.commands;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;
import org.cubexmc.ecobalancer.EcoBalancer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class IntervalCommand extends AbstractCommand {

    public IntervalCommand(EcoBalancer plugin) {
        super(plugin, "interval");
    }

    private <T> T getWithIndex(
            final int index,
            final CommandUtil.IntToBooleanFunction condition,
            final Supplier<T> defaultValue,
            final String n,
            final CommandUtil.IntBiFunction<T> getter
    ) {
        return condition.apply(index) ? getter.get(index, plugin.getFormattedMessage("interval_invalid_" + n)) : defaultValue.get();
    }

    @Override
    void onCommand(CommandInfo info) {
        final int length = info.getInput().length;
        String sortBy;
        boolean a = false;
        if (length > 0) {
            final String sort = info.getInput(0).toLowerCase(Locale.ROOT);
            if (sort.equals("alphabet") || sort.equals("balance")) {
                sortBy = sort;
                a = true;
            } else {
                sortBy = "alphabet";
            }
        } else {
            sortBy = "alphabet";
        }
        double low = getWithIndex(a ? 1 : 0, index -> length > index, () -> Double.NEGATIVE_INFINITY, "low", info::getInputAsDouble);
        double up = getWithIndex(a ? 2 : 1, index -> length > index, () -> Double.POSITIVE_INFINITY, "up", info::getInputAsDouble);
        int page = getWithIndex(a ? 3 : 2, index -> length > index, () -> 1, "page", info::getInputAsInt);
        CompletableFuture.runAsync(() -> {
            final Economy economy = plugin.getEconomy();
            final List<OfflinePlayer> matchedPlayers = Arrays
                    .stream(Bukkit.getOfflinePlayers())
                    .filter(economy::hasAccount)
                    .filter(player -> {
                        final double balance = economy.getBalance(player);
                        return balance >= low && balance <= up;
                    })
                    .collect(Collectors.toList());
            final CommandSender sender = info.getSender();
            sender.sendMessage(plugin.getFormattedMessage("interval_sorting"));
            if (sortBy.equals("balance")) {
                matchedPlayers.sort((p1, p2) -> Double.compare(economy.getBalance(p2), economy.getBalance(p1)));
            } else {
                matchedPlayers.sort(Comparator.comparing(OfflinePlayer::getName));
            }
            final int pageSize = 10;
            final int totalPages = (matchedPlayers.size() + pageSize - 1) / pageSize;
            if (page < 1 || page > totalPages) {
                sender.sendMessage(plugin.getFormattedMessage("invalid_page"));
            }
            int start = (page - 1) * pageSize;
            int end = Math.min(start + pageSize, matchedPlayers.size());
            new HashMap<String, String>(){{
                put("low", String.format("%.2f", low));
                put("up", String.format("%.2f", up));
                sender.sendMessage(plugin.getFormattedMessage("interval_header", this));
            }};
            for (int i = start; i < end; i++) {
                final OfflinePlayer player = matchedPlayers.get(i);
                final double balance = economy.getBalance(player);
                long lastPlayed = player.getLastPlayed();
                long currentTime = System.currentTimeMillis();
                long daysOffline = (currentTime - lastPlayed) / (1000 * 60 * 60 * 24);
                new HashMap<String, String>(){{
                    put("player", player.getName());
                    put("balance", String.format("%.2f", balance));
                    put("days_offline", String.valueOf(daysOffline));
                    sender.sendMessage(plugin.getFormattedMessage("interval_player", this));
                }};
            }
            // add clickable next and previous page messages
            TextComponent previousPage = new TextComponent();
            TextComponent nextPage = new TextComponent();
            final String b = "/interval " + sortBy + " " + low + " " + up + " ";
            if (page > 1) {
                previousPage.setText(plugin.getFormattedMessage("prev_page"));
                previousPage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, b + (page - 1)));
            } else {
                previousPage.setText(plugin.getFormattedMessage("no_prev_page"));
            }
            if (page < totalPages) {
                nextPage.setText(plugin.getFormattedMessage("next_page"));
                nextPage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, b + (page + 1)));
            } else {
                nextPage.setText(plugin.getFormattedMessage("no_next_page"));
            }
            TextComponent message = plugin.getFormattedMessage(
                    "interval_page",
                    new HashMap<String, String>(){{
                        put("prev", previousPage.toPlainText());
                        put("next", nextPage.toPlainText());
                    }},
                    new String[]{"prev", "next"},
                    new TextComponent[]{previousPage, nextPage}
            );
            sender.spigot().sendMessage(message);
            sender.sendMessage(plugin.getFormattedMessage("interval_footer"));
        });
    }

    @Override
    void onTabComplete(TabCompleteInfo info) {
        if (info.getInput().length == 1)
            info.setReturnResult(StringUtil.copyPartialMatches(
                    info.getInput(0).toLowerCase(Locale.ROOT),
                    Arrays.asList("alphabet", "balance"),
                    new ArrayList<>()
            ));
    }
}
