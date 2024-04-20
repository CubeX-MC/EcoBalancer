package org.cubexmc.ecobalancer.commands;

import org.bukkit.command.CommandSender;
import org.cubexmc.ecobalancer.EcoBalancer;

@SuppressWarnings("unchecked")
public class CommandUtil {
    public static <T extends Number> void handleStats(
            final EcoBalancer plugin,
            final Class<T> typeClass,
            final AbstractCommand.CommandInfo info,
            final String usage,
            final String limits,
            final String invalidArgs,
            final StatsConsumer<T> consumer
    ) {
        final int length = info.getInput().length;
        final CommandSender sender = info.getSender();
        if (length < 1 || length > 3) {
            sender.sendMessage(plugin.getFormattedMessage(usage));
            sender.sendMessage(plugin.getFormattedMessage(limits));
            return;
        }
        try {
            double var;
            if (typeClass == Integer.class) {
                var = Integer.parseInt(info.getInput(0));
            } else if (typeClass == Double.class) {
                var = Double.parseDouble(info.getInput(0));
            } else return;
            double low = (length >= 2 && !info.getInput(1).equals("_")) ? Double.parseDouble(info.getInput(1)) : Double.NEGATIVE_INFINITY;
            double up = (length == 3 && !info.getInput(2).equals("_")) ? Double.parseDouble(info.getInput(1)) : Double.POSITIVE_INFINITY;
            consumer.accept(info, (T)(Double) var, low, up);
        } catch (final NumberFormatException e) {
            sender.sendMessage(plugin.getFormattedMessage(invalidArgs));
        }
    }

    @FunctionalInterface
    public interface StatsConsumer<T extends Number> {
        void accept(final AbstractCommand.CommandInfo info, final T var, final double low, final double up);
    }

    @FunctionalInterface
    public interface IntToBooleanFunction {
        boolean apply(final int var);
    }

    @FunctionalInterface
    public interface IntBiFunction<T> {
        T get(final int index, final String message);
    }
}
