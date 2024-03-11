package org.cubexmc.ecobalancer.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.cubexmc.ecobalancer.EcoBalancer;

import java.io.File;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CheckRecordCommand implements CommandExecutor {
    private final EcoBalancer plugin;

    public CheckRecordCommand(EcoBalancer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage(plugin.getFormattedMessage("messages.record_usage", null));
            return true;
        }

        int operationId;
        try {
            operationId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getFormattedMessage("messages.record_invalid_id", null));
            return true;
        }

        int page = 1;
        if (args.length == 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getFormattedMessage("messages.invalid_page", null));
                return true;
            }
        }

        // 获取数据库文件路径
        File dataFolder = plugin.getDataFolder();
        File databaseFile = new File(dataFolder, "records.db");

        // 从数据库中查询对应的操作
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath())) {
            try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM operations WHERE id = ?")) {
                preparedStatement.setInt(1, operationId);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        boolean isCheckAll = resultSet.getBoolean("is_checkall");
                        long timestamp = resultSet.getLong("timestamp");

                        if (isCheckAll) {
                            Map<String, String> placeholders = new HashMap<>();
                            placeholders.put("operation_id", String.valueOf(operationId));
                            sender.sendMessage(plugin.getFormattedMessage("messages.record_all_header", placeholders));

                            int pageSize = 10;
                            int offset = (page - 1) * pageSize;

                            try (PreparedStatement selectStatement = connection.prepareStatement("SELECT * FROM records WHERE operation_id = ? ORDER BY deduction DESC LIMIT ? OFFSET ?")) {
                                selectStatement.setInt(1, operationId);
                                selectStatement.setInt(2, pageSize);
                                selectStatement.setInt(3, offset);

                                try (ResultSet allRecords = selectStatement.executeQuery()) {
                                    int count = 0;
                                    while (allRecords.next()) {
                                        String playerName = allRecords.getString("player_name");
                                        double oldBalance = allRecords.getDouble("old_balance");
                                        double newBalance = allRecords.getDouble("new_balance");
                                        double deduction = allRecords.getDouble("deduction");

                                        Map<String, String> detailPlaceholders = new HashMap<>();
                                        detailPlaceholders.put("player", playerName);
                                        detailPlaceholders.put("old_balance", String.format("%.2f", oldBalance));
                                        detailPlaceholders.put("new_balance", String.format("%.2f", newBalance));
                                        detailPlaceholders.put("deduction", String.format("%.2f", deduction));

                                        String message = plugin.getFormattedMessage("messages.record_all_detail", detailPlaceholders);
                                        sender.sendMessage(message);
                                        count++;
                                    }

                                    if (count == pageSize) {
                                        Map<String, String> nextPlaceholders = new HashMap<>();
                                        nextPlaceholders.put("operation_id", String.valueOf(operationId));
                                        nextPlaceholders.put("page", String.valueOf(page + 1));
                                        sender.sendMessage(plugin.getFormattedMessage("messages.record_all_next", nextPlaceholders));
                                    }
                                }
                            }

                            try (PreparedStatement countStatement = connection.prepareStatement("SELECT COUNT(*) AS total FROM records WHERE operation_id = ?")) {
                                countStatement.setInt(1, operationId);
                                try (ResultSet countResult = countStatement.executeQuery()) {
                                    if (countResult.next()) {
                                        int total = countResult.getInt("total");
                                        int totalPages = (int) Math.ceil((double) total / pageSize);
                                        Map<String, String> pagePlaceholders = new HashMap<>();
                                        pagePlaceholders.put("page", String.valueOf(page));
                                        pagePlaceholders.put("total", String.valueOf(totalPages));
                                        sender.sendMessage(plugin.getFormattedMessage("messages.record_all_page", pagePlaceholders));
                                    }
                                }
                            }
                        } else {
                            // 查询单个玩家的记录
                            try (PreparedStatement selectStatement = connection.prepareStatement("SELECT * FROM records WHERE operation_id = ?")) {
                                selectStatement.setInt(1, operationId);
                                try (ResultSet allRecords = selectStatement.executeQuery()) {
                                    if (allRecords.next()) {
                                        String playerName = allRecords.getString("player_name");
                                        double oldBalance = allRecords.getDouble("old_balance");
                                        double newBalance = allRecords.getDouble("new_balance");
                                        double deduction = allRecords.getDouble("deduction");

                                        Map<String, String> placeholders = new HashMap<>();
                                        placeholders.put("operation_id", String.valueOf(operationId));
                                        placeholders.put("player", playerName);
                                        placeholders.put("old_balance", String.format("%.2f", oldBalance));
                                        placeholders.put("new_balance", String.format("%.2f", newBalance));
                                        placeholders.put("deduction", String.format("%.2f", deduction));

                                        sender.sendMessage(plugin.getFormattedMessage("messages.record_player_header", placeholders));
                                        String message = plugin.getFormattedMessage("messages.record_player_detail", placeholders);
                                        sender.sendMessage(message);
                                    } else {
                                        sender.sendMessage(plugin.getFormattedMessage("messages.record_not_found", null));
                                    }
                                }
                            }
                        }
                    } else {
                        sender.sendMessage(plugin.getFormattedMessage("messages.record_invalid_id", null));
                    }
                }
            }
        } catch (SQLException e) {
            Map<String, String> errorPlaceholders = new HashMap<>();
            sender.sendMessage(plugin.getFormattedMessage("messages.record_error", errorPlaceholders));
        }

        return true;
    }
}
