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
                checkAll();
            }
        }

        return true;
    }

    public void checkAll() {
        int cnt = 0;
        boolean print = false;
        Economy economy = EcoBalancer.getEconomy();
        long currentTime = System.currentTimeMillis();
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (cnt < 10) {
                print = true;
            }
            plugin.checkBalance(currentTime, player, print);
        }
    }
}
