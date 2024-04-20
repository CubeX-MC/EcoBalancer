package org.cubexmc.ecobalancer.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.cubexmc.ecobalancer.EcoBalancer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class RestoreCommand extends AbstractCommand {

    public RestoreCommand(EcoBalancer plugin) {
        super(plugin, "restore");
    }

    @Override
    void onCommand(CommandInfo info) {
        info.checkInput(1, plugin.getFormattedMessage("restore_usage"));
        final CommandSender sender = info.getSender();
        int operationId;
        try {
            operationId = Integer.parseInt(info.getInput(0));
        } catch (final NumberFormatException exception) {
            sender.sendMessage(plugin.getFormattedMessage("restore_invalid_id"));
            return;
        }
        // restore_operation_not_found
        CompletableFuture.runAsync(() -> {
            try {
                final Connection connection = Objects.requireNonNull(plugin.driverConnection.get());
                try (final PreparedStatement statement = connection.prepareStatement("SELECT * FROM operations WHERE id = ?")) {
                    statement.setInt(1, operationId);
                    try (final ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            sender.sendMessage(plugin.getFormattedMessage("restore_operation_not_found"));
                            return;
                        }
                        @SuppressWarnings("SpellCheckingInspection") final boolean checkAll = resultSet.getBoolean("is_checkall");
                        try (final PreparedStatement select = connection.prepareStatement(
                                "SELECT * FROM records WHERE operation_id = ?" + (checkAll ? " AND deduction != 0.0" : "")
                        )) {
                            select.setInt(1, operationId);
                            try (final ResultSet record = select.executeQuery()) {
                                if (checkAll) {
                                    final Map<String, String> placeholder = new HashMap<String, String>(){{ put("operation_id", String.valueOf(operationId)); }};
                                    sender.sendMessage(plugin.getFormattedMessage("restoring_all", placeholder));
                                    while (record.next()) {
                                        final double deduction = record.getDouble("deduction");
                                        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(record.getString("player")));
                                        plugin.getEconomy().depositPlayer(offlinePlayer, deduction);
                                    }
                                    sender.sendMessage(plugin.getFormattedMessage("restored_all", placeholder));
                                } else {
                                    if (record.next()) {
                                        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(record.getString("player")));
                                        final double deduction = record.getDouble("deduction");
                                        plugin.getEconomy().depositPlayer(offlinePlayer, deduction);
                                        new HashMap<String, String>(){{
                                            put("operation_id", String.valueOf(operationId));
                                            put("player", offlinePlayer.getName());
                                            sender.sendMessage(plugin.getFormattedMessage("restored_player", this));
                                        }};
                                    } else {
                                        sender.sendMessage(plugin.getFormattedMessage("restore_not_found"));
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (final Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to restore data with operation id " + operationId, exception);
                sender.sendMessage(plugin.getFormattedMessage("restore_error"));
            }
        });
    }
}