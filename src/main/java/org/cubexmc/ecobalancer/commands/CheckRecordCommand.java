package org.cubexmc.ecobalancer.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;
import org.cubexmc.ecobalancer.EcoBalancer;
import org.cubexmc.ecobalancer.util.StatementWriter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class CheckRecordCommand extends AbstractCommand {

    public CheckRecordCommand(EcoBalancer plugin) {
        super(plugin, "checkrecord");
    }

    private static class SetInfo {
        final int operationId;

        private SetInfo(final int operationId) {
            this.operationId=operationId;
        }
    }

    private void handleDetail(final CommandSender sender, final ResultSet resultSet, final SetInfo info) throws SQLException {
        final String playerName = resultSet.getString("player_name");
        double oldBalance = resultSet.getDouble("old_balance");
        double newBalance = resultSet.getDouble("new_balance");
        double deduction = resultSet.getDouble("deduction");

        Map<String, String> detailPlaceholders = new HashMap<>();
        detailPlaceholders.put("player", playerName);
        detailPlaceholders.put("old_balance", String.format("%.2f", oldBalance));
        detailPlaceholders.put("new_balance", String.format("%.2f", newBalance));
        detailPlaceholders.put("deduction", String.format("%.2f", deduction));

        if (info != null) {
            detailPlaceholders.put("operation_id", String.valueOf(info.operationId));
        }
        sender.sendMessage(plugin.getFormattedMessage("record_" + (info == null ? "all" : "player") + "_detail", detailPlaceholders));
    }

    @Override
    void onCommand(CommandInfo info) {
        final int length = info.getInput().length;
        final CommandSender sender = info.getSender();
        if (length == 0 || length > 3) {
            sender.sendMessage(plugin.getFormattedMessage("record_usage"));
            return;
        }
        int operationId = info.getInputAsInt(0, plugin.getFormattedMessage("record_invalid_id"));
        int page;
        String sortBy;
        if (length >= 2) {
            final String inputSortBy = info.getInput(1).toLowerCase(Locale.ROOT);
            boolean a = false;
            if (inputSortBy.equals("alphabet") || inputSortBy.equals("deduction")) {
                sortBy=inputSortBy;
                if (length == 3) {
                    a=true;
                }
            } else {
                sortBy = "deduction";
            }
            page = info.getInputAsInt(a ? 2 : 1, plugin.getFormattedMessage("invalid_page"));
        } else {
            sortBy = "deduction";
            page = 1;
        }
        CompletableFuture.runAsync(() -> {
            try {
                final Connection connection = Objects.requireNonNull(plugin.driverConnection.get());
                try (final PreparedStatement statement = connection.prepareStatement("SELECT * FROM operations WHERE id = ?")) {
                    statement.setInt(1, operationId);
                    try (final ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            boolean checkAll = resultSet.getBoolean("is_checkall");
                            final Map<String, String> placeholders = new HashMap<>();
                            placeholders.put("operation_id", String.valueOf(operationId));
                            if (checkAll) sender.sendMessage(plugin.getFormattedMessage("record_all_header", placeholders));
                            final int pageSize = 10;
                            final int offset = (page - 1) * pageSize;
                            try (PreparedStatement select = connection.prepareStatement("SELECT * FROM records WHERE operation_id = ? " + (checkAll
                                    ? "ORDER BY "  + (sortBy.equals("alphabet") ? "player_name" : "deduction DESC") + " LIMIT ? OFFSET ?"
                                    : " AND deduction != 0.0"
                            ))) {
                                final StatementWriter writer = StatementWriter.newWriter(select).writeInt(operationId);
                                if (checkAll) writer.writeInt(pageSize).writeInt(offset);
                                try (final ResultSet result = select.executeQuery()) {
                                    if (checkAll) {
                                        while (result.next()) handleDetail(sender, result, null);
                                    } else if (result.next()) {
                                        sender.sendMessage(plugin.getFormattedMessage("record_player_header", placeholders));
                                        handleDetail(sender, result, new SetInfo(operationId));
                                    } else {
                                        sender.sendMessage(plugin.getFormattedMessage("record_not_found"));
                                    }
                                }
                            }
                        } else {
                            sender.sendMessage(plugin.getFormattedMessage("record_invalid_id"));
                        }
                    }
                }
            } catch (final Exception exception) {
                sender.sendMessage(plugin.getFormattedMessage("record_error"));
            }
        });
    }

    @Override
    void onTabComplete(TabCompleteInfo info) {
        if (info.getInput().length == 2) {
            info.setReturnResult(StringUtil.copyPartialMatches(
                    info.getInput(1).toLowerCase(Locale.ROOT),
                    Arrays.asList("deduction", "alphabet"),
                    new ArrayList<>()
            ));
        }
    }
}
