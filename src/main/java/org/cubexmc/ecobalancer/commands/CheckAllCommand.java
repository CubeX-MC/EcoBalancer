package org.cubexmc.ecobalancer.commands;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.cubexmc.ecobalancer.EcoBalancer;

import java.util.UUID;

public class CheckAllCommand implements CommandExecutor {
    EcoBalancer plugin;

    public CheckAllCommand(EcoBalancer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!player.hasPermission("ecobalancer.check")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            if (args.length == 0) {
                player.sendMessage(ChatColor.GREEN + "CHECKING ALL!");
                checkAll(sender);
            }
        }

        return true;
    }

    public void checkAll(CommandSender sender) {
        final long currentTime = System.currentTimeMillis();
        final OfflinePlayer[] players = Bukkit.getOfflinePlayers();
        final int batchSize = 100; // Number of players to process at once
        final int delay = 10; // Delay in ticks between batches (20 ticks = 1 second)

        class BatchRunnable implements Runnable {
            private int index = 0;

            @Override
            public void run() {
                int start = index;
                int end = Math.min(index + batchSize, players.length);
                for (int i = index; i < end; i++) {
                    OfflinePlayer player = players[i];
                    plugin.checkBalance(currentTime, player, false);
                }
                index += batchSize;
                // Send a message to the sender after each batch
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(ChatColor.GREEN + "Processed " + (end - start) + " players. Total processed: " + end));
                if (index < players.length) {
                    // Schedule next batch
                    Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this, delay);
                } else {
                    // All players have been processed, notify the sender
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(ChatColor.GREEN + "All balances have been checked."));
                }
            }
        }

        // Start the first batch
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new BatchRunnable());
    }
}
