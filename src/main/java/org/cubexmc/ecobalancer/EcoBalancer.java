package org.cubexmc.ecobalancer;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.apache.commons.lang.StringUtils;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.cubexmc.ecobalancer.commands.*;
import org.cubexmc.ecobalancer.listeners.AdminLoginListener;
import org.cubexmc.ecobalancer.util.StatementWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import static java.lang.Math.min;

@SuppressWarnings({"ResultOfMethodCallIgnored", "deprecation", "SpellCheckingInspection"})
public final class EcoBalancer extends JavaPlugin {
    private Economy econ;
    private boolean deductBasedOnTime;
    private int inactiveDaysToDeduct;
    private final TreeMap<Integer, Double> taxBrackets = new TreeMap<>();
    private int inactiveDaysToClear;
    private FileHandler fileHandler;
    private final Logger fileLogger = Logger.getLogger("EcoBalancerFileLogger");
    private int recordRetentionDays;
    private String scheduleType;
    private List<Integer> scheduleDaysOfWeek;
    private List<Integer> scheduleDatesOfMonth;
    private String checkTime;
    private FileConfiguration langConfig;
    private boolean taxAccount;
    private String taxAccountName;
    private String messagePrefix;

    public final CompletableFuture<Connection> driverConnection = CompletableFuture
            .supplyAsync(() -> {
                final File file = new File(this.getDataFolder(), "records.db");
                try {
                    if (!getDataFolder().exists()) getDataFolder().mkdirs();
                    file.createNewFile();
                    Class.forName("org.sqlite.JDBC"); // 初始化sqlite driver静态块
                    final Connection connection =  DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
                    try (final Statement statement = connection.createStatement()) {
                        statement.execute("CREATE TABLE IF NOT EXISTS operations (id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp INTEGER NOT NULL, is_checkall BOOLEAN NOT NULL, is_restored BOOLEAN NOT NULL)");
                        statement.execute("CREATE TABLE IF NOT EXISTS records (id INTEGER PRIMARY KEY AUTOINCREMENT, player_name TEXT NOT NULL, player TEXT NOT NULL, old_balance REAL NOT NULL, new_balance REAL NOT NULL, deduction REAL NOT NULL, timestamp INTEGER NOT NULL, is_checkall BOOLEAN NOT NULL, operation_id INTEGER NOT NULL)");
                    }
                    return connection;
                } catch (final Exception exception) {
                    throw new RuntimeException(exception);
                }
            })
            .exceptionally(exception -> {
                final Throwable throwable = (exception.getCause() != null ? exception.getCause() : exception);
                getLogger().log(Level.SEVERE, "无法初始化数据库.", throwable);
                return null;
            });

    @SuppressWarnings("deprecation")
    @Override
    public void onEnable() {
        // 不要对硬Vault实现做太多繁琐的东西.
        econ = Objects.requireNonNull(getServer().getServicesManager().getRegistration(Economy.class)).getProvider();

        saveDefaultConfig();  // 保存默认配置
        loadConfiguration();  // 加载配置

        // 检查数据库初始化失败
        try {
            Objects.requireNonNull(driverConnection.get(), "Database is null!");
        } catch (final Exception exception) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        long initialDelay = calculateDelayForDaily(Calendar.getInstance(), 0, 0); // 在每天的午夜12点运行
        long cleanupPeriod = 24L * 60L * 60L * 20L; // 24小时(以tick为单位)
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::cleanupRecords, initialDelay, cleanupPeriod);

        // Check for an existing log file and compress it if found
        File logDir = new File(getDataFolder() + File.separator + "logs");
        if (!logDir.exists()) {
            logDir.mkdirs(); // This will create the directory if it does not exist
        }
        File lockFile = new File(getDataFolder() + File.separator + "logs" + File.separator + "latest.log.lck");
        if (lockFile.exists()) {
            lockFile.delete(); // This will delete the lock file if it exists
        }
        File existingLogFile = new File(getDataFolder() + File.separator + "logs" + File.separator + "latest.log");
        if (existingLogFile.exists()) {
            compressExistingLogFile(existingLogFile);
        }

