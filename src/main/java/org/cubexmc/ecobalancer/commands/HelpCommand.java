package org.cubexmc.ecobalancer.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class HelpCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(ChatColor.WHITE + "" + ChatColor.BOLD + "EcoBalancer 帮助:");
        sender.sendMessage(ChatColor.YELLOW + "/checkall " + ChatColor.WHITE + "- 检查并清洗全部玩家余额");
        sender.sendMessage(ChatColor.YELLOW + "/checkplayer <player> " + ChatColor.WHITE + "- 检查并清洗单个玩家余额");
        sender.sendMessage(ChatColor.GRAY + "" + ChatColor.ITALIC + "来自CubeX统治阶级工具包");
        return true;
    }
}
