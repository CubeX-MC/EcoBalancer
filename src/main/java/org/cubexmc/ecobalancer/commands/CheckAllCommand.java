package org.cubexmc.ecobalancer.commands;

import org.cubexmc.ecobalancer.EcoBalancer;

@SuppressWarnings("SpellCheckingInspection")
public class CheckAllCommand extends AbstractCommand {

    public CheckAllCommand(EcoBalancer plugin) {
        super(plugin, "checkall");
    }

    @Override
    void onCommand(CommandInfo info) {
        info.checkInput(0);
        info.getSender().sendMessage(plugin.getFormattedMessage("scanning_offline_players"));
        plugin.checkAll(info.getSender());
    }
}
