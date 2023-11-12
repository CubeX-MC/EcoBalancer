package org.cubexmc.ecobalancer.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.cubexmc.ecobalancer.EcoBalancer;

public class UtilCommand implements CommandExecutor {
    EcoBalancer plugin;

    public UtilCommand(EcoBalancer plugin) {
        this.plugin = plugin;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            Bukkit.getScheduler().cancelTasks(plugin);
            plugin.reloadConfig();
            plugin.loadConfiguration(); // You should create this method
            sender.sendMessage(ChatColor.GREEN + "EcoBalancer 重载成功");
            return true;
        } else if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(ChatColor.WHITE + "" + ChatColor.BOLD + "EcoBalancer 帮助:");
            sender.sendMessage(ChatColor.YELLOW + "/ecobal help " + ChatColor.WHITE + "- 帮助");
            sender.sendMessage(ChatColor.YELLOW + "/checkall " + ChatColor.WHITE + "- 检查并清洗全部玩家余额");
            sender.sendMessage(ChatColor.YELLOW + "/checkplayer <player> " + ChatColor.WHITE + "- 检查并清洗单个玩家余额");
            sender.sendMessage(ChatColor.YELLOW + "/ecobal reload " + ChatColor.WHITE + "- 重载配置文件");
            sender.sendMessage(ChatColor.GRAY + "" + ChatColor.ITALIC + "来自CubeX统治阶级工具包");
            return true;
        }
        return false;
    }
}
