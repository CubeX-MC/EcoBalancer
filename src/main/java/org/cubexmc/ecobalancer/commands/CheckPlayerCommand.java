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

public class CheckPlayerCommand implements CommandExecutor {
    EcoBalancer plugin;

    public CheckPlayerCommand(EcoBalancer plugin) {
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
                player.sendMessage(ChatColor.GREEN + "Please enter playername or use /checkall");
            } else {
                checkPlayer(args[0]);
            }
        }

        return true;
    }

    private void checkPlayer(String playerName) {
        System.out.println("checkPlayer()");
        long currentTime = System.currentTimeMillis();
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        System.out.println("player == null?" + (player == null));
        plugin.checkBalance(currentTime, player, true);
    }
}
