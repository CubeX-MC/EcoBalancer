package org.cubexmc.ecobalancer.commands;

import org.bukkit.command.CommandSender;
import org.cubexmc.ecobalancer.EcoBalancer;

public class CheckPlayerCommand extends AbstractCommand {
    public CheckPlayerCommand(EcoBalancer plugin) {
        super(plugin, "checkplayer");
    }

    @Override
    void onCommand(CommandInfo info) {
        final CommandSender sender = info.getSender();
        if (info.getInput().length == 0) {
            sender.sendMessage(plugin.getFormattedMessage("enter_player_name_or_use_checkall"));
        } else {
            info.checkInput(1);
            plugin.checkPlayer(sender, info.getInput(0));
        }
    }
}
