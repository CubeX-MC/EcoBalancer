package org.cubexmc.ecobalancer.commands;

import org.bukkit.command.*;
import org.cubexmc.ecobalancer.EcoBalancer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public abstract class AbstractCommand implements TabCompleter, CommandExecutor {

    public final EcoBalancer plugin;

    public AbstractCommand(final EcoBalancer plugin, final String name) {
        this.plugin=plugin;
        if (name != null) {
            final PluginCommand pluginCommand = plugin.getCommand(name);
            pluginCommand.setExecutor(this);
            pluginCommand.setTabCompleter(this);
        }
    }

    @Override
    public final boolean onCommand(CommandSender sender, Command command, String s, String[] strings) {
        final CommandInfo commandInfo = new CommandInfo(sender, command, s, strings);
        try { onCommand(commandInfo); } catch (final BreakException ignore) {}
        return true;
    }

    @Override
    public final List<String> onTabComplete(CommandSender sender, Command command, String s, String[] strings) {
        final TabCompleteInfo tabCompleteInfo = new TabCompleteInfo(sender, command, s, strings);
        try { onTabComplete(tabCompleteInfo); } catch (final BreakException ignore) {}
        return tabCompleteInfo.getReturnResult();
    }

    abstract void onCommand(final CommandInfo info);
    void onTabComplete(final TabCompleteInfo info) {

    }

    @SuppressWarnings("unused")
    public static class TabCompleteInfo extends CommandInfo {
        private List<String> returnResult = null;
        private TabCompleteInfo(CommandSender sender, Command command, String nativeCommand, String[] input) {
            super(sender, command, nativeCommand, input);
        }

        public void setReturnResult(List<String> returnResult) {
            this.returnResult = returnResult;
        }

        private List<String> getReturnResult() {
            return returnResult == null ? Collections.emptyList() : returnResult;
        }
    }

    @SuppressWarnings("unused")
    public static class CommandInfo {
        private final CommandSender sender;
        private final Command command;
        private final String inputAlias;
        private final String[] input;
        private CommandInfo(final CommandSender sender, final Command command, final String inputAlias, final String[] input) {
            this.sender=sender;
            this.command=command;
            this.inputAlias=inputAlias;
            this.input=input;
        }

        public CommandSender getSender() {
            return sender;
        }

        private List<String> copiedInputList = null;

        public List<String> getInputAsList() {
            return (copiedInputList = (copiedInputList == null ? Arrays.asList(input) : copiedInputList));
        }

        public String[] getInput() {
            return input;
        }

        public String getInput(final int index) {
            if (index < 0) throw new IllegalArgumentException("Index cannot lower than 0!");
            if (index > input.length) throw new IllegalArgumentException("Index out of input! expect " + index + " , got " + input.length);
            return input.length > index ? input[index] : null;
        }

        public int getInputAsInt(final int index, final String exceptionallyMessage) {
            return getAsNumber(Integer::parseInt, index, exceptionallyMessage);
        }

        public double getInputAsDouble(final int index, final String exceptionallyMessage) {
            return getAsNumber(Double::parseDouble, index, exceptionallyMessage);
        }

        private <T extends Number> T getAsNumber(final Function<String, T> function, final int index, final String exceptionallyMessage) {
            try {
                return function.apply(getInput(index));
            } catch (final NumberFormatException exception) {
                if (exceptionallyMessage != null) sender.sendMessage(exceptionallyMessage);
                throw new BreakException();
            }
        }

        public void checkInput(final int length) { checkInput(length, null); }

        public void checkInput(final int length, final String message) {
            if (input.length != length) {
                if (message != null) getSender().sendMessage(message);
                throw new BreakException();
            }
        }

        public Command getCommand() {
            return command;
        }

        public String getInputAlias() {
            return inputAlias;
        }
    }

    private static final class BreakException extends RuntimeException {}
}
