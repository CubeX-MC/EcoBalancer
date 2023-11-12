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
                player.sendMessage(ChatColor.RED + "你没有权限使用这个命令。");
                return true;
            }
            if (args.length == 0) {
                player.sendMessage(ChatColor.GREEN + "请输入玩家名称或使用/checkall");
            } else {
                checkPlayer(player, args[0]);
            }
        }

        return true;
    }

    private void checkPlayer(Player sender, String playerName) {
        long currentTime = System.currentTimeMillis();
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        plugin.checkBalance(sender, currentTime, target, true);
    }
}