        try {
            fileHandler = new FileHandler(getDataFolder() + File.separator + "logs" + File.separator + "latest.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            fileLogger.addHandler(fileHandler);
            fileLogger.setUseParentHandlers(false);
        } catch (IOException exception) {
            getLogger().log(Level.SEVERE, "Could not create the log file handler for EcoBalancer.", exception);
        }

        // metrics
        int pluginId = 20269; // <-- Replace with the id of your plugin!
        Metrics metrics = new Metrics(this, pluginId);
        // Optional: Add custom charts
        metrics.addCustomChart(new SimplePie("chart_id", () -> "My value"));

        // 创建虚拟账户
        if (taxAccount && !econ.hasAccount(taxAccountName)) {
            econ.createPlayerAccount(taxAccountName);
        }

        getServer().getPluginManager().registerEvents(new AdminLoginListener(this), this);
        final List<Function<EcoBalancer, AbstractCommand>> commandList = Arrays.asList(
                UtilCommand::new,
                CheckAllCommand::new,
                CheckPlayerCommand::new,
                DescriptionStatsCommand::new,
                PercentileCommand::new,
                CheckRecordsCommand::new,
                CheckRecordCommand::new,
                RestoreCommand::new,
                IntervalCommand::new
        );
        for (final Function<EcoBalancer, AbstractCommand> command : commandList) command.apply(this);
        displayAsciiArt();
        getLogger().info("EcoBalancer enabled!");
    }

    private void displayAsciiArt() {
        String[] asciiArt = {
                "▓█████  ▄████▄   ▒█████   ▄▄▄▄    ▄▄▄       ██▓    ",
                "▓█   ▀ ▒██▀ ▀█  ▒██▒  ██▒▓█████▄ ▒████▄    ▓██▒    ",
                "▒███   ▒▓█    ▄ ▒██░  ██▒▒██▒ ▄██▒██  ▀█▄  ▒██░    ",
                "▒▓█  ▄ ▒▓▓▄ ▄██▒▒██   ██░▒██░█▀  ░██▄▄▄▄██ ▒██░    ",
                "░▒████▒▒ ▓███▀ ░░ ████▓▒░░▓█  ▀█▓ ▓█   ▓██▒░██████▒",
                "░░ ▒░ ░░ ░▒ ▒  ░░ ▒░▒░▒░ ░▒▓███▀▒ ▒▒   ▓▒█░░ ▒░▓  ░",
                " ░ ░  ░  ░  ▒     ░ ▒ Version: " + getDescription().getVersion(),
                "   ░   ░        ░ ░ ░ Author: " + getDescription().getAuthors().stream().findFirst().orElse(null),
                "   ░  ░░ ░          ░ Website: " + getDescription().getWebsite(),
                "                      Powered by CubeX"
        };
        // 在控制台输出彩色的 ASCII 艺术字符
        getLogger().info("");
        for (int i = 0; i < asciiArt.length; i++) {
            String line = asciiArt[i];
            if (i < 6) {
                line = "&e" + line + "&r";
            } else if (i < 9) {
                // green since 21 characters
                line = "&e" + line.substring(0, 21) + "&r" + line.substring(21) + "&r";
            } else { // asciiArt有10行 期望大于9是不可能的. (i == 9)
                line = line
                        .replace("Cube", "&cCube&f")
                        .replace("X", "&fX&r");
            }
            getLogger().info(ChatColor.translateAlternateColorCodes('&', line));
        }
        getLogger().info("");
    }

    public boolean useTaxAccount() {
        return taxAccount;
    }

    public String getTaxAccountName() {
        return taxAccountName;
    }

    public String getTaxAccountBalance() {
        return String.format("%.2f", econ.getBalance(taxAccountName));
    }

