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
import java.util.UUID;
import java.util.Map;

public class CheckRecordsCommand implements CommandExecutor {
    private final EcoBalancer plugin;

    public CheckRecordsCommand(EcoBalancer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int pageNumber = 1;
        int pageSize = 10;

        if (args.length > 0) {
            try {
                pageNumber = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getFormattedMessage("messages.invalid_page", null));
                return true;
            }
        }

        // 获取数据库文件路径
        File dataFolder = plugin.getDataFolder();
        File databaseFile = new File(dataFolder, "records.db");

        // 从数据库中查询所有操作
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath())) {
            int offset = (pageNumber - 1) * pageSize;
            try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT o.id, o.timestamp, o.is_restored, r.is_checkall, SUM(r.deduction) AS total_deduction FROM operations o JOIN records r ON o.id = r.operation_id GROUP BY o.id ORDER BY o.timestamp DESC LIMIT ? OFFSET ?")) {
                preparedStatement.setInt(1, pageSize);
                preparedStatement.setInt(2, offset);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    sender.sendMessage(plugin.getFormattedMessage("messages.records_header", null));
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    while (resultSet.next()) {
                        int id = resultSet.getInt("id");
                        long timestamp = resultSet.getLong("timestamp");
                        boolean isCheckAll = resultSet.getBoolean("is_checkall");
                        double totalDeduction = resultSet.getDouble("total_deduction");
                        boolean isRestored = resultSet.getBoolean("is_restored");

                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("time", dateFormat.format(new Date(timestamp)));
                        placeholders.put("type", isCheckAll ? "A" : "P");
                        placeholders.put("deduction_amount", String.format("%.2f", totalDeduction));
                        placeholders.put("operation_id", String.valueOf(id));
                        placeholders.put("restored", isRestored ? "x" : " ");

                        sender.sendMessage(plugin.getFormattedMessage("messages.prefix", null));
                        String message = plugin.getFormattedMessage("messages.records_operation", placeholders);
                        sender.sendMessage(message);
                    }
                }
            }

            try (Statement statement = connection.createStatement()) {
                try (ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) AS total FROM (SELECT o.id FROM operations o JOIN records r ON o.id = r.operation_id GROUP BY o.id)")) {
                    if (resultSet.next()) {
                        int total = resultSet.getInt("total");
                        int totalPages = (int) Math.ceil((double) total / pageSize);
                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("page", String.valueOf(pageNumber));
                        placeholders.put("total", String.valueOf(totalPages));
                        sender.sendMessage(plugin.getFormattedMessage("messages.records_page", placeholders));
                        sender.sendMessage(plugin.getFormattedMessage("messages.records_footer", null));
                    }
                }
            }
        } catch (SQLException e) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("error", e.getMessage());
            sender.sendMessage(plugin.getFormattedMessage("messages.records_error", placeholders));
        }

        return true;
    }
}