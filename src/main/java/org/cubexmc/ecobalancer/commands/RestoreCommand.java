package org.cubexmc.ecobalancer.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.cubexmc.ecobalancer.EcoBalancer;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RestoreCommand implements CommandExecutor {
    private final EcoBalancer plugin;

    public RestoreCommand(EcoBalancer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(plugin.getFormattedMessage("messages.restore_usage", null));
            return true;
        }

        int operationId;
        try {
            operationId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getFormattedMessage("messages.restore_invalid_id", null));
            return true;
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

                        if (isCheckAll) {
                            Map<String, String> placeholders = new HashMap<>();
                            placeholders.put("operation_id", String.valueOf(operationId));
                            // 提示正在恢复所有玩家的余额
                            sender.sendMessage(plugin.getFormattedMessage("messages.restoring_all", placeholders));
                            // 恢复所有玩家的余额
                            try (PreparedStatement selectStatement = connection.prepareStatement("SELECT * FROM records WHERE operation_id = ?")) {
                                selectStatement.setInt(1, operationId);
                                try (ResultSet allRecords = selectStatement.executeQuery()) {
                                    while (allRecords.next()) {
                                        String playerUUID = allRecords.getString("player");
                                        double deduction = allRecords.getDouble("deduction");
                                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(playerUUID));
                                        plugin.getEconomy().depositPlayer(offlinePlayer, deduction);
                                    }
                                }
                            }
                            sender.sendMessage(plugin.getFormattedMessage("messages.restored_all", placeholders));
                        } else {
                            // 只恢复单个玩家的余额
                            try (PreparedStatement selectStatement = connection.prepareStatement("SELECT * FROM records WHERE operation_id = ?")) {
                                selectStatement.setInt(1, operationId);
                                try (ResultSet record = selectStatement.executeQuery()) {
                                    if (record.next()) {
                                        String playerUUID = record.getString("player");
                                        double deduction = record.getDouble("deduction");
                                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(playerUUID));
                                        plugin.getEconomy().depositPlayer(offlinePlayer, deduction);
                                        Map<String, String> placeholders = new HashMap<>();
                                        placeholders.put("operation_id", String.valueOf(operationId));
                                        placeholders.put("player", offlinePlayer.getName());
                                        sender.sendMessage(plugin.getFormattedMessage("messages.restored_player", placeholders));
                                    } else {
                                        sender.sendMessage(plugin.getFormattedMessage("messages.restore_not_found", null));
                                    }
                                }
                            }
                        }
                        // 在恢复操作后,更新操作的 is_restored 字段
                        try (PreparedStatement updateStatement = connection.prepareStatement("UPDATE operations SET is_restored = 1 WHERE id = ?")) {
                            updateStatement.setInt(1, operationId);
                            updateStatement.executeUpdate();
                        }
                    } else {
                        sender.sendMessage(plugin.getFormattedMessage("messages.restore_operation_not_found", null));
                    }
                }
            }
        } catch (SQLException e) {
            Map<String, String> errorPlaceholders = new HashMap<>();
            sender.sendMessage(plugin.getFormattedMessage("messages.restore_error", errorPlaceholders));
        }

        return true;
    }
}