    public void loadConfiguration() {
        // Cancel all scheduled tasks
        Bukkit.getScheduler().cancelTasks(this);
        // load language config
        loadLangFile();
        messagePrefix = langConfig.getString("prefix", "&7[&6EcoBalancer&7]&r");
        recordRetentionDays = getConfig().getInt("record-retention-days", 30);
        // Load the new scheduling configuration
        scheduleType = getConfig().getString("check-schedule.type", "daily");
        scheduleDaysOfWeek = getConfig().getIntegerList("check-schedule.days-of-week");
        scheduleDatesOfMonth = getConfig().getIntegerList("check-schedule.dates-of-month");
        checkTime = getConfig().getString("check-time", "01:00");  // 读取配置
        // Determine which scheduling method to use based on the type
        scheduleCheck(calculateNextDelay());
        // deduction setting
        deductBasedOnTime = getConfig().getBoolean("deduct-based-on-time", false);
        inactiveDaysToDeduct = getConfig().getInt("inactive-days-to-deduct", 50);
        inactiveDaysToClear = getConfig().getInt("inactive-days-to-clear", 500);
        List<Map<?, ?>> rawTaxBrackets = getConfig().getMapList("tax-brackets");
        taxAccount = getConfig().getBoolean("tax-account", false);
        taxAccountName = taxAccount ? getConfig().getString("tax-account-name", "tax") : null;

        for (Map<?, ?> bracket : rawTaxBrackets) {
            Integer threshold = bracket.get("threshold") == null ? Integer.MAX_VALUE : (Integer) bracket.get("threshold");
            Double rate = ((Number) bracket.get("rate")).doubleValue();
            taxBrackets.put(threshold, rate);
        }
    }

    private void loadLangFile() {
        // Load the language file based on config
        String lang = getConfig().getString("language", "en_US");
        getLogger().log(Level.INFO, "Loading language file: " + lang);
        File langFile = new File(getDataFolder(), "lang" + File.separator + lang + ".yml");
        if (!langFile.exists()) {
            saveResource("lang" + File.separator + lang + ".yml", false);
        }
        langConfig = YamlConfiguration.loadConfiguration(langFile);
    }

    public String getFormattedMessage(final String path) {
        return getFormattedMessage(path, null);
    }

    public String getFormattedMessage(String path, Map<String, String> placeholders) {
        /* 修改入参是不好的做法.
        if (placeholders == null) {
            placeholders = new HashMap<>();
        }
         */
        String message = langConfig.getString("messages." + path, "Message not found! key:" + path);
        if (placeholders != null) {
            placeholders.put("prefix", messagePrefix);
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        } else message=message.replace("%prefix%", messagePrefix);

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public TextComponent getFormattedMessage(String path, Map<String, String> placeholders, String[] clickablePlaceholders, TextComponent[] clickableComponents) {
        String messageTemplate = langConfig.getString("messages." + path, "Message not found! key:" + path);

        // 初始化一个基础的TextComponent用于最终消息
        TextComponent finalMessage = new TextComponent("");
        if (placeholders != null) {
            placeholders.put("prefix", messagePrefix);
            // 替换除clickablePlaceholders外的所有占位符
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                if (!Arrays.asList(clickablePlaceholders).contains(entry.getKey())) {
                    messageTemplate = messageTemplate.replace("%" + entry.getKey() + "%", entry.getValue());
                }
            }
        }

        // 分割消息模板
        String[] messageParts = messageTemplate.split("%", -1);

        for (String part : messageParts) {
            // 检查这部分是否匹配任何可点击的占位符
            int placeholderIndex = -1;
            for (int j = 0; j < clickablePlaceholders.length; j++) {
                if (part.startsWith(clickablePlaceholders[j])) {
                    placeholderIndex = j;
                    break;
                }
            }

            if (placeholderIndex != -1) {
                // 如果这部分以一个可点击的占位符开始,添加相应的可点击组件
                finalMessage.addExtra(clickableComponents[placeholderIndex]);

                // 如果占位符后还有文本,作为普通文本添加
                String remainingText = part.substring(clickablePlaceholders[placeholderIndex].length());
                if (!remainingText.isEmpty()) {
                    finalMessage.addExtra(new TextComponent(ChatColor.translateAlternateColorCodes('&', remainingText)));
                }
            } else {
                // 如果这部分不是可点击的占位符,作为普通文本添加
                if (!part.isEmpty()) {
                    finalMessage.addExtra(new TextComponent(ChatColor.translateAlternateColorCodes('&', part)));
                }
            }
        }

        return finalMessage;
    }



