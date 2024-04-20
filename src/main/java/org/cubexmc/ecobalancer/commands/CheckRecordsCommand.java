package org.cubexmc.ecobalancer.commands;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.cubexmc.ecobalancer.EcoBalancer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class CheckRecordsCommand extends AbstractCommand {
    public CheckRecordsCommand(EcoBalancer plugin) {
        super(plugin, "checkrecords");
    }

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    void onCommand(CommandInfo info) {
        final int length = info.getInput().length;
        int pageNumber = (length > 0 ? info.getInputAsInt(0, plugin.getFormattedMessage("invalid_page")) : 1);
        int pageSize = 10;
        if (pageNumber < 1) return; // 不认为pageNumber可以小于1
        final CommandSender sender = info.getSender();
        CompletableFuture.runAsync(() -> {
            try {
                final Connection connection = Objects.requireNonNull(plugin.driverConnection.get());
                int offset = (pageNumber - 1) * pageSize;
                try (final PreparedStatement statement = connection.prepareStatement("SELECT o.id, o.timestamp, o.is_restored, r.is_checkall, SUM(r.deduction) AS total_deduction FROM operations o JOIN records r ON o.id = r.operation_id GROUP BY o.id ORDER BY o.timestamp DESC LIMIT ? OFFSET ?")) {
                    statement.setInt(1, pageSize);
                    statement.setInt(2, offset);
                    try (final ResultSet resultSet = statement.executeQuery()) {
                        sender.sendMessage(plugin.getFormattedMessage("records_header"));
                        while (resultSet.next()) {
                            final int id = resultSet.getInt("id");
                            final long timestamp = resultSet.getLong("timestamp");
                            final boolean isCheckAll = resultSet.getBoolean("is_checkall");
                            final double totalDeduction = resultSet.getDouble("total_deduction");
                            final boolean isRestored = resultSet.getBoolean("is_restored");
                            final Map<String, String> placeholders = new HashMap<String, String>(){{
                                put("time", dateFormat.format(new Date(timestamp)));
                                put("type", isCheckAll ? "A" : "P");
                                put("deduction_amount", String.format("%.2f", totalDeduction));
                                put("operation_id", String.valueOf(id));
                                put("restored", isRestored ? "x" : " ");
                            }};
                            final TextComponent textComponent = new TextComponent(String.valueOf(id));
                            textComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/checkrecord " + id));
                            textComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent[]{new TextComponent(plugin.getFormattedMessage("records_click"))}));
                            final TextComponent messageComponent = plugin.getFormattedMessage("records_operation", placeholders, new String[]{"operation_id"}, new TextComponent[]{textComponent});
                            sender.spigot().sendMessage(messageComponent);
                        }
                    }
                }
                try (final Statement statement = connection.createStatement()) {
                    try (final ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) AS total FROM (SELECT o.id FROM operations o JOIN records r ON o.id = r.operation_id GROUP BY o.id)")) {
                        if (resultSet.next()) {
                            int total = resultSet.getInt("total");
                            int totalPages = (int) Math.ceil((double) total / pageSize);
                            TextComponent previousPage = new TextComponent();
                            TextComponent nextPage = new TextComponent();
                            if (pageNumber > 1) {
                                previousPage.setText(plugin.getFormattedMessage("prev_page"));
                                previousPage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/checkrecords " + (pageNumber - 1)));
                            } else {
                                previousPage.setText(plugin.getFormattedMessage("no_prev_page"));
                            }
                            if (pageNumber < totalPages) {
                                nextPage.setText(plugin.getFormattedMessage("next_page"));
                                nextPage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/checkrecords " + (pageNumber + 1)));
                            } else {
                                nextPage.setText(plugin.getFormattedMessage("no_next_page"));
                            }
                            final Map<String, String> placeholders = new HashMap<String, String>(){{
                                put("page", String.valueOf(pageNumber));
                                put("total", String.valueOf(totalPages));
                                put("prev", previousPage.toPlainText());
                                put("next", nextPage.toPlainText());
                            }};
                            TextComponent message = plugin.getFormattedMessage("records_page", placeholders, new String[]{"prev", "next"}, new TextComponent[]{previousPage, nextPage});
                            sender.spigot().sendMessage(message);
                            sender.sendMessage(plugin.getFormattedMessage("records_footer"));
                        }
                    }
                }
            } catch (final Exception exception) {
                sender.sendMessage(plugin.getFormattedMessage("records_error"));
            }
        });
    }
}