    @Override
    public void onDisable() {

        try {
            final Connection connection = driverConnection.get();
            if (connection != null) connection.close();
        } catch (final Exception ignore) {

        }

        // Ensure all pending logs are flushed and the handler is closed
        if (fileHandler != null) {
            fileHandler.flush();
            fileLogger.removeHandler(fileHandler);
            fileHandler.close();
        }

        // Now attempt to compress the log file
        File logFile = new File(getDataFolder() + File.separator + "logs" + File.separator + "latest.log");
        if (logFile.exists()) {
            compressExistingLogFile(logFile);
        }

        getLogger().info("EcoBalancer disabled.");
    }

    // Method to compress the existing log file
    private void compressExistingLogFile(File logFile) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HHmm");
        String timestamp = dateFormat.format(new Date(logFile.lastModified()));
        File renamedLogFile = new File(logFile.getParent(), timestamp + ".log");
        // Rename the file to include the timestamp
        if (!logFile.renameTo(renamedLogFile)) {
            getLogger().severe("Could not rename the log file.");
            return;
        }
        // Compress the renamed log file into a .gz file
        File compressedFile = new File(renamedLogFile.getParent(), renamedLogFile.getName() + ".gz");
        try (GZIPOutputStream gzos = new GZIPOutputStream(Files.newOutputStream(compressedFile.toPath()))) {
            Files.copy(renamedLogFile.toPath(), gzos);
        } catch (IOException e) {
            getLogger().severe("Could not compress the log file: " + e.getMessage());
        }
        // Delete the original (now renamed) log file after it's compressed
        if (!renamedLogFile.delete()) {
            getLogger().severe("Could not delete the original log file after compression.");
        }
    }

    public Economy getEconomy() {
        return econ;
    }

    public void checkBalance(CommandSender sender, long currentTime, OfflinePlayer player, boolean log, boolean isCheckAll, int operationId) {
        long lastPlayed = player.getLastPlayed();
        long daysOffline = (currentTime - lastPlayed) / (1000 * 60 * 60 * 24);
        double balance = econ.hasAccount(player) ? econ.getBalance(player) : 0;
        Double deductionRate = 0.0;

        if (taxAccount && player.getName().equals(taxAccountName)) return;

        Map.Entry<Integer, Double> entry = taxBrackets.higherEntry((int) balance);
        if (entry != null) {
            deductionRate = entry.getValue();
        }
        // If no bracket is found (which should not happen because we use Integer.MAX_VALUE for the highest bracket), use a default rate
        if (deductionRate == null) {
            deductionRate = 0.0; // defaultRate should be defined somewhere in your class
        }

        Map<String, String> placeholders = new HashMap<String, String>(){{
            put("player", player.getName());
            put("balance", String.format("%.2f", balance));
            put("days_offline", String.valueOf(daysOffline));
        }};

        // fix all negative balance
        if (balance < 0.0) {
            econ.depositPlayer(player, -1 * balance);
            placeholders.put("new_balance", String.format("%.2f", econ.getBalance(player)));
            sendMessage(sender, "negative_balance", placeholders, log);
        } else if (balance > 0.0) {
            if (deductBasedOnTime) {
                // 计算玩家离线天数
                if (daysOffline > inactiveDaysToClear) {
                    // 清除超过inactiveDaysToClear天未上线的玩家
                    econ.withdrawPlayer(player, balance);
                    if (taxAccount) econ.depositPlayer(taxAccountName, balance); //
                    placeholders.put("new_balance", String.format("%.2f", econ.getBalance(player)));
                    sendMessage(sender, "offline_extreme", placeholders, log);
                } else if (daysOffline > inactiveDaysToDeduct) {
                    // 对于超过50天未上线的玩家，按税率扣除
                    double deduction = min(balance, balance * deductionRate); // in case deductionRate is greater than 1
                    placeholders.put("deduction", String.format("%.2f", deduction));
                    econ.withdrawPlayer(player, deduction);
                    if (taxAccount) econ.depositPlayer(taxAccountName, deduction);
                    sendMessage(sender, "offline_moderate", placeholders, log);
                } else {
                    sendMessage(sender, "offline_active", placeholders, false);
                }
            } else {
                double deduction = min(balance, balance * deductionRate); // in case deductionRate is greater than 1
                placeholders.put("deduction", String.format("%.2f", deduction));
                econ.withdrawPlayer(player, deduction);
                if (taxAccount) econ.depositPlayer(taxAccountName, deduction);
                sendMessage(sender, "deduction_made", placeholders, log);
            }
        } else {
            sendMessage(sender, "zero_balance", placeholders, log);
        }

        double newBalance = econ.getBalance(player);
        double deduction = balance - newBalance;
        saveRecord(player, balance, newBalance, deduction, isCheckAll, operationId);
    }

    private void sendMessage(CommandSender sender, String path, Map<String, String> placeholders, boolean isLog) {
        String message = getFormattedMessage(path, placeholders);
        if (sender != null) for (String str : message.split("\n")) sender.sendMessage(str);
        if (isLog) for (String str : message.split("\n")) fileLogger.info(str);
    }

    private long calculateNextDelay() {
        Calendar now = Calendar.getInstance();

        // 选择最近的一个执行时间
        switch (scheduleType) {
            case "weekly":
                return calculateDelayForWeekly(now);
            case "monthly":
                return calculateDelayForMonthly(now);
            case "daily":
            default:
                return calculateDelayForDaily(now);
        }
    }

    private long calculateDelayForDaily(Calendar now) {
        int hourOfDay = Integer.parseInt(checkTime.split(":")[0]);
        int minute = Integer.parseInt(checkTime.split(":")[1]);
        return calculateDelayForDaily(now, hourOfDay, minute);
    }
    private long calculateDelayForDaily(Calendar now, int hours, int minutes) {

        Calendar nextCheck = (Calendar) now.clone();
        nextCheck.set(Calendar.HOUR_OF_DAY, hours);
        nextCheck.set(Calendar.MINUTE, minutes);
        nextCheck.set(Calendar.SECOND, 0);
        nextCheck.set(Calendar.MILLISECOND, 0);

        // 如果下一个检查时间在现在之前，添加一天
        if (nextCheck.before(now)) {
            nextCheck.add(Calendar.DAY_OF_MONTH, 1);
        }

        return (nextCheck.getTimeInMillis() - now.getTimeInMillis()) / 50; // 返回ticks
    }

    private long calculateDelayForWeekly(Calendar now) {
        int today = now.get(Calendar.DAY_OF_WEEK);
        if (scheduleDaysOfWeek.contains(today)) {
            // 如果今天是执行日，检查当前时间是否已过计划执行时间
            long delayForToday = calculateDelayForDaily(now);
            if (delayForToday > 0) {
                // 如果还没到计划时间，返回今天的延迟
                return delayForToday;
            }
        }

        int daysUntilNextCheck = scheduleDaysOfWeek.stream()
                .sorted()
                .filter(dayOfWeek -> dayOfWeek > today)
                .map(dayOfWeek -> dayOfWeek - today)
                .findFirst()
                .orElse(7 + scheduleDaysOfWeek.get(0) - today);

        int hourOfDay = Integer.parseInt(checkTime.split(":")[0]);
        int minute = Integer.parseInt(checkTime.split(":")[1]);

        Calendar nextCheck = (Calendar) now.clone();
        nextCheck.add(Calendar.DAY_OF_WEEK, daysUntilNextCheck);
        nextCheck.set(Calendar.HOUR_OF_DAY, hourOfDay);
        nextCheck.set(Calendar.MINUTE, minute);
        nextCheck.set(Calendar.SECOND, 0);
        nextCheck.set(Calendar.MILLISECOND, 0);

        return (nextCheck.getTimeInMillis() - now.getTimeInMillis()) / 50; // 返回ticks
    }

    private long calculateDelayForMonthly(Calendar now) {
        int dayOfMonth = now.get(Calendar.DAY_OF_MONTH);
        if (scheduleDatesOfMonth.contains(dayOfMonth)) {
            // 如果今天是执行日，检查当前时间是否已过计划执行时间
            long delayForToday = calculateDelayForDaily(now);
            if (delayForToday > 0) {
                // 如果还没到计划时间，返回今天的延迟
                return delayForToday;
            }
        }
        int daysUntilNextCheck = scheduleDatesOfMonth.stream()
                .filter(date -> date > dayOfMonth)
                .map(date -> date - dayOfMonth)
                .findFirst()
                .orElse(scheduleDatesOfMonth.get(0) + now.getActualMaximum(Calendar.DAY_OF_MONTH) - dayOfMonth);

        int hourOfDay = Integer.parseInt(checkTime.split(":")[0]);
        int minute = Integer.parseInt(checkTime.split(":")[1]);

        Calendar nextCheck = (Calendar) now.clone();
        nextCheck.add(Calendar.DAY_OF_MONTH, daysUntilNextCheck);
        nextCheck.set(Calendar.HOUR_OF_DAY, hourOfDay);
        nextCheck.set(Calendar.MINUTE, minute);
        nextCheck.set(Calendar.SECOND, 0);
        nextCheck.set(Calendar.MILLISECOND, 0);

        return (nextCheck.getTimeInMillis() - now.getTimeInMillis()) / 50; // 返回ticks
    }


    private void scheduleCheck(long delay) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
            checkAll(null); // 运行任务

            // 任务完成后，计划下一个任务
            scheduleCheck(calculateNextDelay());
        }, delay);
    }

    public void checkPlayer(CommandSender sender, String playerName) {
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> checkPlayer(sender, playerName));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.hasPlayedBefore()) {
            long currentTime = System.currentTimeMillis();
            final int operationId = getNextOperationId(false);  // false for checkPlayer
            checkBalance(sender, currentTime, target, true, false, operationId);
        } else {
            sender.sendMessage(getFormattedMessage("player_not_found", null));
        }
    }

    public void checkAll(CommandSender sender) {
        final long currentTime = System.currentTimeMillis();
        final OfflinePlayer[] players = Bukkit.getOfflinePlayers();
        final int batchSize = 100; // Number of players to process at once
        final int delay = 10; // Delay in ticks between batches (20 ticks = 1 second)

        final int operationId = getNextOperationId(true);

        class BatchRunnable implements Runnable {
            private int index = 0;

            @Override
            public void run() {
                int start = index;
                int end = Math.min(index + batchSize, players.length);
                for (int i = index; i < end; i++) {
                    OfflinePlayer player = players[i];
                    checkBalance(null, currentTime, player, false, true, operationId);
                }
                index += batchSize;

                Map<String, String> placeholders = new HashMap<String, String>(){{
                    put("start", Integer.toString(start));
                    put("end", Integer.toString(end));
                    put("batch", Integer.toString(end - start));
                    put("total_players", Integer.toString(players.length));
                }};

                sendMessage(sender, "players_processing", placeholders, true);
                if (index < players.length) {
                    // Schedule next batch
                    Bukkit.getScheduler().runTaskLaterAsynchronously(EcoBalancer.this, this, delay);
                } else {
                    // All players have been processed, notify the sender.
                    // Send a message to the sender after each batch
                    calculateTotalDeduction(operationId);
                    Bukkit.getScheduler().runTask(EcoBalancer.this, () -> sendMessage(sender, "all_players_processed", null, true));
                }
            }
        }

        // Start the first batch
        Bukkit.getScheduler().runTaskAsynchronously(this, new BatchRunnable());
    }

    private void calculateTotalDeduction(int operationId) {
        // 建立数据库连接
        try  {
            final Connection connection = Objects.requireNonNull(driverConnection.get());
            // 查询该操作ID的所有记录,并计算扣除金额的总和
            try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT SUM(deduction) AS total_deduction FROM records WHERE operation_id = ?")) {
                preparedStatement.setInt(1, operationId);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        double totalDeduction = resultSet.getDouble("total_deduction");
                        // 在这里,你可以选择将总扣除金额记录到数据库的另一个表中,或者简单地记录到日志文件中
                        getLogger().info("Operation " + operationId + " total deduction: " + totalDeduction);
                    }
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "无法计算总扣除金额.", e);
        }
    }

    private int getNextOperationId(boolean isCheckAll) {
        // 建立数据库连接
        try {
            final Connection connection = Objects.requireNonNull(driverConnection.get());
            // 如果表不存在,则创建表
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS operations (id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp INTEGER NOT NULL, is_checkall BOOLEAN NOT NULL, is_restored BOOLEAN NOT NULL DEFAULT 0)");
            }

            // 插入一个新的操作记录
            try (PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO operations (timestamp, is_checkall, is_restored) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                StatementWriter
                        .newWriter(preparedStatement)
                        .writeLong(System.currentTimeMillis())
                        .writeBoolean(isCheckAll)
                        .writeBoolean(false)
                        .executeUpdate();

                // 获取新插入的操作的ID
                try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    } else {
                        throw new SQLException("Creating operation failed, no ID obtained.");
                    }
                }
            }
        } catch (final Exception e) {
            getLogger().log(Level.SEVERE, "无法获取下一个操作ID", e);
            return -1;  // 返回一个无效的操作ID表示出错
        }
    }

    public void generateHistogram(CommandSender sender, int numBars, double low, double up) {
        sender.sendMessage(getFormattedMessage("stats_hist_drawing"));
        final List<Double> balances = getBalances(low, up);

        double min = balances.stream().min(Double::compareTo).orElse(0.0);
        double max = balances.stream().max(Double::compareTo).orElse(0.0);
        double range = max - min;
        double barWidth = range / numBars;
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("min", String.format("%.2f", min));
        placeholders.put("max", String.format("%.2f", max));
        sender.sendMessage(getFormattedMessage("stats_min_max", placeholders));

        int[] histogram = new int[numBars];
        for (double balance : balances) {
            int barIndex = (int) ((balance - min) / barWidth);
            if (barIndex == numBars) {
                barIndex--;
            }
            histogram[barIndex]++;
        }

        int maxBarLength = 100; // 可以根据需要调整这个值
        int maxFrequency = Arrays.stream(histogram).max().orElse(0);

        sender.sendMessage(getFormattedMessage("stats_hist_header"));
        for (int i = 0; i < numBars; i++) {
            double lowerBound = min + i * barWidth;
            double upperBound = lowerBound + barWidth;
            int barLength = (int) (((double) histogram[i] / maxFrequency) * maxBarLength);
            String bar = "§a" + StringUtils.repeat("▏", barLength) + "§r";

            // 创建一个TextComponent作为可点击的条

            int finalI = i;
            Map<String, String> intervalPlaceholders = new HashMap<String, String>(){{
                put("bar", bar);
                put("frequency", Integer.toString(histogram[finalI]));
                put("low", formatNumber(lowerBound));
                put("up", formatNumber(upperBound));
            }};

            TextComponent clickableBar = new TextComponent(bar);
            clickableBar.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/interval balance " + lowerBound + " " + upperBound));
            clickableBar.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(getFormattedMessage("stats_check_interval", intervalPlaceholders)).create()));

            TextComponent message = getFormattedMessage("stats_bar", intervalPlaceholders, new String[]{"bar"}, new TextComponent[]{clickableBar});
            sender.spigot().sendMessage(message);
        }

        // Calculate and print additional statistics
        double mean = balances.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double median = calculateMedian(balances);
        double standardDeviation = calculateStandardDeviation(balances, mean);

        new HashMap<String, String>(){{
            put("mean", String.format("%.2f", mean));
            put("median", String.format("%.2f", median));
            put("sd", String.format("%.2f", standardDeviation));
            sender.sendMessage(getFormattedMessage("stats_mean_median", this));
            sender.sendMessage(getFormattedMessage("stats_sd", this));
        }};
    }

    private double calculateMedian(List<Double> values) {
        Collections.sort(values);
        int size = values.size();
        if (size == 0) {
            return 0;
        } else if (size % 2 == 0) {
            return (values.get(size / 2 - 1) + values.get(size / 2)) / 2;
        } else {
            return values.get(size / 2);
        }
    }

    private double calculateStandardDeviation(List<Double> values, double mean) {
        if (values.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (double value : values) {
            sum += Math.pow(value - mean, 2);
        }
        return Math.sqrt(sum / values.size());
    }

    private String formatNumber(double number) {
        if (number >= 1000000000) {
            return String.format("%.1fb", number / 1000000000);
        } else if (number >= 1000000) {
            return String.format("%.1fm", number / 1000000);
        } else if (number >= 1000) {
            return String.format("%.1fk", number / 1000);
        } else {
            return String.format("%.1f", number);
        }
    }

    public double calculatePercentile(double balance, double low, double high) {
        final List<Double> balances = getBalances(low, high);
        int totalPlayers = balances.size();
        int playersBelow = (int) balances.stream().filter(b -> b < balance).count();
        return (double) playersBelow / totalPlayers * 100;
    }

    private void saveRecord(OfflinePlayer player, double oldBalance, double newBalance, double deduction, boolean isCheckAll, int operationId) {
        // 建立数据库连接
        try  {
            final Connection connection = Objects.requireNonNull(driverConnection.get());
            // 如果表不存在,则创建表
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS records (id INTEGER PRIMARY KEY AUTOINCREMENT, player_name TEXT NOT NULL, player TEXT NOT NULL, old_balance REAL NOT NULL, new_balance REAL NOT NULL, deduction REAL NOT NULL, timestamp INTEGER NOT NULL, is_checkall BOOLEAN NOT NULL, operation_id INTEGER NOT NULL)");
            }

            // 插入记录
            try (StatementWriter writer = StatementWriter.newWriter(connection, "INSERT INTO records (operation_id, player_name, player, old_balance, new_balance, deduction, timestamp, is_checkall) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                writer
                        .writeInt(operationId)
                        .writeString(player.getName())
                        .writeString(player.getUniqueId().toString())
                        .writeDouble(oldBalance)
                        .writeDouble(newBalance)
                        .writeDouble(deduction)
                        .writeLong(System.currentTimeMillis())
                        .writeBoolean(isCheckAll)
                        .executeUpdate();
            }
        } catch (Exception e) {
            getLogger().severe(getFormattedMessage("sql_save_error") + e.getMessage());
        }
    }

    private void deleteRecord(final String sql, final int operationId) throws Exception {
        try (final StatementWriter writer = StatementWriter.newWriter(Objects.requireNonNull(driverConnection.get()), sql)) {
            writer.writeInt(operationId).executeUpdate();
        }
    }

    private void cleanupRecords() {
        // 计算过期时间
        long expirationTime = System.currentTimeMillis() - (long) recordRetentionDays * 24 * 60 * 60 * 1000;
        // 建立数据库连接
        try {
            final Connection connection = Objects.requireNonNull(driverConnection.get());
            // 首先,我们需要找到所有过期的操作ID
            try (PreparedStatement selectExpiredOperations = connection.prepareStatement("SELECT id FROM operations WHERE timestamp < ?")) {
                selectExpiredOperations.setLong(1, expirationTime);
                try (ResultSet expiredOperations = selectExpiredOperations.executeQuery()) {
                    while (expiredOperations.next()) {
                        int operationId = expiredOperations.getInt("id");

                        // 删除records表中所有与该操作ID相关的记录
                        deleteRecord("DELETE FROM records WHERE operation_id = ?", operationId);
                        // 删除operations表中的该操作记录
                        deleteRecord("DELETE FROM operations WHERE id = ?", operationId);
                    }
                }
            }
        } catch (final Exception e) {
            getLogger().log(Level.SEVERE, getFormattedMessage("sql_clean_error"), e);
        }
    }

    private List<Double> getBalances(final double low, final double high) {
        return Arrays
                .stream(Bukkit.getOfflinePlayers())
                .filter(econ::hasAccount)
                .map(econ::getBalance)
                .filter(balance -> balance >= low && balance <= high)
                .collect(Collectors.toList());
    }
}
